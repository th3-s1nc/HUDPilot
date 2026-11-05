package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Freunde und Live-Standort auf dem eigenen Server (ab Server v1.30.0). Alles blockiert: nicht im Hauptthread aufrufen. */
object FriendsApi {
    class Out(val ok: Boolean, val text: String = "")

    private class Res(val code: Int, val json: JSONObject?)

    private fun call(ctx: Context, method: String, path: String, body: JSONObject? = null): Res? {
        val base = RegionSync.serverUrl(ctx)
        if (base.isEmpty() || !ServerProfile.has(ctx)) return null
        return try {
            val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 4_000; c.readTimeout = 8_000; c.requestMethod = method
                c.setRequestProperty("User-Agent", "HUDPilot")
                ServerProfile.apply(ctx, c)
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
                ServerProfile.reachable = true
                if (code == 401) ServerProfile.authFailed = true
                Res(code, try { JSONObject(txt ?: "{}") } catch (_: Exception) { null })
            } finally { c.disconnect() }
        } catch (_: Exception) { ServerProfile.reachable = false; null }
    }

    private fun out(r: Res?): Out = when {
        r == null -> Out(false, Tx.t("Server nicht erreichbar.", "Server not reachable."))
        r.code == 404 && r.json?.optString("fehler").isNullOrEmpty() -> Out(false, Tx.t("Der Server kennt noch keine Freunde (Server v1.30 nötig).", "The server does not know friends yet (server v1.30 needed)."))
        r.code in 200..299 -> Out(true, r.json?.optString("status") ?: "")
        else -> Out(false, r.json?.optString("fehler")?.takeIf { it.isNotEmpty() } ?: Tx.t("Nicht möglich.", "Not possible."))
    }

    /** Freundesliste; null, wenn der Server nicht antwortet. */
    fun list(ctx: Context): FriendsLogic.FriendList? {
        val r = call(ctx, "GET", "/api/friends") ?: return null
        val j = r.json ?: return null
        if (r.code !in 200..299) return null
        val a = j.optJSONArray("friends") ?: return null
        val out = ArrayList<FriendsLogic.Friend>()
        for (i in 0 until a.length()) {
            val f = a.getJSONObject(i)
            out += FriendsLogic.Friend(
                f.optString("name"), f.optString("status", "friend"), f.optBoolean("i_allow"), f.optBoolean("they_allow"),
                f.optBoolean("sharing"), f.optDouble("spd", 0.0), f.optInt("age", 0)
            )
        }
        return FriendsLogic.FriendList(out, j.optInt("offen"), j.optInt("sicht"))
    }

    /** Positionen der Freunde, die dich gerade ihren Standort sehen lassen; null bei Fehler. */
    fun positions(ctx: Context): List<FriendsLogic.Pos>? {
        val r = call(ctx, "GET", "/api/friends/live") ?: return null
        if (r.code !in 200..299) return null
        val a = r.json?.optJSONArray("positions") ?: return null
        val out = ArrayList<FriendsLogic.Pos>()
        for (i in 0 until a.length()) {
            val p = a.getJSONObject(i)
            out += FriendsLogic.Pos(p.optString("name"), p.optDouble("lat"), p.optDouble("lon"), p.optDouble("spd", 0.0), p.optDouble("hdg", 0.0), p.optInt("age", 0))
        }
        return out
    }

    fun request(ctx: Context, name: String) = out(call(ctx, "POST", "/api/friends/request", JSONObject().put("name", name)))
    fun respond(ctx: Context, name: String, accept: Boolean) = out(call(ctx, "POST", "/api/friends/respond", JSONObject().put("name", name).put("accept", accept)))
    fun remove(ctx: Context, name: String) = out(call(ctx, "POST", "/api/friends/remove", JSONObject().put("name", name)))
    fun permit(ctx: Context, name: String, allow: Boolean) = out(call(ctx, "POST", "/api/friends/permit", JSONObject().put("name", name).put("allow", allow)))

    /** Eigene Position senden. Gibt zurück, für wie viele Freunde du sichtbar bist (null bei Fehler). */
    fun live(ctx: Context, lat: Double, lon: Double, kmh: Double, hdg: Double): Int? {
        val r = call(ctx, "POST", "/api/live", JSONObject().put("lat", lat).put("lon", lon).put("spd", kmh).put("hdg", hdg)) ?: return null
        return if (r.code in 200..299) r.json?.optInt("sicht", 0) ?: 0 else null
    }

    fun stop(ctx: Context) { call(ctx, "POST", "/api/live/stop", JSONObject()) }
}
