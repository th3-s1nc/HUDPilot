package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Admin-Meldungen vom eigenen Server (ab Server v1.33.0): nur für Profile, die der Admin im Webserver dafür bestimmt hat.
 * Der Server prüft die Berechtigung selbst; die App merkt sich nur, ob sie "erlaubt" ist. Alles blockiert: nicht im Hauptthread aufrufen.
 */
object AdminNotes {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)

    fun allowed(ctx: Context): Boolean = prefs(ctx).getBoolean("an_allowed", false)

    fun events(ctx: Context): List<AdminNotesLogic.Ev> {
        val txt = prefs(ctx).getString("an_events", "") ?: ""
        if (txt.isEmpty()) return emptyList()
        return try { parse(JSONArray(txt)) } catch (_: Exception) { emptyList() }
    }

    /** Zuletzt vom Server geholte Status-Kacheln (für die Meldungsseite). */
    fun status(ctx: Context): List<AdminNotesLogic.Status> {
        val txt = prefs(ctx).getString("an_status", "") ?: ""
        if (txt.isEmpty()) return emptyList()
        return try { parseStatus(JSONArray(txt)) } catch (_: Exception) { emptyList() }
    }

    private fun parseStatus(a: JSONArray): List<AdminNotesLogic.Status> {
        val out = ArrayList<AdminNotesLogic.Status>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += AdminNotesLogic.Status(o.optString("key"), o.optString("label"), o.optString("wert"), o.optString("info"), o.optString("stufe", "ok"), if (o.has("pct")) o.optInt("pct", -1) else -1)
        }
        return out
    }

    /** Nummer der zuletzt gelesenen Meldung. */
    fun seenId(ctx: Context): Int = prefs(ctx).getInt("an_seen", 0)

    fun unread(ctx: Context): Int = if (!allowed(ctx)) 0 else AdminNotesLogic.unread(events(ctx), prefs(ctx).getInt("an_seen", 0))

    /** Alle als gelesen markieren (Liste wurde geöffnet). */
    fun markRead(ctx: Context) {
        prefs(ctx).edit().putInt("an_seen", AdminNotesLogic.lastId(events(ctx))).apply()
    }

    private fun parse(a: JSONArray): List<AdminNotesLogic.Ev> {
        val out = ArrayList<AdminNotesLogic.Ev>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += AdminNotesLogic.Ev(o.optInt("id"), o.optLong("ts"), o.optString("typ"), o.optString("text"))
        }
        return out
    }

    /**
     * Beim Server nachfragen und merken. Gibt die Meldungen zurück, die neu angekündigt werden sollen (leer, wenn nichts Neues,
     * nicht erlaubt oder der Server nicht antwortet). Beim allerersten Mal wird der vorhandene Verlauf nicht angekündigt.
     */
    fun checkAndAnnounce(ctx: Context): List<AdminNotesLogic.Ev> {
        val app = ctx.applicationContext
        val base = RegionSync.serverUrl(app)
        if (base.isEmpty() || !ServerProfile.has(app)) return emptyList()
        val p = prefs(app)
        val old = events(app)
        return try {
            val c = URL(base.trimEnd('/') + "/api/notify?since=" + AdminNotesLogic.lastId(old)).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 4_000; c.readTimeout = 8_000; c.setRequestProperty("User-Agent", "HUDPilot")
                ServerProfile.apply(app, c)
                val code = c.responseCode
                if (code == 404) { p.edit().putBoolean("an_allowed", false).remove("an_events").apply(); return emptyList() }   // älterer Server
                if (code !in 200..299) return emptyList()
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                if (!j.optBoolean("erlaubt", false)) {
                    p.edit().putBoolean("an_allowed", false).remove("an_events").remove("an_status").remove("an_seen").remove("an_announced").apply()
                    return emptyList()
                }
                val merged = AdminNotesLogic.merge(old, parse(j.optJSONArray("events") ?: JSONArray()))
                val first = !p.contains("an_announced")
                val ann = p.getInt("an_announced", 0)
                val fresh = if (first) emptyList() else AdminNotesLogic.toAnnounce(merged, ann)
                val ed = p.edit().putBoolean("an_allowed", true).putString("an_status", (j.optJSONArray("status") ?: JSONArray()).toString()).putString("an_events", JSONArray().also { a ->
                    for (e in merged) a.put(JSONObject().put("id", e.id).put("ts", e.ts).put("typ", e.typ).put("text", e.text))
                }.toString()).putInt("an_announced", maxOf(AdminNotesLogic.lastId(merged), j.optInt("letzte", 0)))
                if (first) ed.putInt("an_seen", AdminNotesLogic.lastId(merged))   // alter Verlauf zählt nicht als ungelesen
                ed.apply()
                fresh
            } finally { c.disconnect() }
        } catch (_: Exception) { emptyList() }
    }
}
