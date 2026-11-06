package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Schneller Wiedereinstieg nach einer Abweichung: Statt bis zum Ziel neu zu rechnen, wird nur ein kurzes Stück von der aktuellen
 * Position zurück auf die alte Route berechnet. Das neue Stück und der Rest der alten Route werden zusammengesetzt.
 * Reine Rechnung ohne Android.
 */
object RouteSplice {
    /** So weit voraus auf der alten Route wird wieder eingestiegen (m). */
    const val AHEAD_M = 4_000
    /** Bleibt weniger Strecke, lohnt sich das nicht (die volle Berechnung ist dann ohnehin schnell). */
    const val MIN_REMAINING_M = 9_000

    class Target(val index: Int, val atM: Int)

    /** Einstiegspunkt auf der alten Route oder null, wenn die normale Neuberechnung besser ist. */
    fun target(route: PilotRoute, progressM: Int): Target? {
        val n = route.points.size
        if (n < 3 || route.totalM - progressM < MIN_REMAINING_M || route.waypointIndex.isEmpty()) return null
        var want = progressM + AHEAD_M
        // Nicht über den nächsten Wegpunkt hinaus: der muss weiter angefahren werden
        for (k in 1 until route.waypointIndex.size) {
            val c = route.cumM[route.waypointIndex[k].coerceIn(0, n - 1)]
            if (c > progressM) { if (c < want) want = c; break }
        }
        var idx = 0
        while (idx < n - 1 && route.cumM[idx] < want) idx++
        if (idx >= n - 1) return null
        return Target(idx, route.cumM[idx])
    }

    /** Ist das kurze Stück brauchbar? Ein riesiger Umweg zum Einstiegspunkt ist es nicht (dann lieber voll neu rechnen). */
    fun acceptable(short: PilotRoute, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Boolean {
        val straight = Geo.distance(fromLat, fromLon, toLat, toLon)
        return short.points.size >= 2 && short.totalM <= straight * 2.5 + 1200.0
    }

    /**
     * Setzt [short] (Position bis Einstiegspunkt) und den Rest von [old] ab [t] zusammen. [progressM] = bisheriger Fortschritt auf
     * der alten Route (bestimmt, welche Wegpunkte noch vor dir liegen, wie bei [Replan.remaining]). Gibt null zurück, wenn es nicht
     * sauber geht.
     */
    fun splice(old: PilotRoute, t: Target, short: PilotRoute, progressM: Int): PilotRoute? {
        val n = old.points.size
        if (short.points.size < 2 || t.index >= n - 1 || old.waypointIndex.isEmpty()) return null
        val n1 = short.points.size
        val points = ArrayList<RoutePoint>(n1 + n - t.index)
        points.addAll(short.points)
        for (k in t.index + 1 until n) points.add(old.points[k])
        val cum = IntArray(points.size)
        for (i in 0 until n1) cum[i] = short.cumM[i]
        val base = old.cumM[t.index]
        for (k in t.index + 1 until n) cum[n1 + (k - t.index - 1)] = old.cumM[k] - base + short.totalM
        val list = ArrayList<Instruction>()
        for (ins in short.instructions) if (ins.brCmd != BrouterCmd.END && ins.brCmd != BrouterCmd.VIA) list.add(ins)
        for (ins in old.instructions) {
            if (ins.pointIndex < t.index) continue
            val ni = if (ins.pointIndex == t.index) n1 - 1 else n1 + (ins.pointIndex - t.index - 1)
            list.add(ins.copy(pointIndex = ni, atM = cum[ni]))
        }
        val wp = ArrayList<Int>()
        wp.add(0)
        for (w in 1 until old.waypointIndex.size) {
            val last = w == old.waypointIndex.size - 1
            val idx = old.waypointIndex[w].coerceIn(0, n - 1)
            if (last || old.cumM[idx] > progressM) {
                if (idx < t.index) return null   // so ein Wegpunkt läge vor dem Einstieg: nicht zusammensetzen
                wp.add(if (idx == t.index) n1 - 1 else n1 + (idx - t.index - 1))
            }
        }
        val rest = if (old.totalM > 0) (old.totalM - base).toDouble() / old.totalM else 0.0
        val sorted = list.sortedWith(compareBy({ it.atM }, { if (it.brCmd == BrouterCmd.END) 1 else 0 }))
        return PilotRoute(
            points, cum, HintMerge.simplify(sorted),
            short.seconds + (old.seconds * rest).toInt(), short.ascendM + (old.ascendM * rest).toInt(), wp.toIntArray()
        )
    }
}
