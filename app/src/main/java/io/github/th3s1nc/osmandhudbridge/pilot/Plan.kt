package io.github.th3s1nc.osmandhudbridge.pilot

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Ein Punkt der Planung mit Anzeigename. */
class PlanPoint(val lat: Double, val lon: Double, val name: String)

/**
 * Die geplante Fahrt: Start (null = aktuelle Position), Zwischenziele (höchstens [MAX_VIAS]) und Ziel.
 * Reine Logik ohne Android, damit sie getestet werden kann.
 */
class Plan {
    var start: PlanPoint? = null
    val vias = ArrayList<PlanPoint>()
    var dest: PlanPoint? = null

    /** Zwischenziel anhängen; false, wenn schon [MAX_VIAS] da sind. */
    fun addVia(p: PlanPoint): Boolean {
        if (vias.size >= MAX_VIAS) return false
        vias += p
        return true
    }

    fun removeVia(i: Int) { if (i in vias.indices) vias.removeAt(i) }

    /** Zwischenziel um [delta] Plätze verschieben (-1 nach oben, +1 nach unten). */
    fun moveVia(i: Int, delta: Int) {
        val j = i + delta
        if (i !in vias.indices || j !in vias.indices) return
        val p = vias.removeAt(i)
        vias.add(j, p)
    }

    /** Zwischenziel nach oben: das erste wird zum Start (die aktuelle Position fällt dann weg), sonst Platz tauschen. */
    fun moveViaUp(i: Int) {
        if (i !in vias.indices) return
        if (i == 0) start = vias.removeAt(0) else moveVia(i, -1)
    }

    /** Zwischenziel nach unten: das letzte wird zum Ziel (das alte Ziel rückt als letztes Zwischenziel nach), sonst Platz tauschen. */
    fun moveViaDown(i: Int) {
        if (i !in vias.indices) return
        val d = dest
        if (i == vias.size - 1 && d != null) { dest = vias[i]; vias[i] = d } else moveVia(i, 1)
    }

    /** Ziel mit dem letzten Zwischenziel tauschen (Ziel rückt hoch). */
    fun destUp() {
        val d = dest ?: return
        if (vias.isEmpty()) return
        val j = vias.size - 1
        dest = vias[j]; vias[j] = d
    }

    /** Gewählten Start mit dem ersten Zwischenziel tauschen (Start rückt nach unten). */
    fun startDown() {
        val s = start ?: return
        if (vias.isEmpty()) return
        start = vias[0]; vias[0] = s
    }

    /** Start und Ziel tauschen und die Zwischenziele umdrehen (Gegenrichtung). [current] ersetzt "aktuelle Position". false, wenn nicht möglich. */
    fun reverse(current: PlanPoint?): Boolean {
        val s = start ?: current ?: return false
        val d = dest ?: return false
        start = d
        dest = s
        vias.reverse()
        return true
    }

    fun clear() { start = null; vias.clear(); dest = null }

    /** Wegpunkte für den Router: Start (gewählt oder [current]), Zwischenziele, Ziel. null, wenn Ziel oder Start fehlen. */
    fun waypoints(current: PlanPoint?): List<Waypoint>? {
        val s = start ?: current ?: return null
        val d = dest ?: return null
        val out = ArrayList<Waypoint>()
        out += Waypoint(s.lat, s.lon, false, s.name)
        for (v in vias) out += Waypoint(v.lat, v.lon, true, v.name)
        out += Waypoint(d.lat, d.lon, false, d.name)
        return out
    }

    companion object {
        const val MAX_VIAS = 25

        /** Luftlinie in Metern. */
        fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
            val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }

        /** Name für einen Punkt ohne eigenen Namen (langer Druck auf die Karte). */
        fun coordName(lat: Double, lon: Double): String = String.format(Locale.US, "%.4f, %.4f", lat, lon)
    }
}

/** Entfernung für die Trefferliste: "800 m" oder "12 km". */
object PlaceFormat {
    private val houseNo = Regex("^\\d+\\s?[a-zA-Z]?$")

    /** Aus der Nominatim-Angabe einen Namen machen; steht vorn nur eine Hausnummer, wird "Straße Nummer" daraus. */
    fun nameOf(name: String, display: String): String {
        if (name.isNotBlank()) return name
        val parts = display.split(",").map { it.trim() }
        return if (parts.size > 1 && houseNo.matches(parts[0])) parts[1] + " " + parts[0] else parts[0]
    }

    /** Ortsangabe nach dem Namen (bei Straße mit Hausnummer fehlen beide vorderen Teile). */
    fun detailOf(name: String, display: String): String {
        val parts = display.split(",").map { it.trim() }
        val skip = if (name.isBlank() && parts.size > 1 && houseNo.matches(parts[0])) 2 else 1
        return parts.drop(skip).joinToString(", ").take(70)
    }

    fun dist(m: Double): String = when {
        m < 950 -> "${((m + 25) / 50).toInt() * 50} m"
        m < 10_000 -> String.format(Locale.GERMANY, "%.1f km", m / 1000)
        else -> "${(m / 1000).toInt()} km"
    }
}
