package io.github.th3s1nc.osmandhudbridge.pilot

/** Zweite Zeile der Tourenliste: "226 km | 3 h 12 min" (leer, wenn der Server noch nichts gerechnet hat). */
object TourText {
    fun kmMin(km: Double?, min: Int?): String? {
        if (km == null || min == null || km.isNaN() || km < 0 || min < 0) return null
        val k = if (km >= 100) Math.round(km).toString() else String.format(java.util.Locale.ROOT, "%.1f", km)
        return "$k km | ${min / 60} h ${String.format(java.util.Locale.ROOT, "%02d", min % 60)} min"
    }
}
