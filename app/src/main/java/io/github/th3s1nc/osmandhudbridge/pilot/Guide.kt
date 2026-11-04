package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Führung entlang einer Route: aus GPS-Positionen wird "wo bin ich auf der Route, was kommt als Nächstes, bin ich abgekommen".
 * Reine Rechnung ohne Android. Wird vom echten GPS und von der Simulation gleich benutzt.
 */
class Guide(route: PilotRoute, private var wps: List<Waypoint>) {
    var route: PilotRoute = route
        private set

    data class State(
        val progressM: Int, val lateralM: Int,
        val remainingM: Int, val remainingSec: Int,
        val next: Instruction?, val nextDistM: Int,
        val offRoute: Boolean, val finished: Boolean,
        /** Insgesamt gefahrene Strecke (auch vor früheren Neuberechnungen), für den Fortschrittsbalken. */
        val doneM: Int = progressM
    )

    private var seg = 0            // Index des Segments, auf dem wir zuletzt waren
    private var progress = 0.0     // Strecke vom Start in Metern
    private var offCount = 0
    private var lastRerouteMs = Long.MIN_VALUE / 2
    private var finishedFlag = false
    private var doneBefore = 0.0   // Strecke aus früheren Routen (vor Neuberechnungen)
    private var lateral = 0.0
    private var rerouteStreak = 0  // Neuberechnungen kurz hintereinander: Wartezeit wird länger (Schleifen bremsen)

    /** Abstand zur Route (m), ab dem eine Position als "daneben" zählt. */
    var offRouteM = 40.0
    /** So viele Positionen hintereinander daneben, bevor neu berechnet wird (GPS-Ausreißer abfangen). */
    var offRouteFixes = 3
    /** Mindestabstand zwischen zwei Neuberechnungen (ms). */
    var rerouteCooldownMs = 5_000L

    val waypoints: List<Waypoint> get() = wps

    fun setRoute(r: PilotRoute, newWps: List<Waypoint>, nowMs: Long) {
        rerouteStreak = if (nowMs - lastRerouteMs < 90_000L) (rerouteStreak + 1).coerceAtMost(4) else 0
        doneBefore += progress
        route = r
        wps = newWps
        seg = 0
        progress = 0.0
        offCount = 0
        lastRerouteMs = nowMs
        finishedFlag = false
    }

    /** Nach Abweichung: true, wenn jetzt neu berechnet werden soll. */
    fun shouldReroute(nowMs: Long): Boolean =
        !finishedFlag && offCount >= offRouteFixes &&
            (nowMs - lastRerouteMs >= cooldownMs() || (lateral > 100.0 && nowMs - lastRerouteMs >= 3_000L))

    /** Aktuelle Wartezeit bis zur nächsten Neuberechnung: 5 s, nach schneller Wiederholung 10, 15 s (mehr nicht). Wer schon über 100 m weg ist, wartet nur 3 s. */
    fun cooldownMs(): Long = (rerouteCooldownMs shl rerouteStreak).coerceAtMost(15_000L)

    /** Zähler, wie viele Positionen in Folge neben der Route lagen (für das Fahrtprotokoll). */
    val offFixes: Int get() = offCount

    /** Neuberechnung ist gescheitert: später nochmal probieren (mit wachsender Wartezeit). */
    fun rerouteFailed(nowMs: Long) {
        rerouteStreak = if (nowMs - lastRerouteMs < 90_000L) (rerouteStreak + 1).coerceAtMost(4) else 0
        lastRerouteMs = nowMs
    }

    /** Wegpunkte, die ab der aktuellen Position noch vor uns liegen (für die Neuberechnung). */
    fun remainingWaypoints(lat: Double, lon: Double): List<Waypoint> =
        Replan.remaining(route, wps, progress.toInt(), lat, lon)

    /**
     * @param accuracyM Genauigkeit des GPS-Fixes in Metern (null = unbekannt). Ungenaue Fixes lassen die Schwelle für
     * "daneben" wachsen; ab 100 m Ungenauigkeit zählt ein Fix weder als daneben noch als darauf.
     */
    fun update(lat: Double, lon: Double, accuracyM: Float? = null): State {
        val pts = route.points
        val cum = route.cumM
        if (pts.size < 2) return State(0, 0, 0, 0, null, 0, false, true)

        // Nur in der Nähe der letzten Stelle suchen: ein Stück zurück, nach vorn bis ~2 km
        val from = (seg - 5).coerceAtLeast(0)
        var to = seg
        while (to < pts.size - 2 && cum[to] - cum[seg] < 2000) to++
        to = (to + 5).coerceAtMost(pts.size - 2)
        var best = Double.MAX_VALUE
        var bestSeg = seg
        var bestF = 0.0
        for (i in from..to) {
            val d = distToSegment(lat, lon, pts[i], pts[i + 1])
            if (d.first < best) { best = d.first; bestSeg = i; bestF = d.second }
        }
        // Weit weg? Dann einmal über die ganze Route suchen (z. B. wenn man woanders wieder auf die Route trifft)
        if (best > offRouteM * 3) {
            for (i in 0 until pts.size - 1) {
                val d = distToSegment(lat, lon, pts[i], pts[i + 1])
                if (d.first < best - 5) { best = d.first; bestSeg = i; bestF = d.second }
            }
        }
        lateral = best
        val unreliable = accuracyM != null && accuracyM > 100f
        val thr = maxOf(offRouteM, (accuracyM ?: 0f) * 1.5)
        val off = !unreliable && best > thr
        if (unreliable) {
            // nichts ändern: weder Zähler noch Fortschritt
        } else if (off) {
            offCount++
        } else {
            offCount = 0
            seg = bestSeg
            val p = cum[bestSeg] + bestF * (cum[bestSeg + 1] - cum[bestSeg])
            // Rückwärts nur wenig zulassen (GPS-Zittern), sonst weiter vorn bleiben
            progress = if (p >= progress - 60) p else progress
        }
        val total = route.totalM
        val rem = (total - progress).toInt().coerceAtLeast(0)
        if (rem <= 25 && !off && !unreliable) finishedFlag = true
        val remSec = if (total <= 0) 0 else (route.seconds.toDouble() * rem / total).toInt()
        val next = route.instructions.firstOrNull { it.atM >= progress.toInt() - 10 }
        val nextDist = if (next == null) 0 else (next.atM - progress.toInt()).coerceAtLeast(0)
        return State(progress.toInt(), best.toInt(), rem, remSec, next, nextDist, off, finishedFlag, (progress + doneBefore).toInt())
    }

    /** Abstand (m) von Punkt zur Strecke a-b und Anteil f (0..1) des Fußpunkts. */
    private fun distToSegment(lat: Double, lon: Double, a: RoutePoint, b: RoutePoint): Pair<Double, Double> {
        val ax = 0.0
        val ay = 0.0
        val bx = (b.lon - a.lon) * kotlin.math.cos(Math.toRadians((a.lat + b.lat) / 2)) * 111_320.0
        val by = (b.lat - a.lat) * 111_320.0
        val px = (lon - a.lon) * kotlin.math.cos(Math.toRadians((a.lat + b.lat) / 2)) * 111_320.0
        val py = (lat - a.lat) * 111_320.0
        val len2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
        val f = if (len2 <= 0.0) 0.0 else (((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / len2).coerceIn(0.0, 1.0)
        val cx = ax + f * (bx - ax)
        val cy = ay + f * (by - ay)
        val dx = px - cx
        val dy = py - cy
        return Pair(kotlin.math.sqrt(dx * dx + dy * dy), f)
    }

}
