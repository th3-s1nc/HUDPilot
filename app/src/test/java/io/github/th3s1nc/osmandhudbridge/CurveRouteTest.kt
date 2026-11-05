package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.limit.CurveIndex
import io.github.th3s1nc.osmandhudbridge.limit.CurveWarn
import io.github.th3s1nc.osmandhudbridge.limit.LimitAhead
import io.github.th3s1nc.osmandhudbridge.limit.RouteLine
import io.github.th3s1nc.osmandhudbridge.limit.Way
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveRouteTest {
    private val lat0 = 49.0
    private val lon0 = 12.0
    private val kx = 111_320.0 * Math.cos(Math.toRadians(lat0))
    private val ky = 110_540.0
    private fun pt(x: Double, y: Double) = doubleArrayOf(lat0 + y / ky, lon0 + x / kx)

    private fun line(pts: List<DoubleArray>, turns: IntArray = IntArray(0)): RouteLine {
        val cum = IntArray(pts.size)
        var s = 0.0
        for (i in 1 until pts.size) {
            s += Math.hypot((pts[i][1] - pts[i - 1][1]) * kx, (pts[i][0] - pts[i - 1][0]) * ky)
            cum[i] = Math.round(s).toInt()
        }
        return RouteLine(DoubleArray(pts.size) { pts[it][0] }, DoubleArray(pts.size) { pts[it][1] }, cum, turns)
    }

    private fun straight(len: Double) = (0..(len / 5).toInt()).map { pt(0.0, it * 5.0) }

    /** Nach Norden bis y=[lead], dann Rechtskurve (90 Grad, Radius [r]) nach Osten. */
    private fun bend(r: Double, lead: Double = 400.0): List<DoubleArray> {
        val out = ArrayList<DoubleArray>()
        var y = 0.0
        while (y < lead) { out += pt(0.0, y); y += 5.0 }
        var a = 0.0
        while (a < Math.PI / 2) { out += pt(r - r * Math.cos(a), lead + r * Math.sin(a)); a += 4.0 / r }
        var x = 0.0
        while (x <= 300) { out += pt(r + x, lead + r); x += 5.0 }
        return out
    }

    private fun drive(route: RouteLine?, ways: List<Way>, kmh: Double): Int {
        val w = CurveWarn(); val idx = CurveIndex(ways)
        var n = 0; var y = 0.0
        while (y < 420.0) {
            if (w.update(ways, idx, lat0 + y / ky, lon0, 0.0, (kmh / 3.6).toFloat(), 1, 0, false, route, y)) n++
            y += kmh / 3.6
        }
        return n
    }

    private val mapStraight = listOf(Way(1, straight(900.0), mapOf("highway" to "tertiary", "name" to "Testweg")))

    @Test fun routeFindetKurveDieDieKarteGeradeAnnimmt() {
        val r = line(bend(25.0))
        assertEquals("ohne Route kein Ton (Karte gerade)", 0, drive(null, mapStraight, 80.0))
        assertEquals("mit Route ein Ton", 1, drive(r, mapStraight, 80.0))
    }

    @Test fun eigeneAbbiegungGibtKeinenTon() {
        val r = line(bend(25.0), intArrayOf(415))   // Abbiegung direkt in der Kurve
        assertEquals(0, drive(r, mapStraight, 80.0))
    }

    @Test fun weitEntfernteAbbiegungStoertNicht() {
        val r = line(bend(25.0), intArrayOf(100))
        assertEquals(1, drive(r, mapStraight, 80.0))
    }

    @Test fun routeLinePointAtUndTurnWithin() {
        val r = line(straight(100.0), intArrayOf(50))
        val p = r.pointAt(50.0)
        assertEquals(lat0 + 50.0 / ky, p[0], 1e-6)
        assertTrue(r.turnWithin(70.0, 25.0)); assertFalse(r.turnWithin(100.0, 25.0))
        assertEquals(r.pointAt(1e6)[0], r.lat.last(), 0.0)
        assertEquals(r.pointAt(-5.0)[0], r.lat.first(), 0.0)
    }

    // ---- Früherkennung ----
    private fun way(id: Long, y0: Double, y1: Double, tags: Map<String, String>, x: Double = 0.0) =
        Way(id, (0..((y1 - y0) / 5).toInt()).map { pt(x, y0 + it * 5.0) }, tags)

    private val route = line(straight(800.0))
    private val mixed = listOf(
        way(1, 0.0, 300.0, mapOf("highway" to "secondary", "maxspeed" to "100")),
        way(2, 300.0, 800.0, mapOf("highway" to "secondary", "maxspeed" to "50"))
    )

    @Test fun vorausschauWirdMitTempogeschaetzt() {
        assertEquals(40.0, LimitAhead.leadM(2.0), 0.001)
        assertEquals(88.0, LimitAhead.leadM(22.0), 0.001)
        assertEquals(150.0, LimitAhead.leadM(60.0), 0.001)
    }

    @Test fun kommendesLimitErstKurzVorDemWechsel() {
        assertNull(LimitAhead.find(route, 200.0, 22.2, 100, mixed))
        val a = LimitAhead.find(route, 230.0, 22.2, 100, mixed)
        assertNotNull(a); assertEquals(50, a!!.kmh); assertTrue(a.distM in 60..80)
    }

    @Test fun nachDemWechselNichtsMehr() {
        assertNull(LimitAhead.find(route, 350.0, 22.2, 50, mixed))
    }

    @Test fun gleichesLimitOhneMeldung() {
        val same = listOf(way(1, 0.0, 800.0, mapOf("highway" to "secondary", "maxspeed" to "70")))
        assertNull(LimitAhead.find(route, 100.0, 22.2, 70, same))
    }

    @Test fun ohneEingetragenesLimitNichts() {
        val noTag = listOf(way(1, 0.0, 300.0, mapOf("highway" to "secondary", "maxspeed" to "100")), way(2, 300.0, 800.0, mapOf("highway" to "secondary")))
        assertNull(LimitAhead.find(route, 230.0, 22.2, 100, noTag))
    }

    @Test fun querstrasseWirdNichtVerwechselt() {
        val cross = mixed + Way(3, (-20..20).map { pt(it * 5.0, 290.0) }, mapOf("highway" to "residential", "maxspeed" to "30"))
        val a = LimitAhead.find(route, 230.0, 22.2, 100, cross)
        assertEquals(50, a?.kmh)
    }

    @Test fun unbekanntesJetzigesLimitKeineMeldung() {
        assertNull(LimitAhead.find(route, 230.0, 22.2, 0, mixed))
    }
}
