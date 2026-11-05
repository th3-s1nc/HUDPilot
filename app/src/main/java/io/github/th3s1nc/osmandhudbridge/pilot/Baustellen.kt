package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Baustellen und Sperrungen vom eigenen Server (ab Server v1.20, Quelle Mobilithek Bayern).
 * Die Liste wird beim Öffnen der Karte geholt (höchstens alle 6 Stunden) und für die Zeit ohne Netz gemerkt.
 */
object Baustellen {
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile var sites: List<Site> = emptyList(); private set
    /** Stand der Liste auf dem Server (Sekunden), 0 = keine Liste. */
    @Volatile var stand = 0L; private set
    /** Bezugszeit: bei Testdaten der Stand der Datei (sonst wären alle Meldungen abgelaufen), sonst jetzt. */
    @Volatile private var refSec = 0L
    @Volatile var isTest = false; private set
    /** Warum das letzte Holen scheiterte (null = kein Fehler oder noch nicht versucht). */
    @Volatile var lastError: String? = null; private set

    private fun file(ctx: Context) = File(ctx.filesDir, "baustellen.json")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("show_bs", true)
    /** Sperrungen bei der Routenberechnung umfahren (Standard: an). */
    fun avoidEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("avoid_closures", true)
    fun setAvoid(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("avoid_closures", on).apply() }

    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("show_bs", on).apply() }

    /** Zeitpunkt, zu dem die Meldungen gelten sollen (jetzt; bei Testdaten der Stand der Testdatei). */
    fun now(): Long = if (isTest && refSec > 0) refSec else System.currentTimeMillis() / 1000

    fun parse(txt: String): List<Site> {
        val j = JSONObject(txt)
        stand = j.optLong("stand", 0L); refSec = j.optLong("ref", 0L); isTest = j.optBoolean("test", false)
        val a = j.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Site>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            try {
                val p = o.getJSONArray("a"); val q = o.getJSONArray("b")
                val g = o.optJSONArray("g")
                val line: List<Pair<Double, Double>> = if (g != null && g.length() >= 2) (0 until g.length()).map { k -> val c = g.getJSONArray(k); Pair(c.getDouble(0), c.getDouble(1)) }
                    else listOf(Pair(p.getDouble(0), p.getDouble(1)), Pair(q.getDouble(0), q.getDouble(1)))
                out += Site(o.optString("i"), o.optString("r"), o.optString("t"), o.optString("k", "w"), o.optLong("s", 0L), o.optLong("e", 0L), line, approx = !(g != null && g.length() >= 2))
            } catch (_: Exception) { }
        }
        return out
    }

    /** Gemerkte Liste laden (ohne Netz). */
    fun loadCached(ctx: Context) {
        try { val f = file(ctx); if (f.isFile) sites = parse(f.readText()) } catch (_: Exception) { }
    }

    /** Vom Server holen, wenn die gemerkte Liste älter als 6 Stunden ist (oder force). onDone im Hauptthread-freien Hintergrund; der Aufrufer setzt selbst auf den UI-Thread. */
    fun refresh(ctx: Context, force: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val app = ctx.applicationContext
        exec.execute {
            var changed = false
            try {
                val base = RegionSync.serverUrl(app)
                if (base.isEmpty()) { lastError = Tx.t("Keine Server-Adresse", "No server address"); onDone(false); return@execute }
                if (!ServerProfile.has(app)) { lastError = Tx.t("Kein Profil gewählt", "No profile chosen"); onDone(false); return@execute }
                val last = prefs(app).getLong("bs_fetch", 0L)
                // Nach einem App-Update die Liste einmal neu holen (Darstellung und Server können sich geändert haben)
                val appVer = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
                val newVer = prefs(app).getString("bs_app", "") != appVer
                if (!force && !newVer && sites.isNotEmpty() && file(app).isFile && System.currentTimeMillis() - last < 6 * 3600_000L) { onDone(false); return@execute }
                val c = URL(base.trimEnd('/') + "/api/baustellen").openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 4_000; c.readTimeout = 30_000; c.setRequestProperty("User-Agent", "HUDPilot")
                    ServerProfile.apply(app, c)
                    val code = c.responseCode
                    if (code != 200) lastError = Tx.t("Server antwortet mit ", "Server answered ") + code + " (" + base.substringAfter("//").take(30) + ")"
                    if (code == 200) {
                        val txt = c.inputStream.bufferedReader().use { it.readText() }
                        val list = parse(txt)   // prüft zugleich, ob die Antwort gültig ist
                        if (list.isNotEmpty()) { file(app).writeText(txt); prefs(app).edit().putLong("bs_fetch", System.currentTimeMillis()).putString("bs_app", appVer).apply() }   // leere Liste nicht merken
                        sites = list; changed = true
                        lastError = if (list.isEmpty()) Tx.t("Server hat noch keine Baustellen", "Server has no roadworks yet") else null
                    }
                } finally { c.disconnect() }
            } catch (e: Exception) { lastError = Tx.t("Fehler: ", "Error: ") + (e.javaClass.simpleName + " " + (e.message ?: "")).trim().take(80) }
            onDone(changed)
        }
    }
}
