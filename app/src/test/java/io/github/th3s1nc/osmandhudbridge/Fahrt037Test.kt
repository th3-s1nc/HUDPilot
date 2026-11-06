package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.ArrowFilter
import io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd
import io.github.th3s1nc.osmandhudbridge.pilot.DeadReckon
import io.github.th3s1nc.osmandhudbridge.pilot.GpsLogLogic
import io.github.th3s1nc.osmandhudbridge.pilot.PilotRoute
import io.github.th3s1nc.osmandhudbridge.pilot.Replan
import io.github.th3s1nc.osmandhudbridge.pilot.RouteSplice
import io.github.th3s1nc.osmandhudbridge.pilot.RouteWalk
import io.github.th3s1nc.osmandhudbridge.pilot.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gerade Route nach Norden: n Punkte, je 0,001 Grad (ca. 111 m) auseinander, Start bei [lat0]. */
private fun line(n: Int, lat0: Double = 48.0, lon: Double = 12.0, hints: List<Pair<Int, Int>> = emptyList(), wps: IntArray? = null): PilotRoute {
    val lats = DoubleArray(n) { lat0 + it * 0.001 }
    val lons = DoubleArray(n) { lon }
    val w = wps ?: intArrayOf(0, n - 1)
    return PilotRoute.build(
        lats, lons, FloatArray(n), n * 8, n, IntArray(hints.size) { hints[it].first }, IntArray(hints.size) { hints[it].second },
        IntArray(hints.size), w, BooleanArray(w.size)
    )
}

class ArrowFilterTest {
    @Test fun richtungErstAbZehnKmh() {
        val f = ArrowFilter()
        assertNull(f.updateHeading(90.0, 5f))
        assertEquals(90.0, f.updateHeading(90.0, 12f)!!, 0.01)
        assertEquals(90.0, f.updateHeading(270.0, 5f)!!, 0.01)   // langsam: alte Richtung bleibt
    }
    @Test fun sprungBeiLangsamemTempoBrauchtZweiMessungen() {
        val f = ArrowFilter()
        f.updateHeading(90.0, 40f)
        assertEquals(90.0, f.updateHeading(270.0, 15f)!!, 0.01)    // erste Messung: ignoriert
        assertEquals(268.0, f.updateHeading(268.0, 15f)!!, 0.01)   // zweite bestätigt
    }
    @Test fun einzelnerAusreisserWirdIgnoriert() {
        val f = ArrowFilter()
        f.updateHeading(90.0, 40f)
        f.updateHeading(270.0, 15f)
        assertEquals(90.0, f.updateHeading(92.0, 15f)!!, 3.0)      // wieder normal: kein Umschalten
    }
    @Test fun schnellerSprungZaehltSofort() {
        val f = ArrowFilter()
        f.updateHeading(90.0, 60f)
        assertEquals(270.0, f.updateHeading(270.0, 60f)!!, 0.01)
    }
    @Test fun kleineAenderungWirdGeglaettetUeberNordGrenze() {
        val f = ArrowFilter()
        f.updateHeading(350.0, 50f)
        val h = f.updateHeading(10.0, 50f)!!
        assertTrue(h > 355.0 || h < 8.0)
    }
    @Test fun stillstandHaeltPfeilFest() {
        val f = ArrowFilter()
        val last = Pair(48.0, 12.0)
        val a = f.position(48.00005, 12.00004, null, last, 0f, 8f)
        assertTrue(a.frozen); assertEquals(48.0, a.lat, 1e-9)
        val b = f.position(48.00008, 12.0, Pair(48.00008, 12.0), last, 1f, 8f)
        assertTrue(b.frozen); assertEquals(48.0, b.lat, 1e-9)
    }
    @Test fun stillstandGibtFreiWennPositionWeitWegWandert() {
        val f = ArrowFilter()
        f.position(48.0, 12.0, null, Pair(48.0, 12.0), 0f, 8f)
        val far = f.position(48.001, 12.0, null, Pair(48.0, 12.0), 1f, 8f)   // 111 m weg
        assertFalse(far.frozen); assertEquals(48.001, far.lat, 1e-9)
    }
    @Test fun anfahrenGibtFrei() {
        val f = ArrowFilter()
        f.position(48.0, 12.0, null, Pair(48.0, 12.0), 0f, 8f)
        val m = f.position(48.00003, 12.0, Pair(48.00003, 12.0), Pair(48.0, 12.0), 9f, 8f)
        assertFalse(m.frozen); assertEquals(48.00003, m.lat, 1e-9)
    }
    @Test fun ohneLetztePositionKeinFesthalten() {
        val f = ArrowFilter()
        assertFalse(f.position(48.0, 12.0, null, null, 0f, 8f).frozen)
    }
    @Test fun einrastenWennVorhanden() {
        val f = ArrowFilter()
        val s = f.position(48.0, 12.0, Pair(48.00001, 12.00001), Pair(48.0, 12.0), 30f, 5f)
        assertEquals(48.00001, s.lat, 1e-9)
    }
}

