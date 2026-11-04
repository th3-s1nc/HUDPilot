package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Liest eine GPX-Datei (z. B. von Kurviger) und macht daraus Wegpunkte für BRouter.
 * - Mit Routenpunkten (rtept): Start/Ziel/Via/Formpunkte so wie geplant.
 * - Nur mit Track (trkpt): Start, Ziel und alle paar Kilometer ein Formpunkt, damit BRouter nah am Track bleibt.
 */
object GpxRoute {
    class Parsed(val name: String, val waypoints: List<Waypoint>, val fromTrack: Boolean)

    private val attr = Regex("""([a-zA-Z:]+)\s*=\s*"([^"]*)"""")

    private class Pt(val lat: Double, val lon: Double, val type: String, val name: String)

    /** Alle Elemente "<tag ...>...</tag>" oder "<tag ... />" in Reihenfolge. */
    private fun points(xml: String, tag: String): List<Pt> {
        val out = ArrayList<Pt>()
        var pos = 0
        val open = "<$tag"
        while (true) {
            val s = xml.indexOf(open, pos)
            if (s < 0) break
            val after = xml.getOrNull(s + open.length)
            if (after == null || !(after == ' ' || after == '\t' || after == '\n' || after == '\r' || after == '>' || after == '/')) {
                pos = s + open.length
                continue
            }
            val tagEnd = xml.indexOf('>', s)
            if (tagEnd < 0) break
            val head = xml.substring(s, tagEnd)
            val selfClosing = head.endsWith("/")
            var body = ""
            var next = tagEnd + 1
            if (!selfClosing) {
                val close = xml.indexOf("</$tag>", tagEnd)
                if (close < 0) break
                body = xml.substring(tagEnd + 1, close)
                next = close + tag.length + 3
            }
            val a = attr.findAll(head).associate { it.groupValues[1] to it.groupValues[2] }
            val lat = a["lat"]?.toDoubleOrNull()
            val lon = a["lon"]?.toDoubleOrNull()
            if (lat != null && lon != null) {
                val type = Regex("<type>(.*?)</type>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()?.lowercase() ?: ""
                val name = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim() ?: ""
                out.add(Pt(lat, lon, type, name))
            }
            pos = next
        }
        return out
    }

    /** null, wenn die Datei weder Routenpunkte noch einen Track mit mindestens zwei Punkten enthält. */
    fun parse(xml: String, trackEveryM: Int = 4000): Parsed? {
        val routeName = Regex("<rte>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()
            ?: Regex("<metadata>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()
            ?: Regex("<trk>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()
            ?: ""
        val rte = points(xml, "rtept")
        if (rte.size >= 2) {
            val wps = rte.mapIndexed { i, p ->
                // Kurviger: type "via" = echtes Zwischenziel, "shaping" = nur Formpunkt
                Waypoint(p.lat, p.lon, p.type == "via" && i in 1 until rte.size - 1, p.name)
            }
            return Parsed(routeName, wps, false)
        }
        val trk = points(xml, "trkpt")
        if (trk.size < 2) return null
        val wps = ArrayList<Waypoint>()
        wps.add(Waypoint(trk.first().lat, trk.first().lon, false, "Start"))
        var acc = 0.0
        var lastAdded = 0.0
        for (i in 1 until trk.size - 1) {
            acc += Geo.distance(trk[i - 1].lat, trk[i - 1].lon, trk[i].lat, trk[i].lon)
            if (acc - lastAdded >= trackEveryM) {
                wps.add(Waypoint(trk[i].lat, trk[i].lon, false, "Track"))
                lastAdded = acc
            }
        }
        wps.add(Waypoint(trk.last().lat, trk.last().lon, false, "Ziel"))
        return Parsed(routeName, wps, true)
    }
}
