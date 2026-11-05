package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.FriendsLogic
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsLogic.Friend
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsLogic.FriendList
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsLogic.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsLogicTest {
    @Test fun untewegsAlleFuenfSekundenImStandAlleDreissig() {
        assertEquals(5_000L, FriendsLogic.shareGapMs(60f))
        assertEquals(5_000L, FriendsLogic.shareGapMs(3f))
        assertEquals(30_000L, FriendsLogic.shareGapMs(0f))
        assertEquals(30_000L, FriendsLogic.shareGapMs(2.9f))
    }

    @Test fun drosselErsteMeldungSofortDannNachAbstand() {
        val t = FriendsLogic.Throttle()
        assertTrue(t.due(1_000, 80f))
        assertFalse(t.due(3_000, 80f))
        assertTrue(t.due(6_000, 80f))
        assertFalse(t.due(20_000, 0f))     // im Stand erst nach 30 s
        assertTrue(t.due(36_100, 0f))
    }

    @Test fun drosselNachResetSofortWieder() {
        val t = FriendsLogic.Throttle()
        assertTrue(t.due(0, 80f)); t.reset()
        assertTrue(t.due(1, 80f))
    }

    @Test fun schildMitNameTempoUndAlter() {
        val p = Pos("Anna", 49.0, 12.0, 87.4, 90.0, 8)
        assertEquals("Anna · 87 km/h · vor 8 s", FriendsLogic.label(p, false))
        assertEquals("Anna · 87 km/h · 8 s ago", FriendsLogic.label(p, true))
    }

    @Test fun alterText() {
        assertEquals("jetzt", FriendsLogic.ageText(2, false))
        assertEquals("vor 45 s", FriendsLogic.ageText(45, false))
        assertEquals("vor 2 Min", FriendsLogic.ageText(130, false))
        assertEquals("2 min ago", FriendsLogic.ageText(130, true))
    }

    @Test fun anfangsbuchstabe() {
        assertEquals("A", FriendsLogic.initial("anna"))
        assertEquals("Ä", FriendsLogic.initial(" ärger"))
        assertEquals("?", FriendsLogic.initial("  "))
    }

    @Test fun statusTexte() {
        assertEquals("möchte dein Freund sein", FriendsLogic.statusText(Friend("Mike", "incoming"), false))
        assertEquals("Anfrage gesendet", FriendsLogic.statusText(Friend("Mike", "outgoing"), false))
        assertEquals("● teilt gerade · 87 km/h", FriendsLogic.statusText(Friend("Anna", "friend", sharing = true, spdKmh = 87.2), false))
        assertEquals("offline", FriendsLogic.statusText(Friend("Tom", "friend"), false))
    }

    @Test fun sichtbarHinweis() {
        assertEquals("Standort teilen aus", FriendsLogic.visibleText(3, false, false))
        assertEquals("Sichtbar für 1 Freund", FriendsLogic.visibleText(1, true, false))
        assertEquals("Sichtbar für 2 Freunde", FriendsLogic.visibleText(2, true, false))
        assertEquals("Teilen an, aber noch darf dich niemand sehen", FriendsLogic.visibleText(0, true, false))
    }

    @Test fun neueAnfragenWerdenNurEinmalGemeldet() {
        val cur = listOf(Friend("Mike", "incoming"), Friend("Tom", "friend"), Friend("Lisa", "incoming"), Friend("Zoe", "outgoing"))
        assertEquals(listOf("Mike", "Lisa"), FriendsLogic.newIncoming(emptySet(), cur))
        assertEquals(listOf("Lisa"), FriendsLogic.newIncoming(setOf("Mike"), cur))
        assertEquals(emptyList<String>(), FriendsLogic.newIncoming(setOf("Mike", "Lisa"), cur))
        assertEquals(setOf("Mike", "Lisa"), FriendsLogic.incomingNames(cur))
    }

    @Test fun listeWeissOberFreundeDaSindUndWerTeilt() {
        assertFalse(FriendList(listOf(Friend("Mike", "incoming")), 1, 0).hasFriends)
        val l = FriendList(listOf(Friend("Tom", "friend"), Friend("Anna", "friend", sharing = true)), 0, 1)
        assertTrue(l.hasFriends); assertTrue(l.anySharing)
        assertFalse(FriendList(listOf(Friend("Tom", "friend")), 0, 0).anySharing)
    }

    @Test fun profilnameFuerAnfrage() {
        assertEquals("Anna K", FriendsLogic.cleanName("  Anna K "))
        assertEquals("Jörg_1", FriendsLogic.cleanName("Jörg_1"))
        assertNull(FriendsLogic.cleanName("A"))
        assertNull(FriendsLogic.cleanName("x".repeat(25)))
        assertNull(FriendsLogic.cleanName("a/b"))
    }
}
