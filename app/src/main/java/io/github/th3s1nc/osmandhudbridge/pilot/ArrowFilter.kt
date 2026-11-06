package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Beruhigt Pfeil und Richtung. Reine Rechnung ohne Android.
 *
 * Richtung: Die GPS-Richtung zählt erst ab [MIN_HEADING_KMH]. Darunter bleibt die letzte gute Richtung stehen (rollen, schieben und
 * Stehen liefern beim GPS oft Unsinn). Kleine Änderungen werden geglättet. Ein Sprung um mehr als [JUMP_DEG] Grad zählt bei
 * Tempo unter [FAST_KMH] erst, wenn ihn zwei Messungen hintereinander bestätigen.
 *
 * Position: Steht das Motorrad (unter [STILL_KMH], zurück in Fahrt erst über [MOVE_KMH]), bleibt der Pfeil an seiner letzten Stelle und
 * springt nicht mit dem GPS-Zittern. Wandert die rohe Position deutlich weg, wird er wieder freigegeben.
 */
class ArrowFilter {
    companion object {
        const val MIN_HEADING_KMH = 10f
        const val FAST_KMH = 25f
        const val JUMP_DEG = 120.0
        const val CONFIRM_DEG = 45.0
        const val STILL_KMH = 3f
        const val MOVE_KMH = 5f
        const val RELEASE_MIN_M = 30.0
        /** Ab diesem Tempo darf der Pfeil auf die nächste Straße einrasten (mit der gemerkten Richtung). */
        const val SNAP_MIN_KMH = 3f

        fun diff(a: Double, b: Double): Double {
            val d = Math.abs(a - b) % 360.0
            return if (d > 180.0) 360.0 - d else d
        }

        /** Mischt zwei Richtungen im Kreis: w = Anteil der neuen (0..1). */
        fun blend(old: Double, new: Double, w: Double): Double {
            var d = ((new - old + 540.0) % 360.0) - 180.0
            d *= w
            return ((old + d) % 360.0 + 360.0) % 360.0
        }
    }

    /** Gemerkte Richtung (null = noch keine). */
    var heading: Double? = null
        private set
    private var pending: Double? = null
    private var still = false
    private var frozenLat = 0.0
    private var frozenLon = 0.0

    val isStill: Boolean get() = still

    fun reset() { heading = null; pending = null; still = false }

    /** GPS-Richtung [gps] (null = keine) bei Tempo [kmh]. Gibt die zu benutzende Richtung zurück. */
    fun updateHeading(gps: Double?, kmh: Float): Double? {
        if (gps == null || kmh < MIN_HEADING_KMH) return heading
        val cur = heading
        if (cur == null) { heading = gps; pending = null; return heading }
        val d = diff(cur, gps)
        if (d <= 60.0) { heading = blend(cur, gps, 0.6); pending = null; return heading }
        if (d >= JUMP_DEG && kmh < FAST_KMH) {
            val p = pending
            if (p != null && diff(p, gps) <= CONFIRM_DEG) { heading = gps; pending = null }
            else pending = gps
            return heading
        }
        heading = gps; pending = null
        return heading
    }

    class Shown(val lat: Double, val lon: Double, val frozen: Boolean)

    /**
     * Position für den Pfeil. [rawLat]/[rawLon] = GPS, [snapped] = eingerastete Position (oder null), [last] = zuletzt gezeigte
     * Stelle (oder null), [accuracyM] = GPS-Genauigkeit.
     */
    fun position(rawLat: Double, rawLon: Double, snapped: Pair<Double, Double>?, last: Pair<Double, Double>?, kmh: Float, accuracyM: Float?): Shown {
        if (still) { if (kmh > MOVE_KMH) still = false } else if (kmh < STILL_KMH && last != null) {
            still = true; frozenLat = last.first; frozenLon = last.second
        }
        if (still) {
            val dist = Geo.distance(frozenLat, frozenLon, rawLat, rawLon)
            val limit = maxOf(RELEASE_MIN_M, (accuracyM ?: 0f) * 2.0)
            if (dist <= limit) return Shown(frozenLat, frozenLon, true)
            still = false   // die Position ist deutlich weggewandert: freigeben
        }
        val p = snapped ?: Pair(rawLat, rawLon)
        return Shown(p.first, p.second, false)
    }
}
