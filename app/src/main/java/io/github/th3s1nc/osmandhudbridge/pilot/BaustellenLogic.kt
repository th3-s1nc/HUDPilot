package io.github.th3s1nc.osmandhudbridge.pilot

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Eine Baustelle oder Sperrung vom Server (Mobilithek). kind: "c" gesperrt, "r" eingeschränkt (Ampel, eine Spur), "w" Arbeiten ohne Einschränkung. */
class Site(
    val id: String, val road: String, val text: String, val kind: String,
    val startSec: Long, val endSec: Long,
    /** Linie der Baustelle als (lat, lon); mindestens zwei Punkte (echte Straße oder gerade Linie von Anfang bis Ende). */
    val line: List<Pair<Double, Double>>
) {
    val minLat = line.minOf { it.first }; val maxLat = line.maxOf { it.first }
    val minLon = line.minOf { it.second }; val maxLon = line.maxOf { it.second }
}

class SiteHit(val site: Site, val distM: Double)

object BaustellenLogic {
    /** Rang für die Anzeige: gesperrt vor eingeschränkt vor Arbeiten. */
    fun rank(kind: String) = when (kind) { "c" -> 3; "r" -> 2; else -> 1 }

    /** Gilt die Meldung zum Zeitpunkt t (Sekunden)? Ende 0 = gilt weiter. */
    fun activeAt(s: Site, t: Long): Boolean = (s.endSec == 0L || s.endSec >= t) && (s.startSec == 0L || s.startSec <= t)

    /** Abstand eines Punkts zu einer Strecke in Metern (flache Näherung, genau genug für Straßen). */
    fun segDist(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val kLat = 110_540.0; val kLon = 111_320.0 * cos(Math.toRadians(p.first))
        val ax = (a.second - p.second) * kLon; val ay = (a.first - p.first) * kLat
        val bx = (b.second - p.second) * kLon; val by = (b.first - p.first) * kLat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 <= 0.0) return hypot(ax, ay)
        val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    private fun side(p: Pair<Double, Double>, q: Pair<Double, Double>, r: Pair<Double, Double>): Double =
        (q.second - p.second) * (r.first - p.first) - (q.first - p.first) * (r.second - p.second)

