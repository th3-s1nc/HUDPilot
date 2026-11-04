package io.github.th3s1nc.osmandhudbridge.pilot

import java.io.BufferedReader
import java.util.Locale

/** Reine Hilfsfunktionen für "Region laden" (ohne Android, damit testbar). */
object RegionLogic {
    /** "192.168.1.10", "http://nas/" … -> "http://192.168.1.10". Leer bleibt leer. */
    fun normalizeUrl(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s.trimEnd('/')
    }

    /** Adresse für unterwegs: ohne Angabe von http/https gilt https (von außen immer verschlüsselt). */
    fun normalizeAwayUrl(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s.trimEnd('/')
    }

    /** Welche Serveradresse gilt: die Heim-Adresse, wenn sie erreichbar ist, sonst die für unterwegs. Fehlt eine, gilt die andere. */
    fun chooseUrl(home: String, away: String, homeReachable: Boolean): String = when {
        away.isEmpty() -> home
        home.isEmpty() -> away
        homeReachable -> home
        else -> away
    }

    /** "rd5/E5_N45.rd5" -> "E5_N45" */
    fun tileName(datei: String): String = datei.substringAfterLast('/').removeSuffix(".rd5")

    /** Server-Stand (Sekunden) neuer als die lokale Datei (Millisekunden)? 2 s Toleranz. */
    fun needsUpdate(localMs: Long, remoteSec: Long): Boolean = localMs <= 0L || remoteSec * 1000L > localMs + 2000L

    /** Tempo als Text: 12.4 MB/s, 850 kB/s. 0 oder weniger: leer. */
    fun fmtSpeed(bps: Long): String = when {
        bps >= 1_000_000L -> String.format(Locale.US, "%.1f MB/s", bps / 1e6)
        bps >= 1_000L -> String.format(Locale.US, "%d kB/s", bps / 1000)
        bps > 0 -> "$bps B/s"
        else -> ""
    }

    /** Geglättetes Tempo: halb alter, halb neuer Wert (der erste Wert gilt sofort). */
    fun smoothSpeed(old: Long, cur: Long): Long = if (old <= 0) cur else (old + cur) / 2

    fun fmtBytes(b: Long): String = when {
        b >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", b / 1e9)
        b >= 1_000_000L -> String.format(Locale.US, "%.0f MB", b / 1e6)
        b > 0 -> String.format(Locale.US, "%.0f kB", b / 1e3)
        else -> "–"
    }

    /** Ein Land mit seinen Regionen (für die Liste in "Regionen laden"). */
    class LandGroup<T>(val code: String, val title: String, val items: List<T>)

    /** Anzeigename eines Landes: der ausgeschriebene Name vom Server, sonst das Kürzel, sonst "?". */
    fun landTitle(code: String, name: String): String = name.ifBlank { code.ifBlank { "?" } }

    private val collator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.GERMAN).apply { strength = java.text.Collator.PRIMARY }

    /** Nach Ländern gruppiert: Deutschland zuerst, dann alphabetisch nach Landesname; die Regionen eines Landes alphabetisch. */
    fun <T> groupLands(items: List<T>, code: (T) -> String, title: (T) -> String, name: (T) -> String): List<LandGroup<T>> {
        val m = LinkedHashMap<String, MutableList<T>>()
        for (i in items) m.getOrPut(code(i)) { ArrayList() }.add(i)
        return m.map { (c, l) -> LandGroup(c, title(l[0]), l.sortedWith { a, b -> collator.compare(name(a), name(b)) }) }
            .sortedWith { a, b -> if (a.code == "DE" && b.code != "DE") -1 else if (b.code == "DE" && a.code != "DE") 1 else collator.compare(a.title, b.title) }
    }

    /** Suchtext: trifft, wenn jedes Wort im Regions- oder Landesnamen vorkommt (Groß/Klein und Akzente egal). Leer trifft alles. */
    fun matches(query: String, regionName: String, landName: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = fold("$regionName $landName")
        return words.all { hay.contains(fold(it)) }
    }
    private fun fold(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Gruppiert nach Land, Reihenfolge der ersten Nennung. */
    fun <T> byLand(items: List<T>, land: (T) -> String): List<Pair<String, List<T>>> {
        val m = LinkedHashMap<String, MutableList<T>>()
        for (i in items) m.getOrPut(land(i)) { ArrayList() }.add(i)
        return m.map { it.key to it.value }
    }

    /** Höchstens [max] gleichmäßig verteilte Punkte entlang der gefahrenen Strecke (für "Nachfahren"). */
    fun samplePoints(pts: List<Pair<Double, Double>>, max: Int): List<Pair<Double, Double>> {
        if (pts.size <= max) return pts
        val cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + dist(pts[i - 1], pts[i])
        val total = cum.last(); if (total <= 0) return listOf(pts.first(), pts.last())
        val out = ArrayList<Pair<Double, Double>>(); var j = 0
        for (k in 0 until max) {
            val target = total * k / (max - 1)
            while (j < pts.size - 1 && cum[j] < target) j++
            out += pts[j]
        }
        return out.fold(ArrayList<Pair<Double, Double>>()) { l, p -> if (l.isEmpty() || l.last() != p) l.add(p); l }
    }

    private fun dist(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = Math.PI / 180; val dl = (b.first - a.first) * r; val dn = (b.second - a.second) * r
        val x = Math.sin(dl / 2).let { it * it } + Math.cos(a.first * r) * Math.cos(b.first * r) * Math.sin(dn / 2).let { it * it }
        return 2 * 6371000 * Math.asin(Math.sqrt(x))
    }

    /** Range-Header für Fortsetzen: ab [have] Bytes. */
    fun rangeHeader(have: Long): String? = if (have > 0) "bytes=$have-" else null
}

/** Eintrag der POI-Dateien vom Server (Zeilen: art \t name \t lat \t lon \t strasse \t ort). */
object PoiIndex {
    class Poi(val kind: String, val name: String, val lat: Double, val lon: Double, val street: String, val city: String)

    fun parseLine(line: String): Poi? {
        if (line.isEmpty() || line[0] == '#') return null
        val p = line.split('\t')
        if (p.size < 4) return null
        val lat = p[2].toDoubleOrNull() ?: return null
        val lon = p[3].toDoubleOrNull() ?: return null
        return Poi(p[0], p[1], lat, lon, p.getOrElse(4) { "" }, p.getOrElse(5) { "" })
    }

    /** Alle Orte der gewünschten Arten im Ausschnitt. */
    fun search(r: BufferedReader, kinds: Set<String>, south: Double, west: Double, north: Double, east: Double): List<Poi> {
        val out = ArrayList<Poi>()
        while (true) {
            val line = r.readLine() ?: break
            // schnelle Vorprüfung der Art, bevor die Zeile zerlegt wird
            val tab = line.indexOf('\t')
            if (tab <= 0 || line.substring(0, tab) !in kinds) continue
            val p = parseLine(line) ?: continue
            if (p.lat < south || p.lat > north || p.lon < west || p.lon > east) continue
            out += p
        }
        return out
    }

    /** Doppelte (gleicher Name, fast gleicher Ort) entfernen. */
    fun dedupe(l: List<Poi>): List<Poi> {
        val seen = HashSet<String>()
        return l.filter { seen.add(it.name + "|" + String.format(Locale.US, "%.4f,%.4f", it.lat, it.lon)) }
    }
}
