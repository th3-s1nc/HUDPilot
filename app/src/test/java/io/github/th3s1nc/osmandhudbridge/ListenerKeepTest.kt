package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.nav.ListenerKeepLogic as L
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerKeepTest {
    @Test fun nurEingreifenWennErlaubtAberGetrennt() {
        assertTrue(L.needsHeal(granted = true, connected = false))
        assertFalse(L.needsHeal(granted = true, connected = true))
        assertFalse(L.needsHeal(granted = false, connected = false))
        assertFalse(L.needsHeal(granted = false, connected = true))
    }

    @Test fun ausEinschaltenErstesMalImmerErlaubt() {
        assertTrue(L.mayToggle(1_000_000L, 0L, L.TOGGLE_GAP_BG_MS))
    }

    @Test fun ausEinschaltenNichtOefterAlsDerAbstand() {
        val t0 = 10_000_000_000L
        assertFalse(L.mayToggle(t0 + 60_000L, t0, L.TOGGLE_GAP_BG_MS))               // nach 1 Minute nicht
        assertFalse(L.mayToggle(t0 + 5 * 3_600_000L, t0, L.TOGGLE_GAP_BG_MS))        // nach 5 Stunden nicht
        assertTrue(L.mayToggle(t0 + 6 * 3_600_000L, t0, L.TOGGLE_GAP_BG_MS))         // nach 6 Stunden ja
        assertFalse(L.mayToggle(t0 + 10 * 60_000L, t0, L.TOGGLE_GAP_OPEN_MS))        // App geöffnet: 10 Minuten nicht
        assertTrue(L.mayToggle(t0 + 30 * 60_000L, t0, L.TOGGLE_GAP_OPEN_MS))         // 30 Minuten ja
    }

    @Test fun uhrSprungRueckwaertsBlockiertNichtFuerImmer() {
        assertTrue(L.mayToggle(1_000L, 5_000_000L, L.TOGGLE_GAP_BG_MS))
    }

    @Test fun wartezeitenPassenInDieZehnSekundenDesAlarms() {
        val total = L.WAIT_BEFORE_TOGGLE_MS + L.AFTER_TOGGLE_MS + L.FINAL_WAIT_MS
        assertTrue("Summe $total ms", total < 10_000L)
        assertEquals(2_500L, L.REBIND_DELAY_MS)
    }
}