    private fun cross(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>, d: Pair<Double, Double>): Boolean {
        val d1 = side(a, b, c); val d2 = side(a, b, d); val d3 = side(c, d, a); val d4 = side(c, d, b)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun minDist(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>, stopBelow: Double): Double {
        var best = Double.MAX_VALUE
        // Punkte von a gegen Strecken von b und umgekehrt: so wird auch eine Kreuzung ohne gemeinsamen Stützpunkt erkannt
        for (p in a) for (i in 0 until b.size - 1) { val d = segDist(p, b[i], b[i + 1]); if (d < best) { best = d; if (best <= stopBelow) return best } }
        for (p in b) for (i in 0 until a.size - 1) { val d = segDist(p, a[i], a[i + 1]); if (d < best) { best = d; if (best <= stopBelow) return best } }
        // Echte Kreuzung zweier Strecken (kein Stützpunkt nah genug, aber die Linien schneiden sich)
        for (i in 0 until a.size - 1) for (j in 0 until b.size - 1) if (cross(a[i], a[i + 1], b[j], b[j + 1])) return 0.0
        return best
    }

    /**
     * Welche Meldungen liegen auf der Route? route = Punkte (lat, lon), t = Zeitpunkt der Fahrt, radiusM = Abstand.
     * Nur gesperrt und eingeschränkt zählen (reine Arbeiten ohne Einschränkung nicht). Sortiert: gesperrt zuerst, dann nach Abstand.
     */
    fun onRoute(route: List<Pair<Double, Double>>, sites: List<Site>, t: Long, radiusM: Double = 35.0): List<SiteHit> {
        if (route.size < 2) return emptyList()
        val mLat = route.minOf { it.first }; val xLat = route.maxOf { it.first }
        val mLon = route.minOf { it.second }; val xLon = route.maxOf { it.second }
        val padLat = radiusM / 110_540.0; val padLon = radiusM / (111_320.0 * cos(Math.toRadians((mLat + xLat) / 2)))
        val out = ArrayList<SiteHit>()
        for (s in sites) {
            if (s.kind == "w" || !activeAt(s, t)) continue
            if (s.maxLat < mLat - padLat || s.minLat > xLat + padLat || s.maxLon < mLon - padLon || s.minLon > xLon + padLon) continue
            val d = minDist(route, s.line, radiusM)
            if (d <= radiusM) out += SiteHit(s, d)
        }
        return out.sortedWith(compareByDescending<SiteHit> { rank(it.site.kind) }.thenBy { it.distM })
    }

    /**
     * Sperrlinien für BRouter: alle gesperrten, gültigen Meldungen in der Nähe der Route (Umkreis etwa 0,25 Grad),
     * jede als {lat, lon, lat, lon, ...} mit höchstens 40 Punkten. Höchstens 200 Linien.
     */
    fun nogoLines(sites: List<Site>, t: Long, rLats: DoubleArray, rLons: DoubleArray): List<DoubleArray> {
        if (rLats.isEmpty()) return emptyList()
        val pad = 0.25
        var mLat = Double.MAX_VALUE; var xLat = -Double.MAX_VALUE; var mLon = Double.MAX_VALUE; var xLon = -Double.MAX_VALUE
        for (v in rLats) { if (v < mLat) mLat = v; if (v > xLat) xLat = v }
        for (v in rLons) { if (v < mLon) mLon = v; if (v > xLon) xLon = v }
        mLat -= pad; xLat += pad; mLon -= pad; xLon += pad
        val out = ArrayList<DoubleArray>()
        for (s in sites) {
            if (s.kind != "c" || !activeAt(s, t)) continue
            if (s.maxLat < mLat || s.minLat > xLat || s.maxLon < mLon || s.minLon > xLon) continue
            val line = if (s.line.size <= 40) s.line else (0 until 39).map { s.line[it * (s.line.size - 1) / 39] } + s.line.last()
            val a = DoubleArray(line.size * 2); line.forEachIndexed { i, p -> a[2 * i] = p.first; a[2 * i + 1] = p.second }
            out += a
            if (out.size >= 200) break
        }
        return out
    }

    /** Erste Zeile des Texts (Straße und Ort) für eine kurze Anzeige. */
    fun title(s: Site): String {
        val first = s.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (first.length > 60) first.take(57) + "…" else first
    }

    fun kindName(kind: String, en: Boolean = false) = when (kind) {
        "c" -> if (en) "Closed" else "Gesperrt"
        "r" -> if (en) "Restricted" else "Eingeschränkt"
        else -> if (en) "Roadworks" else "Bauarbeiten"
    }

    /** Bis wann (Datum tt.mm.) oder leer, wenn kein Ende bekannt ist. */
    fun untilText(s: Site, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        if (s.endSec <= 0L) return ""
        val c = java.util.Calendar.getInstance(zone); c.timeInMillis = s.endSec * 1000
        return "%02d.%02d.".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1)
    }

    /** Kurzer Hinweis für die Routenvorschau, null wenn nichts im Weg liegt. */
    fun routeNote(hits: List<SiteHit>, en: Boolean = false): String? {
        if (hits.isEmpty()) return null
        val closed = hits.count { it.site.kind == "c" }; val restr = hits.size - closed
        val first = hits[0].site
        val until = untilText(first).let { if (it.isEmpty()) "" else (if (en) ", until " else ", bis ") + it }
        val head = if (en) {
            (if (closed > 0) "$closed closure(s)" else "") + (if (closed > 0 && restr > 0) " and " else "") + (if (restr > 0) "$restr restriction(s)" else "") + " on the route"
        } else {
            (if (closed > 0) "$closed Sperrung" + (if (closed > 1) "en" else "") else "") + (if (closed > 0 && restr > 0) " und " else "") +
                (if (restr > 0) "$restr Einschränkung" + (if (restr > 1) "en" else "") else "") + " auf der Route"
        }
        return "⚠ $head: ${title(first)}$until" + if (hits.size > 1) (if (en) " …" else " …") else ""
    }
}
