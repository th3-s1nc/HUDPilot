package io.github.th3s1nc.osmandhudbridge.pilot

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** Arten von Warnsymbolen in der Karte. */
object HazardKind {
    const val CAMERA = 1       // Tempo- oder Abschnittsblitzer, Tunnel-Blitzer
    const val RED_LIGHT = 2    // Ampelblitzer
    const val LEVEL_CROSSING = 3
    const val ZEBRA = 4
    const val CALMING = 5      // Verkehrsberuhigung

    /** Diese Arten sind immer zu sehen (auf der Route); die übrigen erst kurz davor. */
    fun always(kind: Int) = kind <= LEVEL_CROSSING
}

/** Ein Warnpunkt, den die Karte zeigen kann. */
class Hazard(val id: Long, val lat: Double, val lon: Double, val kind: Int)

/** Ein sichtbarer Warnpunkt mit Entfernung (m, entlang der Route oder Luftlinie). */
class ShownHazard(val hazard: Hazard, val distM: Int)

/**
 * Welche Warnsymbole die Karte zeigt. Mit Route: nur Punkte auf der Strecke (höchstens 35 m daneben); Blitzer und Bahnübergänge
 * bis 2,5 km voraus, Zebrastreifen und Verkehrsberuhigung nur auf den nächsten 300 m. Ohne Route: Punkte vor dir in Fahrtrichtung
 * (Blitzer und Bahnübergänge bis 1,5 km, der Rest bis 300 m). Reine Logik ohne Android, damit sie getestet werden kann.
 */
object MapHazards {
    const val ROUTE_OFF_M = 35.0
    const val NEAR_M = 300.0
    const val ALWAYS_AHEAD_ROUTE_M = 2500.0
    const val ALWAYS_AHEAD_FREE_M = 1500.0
    const val MAX_SHOWN = 30
    private const val M_PER_DEG = 111_320.0

    fun select(
        all: List<Hazard>, lat: Double, lon: Double, headingDeg: Double?,
        route: PilotRoute?, progressM: Int
    ): List<ShownHazard> {
        if (all.isEmpty()) return emptyList()
        val kx = M_PER_DEG * cos(Math.toRadians(lat))
        val out = ArrayList<ShownHazard>()
        val pts = route?.points
        if (route != null && pts != null && pts.size >= 2) {
            // Fenster der Route: ein kleines Stück zurück bis 2,5 km voraus
            val cum = route.cumM
            var a = 0
            while (a < pts.size - 2 && cum[a + 1] < progressM - 100) a++
            var b = a
            while (b < pts.size - 2 && cum[b] < progressM + ALWAYS_AHEAD_ROUTE_M + 100) b++
            for (h in all) {
                if (abs(h.lat - lat) * M_PER_DEG > ALWAYS_AHEAD_ROUTE_M + 200) continue
                var best = Double.MAX_VALUE
                var bestCum = 0.0
                for (i in a..b) {
                    val d = segDist(h.lat, h.lon, pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon, kx)
                    if (d.first < best) { best = d.first; bestCum = cum[i] + d.second * (cum[i + 1] - cum[i]) }
                }
                if (best > ROUTE_OFF_M) continue
                val ahead = bestCum - progressM
                val ok = if (HazardKind.always(h.kind)) ahead in -20.0..ALWAYS_AHEAD_ROUTE_M else ahead in -20.0..NEAR_M
                if (ok) out += ShownHazard(h, ahead.toInt().coerceAtLeast(0))
            }
        } else {
            for (h in all) {
                val dN = (h.lat - lat) * M_PER_DEG
                val dE = (h.lon - lon) * kx
                val d = hypot(dN, dE)
                val limit = if (HazardKind.always(h.kind)) ALWAYS_AHEAD_FREE_M else NEAR_M
                if (d > limit) continue
                if (headingDeg != null && d > 30) {
                    val bearing = Math.toDegrees(Math.atan2(dE, dN))
                    var diff = (bearing - headingDeg) % 360.0
                    if (diff > 180) diff -= 360
                    if (diff < -180) diff += 360
                    if (abs(diff) > 50) continue
                }
                out += ShownHazard(h, d.toInt())
            }
        }
        out.sortBy { it.distM }
        return if (out.size > MAX_SHOWN) out.subList(0, MAX_SHOWN).toList() else out
    }

    /** Abstand (m) von Punkt zu Strecke A-B und Anteil (0..1) des Fußpunkts auf der Strecke. */
    private fun segDist(plat: Double, plon: Double, alat: Double, alon: Double, blat: Double, blon: Double, kx: Double): Pair<Double, Double> {
        val ax = (alon - plon) * kx; val ay = (alat - plat) * M_PER_DEG
        val bx = (blon - plon) * kx; val by = (blat - plat) * M_PER_DEG
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        return Pair(hypot(ax + t * dx, ay + t * dy), t)
    }

    /** Beschriftung für das nächste Symbol: auf 50 m gerundet, ab 1 km in km. */
    fun label(distM: Int): String =
        if (distM >= 1000) String.format(java.util.Locale.GERMANY, "%.1f km", distM / 1000.0)
        else "${((distM + 25) / 50 * 50).coerceAtLeast(50)} m"
}
