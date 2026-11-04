package io.github.th3s1nc.osmandhudbridge

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.PilotStore
import io.github.th3s1nc.osmandhudbridge.pilot.RegionLogic
import io.github.th3s1nc.osmandhudbridge.pilot.RegionSync
import java.util.concurrent.Executors

/** Regionen vom eigenen Kartenserver laden: Karte, Routendaten und Orte (Tanken, Essen …) je Region. */
class RegionsActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var tvInfo: TextView
    private lateinit var tvProg: TextView
    private lateinit var pb: ProgressBar
    private lateinit var list: LinearLayout
    private lateinit var btnLoad: TextView
    private lateinit var btnDel: TextView
    private var manifest: RegionSync.Manifest? = null
    private val checked = HashSet<String>()
    private val boxes = HashMap<String, CheckBox>()
    private val landOpen = HashMap<String, Boolean>()
    private val headUpdaters = ArrayList<() -> Unit>()   // frischen den "n gewählt"-Zähler der Länderzeilen auf, wenn man ein Häkchen setzt
    private lateinit var etSearch: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val ACC = Color.parseColor("#FF7A00")

    private fun tv(t: String, size: Float = 14f, color: Int = Color.WHITE) = TextView(this).apply { text = t; textSize = size; setTextColor(color) }
    /** Knopf als einfaches TextView (kein System-Button mit eigenen Rändern): feste Höhe, Text mittig, abgerundet, blasser wenn aus. */
    private fun btn(t: String, primary: Boolean, onClick: () -> Unit): TextView = object : TextView(this) {
        override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); alpha = if (enabled) 1f else 0.45f }
    }.apply {
        text = t; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER; maxLines = 2
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(12), 0, dp(12), 0); isClickable = true; isFocusable = true
        val shape = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(12).toFloat(); setColor(if (primary) ACC else Color.parseColor("#33333A"))
        }
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), shape, null)
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#14141A")); fitsSystemWindows = true }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(16), dp(4)) }
        head.addView(tv("←", 24f).apply { setPadding(dp(8), dp(8), dp(16), dp(8)); setOnClickListener { finish() } })
        head.addView(tv(Tx.t("Regionen laden", "Load regions"), 20f).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
        root.addView(head)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(24)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        tvInfo = tv("", 13f, Color.parseColor("#A8A8B0")).apply { setPadding(0, dp(8), 0, dp(8)) }
        body.addView(tvInfo)
        etSearch = EditText(this).apply {
            hint = Tx.t("Land oder Region suchen …", "Search country or region …")
            setHintTextColor(Color.parseColor("#7A7A84")); setTextColor(Color.WHITE); textSize = 16f
            isSingleLine = true; inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#22222A")) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (manifest != null) renderList() }
            })
        }
        body.addView(etSearch, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        btnLoad = btn(Tx.t("Gewählte laden", "Load selected"), true) { loadSelected() }
        btnDel = btn(Tx.t("Gewählte löschen", "Delete selected"), false) { deleteSelected() }
        row.addView(btnLoad, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(5) })
        row.addView(btnDel, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(5) })
        body.addView(row)
        body.addView(btn(Tx.t("Download abbrechen", "Cancel download"), false) { RegionSync.cancel() }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })
        tvProg = tv("", 13f, ACC).apply { setPadding(0, dp(10), 0, 0) }
        body.addView(tvProg)
        pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; visibility = View.GONE }
        body.addView(pb, LinearLayout.LayoutParams(-1, -2))
        body.addView(tv(Tx.t(
            "Eine Region besteht aus Karte, Routendaten und Orten (Tanken, Essen, Hotel, Parken). Ist sie auf dem Server noch nicht gebaut, baut er sie auf Anforderung; das kann eine Weile dauern, die App wartet und lädt dann weiter. Gemeldete Regionen hält der Server aktuell.",
            "A region consists of map, route data and places (fuel, food, hotels, parking). If the server has not built it yet, it builds it on request; this can take a while, the app waits and continues. Regions you load are kept up to date by the server."), 12f, Color.parseColor("#A8A8B0")).apply { setPadding(0, dp(12), 0, 0) })

        RegionSync.setListener { showProgress() }
        showProgress()
        if (RegionSync.serverUrl(this).isNotEmpty()) connect() else showLocalOnly()
    }

    private var resumed = false
    override fun onResume() {
        super.onResume()
        val again = resumed; resumed = true
        if (again && ::tvInfo.isInitialized && RegionSync.serverUrl(this).isNotEmpty()) connect()
    }

    override fun onDestroy() { RegionSync.setListener(null); super.onDestroy() }

    private fun connect() {
        val url = RegionSync.homeUrl(this); val away = RegionSync.awayUrl(this)
        if (url.isEmpty() && away.isEmpty()) { showLocalOnly(); return }
        tvInfo.setTextColor(Color.parseColor("#A8A8B0")); tvInfo.text = Tx.t("Verbinde …", "Connecting …")
        exec.execute {
            // erst das Heimnetz, dann (falls eingetragen) die Adresse unterwegs
            var err: String? = null; var m: RegionSync.Manifest? = null; var via = ""; var blocked = false
            fun tryUrl(u: String, label: String) {
                try { m = RegionSync.fetchManifest(u); via = label }
                catch (e: RegionSync.ExternBlocked) { blocked = true; via = label }
                catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            }
            if (url.isNotEmpty()) { tryUrl(url, Tx.t("Heimnetz", "home network")) }
            if (m == null && !blocked && away.isNotEmpty()) tryUrl(away, Tx.t("unterwegs", "on the go"))
            val res = m; val msg = err
            main.post {
                if (isFinishing || isDestroyed) return@post
                manifest = res
                val both = url.isNotEmpty() && away.isNotEmpty()
                when {
                    res != null -> { tvInfo.setTextColor(Color.parseColor("#2E9E5B")); tvInfo.text = Tx.t("Verbunden", "Connected") + (if (both) " ($via)" else "") + "."; renderList() }
                    blocked -> { tvInfo.setTextColor(Color.parseColor("#E0A100")); tvInfo.text = Tx.t("Server erreichbar ($via). Karten und Straßendaten gibt es hier nur im Heimnetz; Touren, Fahrten und Einstellungen gehen.", "Server reachable ($via). Maps and road data are only available on the home network; tours, rides and settings work."); showLocalOnly() }
                    else -> { tvInfo.setTextColor(Color.parseColor("#D14343")); tvInfo.text = Tx.t("Server nicht erreichbar (", "Server not reachable (") + msg + Tx.t("). Bist du im Heim-WLAN?", "). Are you on home Wi-Fi?"); showLocalOnly() }
                }
            }
        }
    }

    /** Ohne Server: nur die schon geladenen Regionen zeigen. */
    private fun showLocalOnly() {
        list.removeAllViews(); boxes.clear()
        if (RegionSync.serverUrl(this).isEmpty()) { tvInfo.setTextColor(Color.parseColor("#E0A100")); tvInfo.text = Tx.t("Noch keine Serveradresse. Bitte im Kartenmenü unter „Server“ eintragen.", "No server address yet. Please enter it in the map menu under “Server”.") }
        val ids = RegionSync.installedIds(this)
        if (ids.isEmpty()) { list.addView(tv(Tx.t("Noch nichts geladen.", "Nothing loaded yet."), 14f, Color.parseColor("#FF7A00"))); return }
        list.addView(tv(Tx.t("Geladen", "Loaded"), 12f, ACC))
        for (id in ids) addRow(id, RegionSync.nameOf(this, id), null)
    }

    private fun renderList() {
        val m = manifest ?: return
        list.removeAllViews(); boxes.clear(); headUpdaters.clear()
        // Oben: schon geladene Regionen (Update zuerst, dann nach Name); darunter nach Ländern nur, was noch fehlt
        val have = RegionSync.installedIds(this).toSet()
        val q = etSearch.text.toString()
        fun hit(r: RegionSync.Info) = RegionLogic.matches(q, r.name, RegionLogic.landTitle(r.land, r.landName))
        val mine = m.all.filter { it.id in have && hit(it) }
        fun needsUpd(id: String): Boolean {
            val f = manifest?.full?.get(id) ?: return false
            val mf = RegionSync.mapFileOf(this, id)
            return (f.map != null && RegionLogic.needsUpdate(mf.lastModified(), f.map.stand)) ||
                (f.pois != null && RegionLogic.needsUpdate(RegionSync.poiFileOf(this, id).lastModified(), f.pois.stand)) ||
                RegionSync.limitsNeeded(this, id, f.roads)
        }
        if (mine.isNotEmpty()) {
            list.addView(tv(Tx.t("AUF DEM HANDY", "ON THIS PHONE"), 12f, ACC).apply { setPadding(0, dp(14), 0, dp(2)) })
            for (r in mine.sortedWith(compareByDescending<RegionSync.Info> { needsUpd(it.id) }.thenBy { it.name.lowercase() })) addRow(r.id, r.name, r)
        }
        val rest = m.all.filter { it.id !in have && hit(it) }
        for (g in RegionLogic.groupLands(rest, { it.land.ifEmpty { "?" } }, { RegionLogic.landTitle(it.land, it.landName) }, { it.name })) {
            val sel = g.items.count { it.id in checked }
            val single = g.items.size == 1
            val open = q.isNotBlank() || single || (landOpen[g.code] ?: (sel > 0))
            val arrow = if (single) "" else if (open) "▾ " else "▸ "
            fun headText(): String {
                val n = g.items.count { it.id in checked }
                val count = if (single) "" else "  ·  ${g.items.size}" + (if (n > 0) " · $n " + Tx.t("gewählt", "selected") else "")
                return arrow + g.title + count
            }
            val head = tv(headText(), 15f, ACC).apply {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(6))
                if (!single && q.isBlank()) setOnClickListener { landOpen[g.code] = !open; renderList() }
            }
            list.addView(head)
            headUpdaters += { head.text = headText() }
            if (open) for (r in g.items) addRow(r.id, r.name, r)
        }
        if (mine.isEmpty() && rest.isEmpty()) list.addView(tv(Tx.t("Keine passende Region.", "No matching region."), 14f, Color.parseColor("#A8A8B0")).apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun addRow(id: String, name: String, info: RegionSync.Info?) {
        val have = RegionSync.mapFileOf(this, id)
        val installed = have.isFile && have.length() > 0
        val f = manifest?.full?.get(id)
        val upd = installed && f != null && ((f.map != null && RegionLogic.needsUpdate(have.lastModified(), f.map.stand)) ||
            (f.pois != null && RegionLogic.needsUpdate(RegionSync.poiFileOf(this, id).lastModified(), f.pois.stand)) ||
            RegionSync.limitsNeeded(this, id, f.roads))
        val status = when {
            installed && upd -> Tx.t("geladen · Update verfügbar", "loaded · update available")
            installed -> Tx.t("geladen · ", "loaded · ") + RegionLogic.fmtBytes(have.length())
            info != null && info.ready -> Tx.t("auf dem Server bereit · ", "ready on server · ") + RegionLogic.fmtBytes(f?.map?.bytes ?: 0L)
            info != null -> Tx.t("noch nicht gebaut, wird auf Anforderung gebaut", "not built yet, built on request")
            else -> ""
        }
        val color = when { upd -> "#E0A100"; installed -> "#2E9E5B"; else -> "#A8A8B0" }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val cb = CheckBox(this).apply {
            isChecked = id in checked
            setOnCheckedChangeListener { _, on -> if (on) checked += id else checked -= id; headUpdaters.forEach { it() } }
        }
        boxes[id] = cb
        row.addView(cb)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(name, 16f))
        col.addView(tv(status, 12f, Color.parseColor(color)))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { cb.isChecked = !cb.isChecked }
        list.addView(row)
    }

    private fun loadSelected() {
        if (RegionSync.status.running) { Toast.makeText(this, Tx.t("Es läuft schon ein Download.", "A download is already running."), Toast.LENGTH_SHORT).show(); return }
        val ids = checked.toList()
        if (ids.isEmpty()) { Toast.makeText(this, Tx.t("Bitte erst Regionen ankreuzen.", "Please tick regions first."), Toast.LENGTH_SHORT).show(); return }
        val metered = try { (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered } catch (_: Exception) { false }
        val go = { RegionSync.start(this, ids); showProgress() }
        if (metered) AlertDialog.Builder(this).setTitle(Tx.t("Mobile Daten verwenden?", "Use mobile data?"))
            .setMessage(Tx.t("Du bist nicht im WLAN. Das verbraucht mobile Daten.", "You are not on Wi-Fi. This uses mobile data."))
            .setPositiveButton(Tx.t("Trotzdem laden", "Load anyway")) { _, _ -> go() }.setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
        else go()
    }

    private fun deleteSelected() {
        val ids = checked.filter { RegionSync.mapFileOf(this, it).isFile }
        if (ids.isEmpty()) return
        AlertDialog.Builder(this).setTitle(Tx.t("Löschen?", "Delete?"))
            .setMessage(Tx.t("${ids.size} Region(en) vom Handy löschen? (Routendaten bleiben liegen.)", "Delete ${ids.size} region(s) from the phone? (Route data stays.)"))
            .setPositiveButton(Tx.t("Löschen", "Delete")) { _, _ ->
                ids.forEach { RegionSync.delete(this, it); checked -= it }
                if (manifest != null) renderList() else showLocalOnly()
            }.setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
    }

    private fun showProgress() {
        val s = RegionSync.status
        btnLoad.isEnabled = !s.running
        if (s.running || s.text.isNotEmpty() || s.error != null) {
            val err = s.error
            tvProg.setTextColor(if (err != null) Color.parseColor("#D14343") else ACC)
            tvProg.text = if (err != null) Tx.t("Fehler: ", "Error: ") + err else s.text +
                (if (s.total > 0) " – " + PilotStore.size(s.done) + " " + Tx.t("von", "of") + " " + PilotStore.size(s.total) + (RegionLogic.fmtSpeed(s.speed).let { if (it.isEmpty()) "" else " · $it" }) else "")
            pb.visibility = if (s.running && s.total > 0) View.VISIBLE else View.GONE
            pb.progress = if (s.total > 0) (s.done * 1000 / s.total).toInt() else 0
        }
        if (!s.running && manifest != null && s.text.isNotEmpty()) renderList()
    }
}
