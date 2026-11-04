package io.github.th3s1nc.osmandhudbridge.limit

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Straßendaten vom eigenen Server: eine Zip-Datei je Region mit fertigen Kacheln (gleiches Format wie der Import auf dem Handy,
 * der Server benutzt dafür denselben Code). Hier wird sie in den Kachel-Speicher einsortiert. Nur java.io, damit es ohne Android testbar ist.
 */
object ServerLimits {
    private val TILE_NAME = Regex("tile3_-?\\d+_-?\\d+\\.json")

    fun isTileName(name: String) = TILE_NAME.matches(name)

    /** Legt alle Kacheln der Zip-Datei in [outDir] (Nachbarregionen an der Kante werden zusammengeführt). Gibt die Zahl der Kacheln zurück. */
    fun unpack(zip: File, outDir: File, nowMs: Long = System.currentTimeMillis(), cancelled: () -> Boolean = { false }, progress: (Int) -> Unit = {}): Int {
        outDir.mkdirs()
        var n = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip), 1 shl 16)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val name = e.name
                if (e.isDirectory || !isTileName(name)) continue
                if (cancelled()) throw ImportCancelled()
                PbfImport.storeTile(File(outDir, name), zin.readBytes(), nowMs)
                n++
                if (n % 200 == 0) progress(n)
            }
        }
        progress(n)
        return n
    }
}
