package io.github.th3s1nc.osmandhudbridge.nav

/**
 * Erkennt die Benachrichtigungen der Moket-App (Paket app.moket) und macht daraus den Text fürs HUD.
 * Erkennung (nur deutsche Texte, Stand der Testfahrten): Zusatzdaten "kind" wenn vorhanden, sonst Titel und Text.
 * Reine Logik ohne Android (testbar).
 */
object MoketParser {
    enum class Kind { HELP, HELP_MSG, ENCOUNTER, CHAT_GROUP, CHAT_PRIVATE }

    /** [hud] = Text fürs HUD (höchstens 19 Zeichen, nur ASCII), [key] = Chat bzw. Absender (für das Zusammenfassen). */
    class Result(val kind: Kind, val hud: String, val key: String? = null)

    const val MAX_CHARS = 19

    private val KIND = Regex(""""kind"\s*:\s*"([^"]+)"""")
    private val HELP_TEXT = Regex("""^(.+?)\s+etwa\s+(\d+(?:[.,]\d+)?)\s*km\b""", RegexOption.IGNORE_CASE)

    /** [channel]: Benachrichtigungskanal, [tag]: Tag, [body]: Zusatzfeld "body" (JSON-Text) oder null. */
    fun parse(title: String?, text: String?, channel: String?, tag: String?, body: String?): Result? {
        val ti = title?.trim().orEmpty()
        val tx = text?.trim().orEmpty()
        if (channel != null && channel.contains("moket-ride")) return null // Fahrtanzeige von Moket
        if (tag != null && tag.contains("Aggregate")) return null // Sammelzeile
        if (ti.isEmpty() && tx.isEmpty()) return null
        val kind = body?.let { KIND.find(it)?.groupValues?.get(1) }
        when (kind) {
            "help_response" -> return named(Kind.HELP_MSG, ti.removeSuffix(" kann dir helfen").trim(), " hilft", prefix = "")
            "help_message" -> return named(Kind.HELP_MSG, ti, "", prefix = "Hilfe: ")
            "event_message" -> return group(ti)
            "help_share", "help_closed", "contact_accepted" -> return null
        }
        if (ti.equals("Ein Biker braucht Hilfe", true)) return help(tx)
        if (ti.startsWith("Du hast einen Biker gekreuzt", true)) return Result(Kind.ENCOUNTER, "Biker gekreuzt")
        if (tx.equals("Neue Nachricht", true) && ti.isNotEmpty()) {
            val n = ascii(ti).take(MAX_CHARS)
            return if (n.isEmpty()) null else Result(Kind.CHAT_PRIVATE, n, key = n)
        }
        if (tx.startsWith("Neue Nachricht von ", true) && ti.isNotEmpty()) return group(ti)
        return null
    }

    private fun help(text: String): Result {
        val m = HELP_TEXT.find(text) ?: return Result(Kind.HELP, "HILFE in der Naehe")
        val art = shorten(ascii(m.groupValues[1]).substringBefore(' '))
        val dist = m.groupValues[2] + "km"
        val room = MAX_CHARS - "HILFE ".length - 1 - dist.length
        val a = art.take(room.coerceAtLeast(0))
        return Result(Kind.HELP, if (a.isEmpty()) "HILFE $dist" else "HILFE $a $dist")
    }

    private val SUFFIXES = listOf("schaden", "panne", "defekt", "problem", "mangel")

    /** Endungen weglassen, wenn davor noch ein Wort bleibt: "Reifenschaden" -> "Reifen", "Motorschaden" -> "Motor". "Panne" bleibt. */
    private fun shorten(art: String): String {
        for (suf in SUFFIXES) if (art.length >= suf.length + 3 && art.endsWith(suf, ignoreCase = true)) return art.dropLast(suf.length)
        return art
    }

    private fun named(kind: Kind, name: String, suffix: String, prefix: String): Result? {
        val n = ascii(name)
        if (n.isEmpty()) return null
        val room = MAX_CHARS - prefix.length - suffix.length
        return Result(kind, prefix + n.take(room.coerceAtLeast(1)) + suffix)
    }

    private fun group(title: String): Result? {
        val n = ascii(chatName(title)).take(MAX_CHARS)
        return if (n.isEmpty()) null else Result(Kind.CHAT_GROUP, n, key = n)
    }

    /** Chat-Name ohne Emojis und ohne Zusatz in Klammern ("Christkindljagt 🎄 (Test)" -> "Christkindljagt"). */
    fun chatName(title: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < title.length) {
            val cp = title.codePointAt(i)
            if (cp == '('.code || cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0xFE00..0xFE0F) break
            sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    /** Wie auf dem HUD: Umlaute umschreiben, nur ASCII. */
    fun ascii(s: String): String {
        val t = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
            .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss")
        val d = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in d) if (c.code in 0x20..0x7e) sb.append(c)
        return sb.toString().trim()
    }
}

/**
 * Fasst Chat-Meldungen zusammen: je Chat höchstens eine Anzeige in [windowMs]; die dazwischen eingetroffenen
 * werden mitgezählt und mit der nächsten Anzeige als "(n)" angehängt.
 */
class NoticeThrottle(private val windowMs: Long = 30_000L) {
    private class S(var shownAt: Long, var pending: Int)
    private val chats = HashMap<String, S>()

    /** Text fürs HUD oder null, wenn für diesen Chat gerade nichts angezeigt werden soll. */
    fun next(key: String, hud: String, nowMs: Long): String? {
        val s = chats.getOrPut(key) { S(Long.MIN_VALUE / 2, 0) }
        s.pending++
        if (nowMs - s.shownAt < windowMs) return null
        val n = s.pending
        s.pending = 0
        s.shownAt = nowMs
        if (chats.size > 50) chats.keys.retainAll(setOf(key))
        if (n <= 1) return hud
        val suffix = " ($n)"
        return hud.take((MoketParser.MAX_CHARS - suffix.length).coerceAtLeast(1)) + suffix
    }

    fun reset() = chats.clear()
}
