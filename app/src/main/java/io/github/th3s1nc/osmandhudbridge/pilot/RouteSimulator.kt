package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Spielt eine Fahrt auf einer berechneten Route ab, ohne dass jemand fährt.
 * Reine Rechnung: advance(dt) schiebt die virtuelle Position weiter, state() sagt, wo sie ist und was als Nächstes kommt.
 */
class RouteSimulator(val route: PilotRoute) {
    /** Geschwindigkeit der virtuellen Fahrt (km/h) und Zeitraffer (1 = Echtzeit). */
    var speedKmh = 50.0
    var timeFactor = 1.0
    private var distM = 0.0

    /** Seitlicher Versatz der virtuellen Position in Metern (zum Testen der Neuberechnung). */
    var sideOffsetM = 0.0

    data class State(
        val lat: Double, val lon: Double, val headingDeg: Double,
        val distM: Int, val remainingM: Int, val remainingSec: Int,
        val next: Instruction?, val nextDistM: Int, val finished: Boolean
    )

    val finished: Boolean get() = distM >= route.totalM

    fun advance(dtSec: Double) {
        if (finished) return
        distM = (distM + speedKmh / 3.6 * timeFactor * dtSec).coerceAtMost(route.totalM.toDouble())
    }

    fun restart() { distM = 0.0 }

    /** Zu einer Strecke vom Start springen (für Tests). */
    fun seek(m: Double) { distM = m.coerceIn(0.0, route.totalM.toDouble()) }

    /** Index des Punkts, dessen Strecke <= m ist (letzter solcher). */
    private fun indexAt(m: Double): Int {
        val c = route.cumM
        var lo = 0
        var hi = c.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (c[mid] <= m) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun position(): Triple<Double, Double, Double> {
        val (lat, lon, hdg) = onRoute()
        if (sideOffsetM == 0.0) return Triple(lat, lon, hdg)
        val (la, lo) = Geo.offset(lat, lon, hdg + 90.0, sideOffsetM)
        return Triple(la, lo, hdg)
    }

    private fun onRoute(): Triple<Double, Double, Double> {
        val pts = route.points
        if (pts.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val i = indexAt(distM)
        if (i >= pts.size - 1) {
            val a = pts[(pts.size - 2).coerceAtLeast(0)]
            val b = pts[pts.size - 1]
            return Triple(b.lat, b.lon, if (pts.size >= 2) Geo.bearing(a.lat, a.lon, b.lat, b.lon) else 0.0)
        }
        val a = pts[i]
        val b = pts[i + 1]
        val seg = (route.cumM[i + 1] - route.cumM[i]).toDouble()
        val f = if (seg <= 0.0) 0.0 else ((distM - route.cumM[i]) / seg).coerceIn(0.0, 1.0)
        return Triple(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, Geo.bearing(a.lat, a.lon, b.lat, b.lon))
    }

    fun state(): State {
        val (lat, lon, hdg) = position()
        val total = route.totalM
        val rem = (total - distM).toInt().coerceAtLeast(0)
        val remSec = if (total <= 0) 0 else (route.seconds.toDouble() * rem / total).toInt()
        val next = route.instructions.firstOrNull { it.atM >= distM.toInt() }
        val nextDist = if (next == null) 0 else (next.atM - distM.toInt()).coerceAtLeast(0)
        return State(lat, lon, hdg, distM.toInt(), rem, remSec, next, nextDist, finished)
    }

    /** Punkt neben der Route (Abweichung), seitlich der Fahrtrichtung. */
    fun offRoutePoint(sideM: Double): Pair<Double, Double> {
        val (lat, lon, hdg) = position()
        return Geo.offset(lat, lon, hdg + 90.0, sideM)
    }
}