class RouteWalkTest {
    @Test fun punktNachStrecke() {
        val r = line(11)
        val at = RouteWalk.pointAt(r, 555.0)!!
        assertEquals(48.005, at.lat, 0.0002)
        assertEquals(0.0, at.bearing, 0.5)
    }
    @Test fun ueberDasEndeHinausBleibtAmZiel() {
        val r = line(11)
        assertEquals(48.01, RouteWalk.pointAt(r, 99999.0)!!.lat, 1e-6)
        assertEquals(48.0, RouteWalk.pointAt(r, -5.0)!!.lat, 1e-6)
    }
}

class DeadReckonTest {
    @Test fun nurBeiAusfallUndTempo() {
        assertFalse(DeadReckon.active(1_000, 50f))
        assertTrue(DeadReckon.active(5_000, 50f))
        assertFalse(DeadReckon.active(5_000, 8f))
        assertFalse(DeadReckon.active(130_000, 50f))
    }
    @Test fun strecke() {
        assertEquals(100.0, DeadReckon.meters(36f, 10_000), 0.01)
    }
}

class RouteSpliceTest {
    @Test fun zuKurzeRestStreckeKeinWiedereinstieg() {
        assertNull(RouteSplice.target(line(60), 100))   // 6,5 km Rest
    }
    @Test fun einstiegVierKilometerVoraus() {
        val r = line(200)
        val t = RouteSplice.target(r, 1000)!!
        assertTrue(t.atM in 5000..5200)
    }
    @Test fun einstiegBeiNaechstemWegpunkt() {
        val r = line(200, wps = intArrayOf(0, 20, 199))
        val t = RouteSplice.target(r, 1000)!!      // Wegpunkt bei ca. 2,2 km liegt näher als 5 km
        assertEquals(20, t.index)
    }
    @Test fun wegpunkteVorDemEinstiegWerdenBeachtet() {
        val r = line(200, wps = intArrayOf(0, 90, 199))
        val t = RouteSplice.target(r, 1000)!!
        assertTrue(t.index < 90)
    }
    @Test fun zusammensetzen() {
        val old = line(200, hints = listOf(Pair(20, BrouterCmd.TR), Pair(150, BrouterCmd.TL)), wps = intArrayOf(0, 199))
        val t = RouteSplice.target(old, 1000)!!
        // kurzes Stück: 10 Punkte von der Position (Index 8) zum Einstiegspunkt, mit eigenem Hinweis
        val short = PilotRoute.build(
            DoubleArray(10) { 48.0 + 0.001 * 8 + it * 0.0005 }, DoubleArray(10) { 12.0005 }, FloatArray(10), 40, 5,
            intArrayOf(3), intArrayOf(BrouterCmd.TL), intArrayOf(0), intArrayOf(0, 9), BooleanArray(2)
        )
        val r = RouteSplice.splice(old, t, short, 1000)!!
        assertEquals(short.points.size + (old.points.size - t.index - 1), r.points.size)
        assertEquals(r.points.size, r.cumM.size)
        assertEquals(short.totalM, r.cumM[short.points.size - 1])
        assertEquals(old.totalM - old.cumM[t.index] + short.totalM, r.totalM)
        // der Hinweis aus dem kurzen Stück steht vor allen Resten, Hinweise der alten Route nach dem Einstieg bleiben
        assertTrue(r.instructions.any { it.brCmd == BrouterCmd.TL && it.pointIndex == 3 })
        assertFalse(r.instructions.any { it.brCmd == BrouterCmd.TR })   // Index 20 lag vor dem Einstieg
        assertTrue(r.instructions.any { it.brCmd == BrouterCmd.TL && it.pointIndex > short.points.size })
        assertEquals(BrouterCmd.END, r.instructions.last().brCmd)
        assertEquals(r.points.size - 1, r.instructions.last().pointIndex)
        // Wegpunkt-Indizes passen zur Wegpunkt-Liste von Replan.remaining (Position + Ziel)
        val wps = Replan.remaining(old, listOf(Waypoint(48.0, 12.0), Waypoint(48.199, 12.0)), 1000, 48.008, 12.0005)
        assertEquals(wps.size, r.waypointIndex.size)
        assertEquals(r.points.size - 1, r.waypointIndex.last())
    }
    @Test fun wegpunktVorDemEinstiegIstNichtZusammensetzbar() {
        val old = line(200, wps = intArrayOf(0, 30, 199))
        val t = RouteSplice.Target(120, old.cumM[120])
        val short = line(5)
        assertNull(RouteSplice.splice(old, t, short, 100))   // Wegpunkt 30 liegt vor dem Einstieg und noch vor uns
    }
    @Test fun grosserUmwegWirdAbgelehnt() {
        val short = line(60)    // ca. 6,5 km
        assertFalse(RouteSplice.acceptable(short, 48.0, 12.0, 48.01, 12.0))   // Luftlinie nur 1,1 km
        assertTrue(RouteSplice.acceptable(line(12), 48.0, 12.0, 48.011, 12.0))
    }
}

