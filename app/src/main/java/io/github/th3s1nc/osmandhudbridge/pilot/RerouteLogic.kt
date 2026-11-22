package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Neuberechnung nach einer Abweichung. Regeln:
 *  1. Die neue Route beginnt immer in Fahrtrichtung, nie mit einem Umdrehen (sonst fällt die Stufe durch).
 *  2. Stufen: zurück auf die alte Route (4, 10, 25 km voraus, nie hinter den nächsten Wegpunkt), sonst volle Route über
 *     alle Wegpunkte, die noch vor dir liegen (Zwischenziele, dann Ziel).
 *  3. Wird der Weg zum nächsten Zwischenziel oder Formpunkt viel länger als geplant (mehr als [SKIP_DETOUR_S]), wird er übersprungen
 *     und die Route führt zum nächsten. Das Endziel wird nie übersprungen.
 * Reine Rechnung ohne Android; der Router kommt als Funktion herein.
 */
object RerouteLogic {
    /** So weit voraus auf der alten Route wird nacheinander versucht, wieder einzusteigen (m). */
    val AHEADS = intArrayOf(4_000, 10_000, 25_000)
    /** Dauert der Weg zum nächsten Zwischenziel so viel länger als geplant (s), wird es übersprungen. */
    const val SKIP_DETOUR_S = 20 * 60

    class Result(val route: PilotRoute, val wps: List<Waypoint>, val how: String, val skipped: List<Waypoint>)

    /** Geschätzte Fahrzeit (s) von Anfang der Route bis zum Wegpunkt [j] (anteilig nach Strecke), oder null. */
    fun secondsTo(r: PilotRoute, j: Int): Double? {
        val idx = r.waypointIndex.getOrNull(j) ?: return null
        if (r.totalM <= 0 || r.cumM.isEmpty()) return null
        return r.seconds.toDouble() * r.cumM[idx.coerceIn(0, r.cumM.size - 1)] / r.totalM
    }

    /**
     * @param old die Route, von der du abgewichen bist; [progressM] dein Fortschritt darauf
     * @param wps Position, Wegpunkte, die noch vor dir liegen, und das Ziel (wie [Replan.remaining])
     * @param baseS je Wegpunkt in [wps] (gleicher Index) die ursprünglich geplante Fahrzeit (s) bis dorthin, oder null
     * @param heading Fahrtrichtung in Grad, null wenn unbekannt oder zu langsam
     * @param compute der Router: Wegpunkte rein, Route raus (null = gescheitert)
     * @param log Protokollzeile (Deutsch, Englisch)
     * @return die Route mit den (ggf. gekürzten) Wegpunkten oder null, wenn es keine Route ohne Umdrehen gibt (dann weiterfahren)
     */
    fun plan(
        old: PilotRoute, progressM: Int, lat: Double, lon: Double, heading: Int?,
        wps: List<Waypoint>, baseS: List<Double?>,
        compute: (List<Waypoint>) -> PilotRoute?,
        log: (String, String) -> Unit
    ): Result? {
        fun against(r: PilotRoute) = heading != null && RouteDirection.startsAgainst(r, heading)

        fun stages(w: List<Waypoint>, allowSplice: Boolean): Pair<PilotRoute, String>? {
            if (allowSplice) {
                val tried = HashSet<Int>()
                for (ahead in AHEADS) {
                    val t = RouteSplice.target(old, progressM, ahead) ?: continue
                    if (!tried.add(t.index)) continue
                    val tp = old.points[t.index]
                    val so = compute(listOf(Waypoint(lat, lon, false, "Position"), Waypoint(tp.lat, tp.lon, false, "Wiedereinstieg"))) ?: continue
                    if (!RouteSplice.acceptable(so, lat, lon, tp.lat, tp.lon)) {
                        log("Wiedereinstieg ${ahead / 1000} km voraus verworfen: zu großer Umweg", "Rejoin ${ahead / 1000} km ahead dropped: too long a detour")
                        continue
                    }
                    if (against(so)) {
                        log("Wiedereinstieg ${ahead / 1000} km voraus verworfen: würde dich umdrehen lassen", "Rejoin ${ahead / 1000} km ahead dropped: would send you back")
                        continue
                    }
                    val joined = RouteSplice.splice(old, t, so, progressM)
                    if (joined == null || joined.waypointIndex.size != w.size) continue
                    return Pair(joined, "Wiedereinstieg ${ahead / 1000} km voraus")
                }
            }
            val full = compute(w) ?: return null
            if (against(full)) {
                log("Route zum nächsten Ziel verworfen: würde dich umdrehen lassen", "Route to the next target dropped: would send you back")
                return null
            }
            return Pair(full, "volle Route")
        }

        var w = wps
        var base = baseS
        val skipped = ArrayList<Waypoint>()
        var res = stages(w, true) ?: return null
        // Zwischenziel (oder Formpunkt) viel zu weit weg geworden? Dann überspringen, nie das Endziel.
        while (w.size > 2) {
            val b = base.getOrNull(1)
            val tNew = secondsTo(res.first, 1)
            if (b == null || tNew == null || tNew - b <= SKIP_DETOUR_S) break
            val mins = ((tNew - b) / 60.0).toInt()
            log("Wegpunkt \"${w[1].name.ifEmpty { "Zwischenziel" }}\" übersprungen: Weg dorthin $mins min länger als geplant",
                "Waypoint \"${w[1].name.ifEmpty { "waypoint" }}\" skipped: the way there is $mins min longer than planned")
            skipped.add(w[1])
            w = w.filterIndexed { i, _ -> i != 1 }
            base = base.filterIndexed { i, _ -> i != 1 }
            res = stages(w, false) ?: return null
        }
        return Result(res.first, w, res.second, skipped)
    }
}
