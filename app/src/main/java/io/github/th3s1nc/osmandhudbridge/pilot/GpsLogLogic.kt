package io.github.th3s1nc.osmandhudbridge.pilot

import java.util.Locale

/** Zeilen und Aufräumen für das GPS-Log (reine Rechnung ohne Android). */
object GpsLogLogic {
    const val MAX_BYTES = 5_000_000
    const val KEEP_BYTES = 3_500_000
    const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    const val HEADER = "zeit_ms;modus;lat;lon;genau_m;tempo_kmh;gps_richtung;nutze_richtung;pfeil_lat;pfeil_lon;eingerastet;still;abstand_route_m;ereignis"

    private fun f(v: Double?, d: Int): String = if (v == null) "" else String.format(Locale.US, "%.${d}f", v)

    /** Eine Zeile. modus: "nav" (Navigation), "frei", "tunnel" (geschätzt). */
    fun row(
        timeMs: Long, mode: String, lat: Double, lon: Double, accuracyM: Float?, kmh: Float?, gpsBearing: Float?, usedHeading: Double?,
        shownLat: Double?, shownLon: Double?, snapped: Boolean, still: Boolean, offRouteM: Int?, event: String = ""
    ): String = listOf(
        timeMs.toString(), mode, f(lat, 6), f(lon, 6), f(accuracyM?.toDouble(), 0), f(kmh?.toDouble(), 1), f(gpsBearing?.toDouble(), 0),
        f(usedHeading, 0), f(shownLat, 6), f(shownLon, 6), if (snapped) "1" else "0", if (still) "1" else "0", offRouteM?.toString() ?: "",
        event.replace(';', ',').replace('\n', ' ')
    ).joinToString(";")

    /** Ereigniszeile ohne Position. */
    fun eventRow(timeMs: Long, text: String): String = row(timeMs, "ereignis", Double.NaN, Double.NaN, null, null, null, null, null, null, false, false, null, text)
        .replace("NaN", "")

    /** Kürzt zu große Texte von vorn, die Kopfzeile bleibt. */
    fun trim(text: String): String {
        if (text.length <= MAX_BYTES) return text
        val cut = text.length - KEEP_BYTES
        val nl = text.indexOf('\n', cut)
        val tail = if (nl < 0) "" else text.substring(nl + 1)
        return HEADER + "\n" + tail
    }

    /** Ist die Aufzeichnung (Beginn [sinceMs]) älter als 7 Tage? */
    fun expired(sinceMs: Long, nowMs: Long): Boolean = sinceMs > 0 && nowMs - sinceMs > MAX_AGE_MS

    fun sizeText(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.GERMANY, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.GERMANY, "%d KB", bytes / 1000)
        else -> "$bytes B"
    }
}
