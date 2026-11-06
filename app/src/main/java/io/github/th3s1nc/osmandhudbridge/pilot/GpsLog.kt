package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.io.File

/**
 * GPS-Log zur Fehlersuche (Werkzeuge): schreibt etwa jede Sekunde eine Zeile (Position, Genauigkeit, Tempo, Richtung, Pfeil, Einrasten)
 * und Ereignisse (Abweichung, Neuberechnung) in eine Datei nur auf diesem Handy. Standard: aus. Nichts wird automatisch gesendet,
 * die Datei wird nur auf "Teilen" weitergegeben. Begrenzt auf etwa 5 MB, nach 7 Tagen wird sie gelöscht.
 */
object GpsLog {
    private const val PREFS = "gpslog"
    private const val KEY_ON = "on"
    private const val KEY_SINCE = "since"
    private const val FILE = "gpslog.csv"
    private const val FLUSH_MS = 5_000L

    private val lock = Any()
    private val buf = StringBuilder()
    private var lastFlush = 0L
    @Volatile private var on = false
    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    /** Läuft die Aufzeichnung? (Beim ersten Aufruf wird auch ein abgelaufenes Log aufgeräumt.) */
    fun enabled(ctx: Context): Boolean {
        if (!loaded) {
            synchronized(lock) {
                if (!loaded) {
                    val p = prefs(ctx)
                    on = p.getBoolean(KEY_ON, false)
                    if (GpsLogLogic.expired(p.getLong(KEY_SINCE, 0L), System.currentTimeMillis())) {
                        file(ctx).delete()
                        p.edit().putLong(KEY_SINCE, if (on) System.currentTimeMillis() else 0L).apply()
                    }
                    loaded = true
                }
            }
        }
        return on
    }

    fun setEnabled(ctx: Context, v: Boolean) {
        enabled(ctx)
        synchronized(lock) {
            if (!v) flushLocked(ctx)
            on = v
            val e = prefs(ctx).edit().putBoolean(KEY_ON, v)
            if (v && prefs(ctx).getLong(KEY_SINCE, 0L) == 0L) e.putLong(KEY_SINCE, System.currentTimeMillis())
            e.apply()
        }
        if (v) event(ctx, Tx.t("GPS-Log gestartet", "GPS log started"))
    }

    fun fix(
        ctx: Context, mode: String, lat: Double, lon: Double, accuracyM: Float?, kmh: Float?, gpsBearing: Float?, usedHeading: Double?,
        shownLat: Double?, shownLon: Double?, snapped: Boolean, still: Boolean, offRouteM: Int?
    ) {
        if (!enabled(ctx)) return
        add(ctx, GpsLogLogic.row(System.currentTimeMillis(), mode, lat, lon, accuracyM, kmh, gpsBearing, usedHeading, shownLat, shownLon, snapped, still, offRouteM))
    }

    fun event(ctx: Context, text: String) {
        if (!enabled(ctx)) return
        add(ctx, GpsLogLogic.eventRow(System.currentTimeMillis(), text))
    }

    private fun add(ctx: Context, line: String) {
        synchronized(lock) {
            buf.append(line).append('\n')
            val now = SystemClock.elapsedRealtime()
            if (now - lastFlush >= FLUSH_MS) flushLocked(ctx)
        }
    }

    private fun flushLocked(ctx: Context) {
        lastFlush = SystemClock.elapsedRealtime()
        if (buf.isEmpty()) return
        try {
            val f = file(ctx)
            if (!f.exists() || f.length() == 0L) f.writeText(GpsLogLogic.HEADER + "\n")
            f.appendText(buf.toString())
            if (f.length() > GpsLogLogic.MAX_BYTES) f.writeText(GpsLogLogic.trim(f.readText()))
        } catch (_: Exception) {
        }
        buf.setLength(0)
    }

    /** Schreibt alles, was noch im Speicher liegt (vor Teilen, Anzeigen und beim Beenden). */
    fun flush(ctx: Context) { synchronized(lock) { if (loaded) flushLocked(ctx) } }

    fun clear(ctx: Context) {
        enabled(ctx)
        synchronized(lock) {
            buf.setLength(0)
            file(ctx).delete()
            prefs(ctx).edit().putLong(KEY_SINCE, if (on) System.currentTimeMillis() else 0L).apply()
        }
    }

    /** Zeile für die Anzeige: Größe und Beginn. */
    fun info(ctx: Context): String {
        enabled(ctx)
        val f = file(ctx)
        val since = prefs(ctx).getLong(KEY_SINCE, 0L)
        val size = if (f.exists()) f.length() else 0L
        val sinceText = if (since > 0) {
            val fmt = java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY)
            Tx.t("seit ", "since ") + fmt.format(java.util.Date(since))
        } else ""
        val state = if (on) Tx.t("läuft", "running") else Tx.t("aus", "off")
        return listOf(state, GpsLogLogic.sizeText(size), sinceText).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** Teilen-Fenster von Android. Gibt false zurück, wenn es noch nichts zu teilen gibt. */
    fun share(ctx: Context): Boolean {
        flush(ctx)
        val f = file(ctx)
        if (!f.exists() || f.length() <= GpsLogLogic.HEADER.length + 1) return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".logs", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, Tx.t("GPS-Log teilen", "Share GPS log"))
        if (ctx !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
        return true
    }
}
