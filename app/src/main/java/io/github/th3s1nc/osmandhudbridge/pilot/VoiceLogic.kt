package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.protocol.NavCommand
import java.util.Locale

/**
 * Entscheidet, was die Navigation ansagt. Pro Abbiegung höchstens zwei Ansagen: eine frühe ("In 300 Metern links abbiegen",
 * Abstand nach Tempo) und eine kurz davor ("Jetzt links abbiegen"). Dazu Zwischenziel, Ziel und Neuberechnung.
 * Reine Logik ohne Android, damit sie getestet werden kann. Alle Aufrufe vom selben Thread.
 */
class VoiceLogic {
    private var cur: Instruction? = null
    private var lastDist = Int.MAX_VALUE
    private var wasRerouting = false
    private var finishedSaid = false
    // Was schon gesagt wurde, bleibt gemerkt (auch wenn "nächste Anweisung" kurz hin- und herspringt): nie zweimal dasselbe
    private val earlySaid = HashSet<Instruction>()
    private val nowSaid = HashSet<Instruction>()
    private val farSaid = HashSet<Instruction>()
    /** "Viele Ansagen": zusätzlich eine frühe Vorankündigung, "halten" wird gesagt, bei dicht folgenden Abbiegungen ein "danach". */
    var many = false
    private var lastSpokenMs = Long.MIN_VALUE / 2
    private var lastRerouteSayMs = Long.MIN_VALUE / 2

    fun reset() {
        cur = null; lastDist = Int.MAX_VALUE; wasRerouting = false; finishedSaid = false
        earlySaid.clear(); nowSaid.clear(); farSaid.clear(); lastSpokenMs = Long.MIN_VALUE / 2; lastRerouteSayMs = Long.MIN_VALUE / 2
    }

    /** Nach einer Neuberechnung: die neue Route darf ihre Abbiegungen wieder ansagen. */
    fun newRoute() { earlySaid.clear(); nowSaid.clear(); farSaid.clear(); cur = null }

    /** Ein Schritt mit den aktuellen Werten; liefert die Sätze, die jetzt gesprochen werden sollen (meist keiner). */
    fun step(
        next: Instruction?, distM: Int, finished: Boolean, speedKmh: Float?, rerouting: Boolean,
        nowMs: Long = System.currentTimeMillis(), after: Instruction? = null
    ): List<String> {
        val out = ArrayList<String>()
        if (rerouting && !wasRerouting && nowMs - lastRerouteSayMs >= REROUTE_SAY_GAP_MS) {
            out += Tx.t("Route wird neu berechnet.", "Recalculating the route.")
            lastRerouteSayMs = nowMs; lastSpokenMs = nowMs
        }
        wasRerouting = rerouting
        if (finished) {
            if (!finishedSaid) { finishedSaid = true; out += Tx.t("Sie haben Ihr Ziel erreicht.", "You have arrived at your destination.") }
            return out
        }
        if (next != cur) {
            if (cur?.command == NavCommand.VIA && lastDist < 80 && nowMs - lastSpokenMs >= NOW_GAP_MS) {
                out += Tx.t("Zwischenziel erreicht.", "Waypoint reached."); lastSpokenMs = nowMs
            }
            cur = next
        }
        lastDist = distM
        val n = next ?: return out
        val cmd = n.command ?: return out
        if (!spokenInstr(n, many)) return out
        val v = (speedKmh ?: 0f).coerceAtLeast(0f) / 3.6
        val earlyAt = if (many) (v * 35).coerceIn(150.0, 1500.0) else (v * 25).coerceIn(150.0, 1000.0)
        val farAt = (v * 70).coerceIn(500.0, 3000.0)
        val nowAt = (v * 4).coerceIn(35.0, 120.0)
        if (n !in nowSaid && distM <= nowAt) {
            // Zu dicht nach der letzten Ansage: nicht sagen, aber auch nicht abhaken (kommt beim nächsten Schritt, falls noch passend)
            if (out.isEmpty() && nowMs - lastSpokenMs >= NOW_GAP_MS) {
                out += Tx.t("Jetzt ", "Now ") + phraseInstr(n, true) + immediately(n, after)
                nowSaid.add(n); earlySaid.add(n); lastSpokenMs = nowMs
            }
        } else if (n !in earlySaid && n !in nowSaid && distM <= earlyAt && distM > nowAt + 60) {
            if (out.isEmpty() && nowMs - lastSpokenMs >= EARLY_GAP_MS) {
                var t = Tx.t("In ${spokenDist(distM)} ", "In ${spokenDist(distM)} ") + phraseInstr(n, false)
                val imm = immediately(n, after)
                if (imm.isNotEmpty()) t += imm
                else if (many && after != null && spokenInstr(after, true) && after.atM - n.atM in 0..400)
                    t += Tx.t(", danach ", ", then ") + phraseInstr(after, false)
                out += t
                earlySaid.add(n); lastSpokenMs = nowMs
            }
        } else if (many && n !in farSaid && n !in earlySaid && n !in nowSaid && distM <= farAt && distM > earlyAt + 150) {
            if (out.isEmpty() && nowMs - lastSpokenMs >= EARLY_GAP_MS) {
                out += Tx.t("In ${spokenDist(distM)} ", "In ${spokenDist(distM)} ") + phraseInstr(n, false)
                farSaid.add(n); lastSpokenMs = nowMs
            }
        }
        return out
    }

