package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.protocol.NavCommand
import java.util.Locale

/** Text und Zahlen für die Anzeige in der Kartenansicht. Reine Logik ohne Android, damit sie getestet werden kann. */
object MapOverlay {
    /** Abbiegepfeil als Zeichen; bei Kreisverkehr mit Ausfahrtsnummer. */
    fun glyph(cmd: NavCommand?, exit: Int): String = when (cmd) {
        null, NavCommand.NONE, NavCommand.STRAIGHT, NavCommand.MERGE -> "↑"
        NavCommand.TURN_LEFT -> "↰"
        NavCommand.TURN_RIGHT -> "↱"
        NavCommand.KEEP_LEFT, NavCommand.RAMP_LEFT -> "↖"
        NavCommand.KEEP_RIGHT, NavCommand.RAMP_RIGHT -> "↗"
        NavCommand.SHARP_LEFT -> "↙"
        NavCommand.SHARP_RIGHT -> "↘"
        NavCommand.UTURN, NavCommand.UTURN_LEFT -> "↶"
        NavCommand.UTURN_RIGHT -> "↷"
        NavCommand.GOAL, NavCommand.GOAL_LEFT, NavCommand.GOAL_RIGHT -> "⚑"
        NavCommand.VIA -> "◆"
        NavCommand.ROUNDABOUT, NavCommand.ROUNDABOUT_LEFT, NavCommand.ROUNDABOUT_RIGHT, NavCommand.ROUNDABOUT_FINISH ->
            if (exit > 0) "⟳$exit" else "⟳"
    }

    /** Entfernung: unter 1 km in 10-m-Schritten (unter 100 m in 10, sonst in 50 m), darüber mit einer Nachkommastelle. */
    fun dist(m: Int): String = when {
        m >= 1000 -> String.format(Locale.GERMANY, "%.1f km", m / 1000.0)
        m >= 200 -> "${(m + 25) / 50 * 50} m"
        else -> "${(m + 5) / 10 * 10} m"
    }

    fun km(m: Int): String = String.format(Locale.GERMANY, "%.1f km", m / 1000.0)

    /** Restzeit: "47 min" oder "1:05 h". */
    fun remaining(sec: Int): String {
        val min = (sec + 30) / 60
        return if (min < 60) "$min min" else String.format(Locale.GERMANY, "%d:%02d h", min / 60, min % 60)
    }

    /** Ankunftszeit "HH:mm" aus Jetzt (ms seit 1970, in Zeitzone [zoneOffsetMs]) plus Restzeit. */
    fun eta(nowMs: Long, remainingSec: Int, zoneOffsetMs: Long): String {
        val t = ((nowMs + zoneOffsetMs + remainingSec * 1000L) / 1000L).mod(86400L)
        return String.format(Locale.GERMANY, "%02d:%02d", t / 3600, (t / 60) % 60)
    }

    /** Himmelsrichtung (N, NO, O, …) zu einem Kurs in Grad. */
    fun compass(deg: Float): String {
        val names = arrayOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
        val i = (((deg % 360f) + 360f) % 360f / 45f + 0.5f).toInt() % 8
        return names[i]
    }

    /** Anteil der gefahrenen Strecke (0..1). */
    fun progress(progressM: Int, remainingM: Int): Float {
        val total = progressM + remainingM
        return if (total <= 0) 0f else (progressM.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Zu schnell: Limit bekannt (größer 0) und Tempo darüber (ganze km/h). */
    fun over(kmh: Float, limit: Int): Boolean = limit > 0 && kotlin.math.round(kmh).toInt() > limit
}
