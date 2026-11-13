package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.CityCost
import io.github.th3s1nc.osmandhudbridge.pilot.PasswordRules
import io.github.th3s1nc.osmandhudbridge.pilot.PlannerOverride
import io.github.th3s1nc.osmandhudbridge.pilot.TourText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityCostTest {
    private val prof = "assign avoid_unpaved = 0\n\n add switch and istoll avoid_toll 10 0\n\n add switch and avoid_unpaved isunpaved 12 0\n\n switch and highway= not route=ferry  10000\n"

    @Test fun staerkeWirdAufStufenGerundet() {
        assertEquals(1.5, CityCost.snap(1.6), 0.0); assertEquals(4.0, CityCost.snap(9.0), 0.0)
        assertEquals(0.5, CityCost.snap(0.0), 0.0); assertEquals(1.5, CityCost.snap(null), 0.0); assertEquals(1.5, CityCost.snap(Double.NaN), 0.0)
    }
    @Test fun zeileStimmtMitDemServerUeberein() {
        assertEquals(" add switch or isresidentialorliving or maxspeed=50 or maxspeed=30 or maxspeed=20 maxspeed=10 1.5 0", CityCost.line(1.5))
        assertEquals(" add switch or isresidentialorliving or maxspeed=50 or maxspeed=30 or maxspeed=20 maxspeed=10 3 0", CityCost.line(3.0))
    }
    @Test fun codeFuerDenDateinamen() { assertEquals("15", CityCost.code(1.5)); assertEquals("40", CityCost.code(4.0)); assertEquals("5", CityCost.code(0.5)) }
    @Test fun zeileKommtEinmalHinterDieUnbefestigtZeile() {
        val o = CityCost.patch(prof, 2.0)
        assertEquals(1, Regex("isresidentialorliving").findAll(o).count())
        val lines = o.lines(); val i = lines.indexOfFirst { it.contains("avoid_unpaved isunpaved") }
        assertEquals(CityCost.line(2.0), lines[i + 1])
        assertEquals(prof.length + CityCost.line(2.0).length + 1, o.length)
    }
    @Test fun ohneZielzeileBleibtDasProfilGleich() { assertEquals("x\ny\n", CityCost.patch("x\ny\n", 1.0)) }

    @Test fun plannerTraegtStaerkeNurMitSchalter() {
        val v = listOf("a", "b")
        val o = PlannerOverride(); o.begin("a", "b", listOf("city"), v, 3.0); assertEquals(3.0, o.cityw!!, 0.0)
        o.begin("a", "b", listOf("toll"), v, 3.0); assertNull(o.cityw)
        o.begin("a", "b", listOf("city"), v); assertEquals(1.5, o.cityw!!, 0.0)
        o.dropAvoid(); assertNull(o.cityw)
        o.begin("a", "b", listOf("city"), v, 2.0); o.end("b"); assertNull(o.cityw)
    }

    @Test fun passwortRegeln() {
        assertNull(PasswordRules.check("alt", "NeuesPasswort1", "NeuesPasswort1"))
        assertNotNull(PasswordRules.check("", "NeuesPasswort1", "NeuesPasswort1"))
        assertNotNull(PasswordRules.check("alt", "kurz", "kurz"))
        assertNotNull(PasswordRules.check("alt", "x".repeat(65), "x".repeat(65)))
        assertNotNull(PasswordRules.check("alt", "Passwörter123", "Passwörter123"))
        assertNotNull(PasswordRules.check("alt", "Pass|wort123", "Pass|wort123"))
        assertNotNull(PasswordRules.check("alt", "Pass;wort123", "Pass;wort123"))
        assertNotNull(PasswordRules.check("AltesPasswort1", "AltesPasswort1", "AltesPasswort1"))
        assertNotNull(PasswordRules.check("alt", "NeuesPasswort1", "NeuesPasswort2"))
        assertNull(PasswordRules.check("alt", "Mit Leerzeichen 1", "Mit Leerzeichen 1"))
        assertNull(PasswordRules.check("ALTESPASSWORT", "Altespasswort", "Altespasswort"))   // nur die Schreibweise anders: ist ein neues Passwort
    }

    @Test fun tourenzeileKmUndZeit() {
        assertEquals("226 km | 3 h 12 min", TourText.kmMin(226.14, 192))
        assertEquals("48.5 km | 0 h 55 min", TourText.kmMin(48.5, 55))
        assertEquals("100 km | 2 h 00 min", TourText.kmMin(100.0, 120))
        assertNull(TourText.kmMin(null, 10)); assertNull(TourText.kmMin(10.0, null)); assertNull(TourText.kmMin(-1.0, 5))
    }
}
