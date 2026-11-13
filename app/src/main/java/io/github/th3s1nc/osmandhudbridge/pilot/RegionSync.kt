package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.BridgeService
import io.github.th3s1nc.osmandhudbridge.limit.CameraImport
import io.github.th3s1nc.osmandhudbridge.limit.CameraStore
import io.github.th3s1nc.osmandhudbridge.limit.ImportLog
import io.github.th3s1nc.osmandhudbridge.limit.ImportStats
import io.github.th3s1nc.osmandhudbridge.limit.ServerLimits
import io.github.th3s1nc.osmandhudbridge.limit.TileStore
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** "Region laden": Karte, Routendaten, POI-Datei und Straßendaten (Tempolimits, Blitzer …) einer Region vom eigenen Kartenserver holen. */
object RegionSync {
    class Info(val id: String, val name: String, val land: String, val ready: Boolean, val landName: String = "")
    /** [extra]: Zusatzangabe des Servers (bei Straßendaten die Zahlen aus der Datei, bei der Blitzerliste die Anzahl). */
    class FileRef(val path: String, val bytes: Long, val stand: Long, val extra: String = "")
    class Full(val info: Info, val map: FileRef?, val routes: List<FileRef>, val pois: FileRef?, val roads: FileRef? = null)
    class Manifest(val all: List<Info>, val full: Map<String, Full>, val cameras: FileRef? = null)

    class Status(var text: String = "", var done: Long = 0, var total: Long = 0, var running: Boolean = false, var error: String? = null, var speed: Long = 0)