class GpsLogLogicTest {
    @Test fun zeileHatAlleSpalten() {
        val r = GpsLogLogic.row(1000, "nav", 48.1, 12.2, 8f, 42.5f, 90f, 91.0, 48.1, 12.2, true, false, 3, "Test;x")
        assertEquals(GpsLogLogic.HEADER.split(";").size, r.split(";").size)
        assertTrue(r.contains("42.5"))
        assertTrue(r.endsWith("Test,x"))
    }
    @Test fun ereigniszeileOhnePosition() {
        val r = GpsLogLogic.eventRow(5, "Neuberechnung")
        assertEquals(GpsLogLogic.HEADER.split(";").size, r.split(";").size)
        assertFalse(r.contains("NaN"))
    }
    @Test fun kuerzenBehaeltKopfUndEnde() {
        val line = "x".repeat(99) + "\n"
        val big = GpsLogLogic.HEADER + "\n" + line.repeat(60_000)
        val t = GpsLogLogic.trim(big)
        assertTrue(t.length < big.length)
        assertTrue(t.startsWith(GpsLogLogic.HEADER))
        assertTrue(t.endsWith(line))
        assertEquals(small(), GpsLogLogic.trim(small()))
    }
    private fun small() = GpsLogLogic.HEADER + "\nabc\n"
    @Test fun ablauf() {
        assertFalse(GpsLogLogic.expired(1000, 1000 + 6L * 24 * 3600 * 1000))
        assertTrue(GpsLogLogic.expired(1000, 1000 + 8L * 24 * 3600 * 1000))
        assertFalse(GpsLogLogic.expired(0, 99999999999))
    }
    @Test fun groesse() {
        assertEquals("3,2 MB", GpsLogLogic.sizeText(3_200_000))
        assertEquals("12 KB", GpsLogLogic.sizeText(12_400))
    }
}
