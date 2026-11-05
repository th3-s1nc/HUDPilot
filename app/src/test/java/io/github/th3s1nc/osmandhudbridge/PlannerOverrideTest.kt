package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.PlannerOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerOverrideTest {
    private val valid = listOf("a", "b", "auto")

    @Test fun plannerProfilGiltEigenesKommtZurueck() {
        val o = PlannerOverride()
        val p = o.begin("a", "b", listOf("motorway"), valid)
        assertEquals("b", p); assertTrue(o.active); assertEquals(setOf("motorway"), o.avoid)
        assertEquals("a", o.end(p)); assertFalse(o.active); assertNull(o.avoid)
    }
    @Test fun zweitePlanerRouteBehaeltUrspruenglichesEigenesProfil() {
        val o = PlannerOverride()
        var cur = o.begin("a", "b", emptyList(), valid)
        cur = o.begin(cur, "auto", emptyList(), valid)
        assertEquals("auto", cur); assertEquals("a", o.end(cur))
    }
    @Test fun unbekanntesProfilBleibtAktuellVermeidenGiltTrotzdem() {
        val o = PlannerOverride()
        assertEquals("a", o.begin("a", "gibtsnicht", listOf("toll"), valid)); assertEquals(setOf("toll"), o.avoid)
    }
    @Test fun ohneVermeidenAngabeBleibenEigeneSchalter() {
        val o = PlannerOverride(); o.begin("a", "b", null, valid); assertNull(o.avoid)
    }
    @Test fun leereListeHeisstNichtsVermeiden() {
        val o = PlannerOverride(); o.begin("a", "b", emptyList(), valid); assertEquals(emptySet<String>(), o.avoid)
    }
    @Test fun dropAvoidLaesstProfilEntferntSchalter() {
        val o = PlannerOverride(); o.begin("a", "b", listOf("ferry"), valid); o.dropAvoid()
        assertNull(o.avoid); assertTrue(o.active)
    }
    @Test fun endOhneBeginAendertNichts() { assertEquals("x", PlannerOverride().end("x")) }
}
