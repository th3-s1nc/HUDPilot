package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd
import io.github.th3s1nc.osmandhudbridge.pilot.Guide
import io.github.th3s1nc.osmandhudbridge.pilot.PilotRoute
import io.github.th3s1nc.osmandhudbridge.pilot.RouteSimulator
import io.github.th3s1nc.osmandhudbridge.pilot.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    // Gerade Strecke nach Norden, ~1000 m, Punkte alle 100 m; rechts bei ~200 m, Kreisverkehr bei ~500 m
    private fun route(): PilotRoute {
        val n = 11
        val lats = DoubleArray(n) { 48.0 + it * 100.0 / 111_320.0 }
        val lons = DoubleArray(n) { 11.0 }
        return PilotRoute.build(
            lats, lons, FloatArray(n), 120, 0,
            intArrayOf(2, 5), intArrayOf(BrouterCmd.TR, BrouterCmd.RNDB), intArrayOf(0, 2),
            intArrayOf(0, 10), booleanArrayOf(false, false)
        )
    }
    private val wps = listOf(Waypoint(48.0, 11.0), Waypoint(48.009, 11.0))

    @Test fun folgtDerRouteUndZeigtNaechsteAnweisung() {
        val g = Guide(route(), wps)
        val s = g.update(48.0 + 50.0 / 111_320.0, 11.0)
        assertEquals(50, s.progressM)
        assertEquals(BrouterCmd.TR, s.next!!.brCmd)
        assertTrue("Abstand ${s.nextDistM}", s.nextDistM in 145..155)
        assertFalse(s.offRoute)
    }

    @Test fun anweisungWechseltNachDemAbbiegen() {
        val g = Guide(route(), wps)
        g.update(48.0 + 190.0 / 111_320.0, 11.0)
        val s = g.update(48.0 + 260.0 / 111_320.0, 11.0)
        assertEquals(BrouterCmd.RNDB, s.next!!.brCmd)
    }

    @Test fun abweichungErstNachMehrerenPositionenUndMitPause() {
        val g = Guide(route(), wps)
        g.update(48.0 + 100.0 / 111_320.0, 11.0)
        val side = 11.0 + 0.001 // ~75 m daneben
        val lat = 48.0 + 100.0 / 111_320.0
        assertTrue(g.update(lat, side).offRoute)
        g.update(lat, side)
        assertFalse(g.shouldReroute(100_000))
        g.update(lat, side)
        assertTrue(g.shouldReroute(100_000))
        // einmal zurück auf der Route setzt den Zähler zurück
        g.update(lat, 11.0)
        assertFalse(g.shouldReroute(100_000))
    }

    @Test fun neuberechnungHatWartezeit() {
        val g = Guide(route(), wps)
        val lat = 48.0 + 100.0 / 111_320.0
        repeat(3) { g.update(lat, 11.001) }
        assertTrue(g.shouldReroute(50_000))
        g.rerouteFailed(50_000)
        assertFalse(g.shouldReroute(52_000))
        assertTrue(g.shouldReroute(60_000))
    }

    @Test fun zielWirdErkannt() {
        val g = Guide(route(), wps)
        val s = g.update(48.0 + 995.0 / 111_320.0, 11.0)
        assertTrue(s.finished)
        assertEquals(BrouterCmd.END, s.next!!.brCmd)
    }

    @Test fun simulationUndGuideSindSichEinig() {
        val r = route()
        val sim = RouteSimulator(r)
        val g = Guide(r, wps)
        sim.speedKmh = 36.0
        var d = -1
        for (i in 0 until 90) {
            sim.advance(1.0)
            val ss = sim.state()
            val gs = g.update(ss.lat, ss.lon)
            assertTrue("bei ${ss.distM}", Math.abs(ss.distM - gs.progressM) <= 3)
            assertFalse(gs.offRoute)
            d = gs.remainingM
        }
        assertTrue(d < 120)
    }

    @Test fun ungenauesGpsLoestKeineNeuberechnungAus() {
        val g = Guide(route(), wps)
        // 70 m neben der Route, aber GPS nur auf 80 m genau: Schwelle wächst auf 120 m, also nicht "daneben"
        var s = g.update(48.0 + 400.0 / 111_320.0, 11.0 + 70.0 / (111_320.0 * Math.cos(Math.toRadians(48.0))), 80f)
        assertFalse(s.offRoute)
        // Sehr ungenau (150 m): zählt weder als daneben noch als darauf
        s = g.update(48.0 + 400.0 / 111_320.0, 11.0 + 500.0 / (111_320.0 * Math.cos(Math.toRadians(48.0))), 150f)
        assertFalse(s.offRoute)
        assertEquals(0, g.offFixes)
    }

    @Test fun schnelleWiederholungenWartenLaenger() {
        val g = Guide(route(), wps)
        assertEquals(5_000L, g.cooldownMs())
        g.setRoute(route(), wps, 100_000)      // erste Neuberechnung
        assertEquals(5_000L, g.cooldownMs())
        g.setRoute(route(), wps, 120_000)      // schon nach 20 s wieder
        assertEquals(10_000L, g.cooldownMs())
        g.setRoute(route(), wps, 140_000)
        assertEquals(15_000L, g.cooldownMs())
        g.setRoute(route(), wps, 150_000)
        assertEquals(15_000L, g.cooldownMs())  // mehr als 15 s wird nie gewartet
        g.setRoute(route(), wps, 400_000)      // lange Ruhe: wieder normal
        assertEquals(5_000L, g.cooldownMs())
    }

    @Test fun weitWegWartetNurDreiSekunden() {
        val g = Guide(route(), wps)
        val lat = 48.0 + 100.0 / 111_320.0
        g.setRoute(route(), wps, 100_000)
        g.setRoute(route(), wps, 105_000)      // Wartezeit jetzt 10 s
        repeat(3) { g.update(lat, 11.002) }    // ~150 m daneben
        assertFalse(g.shouldReroute(106_000))  // unter 3 s
        assertTrue(g.shouldReroute(109_000))   // über 3 s, obwohl noch keine 10 s
    }

    @Test fun gefahreneStreckeBleibtNachNeuberechnung() {
        val g = Guide(route(), wps)
        val s1 = g.update(48.0 + 400.0 / 111_320.0, 11.0)
        g.setRoute(route(), wps, 1_000)
        val s2 = g.update(48.0 + 10.0 / 111_320.0, 11.0)
        assertTrue(s2.doneM >= s1.progressM)
        assertEquals(10, s2.progressM)
    }

    @Test fun nachNeuberechnungWiederVonVorn() {
        val g = Guide(route(), wps)
        g.update(48.0 + 400.0 / 111_320.0, 11.0)
        val r2 = route()
        g.setRoute(r2, wps, 1_000)
        val s = g.update(48.0 + 10.0 / 111_320.0, 11.0)
        assertEquals(10, s.progressM)
        assertFalse(g.shouldReroute(1_500))
    }
}

