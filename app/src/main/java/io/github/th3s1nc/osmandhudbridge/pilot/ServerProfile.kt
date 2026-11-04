package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.track.RideStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Profil auf dem eigenen Server (ab Server v1.11): jede Person hat Name und Code, Touren und Fahrten gehören zum Profil.
 * Neue Profile entstehen mit dem Einladungscode, den der Server-Betreiber in der Admin-Oberfläche festlegt.
 */
object ServerProfile {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)

    fun name(ctx: Context): String = prefs(ctx).getString("prof_name", "") ?: ""
    fun code(ctx: Context): String = prefs(ctx).getString("prof_code", "") ?: ""
    fun has(ctx: Context): Boolean = name(ctx).isNotEmpty() && code(ctx).isNotEmpty()
    fun set(ctx: Context, name: String, code: String) {
        prefs(ctx).edit().putString("prof_name", name).putString("prof_code", code.trim().uppercase())
            .remove("tours_cache").remove("rides_uploaded").remove("set_ts").remove("set_fp").putLong("outbox_ts", 0L).apply()
        authFailed = false
    }
    fun clear(ctx: Context) { prefs(ctx).edit().remove("prof_name").remove("prof_code").apply(); authFailed = false }

    /** Zuletzt erreichbar? null = noch nicht versucht. */
    @Volatile var reachable: Boolean? = null
    /** Der Server hat Name/Code abgelehnt (401). */
    @Volatile var authFailed = false

    /** Kopfzeilen für Anfragen, die zum Profil gehören (Touren, Fahrten, Ablage). */
    fun apply(ctx: Context, c: HttpURLConnection) {
        DeviceId.apply(c)
        if (!has(ctx)) return
        c.setRequestProperty("X-Profile", URLEncoder.encode(name(ctx), "UTF-8").replace("+", "%20"))
        c.setRequestProperty("X-Code", code(ctx))
    }

    class Result(val ok: Boolean, val text: String, val code: String = "")

    private fun post(base: String, path: String, body: JSONObject): Pair<Int, JSONObject?> {
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 4_000; c.readTimeout = 10_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("User-Agent", "HUDPilot")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
            Pair(code, try { JSONObject(txt ?: "{}") } catch (_: Exception) { null })
        } finally { c.disconnect() }
    }

    /** Neues Profil anlegen (Name + Einladungscode). Blockiert: nicht im Hauptthread aufrufen. */
    fun create(ctx: Context, name: String, invite: String): Result {
        val base = RegionSync.serverUrl(ctx)
        if (base.isEmpty()) return Result(false, Tx.t("Erst die Serveradresse eintragen (Menü → Server).", "Enter the server address first (menu → server)."))
        return try {
            val (code, j) = post(base, "/api/profile/create", JSONObject().put("name", name.trim()).put("invite", invite.trim()))
            reachable = true
            if (code == 200 && j != null && j.optBoolean("ok")) {
                set(ctx, j.optString("name", name.trim()), j.optString("code"))
                Result(true, "", j.optString("code"))
            } else if (code == 404) Result(false, Tx.t("Der Server kennt noch keine Profile (Server v1.11 nötig).", "The server does not know profiles yet (server v1.11 needed)."))
            else Result(false, j?.optString("fehler")?.takeIf { it.isNotEmpty() } ?: Tx.t("Anlegen nicht möglich.", "Could not create."))
        } catch (_: Exception) { reachable = false; Result(false, Tx.t("Server nicht erreichbar.", "Server not reachable.")) }
    }

    /** Mit vorhandenem Profil anmelden (Name + Code). */
    fun login(ctx: Context, name: String, code: String): Result {
        val base = RegionSync.serverUrl(ctx)
        if (base.isEmpty()) return Result(false, Tx.t("Erst die Serveradresse eintragen (Menü → Server).", "Enter the server address first (menu → server)."))
        return try {
            val c = URL(base.trimEnd('/') + "/api/me").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 4_000; c.readTimeout = 10_000; c.setRequestProperty("User-Agent", "HUDPilot")
                c.setRequestProperty("X-Profile", URLEncoder.encode(name.trim(), "UTF-8").replace("+", "%20")); c.setRequestProperty("X-Code", code.trim().uppercase()); DeviceId.apply(c)
                reachable = true
                when (c.responseCode) {
                    200 -> {
                        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                        set(ctx, j.optString("name", name.trim()), code); Result(true, "")
                    }
                    401 -> Result(false, Tx.t("Name oder Code stimmt nicht.", "Name or code is wrong."))
                    404 -> Result(false, Tx.t("Der Server kennt noch keine Profile (Server v1.11 nötig).", "The server does not know profiles yet (server v1.11 needed)."))
                    else -> Result(false, Tx.t("Anmelden nicht möglich.", "Could not sign in."))
                }
            } finally { c.disconnect() }
        } catch (_: Exception) { reachable = false; Result(false, Tx.t("Server nicht erreichbar.", "Server not reachable.")) }
    }

    /** Anzahl Fahrten, die noch nicht auf dem Server sind. */
    fun pending(ctx: Context): Int {
        val done = TourSync.uploadedIds(ctx)
        return RideStore.list(ctx).count { it.id !in done }
    }

    /** Kurze Statuszeile für das Menü. */
    fun statusLine(ctx: Context): String {
        if (RegionSync.serverUrl(ctx).isEmpty()) return Tx.t("✕ Kein Server", "✕ No server")
        if (!has(ctx) || authFailed) return Tx.t("✕ Kein Profil", "✕ No profile")
        if (reachable == false) return Tx.t("✕ Nicht erreichbar", "✕ Not reachable")
        val n = pending(ctx)
        if (n > 0) return Tx.t("⟳ $n offen", "⟳ $n pending")
        val away = if (RegionSync.isAway(ctx)) Tx.t(" (unterwegs)", " (away)") else ""
        return if (reachable == true) Tx.t("✓ Synchron", "✓ In sync") + away else name(ctx) + away
    }

    /** Jetzt abgleichen: offene Fahrten hochladen, Tourenliste holen. Blockiert. */
    fun syncNow(ctx: Context): String {
        if (RegionSync.serverUrl(ctx).isEmpty() || !has(ctx)) return statusLine(ctx)
        try { TourSync.uploadPending(ctx) } catch (_: Exception) { }
        try { TourSync.fetchPlanned(ctx) } catch (_: Exception) { }
        return statusLine(ctx)
    }
}
