package io.github.th3s1nc.osmandhudbridge.limit

import java.io.File
import kotlin.system.exitProcess

/**
 * Kleines Programm für den Server: nutzt genau den Import-Code der App.
 *   roads   <datei.osm.pbf> <ausgabe-ordner>     Straßendaten-Kacheln schreiben, letzte Zeile: STATS <zahlen>
 *   cameras <ausgabe.csv> <datei.gpx> [...]       SCDB-Listen (GPX) zusammenführen, letzte Zeile: CAMERAS <anzahl>
 *   unpack  <region.zip> <ordner>                 Zip einer Region wie die App einsortieren (nur zum Prüfen)
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) { System.err.println("roads | cameras"); exitProcess(2) }
    try {
        when (args[0]) {
            "roads" -> {
                if (args.size < 3) { System.err.println("roads <pbf> <ordner>"); exitProcess(2) }
                val pbf = File(args[1]); val out = File(args[2])
                val tmp = File(out.parentFile ?: File("."), out.name + ".tmp")
                var lastPct = -1
                val l = object : ImportListener {
                    override fun progress(step: Int, steps: Int, fraction: Double, text: String, tilesDone: Int) {
                        val pct = (fraction * 100).toInt()
                        if (pct != lastPct && pct % 5 == 0) { lastPct = pct; println("PROGRESS $pct $step/$steps $text") }
                    }
                    override fun cancelled() = false
                }
                val r = PbfImport.run({ pbf.inputStream() }, pbf.length(), out, tmp, l)
                println("TILES ${r.tiles} WAYS ${r.ways} POINTS ${r.points} BYTES ${r.bytes} SECONDS ${r.seconds}")
                println("STATS ${r.stats.toLine()}")
            }
            "cameras" -> {
                if (args.size < 3) { System.err.println("cameras <csv> <gpx>..."); exitProcess(2) }
                val all = ArrayList<CameraImport.Parsed>()
                for (a in args.drop(2)) all += CameraImport.parseGpx(File(a).readText(Charsets.UTF_8))
                val merged = CameraImport.merge(all)
                File(args[1]).writeText(CameraImport.toCsv(merged), Charsets.UTF_8)
                println("CAMERAS ${merged.size} PARSED ${all.size}")
            }
            "unpack" -> {
                if (args.size < 3) { System.err.println("unpack <zip> <ordner>"); exitProcess(2) }
                println("UNPACKED ${ServerLimits.unpack(File(args[1]), File(args[2]))}")
            }
            else -> { System.err.println("unbekannt: ${args[0]}"); exitProcess(2) }
        }
    } catch (e: Exception) {
        System.err.println("FEHLER " + (e.message ?: e.javaClass.simpleName)); exitProcess(1)
    }
}