class RouteGpxTest {
    @Test fun schreibtTrackUndWirdWiederGelesen() {
        val n = 4
        val r = PilotRoute.build(
            DoubleArray(n) { 48.0 + it * 0.001 }, DoubleArray(n) { 11.0 + it * 0.001 }, FloatArray(n) { 500f + it },
            60, 3, intArrayOf(1), intArrayOf(BrouterCmd.TR), intArrayOf(0), intArrayOf(0, 3), booleanArrayOf(false, false)
        )
        val xml = io.github.th3s1nc.osmandhudbridge.pilot.RouteGpx.write(r, "Test & <Route>")
        assertTrue(xml.contains("Test &amp; &lt;Route&gt;"))
        assertEquals(4, Regex("<trkpt ").findAll(xml).count())
        assertTrue(xml.contains("<ele>501.0</ele>"))
        val back = io.github.th3s1nc.osmandhudbridge.pilot.GpxRoute.parse(xml)!!
        assertTrue(back.waypoints.size >= 2)
    }
}

class MapNightTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        java.time.ZonedDateTime.of(y, mo, d, h, mi, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun sonnenstandPasst() {
        val sun = io.github.th3s1nc.osmandhudbridge.pilot.Sun
        // Frankfurt am Sonnenhöchststand im Juni: um 11 Uhr UTC etwa 62 Grad
        val noon = sun.elevationDeg(50.1, 8.7, utc(2026, 6, 21, 11, 0))
        assertTrue("Mittag $noon", noon in 58.0..66.0)
        // Mitternacht: weit unter dem Horizont
        assertTrue(sun.elevationDeg(50.1, 8.7, utc(2026, 12, 21, 0, 0)) < -40.0)
    }

    @Test fun dunkelNachSonnenuntergang() {
        val sun = io.github.th3s1nc.osmandhudbridge.pilot.Sun
        // Ende Oktober: Sonnenuntergang in Frankfurt ca. 16:45 UTC
        assertFalse(sun.isDark(50.1, 8.7, utc(2026, 10, 31, 12, 0)))
        assertFalse(sun.isDark(50.1, 8.7, utc(2026, 10, 31, 16, 15)))
        assertTrue(sun.isDark(50.1, 8.7, utc(2026, 10, 31, 18, 0)))
        // Sommer: um 20 Uhr UTC (22 Uhr MESZ) noch Dämmerung, aber nachts um 22 UTC dunkel
        assertTrue(sun.isDark(50.1, 8.7, utc(2026, 6, 21, 23, 0)))
        assertFalse(sun.isDark(50.1, 8.7, utc(2026, 6, 21, 17, 0)))
    }

    @Test fun tunnelDurchStrassenkennzeichen() {
        val t = io.github.th3s1nc.osmandhudbridge.pilot.TunnelWatch()
        assertFalse(t.update(0, 500, 80f, false))
        assertTrue(t.update(1000, 500, 80f, true))
        // draußen, aber noch kurz dunkel
        assertTrue(t.update(2000, 500, 80f, false))
        assertTrue(t.update(4000, 500, 80f, false))
        assertFalse(t.update(5500, 500, 80f, false))
    }

    @Test fun tunnelDurchGpsLuecke() {
        val t = io.github.th3s1nc.osmandhudbridge.pilot.TunnelWatch()
        // GPS 3 s weg: noch kein Tunnel
        assertFalse(t.update(0, 3000, 90f, false))
        assertTrue(t.update(1000, 6000, 90f, false))
        // im Stand fehlt das GPS auch einmal: kein Tunnel
        val u = io.github.th3s1nc.osmandhudbridge.pilot.TunnelWatch()
        assertFalse(u.update(0, 20000, 3f, false))
        // GPS wieder da: nach kurzer Haltezeit hell
        assertTrue(t.update(2000, 300, 90f, false))
        assertFalse(t.update(6000, 300, 90f, false))
    }

    @Test fun auswahlDunkelHell() {
        val m = io.github.th3s1nc.osmandhudbridge.pilot.MapNight
        assertFalse(m.dark(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.DAY, true, true))
        assertTrue(m.dark(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.NIGHT, false, false))
        assertTrue(m.dark(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.AUTO, true, false))
        assertTrue(m.dark(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.AUTO, false, true))
        assertFalse(m.dark(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.AUTO, false, false))
        assertEquals(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.AUTO, io.github.th3s1nc.osmandhudbridge.pilot.NightPref.parse(null))
        assertEquals(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.NIGHT, io.github.th3s1nc.osmandhudbridge.pilot.NightPref.parse("NIGHT"))
        assertEquals(io.github.th3s1nc.osmandhudbridge.pilot.NightPref.DAY, io.github.th3s1nc.osmandhudbridge.pilot.NightPref.AUTO.next())
    }
}

