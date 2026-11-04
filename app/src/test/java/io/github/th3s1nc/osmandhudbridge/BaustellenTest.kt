package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.BaustellenLogic
import io.github.th3s1nc.osmandhudbridge.pilot.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaustellenTest {
    private fun site(id: String, kind: String, a: Pair<Double, Double>, b: Pair<Double, Double>, s: Long = 0, e: Long = 0) =
        Site(id, "B85", "B85 Amberg\nzwischen A und B\nVollsperrung, von x bis y", kind, s, e, listOf(a, b))

    // Route: gerade Linie von West nach Ost bei 49.0 Nord, 11.00 bis 11.10 Ost
    private val route = (0..100).map { Pair(49.0, 11.0 + it * 0.001) }

    @Test fun sperrungAufDerRouteWirdGefunden() {
        val s = site("1", "c", Pair(49.0003, 11.050), Pair(49.0003, 11.052))   // ca. 33 m neben der Route
        val h = BaustellenLogic.onRoute(route, listOf(s), 1_000)
        assertEquals(1, h.size)
    }

    @Test fun zuWeitWegZaehltNicht() {
        val s = site("1", "c", Pair(49.002, 11.050), Pair(49.002, 11.052))   // ca. 220 m daneben
        assertEquals(0, BaustellenLogic.onRoute(route, listOf(s), 1_000).size)
    }

    @Test fun kreuzungOhneGemeinsamenPunktWirdErkannt() {
        // Baustelle quert die Route senkrecht, Anfang und Ende weit weg
        val s = site("1", "c", Pair(48.99, 11.0505), Pair(49.01, 11.0505))
        assertEquals(1, BaustellenLogic.onRoute(route, listOf(s), 1_000).size)
    }

    @Test fun zeitraumWirdBeachtet() {
        val spaeter = site("1", "c", Pair(49.0, 11.05), Pair(49.0, 11.051), s = 5_000, e = 9_000)
        val vorbei = site("2", "c", Pair(49.0, 11.05), Pair(49.0, 11.051), s = 100, e = 900)
        val offen = site("3", "c", Pair(49.0, 11.05), Pair(49.0, 11.051))   // ohne Ende
        assertEquals(listOf("3"), BaustellenLogic.onRoute(route, listOf(spaeter, vorbei, offen), 1_000).map { it.site.id })
        assertEquals(listOf("1", "3"), BaustellenLogic.onRoute(route, listOf(spaeter, vorbei, offen), 6_000).map { it.site.id }.sorted())
    }

    @Test fun reineArbeitenWarnenNicht() {
        val w = site("1", "w", Pair(49.0, 11.05), Pair(49.0, 11.051))
        assertEquals(0, BaustellenLogic.onRoute(route, listOf(w), 1_000).size)
    }

    @Test fun gesperrtKommtVorEingeschraenkt() {
        val r = site("r", "r", Pair(49.0, 11.02), Pair(49.0, 11.021))
        val c = site("c", "c", Pair(49.0, 11.08), Pair(49.0, 11.081))
        assertEquals(listOf("c", "r"), BaustellenLogic.onRoute(route, listOf(r, c), 1_000).map { it.site.id })
    }

    @Test fun hinweistext() {
        assertNull(BaustellenLogic.routeNote(emptyList()))
        val c = site("c", "c", Pair(49.0, 11.08), Pair(49.0, 11.081), e = 1_772_000_000)
        val t = BaustellenLogic.routeNote(BaustellenLogic.onRoute(route, listOf(c), 1_000))!!
        assertTrue(t.startsWith("⚠ 1 Sperrung auf der Route: B85 Amberg"))
        assertTrue(t.contains(", bis "))
    }

    @Test fun kurzeStreckeUndNullLaenge() {
        val s = site("1", "c", Pair(49.0, 11.05), Pair(49.0, 11.05))   // Anfang = Ende (Punkt)
        assertEquals(1, BaustellenLogic.onRoute(route, listOf(s), 1_000).size)
    }

    @Test fun sperrlinienNurGesperrteGueltigeInDerNaehe() {
        val c = site("c", "c", Pair(49.0, 11.05), Pair(49.0, 11.051))
        val r = site("r", "r", Pair(49.0, 11.05), Pair(49.0, 11.051))
        val vorbei = site("v", "c", Pair(49.0, 11.05), Pair(49.0, 11.051), s = 100, e = 900)
        val weit = site("w", "c", Pair(52.0, 13.0), Pair(52.0, 13.001))
        val lats = DoubleArray(11) { 49.0 }; val lons = DoubleArray(11) { 11.0 + it * 0.01 }
        val out = BaustellenLogic.nogoLines(listOf(c, r, vorbei, weit), 1_000, lats, lons)
        assertEquals(1, out.size)
        assertEquals(4, out[0].size)
        assertEquals(49.0, out[0][0], 1e-9)
    }

    @Test fun langeLinienWerdenAufVierzigPunkteGekuerzt() {
        val pts = (0..200).map { Pair(49.0 + it * 0.0001, 11.0) }
        val s = Site("1", "B1", "x", "c", 0, 0, pts)
        val out = BaustellenLogic.nogoLines(listOf(s), 1_000, doubleArrayOf(49.0, 49.02), doubleArrayOf(11.0, 11.0))
        assertEquals(80, out[0].size)
        assertEquals(pts.last().first, out[0][78], 1e-9)
    }
}
