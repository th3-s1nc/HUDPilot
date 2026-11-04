package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.limit.PathMatcher
import io.github.th3s1nc.osmandhudbridge.limit.Way
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class PathMatcherTest {
    private val lat0 = 48.0
    private val lon0 = 10.0
    private fun la(northM: Double) = lat0 + northM / 110_540.0
    private fun lo(eastM: Double) = lon0 + eastM / (111_320.0 * cos(Math.toRadians(lat0)))
    private fun way(id: Long, name: String, limit: String?, vararg pts: Pair<Double, Double>): Way {
        val tags = HashMap<String, String>()
        tags["highway"] = "secondary"; tags["name"] = name
        if (limit != null) tags["maxspeed"] = limit
        return Way(id, pts.map { doubleArrayOf(la(it.second), lo(it.first)) }, tags)
    }

    /** Eine Fahrt nach Osten auf y = [y] m, Fix je Sekunde bei 15 m/s. */
    private fun driveEast(m: PathMatcher, ways: List<Way>, fromX: Double, steps: Int, y: Double, t0: Long, noiseAt: Int = -1, noiseY: Double = 0.0): Pair<Int?, String?> {
        var res: Pair<Int?, String?> = null to null
        for (i in 0 until steps) {
            val x = fromX + 15.0 * i
            val yy = if (i == noiseAt) y + noiseY else y
            val r = m.update(ways, la(yy), lo(x), 90.0, 15f, 5f, t0 + i * 1000L)
            res = r.limit to r.street
        }
        return res
    }

    @Test
    fun parallelstrasseEinAusreisserBringtNichtsDurcheinander() {
        val main = way(1, "Hauptstrasse", "100", -2000.0 to 0.0, 2000.0 to 0.0)
        val service = way(2, "Nebenweg", "30", -2000.0 to 9.0, 2000.0 to 9.0)
        val m = PathMatcher()
        // 9 Fixe auf der Hauptstrasse, ein Fix dazwischen 7 m nach Norden (näher an der Nebenstrasse)
        val (lim, name) = driveEast(m, listOf(main, service), 0.0, 10, 0.0, 0L, noiseAt = 8, noiseY = 7.0)
        assertEquals(100, lim)
        assertEquals("Hauptstrasse", name)
    }

    @Test
    fun abbiegenWechseltNachWenigenSekunden() {
        val a = way(1, "Alte Strasse", "100", -2000.0 to 0.0, 0.0 to 0.0)
        val b = way(2, "Neue Strasse", "50", 0.0 to 0.0, 0.0 to 2000.0)
        val m = PathMatcher()
        val ways = listOf(a, b)
        driveEast(m, ways, -150.0, 10, 0.0, 0L) // bis x = -15
        // jetzt nach Norden: Fixe bei x=0, y = 15, 30, 45, 60
        var last: Pair<Int?, String?> = null to null
        for (i in 1..5) {
            val r = m.update(ways, la(15.0 * i), lo(0.0), 0.0, 15f, 5f, 20_000L + i * 1000L)
            last = r.limit to r.street
        }
        assertEquals(50, last.first)
        assertEquals("Neue Strasse", last.second)
    }

    @Test
    fun kreuzendeStrasseWirdIgnoriert() {
        val main = way(1, "Hauptstrasse", "100", -2000.0 to 0.0, 2000.0 to 0.0)
        val cross = way(2, "Querstrasse", "30", 0.0 to -500.0, 0.0 to 500.0)
        val m = PathMatcher()
        val (lim, _) = driveEast(m, listOf(main, cross), -60.0, 9, 0.0, 0L) // fährt über die Kreuzung hinweg
        assertEquals(100, lim)
    }

    @Test
    fun imStandBleibtDieStrasseErhalten() {
        val main = way(1, "Hauptstrasse", "70", -2000.0 to 0.0, 2000.0 to 0.0)
        val side = way(2, "Seitenweg", "30", -2000.0 to 10.0, 2000.0 to 10.0)
        val m = PathMatcher()
        driveEast(m, listOf(main, side), 0.0, 8, 0.0, 0L)
        // jetzt Stand, GPS wandert zwischen beide Strassen
        var res = m.update(listOf(main, side), la(5.0), lo(105.0), null, 0f, 8f, 10_000L)
        assertEquals(70, res.limit)
        res = m.update(listOf(main, side), la(6.0), lo(106.0), null, 0f, 8f, 12_000L)
        assertEquals(70, res.limit)
    }

    @Test
    fun keineStrasseInDerNaehe() {
        val far = way(1, "Weit weg", "50", -2000.0 to 500.0, 2000.0 to 500.0)
        val m = PathMatcher()
        val r = m.update(listOf(far), la(0.0), lo(0.0), 90.0, 15f, 5f, 0L)
        assertTrue(r.noCandidates)
        assertNull(r.limit)
    }

    @Test
    fun zweiGleichwertigeStrassenMitAnderemLimitErgebenNull() {
        val a = way(1, "Strasse A", "50", -2000.0 to 0.0, 2000.0 to 0.0)
        val b = way(2, "Strasse B", "100", -2000.0 to 1.0, 2000.0 to 1.0)
        val m = PathMatcher()
        val (lim, _) = driveEast(m, listOf(a, b), 0.0, 6, 0.4, 0L)
        assertNull(lim)
    }

    @Test
    fun luckeImGpsFangtNeuAn() {
        val main = way(1, "Hauptstrasse", "100", -2000.0 to 0.0, 4000.0 to 0.0)
        val m = PathMatcher()
        driveEast(m, listOf(main), 0.0, 5, 0.0, 0L)
        val r = m.update(listOf(main), la(0.0), lo(900.0), 90.0, 15f, 5f, 60_000L) // 56 s später (Tunnel)
        assertEquals(100, r.limit)
    }
}
