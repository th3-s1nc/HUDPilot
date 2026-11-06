package io.github.th3s1nc.osmandhudbridge.pilot

/** Stelle auf einer Route nach einer Strecke vom Start. Reine Rechnung ohne Android. */
object RouteWalk {
    class At(val lat: Double, val lon: Double, val bearing: Double, val index: Int)

    fun pointAt(route: PilotRoute, distM: Double): At? {
        val n = route.points.size
        if (n < 2) return null
        val d = distM.coerceIn(0.0, route.totalM.toDouble())
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (route.cumM[mid] <= d) lo = mid else hi = mid
        }
        val a = route.points[lo]
        val b = route.points[hi]
        val seg = (route.cumM[hi] - route.cumM[lo]).toDouble()
        val f = if (seg <= 0.0) 0.0 else ((d - route.cumM[lo]) / seg).coerceIn(0.0, 1.0)
        return At(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, Geo.bearing(a.lat, a.lon, b.lat, b.lon), lo)
    }
}

/** Weiterlaufen bei GPS-Verlust (Tunnel): der Pfeil läuft mit dem letzten Tempo auf der Route weiter. */
object DeadReckon {
    /** So lange ohne Position, bevor weitergelaufen wird (ms). */
    const val START_MS = 3_000L
    /** Danach nicht länger als so viele ms (dann bleibt es stehen). */
    const val MAX_MS = 120_000L
    /** Nur bei diesem Tempo (km/h) vor dem Ausfall. */
    const val MIN_KMH = 15f

    fun active(msSinceFix: Long, lastKmh: Float): Boolean = msSinceFix in START_MS..MAX_MS && lastKmh >= MIN_KMH

    /** Strecke (m), die seit der letzten echten Position zurückgelegt wurde. */
    fun meters(lastKmh: Float, msSinceFix: Long): Double = lastKmh / 3.6 * (msSinceFix / 1000.0)
}
