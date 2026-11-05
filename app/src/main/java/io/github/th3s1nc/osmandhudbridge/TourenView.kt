package io.github.th3s1nc.osmandhudbridge

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.ServerProfile
import io.github.th3s1nc.osmandhudbridge.pilot.TourSync
import io.github.th3s1nc.osmandhudbridge.track.Ride
import io.github.th3s1nc.osmandhudbridge.track.RideFormat
import io.github.th3s1nc.osmandhudbridge.track.RideStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * Touren im Reiter "Touren" der App: "Geplant" (vom Server), "Gefahren" (Fahrten mit Navigation) und "Aufnahmen" (freie Aufnahmen).
 * Die View sitzt in einer Karte der Seite; die Zusammenfassung (Saison) steht darüber.
 */
class TourenView(private val act: AppCompatActivity) {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val ACC = Color.parseColor("#FF7A00")
    val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val tab1: TextView
    private val tab2: TextView
    private val tab3: TextView
    private val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private var tab = 1
    private var status = ""

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()
    private fun tv(t: String, size: Float, color: Int? = null, bold: Boolean = false, muted: Boolean = false) = TextView(act).apply {
        text = t; textSize = size; if (color != null) setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); if (muted) alpha = 0.7f
    }

    init {
        val tabs = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        fun mk(i: Int) = tv("", 15f, bold = true).apply { gravity = android.view.Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); setOnClickListener { select(i) } }
        tab1 = mk(0); tab2 = mk(1); tab3 = mk(2)
        tabs.addView(tab1, LinearLayout.LayoutParams(0, -2, 1f)); tabs.addView(tab2, LinearLayout.LayoutParams(0, -2, 1f)); tabs.addView(tab3, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tabs)
        root.addView(box)
        render()
    }

    /** Seite wird sichtbar: Liste neu zeichnen, bei "Geplant" die Serverliste holen. */
    fun onShow() {
        render()
        if (tab == 0 && TourSync.base(act).isNotEmpty()) refreshPlanned()
    }

    /** Reiter 0 Geplant, 1 Gefahren, 2 Aufnahmen. */
    fun select(t: Int) {
        tab = t
        render()
        if (t == 0 && TourSync.base(act).isNotEmpty()) refreshPlanned()
    }

    private fun refreshPlanned() {
        status = Tx.t("Hole Liste vom Server …", "Fetching list from the server …"); render()
        exec.execute {
            val l = TourSync.fetchPlanned(act)
            main.post {
                if (act.isFinishing || act.isDestroyed) return@post
                status = if (l != null) ""
                    else if (!ServerProfile.has(act) || ServerProfile.authFailed)
                        Tx.t("Kein Profil auf dem Server. Bitte in der Karte unter ⚙ → Server anlegen oder anmelden.", "No profile on the server. Please create or sign in on the map under ⚙ → Server.")
                    else Tx.t("Server nicht erreichbar. Gezeigt wird die zuletzt geholte Liste.", "Server not reachable. Showing the last fetched list.")
                render()
            }
        }
    }

    fun render() {
        val planned = TourSync.cachedPlanned(act)
        val rides = RideStore.list(act)
        val nav = rides.filter { it.kind == RideStore.KIND_NAV }
        val free = rides.filter { it.kind != RideStore.KIND_NAV }
        tab1.text = Tx.t("Geplant", "Planned") + " (${planned.size})"
        tab2.text = Tx.t("Gefahren", "Ridden") + " (${nav.size})"
        tab3.text = Tx.t("Aufnahmen", "Recordings") + " (${free.size})"
        for ((i, t) in listOf(tab1, tab2, tab3).withIndex()) { t.setTextColor(if (tab == i) ACC else Color.parseColor("#A8A8B0")) }
        box.removeAllViews()
        when (tab) {
            0 -> renderPlanned(planned)
            1 -> renderRides(nav)
            else -> renderRides(free)
        }
    }

    private fun row(title: String, sub: String, onTap: () -> Unit, onLong: (() -> Unit)? = null): View {
        val r = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)); isClickable = true; setOnClickListener { onTap() } }
        if (onLong != null) r.setOnLongClickListener { onLong(); true }
        r.addView(tv(title, 16f, bold = true)); r.addView(tv(sub, 13f, muted = true))
        val wrap = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(r)
        wrap.addView(View(act).apply { setBackgroundColor(Color.parseColor("#2B2B33")) }, LinearLayout.LayoutParams(-1, 1))
        return wrap
    }

    private fun renderPlanned(l: List<TourSync.Planned>) {
        if (TourSync.base(act).isEmpty()) {
            box.addView(tv(Tx.t("Noch keine Serveradresse. Bitte in der Karte unter ▦ → Karte → Regionen laden eintragen.", "No server address yet. Please enter it on the map under ▦ → Map → Load regions."), 14f, ACC).apply { setPadding(0, dp(10), 0, 0) })
            return
        }
        if (status.isNotEmpty()) box.addView(tv(status, 13f, muted = true).apply { setPadding(0, dp(8), 0, dp(4)) })
        if (l.isEmpty()) { box.addView(tv(Tx.t("Noch keine geplante Tour. Im Webplaner speichern, oder in der Karte unter ▦ → „Plan auf Server speichern“.", "No planned tour yet. Save one in the web planner, or on the map under ▦ → “Save plan to server”."), 14f, muted = true).apply { setPadding(0, dp(10), 0, 0) }); return }
        val df = SimpleDateFormat("dd.MM.yyyy", Tx.loc())
        for (p in l) box.addView(row(p.name, p.points.toString() + Tx.t(" Punkte · ", " points · ") + p.profile.removePrefix("motorrad_") + " · " + df.format(Date(p.updated * 1000)), { openPlanned(p) }))
        box.addView(tv(Tx.t("Antippen legt die Tour in die Planung der Karte.", "Tap to put the tour into the map planning."), 12f, muted = true).apply { setPadding(0, dp(10), 0, 0) })
    }

    private fun openPlanned(p: TourSync.Planned) {
        Toast.makeText(act, Tx.t("Lade Tour …", "Loading tour …"), Toast.LENGTH_SHORT).show()
        exec.execute {
            val t = TourSync.fetchTour(act, p.id)
            main.post {
                if (act.isFinishing || act.isDestroyed) return@post
                if (t == null || t.points.size < 2) { Toast.makeText(act, Tx.t("Tour nicht ladbar (Server erreichbar?).", "Tour not loadable (server reachable?)."), Toast.LENGTH_LONG).show(); return@post }
                openOnMap(t.name, t.profile, t.points, t.avoid)
            }
        }
    }

    private fun openOnMap(name: String, profile: String, pts: List<Pair<Double, Double>>, avoid: List<String>? = null) {
        val arr = DoubleArray(pts.size * 2) { if (it % 2 == 0) pts[it / 2].first else pts[it / 2].second }
        act.startActivity(Intent(act, PilotMapLibreActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("tour_name", name).putExtra("tour_profile", profile).putExtra("tour_pts", arr)
            .apply { if (avoid != null) putExtra("tour_avoid", avoid.toTypedArray()) })
    }

    private fun renderRides(l: List<Ride>) {
        if (TourSync.base(act).isNotEmpty() && l.isNotEmpty()) {
            val open = l.count { it.id !in TourSync.uploadedIds(act) }
            if (open > 0) box.addView(tv(Tx.t("$open Fahrt(en) noch nicht auf dem Server. Sie werden im Heim-WLAN automatisch hochgeladen. ", "$open ride(s) not on the server yet. They upload automatically on home Wi-Fi. "), 13f, ACC).apply {
                setPadding(0, dp(8), 0, dp(4)); isClickable = true
                setOnClickListener { uploadAll() }
            })
        }
        if (l.isEmpty()) {
            box.addView(tv(if (tab == 1) Tx.t("Noch keine Navigationsfahrt.", "No navigation ride yet.") else Tx.t("Noch keine Aufnahme. Der rote Knopf in der Karte startet eine.", "No recording yet. The red button on the map starts one."), 14f, muted = true).apply { setPadding(0, dp(10), 0, 0) })
            return
        }
        val up = TourSync.uploadedIds(act)
        for (r in l) {
            val s = r.stats
            val sub = RideStore.dateText(s.startMs) + " · " + RideFormat.distance(s.distanceM) + " · " + RideFormat.duration(s.movingMs) + " · max ${s.maxKmh} km/h" +
                (if (TourSync.base(act).isEmpty()) "" else if (r.id in up) "  ✓ " + Tx.t("auf Server", "on server") else "  ○ " + Tx.t("noch nicht auf Server", "not on server yet"))
            box.addView(row(r.name, sub, { act.startActivity(Intent(act, RideDetailActivity::class.java).putExtra(RideDetailActivity.EXTRA_ID, r.id)) }, { rideActions(r) }))
        }
        box.addView(tv(Tx.t("Antippen öffnet die Fahrt mit Diagrammen. Langer Druck: nachfahren, auf den Server sichern oder löschen.", "Tap opens the ride with charts. Long press: ride it again, back it up to the server or delete."), 12f, muted = true).apply { setPadding(0, dp(10), 0, 0) })
    }

    private fun uploadAll() {
        Toast.makeText(act, Tx.t("Lade hoch …", "Uploading …"), Toast.LENGTH_SHORT).show()
        exec.execute {
            val n = TourSync.uploadPending(act)
            main.post { if (!act.isFinishing && !act.isDestroyed) { Toast.makeText(act, if (n > 0) Tx.t("$n Fahrt(en) hochgeladen.", "$n ride(s) uploaded.") else Tx.t("Nichts hochgeladen (Server erreichbar?).", "Nothing uploaded (server reachable?)."), Toast.LENGTH_LONG).show(); render() } }
        }
    }

    private fun rideActions(r: Ride) {
        AlertDialog.Builder(act).setTitle(r.name)
            .setItems(arrayOf(Tx.t("Als Route nachfahren", "Ride it again as a route"), Tx.t("Jetzt auf Server sichern", "Back up to server now"), Tx.t("Löschen", "Delete"))) { _, which ->
                when (which) {
                    0 -> {
                        val pts = RideStore.points(act, r.id).map { Pair(it.lat, it.lon) }
                        if (pts.size < 2) { Toast.makeText(act, Tx.t("Keine Punkte gespeichert.", "No points stored."), Toast.LENGTH_SHORT).show(); return@setItems }
                        openOnMap(r.name, "", io.github.th3s1nc.osmandhudbridge.pilot.RegionLogic.samplePoints(pts, 27))
                    }
                    1 -> exec.execute {
                        val ok = TourSync.uploadRide(act, r.id)
                        main.post { if (!act.isFinishing && !act.isDestroyed) { Toast.makeText(act, if (ok) Tx.t("Gesichert.", "Backed up.") else Tx.t("Server nicht erreichbar.", "Server not reachable."), Toast.LENGTH_SHORT).show(); render() } }
                    }
                    else -> AlertDialog.Builder(act).setTitle(Tx.t("Fahrt löschen?", "Delete ride?")).setMessage(r.name)
                        .setPositiveButton(Tx.t("Löschen", "Delete")) { _, _ -> RideStore.delete(act, r); render() }
                        .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
                }
            }.setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
    }
}
