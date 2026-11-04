package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.RegionLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionListTest {
    private class R(val name: String, val land: String, val landName: String)

    private val all = listOf(
        R("Bayern", "DE", "Deutschland"), R("Berlin", "DE", "Deutschland"),
        R("Wien", "AT", "Österreich"), R("Tirol", "AT", "Österreich"),
        R("Bretagne", "FR", "Frankreich"), R("Alsace", "FR", "Frankreich"),
        R("Zürich", "CH", "Schweiz"), R("Dänemark", "DK", "Dänemark")
    )

    @Test fun landTitle() {
        assertEquals("Frankreich", RegionLogic.landTitle("FR", "Frankreich"))
        assertEquals("FR", RegionLogic.landTitle("FR", ""))
        assertEquals("?", RegionLogic.landTitle("", ""))
    }

    @Test fun deutschlandZuerstDannAlphabetischMitUmlauten() {
        val g = RegionLogic.groupLands(all, { it.land }, { RegionLogic.landTitle(it.land, it.landName) }, { it.name })
        assertEquals(listOf("Deutschland", "Dänemark", "Frankreich", "Österreich", "Schweiz"), g.map { it.title })
    }

    @Test fun regionenEinesLandesAlphabetisch() {
        val g = RegionLogic.groupLands(all, { it.land }, { it.landName }, { it.name })
        assertEquals(listOf("Alsace", "Bretagne"), g.first { it.code == "FR" }.items.map { it.name })
        assertEquals(listOf("Tirol", "Wien"), g.first { it.code == "AT" }.items.map { it.name })
    }

    @Test fun sucheEgalObGrossKleinOderAkzent() {
        assertTrue(RegionLogic.matches("", "Bayern", "Deutschland"))
        assertTrue(RegionLogic.matches("bay", "Bayern", "Deutschland"))
        assertTrue(RegionLogic.matches("OESTERREICH".lowercase().replace("oe", "ö"), "Wien", "Österreich"))
        assertTrue(RegionLogic.matches("zurich", "Zürich", "Schweiz"))
        assertTrue(RegionLogic.matches("frank bret", "Bretagne", "Frankreich"))
        assertFalse(RegionLogic.matches("frank bayern", "Bretagne", "Frankreich"))
        assertFalse(RegionLogic.matches("xyz", "Bayern", "Deutschland"))
    }

    @Test fun sucheFindetLandUndRegionen() {
        val hit = all.filter { RegionLogic.matches("ital", it.name, it.landName) }
        assertTrue(hit.isEmpty())
        val fr = all.filter { RegionLogic.matches("frankreich", it.name, it.landName) }
        assertEquals(2, fr.size)
    }
}