    /** Folgt die nächste Abbiegung weniger als 60 m danach, sagt die Ansage ", danach sofort …" (auch ohne "viele Ansagen"). */
    private fun immediately(n: Instruction, after: Instruction?): String =
        if (after != null && spokenInstr(after, true) && after.atM - n.atM in 0..60)
            Tx.t(", danach sofort ", ", then immediately ") + phraseInstr(after, false)
        else ""

    companion object {
        /** Mindestabstand zwischen zwei Ansagen: "In 300 Metern …" und "Jetzt …" (ms). */
        const val EARLY_GAP_MS = 6_000L
        const val NOW_GAP_MS = 2_500L
        /** "Route wird neu berechnet" höchstens einmal in dieser Zeit (ms). */
        const val REROUTE_SAY_GAP_MS = 45_000L

        /**
         * Zu diesen Pfeilen gibt es keine Ansage: geradeaus, Einfädeln, Ziel und Zwischenziel melden sich selbst,
         * und leichte Kurven ("halten") sagt die Navigation nicht an (auf Landstraßen wären es dauernd welche).
         */
        fun spoken(c: NavCommand): Boolean = when (c) {
            NavCommand.NONE, NavCommand.STRAIGHT, NavCommand.MERGE, NavCommand.VIA,
            NavCommand.KEEP_LEFT, NavCommand.KEEP_RIGHT,
            NavCommand.GOAL, NavCommand.GOAL_LEFT, NavCommand.GOAL_RIGHT, NavCommand.ROUNDABOUT_FINISH -> false
            else -> true
        }

        /** Wird zu dieser Anweisung etwas gesagt? Leichte Abbiegungen immer, "halten" nur bei vielen Ansagen. */
        fun spokenInstr(i: Instruction, many: Boolean): Boolean {
            val c = i.command ?: return false
            return when (i.brCmd) {
                BrouterCmd.TSLL, BrouterCmd.TSLR -> true
                BrouterCmd.KL, BrouterCmd.KR -> many
                else -> spoken(c)
            }
        }

        fun phraseInstr(i: Instruction, now: Boolean): String = when (i.brCmd) {
            BrouterCmd.TSLL -> Tx.t("leicht links abbiegen", "turn slightly left")
            BrouterCmd.TSLR -> Tx.t("leicht rechts abbiegen", "turn slightly right")
            else -> phrase(i.command ?: NavCommand.NONE, i.exit, now)
        }

        private val ordDe = arrayOf("", "erste", "zweite", "dritte", "vierte", "fünfte", "sechste", "siebte", "achte", "neunte")
        private val ordEn = arrayOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth")

        /** Der Satz zur Abbiegung ohne Vorspann. [now]: kurz vor der Abbiegung. */
        fun phrase(c: NavCommand, exit: Int, now: Boolean): String = when (c) {
            NavCommand.TURN_LEFT -> Tx.t("links abbiegen", "turn left")
            NavCommand.TURN_RIGHT -> Tx.t("rechts abbiegen", "turn right")
            NavCommand.KEEP_LEFT -> Tx.t("links halten", "keep left")
            NavCommand.KEEP_RIGHT -> Tx.t("rechts halten", "keep right")
            NavCommand.SHARP_LEFT -> Tx.t("scharf links abbiegen", "turn sharp left")
            NavCommand.SHARP_RIGHT -> Tx.t("scharf rechts abbiegen", "turn sharp right")
            NavCommand.RAMP_LEFT -> Tx.t("links der Auffahrt folgen", "follow the ramp on the left")
            NavCommand.RAMP_RIGHT -> Tx.t("rechts der Auffahrt folgen", "follow the ramp on the right")
            NavCommand.UTURN, NavCommand.UTURN_LEFT, NavCommand.UTURN_RIGHT -> Tx.t("wenden", "make a U-turn")
            NavCommand.ROUNDABOUT, NavCommand.ROUNDABOUT_LEFT, NavCommand.ROUNDABOUT_RIGHT ->
                if (exit in 1..9) Tx.t("im Kreisverkehr die ${ordDe[exit]} Ausfahrt nehmen", "take the ${ordEn[exit]} exit at the roundabout")
                else if (exit > 9) Tx.t("im Kreisverkehr die Ausfahrt $exit nehmen", "take exit $exit at the roundabout")
                else Tx.t("in den Kreisverkehr einfahren", "enter the roundabout")
            else -> ""
        }

        /** Entfernung gesprochen: bis 1 km auf 50 m gerundet, darüber in Kilometern (Dativ: "300 Metern", "1,5 Kilometern"). */
        fun spokenDist(m: Int): String {
            if (m < 1000) {
                val r = (((m + 25) / 50) * 50).coerceAtLeast(50)
                if (r >= 1000) return Tx.t("einem Kilometer", "1 kilometer")
                return Tx.t("$r Metern", "$r meters")
            }
            val km = Math.round(m / 100.0) / 10.0
            if (km <= 1.0) return Tx.t("einem Kilometer", "1 kilometer")
            val s = if (km == Math.floor(km)) km.toInt().toString() else String.format(if (Tx.en) Locale.US else Locale.GERMANY, "%.1f", km)
            return Tx.t("$s Kilometern", "$s kilometers")
        }
    }
}
