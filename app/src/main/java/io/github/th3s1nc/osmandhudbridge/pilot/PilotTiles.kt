package io.github.th3s1nc.osmandhudbridge.pilot

import kotlin.math.floor

/**
 * Routendaten von BRouter liegen in Kacheln von 5 x 5 Grad, benannt nach der unteren linken Ecke,
 * z. B. E10_N45 (Länge 10 bis 15 Grad Ost, Breite 45 bis 50 Grad Nord).
 */
object PilotTiles {
    fun name(lat: Double, lon: Double): String {
        val lo = floor(lon / 5.0).toInt() * 5
        val la = floor(lat / 5.0).toInt() * 5
        val slon = if (lo < 0) "W${-lo}" else "E$lo"
        val slat = if (la < 0) "S${-la}" else "N$la"
        return "${slon}_$slat"
    }

    /** Alle Kacheln, die eine Folge von Punkten berührt (für "diese Route braucht ..."). Reihenfolge stabil. */
    fun along(points: List<Pair<Double, Double>>): List<String> {
        val out = LinkedHashSet<String>()
        for (p in points) out.add(name(p.first, p.second))
        return out.toList()
    }

    /** Alle Kacheln, die ein Rechteck (Breite/Länge in Grad) berührt. Ein Rand genau auf der Kachelgrenze zählt nicht zur nächsten. */
    fun forBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<String> {
        val out = ArrayList<String>()
        val eps = 1e-6
        var la = floor(minLat / 5.0).toInt() * 5
        while (la <= maxLat - eps) {
            var lo = floor(minLon / 5.0).toInt() * 5
            while (lo <= maxLon - eps) {
                out.add(name(la + 0.5, lo + 0.5))
                lo += 5
            }
            la += 5
        }
        return out
    }

    const val BASE_URL = "https://brouter.de/brouter/segments4/"
    const val LOOKUPS_URL = "https://brouter.de/brouter/profiles2/lookups.dat"

    fun url(tile: String) = "$BASE_URL$tile.rd5"
}

/** Ein wählbares Gebiet = eine Kachel mit ungefährer Beschreibung. */
data class PilotArea(val tile: String, val de: String, val en: String)

object PilotAreas {
    /** Die Lage ist nur ungefähr: eine Kachel ist ein Rechteck, Landesgrenzen schneiden sie. */
    val all: List<PilotArea> = listOf(
        PilotArea("E10_N45", "Bayern Süd, Österreich West, Südtirol, Dolomiten, Venetien", "Southern Bavaria, western Austria, South Tyrol, Dolomites, Veneto"),
        PilotArea("E5_N45", "Baden-Württemberg, Schweiz, Elsass, Savoyen, Piemont", "Baden-Württemberg, Switzerland, Alsace, Savoy, Piedmont"),
        PilotArea("E15_N45", "Wien, Steiermark, Burgenland, Slowenien Ost, Kroatien Nord", "Vienna, Styria, Burgenland, eastern Slovenia, northern Croatia"),
        PilotArea("E5_N50", "Nordrhein-Westfalen, Hessen, Niedersachsen West, Benelux Ost", "North Rhine-Westphalia, Hesse, western Lower Saxony, eastern Benelux"),
        PilotArea("E10_N50", "Thüringen, Sachsen, Berlin, Brandenburg, Nordbayern, Hamburg Ost", "Thuringia, Saxony, Berlin, Brandenburg, northern Bavaria, eastern Hamburg"),
        PilotArea("E15_N50", "Ostsachsen, Tschechien Nord, Polen West", "Eastern Saxony, northern Czechia, western Poland"),
        PilotArea("E0_N45", "Frankreich Mitte und Süd-West", "Central and south-west France"),
        PilotArea("E0_N50", "Nordfrankreich, Belgien West", "Northern France, western Belgium"),
        PilotArea("E5_N40", "Ligurien, Piemont Süd, Korsika", "Liguria, southern Piedmont, Corsica"),
        PilotArea("E10_N40", "Toskana, Umbrien, Latium, Emilia-Romagna Süd", "Tuscany, Umbria, Lazio, southern Emilia-Romagna")
    )

    fun byTile(tile: String): PilotArea? = all.firstOrNull { it.tile == tile }
}
