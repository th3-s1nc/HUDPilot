package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.limit.Tx

/** Regeln für ein neues Passwort (gleich wie auf dem Server). Reine Logik ohne Android. */
object PasswordRules {
    const val MIN = 8
    const val MAX = 64

    /** null = in Ordnung, sonst die Meldung für den Nutzer. */
    fun check(old: String, neu: String, repeat: String): String? {
        val o = old.trim()
        if (o.isEmpty()) return Tx.t("Bitte das bisherige Passwort eintragen.", "Please enter the current password.")
        if (neu.length < MIN) return Tx.t("Das neue Passwort braucht mindestens $MIN Zeichen.", "The new password needs at least $MIN characters.")
        if (neu.length > MAX) return Tx.t("Das neue Passwort darf höchstens $MAX Zeichen haben.", "The new password may have at most $MAX characters.")
        if (neu.any { it.code !in 32..126 || it == '|' || it == ';' })
            return Tx.t("Nur normale Zeichen ohne Umlaute, ohne | und ohne ;", "Only plain characters, no umlauts, no | and no ;")
        if (neu == o) return Tx.t("Das neue Passwort ist gleich dem alten.", "The new password is the same as the old one.")
        if (neu != repeat) return Tx.t("Die beiden neuen Passwörter sind nicht gleich.", "The two new passwords do not match.")
        return null
    }
}
