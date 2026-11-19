package io.github.th3s1nc.osmandhudbridge.track

/**
 * Werte einer Fahrt für die Diagramme: je Punkt Tempo, Höhe, Schräglage, Beschleunigung. Fehlende Werte sind NaN.
 * Die Beschleunigung (m/s²) wird aus dem Tempo errechnet (Unterschied zum Nachbarpunkt, leicht geglättet);
 * über Lücken (Autopause) hinweg gibt es keine.
 */
class RideSeries(
    val speedKmh: FloatArray,
    val ele: FloatArray,
    val lean: FloatArray,
    val accel: FloatArray,
    val limit: FloatArray,
    val maxAccel: Float,
    val maxBrake: Float
) {
    val size: Int get() = speedKmh.size
}

object RideAnalysis {
    /** Lücken ab dieser Dauer zwischen zwei Punkten gelten als Pause. */
    const val GAP_MS = 5_000L

    fun series(points: List<TrackPoint>): RideSeries {
        val n = points.size
        val sp = FloatArray(n) { points[it].speedKmh }
        val ele = FloatArray(n) { points[it].ele?.toFloat() ?: Float.NaN }
        val lean = smoothLean(FloatArray(n) { points[it].leanDeg ?: Float.NaN })
        val limit = FloatArray(n) { if (points[it].limitKmh > 0) points[it].limitKmh.toFloat() else Float.NaN }
        val raw = FloatArray(n)
        for (i in 1 until n - 1) {
            val dt = points[i + 1].timeMs - points[i - 1].timeMs
            if (dt <= 0 || points[i].timeMs - points[i - 1].timeMs > GAP_MS || points[i + 1].timeMs - points[i].timeMs > GAP_MS) continue
            raw[i] = (sp[i + 1] - sp[i - 1]) / 3.6f / (dt / 1000f)
        }
        // leicht glätten (3 Punkte), sonst zappelt das GPS-Tempo
        val acc = FloatArray(n)
        for (i in 0 until n) {
            val a = raw.getOrElse(i - 1) { raw[i] }
            val b = raw.getOrElse(i + 1) { raw[i] }
            acc[i] = (a + raw[i] + b) / 3f
        }
        var mx = 0f
        var mn = 0f
        for (v in acc) { if (v > mx) mx = v; if (v < mn) mn = v }
        return RideSeries(sp, ele, lean, acc, limit, mx, mn)
    }

    /**
     * Schräglage glätten: Median über 5 Punkte (2 vor, 2 nach dem Punkt). Ein einzelner Ruck (1 bis 2 Punkte) verschwindet,
     * eine echte Kurve (mehrere Sekunden) bleibt. Lücken (NaN) zählen nicht mit; ein Punkt ohne Wert bleibt ohne Wert.
     */
    fun smoothLean(a: FloatArray): FloatArray {
        val n = a.size
        val out = FloatArray(n) { Float.NaN }
        val w = ArrayList<Float>(5)
        for (i in 0 until n) {
            if (a[i].isNaN()) continue
            // symmetrisches Fenster (am Rand kleiner); ganz am Rand die 3 nächsten Punkte
            val r = minOf(2, i, n - 1 - i)
            var lo = i - r
            var hi = i + r
            if (r == 0 && n >= 3) { lo = if (i == 0) 0 else n - 3; hi = lo + 2 }
            w.clear()
            for (j in lo..hi) if (!a[j].isNaN()) w += Math.abs(a[j])
            if (w.size < 3) { out[i] = a[i]; continue }
            w.sort()
            val m = if (w.size % 2 == 1) w[w.size / 2] else (w[w.size / 2 - 1] + w[w.size / 2]) / 2f
            out[i] = Math.copySign(m, a[i])
        }
        return out
    }

    /** Größte Schräglage (Betrag) nach der Glättung; 0 ohne Werte. */
    fun maxLeanSmoothed(a: FloatArray): Float {
        var m = 0f
        for (v in smoothLean(a)) if (!v.isNaN() && Math.abs(v) > m) m = Math.abs(v)
        return m
    }

    /** Hat die Reihe mindestens einen Wert (nicht NaN)? */
    fun hasData(a: FloatArray): Boolean = a.any { !it.isNaN() }
}
