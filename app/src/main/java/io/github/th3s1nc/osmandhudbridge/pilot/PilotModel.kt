package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.protocol.NavCommand
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

data class RoutePoint(val lat: Double, val lon: Double, val ele: Float)

/** Ein Wegpunkt der Planung. visit = Zwischenziel (wird sicher angefahren), sonst Formpunkt. */
data class Waypoint(val lat: Double, val lon: Double, val visit: Boolean = false, val name: String = "")

/** Eine Anweisung entlang der Route. brCmd ist der BRouter-Befehl (Zahlen siehe BrouterCmd). */
data class Instruction(val pointIndex: Int, val atM: Int, val brCmd: Int, val exit: Int, val command: NavCommand?, val lanes: Int = 0) {
    fun text(): String = BrouterCmd.text(brCmd, exit)
}

/** Berechnete Route mit Anweisungen. cumM[i] = Strecke vom Start bis Punkt i in Metern. */
class PilotRoute(
    val points: List<RoutePoint>,
    val cumM: IntArray,
    val instructions: List<Instruction>,
    val seconds: Int,
    val ascendM: Int,
    /** Punktindex der übergebenen Wegpunkte (Start, Formpunkte, Zwischenziele, Ziel). */
    val waypointIndex: IntArray = IntArray(0)
) {
    val totalM: Int get() = if (cumM.isEmpty()) 0 else cumM[cumM.size - 1]

    companion object {
        /**
         * Aus den Zahlenfeldern von BRouter bauen. Fügt Zwischenziele (visit) und das Ziel als eigene Anweisungen ein
         * und lässt Befehle ohne HUD-Pfeil (z. B. "Route verlassen") weg.
         */
        fun build(
            lats: DoubleArray, lons: DoubleArray, ele: FloatArray, seconds: Int, ascendM: Int,
            hintIndex: IntArray, hintCmd: IntArray, hintExit: IntArray,
            waypointIndex: IntArray, visit: BooleanArray,
            hintLanes: IntArray = IntArray(0)
        ): PilotRoute {
            val n = lats.size
            val pts = ArrayList<RoutePoint>(n)
            for (i in 0 until n) pts.add(RoutePoint(lats[i], lons[i], if (i < ele.size) ele[i] else 0f))
            val cum = IntArray(n)
            var acc = 0.0
            for (i in 1 until n) {
                acc += Geo.distance(lats[i - 1], lons[i - 1], lats[i], lons[i])
                cum[i] = acc.toInt()
            }
            val list = ArrayList<Instruction>()
            for (i in hintIndex.indices) {
                val idx = hintIndex[i].coerceIn(0, n - 1)
                val hud = BrouterCmd.toHud(hintCmd[i], abs(hintExit[i])) ?: continue
                list.add(Instruction(idx, cum[idx], hintCmd[i], abs(hintExit[i]), hud, hintLanes.getOrElse(i) { 0 }))
            }
            // Zwischenziele (nicht Start, nicht Ziel)
            for (w in 1 until waypointIndex.size - 1) {
                if (w < visit.size && visit[w]) {
                    val idx = waypointIndex[w].coerceIn(0, n - 1)
                    list.add(Instruction(idx, cum[idx], BrouterCmd.VIA, 0, NavCommand.VIA))
                }
            }
            if (n > 0) list.add(Instruction(n - 1, cum[n - 1], BrouterCmd.END, 0, NavCommand.GOAL))
            list.sortWith(compareBy({ it.atM }, { if (it.brCmd == BrouterCmd.END) 1 else 0 }))
            return PilotRoute(pts, cum, HintMerge.simplify(list), seconds, ascendM, waypointIndex.copyOf())
        }
    }
}

/**
 * Fasst dicht aufeinanderfolgende Hinweise zusammen: leichte Kurven ("leicht rechts", "halten") kurz vor einem Kreisverkehr
 * oder einer anderen Abbiegung entfallen (die Abbiegung wird dann früh angesagt), und zwei gleiche leichte Kurven dicht
 * hintereinander werden eine. Einzelne leichte Kurven bleiben.
 */
object HintMerge {
    const val BEFORE_TURN_M = 250
    const val SAME_SLIGHT_M = 120

    private fun slightSide(c: Int): Int = when (c) {
        BrouterCmd.TSLL, BrouterCmd.KL -> -1
        BrouterCmd.TSLR, BrouterCmd.KR -> 1
        else -> 0
    }

    private fun isManeuver(c: Int): Boolean = when (c) {
        BrouterCmd.TL, BrouterCmd.TR, BrouterCmd.TSHL, BrouterCmd.TSHR, BrouterCmd.TU, BrouterCmd.TLU, BrouterCmd.TRU,
        BrouterCmd.RNDB, BrouterCmd.RNLB, BrouterCmd.EL, BrouterCmd.ER -> true
        else -> false
    }

    fun simplify(list: List<Instruction>): List<Instruction> {
        // 1) leichte Kurve direkt vor einer Abbiegung / einem Kreisverkehr (über weitere leichte Kurven hinweg gesehen)
        val keep = BooleanArray(list.size) { true }
        for (i in list.indices) {
            if (slightSide(list[i].brCmd) == 0) continue
            var k = i + 1
            while (k < list.size && slightSide(list[k].brCmd) != 0) k++
            if (k < list.size && isManeuver(list[k].brCmd) && list[k].atM - list[i].atM <= BEFORE_TURN_M) keep[i] = false
        }
        val step1 = list.filterIndexed { i, _ -> keep[i] }
        // 2) zwei gleiche leichte Kurven dicht hintereinander: nur die erste
        val out = ArrayList<Instruction>()
        for (ins in step1) {
            val side = slightSide(ins.brCmd)
            val prev = out.lastOrNull()
            if (side != 0 && prev != null && slightSide(prev.brCmd) == side && ins.atM - prev.atM <= SAME_SLIGHT_M) continue
            out.add(ins)
        }
        return out
    }
}

