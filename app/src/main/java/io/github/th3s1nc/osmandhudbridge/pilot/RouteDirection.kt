package io.github.th3s1nc.osmandhudbridge.pilot

import kotlin.math.abs

/**
 * Prüft, ob eine neu berechnete Route in deine Fahrtrichtung beginnt. Eine Route, die dich gleich umdrehen lässt,
 * wird nie angeboten. Reine Rechnung ohne Android.
 */
object RouteDirection {
    /** So weit vom Start wird die Richtung der Route gemessen (m). */
    const val CHECK_M = 150
    /** Mehr Abweichung von der Fahrtrichtung (Grad) gilt als Umdrehen. */
    const val MAX_DIFF = 90

    /** Kleinster Winkelunterschied zweier Richtungen, 0 bis 180 Grad. */
    fun diff(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)
        return d
    }

    /**
     * Richtung vom Start der Route zu dem Punkt etwa [lenM] weiter, in Grad (0 = Nord). null, wenn die Route zu kurz ist,
     * um das zu beurteilen, oder wenn sie in sich zurückläuft (der Punkt liegt wieder fast am Start).
     */
    fun startBearing(route: PilotRoute, lenM: Int = CHECK_M): Double? {
        val pts = route.points
        if (pts.size < 2 || route.cumM.size != pts.size) return null
        var j = 1
        while (j < pts.size - 1 && route.cumM[j] - route.cumM[0] < lenM) j++
        val along = route.cumM[j] - route.cumM[0]
        if (along < 20) return null   // zu kurz zum Beurteilen (z. B. Ziel gleich da)
        val d = Geo.distance(pts[0].lat, pts[0].lon, pts[j].lat, pts[j].lon)
        if (d < 25.0 && along >= 80) return Double.NaN   // läuft in sich zurück: Wende
        if (d < 5.0) return null
        return Geo.bearing(pts[0].lat, pts[0].lon, pts[j].lat, pts[j].lon)
    }

    /** true, wenn die Route am Anfang gegen die Fahrtrichtung [headingDeg] läuft (Umdrehen). */
    fun startsAgainst(route: PilotRoute, headingDeg: Int, lenM: Int = CHECK_M, maxDiff: Int = MAX_DIFF): Boolean {
        val b = startBearing(route, lenM) ?: return false
        if (b.isNaN()) return true
        return diff(b, headingDeg.toDouble()) > maxDiff
    }
}