    val status = Status()
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancel = AtomicBoolean(false)
    @Volatile private var listener: (() -> Unit)? = null
    fun setListener(l: (() -> Unit)?) { listener = l }
    private fun changed() { main.post { listener?.invoke() } }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)
    /** Adresse im Heimnetz (zum Beispiel http://192.168.1.10). */
    fun homeUrl(ctx: Context): String = prefs(ctx).getString("url", "") ?: ""
    /** Adresse von unterwegs (über den Nginx Proxy Manager, https). Leer = nur Heimnetz. */
    fun awayUrl(ctx: Context): String = prefs(ctx).getString("url_away", "") ?: ""
    fun setServerUrl(ctx: Context, v: String) { prefs(ctx).edit().putString("url", RegionLogic.normalizeUrl(v)).apply(); routeAt = 0 }
    fun setAwayUrl(ctx: Context, v: String) { prefs(ctx).edit().putString("url_away", RegionLogic.normalizeAwayUrl(v)).apply(); routeAt = 0 }

    @Volatile private var routeAt = 0L
    @Volatile private var routeHomeOk = true
    private val probing = AtomicBoolean(false)
    private val probeExec = Executors.newSingleThreadExecutor()
    private const val ROUTE_TTL_MS = 60_000L

    /** Ist das Heimnetz gerade erreichbar? Kurze Probe (1,5 s), das Ergebnis gilt eine Minute. */
    private fun probe(home: String) {
        val ok = try {
            val c = URL("$home/logo.png").openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"; c.connectTimeout = 1_500; c.readTimeout = 1_500
            try { c.responseCode in 200..499 } finally { c.disconnect() }
        } catch (_: Exception) { false }
        routeHomeOk = ok; routeAt = SystemClock.elapsedRealtime(); probing.set(false)
    }

    /** Die Adresse, die jetzt gilt (Heimnetz oder unterwegs). Im Hintergrund wird bei Bedarf kurz geprüft, auf dem Hauptthread nie gewartet. */
    fun serverUrl(ctx: Context): String {
        val home = homeUrl(ctx); val away = awayUrl(ctx)
        if (away.isEmpty() || home.isEmpty()) return RegionLogic.chooseUrl(home, away, true)
        if (SystemClock.elapsedRealtime() - routeAt > ROUTE_TTL_MS) {
            if (Looper.myLooper() == Looper.getMainLooper()) { if (probing.compareAndSet(false, true)) probeExec.execute { probe(home) } }
            else { probing.set(true); probe(home) }
        }
        return RegionLogic.chooseUrl(home, away, routeHomeOk)
    }

    /** True, wenn gerade die Adresse für unterwegs benutzt wird. */
    fun isAway(ctx: Context): Boolean { val u = serverUrl(ctx); return u.isNotEmpty() && u == awayUrl(ctx) && u != homeUrl(ctx) }

    /** Der Server hat Karten und Straßendaten von außen gesperrt (Schalter "Karten extern ladbar" ist aus). */
    class ExternBlocked : IllegalStateException(Tx.t("Karten und Straßendaten gibt es nur im Heimnetz (Server-Einstellung „Karten extern ladbar“).", "Maps and road data are only available on the home network (server setting “Karten extern ladbar”)."))
    private fun httpFail(code: Int, what: String = ""): IllegalStateException =
        if (code == 403) ExternBlocked() else IllegalStateException(if (what.isEmpty()) "HTTP $code" else "$what: HTTP $code")

    fun mapsDir(ctx: Context) = File(ctx.filesDir, "maplibre/regions").also { it.mkdirs() }
    fun poisDir(ctx: Context) = File(ctx.filesDir, "pois").also { it.mkdirs() }
    fun mapFileOf(ctx: Context, id: String) = File(mapsDir(ctx), "$id.pmtiles")
    fun poiFileOf(ctx: Context, id: String) = File(poisDir(ctx), "$id.tsv.gz")

    /** IDs der Regionen, die auf dem Handy liegen. */
    fun installedIds(ctx: Context): List<String> =
        (mapsDir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") && f.length() > 0 } ?: emptyArray())
            .map { it.name.removeSuffix(".pmtiles") }.sorted()

    fun nameOf(ctx: Context, id: String): String = prefs(ctx).getString("name_$id", null) ?: id

    fun delete(ctx: Context, id: String) { mapFileOf(ctx, id).delete(); poiFileOf(ctx, id).delete(); prefs(ctx).edit().remove("limits_stand_$id").apply() }

    private fun conn(url: String, method: String = "GET"): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 8_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "HUDPilot"); DeviceId.apply(c); return c
    }

    private fun ref(o: JSONObject?, extraKey: String? = null): FileRef? =
        o?.let { FileRef(it.getString("datei"), it.optLong("bytes"), it.optLong("stand"), if (extraKey == null) "" else it.opt(extraKey)?.toString() ?: "") }

    /** Wirft bei Fehlern eine Exception mit lesbarem Text. */
    fun fetchManifest(base: String): Manifest {
        val c = conn("$base/manifest.json")
        try {
            if (c.responseCode != 200) throw httpFail(c.responseCode)
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val full = LinkedHashMap<String, Full>()
            val reg = j.optJSONArray("regionen")
            for (i in 0 until (reg?.length() ?: 0)) {
                val o = reg!!.getJSONObject(i)
                val rt = o.optJSONArray("routen")
                val routes = (0 until (rt?.length() ?: 0)).mapNotNull { ref(rt!!.getJSONObject(it)) }
                full[o.getString("id")] = Full(Info(o.getString("id"), o.getString("name"), o.optString("land"), true, o.optString("landname")), ref(o.optJSONObject("karte")), routes, ref(o.optJSONObject("pois")), ref(o.optJSONObject("strassen"), "zahlen"))
            }
            val all = ArrayList<Info>()
            val al = j.optJSONArray("alle")
            if (al != null) for (i in 0 until al.length()) {
                val o = al.getJSONObject(i); all += Info(o.getString("id"), o.getString("name"), o.optString("land"), o.optBoolean("bereit"), o.optString("landname"))
            } else all += full.values.map { it.info }
            return Manifest(all, full, ref(j.optJSONObject("blitzer"), "anzahl"))
        } finally { c.disconnect() }
    }

    /** Meldet Regionen dem Server (er baut fehlende und hält gemeldete aktuell). Ergebnis: id -> fertig/wartet/baut/neu */
    fun need(base: String, ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val c = conn("$base/api/need?ids=" + ids.joinToString(","))
        try {
            if (c.responseCode != 200) throw httpFail(c.responseCode)
            val r = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONObject("regionen") ?: return emptyMap()
            return r.keys().asSequence().associateWith { r.optString(it) }
        } finally { c.disconnect() }
    }

    class Outbox(val ts: Long, val name: String, val profile: String, val points: List<Pair<Double, Double>>, val avoid: List<String>? = null, val cityw: Double? = null,
                  /** Gespeicherte Strecke (lat, lon), falls die Tour vom Server eine mitbrachte. */ val route: List<Pair<Double, Double>>? = null)

    /** Vom Web-Planer gesendete Route (null: keine oder Server nicht erreichbar). Kurze Zeitgrenzen, damit die Karte nicht wartet. */
    fun fetchOutbox(ctx: Context): Outbox? {
        val base = serverUrl(ctx); if (base.isEmpty()) return null
        return try {
            val c = conn("$base/api/outbox"); c.connectTimeout = 2_500; c.readTimeout = 4_000
            ServerProfile.apply(ctx, c)
            try {
                if (c.responseCode != 200) return null
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val a = j.optJSONArray("points") ?: return null
                if (a.length() < 2) return null
                val pts = (0 until a.length()).map { val p = a.getJSONArray(it); Pair(p.getDouble(0), p.getDouble(1)) }
                Outbox(j.optLong("ts"), j.optString("name", "Tour"), j.optString("profile"), pts,
                    j.optJSONArray("avoid")?.let { x -> (0 until x.length()).map { x.getString(it) } },
                    if (j.has("cityw")) j.optDouble("cityw") else null)
            } finally { c.disconnect() }
        } catch (_: Exception) { null }
    }

    fun seenOutbox(ctx: Context): Long = prefs(ctx).getLong("outbox_ts", 0L)
    fun markOutboxSeen(ctx: Context, ts: Long) { prefs(ctx).edit().putLong("outbox_ts", ts).apply() }

    /** Beim App-Start (im WLAN): installierte Regionen melden und prüfen, wo es Neueres gibt. Gibt die Namen mit Update zurück. */
    fun checkUpdates(ctx: Context): List<String> {
        val base = serverUrl(ctx); val ids = installedIds(ctx)
        if (base.isEmpty() || ids.isEmpty() || isAway(ctx)) return emptyList()   // unterwegs nichts still nachladen
        return try {
            need(base, ids)
            val m = fetchManifest(base)
            val names = ids.mapNotNull { id ->
                val f = m.full[id] ?: return@mapNotNull null
                val upd = (f.map != null && RegionLogic.needsUpdate(mapFileOf(ctx, id).lastModified(), f.map.stand)) ||
                    (f.pois != null && RegionLogic.needsUpdate(poiFileOf(ctx, id).lastModified(), f.pois.stand))
                if (upd) f.info.name else null
            }
            syncExtras(ctx, base, m, ids)   // Straßendaten und Blitzerliste sind klein genug, um sie ohne Nachfrage nachzuziehen (nur im WLAN, siehe Aufrufer)
            names
        } catch (_: Exception) { emptyList() }
    }

    fun cancel() { cancel.set(true) }

    /** Gewählte Regionen laden bzw. aktualisieren. */
    fun start(ctx: Context, ids: List<String>) {
        if (ids.isEmpty() || status.running) return
        val app = ctx.applicationContext; val base = serverUrl(app)
        if (base.isEmpty()) return
        cancel.set(false)
        status.running = true; status.error = null; status.text = ""; status.done = 0; status.total = 0; changed()
        exec.execute {
            try {
                for (id in ids) { if (cancel.get()) break; loadOne(app, base, id) }
                if (!cancel.get()) {
                    try { syncCameras(app, base, fetchManifest(base)) } catch (e: Exception) { BridgeBus.log("Blitzerliste: " + (e.message ?: e.javaClass.simpleName)) }
                }
                if (!cancel.get()) { setText(Tx.t("Fertig.", "Done.")) }
            } catch (e: Exception) {
                BridgeBus.log("Region laden: " + (e.message ?: e.javaClass.simpleName))
                main.post { status.error = e.message ?: e.javaClass.simpleName; listener?.invoke() }
            }
            main.post { status.running = false; listener?.invoke() }
        }
    }

    private fun setText(t: String, done: Long = 0, total: Long = 0, speed: Long = 0) {
        main.post { status.text = t; status.done = done; status.total = total; status.speed = speed; listener?.invoke() }
    }

    private fun loadOne(ctx: Context, base: String, id: String) {
        // Server bitten, die Region zu bauen, falls es sie noch nicht gibt; dann warten.
        var tries = 0
        var m: Manifest
        while (true) {
            if (cancel.get()) return
            val st = need(base, listOf(id))[id] ?: "neu"
            m = fetchManifest(base)
            if (m.full[id]?.map != null) break
            setText(Tx.t("Server baut ${id} (", "Server is building ${id} (") + st + Tx.t("), das dauert …", "), this takes a while …"))
            Thread.sleep(8_000)
            if (++tries > 450) throw IllegalStateException(Tx.t("Server hat die Region nicht rechtzeitig gebaut.", "Server did not finish the region in time."))
        }
        val f = m.full[id]!!
        val name = f.info.name
        prefs(ctx).edit().putString("name_$id", name).apply()
        f.map?.let { r ->
            val dest = mapFileOf(ctx, id)
            if (RegionLogic.needsUpdate(dest.lastModified(), r.stand) || !dest.isFile) fetch(base, r, dest, Tx.t("$name: Karte", "$name: map"))
        }
        if (cancel.get()) return
        for (r in f.routes) {
            val dest = PilotStore.tileFile(ctx, RegionLogic.tileName(r.path))
            if (!dest.isFile || RegionLogic.needsUpdate(dest.lastModified(), r.stand)) fetch(base, r, dest, Tx.t("$name: Routen ", "$name: routes ") + RegionLogic.tileName(r.path))
            if (cancel.get()) return
        }
        f.pois?.let { r ->
            val dest = poiFileOf(ctx, id)
            if (!dest.isFile || RegionLogic.needsUpdate(dest.lastModified(), r.stand)) fetch(base, r, dest, Tx.t("$name: Orte", "$name: places"))
        }
        if (cancel.get()) return
        f.roads?.let { r -> if (limitsNeeded(ctx, id, r)) loadLimits(ctx, base, id, name, r) }
        AreaDownloader.refreshLookups(ctx)
    }

    // ---------- Straßendaten (Tempolimits, Blitzer, Schilder …) und Blitzerliste vom Server ----------

    private val syncLock = Any()

    /** Gibt es bei dieser Region neuere Straßendaten auf dem Server als auf dem Handy? */
    fun limitsNeeded(ctx: Context, id: String, r: FileRef?): Boolean =
        r != null && RegionLogic.needsUpdate(prefs(ctx).getLong("limits_stand_$id", 0L), r.stand)

    /** Zip der Region laden und in den Kachel-Speicher einsortieren (Nachbarregionen werden an der Kante zusammengeführt). */
    private fun loadLimits(ctx: Context, base: String, id: String, name: String, r: FileRef) = synchronized(syncLock) {
        val zip = File(ctx.cacheDir, "limits_$id.zip")
        fetch(base, r, zip, Tx.t("$name: Straßendaten", "$name: road data"))
        if (cancel.get() || !zip.isFile) return@synchronized
        setText(Tx.t("$name: Straßendaten einsortieren …", "$name: sorting in road data …"))
        val n = try {
            ServerLimits.unpack(zip, TileStore.dir(ctx), cancelled = { cancel.get() }, progress = { c ->
                setText(Tx.t("$name: Straßendaten einsortieren (", "$name: sorting in road data (") + String.format(Tx.loc(), "%,d", c) + Tx.t(" Kacheln) …", " tiles) …"))
            })
        } catch (e: io.github.th3s1nc.osmandhudbridge.limit.ImportCancelled) { return@synchronized }
        zip.delete()
        prefs(ctx).edit().putLong("limits_stand_$id", r.stand * 1000L).apply()
        // Zusammenfassung der App (Zahlen, Alter der Daten) mit den Zahlen vom Server füllen
        val st = ImportStats.fromLine(r.extra)
        if (st != null) {
            val sp = ctx.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
            val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(r.stand * 1000L))
            sp.edit().putString(BridgeService.KEY_IMPORT_LOG, ImportLog.add(sp.getString(BridgeService.KEY_IMPORT_LOG, null), ImportLog.Entry(name + Tx.t(" (Server)", " (server)"), date, st))).apply()
        }
        BridgeBus.log(Tx.t("Straßendaten vom Server: $name, $n Kacheln", "Road data from server: $name, $n tiles"))
    }

    /**
     * Blitzerliste (SCDB) vom Server: neuere Fassung laden und als eigene Liste speichern. Hat der Server keine mehr
     * (dort gelöscht), wird die vom Server stammende Liste auch auf dem Handy entfernt.
     */
    private fun syncCameras(ctx: Context, base: String, m: Manifest) = synchronized(syncLock) {
        val p = prefs(ctx)
        val bp = ctx.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
        val r = m.cameras
        if (r == null) {
            if (p.getLong("cam_stand", 0L) > 0L) {
                CameraStore.clear(ctx.filesDir)
                p.edit().remove("cam_stand").apply()
                bp.edit().remove(BridgeService.KEY_CAM_LIST_COUNT).remove(BridgeService.KEY_CAM_LIST_FILES).apply()
            }
            return@synchronized
        }
        if (!RegionLogic.needsUpdate(p.getLong("cam_stand", 0L), r.stand) && CameraStore.file(ctx.filesDir).isFile) return@synchronized
        val tmp = File(ctx.cacheDir, "cameras_server.csv")
        fetch(base, r, tmp, Tx.t("Blitzerliste", "Camera list"))
        if (cancel.get() || !tmp.isFile) return@synchronized
        val list = try { CameraImport.fromCsv(tmp.readText(Charsets.UTF_8)) } catch (_: Exception) { emptyList() }
        tmp.delete()
        if (list.isEmpty()) return@synchronized
        CameraStore.save(ctx.filesDir, list)
        p.edit().putLong("cam_stand", r.stand * 1000L).apply()
        bp.edit().putInt(BridgeService.KEY_CAM_LIST_COUNT, list.size).putInt(BridgeService.KEY_CAM_LIST_FILES, 0).apply()
        BridgeBus.log(Tx.t("Blitzerliste vom Server: ${list.size} Blitzer", "Camera list from server: ${list.size} cameras"))
    }

    /** Nach "Straßendaten entfernen": vergessen, was geladen war, damit der nächste Abgleich alles neu holt. */
    fun forgetLimits(ctx: Context) {
        val e = prefs(ctx).edit()
        for (k in prefs(ctx).all.keys) if (k.startsWith("limits_stand_")) e.remove(k)
        e.apply()
    }

    /** Knopf "Jetzt vom Server holen": nur Straßendaten und Blitzerliste prüfen und nachziehen (keine Karten). */
    fun syncExtrasNow(ctx: Context) {
        if (status.running) return
        val app = ctx.applicationContext; val base = serverUrl(app)
        if (base.isEmpty()) return
        cancel.set(false)
        status.running = true; status.error = null; status.text = Tx.t("Prüfe Server …", "Checking server …"); status.done = 0; status.total = 0; changed()
        exec.execute {
            try {
                val ids = installedIds(app)
                val m = fetchManifest(base)
                if (ids.isNotEmpty()) need(base, ids)
                syncExtrasInner(app, base, m, ids)
                if (!cancel.get()) {
                    val have = ids.any { m.full[it]?.roads != null }
                    setText(when {
                        ids.isEmpty() && m.cameras == null -> Tx.t("Noch keine Region geladen (Karte → ⚙ → Regionen).", "No region loaded yet (Map → ⚙ → Regions).")
                        ids.isNotEmpty() && !have -> Tx.t("Der Server hat noch keine Straßendaten gebaut. Bitte später nochmal.", "The server has not built road data yet. Please try again later.")
                        else -> Tx.t("Fertig, alles aktuell.", "Done, everything is up to date.")
                    })
                }
            } catch (e: Exception) {
                BridgeBus.log("Server abfragen: " + (e.message ?: e.javaClass.simpleName))
                main.post { status.error = e.message ?: e.javaClass.simpleName; listener?.invoke() }
            }
            main.post { status.running = false; listener?.invoke() }
        }
    }

    /** Still nachziehen, was neu ist: Straßendaten der geladenen Regionen und die Blitzerliste. */
    private fun syncExtras(ctx: Context, base: String, m: Manifest, ids: List<String>) {
        if (!status.running) cancel.set(false) // ein früherer Abbruch soll das stille Nachziehen nicht blockieren
        try { syncExtrasInner(ctx, base, m, ids) } finally {
            if (!status.running) main.post { if (!status.running) { status.text = ""; status.done = 0; status.total = 0; listener?.invoke() } }
        }
    }

    private fun syncExtrasInner(ctx: Context, base: String, m: Manifest, ids: List<String>) {
        for (id in ids) {
            if (cancel.get()) return
            val f = m.full[id] ?: continue
            val r = f.roads ?: continue
            if (limitsNeeded(ctx, id, r)) {
                try { loadLimits(ctx, base, id, f.info.name, r) } catch (e: Exception) { BridgeBus.log("Straßendaten ${f.info.name}: " + (e.message ?: e.javaClass.simpleName)) }
            }
        }
        try { syncCameras(ctx, base, m) } catch (e: Exception) { BridgeBus.log("Blitzerliste: " + (e.message ?: e.javaClass.simpleName)) }
    }

    /** Datei laden, bei Abbruch fortsetzbar (.part bleibt liegen). Danach bekommt die Datei das Datum vom Server. */
    private fun fetch(base: String, r: FileRef, dest: File, label: String) {
        val part = File(dest.path + ".part")
        dest.parentFile?.mkdirs()
        var have = if (part.isFile) part.length() else 0L
        if (r.bytes > 0 && have > r.bytes) { part.delete(); have = 0 }
        if (have == r.bytes && have > 0) { finish(part, dest, r); return }
        val c = conn("$base/${r.path}")
        c.readTimeout = 60_000
        RegionLogic.rangeHeader(have)?.let { c.setRequestProperty("Range", it) }
        try {
            val code = c.responseCode
            if (code == 416) { part.delete(); throw IllegalStateException(Tx.t("Bitte nochmal tippen (Datei neu beginnen).", "Please tap again (restarting file).")) }
            if (code != 200 && code != 206) throw httpFail(code, r.path)
            if (code == 200) have = 0
            val total = if (r.bytes > 0) r.bytes else have + c.contentLengthLong
            var done = have; var lastUi = 0L; var spT = System.currentTimeMillis(); var spB = done; var speed = 0L
            RandomAccessFile(part, "rw").use { out ->
                out.seek(have); if (code == 200) out.setLength(0)
                c.inputStream.use { input ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        if (cancel.get()) return
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val now = System.currentTimeMillis()
                        if (now - spT >= 1000) { val cur = (done - spB) * 1000 / (now - spT); speed = RegionLogic.smoothSpeed(speed, cur); spT = now; spB = done }
                        if (now - lastUi > 400) { lastUi = now; setText(label, done, total, speed) }
                    }
                }
            }
            if (total > 0 && part.length() != total) throw IllegalStateException(Tx.t("${r.path}: Verbindung abgebrochen, bitte nochmal tippen (macht weiter).", "${r.path}: connection lost, tap again (resumes)."))
            finish(part, dest, r)
        } finally { c.disconnect() }
    }

    private fun finish(part: File, dest: File, r: FileRef) {
        dest.delete()
        if (!part.renameTo(dest)) { part.copyTo(dest, true); part.delete() }
        if (r.stand > 0) dest.setLastModified(r.stand * 1000L)
    }
}
