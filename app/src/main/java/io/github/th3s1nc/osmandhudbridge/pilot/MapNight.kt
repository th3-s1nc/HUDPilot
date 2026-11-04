package io.github.th3s1nc.osmandhudbridge.pilot

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Einstellung der Karte: Automatisch (Abend und Tunnel), immer hell oder immer dunkel. */
enum class NightPref { AUTO, DAY, NIGHT;
    fun next(): NightPref = values()[(ordinal + 1) % values().size]
    companion object { fun parse(s: String?): NightPref = values().firstOrNull { it.name == s } ?: AUTO }
}

/** Sonnenstand ohne Internet: ob es an einem Ort zu einer Zeit schon dunkel ist. Reine Rechnung, ohne Android. */
object Sun {
    /** Höhe der Sonne über dem Horizont in Grad (Näherung auf etwa ein halbes Grad genau). */
    fun elevationDeg(lat: Double, lon: Double, epochMs: Long): Double {
        val jd = epochMs / 86_400_000.0 + 2440587.5
        val n = jd - 2451545.0
        val l = (280.460 + 0.9856474 * n).mod(360.0)               // mittlere Länge
        val g = Math.toRadians((357.528 + 0.9856003 * n).mod(360.0)) // mittlere Anomalie
        val lambda = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val eps = Math.toRadians(23.439 - 0.0000004 * n)
        val decl = asin(sin(eps) * sin(lambda))
        val ra = Math.atan2(cos(eps) * sin(lambda), cos(lambda))
        val gmst = (18.697374558 + 24.06570982441908 * n).mod(24.0) // Stunden
        val lstDeg = (gmst * 15.0 + lon).mod(360.0)
        val ha = Math.toRadians(lstDeg) - ra
        val phi = Math.toRadians(lat)
        val sinAlt = sin(phi) * sin(decl) + cos(phi) * cos(decl) * cos(ha)
        return Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))
    }

    /** Dunkel, sobald die Sonne mehr als ~4 Grad unter dem Horizont steht (Abenddämmerung). */
    fun isDark(lat: Double, lon: Double, epochMs: Long): Boolean = elevationDeg(lat, lon, epochMs) < DARK_BELOW_DEG

    const val DARK_BELOW_DEG = -4.0
}

/**
 * Erkennt einen Tunnel: entweder meldet der Straßenimport "Tunnel", oder das GPS fehlt bei Fahrt.
 * Beim Hinausfahren bleibt es kurz dunkel, damit die Karte nicht flackert.
 */
class TunnelWatch(
    private val gpsGapMs: Long = 5_000L,
    private val minKmh: Float = 25f,
    private val exitHoldMs: Long = 3_000L
) {
    private var inTunnel = false
    private var clearSince = -1L

    /**
     * @param fixAgeMs Alter der letzten Position in ms
     * @param kmh letzte bekannte Geschwindigkeit
     * @param roadTunnel der Straßenimport sagt: aktuelle Straße ist ein Tunnel
     */
    fun update(nowMs: Long, fixAgeMs: Long, kmh: Float, roadTunnel: Boolean): Boolean {
        val gap = fixAgeMs > gpsGapMs && kmh >= minKmh
        if (roadTunnel || gap) {
            inTunnel = true
            clearSince = -1L
        } else if (inTunnel) {
            if (clearSince < 0) clearSince = nowMs
            if (nowMs - clearSince >= exitHoldMs) { inTunnel = false; clearSince = -1L }
        }
        return inTunnel
    }

    fun reset() { inTunnel = false; clearSince = -1L }
}

object MapNight {
    /** Soll die Karte dunkel sein? */
    fun dark(pref: NightPref, sunDark: Boolean, inTunnel: Boolean): Boolean = when (pref) {
        NightPref.DAY -> false
        NightPref.NIGHT -> true
        NightPref.AUTO -> sunDark || inTunnel
    }
}
