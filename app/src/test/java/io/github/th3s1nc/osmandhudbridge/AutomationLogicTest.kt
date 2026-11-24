package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.AutomationLogic as L
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class AutomationLogicTest {
    private fun cfg(moket: Boolean = true, nav: Boolean = true, rec: Boolean = true, sp: Boolean = false, play: Boolean = false, delay: Int = 5, ret: Boolean = false, retS: Int = 5, start: Int = 0) =
        L.Cfg(moket, nav, rec, sp, play, delay, start, ret, retS)

    @Test fun grenzenWerdenBeschnitten() {
        assertEquals(0, L.clampPlayDelay(-3)); assertEquals(30, L.clampPlayDelay(99)); assertEquals(7, L.clampPlayDelay(7))
        assertEquals(1, L.clampReturn(0)); assertEquals(15, L.clampReturn(40)); assertEquals(5, L.clampReturn(5))
    }

    @Test fun abkuehlzeitVerhindertSchleife() {
        assertTrue(L.mayRun(1000L, 0L))
        assertFalse(L.mayRun(60_000L, 1_000L))
        assertTrue(L.mayRun(121_001L, 1_000L))
        assertTrue(L.mayRun(500L, 1_000L))   // Uhr zurückgestellt
    }

    @Test fun moketStartetBeiNavigationUndNurEinmal() {
        assertEquals("start", L.moketAction(true, true, cfg(), navActive = true, recActive = false, moketOn = false))
        assertNull(L.moketAction(true, false, cfg(), navActive = true, recActive = true, moketOn = true))   // läuft schon
    }

    @Test fun moketStoppErstWennKeineQuelleMehrLaeuft() {
        val c = cfg()
        assertNull(L.moketAction(false, true, c, navActive = false, recActive = true, moketOn = true))     // Aufnahme läuft noch
        assertEquals("stop", L.moketAction(false, false, c, navActive = false, recActive = false, moketOn = true))
        assertNull(L.moketAction(false, true, c, navActive = false, recActive = false, moketOn = false))   // nie gestartet
    }

    @Test fun moketRespektiertQuellenWahlUndHauptschalter() {
        assertNull(L.moketAction(true, true, cfg(nav = false), navActive = true, recActive = false, moketOn = false))
        assertEquals("start", L.moketAction(true, false, cfg(nav = false), navActive = false, recActive = true, moketOn = false))
        assertNull(L.moketAction(true, true, cfg(moket = false), navActive = true, recActive = false, moketOn = false))
        // laufende Aufnahme zählt nicht, wenn nur Navigation gewählt ist
        assertEquals("stop", L.moketAction(false, true, cfg(rec = false), navActive = false, recActive = true, moketOn = true))
    }

    @Test fun planOeffnenMitSpotifyPlayUndReturn() {
        val p = L.planOpen(cfg(moket = false, sp = true, play = true, delay = 8, ret = true, retS = 4), moketLaunched = false)
        assertEquals(listOf(L.Kind.SPOTIFY_OPEN, L.Kind.SPOTIFY_PLAY, L.Kind.RETURN), p.map { it.kind })
        assertEquals(listOf(0L, 8000L, 4000L), p.map { it.atMs })
    }

    @Test fun planOhnePlayOhneReturn() {
        val p = L.planOpen(cfg(sp = true), moketLaunched = false)
        assertEquals(listOf(L.Kind.SPOTIFY_OPEN), p.map { it.kind })
        assertTrue(L.planOpen(cfg(), moketLaunched = false).isEmpty())
    }

    @Test fun returnAuchNurMitMoketOhneSpotify() {
        val p = L.planOpen(cfg(ret = true, retS = 3), moketLaunched = true)
        assertEquals(listOf(L.Kind.RETURN), p.map { it.kind }); assertEquals(3000L, p[0].atMs)
        assertTrue(L.planOpen(cfg(ret = true), moketLaunched = false).isEmpty())   // nichts geöffnet, nichts zurückzuholen
        assertEquals(1, L.planReturn(cfg(ret = true)).size); assertTrue(L.planReturn(cfg()).isEmpty())
    }

    @Test fun startVerzoegerungSchiebtAlleSpotifySchritteNach() {
        val p = L.planOpen(cfg(moket = false, sp = true, play = true, delay = 8, ret = true, retS = 4, start = 10), moketLaunched = false)
        assertEquals(listOf(L.Kind.SPOTIFY_OPEN, L.Kind.SPOTIFY_PLAY, L.Kind.RETURN), p.map { it.kind })
        assertEquals(listOf(10_000L, 18_000L, 14_000L), p.map { it.atMs })
    }

    @Test fun startVerzoegerungGrenzenUndOhneSpotifyKeineWirkung() {
        assertEquals(0, L.clampStartDelay(-1)); assertEquals(30, L.clampStartDelay(500)); assertEquals(12, L.clampStartDelay(12))
        val p = L.planOpen(cfg(sp = false, ret = true, retS = 3, start = 20), moketLaunched = true)
        assertEquals(3000L, p.single().atMs)   // Moket-Rückkehr ohne Spotify wartet nicht auf die Startverzögerung
        assertEquals(30_000L, L.planOpen(cfg(sp = true, start = 99), moketLaunched = false).first().atMs)
    }

    @Test fun texte() {
        assertEquals("Startverzögerung: 7 s", L.startDelayText(7, false)); assertEquals("Start delay: 7 s", L.startDelayText(7, true))
        assertEquals("Verzögerung: 5 s", L.delayText(5, false)); assertEquals("Back after: 3 s", L.returnText(3, true))
    }
}
