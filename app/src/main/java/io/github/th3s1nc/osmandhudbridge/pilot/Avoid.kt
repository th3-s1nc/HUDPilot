package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.limit.Tx

/** "Vermeiden": Autobahn, Fähren, Maut, unbefestigte Wege, Ortsdurchfahrten. Gilt für alle Profile und jede Berechnung. */
object Avoid {
    const val MOTORWAY = "motorway"
    const val FERRY = "ferry"
    const val TOLL = "toll"
    const val UNPAVED = "unpaved"
    const val CITY = "city"
    val KEYS = listOf(MOTORWAY, FERRY, TOLL, UNPAVED, CITY)

    class Set(val on: kotlin.collections.Set<String>, cityw: Double = CityCost.DEFAULT) {
        /** Stärke der Ortsmeidung (nur wirksam mit dem Schalter "city"). */
        val cityw: Double = CityCost.snap(cityw)
        fun none() = on.isEmpty()
        fun has(k: String) = k in on
        /** Name der Profil-Variable und Wert (0/1), in fester Reihenfolge. */
        fun values(): List<Pair<String, Int>> = KEYS.map { it to (if (it in on) 1 else 0) }
        /** Kurzcode für den Dateinamen, z. B. m1f0t0u1. */
        fun code(): String = KEYS.joinToString("") { it.substring(0, 1) + (if (it !in on) "0" else if (it == CITY) CityCost.code(cityw) else "1") }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_ui", Context.MODE_PRIVATE)
    private fun key(k: String) = "avoid_$k"

    fun get(ctx: Context): Set = Set(KEYS.filter { prefs(ctx).getBoolean(key(it), false) }.toSet(), cityStrength(ctx))

    fun cityStrength(ctx: Context): Double = CityCost.snap(prefs(ctx).getFloat("avoid_city_w", CityCost.DEFAULT.toFloat()).toDouble())
    fun setCityStrength(ctx: Context, w: Double) { prefs(ctx).edit().putFloat("avoid_city_w", CityCost.snap(w).toFloat()).apply() }

    fun set(ctx: Context, k: String, on: Boolean) {
        prefs(ctx).edit().putBoolean(key(k), on).apply()
    }

    fun label(k: String): String = when (k) {
        MOTORWAY -> Tx.t("Autobahn", "Motorways")
        FERRY -> Tx.t("Fähren", "Ferries")
        TOLL -> Tx.t("Mautstraßen", "Toll roads")
        CITY -> Tx.t("Ortsdurchfahrten", "Town centres")
        else -> Tx.t("Unbefestigte Wege", "Unpaved roads")
    }

    /** Kurzer Text für die Menüzeile, z. B. "Autobahn, Fähren" oder "nichts". */
    fun summary(ctx: Context): String {
        val s = get(ctx)
        return if (s.none()) Tx.t("nichts", "nothing") else KEYS.filter { s.has(it) }.joinToString(", ") { label(it) }
    }
}