class MapOverlayTest {
    private val o = io.github.th3s1nc.osmandhudbridge.pilot.MapOverlay

    @Test fun pfeile() {
        assertEquals("↱", o.glyph(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 0))
        assertEquals("↰", o.glyph(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 0))
        assertEquals("↑", o.glyph(null, 0))
        assertEquals("⟳2", o.glyph(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.ROUNDABOUT_RIGHT, 2))
        assertEquals("⟳", o.glyph(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.ROUNDABOUT, 0))
        assertEquals("⚑", o.glyph(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.GOAL, 0))
        // jeder Befehl hat ein Zeichen
        for (c in io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.values()) assertTrue(o.glyph(c, 0).isNotEmpty())
    }

    @Test fun entfernungen() {
        assertEquals("80 m", o.dist(82))
        assertEquals("350 m", o.dist(349))
        assertEquals("1,2 km", o.dist(1234))
        assertEquals("42,6 km", o.km(42_600))
    }

    @Test fun zeiten() {
        assertEquals("47 min", o.remaining(47 * 60))
        assertEquals("1:05 h", o.remaining(65 * 60))
        // 20:00 UTC + 1 h Zone + 47 min = 21:47
        val t = 20L * 3600_000L
        assertEquals("21:47", o.eta(t, 47 * 60, 3600_000L))
        // über Mitternacht
        assertEquals("00:30", o.eta(23L * 3600_000L, 90 * 60, 0L))
    }

    @Test fun kompassUndFortschritt() {
        assertEquals("N", o.compass(0f))
        assertEquals("N", o.compass(359f))
        assertEquals("O", o.compass(90f))
        assertEquals("SW", o.compass(225f))
        assertTrue(Math.abs(o.progress(250, 750) - 0.25f) < 0.0001f)
        assertTrue(o.progress(0, 0) == 0f)
    }

    @Test fun zuSchnell() {
        assertTrue(o.over(101.4f, 100))
        assertFalse(o.over(100.2f, 100))
        assertFalse(o.over(130f, 0))
    }
}

class MapHazardsTest {
    private val kind = io.github.th3s1nc.osmandhudbridge.pilot.HazardKind
    private fun hz(id: Long, dN: Double, dE: Double, k: Int) =
        io.github.th3s1nc.osmandhudbridge.pilot.Hazard(id, 48.0 + dN / 111_320.0, 11.0 + dE / (111_320.0 * Math.cos(Math.toRadians(48.0))), k)

    // Gerade Strecke nach Norden, 3000 m, Punkte alle 100 m
    private fun route(): PilotRoute {
        val n = 31
        val lats = DoubleArray(n) { 48.0 + it * 100.0 / 111_320.0 }
        val lons = DoubleArray(n) { 11.0 }
        return PilotRoute.build(lats, lons, FloatArray(n), 300, 0, intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf(0, 30), booleanArrayOf(false, false))
    }

    private fun ids(l: List<io.github.th3s1nc.osmandhudbridge.pilot.ShownHazard>) = l.map { it.hazard.id }

