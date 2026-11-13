package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.track.RideStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Touren mit dem eigenen Server abgleichen: geplante Touren holen/sichern, gefahrene Fahrten hochladen. */
object TourSync {
    class Planned(val id: String, val name: String, val profile: String, val points: Int, val updated: Long, val km: Double? = null, val min: Int? = null)
    class Tour(val name: String, val profile: String, val points: List<Pair<Double, Double>>, val avoid: List<String>? = null, val cityw: Double? = null, val route: List<Pair<Double, Double>>? = null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)
    fun base(ctx: Context): String = RegionSync.serverUrl(ctx)

    private fun http(ctx: Context, url: String, body: String?, readMs: Int = 15_000): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4_000; c.readTimeout = readMs
            c.setRequestProperty("User-Agent", "HUDPilot")
            ServerProfile.apply(ctx, c)
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            ServerProfile.reachable = true
            if (code == 401) { ServerProfile.authFailed = true; return null }
            if (code == 200) ServerProfile.authFailed = false
            if (code != 200) return null
            return c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) { ServerProfile.reachable = false; return null } finally { c.disconnect() }
    }

    // ---------------- geplante Touren ----------------

    private fun parsePlanned(txt: String): List<Planned> {
        val a = JSONObject(txt).optJSONArray("touren") ?: return emptyList()
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Planned(o.getString("id"), o.optString("name", "Tour"), o.optString("profile"), o.optInt("points"), o.optLong("updated"),
                if (o.has("km") && !o.isNull("km")) o.optDouble("km") else null, if (o.has("min") && !o.isNull("min")) o.optInt("min") else null)
        }
    }

    /** Zuletzt geholte Liste (auch ohne Netz). */
    fun cachedPlanned(ctx: Context): List<Planned> {
        val txt = prefs(ctx).getString("tours_cache", null) ?: return emptyList()
        return try { parsePlanned(txt) } catch (_: Exception) { emptyList() }
    }

    /** Liste vom Server holen und merken. null = nicht erreichbar. */
    fun fetchPlanned(ctx: Context): List<Planned>? {
        val b = base(ctx); if (b.isEmpty()) return null
        val txt = http(ctx, "$b/api/tours", null) ?: return null
        return try { val l = parsePlanned(txt); prefs(ctx).edit().putString("tours_cache", txt).apply(); l } catch (_: Exception) { null }
    }

    fun fetchTour(ctx: Context, id: String): Tour? {
        val b = base(ctx); if (b.isEmpty()) return null
        val txt = http(ctx, "$b/api/tour?id=" + java.net.URLEncoder.encode(id, "UTF-8"), null) ?: return null
        return try {
            val j = JSONObject(txt); val a = j.getJSONArray("points")
            Tour(j.optString("name", "Tour"), j.optString("profile"), (0 until a.length()).map { val p = a.getJSONArray(it); Pair(p.getDouble(0), p.getDouble(1)) },
                j.optJSONArray("avoid")?.let { x -> (0 until x.length()).map { x.getString(it) } },
                if (j.has("cityw")) j.optDouble("cityw") else null,
                j.optJSONArray("route")?.let { r -> (0 until r.length()).map { k -> val c = r.getJSONArray(k); Pair(c.getDouble(1), c.getDouble(0)) } }?.takeIf { it.size >= 2 })   // Server liefert [lon, lat, höhe]
        } catch (_: Exception) { null }
    }

    fun saveTour(ctx: Context, name: String, profile: String, points: List<Pair<Double, Double>>): Boolean {
        val b = base(ctx); if (b.isEmpty()) return false
        val pts = JSONArray(); for (p in points) pts.put(JSONArray().put(p.first).put(p.second))
        val avs = PilotSession.avoid(ctx)
        val av = JSONArray(); for (k in avs.on) av.put(k)
        val body = JSONObject().put("name", name).put("profile", profile).put("avoid", av).put("cityw", avs.cityw).put("points", pts).toString()
        return http(ctx, "$b/api/tour/save", body) != null
    }

    // ---------------- gefahrene Fahrten ----------------

    fun uploadedIds(ctx: Context): Set<String> = prefs(ctx).getStringSet("rides_uploaded", emptySet()) ?: emptySet()

    /** Eine Fahrt (alle aufgezeichneten Werte) hochladen. */
    fun uploadRide(ctx: Context, id: String): Boolean {
        val b = base(ctx); if (b.isEmpty()) return false
        val ride = RideStore.find(ctx, id) ?: return false
        val f = RideStore.ptsFile(ctx, id); if (!f.isFile) return false
        val body = JSONObject().put("id", id).put("name", ride.name).put("kind", ride.kind).put("pts", f.readText()).toString()
        if (http(ctx, "$b/api/ride/upload", body, 90_000) == null) return false
        prefs(ctx).edit().putStringSet("rides_uploaded", HashSet(uploadedIds(ctx)) + id).apply()
        return true
    }

    /** Alle noch nicht hochgeladenen Fahrten. Gibt die Anzahl der erfolgreichen zurück. */
    fun uploadPending(ctx: Context): Int {
        if (base(ctx).isEmpty()) return 0
        val done = uploadedIds(ctx); var n = 0
        for (r in RideStore.list(ctx)) {
            if (r.id in done) continue
            if (uploadRide(ctx, r.id)) n++ else break   // Server nicht erreichbar: nicht jede Fahrt einzeln versuchen
        }
        return n
    }

}
