package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.io.File

/** Wählbares Profil (Fahrstil) für BRouter. */
data class PilotProfile(val id: String, val de: String, val en: String) {
    fun label(): String = Tx.t(de, en)
}

/** Ordner und Dateien von BRouter in HUDPilot (alles im privaten App-Ordner). */
object PilotStore {
    val profiles = listOf(
        PilotProfile("motorrad_landstrasse", "Landstraße (Motorrad)", "Country roads (motorcycle)"),
        PilotProfile("motorrad_kurvig", "Kurvig (Motorrad)", "Twisty (motorcycle)"),
        PilotProfile("motorrad_schnell", "Schnell (Motorrad)", "Fast (motorcycle)"),
        PilotProfile("motorrad_schnellste", "Schnellste (mit Autobahn)", "Fastest (with motorway)"),
        PilotProfile("auto", "Auto", "Car")
    )

    fun baseDir(ctx: Context) = File(ctx.filesDir, "brouter")
    fun segmentDir(ctx: Context) = File(baseDir(ctx), "segments4").also { it.mkdirs() }
    fun profileDir(ctx: Context) = File(baseDir(ctx), "profiles2").also { it.mkdirs() }
    fun tileFile(ctx: Context, tile: String) = File(segmentDir(ctx), "$tile.rd5")
    fun hasTile(ctx: Context, tile: String): Boolean = tileFile(ctx, tile).let { it.isFile && it.length() > 0 }
    fun profilePath(ctx: Context, id: String): String = File(profileDir(ctx), "$id.brf").path

    /**
     * Profile aus den Assets in den App-Ordner kopieren. Die .brf-Dateien gehören zur App und werden immer erneuert.
     * lookups.dat nur, wenn sie fehlt: sie wird beim Laden der Gebiete vom Server aktualisiert und muss zu den Daten passen.
     */
    fun ensureProfiles(ctx: Context) {
        val dir = profileDir(ctx)
        val names = ctx.assets.list("brouter/profiles2") ?: return
        var changed = false
        for (n in names) {
            val target = File(dir, n)
            if (n == "lookups.dat" && target.isFile && target.length() > 0) continue
            val bytes = ctx.assets.open("brouter/profiles2/$n").use { it.readBytes() }
            if (target.isFile && target.length() == bytes.size.toLong() && target.readBytes().contentEquals(bytes)) continue
            target.outputStream().use { it.write(bytes) }
            if (n.endsWith(".brf")) changed = true
        }
        // Profil aus der App erneuert: abgeleitete Vermeiden-Varianten sind veraltet
        if (changed) dir.listFiles { f -> f.name.contains("__") && f.name.endsWith(".brf") }?.forEach { it.delete() }
    }

    /**
     * Profildatei für die aktuellen Vermeiden-Schalter: die Werte `assign avoid_x = 0` werden eingesetzt und als
     * eigene Datei `<profil>__<code>.brf` abgelegt (BRouter merkt sich Profile je Dateiname). Ohne Schalter: die Originaldatei.
     */
    fun variantPath(ctx: Context, id: String, avoid: Avoid.Set): String {
        val base = File(profileDir(ctx), "$id.brf")
        if (!base.isFile || avoid.none()) return base.path
        val out = File(profileDir(ctx), "${id}__${avoid.code()}.brf")
        if (!out.isFile || out.lastModified() < base.lastModified()) {
            var txt = base.readText()
            for ((k, v) in avoid.values()) txt = txt.replace(Regex("(?m)^assign avoid_$k\\s*=\\s*\\d.*$"), "assign avoid_$k = $v")
            if (avoid.has(Avoid.CITY)) txt = CityCost.patch(txt, avoid.cityw)   // Ortsdurchfahrten: Aufschlag nur in der Variante
            out.writeText(txt)
        }
        return out.path
    }

    /** Dateigröße lesbar (MB/GB). */
    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(Tx.loc(), "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000L -> String.format(Tx.loc(), "%.0f MB", bytes / 1e6)
        else -> String.format(Tx.loc(), "%.0f KB", bytes / 1e3)
    }
}