    @Test fun mitRouteNurAufDerStrecke() {
        val all = listOf(
            hz(1, 800.0, 5.0, kind.CAMERA),         // auf der Strecke, 800 m voraus
            hz(2, 800.0, 80.0, kind.CAMERA),        // 80 m daneben: nicht
            hz(3, 200.0, 0.0, kind.ZEBRA),          // 200 m voraus: ja
            hz(4, 600.0, 0.0, kind.ZEBRA),          // zu weit für Zebrastreifen
            hz(5, 2000.0, 0.0, kind.LEVEL_CROSSING),// Bahnübergang weit voraus: ja
            hz(6, -300.0, 0.0, kind.CAMERA)         // hinter uns: nicht
        )
        val r = io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.select(all, 48.0, 11.0, 0.0, route(), 0)
        assertEquals(listOf(3L, 1L, 5L), ids(r))
        assertTrue(Math.abs(r[1].distM - 800) <= 15)
    }

    @Test fun mitRouteWeitervorneGerechnet() {
        val all = listOf(hz(1, 1500.0, 0.0, kind.RED_LIGHT), hz(2, 1600.0, 0.0, kind.CALMING), hz(3, 1000.0, 0.0, kind.CAMERA))
        // wir sind schon bei 1400 m der Strecke
        val r = io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.select(all, 48.0 + 1400.0 / 111_320.0, 11.0, 0.0, route(), 1400)
        assertEquals(listOf(1L, 2L), ids(r))
    }

    @Test fun ohneRouteVorDirInFahrtrichtung() {
        val all = listOf(
            hz(1, 700.0, 0.0, kind.CAMERA),     // vor uns
            hz(2, -700.0, 0.0, kind.CAMERA),    // hinter uns
            hz(3, 200.0, 0.0, kind.ZEBRA),      // vor uns, nah
            hz(4, 500.0, 0.0, kind.ZEBRA),      // zu weit für Zebrastreifen
            hz(5, 2000.0, 0.0, kind.CAMERA)     // zu weit
        )
        val r = io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.select(all, 48.0, 11.0, 0.0, null, 0)
        assertEquals(listOf(3L, 1L), ids(r))
    }

    @Test fun begrenzungUndBeschriftung() {
        val many = (1..50).map { hz(it.toLong(), 100.0 + it * 10, 0.0, kind.CAMERA) }
        val r = io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.select(many, 48.0, 11.0, 0.0, null, 0)
        assertEquals(io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.MAX_SHOWN, r.size)
        assertEquals(1L, r[0].hazard.id)
        assertEquals("450 m", io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.label(447))
        assertEquals("1,2 km", io.github.th3s1nc.osmandhudbridge.pilot.MapHazards.label(1234))
        assertTrue(io.github.th3s1nc.osmandhudbridge.pilot.HazardKind.always(kind.LEVEL_CROSSING))
        assertFalse(io.github.th3s1nc.osmandhudbridge.pilot.HazardKind.always(kind.ZEBRA))
    }
}

class PlanTest {
    private fun p(n: String) = io.github.th3s1nc.osmandhudbridge.pilot.PlanPoint(50.0, 8.0, n)

    @Test fun appendDestMakesOldDestinationTheLastVia() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        assertTrue(pl.appendDest(p("A")))
        assertEquals("A", pl.dest!!.name)
        assertTrue(pl.vias.isEmpty())
        pl.addVia(p("V"))
        assertTrue(pl.appendDest(p("B")))
        assertEquals("B", pl.dest!!.name)
        assertEquals(listOf("V", "A"), pl.vias.map { it.name })
    }

    @Test fun appendDestRefusedWhenViasFull() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        pl.dest = p("Ziel")
        for (i in 1..io.github.th3s1nc.osmandhudbridge.pilot.Plan.MAX_VIAS) pl.addVia(p("V$i"))
        assertFalse(pl.appendDest(p("Neu")))
        assertEquals("Ziel", pl.dest!!.name)
    }

    @Test fun waypointsNeedDestinationAndStart() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        assertTrue(pl.waypoints(p("jetzt")) == null)
        pl.dest = p("Ziel")
        assertTrue(pl.waypoints(null) == null)
        val w = pl.waypoints(p("jetzt"))!!
        assertEquals(2, w.size)
        assertEquals("jetzt", w[0].name)
    }

    @Test fun viasAreVisitPointsInOrderAndChosenStartWins() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        pl.dest = p("Ziel"); pl.start = p("Start")
        pl.addVia(p("A")); pl.addVia(p("B"))
        val w = pl.waypoints(p("jetzt"))!!
        assertEquals(listOf("Start", "A", "B", "Ziel"), w.map { it.name })
        assertEquals(listOf(false, true, true, false), w.map { it.visit })
    }

    @Test fun maxTwentyFiveVias() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        for (i in 1..25) assertTrue(pl.addVia(p("v$i")))
        assertFalse(pl.addVia(p("zu viel")))
        assertEquals(25, pl.vias.size)
    }

    @Test fun formpunkteSindKeineZwischenzieleUndZaehlenNicht() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        pl.start = p("Start"); pl.dest = p("Ziel")
        for (i in 1..100) assertTrue(pl.addVia(io.github.th3s1nc.osmandhudbridge.pilot.PlanPoint(49.0 + i * 0.001, 12.0, "f$i", true)))
        assertEquals(0, pl.visibleCount())
        for (i in 1..25) assertTrue(pl.addVia(p("v$i")))
        assertFalse(pl.addVia(p("zu viel")))
        assertEquals(25, pl.visibleCount())
        val w = pl.waypoints(null)!!
        assertEquals(127, w.size)
        assertEquals(25, w.count { it.visit })
        assertEquals("f1", w[1].name)
        assertFalse(w[1].visit)
    }

    @Test fun moveAndRemoveVias() {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        pl.addVia(p("A")); pl.addVia(p("B")); pl.addVia(p("C"))
        pl.moveVia(0, 1)
        assertEquals(listOf("B", "A", "C"), pl.vias.map { it.name })
        pl.moveVia(0, -1) // geht nicht weiter nach oben
        assertEquals(listOf("B", "A", "C"), pl.vias.map { it.name })
        pl.removeVia(1)
        assertEquals(listOf("B", "C"), pl.vias.map { it.name })
    }

    @Test fun distanceAndFormat() {
        val d = io.github.th3s1nc.osmandhudbridge.pilot.Plan.distM(50.0, 8.0, 50.0, 8.01)
        assertTrue(d > 700 && d < 730)
        assertEquals("700 m", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.dist(710.0))
        assertEquals("12 km", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.dist(12400.0))
    }
}

