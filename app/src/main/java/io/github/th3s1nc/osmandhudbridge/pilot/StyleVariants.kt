package io.github.th3s1nc.osmandhudbridge.pilot

import org.json.JSONArray
import org.json.JSONObject

/**
 * Abwandlungen des Kartenstils nach dem Zusammenfügen der Regionen:
 * - Farbwelt: "standard" (Protomaps-Farben), "vivid" (kräftig) oder "contrast" (Kontrast zum Fahren), je für Tag und Nacht,
 * - "ruhig": lässt Nebenbeschriftungen weg,
 * - "3D-Gebäude": ersetzt die flache Gebäudeebene durch Kästen (Höhe aus den Kartendaten, unbekannt = 6 m, mal 0,6, höchstens 40 m).
 * Wirkt auf alle Kopien einer Ebene (Ebenen-Namen mit "~Nummer" bei mehreren Regionen).
 */
object StyleVariants {
    private val CALM_REMOVE = setOf(
        "address_label", "pois", "roads_shields", "roads_labels_minor", "water_waterway_label", "earth_label_islands", "places_subplace"
    )

    const val STANDARD = "standard"
    const val VIVID = "vivid"
    const val CONTRAST = "contrast"

    private class Pal(
        val earth: String, val green: String, val park: String, val water: String, val building: String, val roof: String,
        val industry: String, val minor: String, val minorCase: String, val link: String, val major: String, val majorCase: String,
        val highway: String, val highwayCase: String, val tunnel: String, val tunnelCase: String,
        val text: String, val halo: String, val place: String, val widen: Double
    )

    private fun palette(theme: String, dark: Boolean): Pal? = when {
        theme == VIVID && !dark -> Pal("#efe9dc", "#cde5b1", "#b3de9d", "#5bb8f0", "#d8cfc0", "#e8dfce", "#e6e2d8", "#ffffff", "#c9bfae",
            "#ffe08a", "#ffd36b", "#d9a93c", "#ffa733", "#d9822b", "#e6dccb", "#cfc3ae", "#5b4a2e", "#ffffff", "#222222", 1.0)
        theme == VIVID && dark -> Pal("#1b2026", "#1f3a2a", "#22442f", "#1d4e7a", "#2b3138", "#4a525c", "#23282e", "#4a525c", "#14181c",
            "#a8792f", "#c98a2e", "#1a1208", "#e07b28", "#1a1208", "#2a3037", "#14181c", "#c9cdd2", "#14181c", "#e8eaed", 1.0)
        theme == CONTRAST && !dark -> Pal("#ffffff", "#e6f0e0", "#d8ead0", "#9ad0f5", "#d0d0d0", "#e6e6e6", "#f0f0f0", "#ffffff", "#555555",
            "#ffe680", "#ffd400", "#1a1a1a", "#ff8c00", "#1a1a1a", "#eeeeee", "#9a9a9a", "#000000", "#ffffff", "#000000", 1.3)
        theme == CONTRAST && dark -> Pal("#000000", "#0a2a14", "#0c3318", "#0b3a66", "#202020", "#3a3a3a", "#101010", "#bfbfbf", "#000000",
            "#d9b800", "#ffd400", "#000000", "#ff9500", "#000000", "#1a1a1a", "#000000", "#ffffff", "#000000", "#ffffff", 1.3)
        else -> null
    }

    private fun colorMap(p: Pal): Map<String, String> {
        val m = HashMap<String, String>()
        fun put(c: String, vararg ids: String) { for (i in ids) m[i] = c }
        put(p.earth, "background", "earth")
        put(p.green, "landcover")
        put(p.park, "landuse_park", "landuse_urban_green")
        put(p.water, "water", "water_stream", "water_river")
        put(p.building, "buildings")
        put(p.industry, "landuse_industrial", "landuse_hospital", "landuse_school", "landuse_pedestrian")
        put(p.minor, "roads_other", "roads_minor", "roads_minor_service", "roads_bridges_other", "roads_bridges_minor")
        put(p.minorCase, "roads_minor_service_casing", "roads_minor_casing", "roads_link_casing", "roads_bridges_other_casing",
            "roads_bridges_minor_casing", "roads_bridges_link_casing")
        put(p.link, "roads_link", "roads_bridges_link")
        put(p.major, "roads_major", "roads_bridges_major")
        put(p.majorCase, "roads_major_casing_early", "roads_major_casing_late", "roads_bridges_major_casing")
        put(p.highway, "roads_highway", "roads_bridges_highway")
        put(p.highwayCase, "roads_highway_casing_early", "roads_highway_casing_late", "roads_bridges_highway_casing")
        put(p.tunnel, "roads_tunnels_other", "roads_tunnels_minor", "roads_tunnels_link", "roads_tunnels_major", "roads_tunnels_highway")
        put(p.tunnelCase, "roads_tunnels_other_casing", "roads_tunnels_minor_casing", "roads_tunnels_link_casing",
            "roads_tunnels_major_casing", "roads_tunnels_highway_casing")
        return m
    }

