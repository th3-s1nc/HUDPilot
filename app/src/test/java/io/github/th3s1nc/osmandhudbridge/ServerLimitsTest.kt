package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.limit.PbfImport
import io.github.th3s1nc.osmandhudbridge.limit.ServerLimits
import io.github.th3s1nc.osmandhudbridge.limit.TileCache
import io.github.th3s1nc.osmandhudbridge.limit.TileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ServerLimitsTest {
    private fun tmp(): File = File.createTempFile("srvlim", "").let { it.delete(); it.mkdirs(); it }

    private fun way(id: Long, maxspeed: String) =
        "{\"type\":\"way\",\"id\":$id,\"tags\":{\"highway\":\"residential\",\"maxspeed\":\"$maxspeed\"},\"geometry\":[{\"lat\":49.0100000,\"lon\":12.0100000},{\"lat\":49.0110000,\"lon\":12.0110000}]}"

    /** Eine Kachel im Format des Imports (wie sie der Server schickt). */
    private fun tileBytes(lines: List<String>): ByteArray {
        val sb = StringBuilder(PbfImport.MARKER).append('\n')
        for ((i, l) in lines.withIndex()) { sb.append(l); if (i < lines.size - 1) sb.append(','); sb.append('\n') }
        sb.append("]}\n")
        val bo = ByteArrayOutputStream()
        GZIPOutputStream(bo).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        return bo.toByteArray()
    }

    private fun text(f: File): String = GZIPInputStream(f.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }

    private fun zip(f: File, stored: Boolean, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, data) in entries) {
                val e = ZipEntry(name)
                if (stored) {
                    e.method = ZipEntry.STORED; e.size = data.size.toLong(); e.compressedSize = data.size.toLong()
                    val c = CRC32(); c.update(data); e.crc = c.value
                }
                z.putNextEntry(e); z.write(data); z.closeEntry()
            }
        }
    }

    @Test fun kachelNamenWerdenStreng_geprueft() {
        assertTrue(ServerLimits.isTileName("tile3_2450_400.json"))
        assertTrue(ServerLimits.isTileName("tile3_1888_-4080.json"))
        assertFalse(ServerLimits.isTileName("../tile3_1_1.json"))
        assertFalse(ServerLimits.isTileName("sub/tile3_1_1.json"))
        assertFalse(ServerLimits.isTileName("tile2_1_1.json"))
        assertFalse(ServerLimits.isTileName("readme.txt"))
    }

    @Test fun zipWirdEntpackt_gespeichert_und_gepackt_fremde_namen_ignoriert() {
        for (stored in listOf(true, false)) {
            val dir = tmp(); val z = File(dir, "r.zip"); val out = File(dir, "limits")
            zip(z, stored, listOf(
                "tile3_10_20.json" to tileBytes(listOf(way(1, "50"))),
                "tile3_10_-21.json" to tileBytes(listOf(way(2, "30"))),
                "../tile3_9_9.json" to tileBytes(listOf(way(3, "30"))),
                "notes.txt" to "x".toByteArray()
            ))
            val seen = ArrayList<Int>()
            val n = ServerLimits.unpack(z, out, 1_000_000L, progress = { seen += it })
            assertEquals(2, n)
            assertEquals(listOf("tile3_10_-21.json", "tile3_10_20.json"), out.list()!!.sorted())
            assertFalse(File(dir, "tile3_9_9.json").exists())
            assertTrue(text(File(out, "tile3_10_20.json")).contains("\"id\":1,"))
            assertTrue(PbfImport.isImportTile(File(out, "tile3_10_20.json")))
            assertEquals(2, PbfImport.countImported(out))
            assertEquals(2, seen.last())
            dir.deleteRecursively()
        }
    }

    @Test fun vorhandeneImportKachelWirdZusammengefuehrt_neueGewinnt() {
        val dir = tmp(); val f = File(dir, "tile3_10_20.json")
        PbfImport.storeTile(f, tileBytes(listOf(way(1, "50"), way(2, "50"))), 1000L)
        PbfImport.storeTile(f, tileBytes(listOf(way(2, "70"), way(3, "30"))), 2000L)
        val t = text(f)
        assertTrue(t.startsWith(PbfImport.MARKER))
        assertEquals(1, Regex("\"id\":1,").findAll(t).count())
        assertEquals(1, Regex("\"id\":2,").findAll(t).count())
        assertEquals(1, Regex("\"id\":3,").findAll(t).count())
        assertTrue(t.contains("\"id\":2,\"tags\":{\"highway\":\"residential\",\"maxspeed\":\"70\"}"))
        assertFalse(t.contains("\"maxspeed\":\"50\"}") && t.contains("\"id\":2,\"tags\":{\"highway\":\"residential\",\"maxspeed\":\"50\"}"))
        assertEquals(2000L, f.lastModified())
        // gleiche Kachel nochmal: nichts doppelt
        PbfImport.storeTile(f, tileBytes(listOf(way(2, "70"), way(3, "30"))), 3000L)
        assertEquals(1, Regex("\"id\":3,").findAll(text(f)).count())
        // ergibt gültiges JSON-Feld: Zeilen mit Komma getrennt, am Ende ]}
        assertTrue(text(f).trimEnd().endsWith("]}"))
        dir.deleteRecursively()
    }

    @Test fun alteOnlineKachelWirdErsetzt() {
        val dir = tmp(); val f = File(dir, "tile3_10_20.json")
        GZIPOutputStream(f.outputStream()).use { it.write("{\"elements\":[{\"type\":\"way\",\"id\":9}]}".toByteArray()) }
        PbfImport.storeTile(f, tileBytes(listOf(way(1, "50"))), 5L)
        val t = text(f)
        assertFalse(t.contains("\"id\":9"))
        assertTrue(PbfImport.isImportTile(f))
        dir.deleteRecursively()
    }

    @Test fun aufraeumenLoeschtKeineImportKacheln() {
        val dir = tmp()
        try {
            val cache = TileCache(dir)
            PbfImport.storeTile(File(dir, TileKey(1, 1).fileName), tileBytes(listOf(way(1, "50"))), 1_000L) // sehr alt
            for (i in 2..5) {
                cache.write(TileKey(i, i), "x".repeat(2000) + i)
                File(dir, TileKey(i, i).fileName).setLastModified(System.currentTimeMillis() - (10 - i) * 60_000L)
            }
            cache.maxBytes = 1
            cache.trimNow()
            assertNotNull(cache.read(TileKey(1, 1), System.currentTimeMillis()))
            assertTrue(PbfImport.isImportTile(File(dir, TileKey(1, 1).fileName)))
            assertEquals(1, dir.listFiles { x -> x.name.endsWith(".json") }!!.size) // Zwischenspeicher ist weg, Import bleibt
        } finally { dir.deleteRecursively() }
    }
}