class PlaceNameTest {
    @Test fun houseNumberFirstBecomesStreetAndNumber() {
        val d = "27, Hauptstraße, Neukirchen, Landkreis Cham, Bayern, 93047, Deutschland"
        assertEquals("Hauptstraße 27", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.nameOf("", d))
        assertEquals("Neukirchen, Landkreis Cham, Bayern, 93047, Deutschland", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.detailOf("", d))
    }

    @Test fun ownNameIsKept() {
        val d = "Gasthaus Adler, Hauptstraße, Neukirchen, Bayern"
        assertEquals("Gasthaus Adler", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.nameOf("Gasthaus Adler", d))
        assertEquals("Hauptstraße, Neukirchen, Bayern", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.detailOf("Gasthaus Adler", d))
    }

    @Test fun namelessNonNumberUsesFirstPart() {
        assertEquals("Neukirchen", io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat.nameOf("", "Neukirchen, Bayern"))
    }
}

class VoiceLogicTest {
    private fun ins(cmd: io.github.th3s1nc.osmandhudbridge.protocol.NavCommand, at: Int, exit: Int = 0) =
        io.github.th3s1nc.osmandhudbridge.pilot.Instruction(at, at, 0, exit, cmd)

    @Test fun earlyThenNowEachOnce() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val i = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 1000)
        assertTrue(v.step(i, 900, false, 50f, false, 0).isEmpty())          // noch zu weit
        val early = v.step(i, 340, false, 50f, false, 1_000)                // 50 km/h: früh bei ca. 350 m
        assertEquals(1, early.size)
        assertEquals("In 350 Metern links abbiegen", early[0])
        assertTrue(v.step(i, 300, false, 50f, false, 2_000).isEmpty())     // nicht doppelt
        val now = v.step(i, 40, false, 50f, false, 30_000)
        assertEquals(listOf("Jetzt links abbiegen"), now)
        assertTrue(v.step(i, 20, false, 50f, false, 32_000).isEmpty())
    }

    @Test fun dichtFolgendeAbbiegungWirdMitSofortAngesagt() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val a = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 1000)
        val b = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 1030)
        val early = v.step(a, 340, false, 50f, false, 1_000, b)
        assertEquals(listOf("In 350 Metern links abbiegen, danach sofort rechts abbiegen"), early)
    }

    @Test fun weitFolgendeAbbiegungOhneSofort() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val a = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 1000)
        val b = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 1200)
        assertEquals(listOf("In 350 Metern links abbiegen"), v.step(a, 340, false, 50f, false, 1_000, b))
    }

    @Test fun fastSpeedAnnouncesEarlier() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val i = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 5000)
        assertTrue(v.step(i, 1200, false, 100f, false, 0).isEmpty())
        assertEquals(1, v.step(i, 690, false, 100f, false, 1_000).size)    // 100 km/h: ca. 700 m
    }


    private fun insBr(cmd: io.github.th3s1nc.osmandhudbridge.protocol.NavCommand, br: Int, at: Int) =
        io.github.th3s1nc.osmandhudbridge.pilot.Instruction(at, at, br, 0, cmd)

    @Test fun slightTurnIsAnnouncedButKeepIsNot() {
        val nc = io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.KEEP_RIGHT
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val slight = insBr(nc, io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd.TSLR, 1000)
        assertEquals("In 350 Metern leicht rechts abbiegen", v.step(slight, 340, false, 50f, false, 1_000)[0])
        val v2 = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val keep = insBr(nc, io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd.KR, 1000)
        assertTrue(v2.step(keep, 100, false, 50f, false, 1_000).isEmpty())
        v2.many = true
        assertEquals("In 200 Metern rechts halten", v2.step(keep, 200, false, 50f, false, 10_000)[0])
    }

    @Test fun manyAddsFarAnnouncementAndThen() {
        val nc = io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        v.many = true
        val a = insBr(nc, io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd.TL, 5000)
        val b = insBr(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd.TR, 5150)
        assertTrue(v.step(a, 2500, false, 100f, false, 0).isEmpty())
        assertEquals("In 1,9 Kilometern links abbiegen", v.step(a, 1900, false, 100f, false, 1_000, b)[0])   // 100 km/h: Vorankündigung bei ca. 1,9 km
        assertEquals("In 950 Metern links abbiegen, danach rechts abbiegen", v.step(a, 950, false, 100f, false, 20_000, b)[0])
    }

    @Test fun roundaboutSaysExit() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val i = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.ROUNDABOUT, 100, 2)
        val t = v.step(i, 200, false, 30f, false, 0)
        assertEquals("In 200 Metern im Kreisverkehr die zweite Ausfahrt nehmen", t[0])
    }

    @Test fun straightAndGoalMakeNoAnnouncement() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        assertTrue(v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.STRAIGHT, 100), 100, false, 50f, false, 0).isEmpty())
        assertTrue(v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.GOAL, 200), 100, false, 50f, false, 0).isEmpty())
    }

    @Test fun viaThenGoalAndReroute() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val via = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.VIA, 100)
        assertTrue(v.step(via, 50, false, 50f, false, 0).isEmpty())
        val t = v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 900), 800, false, 50f, false, 10_000)
        assertEquals("Zwischenziel erreicht.", t[0])
        assertEquals(listOf("Route wird neu berechnet."), v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 900), 800, false, 50f, true, 20_000))
        assertTrue(v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 900), 800, false, 50f, true, 21_000).isEmpty()) // nur einmal je Neuberechnung
        assertEquals(listOf("Sie haben Ihr Ziel erreicht."), v.step(null, 0, true, 0f, false, 30_000))
        assertTrue(v.step(null, 0, true, 0f, false, 31_000).isEmpty())
    }

    @Test fun shortSegmentSkipsEarly() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        assertEquals(listOf("Jetzt rechts abbiegen"), v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 100), 30, false, 50f, false, 0))
    }

    @Test fun slightBendsAreNotAnnounced() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        assertTrue(v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.KEEP_RIGHT, 100), 30, false, 50f, false, 0).isEmpty())
        assertTrue(v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.KEEP_LEFT, 100), 30, false, 50f, false, 10_000).isEmpty())
        assertEquals(1, v.step(ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.SHARP_LEFT, 100), 30, false, 50f, false, 20_000).size)
    }

    @Test fun flippingNextInstructionNeverRepeats() {
        // "nächste Anweisung" springt zwischen zwei Abbiegungen hin und her: jede wird höchstens einmal angesagt
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val a = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 1000); val b = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 1100)
        var spoken = 0
        for (k in 0 until 40) spoken += v.step(if (k % 2 == 0) a else b, 30, false, 50f, false, k * 1_000L).size
        assertEquals(2, spoken)
    }

    @Test fun minimumGapBetweenAnnouncements() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val a = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 1000); val b = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_RIGHT, 1060)
        assertEquals(1, v.step(a, 30, false, 50f, false, 0).size)
        assertTrue(v.step(b, 30, false, 50f, false, 1_000).isEmpty())   // zu dicht dahinter: noch nicht
        assertEquals(1, v.step(b, 25, false, 50f, false, 3_000).size)   // nach 2,5 s darf "Jetzt" kommen
    }

    @Test fun rerouteIsAnnouncedAtMostEvery45Seconds() {
        val v = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic()
        val a = ins(io.github.th3s1nc.osmandhudbridge.protocol.NavCommand.TURN_LEFT, 5000)
        var n = 0
        for (k in 0 until 6) {   // alle 8 Sekunden eine neue Neuberechnung
            n += v.step(a, 4000, false, 50f, true, k * 8_000L).size
            v.step(a, 4000, false, 50f, false, k * 8_000L + 4_000)
        }
        assertEquals(1, n)
    }

    @Test fun spokenDistances() {
        val f = io.github.th3s1nc.osmandhudbridge.pilot.VoiceLogic.Companion
        assertEquals("300 Metern", f.spokenDist(310))
        assertEquals("einem Kilometer", f.spokenDist(990))
        assertEquals("1,5 Kilometern", f.spokenDist(1480))
        assertEquals("2 Kilometern", f.spokenDist(2010))
    }
}

