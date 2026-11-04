package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.limit.TileDownloader
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Online-Suche nach Adressen, Orten und Lokalen (Nominatim) sowie nach Kategorien in der Nähe (Overpass). Im Hintergrund-Thread aufrufen. */
object PlaceSearch {
    class Hit(val name: String, val detail: String, val lat: Double, val lon: Double, var distM: Double = 0.0) {
        /** Name für die Punkteliste: mit erstem Teil der Ortsangabe, z. B. "Hauptstraße 27, Ort". */
        val label: String get() = if (detail.isBlank()) name else name + ", " + detail.substringBefore(',').trim()
    }

    enum class Cat(val de: String, val en: String, val filter: String) {
        FUEL("⛽ Tanken", "⛽ Fuel", "[\"amenity\"=\"fuel\"]"),
        FOOD("🍴 Essen & Trinken", "🍴 Food & drink", "[\"amenity\"~\"^(restaurant|cafe|pub|biergarten|fast_food|bar)$\"]"),
        HOTEL("🏨 Hotel", "🏨 Hotel", "[\"tourism\"~\"^(hotel|guest_house|hostel|motel)$\"]"),
        PARKING("🅿 Parken", "🅿 Parking", "[\"amenity\"=\"parking\"][\"access\"!~\"^(private|customers)$\"]");

        fun label() = Tx.t(de, en)

        /** Die "kind"-Werte dieser Kategorie in der POI-Ebene der Protomaps-Karte. */
        fun poiKinds(): Set<String> = when (this) {
            FUEL -> setOf("fuel")
            FOOD -> setOf("restaurant", "cafe", "pub", "biergarten", "fast_food", "bar", "food_court")
            HOTEL -> setOf("hotel", "guest_house", "hostel", "motel")
            PARKING -> setOf("parking")
        }
    }

    private const val UA = "HUDPilot/0.8 (+https://github.com/th3-s1nc/OSMAnd-HUD-Bridge; private use)"

    /** Freitext: Adresse, Ort oder Name eines Lokals. Treffer in der Nähe von ([lat], [lon]) werden bevorzugt. */
    fun searchText(query: String, lat: Double, lon: Double): List<Hit> {
        val vb = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", lon - 0.6, lat + 0.4, lon + 0.6, lat - 0.4)
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=10&accept-language=" + (if (Tx.en) "en" else "de") +
            "&viewbox=" + vb + "&q=" + URLEncoder.encode(query, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", UA)
        if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
        val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        val out = ArrayList<Hit>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val display = o.optString("display_name")
            val raw = o.optString("name")
            val name = PlaceFormat.nameOf(raw, display)
            val detail = PlaceFormat.detailOf(raw, display)
            out += Hit(name, detail, o.getString("lat").toDouble(), o.getString("lon").toDouble())
        }
        return sorted(out, lat, lon)
    }

    /** Rechteck der Karte (Grad). */
    class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
        val centerLat get() = (south + north) / 2
        val centerLon get() = (west + east) / 2
        /** Ausdehnung in km (Breite, Höhe). */
        val widthKm get() = (east - west) * 111.32 * Math.cos(Math.toRadians(centerLat))
        val heightKm get() = (north - south) * 111.32
    }

    /** Größer als das darf der Ausschnitt nicht sein, sonst sind es zu viele Treffer für die freien Server. */
    const val MAX_BOX_KM = 60.0

    /** Orte einer Kategorie im sichtbaren Kartenausschnitt, sortiert nach Abstand zu ([lat], [lon]). Fragt zwei Server gleichzeitig, höchstens etwa 20 s. */
    fun searchCategory(cat: Cat, box: Box, lat: Double, lon: Double): List<Hit> {
        val q = String.format(Locale.US, "[out:json][timeout:15];nwr%s(%.5f,%.5f,%.5f,%.5f);out center 80;", cat.filter, box.south, box.west, box.north, box.east)
        val o = JSONObject(raceTwo(q))
        return parseCategory(cat, o, lat, lon)
    }

    private fun raceTwo(q: String): String {
        val now = System.currentTimeMillis()
        val a = TileDownloader.pool.next(now)
        var b = TileDownloader.pool.next(now)
        if (b == a) b = TileDownloader.ENDPOINTS.firstOrNull { it != a } ?: a
        val ex = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs = listOf(a, b).distinct().map { ep ->
                java.util.concurrent.Callable {
                    try {
                        val t = TileDownloader.downloadOne(ep, q, null, 16_000)
                        TileDownloader.pool.ok(ep)
                        t
                    } catch (e: Exception) {
                        TileDownloader.pool.fail(ep, System.currentTimeMillis(), TileDownloader.pauseFor(e))
                        throw IllegalStateException(TileDownloader.hostOf(ep) + ": " + (e.message ?: e.javaClass.simpleName))
                    }
                }
            }
            return try {
                ex.invokeAny(jobs, 20, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                throw IllegalStateException("Zeitüberschreitung")
            } catch (e: java.util.concurrent.ExecutionException) {
                throw IllegalStateException(e.cause?.message ?: "keine Antwort")
            }
        } finally {
            ex.shutdownNow()
        }
    }

    fun parseCategory(cat: Cat, o: JSONObject, lat: Double, lon: Double): List<Hit> {
        val els = o.optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Hit>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val center = e.optJSONObject("center")
            val la = if (e.has("lat")) e.getDouble("lat") else center?.optDouble("lat") ?: continue
            val lo = if (e.has("lon")) e.getDouble("lon") else center?.optDouble("lon") ?: continue
            val name = tags.optString("name").ifBlank { tags.optString("brand") }.ifBlank { cat.label().substringAfter(' ') }
            val street = listOf(tags.optString("addr:street"), tags.optString("addr:housenumber")).filter { it.isNotBlank() }.joinToString(" ")
            val place = tags.optString("addr:city")
            out += Hit(name, listOf(street, place).filter { it.isNotBlank() }.joinToString(", "), la, lo)
        }
        return sorted(out, lat, lon).take(25)
    }

    fun sorted(hits: List<Hit>, lat: Double, lon: Double): List<Hit> {
        for (h in hits) h.distM = Plan.distM(lat, lon, h.lat, h.lon)
        return hits.sortedBy { it.distM }
    }
}
