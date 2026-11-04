package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd
import io.github.th3s1nc.osmandhudbridge.pilot.Geo
import io.github.th3s1nc.osmandhudbridge.pilot.GpxRoute
import io.github.th3s1nc.osmandhudbridge.pilot.PilotAreas
import io.github.th3s1nc.osmandhudbridge.pilot.PilotRoute
import io.github.th3s1nc.osmandhudbridge.pilot.PilotTiles
import io.github.th3s1nc.osmandhudbridge.pilot.Replan
import io.github.th3s1nc.osmandhudbridge.pilot.Waypoint
import io.github.th3s1nc.osmandhudbridge.pilot.RouteSimulator
import io.github.th3s1nc.osmandhudbridge.protocol.NavCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val KURVIGER = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Kurviger" xmlns="http://www.topografix.com/GPX/1/1">
	<metadata><name>Testroute</name></metadata>
	<rte>
		<name>Testroute</name>
		<rtept lat="47.5000" lon="11.0000">
			<name>Start</name><sym>Flag, Green</sym><type>start</type>
			<extensions><trp:ViaPoint><trp:CalculationMode>Adventurous</trp:CalculationMode></trp:ViaPoint></extensions>
		</rtept>
		<rtept lat="47.5300" lon="11.0900">
			<name>Additional Waypoint</name><type>shaping</type>
			<extensions><trp:ShapingPoint/></extensions>
		</rtept>
		<rtept lat="47.6000" lon="11.8000">
			<name>Via Point 3</name><type>via</type>
		</rtept>
		<rtept lat="47.5300" lon="11.8100">
			<name>Additional Waypoint</name><type>shaping</type>
		</rtept>
		<rtept lat="47.5000" lon="11.0000">
			<name>Destination</name><type>destination</type>
		</rtept>
	</rte>
	<trk><name>Testroute</name><trkseg>
		<trkpt lat="47.5000" lon="11.0000"><ele>360.5</ele></trkpt>
	</trkseg></trk>
