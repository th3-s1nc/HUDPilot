package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Einstellungen pro Profil auf dem eigenen Server sichern und auf einem anderen Handy wieder holen (ab Server v1.12).
 * Nicht gesichert: Serveradresse, Profil-Code, Bluetooth-Adresse des HUD, geladene Karten/Regionen, Import-Stand,
 * Berechtigungs-Merker und flüchtige Zustände. Nichts wird von selbst hochgeladen.
 */
object SettingsBackup {
    private val FILES = listOf("pilot_ui", "pilot_voice", "bridge")

    /** Schlüssel, die nie gesichert oder überschrieben werden (je Handy verschieden oder nur technischer Zustand). */
    private val SKIP_EXACT = setOf(
        "hud_address", "hud_name", "bridge_enabled", "hud_on", "addr_manual",
        "notif_asked", "rec_nav_start", "demo_done", "page", "cam_card_open", "tours_cache"
    )
    private val SKIP_PREFIX = listOf("import_", "region_", "dl_", "cam_list_")

    private fun skip(key: String) = key in SKIP_EXACT || SKIP_PREFIX.any { key.startsWith(it) }

    class Result(val ok: Boolean, val text: String, val whenSec: Long = 0, val device: String = "", val data: JSONObject? = null)

    /** Alle sicherbaren Einstellungen als JSON: {datei: {schlüssel: {t: Typ, v: Wert}}}. */
    fun collect(ctx: Context): JSONObject {
        val out = JSONObject()
        for (f in FILES) {
            val o = JSONObject()
            for ((k, v) in ctx.getSharedPreferences(f, Context.MODE_PRIVATE).all) {
                if (skip(k) || v == null) continue
                val e = JSONObject()
                when (v) {
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is String -> e.put("t", "s").put("v", v)
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                o.put(k, e)
            }
            out.put(f, o)
        }
        return out
    }

    /** Gesicherte Einstellungen einspielen: die sicherbaren Schlüssel werden ersetzt, ausgenommene bleiben unberührt. */
    fun restore(ctx: Context, data: JSONObject) {
        for (f in FILES) {
            val src = data.optJSONObject(f) ?: continue
            val sp = ctx.getSharedPreferences(f, Context.MODE_PRIVATE)
            val ed = sp.edit()
            for (k in sp.all.keys) if (!skip(k)) ed.remove(k)
            val it = src.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (skip(k)) continue
                val e = src.optJSONObject(k) ?: continue
                try {
                    when (e.optString("t")) {
                        "b" -> ed.putBoolean(k, e.getBoolean("v"))
                        "i" -> ed.putInt(k, e.getInt("v"))
                        "l" -> ed.putLong(k, e.getLong("v"))
                        "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                        "s" -> ed.putString(k, e.getString("v"))
                        "ss" -> { val a = e.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { i -> a.getString(i) }.toSet()) }
                    }
                } catch (_: Exception) { }
            }
            ed.apply()
        }
        // Gespeicherte Stimme sofort wirksam machen
        try { PilotVoice.setVoiceName(ctx, PilotVoice.voiceName(ctx)) } catch (_: Exception) { }
    }

    internal fun call(ctx: Context, path: String, body: JSONObject?): Pair<Int, String?> {
        val base = RegionSync.serverUrl(ctx)
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 4_000; c.readTimeout = 15_000; c.setRequestProperty("User-Agent", "HUDPilot")
            ServerProfile.apply(ctx, c)
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            ServerProfile.reachable = true
            Pair(code, (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() })
        } finally { c.disconnect() }
    }

    private fun problem(code: Int): Result = when (code) {
        401 -> { ServerProfile.authFailed = true; Result(false, Tx.t("Profil wurde vom Server abgelehnt.", "The server rejected the profile.")) }
        404 -> Result(false, Tx.t("Der Server kann das noch nicht (Server v1.12 nötig).", "The server cannot do this yet (server v1.12 needed)."))
        else -> Result(false, Tx.t("Server-Fehler ($code).", "Server error ($code)."))
    }

    private fun noProfile(ctx: Context): Result? =
        if (RegionSync.serverUrl(ctx).isEmpty() || !ServerProfile.has(ctx)) Result(false, Tx.t("Erst auf dem Server anmelden.", "Sign in on the server first.")) else null

    /** Einstellungen auf den Server sichern. Blockiert: nicht im Hauptthread aufrufen. */
    fun upload(ctx: Context): Result {
        noProfile(ctx)?.let { return it }
        return try {
            val dev = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim()
            val (code, _) = call(ctx, "/api/settings/save", JSONObject().put("device", dev).put("data", collect(ctx)))
            if (code == 200) Result(true, Tx.t("Einstellungen gesichert.", "Settings saved.")) else problem(code)
        } catch (_: Exception) { ServerProfile.reachable = false; Result(false, Tx.t("Server nicht erreichbar.", "Server not reachable.")) }
    }

    /** Gesicherte Einstellungen vom Server lesen (noch nicht einspielen). whenSec = 0: es gibt keine Sicherung. Blockiert. */
    fun fetch(ctx: Context): Result {
        noProfile(ctx)?.let { return it }
        return try {
            val (code, txt) = call(ctx, "/api/settings", null)
            if (code != 200 || txt == null) return problem(code)
            val j = JSONObject(txt)
            Result(true, "", j.optLong("ts", 0L), j.optString("device"), j.optJSONObject("data"))
        } catch (_: Exception) { ServerProfile.reachable = false; Result(false, Tx.t("Server nicht erreichbar.", "Server not reachable.")) }
    }

    /** Zeit und Handy der Sicherung als kurzer Text. */
    fun describe(r: Result): String {
        val d = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(r.whenSec * 1000))
        return d + if (r.device.isNotBlank()) " (${r.device})" else ""
    }
}
