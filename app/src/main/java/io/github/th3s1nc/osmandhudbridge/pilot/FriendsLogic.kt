package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Freunde und Live-Standort (ab App 0.33.0): reine Logik ohne Android, damit sie getestet werden kann.
 * Die Verbindung zum Server steht in [FriendsApi], das Senden der eigenen Position in [LiveShare].
 */
object FriendsLogic {
    /** status: "friend", "incoming" (Anfrage an dich) oder "outgoing" (deine Anfrage). */
    class Friend(
        val name: String, val status: String, val iAllow: Boolean = false, val theyAllow: Boolean = false,
        val sharing: Boolean = false, val spdKmh: Double = 0.0, val ageSec: Int = 0
    )

    class Pos(val name: String, val lat: Double, val lon: Double, val spdKmh: Double, val hdg: Double, val ageSec: Int)

    class FriendList(val friends: List<Friend>, val pendingIn: Int, val viewers: Int) {
        /** Gibt es schon bestätigte Freunde? Dann zeigt die Karte den Knopf "Standort teilen". */
        val hasFriends: Boolean get() = friends.any { it.status == "friend" }
        val anySharing: Boolean get() = friends.any { it.status == "friend" && it.sharing }
    }

    /** Abstand zwischen zwei Meldungen: unterwegs alle 5 Sekunden, im Stand alle 30. */
    fun shareGapMs(kmh: Float): Long = if (kmh >= 3f) 5_000L else 30_000L

    /** Entscheidet, wann die nächste Position gesendet wird (von mehreren Stellen aufrufbar). */
    class Throttle {
        private var last = -1L
        @Synchronized fun due(nowMs: Long, kmh: Float): Boolean {
            if (last >= 0 && nowMs - last < shareGapMs(kmh)) return false
            last = nowMs; return true
        }
        @Synchronized fun reset() { last = -1L }
    }

    fun ageText(sec: Int, en: Boolean): String = when {
        sec < 5 -> if (en) "now" else "jetzt"
        sec < 60 -> if (en) "$sec s ago" else "vor $sec s"
        else -> if (en) "${sec / 60} min ago" else "vor ${sec / 60} Min"
    }

    /** Schild am Marker: "Anna · 87 km/h · vor 3 s". */
    fun label(p: Pos, en: Boolean): String = "${p.name} · ${Math.round(p.spdKmh)} km/h · ${ageText(p.ageSec, en)}"

    fun initial(name: String): String = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    fun statusText(f: Friend, en: Boolean): String = when (f.status) {
        "incoming" -> if (en) "wants to be your friend" else "möchte dein Freund sein"
        "outgoing" -> if (en) "request sent" else "Anfrage gesendet"
        else -> if (f.sharing) (if (en) "● sharing · ${Math.round(f.spdKmh)} km/h" else "● teilt gerade · ${Math.round(f.spdKmh)} km/h")
        else (if (en) "offline" else "offline")
    }

    /** Hinweis nach dem Antippen des Knopfes: "Sichtbar für 2 Freunde". */
    fun visibleText(n: Int, sharing: Boolean, en: Boolean): String = when {
        !sharing -> if (en) "Location sharing off" else "Standort teilen aus"
        n <= 0 -> if (en) "Sharing on, but nobody may see you yet" else "Teilen an, aber noch darf dich niemand sehen"
        n == 1 -> if (en) "Visible to 1 friend" else "Sichtbar für 1 Freund"
        else -> if (en) "Visible to $n friends" else "Sichtbar für $n Freunde"
    }

    /** Namen offener Anfragen an dich, die noch nicht gemeldet wurden. */
    fun newIncoming(seen: Set<String>, cur: List<Friend>): List<String> =
        cur.filter { it.status == "incoming" && it.name !in seen }.map { it.name }

    fun incomingNames(cur: List<Friend>): Set<String> = cur.filter { it.status == "incoming" }.map { it.name }.toSet()

    private val NAME = Regex("^[\\p{L}\\p{N}_\\- ]{2,24}$")

    /** Profilname für eine Anfrage: 2 bis 24 Zeichen, sonst null. */
    fun cleanName(s: String): String? = s.trim().takeIf { NAME.matches(it) }
}
