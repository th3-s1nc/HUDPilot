package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Automatischer Abgleich mit dem eigenen Server (ab App 0.25.0, abschaltbar im Server-Bildschirm):
 * Fahrten gehen nach dem Speichern sofort hoch (auch über mobile Daten, mit späteren Wiederholungen),
 * Einstellungen werden nach Änderungen gesichert und beim Öffnen vom Server geholt, wenn dort etwas Neueres liegt.
 * Haben beide Seiten Änderungen, wird gefragt.
 */
object AutoSync {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_sync", true)
    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("auto_sync", on).apply() }

    private fun ready(ctx: Context) = RegionSync.serverUrl(ctx).isNotEmpty() && ServerProfile.has(ctx)

    // ---------------- Fahrten ----------------

    private val delays = longArrayOf(30_000L, 120_000L, 600_000L)

    /** Eine Fahrt wurde gespeichert: sofort hochladen, bei Misserfolg bis zu dreimal später erneut. */
    fun rideSaved(ctx: Context) {
        val app = ctx.applicationContext
        if (!enabled(app)) return
        attempt(app, 0)
    }

    private fun attempt(app: Context, n: Int) {
        exec.execute {
            if (!ready(app)) return@execute
            try { TourSync.uploadPending(app) } catch (_: Exception) { }
            if (ServerProfile.pending(app) > 0 && n < delays.size) main.postDelayed({ attempt(app, n + 1) }, delays[n])
        }
    }

    // ---------------- Einstellungen ----------------

    /** Gleicher Inhalt = gleicher Text, unabhängig von der Reihenfolge. */
    fun fingerprint(ctx: Context): String {
        val d = SettingsBackup.collect(ctx)
        val sb = StringBuilder()
        for (f in d.keys().asSequence().toList().sorted()) {
            val o = d.getJSONObject(f)
            for (k in o.keys().asSequence().toList().sorted()) sb.append(f).append('/').append(k).append('=').append(o.getJSONObject(k)).append('\n')
        }
        return sb.toString().hashCode().toString() + ":" + sb.length
    }

    /** Stand nach erfolgreichem Abgleich merken (Zeit der Sicherung auf dem Server + Inhalt dieses Handys). */
    fun markSynced(ctx: Context, serverTs: Long) {
        prefs(ctx).edit().putLong("set_ts", serverTs).putString("set_fp", fingerprint(ctx)).apply()
    }

    sealed class Outcome {
        object None : Outcome()
        object Pushed : Outcome()
        object Restored : Outcome()
        class Conflict(val remote: SettingsBackup.Result) : Outcome()
    }

    /** Hochladen und danach die Serverzeit merken. Blockiert. */
    private fun push(ctx: Context): Boolean {
        if (!SettingsBackup.upload(ctx).ok) return false
        val r = SettingsBackup.fetch(ctx)
        markSynced(ctx, if (r.ok) r.whenSec else 0L)
        return true
    }

    /** Einstellungen abgleichen. Blockiert: nicht im Hauptthread aufrufen. */
    fun syncSettings(ctx: Context): Outcome {
        if (!enabled(ctx) || !ready(ctx)) return Outcome.None
        val r = SettingsBackup.fetch(ctx)
        if (!r.ok) return Outcome.None
        val p = prefs(ctx)
        val lastTs = p.getLong("set_ts", 0L); val lastFp = p.getString("set_fp", "") ?: ""
        val localChanged = fingerprint(ctx) != lastFp
        val remoteNew = r.whenSec > 0L && r.whenSec != lastTs && r.data != null
        return when {
            r.whenSec <= 0L -> { if (push(ctx)) Outcome.Pushed else Outcome.None }
            remoteNew && localChanged && lastFp.isNotEmpty() -> Outcome.Conflict(r)
            remoteNew && lastFp.isEmpty() -> Outcome.Conflict(r)   // erster Abgleich auf diesem Handy: nicht blind überschreiben
            remoteNew -> { SettingsBackup.restore(ctx, r.data!!); markSynced(ctx, r.whenSec); Outcome.Restored }
            localChanged -> { if (push(ctx)) Outcome.Pushed else Outcome.None }
            else -> Outcome.None
        }
    }

    /** Konflikt gelöst: Server übernehmen. */
    fun takeRemote(ctx: Context, r: SettingsBackup.Result) {
        r.data?.let { SettingsBackup.restore(ctx, it); markSynced(ctx, r.whenSec) }
    }

    /** Konflikt gelöst: dieses Handy gewinnt. Blockiert. */
    fun takeLocal(ctx: Context): Boolean = push(ctx)

    /** Nach dem Öffnen der Karte: Fahrten, Touren und Einstellungen abgleichen; onDone im Hauptthread. */
    fun onOpen(ctx: Context, onDone: (Outcome) -> Unit) {
        val app = ctx.applicationContext
        if (!enabled(app) || !ready(app)) return
        exec.execute {
            try { TourSync.uploadPending(app) } catch (_: Exception) { }
            try { TourSync.fetchPlanned(app) } catch (_: Exception) { }
            val o = try { syncSettings(app) } catch (_: Exception) { Outcome.None }
            main.post { onDone(o) }
        }
    }

    /** Beim Verlassen: geänderte Einstellungen still hochladen. */
    fun pushIfChanged(ctx: Context) {
        val app = ctx.applicationContext
        if (!enabled(app) || !ready(app)) return
        exec.execute {
            try {
                val p = prefs(app)
                if (p.getString("set_fp", "").isNullOrEmpty()) return@execute   // erster Abgleich läuft über syncSettings
                if (fingerprint(app) != p.getString("set_fp", "")) push(app)
            } catch (_: Exception) { }
        }
    }
}
