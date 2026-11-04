package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lädt Routendaten (.rd5) von brouter.de. Läuft im Hintergrund, solange die App-Prozess lebt;
 * ein abgebrochener Download beginnt beim nächsten Mal von vorn.
 */
object AreaDownloader {
    data class Remote(val size: Long, val modified: Long)

    /** Stand für die Anzeige. Wird nur im Hauptthread gelesen/geschrieben. */
    class Status(
        var tile: String = "", var done: Long = 0, var total: Long = 0,
        var queue: Int = 0, var error: String? = null, var running: Boolean = false
    )

    val status = Status()
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancel = AtomicBoolean(false)
    @Volatile private var listener: (() -> Unit)? = null

    fun setListener(l: (() -> Unit)?) { listener = l }
    private fun changed() { main.post { listener?.invoke() } }

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "HUDPilot")
        return c
    }

    /** Größe und Datum auf dem Server (null: nicht erreichbar oder nicht vorhanden). */
    fun remote(tile: String): Remote? = try {
        val c = open(PilotTiles.url(tile), "HEAD")
        try {
            if (c.responseCode == 200) Remote(c.contentLengthLong, c.lastModified) else null
        } finally { c.disconnect() }
    } catch (_: Exception) { null }

    fun cancel() { cancel.set(true) }

    /** Gebiete nacheinander laden. Fertige Dateien werden erst am Ende des Ladens umbenannt. */
    fun start(ctx: Context, tiles: List<String>) {
        if (tiles.isEmpty() || status.running) return
        val app = ctx.applicationContext
        cancel.set(false)
        status.running = true; status.error = null; status.queue = tiles.size
        changed()
        exec.execute {
            refreshLookups(app)
            var left = tiles.size
            for (t in tiles) {
                if (cancel.get()) break
                main.post { status.tile = t; status.done = 0; status.total = 0; status.queue = left; listener?.invoke() }
                val err = download(app, t)
                if (err != null) {
                    BridgeBus.log(Tx.t("Pilot: $t nicht geladen: $err", "Pilot: $t not downloaded: $err"))
                    main.post { status.error = "$t: $err"; listener?.invoke() }
                } else {
                    BridgeBus.log(Tx.t("Pilot: Routendaten $t geladen", "Pilot: route data $t downloaded"))
                }
                left--
            }
            main.post { status.running = false; status.queue = 0; status.tile = ""; listener?.invoke() }
        }
    }

    /** lookups.dat vom Server holen, damit sie zu den Daten passt; bei Fehler bleibt die mitgelieferte. */
    fun refreshLookups(ctx: Context) {
        try {
            PilotStore.ensureProfiles(ctx)
            val c = open(PilotTiles.LOOKUPS_URL, "GET")
            try {
                if (c.responseCode != 200) return
                val bytes = c.inputStream.use { it.readBytes() }
                if (bytes.size < 1000) return
                val f = File(PilotStore.profileDir(ctx), "lookups.dat")
                val tmp = File(f.path + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
            } finally { c.disconnect() }
        } catch (_: Exception) {
        }
    }

    /** null = Erfolg, sonst Fehlertext. */
    private fun download(ctx: Context, tile: String): String? {
        val target = PilotStore.tileFile(ctx, tile)
        val tmp = File(target.path + ".tmp")
        try {
            val c = open(PilotTiles.url(tile), "GET")
            try {
                if (c.responseCode == 404) return Tx.t("gibt es auf dem Server nicht", "not available on the server")
                if (c.responseCode != 200) return "HTTP ${c.responseCode}"
                val total = c.contentLengthLong
                val modified = c.lastModified
                main.post { status.total = total; listener?.invoke() }
                var done = 0L
                var lastUi = 0L
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancel.get()) { tmp.delete(); return Tx.t("abgebrochen", "cancelled") }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 400) {
                                lastUi = now
                                val d = done
                                main.post { status.done = d; listener?.invoke() }
                            }
                        }
                    }
                }
                if (total > 0 && tmp.length() != total) {
                    tmp.delete()
                    return Tx.t("Download unvollständig", "download incomplete")
                }
                if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) return Tx.t("Datei nicht speicherbar", "cannot save file") }
                if (modified > 0) target.setLastModified(modified)
                main.post { status.done = done; listener?.invoke() }
                return null
            } finally { c.disconnect() }
        } catch (e: Exception) {
            tmp.delete()
            return e.message ?: e.javaClass.simpleName
        }
    }
}