/** Befehle von BRouter (VoiceHint) und ihre Zuordnung zum HUD-Pfeil. */
object BrouterCmd {
    const val C = 1
    const val TL = 2
    const val TSLL = 3
    const val TSHL = 4
    const val TR = 5
    const val TSLR = 6
    const val TSHR = 7
    const val KL = 8
    const val KR = 9
    const val TLU = 10
    const val TRU = 11
    const val OFFR = 12
    const val RNDB = 13
    const val RNLB = 14
    const val TU = 15
    const val BL = 16
    const val EL = 17
    const val ER = 18
    const val VIA = 90 // eigener Wert für Zwischenziele
    const val END = 100

    /** Zuordnung der Pfeile (leichte Kurven = "halten"). null = kein Pfeil. */
    fun toHud(cmd: Int, exit: Int): NavCommand? = when (cmd) {
        C -> NavCommand.STRAIGHT
        TL -> NavCommand.TURN_LEFT
        TSLL -> NavCommand.KEEP_LEFT
        TSHL -> NavCommand.SHARP_LEFT
        TR -> NavCommand.TURN_RIGHT
        TSLR -> NavCommand.KEEP_RIGHT
        TSHR -> NavCommand.SHARP_RIGHT
        KL -> NavCommand.KEEP_LEFT
        KR -> NavCommand.KEEP_RIGHT
        TLU, TU -> NavCommand.UTURN_LEFT
        TRU -> NavCommand.UTURN_RIGHT
        RNDB -> NavCommand.ROUNDABOUT
        RNLB -> NavCommand.ROUNDABOUT_LEFT
        EL -> NavCommand.RAMP_LEFT
        ER -> NavCommand.RAMP_RIGHT
        END -> NavCommand.GOAL
        else -> null
    }

    fun text(cmd: Int, exit: Int): String = when (cmd) {
        C -> Tx.t("Geradeaus", "Continue straight")
        TL -> Tx.t("Links abbiegen", "Turn left")
        TSLL -> Tx.t("Leicht links", "Slight left")
        TSHL -> Tx.t("Scharf links", "Sharp left")
        TR -> Tx.t("Rechts abbiegen", "Turn right")
        TSLR -> Tx.t("Leicht rechts", "Slight right")
        TSHR -> Tx.t("Scharf rechts", "Sharp right")
        KL -> Tx.t("Links halten", "Keep left")
        KR -> Tx.t("Rechts halten", "Keep right")
        TLU, TU, TRU -> Tx.t("Wenden", "Make a U-turn")
        RNDB, RNLB -> if (exit > 0) Tx.t("Kreisverkehr, $exit. Ausfahrt", "Roundabout, exit $exit") else Tx.t("Kreisverkehr", "Roundabout")
        EL -> Tx.t("Ausfahrt links", "Exit left")
        ER -> Tx.t("Ausfahrt rechts", "Exit right")
        VIA -> Tx.t("Zwischenziel", "Waypoint")
        END -> Tx.t("Ziel", "Destination")
        else -> ""
    }
}

/** Kleine Geo-Hilfen (kurze Strecken, Näherung reicht). */
object Geo {
    private const val M_PER_DEG = 111_320.0

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = (lat2 - lat1) * M_PER_DEG
        val dx = (lon2 - lon1) * M_PER_DEG * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dx * dx + dy * dy)
    }

    /** Richtung von Punkt 1 nach Punkt 2 in Grad (0 = Nord, im Uhrzeigersinn). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dy = lat2 - lat1
        val dx = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val deg = Math.toDegrees(atan2(dx, dy))
        return (deg + 360.0) % 360.0
    }

    /** Punkt in Entfernung distM von (lat, lon) in Richtung bearingDeg. */
    fun offset(lat: Double, lon: Double, bearingDeg: Double, distM: Double): Pair<Double, Double> {
        val rad = Math.toRadians(bearingDeg)
        val dLat = distM * cos(rad) / M_PER_DEG
        val dLon = distM * kotlin.math.sin(rad) / (M_PER_DEG * cos(Math.toRadians(lat)))
        return Pair(lat + dLat, lon + dLon)
    }
}

/** Neuplanung nach einer Abweichung: von der aktuellen Position zu den Wegpunkten, die noch vor dir liegen. */
object Replan {
    fun remaining(route: PilotRoute, wps: List<Waypoint>, distM: Int, lat: Double, lon: Double): List<Waypoint> {
        val out = ArrayList<Waypoint>()
        out.add(Waypoint(lat, lon, false, "Position"))
        for (w in 1 until wps.size) {
            val last = w == wps.size - 1
            val idx = route.waypointIndex.getOrNull(w)
            val ahead = idx == null || route.cumM[idx.coerceIn(0, route.cumM.size - 1)] > distM
            if (last || ahead) out.add(wps[w].copy(visit = wps[w].visit && !last))
        }
        return out
    }
}