</gpx>"""

private val NUR_TRACK = """<gpx><trk><name>Nur Track</name><trkseg>
${(0..100).joinToString("\n") { """<trkpt lat="${48.0 + it * 0.001}" lon="11.0"><ele>400</ele></trkpt>""" }}
</trkseg></trk></gpx>"""

class GpxRouteTest {
    @Test fun kurvigerRoutenpunkteUndZwischenziel() {
        val p = GpxRoute.parse(KURVIGER)!!
        assertEquals("Testroute", p.name)
        assertFalse(p.fromTrack)
        assertEquals(5, p.waypoints.size)
        assertEquals(47.5, p.waypoints[0].lat, 1e-9)
        assertEquals(11.0, p.waypoints[0].lon, 1e-9)
        assertEquals(listOf(false, false, true, false, false), p.waypoints.map { it.visit })
    }

    @Test fun nurTrackWirdAbgetastet() {
        val p = GpxRoute.parse(NUR_TRACK, trackEveryM = 4000)!!
        assertTrue(p.fromTrack)
        assertEquals("Nur Track", p.name)
        // 100 * 111 m = 11 km: Start, 2 Formpunkte (4,4 km Abstand), Ziel
        assertTrue("Anzahl ${p.waypoints.size}", p.waypoints.size in 4..5)
        assertTrue(p.waypoints.none { it.visit })
        assertEquals(48.1, p.waypoints.last().lat, 1e-9)
    }

    @Test fun keineBrauchbarenPunkteGebenNull() {
        assertNull(GpxRoute.parse("<gpx></gpx>"))
        assertNull(GpxRoute.parse("""<gpx><rte><rtept lat="48" lon="11"/></rte></gpx>"""))
    }

    @Test fun selbstschliessendePunkte() {
        val p = GpxRoute.parse("""<gpx><rte><rtept lat="48.0" lon="11.0"/><rtept lat="48.5" lon="11.5"/></rte></gpx>""")!!
        assertEquals(2, p.waypoints.size)
        assertEquals(11.5, p.waypoints[1].lon, 1e-9)
    }
}

class PilotTilesTest {
    @Test fun kachelnamen() {
        assertEquals("E10_N45", PilotTiles.name(47.9, 10.15))
        assertEquals("E5_N50", PilotTiles.name(50.0025, 8.72)) // Dreieich, wie die BRouter-Testdaten
        assertEquals("E5_N45", PilotTiles.name(49.8, 9.9)) // Würzburg liegt knapp westlich von 10 Grad
        assertEquals("W5_N50", PilotTiles.name(51.5, -0.1)) // London
        assertEquals("E15_S35", PilotTiles.name(-33.9, 18.4)) // Kapstadt
    }

    @Test fun kachelnEntlangEinerRoute() {
        val t = PilotTiles.along(listOf(47.91 to 10.15, 48.0 to 10.9, 48.2 to 10.5, 48.6 to 9.8))
        assertEquals(listOf("E10_N45", "E5_N45"), t)
    }

    @Test fun gebietslisteHatKeineDoppelten() {
        val tiles = PilotAreas.all.map { it.tile }
        assertEquals(tiles.size, tiles.toSet().size)
        assertNotNull(PilotAreas.byTile("E10_N45"))
        assertTrue(PilotAreas.all.all { it.de.isNotBlank() && it.en.isNotBlank() })
        assertEquals("https://brouter.de/brouter/segments4/E10_N45.rd5", PilotTiles.url("E10_N45"))
    }
}

class PilotModelTest {
    /** Gerade Strecke nach Norden, 1 km (11 Punkte), Hinweise bei 200 m (rechts), 500 m (Kreisverkehr Ausfahrt 2). */
    private fun route(): PilotRoute {
        val n = 11
        val lats = DoubleArray(n) { 48.0 + it * 0.001 * (1000.0 / 111.32) / 10.0 * 1.0 }
        val lons = DoubleArray(n) { 11.0 }
        val ele = FloatArray(n) { 400f }
        return PilotRoute.build(
            lats, lons, ele, 120, 5,
            intArrayOf(2, 5, 7), intArrayOf(BrouterCmd.TR, BrouterCmd.RNDB, BrouterCmd.OFFR), intArrayOf(0, 2, 0),
            intArrayOf(0, 8, 10), booleanArrayOf(false, true, false)
        )
    }

    @Test fun streckeUndAnweisungenSortiert() {
        val r = route()
        assertTrue("Gesamt ${r.totalM}", r.totalM in 990..1010)
        // OFFR hat keinen Pfeil -> fällt weg; dazu kommen Zwischenziel (Punkt 8) und Ziel
        assertEquals(listOf(BrouterCmd.TR, BrouterCmd.RNDB, BrouterCmd.VIA, BrouterCmd.END), r.instructions.map { it.brCmd })
        assertEquals(NavCommand.TURN_RIGHT, r.instructions[0].command)
        assertEquals(NavCommand.ROUNDABOUT, r.instructions[1].command)
        assertEquals(2, r.instructions[1].exit)
        assertEquals(NavCommand.VIA, r.instructions[2].command)
        assertEquals(NavCommand.GOAL, r.instructions.last().command)
        assertTrue(r.instructions.zipWithNext().all { it.first.atM <= it.second.atM })
    }

    @Test fun hudZuordnungWieBeiOsmand() {
        assertEquals(NavCommand.KEEP_LEFT, BrouterCmd.toHud(BrouterCmd.TSLL, 0))
        assertEquals(NavCommand.KEEP_RIGHT, BrouterCmd.toHud(BrouterCmd.TSLR, 0))
        assertEquals(NavCommand.SHARP_LEFT, BrouterCmd.toHud(BrouterCmd.TSHL, 0))
        assertEquals(NavCommand.UTURN_LEFT, BrouterCmd.toHud(BrouterCmd.TU, 0))
        assertEquals(NavCommand.ROUNDABOUT_LEFT, BrouterCmd.toHud(BrouterCmd.RNLB, 1))
        assertNull(BrouterCmd.toHud(BrouterCmd.OFFR, 0))
        assertNull(BrouterCmd.toHud(BrouterCmd.BL, 0))
    }

    @Test fun texteDeutschUndEnglisch() {
        val old = Tx.en
        try {
            Tx.en = false
            assertEquals("Rechts abbiegen", BrouterCmd.text(BrouterCmd.TR, 0))
            assertEquals("Kreisverkehr, 3. Ausfahrt", BrouterCmd.text(BrouterCmd.RNDB, 3))
            assertEquals("Ziel", BrouterCmd.text(BrouterCmd.END, 0))
            Tx.en = true
            assertEquals("Turn right", BrouterCmd.text(BrouterCmd.TR, 0))
            assertEquals("Roundabout, exit 3", BrouterCmd.text(BrouterCmd.RNDB, 3))
            assertEquals("Destination", BrouterCmd.text(BrouterCmd.END, 0))
            assertEquals("Waypoint", BrouterCmd.text(BrouterCmd.VIA, 0))
        } finally {
            Tx.en = old
        }
    }

    @Test fun neuplanungNimmtNurWegpunkteVorDir() {
        val r = route() // Wegpunkte bei Punkt 0, 8 (Zwischenziel), 10
        val wps = listOf(Waypoint(48.0, 11.0), Waypoint(48.007, 11.0, true), Waypoint(48.009, 11.0))
        // früh auf der Strecke: Zwischenziel und Ziel liegen noch vor uns
        val a = Replan.remaining(r, wps, 100, 48.001, 11.002)
        assertEquals(3, a.size)
        assertEquals(48.001, a[0].lat, 1e-9)
        assertFalse(a[0].visit)
        assertTrue(a[1].visit)
        assertFalse(a[2].visit)
        // hinter dem Zwischenziel: nur noch Position und Ziel
        val b = Replan.remaining(r, wps, 900, 48.0085, 11.002)
        assertEquals(2, b.size)
        assertEquals(48.009, b[1].lat, 1e-9)
    }

    @Test fun geoRichtungUndAbstand() {
        assertEquals(111.3, Geo.distance(48.0, 11.0, 48.001, 11.0), 0.5)
        assertEquals(0.0, Geo.bearing(48.0, 11.0, 48.01, 11.0), 0.5)
        assertEquals(90.0, Geo.bearing(48.0, 11.0, 48.0, 11.01), 0.5)
        val (la, lo) = Geo.offset(48.0, 11.0, 90.0, 200.0)
        assertEquals(200.0, Geo.distance(48.0, 11.0, la, lo), 1.0)
    }
}

class RouteSimulatorTest {
    private fun route(): PilotRoute {
        val n = 11
        val lats = DoubleArray(n) { 48.0 + it * 0.001 * (1000.0 / 111.32) / 10.0 }
        val lons = DoubleArray(n) { 11.0 }
        return PilotRoute.build(
            lats, lons, FloatArray(n), 120, 0,
            intArrayOf(2, 5), intArrayOf(BrouterCmd.TR, BrouterCmd.RNDB), intArrayOf(0, 2),
            intArrayOf(0, 10), booleanArrayOf(false, false)
        )
    }

    @Test fun startZeigtErsteAnweisung() {
        val s = RouteSimulator(route()).state()
        assertEquals(0, s.distM)
        assertEquals(BrouterCmd.TR, s.next!!.brCmd)
        assertTrue("Abstand ${s.nextDistM}", s.nextDistM in 195..205)
        assertFalse(s.finished)
        assertEquals(0.0, s.headingDeg, 1.0)
    }

    @Test fun bewegungMitTempoUndZeitraffer() {
        val sim = RouteSimulator(route())
        sim.speedKmh = 36.0 // 10 m/s
        sim.advance(10.0)
        assertEquals(100, sim.state().distM)
        sim.timeFactor = 10.0
        sim.advance(1.0)
        assertEquals(200, sim.state().distM)
        // Anweisung "rechts" liegt bei ~200 m: knapp davor noch dran, danach die nächste
        sim.seek(150.0)
        assertEquals(BrouterCmd.TR, sim.state().next!!.brCmd)
        sim.seek(300.0)
        assertEquals(BrouterCmd.RNDB, sim.state().next!!.brCmd)
    }

    @Test fun zielErreichtUndStehenBleiben() {
        val sim = RouteSimulator(route())
        sim.speedKmh = 360.0
        sim.timeFactor = 50.0
        sim.advance(60.0)
        val s = sim.state()
        assertTrue(s.finished)
        assertEquals(0, s.remainingM)
        assertEquals(BrouterCmd.END, s.next!!.brCmd)
        sim.advance(10.0)
        assertEquals(s.distM, sim.state().distM)
        sim.restart()
        assertFalse(sim.finished)
    }

    @Test fun restzeitNimmtAb() {
        val sim = RouteSimulator(route())
        val a = sim.state().remainingSec
        sim.seek(500.0)
        val b = sim.state().remainingSec
        assertEquals(120, a)
        assertTrue("Rest $b", b in 55..65)
    }

    @Test fun abweichungLiegtSeitlich() {
        val sim = RouteSimulator(route())
        sim.seek(500.0)
        val (la, lo) = sim.offRoutePoint(200.0)
        val s = sim.state()
        assertEquals(200.0, Geo.distance(s.lat, s.lon, la, lo), 2.0)
        assertTrue(lo > 11.0) // Fahrt nach Norden: 90 Grad rechts ist Osten
    }
}