class PlanMoveTest {
    private fun p(n: String) = io.github.th3s1nc.osmandhudbridge.pilot.PlanPoint(50.0, 8.0, n)
    private fun plan(vararg v: String): io.github.th3s1nc.osmandhudbridge.pilot.Plan {
        val pl = io.github.th3s1nc.osmandhudbridge.pilot.Plan()
        pl.dest = p("Ziel")
        for (x in v) pl.addVia(p(x))
        return pl
    }

    @Test fun lastViaDownBecomesDestination() {
        val pl = plan("A")
        pl.moveViaDown(0)
        assertEquals("A", pl.dest!!.name)
        assertEquals(listOf("Ziel"), pl.vias.map { it.name })
    }

    @Test fun firstViaUpBecomesStartAndCurrentPositionIsDropped() {
        val pl = plan("A", "B")
        pl.moveViaUp(0)
        assertEquals("A", pl.start!!.name)
        assertEquals(listOf("B"), pl.vias.map { it.name })
    }

    @Test fun middleViasSwapNormally() {
        val pl = plan("A", "B", "C")
        pl.moveViaDown(0)
        assertEquals(listOf("B", "A", "C"), pl.vias.map { it.name })
        pl.moveViaUp(2)
        assertEquals(listOf("B", "C", "A"), pl.vias.map { it.name })
    }