    private val ROAD_WIDTH = Regex("^roads_(?!tunnels|pier|runway|taxiway|rail|oneway|labels|shields).*")

    /** Breite verbreitern: Zahl direkt, "interpolate"-Ausdruck an den Stützstellen (Verschachteltes bleibt unverändert). */
    private fun scale(v: Any?, f: Double): Any? {
        if (v is Number) return v.toDouble() * f
        if (v is JSONArray && v.length() >= 5 && v.optString(0) == "interpolate") {
            val out = JSONArray()
            for (i in 0 until v.length()) {
                val x = v.get(i)
                out.put(if (i >= 4 && i % 2 == 0 && x is Number) x.toDouble() * f else x)
            }
            return out
        }
        return v
    }

    fun apply(styleJson: String, calm: Boolean, buildings3d: Boolean, dark: Boolean, theme: String = STANDARD): String {
        val pal = palette(theme, dark)
        if (!calm && !buildings3d && pal == null) return styleJson
        val colors = if (pal != null) colorMap(pal) else emptyMap()
        val j = JSONObject(styleJson)
        val layers = j.getJSONArray("layers")
        val out = JSONArray()
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            val base = l.optString("id").substringBefore("~")
            val type = l.optString("type")
            if (calm && base in CALM_REMOVE) continue
            if (pal != null) {
                val paint = l.optJSONObject("paint")
                if (paint != null) {
                    colors[base]?.let { c ->
                        when (type) {
                            "fill" -> paint.put("fill-color", c)
                            "line" -> paint.put("line-color", c)
                            "background" -> paint.put("background-color", c)
                        }
                    }
                    if (type == "symbol") {
                        if (paint.has("text-color")) paint.put("text-color", if (base.startsWith("places_locality")) pal.place else pal.text)
                        if (paint.has("text-halo-color")) paint.put("text-halo-color", pal.halo)
                    }
                    if (type == "line" && pal.widen != 1.0 && ROAD_WIDTH.matches(base) && paint.has("line-width")) {
                        paint.put("line-width", scale(paint.get("line-width"), pal.widen))
                    }
                }
            }
            if (buildings3d && base == "buildings" && type == "fill") {
                val e = JSONObject()
                e.put("id", l.getString("id"))
                e.put("type", "fill-extrusion")
                e.put("source", l.getString("source"))
                e.put("source-layer", "buildings")
                e.put("minzoom", 15)
                if (l.has("filter")) e.put("filter", l.get("filter"))
                val paint = JSONObject()
                paint.put("fill-extrusion-color", pal?.roof ?: if (dark) "#5A5A66" else "#CBC7BE")
                // Höhe unbekannt (fehlt, 0 oder unter 3 m) = 6 m; sonst Wert aus den Daten (höchstens 40 m), mal 0,6
                paint.put("fill-extrusion-height", JSONArray("[\"let\",\"h\",[\"to-number\",[\"get\",\"height\"],0],[\"case\",[\">=\",[\"var\",\"h\"],3],[\"*\",[\"min\",[\"var\",\"h\"],40],0.6],6]]"))
                paint.put("fill-extrusion-base", JSONArray("[\"let\",\"h\",[\"to-number\",[\"get\",\"height\"],0],[\"case\",[\">=\",[\"var\",\"h\"],3],[\"*\",[\"min\",[\"to-number\",[\"get\",\"min_height\"],0],[\"var\",\"h\"],40],0.6],0]]"))
                paint.put("fill-extrusion-opacity", 1.0)
                paint.put("fill-extrusion-vertical-gradient", true)
                e.put("paint", paint)
                out.put(e)
                continue
            }
            out.put(l)
        }
        j.put("layers", out)
        return j.toString()
    }
}
