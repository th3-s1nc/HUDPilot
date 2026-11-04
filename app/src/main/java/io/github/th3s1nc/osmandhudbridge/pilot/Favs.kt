package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Zuhause und Favoriten-Adressen. Liegen auf dem Handy und gleichen sich mit dem Profil auf dem Server ab (ab Server v1.16).
 * Zuhause ist ein Favorit mit der festen Kennung "home". Gelöschte Einträge bleiben als Marke (del), damit das Löschen
 * auch ein zweites Handy erreicht; beim Abgleich gewinnt je Eintrag die jüngere Änderung (ts, Sekunden).
 */
object Favs {
    const val HOME = "home"

    class Fav(val id: String, val name: String, val lat: Double, val lon: Double, val home: Boolean, val ts: Long, val del: Boolean) {
        fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("lat", lat).put("lon", lon).put("home", home).put("ts", ts).put("del", del)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_favs", Context.MODE_PRIVATE)
    private fun now() = System.currentTimeMillis() / 1000

    private fun parse(txt: String?): MutableList<Fav> {
        val out = ArrayList<Fav>()
        try {
            val a = JSONArray(txt ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += Fav(o.getString("id"), o.optString("name"), o.getDouble("lat"), o.getDouble("lon"), o.optBoolean("home"), o.optLong("ts"), o.optBoolean("del"))
            }
        } catch (_: Exception) { }
        return out
    }

    private fun load(ctx: Context): MutableList<Fav> = parse(prefs(ctx).getString("list", "[]"))
    private fun save(ctx: Context, l: List<Fav>) {
        val a = JSONArray(); l.forEach { a.put(it.toJson()) }
        prefs(ctx).edit().putString("list", a.toString()).apply()
    }

    /** Sichtbare Favoriten: Zuhause zuerst, dann nach Name. */
    @Synchronized fun live(ctx: Context): List<Fav> =
        load(ctx).filter { !it.del }.sortedWith(compareBy({ if (it.id == HOME) 0 else 1 }, { it.name.lowercase() }))

    @Synchronized fun home(ctx: Context): Fav? = live(ctx).firstOrNull { it.id == HOME }

    @Synchronized private fun put(ctx: Context, f: Fav) {
        val l = load(ctx); l.removeAll { it.id == f.id }; l.add(f); save(ctx, l); syncAsync(ctx)
    }

    @Synchronized fun setHome(ctx: Context, name: String, lat: Double, lon: Double) =
        put(ctx, Fav(HOME, name.ifBlank { "Zuhause" }, lat, lon, true, now(), false))

    @Synchronized fun add(ctx: Context, name: String, lat: Double, lon: Double) =
        put(ctx, Fav(java.util.UUID.randomUUID().toString().take(12), name.ifBlank { "Favorit" }, lat, lon, false, now(), false))

    @Synchronized fun rename(ctx: Context, id: String, name: String) {
        val f = load(ctx).firstOrNull { it.id == id && !it.del } ?: return
        put(ctx, Fav(f.id, name.ifBlank { f.name }, f.lat, f.lon, f.home, now(), false))
    }

    @Synchronized fun remove(ctx: Context, id: String) {
        val f = load(ctx).firstOrNull { it.id == id } ?: return
        put(ctx, Fav(f.id, f.name, f.lat, f.lon, f.home, now(), true))
    }

    /** Ein Favorit an (fast) derselben Stelle (etwa 15 m). */
    @Synchronized fun at(ctx: Context, lat: Double, lon: Double): Fav? =
        live(ctx).firstOrNull { Math.abs(it.lat - lat) < 0.00015 && Math.abs(it.lon - lon) < 0.0002 }

    // ---------------- Abgleich mit dem Server ----------------

    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var lastSync = 0L

    /** Im Hintergrund abgleichen (nach jeder Änderung, beim Anmelden und beim Öffnen der App). */
    fun syncAsync(ctx: Context, minGapMs: Long = 0L) {
        val app = ctx.applicationContext
        if (minGapMs > 0 && android.os.SystemClock.elapsedRealtime() - lastSync < minGapMs) return
        exec.execute { try { sync(app) } catch (_: Exception) { } }
    }

    /** Blockiert: lokale Liste hochschicken, die zusammengeführte Liste zurücknehmen. */
    fun sync(ctx: Context): Boolean {
        if (RegionSync.serverUrl(ctx).isEmpty() || !ServerProfile.has(ctx)) return false
        lastSync = android.os.SystemClock.elapsedRealtime()
        return try {
            val local = synchronized(this) { load(ctx) }
            val a = JSONArray(); local.forEach { a.put(it.toJson()) }
            val (code, txt) = SettingsBackup.call(ctx, "/api/favs/save", JSONObject().put("favs", a))
            if (code != 200 || txt == null) return false
            val merged = parse(JSONObject(txt).optJSONArray("favs")?.toString())
            synchronized(this) {
                // Änderungen, die seit dem Hochschicken lokal dazugekommen sind, nicht verlieren
                val now = load(ctx).associateBy { it.id }
                val out = merged.associateBy { it.id }.toMutableMap()
                for ((id, f) in now) { val m = out[id]; if (m == null || f.ts > m.ts) out[id] = f }
                save(ctx, out.values.toList())
            }
            true
        } catch (_: Exception) { false }
    }
}
