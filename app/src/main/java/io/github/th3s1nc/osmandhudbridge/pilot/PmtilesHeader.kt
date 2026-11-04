package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Liest den Kopf (127 Bytes) einer .pmtiles-Datei (Version 3). Dient der Fehlersuche auf der MapLibre-Testseite:
 * ist die Datei gültig, vollständig kopiert, und wie sind Kacheln und Verzeichnisse komprimiert?
 * Reine Rechnung ohne Android.
 */
object PmtilesHeader {
    const val SIZE = 127

    data class Info(
        val version: Int,
        val rootOffset: Long, val rootLength: Long,
        val leafOffset: Long, val leafLength: Long,
        val dataOffset: Long, val dataLength: Long,
        val tileEntries: Long,
        val internalCompression: Int, val tileCompression: Int, val tileType: Int,
        val minZoom: Int, val maxZoom: Int,
        val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double
    ) {
        /** So lang muss die Datei mindestens sein (hinter den Kacheldaten bzw. Verzeichnissen). */
        fun expectedMinSize(): Long = maxOf(dataOffset + dataLength, leafOffset + leafLength, rootOffset + rootLength)
    }

    private fun u64(b: ByteArray, o: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xff)
        return v
    }

    private fun i32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    /** null, wenn das keine PMTiles-Datei (Version 3) ist. */
    fun parse(b: ByteArray): Info? {
        if (b.size < SIZE) return null
        if (String(b, 0, 7, Charsets.ISO_8859_1) != "PMTiles") return null
        val ver = b[7].toInt() and 0xff
        if (ver != 3) return null
        return Info(
            ver,
            u64(b, 8), u64(b, 16), u64(b, 40), u64(b, 48), u64(b, 56), u64(b, 64), u64(b, 80),
            b[97].toInt() and 0xff, b[98].toInt() and 0xff, b[99].toInt() and 0xff,
            b[100].toInt() and 0xff, b[101].toInt() and 0xff,
            i32(b, 102) / 1e7, i32(b, 106) / 1e7, i32(b, 110) / 1e7, i32(b, 114) / 1e7
        )
    }

    fun compressionName(c: Int): String = when (c) {
        0 -> "unbekannt"; 1 -> "keine"; 2 -> "gzip"; 3 -> "brotli"; 4 -> "zstd"; else -> "?($c)"
    }

    fun typeName(t: Int): String = when (t) {
        1 -> "Vektor (MVT)"; 2 -> "PNG"; 3 -> "JPEG"; 4 -> "WebP"; 5 -> "AVIF"; else -> "?($t)"
    }

    /** Mehrzeiliger Bericht für die Anzeige. */
    fun report(b: ByteArray, fileLen: Long): String {
        val h = parse(b) ?: return "Kein gültiger PMTiles-Kopf (erwartet: \"PMTiles\", Version 3). Datei $fileLen Bytes."
        val need = h.expectedMinSize()
        val sb = StringBuilder()
        sb.append("PMTiles v${h.version}, ${typeName(h.tileType)}\n")
        sb.append("Zoom ${h.minZoom}–${h.maxZoom}\n")
        sb.append(String.format(java.util.Locale.US, "Gebiet %.2f,%.2f bis %.2f,%.2f\n", h.minLon, h.minLat, h.maxLon, h.maxLat))
        sb.append("Kacheln: ${h.tileEntries}\n")
        sb.append("Kompression: Kacheln ${compressionName(h.tileCompression)}, Verzeichnisse ${compressionName(h.internalCompression)}\n")
        sb.append("Datei ${fileLen / 1_000_000} MB, erwartet mindestens ${need / 1_000_000} MB: ")
        sb.append(if (fileLen >= need) "vollständig" else "ZU KURZ (Kopie abgebrochen?)")
        return sb.toString()
    }
}
