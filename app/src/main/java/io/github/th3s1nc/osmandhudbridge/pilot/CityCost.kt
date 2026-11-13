package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * "Ortsdurchfahrten meiden" mit einstellbarer Stärke. Reine Logik ohne Android, gleiche Regel wie auf dem Server (Planer):
 * ein Aufschlag auf Wohnstraßen und Straßen mit Tempo 50 oder weniger. Nur die abgeleitete Profil-Variante bekommt die Zeile.
 */
object CityCost {
    val STEPS = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
    const val DEFAULT = 1.5

    /** Auf die nächste erlaubte Stufe runden; Unsinn ergibt den Standard. */
    fun snap(x: Double?): Double {
        if (x == null || x.isNaN()) return DEFAULT
        return STEPS.minByOrNull { Math.abs(it - x) } ?: DEFAULT
    }

    /** Zahl ohne unnötige Nachkommastellen, immer mit Punkt (BRouter-Profil). */
    fun num(w: Double): String = if (w == Math.floor(w)) w.toInt().toString() else w.toString()

    fun line(w: Double): String =
        " add switch or isresidentialorliving or maxspeed=50 or maxspeed=30 or maxspeed=20 maxspeed=10 ${num(snap(w))} 0"

    /** Kurzcode für den Dateinamen: 1.5 -> "15", 4 -> "40". */
    fun code(w: Double): String = Math.round(snap(w) * 10).toString()

    /** Die Aufschlag-Zeile hinter der Zeile "add switch and avoid_unpaved isunpaved ..." einfügen (nur einmal). */
    fun patch(profileText: String, w: Double): String {
        val re = Regex("(?m)^( *add switch and avoid_unpaved isunpaved [^\\n]*)$")
        val m = re.find(profileText) ?: return profileText
        return profileText.substring(0, m.range.last + 1) + "\n" + line(w) + profileText.substring(m.range.last + 1)
    }
}
