package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Admin-Meldungen (ab App 0.35.0): reine Logik ohne Android, damit sie getestet werden kann.
 * Die Verbindung zum Server steht in [AdminNotes].
 */
object AdminNotesLogic {
    class Ev(val id: Int, val ts: Long, val typ: String, val text: String)

    const val MAX_KEPT = 50

    /** Neue Meldungen an die gemerkten anhängen: nur Nummern, die noch fehlen, höchstens [MAX_KEPT], neueste zuletzt. */
    fun merge(old: List<Ev>, fresh: List<Ev>): List<Ev> {
        val have = old.map { it.id }.toSet()
        return (old + fresh.filter { it.id !in have }).sortedBy { it.id }.takeLast(MAX_KEPT)
    }

    /** Wie viele Meldungen sind neuer als die zuletzt gelesene Nummer? */
    fun unread(evs: List<Ev>, seenId: Int): Int = evs.count { it.id > seenId }

    /** Höchste Nummer (0 = keine). */
    fun lastId(evs: List<Ev>): Int = evs.maxOfOrNull { it.id } ?: 0

    /** Knopfbeschriftung: "Admin-Meldungen" oder "Admin-Meldungen (3)". */
    fun buttonText(unread: Int, en: Boolean): String {
        val base = if (en) "Admin messages" else "Admin-Meldungen"
        return if (unread > 0) "$base ($unread)" else base
    }

    /** Text der Benachrichtigung: eine Meldung wörtlich, mehrere als Zahl. */
    fun notifyText(fresh: List<Ev>, en: Boolean): String = when {
        fresh.isEmpty() -> ""
        fresh.size == 1 -> fresh[0].text
        else -> if (en) "${fresh.size} new admin messages: " + fresh.last().text else "${fresh.size} neue Admin-Meldungen, zuletzt: " + fresh.last().text
    }

    /** Welche Meldungen sind seit dem letzten Hinweis neu? (Hinweisnummer höher, aber nur, wenn überhaupt schon ein Stand gemerkt war.) */
    fun toAnnounce(evs: List<Ev>, announcedId: Int): List<Ev> = evs.filter { it.id > announcedId }.sortedBy { it.id }

    // ---------- Status-Kacheln und Darstellung der Meldungsseite (ab App 0.36.0) ----------

    /** Eine Status-Kachel vom Server. level: "ok", "warn", "bad" oder "run"; pct = Fortschritt (oder -1). */
    class Status(val key: String, val label: String, val value: String, val info: String, val level: String, val pct: Int = -1)

    /** Titel der Meldung nach Art (der Text kommt vom Server). */
    fun headline(typ: String, en: Boolean): String = when (typ) {
        "scdb" -> if (en) "Speed camera list due" else "Blitzerliste fällig"
        "lauf_ok" -> if (en) "Update finished" else "Update fertig"
        "lauf_fehler" -> if (en) "Update had problems" else "Update mit Problemen"
        "haengt" -> if (en) "Server seems stuck" else "Server hängt"
        "baustellen" -> if (en) "Roadworks fetch failed" else "Baustellen-Abruf fehlgeschlagen"
        "zertifikat" -> if (en) "Certificate expires soon" else "Zertifikat läuft ab"
        "platte" -> if (en) "Disk almost full" else "Festplatte fast voll"
        "start" -> if (en) "Server started" else "Server gestartet"
        "profil" -> if (en) "New profile" else "Neues Profil"
        "version" -> if (en) "New server version" else "Neue Server-Version"
        "test" -> if (en) "Test message" else "Testmeldung"
        else -> if (en) "Message" else "Meldung"
    }

    /** Symbol der Meldung. */
    fun icon(typ: String): String = when (typ) {
        "lauf_ok" -> "✓"
        "scdb" -> "📋"
        "lauf_fehler", "haengt", "baustellen" -> "⚠"
        "zertifikat" -> "🔑"
        "platte" -> "💾"
        "start", "version" -> "⟳"
        "profil" -> "👤"
        else -> "🔔"
    }

    /** Farbstufe des Symbols: "ok", "warn", "bad" oder "info". */
    fun level(typ: String): String = when (typ) {
        "lauf_ok" -> "ok"
        "scdb", "zertifikat", "platte" -> "warn"
        "lauf_fehler", "haengt", "baustellen" -> "bad"
        else -> "info"
    }

    /** "gerade eben", "vor 12 Min.", "heute 07:00", "gestern 01:03" oder "03.11. 19:20". Zeitzone wird übergeben, damit es testbar ist. */
    fun ageText(tsSec: Long, nowSec: Long, tz: java.util.TimeZone, en: Boolean): String {
        val d = nowSec - tsSec
        if (d < 90) return if (en) "just now" else "gerade eben"
        if (d < 3600) return if (en) "${d / 60} min ago" else "vor ${d / 60} Min."
        fun cal(t: Long) = java.util.Calendar.getInstance(tz).apply { timeInMillis = t * 1000 }
        val a = cal(tsSec); val n = cal(nowSec)
        val hm = "%02d:%02d".format(a.get(java.util.Calendar.HOUR_OF_DAY), a.get(java.util.Calendar.MINUTE))
        val dayDiff = (n.get(java.util.Calendar.YEAR) * 366 + n.get(java.util.Calendar.DAY_OF_YEAR)) - (a.get(java.util.Calendar.YEAR) * 366 + a.get(java.util.Calendar.DAY_OF_YEAR))
        return when (dayDiff) {
            0 -> (if (en) "today " else "heute ") + hm
            1 -> (if (en) "yesterday " else "gestern ") + hm
            else -> "%02d.%02d. ".format(a.get(java.util.Calendar.DAY_OF_MONTH), a.get(java.util.Calendar.MONTH) + 1) + hm
        }
    }
}
