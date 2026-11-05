package io.github.th3s1nc.osmandhudbridge.limit

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Früherkennung: das Tempolimit der Straße, die als Nächstes auf der geplanten Route kommt (ungefähr 4 Sekunden vorher).
 * Nur ein ausdrücklich eingetragenes Limit (maxspeed) zählt, nichts Geschätztes. Zwei Punkte kurz hintereinander auf der Route
 * müssen dieselbe Straße mit demselben Limit treffen, damit eine Kreuzung nicht für Unruhe sorgt. Reine Logik ohne Android.
 */
object LimitAhead {
    class Ahead(val kmh: Int, val distM: Int)

    const val SECONDS = 4.0
    const val MIN_M = 40.0
    const val MAX_M = 150.0
    private const val CONFIRM_M = 12.0
    private const val NEAR_M = 12.0
    private const val MAX_ANGLE = 30.0
    private const val M_PER_DEG = 111_320.0
    private const val KY = 110_540.0

    /** Vorausschau in Metern für das aktuelle Tempo. */
    fun leadM(speedMs: Double): Double = (speedMs * SECONDS).coerceIn(MIN_M, MAX_M)

    /**
     * [current]: aktuell erkanntes Limit (0 = unbekannt: dann keine Meldung). Rückgabe: das kommende Limit, wenn es sich vom jetzigen unterscheidet; sonst null.
     * Gesucht wird alle 10 m bis [leadM] m voraus; das erste andere, eindeutige Limit wird gemeldet.
     */
    fun find(line: RouteLine, progressM: Double, speedMs: Double, current: Int, ways: List<Way>): Ahead? {
        if (current <= 0) return null   // jetziges Limit unbekannt: nichts vergleichbar, keine Meldung
        val lead = leadM(speedMs)
        var d = 10.0
        while (d <= lead) {
            val l = limitAt(line, progressM + d, ways)
            if (l != null && l != current) return Ahead(l, d.toInt())
            d += 10.0
        }
        return null
    }

    /** Das Limit der Straße bei Strecke [s] der Route, wenn zwei dicht benachbarte Punkte dasselbe eindeutige Limit treffen. */
    internal fun limitAt(line: RouteLine, s: Double, ways: List<Way>): Int? {
        val a = wayLimit(line, s, ways) ?: return null
        val b = wayLimit(line, s + CONFIRM_M, ways) ?: return null
        return if (a == b) a else null
    }

    private fun wayLimit(line: RouteLine, s: Double, ways: List<Way>): Int? {
        if (s >= line.totalM) return null
        val p = line.pointAt(s); val q = line.pointAt(s + 6.0); val o = line.pointAt(maxOf(0.0, s - 6.0))
        val kx = M_PER_DEG * cos(Math.toRadians(p[0]))
        val rb = (Math.toDegrees(atan2((q[1] - o[1]) * kx, (q[0] - o[0]) * KY)) + 360.0) % 360.0
        var bestD = Double.MAX_VALUE; var best: Way? = null
        for (w in ways) {
            if (w.points.size < 2) continue
            for (i in 0 until w.points.size - 1) {
                val ax = (w.points[i][1] - p[1]) * kx; val ay = (w.points[i][0] - p[0]) * KY
                val bx = (w.points[i + 1][1] - p[1]) * kx; val by = (w.points[i + 1][0] - p[0]) * KY
                val dx = bx - ax; val dy = by - ay; val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
                val dist = hypot(ax + t * dx, ay + t * dy)
                if (dist > NEAR_M || dist >= bestD) continue
                val sb = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
                var diff = abs(rb - sb) % 360.0; if (diff > 180.0) diff = 360.0 - diff
                if (minOf(diff, 180.0 - diff) > MAX_ANGLE) continue
                bestD = dist; best = w
            }
        }
        return SpeedLimitMatcher.parseMaxspeed(best?.tags?.get("maxspeed"))
    }
}
