package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.protocol.Lane
import io.github.th3s1nc.osmandhudbridge.protocol.LaneDirection
import io.github.th3s1nc.osmandhudbridge.protocol.LaneRecommendation

/**
 * Spurhinweis aus der Spuranzahl der Straße (die Routendaten kennen keine Spurpfeile): bei Abbiegen, Halten und Ausfahren
 * auf einer Straße mit mehreren Spuren "3 Spuren · rechts einordnen". Reine Logik, damit sie getestet werden kann.
 */
object LaneHint {
    /** Bis zu dieser Entfernung vor der Anweisung wird der Hinweis gezeigt. */
    const val SHOW_M = 500

    fun side(brCmd: Int): Int = when (brCmd) {
        BrouterCmd.TL, BrouterCmd.TSLL, BrouterCmd.TSHL, BrouterCmd.KL, BrouterCmd.EL, BrouterCmd.TLU -> -1
        BrouterCmd.TR, BrouterCmd.TSLR, BrouterCmd.TSHR, BrouterCmd.KR, BrouterCmd.ER, BrouterCmd.TRU -> 1
        else -> 0
    }

    /** Text oder null (weniger als 2 Spuren, unbekannt oder geradeaus/Kreisverkehr/Ziel). */
    fun text(lanes: Int, brCmd: Int): String? {
        if (lanes < 2 || lanes > 8) return null
        val s = side(brCmd)
        if (s == 0) return null
        val n = Tx.t("$lanes Spuren", "$lanes lanes")
        return n + " · " + if (s > 0) Tx.t("rechts einordnen", "keep to the right lane") else Tx.t("links einordnen", "keep to the left lane")
    }

    /** Bis zu dieser Entfernung vor der Anweisung bekommt das HUD die Spuren. */
    const val HUD_M = 400

    /**
     * Spuren fürs HUD, von links nach rechts. Das HUD zeichnet je Spur einen Pfeil (eine Spur mit mehreren Richtungen als
     * Geradeauspfeil) und hebt empfohlene Spuren hervor. Abbiegen nach rechts: rechte Spur Rechtspfeil, empfohlen; die übrigen
     * Geradeaus, nicht empfohlen. Links entsprechend. Leer = nichts zeigen (weniger als 2 Spuren, geradeaus, zu weit weg).
     */
    fun hudLanes(lanes: Int, brCmd: Int, distM: Int): List<Lane> {
        if (lanes < 2 || lanes > 8 || distM > HUD_M) return emptyList()
        val s = side(brCmd)
        if (s == 0) return emptyList()
        return List(lanes) { i ->
            val outer = if (s > 0) i == lanes - 1 else i == 0
            if (outer) Lane(if (s > 0) LaneDirection.RIGHT else LaneDirection.LEFT, LaneRecommendation.HIGHLY_RECOMMENDED)
            else Lane(LaneDirection.STRAIGHT, LaneRecommendation.NOT_RECOMMENDED)
        }
    }

    /** Soll vor dieser Anweisung näher herangezoomt werden? Abbiegen, Halten, Ausfahren, Kehre; nicht geradeaus, Kreisverkehr, Ziel. */
    fun zoomsIn(brCmd: Int): Boolean = side(brCmd) != 0

    /** Zoomstufe vor der Anweisung: Halten/Ausfahren und mehrspurig etwas näher. */
    fun zoomLevel(brCmd: Int, lanes: Int): Double =
        if (lanes >= 3 || brCmd == BrouterCmd.KL || brCmd == BrouterCmd.KR || brCmd == BrouterCmd.EL || brCmd == BrouterCmd.ER) 18.0 else 17.5
}
