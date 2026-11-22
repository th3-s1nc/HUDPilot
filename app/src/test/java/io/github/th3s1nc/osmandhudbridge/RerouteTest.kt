package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.Geo
import io.github.th3s1nc.osmandhudbridge.pilot.PilotRoute
import io.github.th3s1nc.osmandhudbridge.pilot.RerouteLogic
import io.github.th3s1nc.osmandhudbridge.pilot.RouteDirection
import io.github.th3s1nc.osmandhudbridge.pilot.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Route aus Breitengraden (alle auf Längengrad [lon]); [wps] sind Punktindizes der Wegpunkte, [sec] die Fahrzeit. */
private fun road(lats: DoubleArray, wps: IntArray, sec: Int = 1000, lon: Double = 12.0): PilotRoute =
    PilotRoute.build(lats, DoubleArray(lats.size) { lon }, FloatArray(lats.size), sec, 0, IntArray(0), IntArray(0), IntArray(0), wps, BooleanArray(wps.size))

/** Gerade nach Norden von [from] bis [to] in 50-m-Schritten. */
private fun north(from: Double, to: Double): DoubleArray {
    val n = ((to - from) / 0.00045).toInt().coerceAtLeast(1)
    return DoubleArray(n + 1) { from + (to - from) * it / n }
}

/** Erst 450 m nach Süden (Umdrehen), dann nach Norden bis [to]. */
private fun uTurn(from: Double, to: Double): DoubleArray {
    val down = DoubleArray(10) { from - it * 0.00045 }
    val up = north(from - 0.00405, to)
    return down + up
}

private const val LAT0 = 48.0

class RouteDirectionTest {
    @Test fun unterschiedZweierRichtungen() {
        assertEquals(10.0, RouteDirection.diff(355.0, 5.0), 1e-9)
        assertEquals(180.0, RouteDirection.diff(0.0, 180.0), 1e-9)
        assertEquals(90.0, RouteDirection.diff(0.0, 90.0), 1e-9)
    }
    @Test fun nachNordenBeiFahrtNachNorden() {
        val r = road(north(LAT0, LAT0 + 0.05), intArrayOf(0, 111))
        assertFalse(RouteDirection.startsAgainst(r, 0))
        assertFalse(RouteDirection.startsAgainst(r, 60))      // 60 Grad Unterschied: noch vorwärts
        assertTrue(RouteDirection.startsAgainst(r, 180))      // gegen die Fahrtrichtung
        assertTrue(RouteDirection.startsAgainst(r, 100))      // mehr als 90 Grad
    }
    @Test fun umdrehenAmStartWirdErkannt() {
        val r = road(uTurn(LAT0, LAT0 + 0.05), intArrayOf(0, 100))
        assertTrue(RouteDirection.startsAgainst(r, 0))
        assertFalse(RouteDirection.startsAgainst(r, 180))     // wer nach Süden fährt, für den ist das vorwärts
    }
    @Test fun routeDieNachEinerKurzenStreckeWiederZurueckLaeuftIstEinWenden() {
        // 75 m nach Norden bis zu einer Kreuzung, dann in 25-m-Schritten zurück am Start vorbei nach Süden
        val m = 1.0 / 111_320.0
        val lats = doubleArrayOf(0.0, 25.0, 50.0, 75.0, 50.0, 25.0, 0.0, -25.0, -50.0).map { LAT0 + it * m }.toDoubleArray()
        val r = road(lats, intArrayOf(0, lats.size - 1))
        assertTrue(RouteDirection.startsAgainst(r, 0))
    }
    @Test fun ohneRichtungOderZuKurzKeinUrteil() {
        val short = road(north(LAT0, LAT0 + 0.0001), intArrayOf(0, 1))
        assertFalse(RouteDirection.startsAgainst(short, 180))
    }
}

class RerouteTest {
    private val heading = 0   // wir fahren nach Norden

    /** Alte Route: 44 km nach Norden. Wir sind bei ca. 1 km (Punkt 9) und fahren daneben (Lage 60 m östlich) weiter. */
    private val old = road(north(LAT0, LAT0 + 0.4), intArrayOf(0, 888), 3000)
    private val pos = Waypoint(LAT0 + 0.009, 12.0008, false, "Position")
    private val ziel = Waypoint(LAT0 + 0.4, 12.0, false, "Ziel")

    private fun plan(
        wps: List<Waypoint>, baseS: List<Double?>, head: Int? = heading, oldRoute: PilotRoute = old,
        compute: (List<Waypoint>) -> PilotRoute?
    ) = RerouteLogic.plan(oldRoute, 1000, pos.lat, pos.lon, head, wps, baseS, compute) { _, _ -> }

    @Test fun kurzesStueckInFahrtrichtungWirdGenommen() {
        val r = plan(listOf(pos, ziel), listOf(null, null)) { w ->
            if (w.size == 2 && w[1].name == "Wiedereinstieg") road(north(w[0].lat, w[1].lat), intArrayOf(0, 100), 100) else null
        }
        assertNotNull(r)
        assertTrue(r!!.how, r.how.startsWith("Wiedereinstieg 4 km"))
        assertEquals(2, r.route.waypointIndex.size)
    }

    @Test fun umdrehenVerwirftDieStufenUndDieVolleRouteFuehrtVorwaerts() {
        var calls = 0
        val r = plan(listOf(pos, ziel), listOf(null, null)) { w ->
            calls++
            if (w.size == 2 && w[1].name == "Wiedereinstieg") road(uTurn(w[0].lat, w[1].lat), intArrayOf(0, 100), 100)   // alle kurzen Stücke wenden
            else road(north(w[0].lat, w[1].lat), intArrayOf(0, 100), 100)                                                 // volle Route geht nach vorn
        }
        assertNotNull(r)
        assertEquals("volle Route", r!!.how)
        assertFalse(RouteDirection.startsAgainst(r.route, heading))
        assertTrue("alle drei Wiedereinstiege wurden versucht: $calls", calls >= 4)
    }

