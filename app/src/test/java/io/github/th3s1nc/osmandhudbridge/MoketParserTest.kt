package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.nav.MoketParser
import io.github.th3s1nc.osmandhudbridge.nav.MoketParser.Kind
import io.github.th3s1nc.osmandhudbridge.nav.NoticeThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoketParserTest {
    private fun p(title: String, text: String, kind: String? = null, channel: String = "social", tag: String = "x"): MoketParser.Result? =
        MoketParser.parse(title, text, channel, tag, kind?.let { """{"id":"1","kind":"$it","screen":"X"}""" })

    @Test
    fun hilfeAnfrage() {
        val r = p("Ein Biker braucht Hilfe", "Panne etwa 5 km von dir. th3_s1nc, Kawasaki Z750 SF.")!!
        assertEquals(Kind.HELP, r.kind); assertEquals("HILFE Panne 5km", r.hud)
        assertEquals("HILFE Batterie 5km", p("Ein Biker braucht Hilfe", "Batterie leer etwa 5 km von dir. a, b.")!!.hud)
        assertEquals("HILFE Unfall 3km", p("Ein Biker braucht Hilfe", "Unfall etwa 3 km von dir. a, b.")!!.hud)
        assertEquals("HILFE Reifen 12km", p("Ein Biker braucht Hilfe", "Reifenschaden etwa 12 km von dir. a, b.")!!.hud)
        assertEquals("HILFE Motor 7km", p("Ein Biker braucht Hilfe", "Motorschaden etwa 7 km von dir. a, b.")!!.hud)
        val lang = p("Ein Biker braucht Hilfe", "Sprachsteuerung etwa 12 km von dir. a, b.")!!.hud
        assertEquals("HILFE Sprachst 12km", lang); assertEquals(19, lang.length)
        assertEquals("HILFE in der Naehe", p("Ein Biker braucht Hilfe", "irgendwas")!!.hud)
    }

    @Test
    fun hilfeAntwortUndNachricht() {
        assertEquals("th3_s1nc hilft", p("th3_s1nc kann dir helfen", "Kawasaki Z750 SF, etwa 5 km entfernt.", "help_response")!!.hud)
        val m = p("th3_s1nc", "Neue Hilfe-Nachricht", "help_message")!!
        assertEquals(Kind.HELP_MSG, m.kind); assertEquals("Hilfe: th3_s1nc", m.hud)
        assertNull(p("th3_s1nc hat dir den Standort geschickt", "Nur du siehst ihn", "help_share"))
        assertNull(p("Hilfeanfrage beendet", "Wie lief es?", "help_closed"))
    }

    @Test
    fun begegnung() {
        val r = p("Du hast einen Biker gekreuzt!", "Wer es war, siehst du, sobald ihr beide die Fahrt beendet habt.")!!
        assertEquals(Kind.ENCOUNTER, r.kind); assertEquals("Biker gekreuzt", r.hud)
        assertNull(p("Neue Begegnungen", "Du hast auf deiner letzten Fahrt 1 Biker gekreuzt."))
    }

    @Test
    fun gruppenchatOhneEmojiUndKlammer() {
        val r = p("Christkindljagt 🎄 (Nur n Test 😉)", "Neue Nachricht von th3_s1nc", "event_message")!!
        assertEquals(Kind.CHAT_GROUP, r.kind); assertEquals("Christkindljagt", r.hud)
        assertEquals("Tour Sued", p("Tour Süd", "Neue Nachricht von Tom")!!.hud) // ohne Zusatzdaten über den Text
    }

    @Test
    fun privatChat() {
        val r = p("th3_s1nc", "Neue Nachricht")!!
        assertEquals(Kind.CHAT_PRIVATE, r.kind); assertEquals("th3_s1nc", r.hud)
    }

    @Test
    fun ignoriert() {
        assertNull(p("Neue Ausfahrt in deiner Nähe", "Test · 28.10. 13:00 · ca. 5 km entfernt"))
        assertNull(p("Anfrage angenommen", "x", "contact_accepted"))
        assertNull(p("Fahrt läuft", "7,0 km · Ø 49 km/h", channel = "moket-ride"))
        assertNull(MoketParser.parse(null, null, "social", "0|app.moket|g:Aggregate_AlertingSection", null))
    }

    @Test
    fun zusammenfassenJeChat() {
        val t = NoticeThrottle(30_000L)
        assertEquals("Christkindljagt", t.next("c", "Christkindljagt", 0L))
        assertNull(t.next("c", "Christkindljagt", 5_000L))
        assertNull(t.next("c", "Christkindljagt", 10_000L))
        assertEquals("Christkindljagt (3)", t.next("c", "Christkindljagt", 31_000L))
        assertEquals("Anna", t.next("a", "Anna", 31_500L)) // anderer Chat unabhängig
        assertNotNull(t.next("c", "Christkindljagt", 70_000L))
    }
}