    @Test fun destUpAndStartDown() {
        val pl = plan("A", "B")
        pl.destUp()
        assertEquals("B", pl.dest!!.name)
        assertEquals(listOf("A", "Ziel"), pl.vias.map { it.name })
        pl.start = p("S")
        pl.startDown()
        assertEquals("A", pl.start!!.name)
        assertEquals(listOf("S", "Ziel"), pl.vias.map { it.name })
    }

    @Test fun reverseSwapsEndsAndFlipsVias() {
        val pl = plan("A", "B")
        pl.start = p("S")
        assertTrue(pl.reverse(null))
        assertEquals("Ziel", pl.start!!.name)
        assertEquals("S", pl.dest!!.name)
        assertEquals(listOf("B", "A"), pl.vias.map { it.name })
    }

    @Test fun reverseNeedsStart() {
        val pl = plan("A")
        assertFalse(pl.reverse(null))
        assertTrue(pl.reverse(p("jetzt")))
        assertEquals("jetzt", pl.dest!!.name)
    }
}


class RouteSnapTest {
    private fun route() = listOf(
        io.github.th3s1nc.osmandhudbridge.pilot.RoutePoint(50.0, 12.0, 0f),
        io.github.th3s1nc.osmandhudbridge.pilot.RoutePoint(50.0, 12.01, 0f)
    )

    @Test fun nearbyPositionIsPulledOntoRoute() {
        val h = io.github.th3s1nc.osmandhudbridge.pilot.RouteSnap.snap(route(), 50.0001, 12.005)
        assertNotNull(h)
        assertTrue(Math.abs(h!!.lat - 50.0) < 1e-6)
        assertTrue(Math.abs(h.lon - 12.005) < 1e-6)
        assertTrue(h.distM > 10.0 && h.distM < 12.0)
    }

    @Test fun farPositionIsNotSnapped() {
        assertNull(io.github.th3s1nc.osmandhudbridge.pilot.RouteSnap.snap(route(), 50.001, 12.005))
    }

    @Test fun beyondTheEndSnapsToEndPoint() {
        val h = io.github.th3s1nc.osmandhudbridge.pilot.RouteSnap.snap(route(), 50.0, 12.0101)
        assertNotNull(h)
        assertTrue(Math.abs(h!!.lon - 12.01) < 1e-6)
    }

    @Test fun emptyRouteGivesNull() {
        assertNull(io.github.th3s1nc.osmandhudbridge.pilot.RouteSnap.snap(emptyList(), 50.0, 12.0))
    }
}


class PmtilesHeaderTest {
    private fun header(): ByteArray {
        val b = ByteArray(127)
        "PMTiles".toByteArray(Charsets.ISO_8859_1).copyInto(b)
        b[7] = 3
        fun put64(o: Int, v: Long) { for (i in 0 until 8) b[o + i] = ((v shr (8 * i)) and 0xff).toByte() }
        fun put32(o: Int, v: Int) { for (i in 0 until 4) b[o + i] = ((v shr (8 * i)) and 0xff).toByte() }
        put64(8, 127); put64(16, 1000)       // Wurzelverzeichnis
        put64(40, 2000); put64(48, 5000)     // Blatt-Verzeichnisse
        put64(56, 10_000); put64(64, 90_000) // Kacheldaten
        put64(80, 12345)
        b[97] = 2; b[98] = 2; b[99] = 1; b[100] = 0; b[101] = 15
        put32(102, 89_700_000); put32(106, 472_700_000); put32(110, 138_400_000); put32(114, 505_600_000)
        return b
    }