    @Test fun spaeterEinstiegWirdGenommenWennDerNaechsteWendet() {
        var n = 0
        val r = plan(listOf(pos, ziel), listOf(null, null)) { w ->
            if (w.size == 2 && w[1].name == "Wiedereinstieg") { n++; if (n < 3) road(uTurn(w[0].lat, w[1].lat), intArrayOf(0, 100), 100) else road(north(w[0].lat, w[1].lat), intArrayOf(0, 100), 100) }
            else null
        }
        assertNotNull(r)
        assertTrue(r!!.how, r.how.startsWith("Wiedereinstieg 25 km"))
    }

    @Test fun gibtEsNurEinUmdrehenKommtKeineRouteUndWirdWeitergefahren() {
        val r = plan(listOf(pos, ziel), listOf(null, null)) { w -> road(uTurn(w[0].lat, w[1].lat), intArrayOf(0, 100), 100) }
        assertNull(r)   // die App bleibt dann bei "Weiterfahren" und versucht es bald wieder
    }

    @Test fun ohneBekannteFahrtrichtungKeinRichtungstest() {
        val r = plan(listOf(pos, ziel), listOf(null, null), head = null) { w -> road(uTurn(w[0].lat, w[1].lat), intArrayOf(0, 100), 100) }
        assertNotNull(r)
    }

    private val via = Waypoint(LAT0 + 0.3, 12.0, true, "Pass")
    private val shortOld = road(north(LAT0, LAT0 + 0.05), intArrayOf(0, 111), 600)   // zu kurz für einen Wiedereinstieg: nur volle Routen

    @Test fun zwischenzielBleibtBeiKleinemUmweg() {
        // Weg bis zum Zwischenziel: Punkt 400 von 600 der Route, 3600 s gesamt: 2400 s; geplant waren 1500 s: +15 min
        val r = plan(listOf(pos, via, ziel), listOf(null, 1500.0, null), oldRoute = shortOld) { w ->
            road(north(w[0].lat, w[w.size - 1].lat), intArrayOf(0, 400, 599), 3600)
        }
        assertNotNull(r)
        assertTrue(r!!.skipped.isEmpty())
        assertEquals(3, r.wps.size)
    }

    @Test fun zwischenzielWirdUebersprungenWennDerWegZuLangWird() {
        // geplant waren 600 s, jetzt 2700 s: 35 min länger, mehr als die 20 Minuten
        var call = 0
        val r = plan(listOf(pos, via, ziel), listOf(null, 600.0, null), oldRoute = shortOld) { w ->
            call++
            if (w.size == 3) road(north(w[0].lat, w[2].lat), intArrayOf(0, 450, 599), 3600)      // Zwischenziel bei 75 % = 2700 s
            else road(north(w[0].lat, w[1].lat), intArrayOf(0, 100), 1000)                      // direkt zum Ziel
        }
        assertNotNull(r)
        assertEquals(listOf(via), r!!.skipped)
        assertEquals(2, r.wps.size)
        assertEquals(ziel, r.wps.last())
        assertEquals(2, call)
    }

    @Test fun dasEndzielWirdNieUebersprungen() {
        // nur Position und Ziel: egal wie lang der Weg ist
        val r = plan(listOf(pos, ziel), listOf(null, 100.0), oldRoute = shortOld) { w -> road(north(w[0].lat, w[1].lat), intArrayOf(0, 100), 99999) }
        assertNotNull(r)
        assertTrue(r!!.skipped.isEmpty())
    }

    @Test fun ohneGeplanteZeitWirdNichtUebersprungen() {
        val r = plan(listOf(pos, via, ziel), listOf(null, null, null), oldRoute = shortOld) { w -> road(north(w[0].lat, w[w.size - 1].lat), intArrayOf(0, 450, 599), 99999) }
        assertNotNull(r)
        assertTrue(r!!.skipped.isEmpty())
    }

    @Test fun uebersprungenUndTrotzdemNurUmdrehenGibtKeineRoute() {
        val r = plan(listOf(pos, via, ziel), listOf(null, 600.0, null), oldRoute = shortOld) { w ->
            if (w.size == 3) road(north(w[0].lat, w[2].lat), intArrayOf(0, 450, 599), 3600) else road(uTurn(w[0].lat, w[1].lat), intArrayOf(0, 100), 100)
        }
        assertNull(r)
    }

    @Test fun mehrereZwischenzieleNacheinanderUebersprungen() {
        val via2 = Waypoint(LAT0 + 0.35, 12.0, true, "Pass 2")
        val r = plan(listOf(pos, via, via2, ziel), listOf(null, 600.0, 700.0, null), oldRoute = shortOld) { w ->
            when (w.size) {
                4 -> road(north(w[0].lat, w[3].lat), intArrayOf(0, 450, 700, 860), 3600)   // erstes Zwischenziel bei 52 %: 1866 s, geplant 600 s: +21 min
                3 -> road(north(w[0].lat, w[2].lat), intArrayOf(0, 700, 860), 4000)        // zweites bei 81 %: 3230 s, geplant 700 s: ebenfalls zu weit
                else -> road(north(w[0].lat, w[1].lat), intArrayOf(0, 100), 500)
            }
        }
        assertNotNull(r)
        assertEquals(listOf(via, via2), r!!.skipped)
        assertEquals(listOf(pos, ziel), r.wps)
    }
}
