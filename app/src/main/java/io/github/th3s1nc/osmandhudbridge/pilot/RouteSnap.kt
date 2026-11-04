package io.github.th3s1nc.osmandhudbridge.pilot

/** Nächster Punkt auf der Route zu einer GPS-Position (für die Anzeige des Pfeils). */
object RouteSnap {
    class Hit(val lat: Double, val lon: Double, val distM: Double)

    /**
     * Zieht ([lat], [lon]) auf die nächste Stelle der Route. Gibt null zurück, wenn die Route weiter als [maxM] entfernt ist
     * (dann bleibt der Pfeil auf der GPS-Position, zum Beispiel bei einer Abweichung).
     */
    fun snap(points: List<RoutePoint>, lat: Double, lon: Double, maxM: Double = 30.0): Hit? {
        if (points.size < 2) return null
        val kLat = 111_320.0
        val kLon = 111_320.0 * Math.cos(Math.toRadians(lat))
        var best = Double.MAX_VALUE
        var bLat = 0.0
        var bLon = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lon - lon) * kLon; val ay = (a.lat - lat) * kLat
            val bx = (b.lon - lon) * kLon; val by = (b.lat - lat) * kLat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val d = Math.sqrt(px * px + py * py)
            if (d < best) { best = d; bLat = lat + py / kLat; bLon = lon + px / kLon }
        }
        return if (best <= maxM) Hit(bLat, bLon, best) else null
    }
}
