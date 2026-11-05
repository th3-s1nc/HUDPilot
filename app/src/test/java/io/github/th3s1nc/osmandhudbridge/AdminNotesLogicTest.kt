package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.AdminNotesLogic
import io.github.th3s1nc.osmandhudbridge.pilot.AdminNotesLogic.Ev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminNotesLogicTest {
    private fun ev(id: Int, text: String = "t$id") = Ev(id, 1000L + id, "x", text)

    @Test fun mergeIgnoriertBekannteUndSortiert() {
        val m = AdminNotesLogic.merge(listOf(ev(1), ev(2)), listOf(ev(2), ev(4), ev(3)))
        assertEquals(listOf(1, 2, 3, 4), m.map { it.id })
    }

    @Test fun mergeBehaeltHoechstensFuenfzig() {
        val m = AdminNotesLogic.merge((1..40).map { ev(it) }, (41..80).map { ev(it) })
        assertEquals(50, m.size); assertEquals(31, m.first().id); assertEquals(80, m.last().id)
    }

    @Test fun ungelesenUndLetzteNummer() {
        val l = listOf(ev(3), ev(5), ev(9))
        assertEquals(2, AdminNotesLogic.unread(l, 3)); assertEquals(0, AdminNotesLogic.unread(l, 9)); assertEquals(3, AdminNotesLogic.unread(l, 0))
        assertEquals(9, AdminNotesLogic.lastId(l)); assertEquals(0, AdminNotesLogic.lastId(emptyList()))
    }

    @Test fun knopfText() {
        assertEquals("Admin-Meldungen", AdminNotesLogic.buttonText(0, false))
        assertEquals("Admin-Meldungen (3)", AdminNotesLogic.buttonText(3, false))
        assertEquals("Admin messages (1)", AdminNotesLogic.buttonText(1, true))
    }

    @Test fun benachrichtigungstext() {
        assertEquals("", AdminNotesLogic.notifyText(emptyList(), false))
        assertEquals("Update fertig", AdminNotesLogic.notifyText(listOf(ev(1, "Update fertig")), false))
        assertTrue(AdminNotesLogic.notifyText(listOf(ev(1, "a"), ev(2, "b")), false).startsWith("2 neue Admin-Meldungen"))
        assertTrue(AdminNotesLogic.notifyText(listOf(ev(1, "a"), ev(2, "b")), false).endsWith("b"))
    }

    @Test fun neuAnzukuendigen() {
        assertEquals(listOf(6, 7), AdminNotesLogic.toAnnounce(listOf(ev(7), ev(5), ev(6)), 5).map { it.id })
    }
}

class AdminNotesLogicViewTest {
    private val utc = java.util.TimeZone.getTimeZone("UTC")
    // 2026-11-05 12:00:00 UTC
    private val now = 1793880000L

    @Test fun zeitText() {
        assertEquals("gerade eben", AdminNotesLogic.ageText(now - 30, now, utc, false))
        assertEquals("vor 12 Min.", AdminNotesLogic.ageText(now - 12 * 60, now, utc, false))
        assertEquals("heute 07:00", AdminNotesLogic.ageText(now - 5 * 3600, now, utc, false))
        assertEquals("gestern 01:03", AdminNotesLogic.ageText(now - 34 * 3600 - 57 * 60, now, utc, false))
        assertEquals("03.11. 19:20", AdminNotesLogic.ageText(now - (40 * 3600 + 40 * 60), now, utc, false))
        assertEquals("yesterday 01:03", AdminNotesLogic.ageText(now - 34 * 3600 - 57 * 60, now, utc, true))
        assertEquals("today 07:00", AdminNotesLogic.ageText(now - 5 * 3600, now, utc, true))
    }

    @Test fun titelSymbolUndStufe() {
        assertEquals("Update fertig", AdminNotesLogic.headline("lauf_ok", false))
        assertEquals("Update finished", AdminNotesLogic.headline("lauf_ok", true))
        assertEquals("Meldung", AdminNotesLogic.headline("irgendwas", false))
        assertEquals("✓", AdminNotesLogic.icon("lauf_ok")); assertEquals("ok", AdminNotesLogic.level("lauf_ok"))
        assertEquals("bad", AdminNotesLogic.level("baustellen")); assertEquals("warn", AdminNotesLogic.level("scdb"))
        assertEquals("info", AdminNotesLogic.level("start")); assertEquals("🔔", AdminNotesLogic.icon("unbekannt"))
    }
}