    @Test fun readsFields() {
        val h = io.github.th3s1nc.osmandhudbridge.pilot.PmtilesHeader.parse(header())!!
        assertEquals(3, h.version)
        assertEquals(15, h.maxZoom)
        assertEquals(12345L, h.tileEntries)
        assertEquals(2, h.tileCompression)
        assertEquals(8.97, h.minLon, 0.0001)
        assertEquals(50.56, h.maxLat, 0.0001)
        assertEquals(100_000L, h.expectedMinSize())
    }

    @Test fun rejectsOtherFiles() {
        assertNull(io.github.th3s1nc.osmandhudbridge.pilot.PmtilesHeader.parse(ByteArray(127)))
        assertNull(io.github.th3s1nc.osmandhudbridge.pilot.PmtilesHeader.parse(ByteArray(10)))
    }

    @Test fun reportSaysTruncated() {
        val r = io.github.th3s1nc.osmandhudbridge.pilot.PmtilesHeader.report(header(), 50_000)
        assertTrue(r.contains("ZU KURZ"))
        assertTrue(io.github.th3s1nc.osmandhudbridge.pilot.PmtilesHeader.report(header(), 100_000).contains("vollständig"))
    }
}

class RegionLogicTest {
    private val R = io.github.th3s1nc.osmandhudbridge.pilot.RegionLogic
    private val P = io.github.th3s1nc.osmandhudbridge.pilot.PoiIndex

    @Test fun serverAdresseWirdNormalisiert() {
        assertEquals("http://192.168.1.10", R.normalizeUrl(" 192.168.1.10/ "))
        assertEquals("https://nas.local:8085", R.normalizeUrl("https://nas.local:8085/"))
        assertEquals("", R.normalizeUrl("  "))
    }

    @Test fun adresseUnterwegsUndAuswahl() {
        assertEquals("https://navi.beispiel.duckdns.org", R.normalizeAwayUrl(" navi.beispiel.duckdns.org/ "))
        assertEquals("http://192.168.1.5", R.normalizeAwayUrl("http://192.168.1.5/"))
        assertEquals("", R.normalizeAwayUrl(" "))
        assertEquals("http://h", R.chooseUrl("http://h", "https://a", true))
        assertEquals("https://a", R.chooseUrl("http://h", "https://a", false))
        assertEquals("http://h", R.chooseUrl("http://h", "", false))
        assertEquals("https://a", R.chooseUrl("", "https://a", true))
    }

    @Test fun kachelNameAusPfad() {
        assertEquals("E5_N45", R.tileName("rd5/E5_N45.rd5"))
    }

    @Test fun updateErkennung() {
        assertTrue(R.needsUpdate(0L, 100L))
        assertFalse(R.needsUpdate(100_000L, 100L))
        assertFalse(R.needsUpdate(99_000L, 100L))
        assertTrue(R.needsUpdate(90_000L, 100L))
    }

    @Test fun rangeNurWennSchonDaten() {
        assertNull(R.rangeHeader(0))
        assertEquals("bytes=500-", R.rangeHeader(500))
    }

    @Test fun nachLandGruppiert() {
        val g = R.byLand(listOf("a" to "D", "b" to "AT", "c" to "D")) { it.second }
        assertEquals(listOf("D", "AT"), g.map { it.first })
        assertEquals(2, g[0].second.size)
    }

    @Test fun poiSucheFiltertArtUndAusschnitt() {
        val tsv = "# art\tname\tlat\tlon\tstrasse\tort\n" +
            "fuel\tAral\t48.10000\t11.50000\tMain 1\tMünchen\n" +
            "fuel\tShell\t50.00000\t8.00000\t\t\n" +
            "cafe\tCafé\t48.10000\t11.50000\t\t\n" +
            "fuel\tDefekt\tx\ty\n"
        val r = P.search(java.io.BufferedReader(java.io.StringReader(tsv)), setOf("fuel"), 47.0, 10.0, 49.0, 12.0)
        assertEquals(1, r.size)
        assertEquals("Aral", r[0].name)
        assertEquals("München", r[0].city)
    }

    @Test fun fahrtWirdAufWenigePunkteReduziert() {
        val pts = (0 until 500).map { Pair(48.0 + it * 0.0001, 11.0) }
        val r = R.samplePoints(pts, 27)
        assertTrue(r.size in 20..27)
        assertEquals(pts.first(), r.first())
        assertEquals(pts.last(), r.last())
        assertEquals(3, R.samplePoints(pts.take(3), 27).size)
    }

    @Test fun poiDoppelteEntfallen() {
        val a = io.github.th3s1nc.osmandhudbridge.pilot.PoiIndex.Poi("fuel", "A", 48.1, 11.5, "", "")
        val b = io.github.th3s1nc.osmandhudbridge.pilot.PoiIndex.Poi("fuel", "A", 48.1, 11.5, "x", "")
        assertEquals(1, P.dedupe(listOf(a, b)).size)
    }
}
