package io.github.th3s1nc.osmandhudbridge.nav

/**
 * Entscheidungen für das Wiederverbinden des Benachrichtigungszugriffs (ohne Android, damit sie sich testen lassen).
 * Android lässt den Schalter manchmal an, trennt aber die Verbindung (Standby, Schlaflisten der Hersteller).
 */
object ListenerKeepLogic {
    /** Abstand zwischen zwei Aus-/Einschalt-Versuchen des Dienstes beim Hintergrund-Wächter (6 Stunden). */
    const val TOGGLE_GAP_BG_MS = 6 * 60 * 60 * 1000L
    /** Abstand, wenn der Nutzer die App öffnet (30 Minuten). */
    const val TOGGLE_GAP_OPEN_MS = 30 * 60 * 1000L
    /** Verzögerung für requestRebind nach einer Trennung. */
    const val REBIND_DELAY_MS = 2_500L
    /** So lange warten, ob requestRebind gewirkt hat, bevor der Dienst aus- und eingeschaltet wird. */
    const val WAIT_BEFORE_TOGGLE_MS = 5_000L
    /** Letzte Wartezeit nach dem Aus-/Einschalten (kurz, weil ein Alarm-Empfänger nur etwa 10 Sekunden Zeit hat: 5 + 1 + 2,5 s). */
    const val FINAL_WAIT_MS = 2_500L
    /** Pause zwischen Aus-/Einschalten und dem anschließenden requestRebind. */
    const val AFTER_TOGGLE_MS = 1_000L

    /** Nur eingreifen, wenn der Zugriff erlaubt ist und trotzdem keine Verbindung besteht. */
    fun needsHeal(granted: Boolean, connected: Boolean): Boolean = granted && !connected

    /** Aus-/Einschalten nur, wenn der letzte Versuch lange genug her ist (nie jede Minute, sonst kann der Eintrag verloren gehen). */
    fun mayToggle(nowMs: Long, lastToggleMs: Long, gapMs: Long): Boolean = lastToggleMs <= 0L || nowMs - lastToggleMs >= gapMs || nowMs < lastToggleMs
}
