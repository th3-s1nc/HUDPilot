package io.github.th3s1nc.osmandhudbridge.limit

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Ordnet die Fahrt (nicht nur den letzten Punkt) einer Straße zu ("Map-Matching über den Weg").
 *
 * Statt nur die gerade gemessene Position mit der nächsten Straße zu vergleichen, wird der Weg der letzten Sekunden
 * ausgewertet: die Straße gewinnt, an der die ganze Spur im Mittel am dichtesten liegt (neuere Punkte zählen mehr).
 * Dazu kommen die Fahrtrichtung (aus der Spur und dem GPS), ein Bonus für die Straße, auf der wir schon fahren
 * (gleiche Straße, gleicher Name, oder direkt anschließend), und eine Mehrdeutigkeitsprüfung.
 * Ein einzelner GPS-Ausreißer oder eine Parallelstraße 10 m daneben bringt die Zuordnung so nicht mehr durcheinander,
 * nach einem Abbiegen wechselt sie nach etwa zwei Sekunden.
 *
 * Reine Logik ohne Android (testbar).
 */
class PathMatcher(
    private val horizonMs: Long = 15_000L,
    private val maxPoints: Int = 12,
    private val gapResetMs: Long = 10_000L
) {
    private class P(val lat: Double, val lon: Double, val t: Long)

    private val trail = ArrayDeque<P>()
    private var prevWay: Way? = null

    fun reset() { trail.clear(); prevWay = null }

    /** Bisher zugeordnete Straße (für Tests und Anzeige). */
    val currentWay: Way? get() = prevWay

    fun update(
        ways: List<Way>, lat: Double, lon: Double, headingDeg: Double?, speedMs: Float, accuracyM: Float,
        nowMs: Long, opts: MatchOptions = MatchOptions()
    ): SpeedLimitMatcher.MatchResult {
        // Spur fortschreiben
        val last = trail.lastOrNull()
        if (last != null && nowMs - last.t > gapResetMs) { trail.clear() } // Lücke (Tunnel, GPS weg): neu anfangen
        val moving = speedMs > MOVING_MS
        val l2 = trail.lastOrNull()
        if (l2 == null || dist(l2.lat, l2.lon, lat, lon) >= MIN_STEP_M || nowMs - l2.t > 4_000L) {
            // im Stand keine neuen Punkte sammeln, damit die Spur nicht auf einen Haufen schrumpft
            if (moving || trail.isEmpty()) trail.addLast(P(lat, lon, nowMs))
        }
        while (trail.size > maxPoints || (trail.isNotEmpty() && nowMs - trail.first().t > horizonMs)) trail.removeFirst()

        val maxDist = (accuracyM * 1.5).coerceIn(15.0, 35.0)
        val travel = travelBearing(headingDeg, moving)

        class C(val way: Way, val score: Double, val limit: Int?, val street: String?)
        val cands = ArrayList<C>()
        for (w in ways) {
            val h = SpeedLimitMatcher.nearest(w, lat, lon) ?: continue
            if (h.distM > maxDist) continue
            var forward: Boolean? = null
            var angleCost = 0.0
            if (travel != null) {
                val diff = angleDiff(travel, h.bearing)
                val along = minOf(diff, 180.0 - diff)
                if (along > MAX_ANGLE_ALONG) continue // kreuzende Straße
                angleCost = along * 0.2
                forward = diff < 90.0
            }
            val cost = trailCost(w) + angleCost - continuityBonus(w)
            cands += C(w, cost, SpeedLimitMatcher.wayLimit(w, forward, opts), (w.tags["name"] ?: w.tags["ref"])?.takeIf { it.isNotBlank() })
        }
        if (cands.isEmpty()) return SpeedLimitMatcher.MatchResult(null, null, noCandidates = true)
        cands.sortBy { it.score }
        val best = cands[0]
        prevWay = best.way
        // Mehrdeutig: eine andere Straße (nicht dieselbe) liegt gleichauf und hätte ein anderes Limit bzw. einen anderen Namen
        val rivals = cands.drop(1).filter { it.way.id != best.way.id && it.score - best.score < AMBIGUITY }
        val limitClash = rivals.any { it.limit != best.limit }
        val nameClash = rivals.any { it.street != best.street }
        return SpeedLimitMatcher.MatchResult(if (limitClash) null else best.limit, if (nameClash) null else best.street)
    }

    /** Mittlerer Abstand der Spur zur Straße, neuere Punkte zählen stärker; ein einzelner Ausreißer wird gekappt. */
    private fun trailCost(w: Way): Double {
        var sum = 0.0
        var wsum = 0.0
        var weight = 1.0
        for (i in trail.indices.reversed()) {
            val p = trail[i]
            val d = SpeedLimitMatcher.nearest(w, p.lat, p.lon)?.distM ?: CAP_M
            sum += weight * minOf(d, CAP_M)
            wsum += weight
            weight *= DECAY
        }
        return if (wsum == 0.0) CAP_M else sum / wsum
    }

    /** Bonus (in Metern) für die Straße, auf der wir schon fahren: gleiche Straße, gleicher Name/Ref, oder Anschluss an sie. */
    private fun continuityBonus(w: Way): Double {
        val pw = prevWay ?: return 0.0
        if (pw === w || pw.id == w.id) return BONUS_SAME
        val a = (pw.tags["name"] ?: pw.tags["ref"])?.takeIf { it.isNotBlank() }
        val b = (w.tags["name"] ?: w.tags["ref"])?.takeIf { it.isNotBlank() }
        if (a != null && a == b) return BONUS_NAME
        if (connected(pw, w)) return BONUS_CONNECTED
        return 0.0
    }

    /** Teilen die Straßen einen Endpunkt (Kreuzung)? Toleranz 3 m. */
    private fun connected(a: Way, b: Way): Boolean {
        if (a.points.isEmpty() || b.points.isEmpty()) return false
        val ea = listOf(a.points.first(), a.points.last())
        val eb = listOf(b.points.first(), b.points.last())
        for (x in ea) for (y in eb) if (dist(x[0], x[1], y[0], y[1]) <= 3.0) return true
        // Einmündung mitten in die andere Straße: Endpunkt der einen liegt auf der anderen
        for (x in ea) if ((SpeedLimitMatcher.nearest(b, x[0], x[1])?.distM ?: 99.0) <= 3.0) return true
        for (y in eb) if ((SpeedLimitMatcher.nearest(a, y[0], y[1])?.distM ?: 99.0) <= 3.0) return true
        return false
    }

    /** Fahrtrichtung: GPS-Peilung (aktuell, auch in Kurven richtig), sonst aus der Spur; null im Stand. */
    private fun travelBearing(gps: Double?, moving: Boolean): Double? {
        if (!moving) return null
        if (gps != null) return gps
        if (trail.size >= 2) {
            val a = trail.first()
            val b = trail.last()
            if (dist(a.lat, a.lon, b.lat, b.lon) >= MIN_SPAN_M) {
                val kx = 111_320.0 * cos(Math.toRadians(b.lat))
                return (Math.toDegrees(atan2((b.lon - a.lon) * kx, (b.lat - a.lat) * 110_540.0)) + 360.0) % 360.0
            }
        }
        return null
    }

    private fun dist(la: Double, lo: Double, lb: Double, lob: Double): Double {
        val kx = 111_320.0 * cos(Math.toRadians((la + lb) / 2))
        return hypot((lob - lo) * kx, (lb - la) * 110_540.0)
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    private companion object {
        const val MOVING_MS = 2.5f
        const val MAX_ANGLE_ALONG = 40.0
        const val MIN_STEP_M = 3.0
        const val MIN_SPAN_M = 15.0
        const val CAP_M = 60.0
        const val DECAY = 0.82
        const val BONUS_SAME = 4.0
        const val BONUS_NAME = 3.0
        const val BONUS_CONNECTED = 2.0
        const val AMBIGUITY = 5.0
    }
}
