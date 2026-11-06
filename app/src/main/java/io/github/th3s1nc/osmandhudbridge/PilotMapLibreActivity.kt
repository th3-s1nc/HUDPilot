package io.github.th3s1nc.osmandhudbridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import io.github.th3s1nc.osmandhudbridge.track.RideFormat
import io.github.th3s1nc.osmandhudbridge.track.RideStore
import io.github.th3s1nc.osmandhudbridge.track.TrackRecorder
import io.github.th3s1nc.osmandhudbridge.pilot.Avoid
import io.github.th3s1nc.osmandhudbridge.pilot.RouteSnap
import io.github.th3s1nc.osmandhudbridge.pilot.RegionSync
import io.github.th3s1nc.osmandhudbridge.pilot.AutoSync
import io.github.th3s1nc.osmandhudbridge.pilot.Baustellen
import io.github.th3s1nc.osmandhudbridge.pilot.BaustellenLogic
import io.github.th3s1nc.osmandhudbridge.pilot.Favs
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsApi
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsLogic
import io.github.th3s1nc.osmandhudbridge.pilot.LiveShare
import io.github.th3s1nc.osmandhudbridge.pilot.ServerProfile
import io.github.th3s1nc.osmandhudbridge.pilot.SettingsBackup
import io.github.th3s1nc.osmandhudbridge.pilot.TourSync
import io.github.th3s1nc.osmandhudbridge.pilot.PoiIndex
import io.github.th3s1nc.osmandhudbridge.pilot.StyleMerge
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.Guide
import io.github.th3s1nc.osmandhudbridge.pilot.Hazard
import io.github.th3s1nc.osmandhudbridge.pilot.HazardKind
import io.github.th3s1nc.osmandhudbridge.pilot.MapHazards
import io.github.th3s1nc.osmandhudbridge.pilot.StyleVariants
import io.github.th3s1nc.osmandhudbridge.pilot.LaneHint
import io.github.th3s1nc.osmandhudbridge.pilot.MapOverlay
import io.github.th3s1nc.osmandhudbridge.pilot.MapNight
import io.github.th3s1nc.osmandhudbridge.pilot.NightPref
import io.github.th3s1nc.osmandhudbridge.pilot.PilotBus
import io.github.th3s1nc.osmandhudbridge.pilot.Sun
import io.github.th3s1nc.osmandhudbridge.pilot.TunnelWatch
import io.github.th3s1nc.osmandhudbridge.pilot.PilotNav
import io.github.th3s1nc.osmandhudbridge.pilot.PilotRoute
import io.github.th3s1nc.osmandhudbridge.pilot.PilotRouter
import io.github.th3s1nc.osmandhudbridge.pilot.PilotVoice
import io.github.th3s1nc.osmandhudbridge.pilot.Plan
import io.github.th3s1nc.osmandhudbridge.pilot.PlanPoint
import io.github.th3s1nc.osmandhudbridge.pilot.PlaceFormat
import io.github.th3s1nc.osmandhudbridge.pilot.PlaceSearch
import io.github.th3s1nc.osmandhudbridge.pilot.Waypoint
import io.github.th3s1nc.osmandhudbridge.limit.StatusText
import io.github.th3s1nc.osmandhudbridge.pilot.PilotSession
import io.github.th3s1nc.osmandhudbridge.pilot.PilotStore
import io.github.th3s1nc.osmandhudbridge.pilot.PmtilesHeader
import io.github.th3s1nc.osmandhudbridge.pilot.RouteSimulator
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hauptkarte auf Basis von MapLibre (Vektorkarte aus einer lokalen .pmtiles-Datei). Gleiche Bedienung wie die Mapsforge-Karte:
 * Suche, Ziel per langem Druck, Routenvorschau, Navigation mit Ansagen und Aufzeichnung, dazu ein Simulationsmodus.
 * Die Logik darunter (Planung, Führung, Ansagen, Suche) ist dieselbe wie bei der alten Karte.
 */
class PilotMapLibreActivity : AppCompatActivity(), PilotNav.Listener {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private val addedImages = HashSet<String>()
    private var appliedDark = false
    private lateinit var root: FrameLayout
    private val ui = ArrayList<View>()          // alle Bedienelemente und Anzeigen (zum Neuaufbau beim Drehen)
    private val hitViews = ArrayList<View>()    // darauf ist ein Tipp kein "Anzeige aus/ein"
    private val overlayViews = ArrayList<View>() // Anzeige, die per Tipp auf die Karte verschwindet
    private var turnArrow: TurnArrowView? = null
    private var tvDist: TextView? = null
    private var tvThen: TextView? = null
    private var bannerBox: View? = null
    private var tvHint: TextView? = null
    private var tvChips: TextView? = null
    private var tvEta: TextView? = null
    private var tvEtaLbl: TextView? = null
    private var tvRem: TextView? = null
    private var tvRemLbl: TextView? = null
    private var infoRow: FrameLayout? = null
    private var leftColView: View? = null
    private var infoDock = -1
    private var placedKey = ""
    private var btnZoomIn: View? = null
    private var btnZoomOut: View? = null
    private val glassViews = HashSet<View>()
    private var tvSpeed: TextView? = null
    private var speedBox: View? = null
    private var tvLimit: TextView? = null
    private var tvAhead: TextView? = null   // Früherkennung: kommendes Tempolimit
    private var barBox: View? = null
    private var tvBar: TextView? = null
    private var pbProgress: ProgressBar? = null
    private var landscape = false
    private var overlayOn = true
    private var curRoute: PilotRoute? = null
    private var lastState: Guide.State? = null
    private var hintOverride: String? = null
    private var arrivedBar: View? = null
    private val hideArrived = Runnable { arrivedBar?.visibility = View.GONE }
    private var altitude: Double? = null
    private var curCx = 0.5f
    private var curCy = CENTER_Y
    private var downAt = 0L
    private var downOnUi = false
    private var lastHazardAt = 0L
    private var hazardWant: List<Pair<Hazard, String?>> = emptyList()
    private var hazardKey = ""
    private var hitList: List<PlaceSearch.Hit> = emptyList()
    private var lastShown: LatLng? = null
    // Optionen der Anzeige (Dialog unter ⚙ → Anzeige)
    private var optEta = true
    private var optThen = false
    private var optProgress = false
    private var optRemTime = false
    private var optClock = false
    private var optAlt = false
    private var optCompass = false
    private var optHazards = true
    private val cards = ArrayList<Pair<View, Int>>()
    private val texts = ArrayList<TextView>()
    private lateinit var btnFollow: TextView
    // Ziel und Routenvorschau
    private val planExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val fileExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val pl = Plan()
    private var addMode = 0                 // 0 = nichts, 1 = nächster Punkt wird Zwischenziel, 2 = nächster Punkt wird Start
    private var startAfterPlan = false
    private val planHandler = Handler(Looper.getMainLooper())
    private val recalcRun = Runnable { recalc() }
    private val searchExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var searchSeq = 0
    private var searchBox: View? = null
    private var searchEdit: android.widget.EditText? = null
    private var resultsScroll: View? = null
    private var resultsBox: LinearLayout? = null
    private var pointsBox: LinearLayout? = null
    private var pointsScroll: View? = null
    private var actionsRow: View? = null
    private var sheetLevel = 1   // 0 = weggeschoben (nur Griff), 1 = kompakt, 2 = voll (Punkte und Profile)
    private var planning = false
    private var planMsg: String? = null
    private var planSeq = 0
    private var previewShown = false
    private var sheet: LinearLayout? = null
    private var tvSheetTitle: TextView? = null
    private var tvSheetSub: TextView? = null
    private var tvSheetNote: TextView? = null
    private var profChip: TextView? = null
    private var profPopup: android.widget.PopupWindow? = null
    private var previewRoute: PilotRoute? = null
    private var btnStart: TextView? = null
    private var btnMain: TextView? = null
    private var btnStop: TextView? = null
    private val panelBtns = ArrayList<View>()   // Knöpfe, die während der Navigation hinter dem Zahnrad stecken
    private var panelShown = false
    private val panelHide = Runnable { panelShown = false; applyPanel() }
    private var btnHud: TextView? = null
    private lateinit var btnNorth: TextView
    private var searchOpen = false
    private var btnSearch: TextView? = null
    private var btnHome: TextView? = null
    private var btnShare: TextView? = null
    private var favChip: TextView? = null
    private var homePick = false
    private var btnRec: TextView? = null
    private var recLook: Boolean? = null
    // Karte und Fahrzeug
    private var follow = true
    private var headUp = true
    private var manualRot = false
    private var tilt3d = false      // 3D: Fahrtrichtung oben und geneigt
    private var rideDialogOpen = false
    private var stopAsking = false
    private var historyShown = false
    private var rotAtPointerDown = 0f
    private var autoZoom = true
    private var lastZoomChange = 0L
    private var heading: Float? = null
    private var lastPos: LatLng? = null
    private var lastKmh = 0f
    private var haveFix = false
    private var downX = 0f
    private var downY = 0f
    private var locMgr: LocationManager? = null
    private var nightPref = NightPref.AUTO
    private val tunnelWatch = TunnelWatch()
    private var lastFixAt = 0L
    // Simulation
    private var sim: RouteSimulator? = null
    private var simRunning = false
    private var simLast = 0L
    private var simSpeed = 50.0
    private var simFactor = 1.0
    private var simBar: View? = null
    private var gpsLogDot: TextView? = null
    private val simChips = ArrayList<TextView>()
    private val ticker = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("pilot_ui", MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            evaluateTheme()
            updateHazards()
            refreshOverlay()
            updateButtons()
            ticker.postDelayed(this, 1000)
        }
    }

    private val pickMap = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importMap(uri) }
    private val pickGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadGpxUri(uri) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startLocation()
        askNotifications()
    }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Benachrichtigungen erlauben (ab Android 13). Ohne sie ist die Dauer-Benachrichtigung des Dienstes unsichtbar,
     * und damit auch der "Beenden"-Knopf darin. Höchstens zweimal fragen.
     */
    private fun askNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val n = prefs.getInt("notif_asked", 0)
        if (n >= 2) return
        prefs.edit().putInt("notif_asked", n + 1).apply()
        askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        nightPref = NightPref.parse(prefs.getString("map_night", null))
        // Ansicht (N / Fahrtrichtung oben / 3D) wiederherstellen; mit 3D-Gebäuden ohne gemerkte Ansicht gleich 3D
        when (prefs.getString("view_mode", null) ?: if (prefs.getBoolean("map_3d", false)) "3d" else null) {
            "n" -> { headUp = false; tilt3d = false }
            "up" -> { headUp = true; tilt3d = false }
            "3d" -> { headUp = true; tilt3d = true }
        }
        optEta = prefs.getBoolean("ov_eta", true)
        optThen = prefs.getBoolean("ov_then", false)
        optProgress = prefs.getBoolean("ov_prog", false)
        optRemTime = prefs.getBoolean("ov_remtime", false)
        optClock = prefs.getBoolean("ov_clock", false)
        optAlt = prefs.getBoolean("ov_alt", false)
        optCompass = prefs.getBoolean("ov_compass", false)
        optHazards = prefs.getBoolean("ov_hazards", true)
        root = FrameLayout(this).apply { fitsSystemWindows = true; setBackgroundColor(0xFF000000.toInt()) }
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        root.addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        buildUi()
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = true
            m.uiSettings.isTiltGesturesEnabled = true
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.setMinZoomPreference(3.0)
            m.setMaxZoomPreference(20.0)
            // Startposition: Routenanfang, sonst Mitte der Kartendatei, sonst Bayern
            val first = PilotSession.route?.points?.firstOrNull()?.let { LatLng(it.lat, it.lon) }
            val ctr = first ?: mapCenter() ?: LatLng(49.0, 11.5)
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(ctr, if (first != null) 13.0 else 8.0))
            // Langer Druck auf die Karte setzt das Ziel
            m.addOnMapLongClickListener { ll ->
                if (PilotNav.guiding) false else { onMapPoint(ll.latitude, ll.longitude, null); true }
            }
            m.addOnMapClickListener { ll -> if (PilotNav.guiding || menuLayer != null) false else tapBaustelle(ll) }
            applyPadding()
            loadStyle(isDarkNow())
            centerOnLastKnown()
        }
        if (mapFiles().isEmpty()) {
            Toast.makeText(this, Tx.t("Noch keine Karte. Unter ⚙ → Karte → Regionen laden die Region vom Server holen.", "No map yet. Under ⚙ → Map → Load regions fetch a region from the server."), Toast.LENGTH_LONG).show()
        }
        refreshOverlay()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (menuLayer != null) closeMenu()
                else if (resultsScroll?.visibility == View.VISIBLE) showResults(null, null)
                else if (searchOpen) closeSearch()
                else if (pl.dest != null && !PilotNav.guiding) clearAll()
                else if (PilotNav.guiding) confirmStop()
                else finishAffinity()
            }
        })
        handleTourIntent(intent)
        handleGpxIntent(intent)
        prefs.getString("default_profile", null)?.let { id -> if (PilotStore.profiles.any { it.id == id }) PilotSession.profileId = id }
        refreshSheet()
    }

    // ---------------- Karte, Stil und Ebenen ----------------

    private fun mapFile(): File = File(File(filesDir, "maplibre").also { it.mkdirs() }, "map.pmtiles")

    /** Alle Kartendateien: geladene Regionen und die selbst gewählte Datei. */
    private fun mapFiles(): List<File> {
        val l = ArrayList<File>()
        for (id in RegionSync.installedIds(this)) l += RegionSync.mapFileOf(this, id)
        if (mapFile().isFile && mapFile().length() > 0) l += mapFile()
        return l
    }

    private fun mapsSignature(): String = mapFiles().joinToString("|") { it.name + ":" + it.length() + ":" + it.lastModified() }
    private var shownSignature = ""

    private fun emptyStyle(dark: Boolean): String =
        "{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"bg\",\"type\":\"background\",\"paint\":{\"background-color\":\"" + (if (dark) "#2B2B2E" else "#CCCCCC") + "\"}}]}"

    /** Mitte des Gebiets der Kartendatei (aus dem Kopf der Datei), oder null. */
    private fun mapCenter(): LatLng? {
        val f = mapFiles().firstOrNull() ?: return null
        return try {
            val head = ByteArray(PmtilesHeader.SIZE)
            java.io.RandomAccessFile(f, "r").use { it.readFully(head) }
            val h = PmtilesHeader.parse(head) ?: return null
            LatLng((h.minLat + h.maxLat) / 2, (h.minLon + h.maxLon) / 2)
        } catch (_: Exception) { null }
    }

    /** Stil laden (hell oder dunkel) und danach Route, Marken und Position wieder darauflegen. */
    private fun loadStyle(dark: Boolean) {
        val m = map ?: return
        appliedDark = dark
        val files = mapFiles()
        shownSignature = mapsSignature()
        val json = if (files.isNotEmpty()) {
            try {
                val base = assets.open("maplibre/style-" + (if (dark) "dark" else "light") + ".json").bufferedReader().use { it.readText() }
                    .replace("__PATH__", files[0].absolutePath)
                StyleVariants.apply(
                    StyleMerge.merge(base, files.map { it.absolutePath }),
                    prefs.getBoolean("map_calm", false), prefs.getBoolean("map_3d", false), dark, prefs.getString("map_theme", "standard") ?: "standard"
                ).also {
                    BridgeBus.log("MapLibre: Stil " + (if (dark) "dunkel" else "hell") + ", ruhig=" + prefs.getBoolean("map_calm", false) +
                        ", 3D-Gebäude=" + prefs.getBoolean("map_3d", false) + ", Gebäude-Ebenen als Kasten: " + Regex("\"fill-extrusion\"").findAll(it).count())
                }
            } catch (e: Exception) {
                BridgeBus.log("MapLibre: Stil: " + (e.message ?: e.javaClass.simpleName)); emptyStyle(dark)
            }
        } else emptyStyle(dark)
        style = null
        roadIds = null
        m.setStyle(Style.Builder().fromJson(json)) { st ->
            try {
                addedImages.clear()
                setupOverlays(st)
                style = st
                pushAll()
            } catch (e: Exception) {
                BridgeBus.log("MapLibre: Stilfehler " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Routenfarbe: tags Orange, nachts Magenta (die Straßen sind nachts auch orange). */
    private fun routeColorHex() = if (appliedDark) "#FF2BD6" else "#FF7A00"
    private fun routeColorInt() = if (appliedDark) 0xFFFF2BD6.toInt() else 0xFFFF7A00.toInt()

    private fun setupOverlays(st: Style) {
        // Baustellen und Sperrungen liegen unter der Route
        st.addSource(GeoJsonSource("bs-src"))
        val bsColor = Expression.match(Expression.get("k"), Expression.color(0xFFFFB300.toInt()),
            Expression.stop("c", Expression.color(0xFFE53935.toInt())), Expression.stop("r", Expression.color(0xFFFF8F00.toInt())))
        st.addLayer(LineLayer("bs-line", "bs-src").withFilter(Expression.eq(Expression.get("z"), Expression.literal(0))).withProperties(
            PropertyFactory.lineColor(bsColor),
            PropertyFactory.lineWidth(Expression.match(Expression.get("k"), Expression.literal(4f),
                Expression.stop("c", Expression.literal(8f)), Expression.stop("r", Expression.literal(6f)))),
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"), PropertyFactory.lineOpacity(0.85f)
        ).apply { minZoom = 8f })
        // Keine Straße gefunden: nur die Luftlinie zwischen den zwei Punkten, gestrichelt
        st.addLayer(LineLayer("bs-dash", "bs-src").withFilter(Expression.eq(Expression.get("z"), Expression.literal(1))).withProperties(
            PropertyFactory.lineColor(bsColor), PropertyFactory.lineWidth(5f),
            PropertyFactory.lineDasharray(arrayOf(1.6f, 1.4f)), PropertyFactory.lineCap("butt"), PropertyFactory.lineOpacity(0.85f)
        ).apply { minZoom = 8f })
        st.addSource(GeoJsonSource("route-src"))
        st.addLayer(LineLayer("route-casing", "route-src").withProperties(
            PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(14f),
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
        ))
        st.addLayer(LineLayer("route-line", "route-src").withProperties(
            PropertyFactory.lineColor(routeColorHex()), PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")
        ))
        for (id in listOf("hits", "pts", "hz", "friends", "me")) {
            st.addSource(GeoJsonSource("$id-src"))
            val layer = SymbolLayer("$id-layer", "$id-src").withProperties(
                PropertyFactory.iconImage(Expression.get("img")),
                PropertyFactory.iconAnchor(Expression.get("anchor")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
            if (id == "me") layer.withProperties(
                PropertyFactory.iconRotate(Expression.get("rot")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
            )
            st.addLayer(layer)
        }
    }

    private fun ensureImage(st: Style, name: String, make: () -> Bitmap) {
        if (addedImages.add(name)) {
            val b = make()
            b.density = resources.displayMetrics.densityDpi
            st.addImage("hud-$name", b) // Präfix: der Kartenstil hat eigene Symbole (z. B. "arrow" für Einbahnstraßen)
        }
    }

    private fun src(id: String): GeoJsonSource? = style?.getSourceAs<GeoJsonSource>(id)

    private fun feat(lat: Double, lon: Double, img: String, anchor: String = "center", rot: Double = 0.0): Feature =
        Feature.fromGeometry(Point.fromLngLat(lon, lat)).also {
            it.addStringProperty("img", "hud-$img"); it.addStringProperty("anchor", anchor); it.addNumberProperty("rot", rot)
        }

    private fun pushAll() {
        pushRoute(); pushPoints(); pushHits(); pushHazards(); pushFriends(); pushMe(); pushBaustellen()
    }

    private fun pushRoute() {
        val s = src("route-src") ?: return
        val r = curRoute
        if (r == null || r.points.size < 2) s.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        else s.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lon, it.lat) })))
    }

    /** Baustellen und Sperrungen als Linien: rot gesperrt, orange eingeschränkt, gelb Arbeiten. */
    private fun pushBaustellen() {
        val s = src("bs-src") ?: return
        if (!Baustellen.enabled(this)) { s.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>())); return }
        val t = Baustellen.now()
        val feats = ArrayList<Feature>()
        for ((i, x) in Baustellen.sites.withIndex()) {
            if (!BaustellenLogic.activeAt(x, t)) continue
            val f = Feature.fromGeometry(LineString.fromLngLats(x.line.map { Point.fromLngLat(it.second, it.first) }))
            f.addStringProperty("k", x.kind); f.addNumberProperty("n", i); f.addNumberProperty("z", if (x.approx) 1 else 0)
            feats += f
        }
        s.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    /** Tipp auf eine Baustellenlinie: Text und Zeitraum zeigen. Gibt true zurück, wenn etwas getroffen wurde. */
    private fun tapBaustelle(ll: LatLng): Boolean {
        val m = map ?: return false
        if (!Baustellen.enabled(this) || m.cameraPosition.zoom < 8.0 || style?.getLayer("bs-line") == null) return false
        val pt = m.projection.toScreenLocation(ll); val r = dp(22).toFloat()
        val f = m.queryRenderedFeatures(RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r), "bs-line", "bs-dash").firstOrNull() ?: return false
        val x = Baustellen.sites.getOrNull(f.getNumberProperty("n")?.toInt() ?: -1) ?: return false
        val until = BaustellenLogic.untilText(x)
        AlertDialog.Builder(this)
            .setTitle(BaustellenLogic.kindName(x.kind, Tx.en) + if (x.road.isNotEmpty()) " · " + x.road else "")
            .setMessage(x.text + if (until.isNotEmpty()) "\n\n" + Tx.t("Bis ", "Until ") + until else "")
            .setPositiveButton("OK", null).show()
        return true
    }

    /** Liegen Sperrungen oder Einschränkungen auf der Route? Hinweistext für die Vorschau (null = nichts). */
    private fun baustellenNote(r: PilotRoute?): String? {
        if (r == null || !Baustellen.enabled(this) || Baustellen.sites.isEmpty()) return null
        val av = if (PilotRouter.avoidedRoute === r) PilotRouter.avoidedCount else 0
        val key = System.identityHashCode(r) * 31 + Baustellen.sites.size + (Baustellen.stand % 100000).toInt() + av * 7919
        if (bsNoteKey == key) return bsNoteVal
        val hits = BaustellenLogic.onRoute(r.points.map { Pair(it.lat, it.lon) }, Baustellen.sites, Baustellen.now())
        val warn = BaustellenLogic.routeNote(hits, Tx.en)
        val pre = when {
            av > 0 -> "✓ " + (if (av == 1) Tx.t("1 Sperrung umfahren", "1 closure avoided") else Tx.t("$av Sperrungen umfahren", "$av closures avoided"))
            av < 0 && warn != null -> Tx.t("Keine Umfahrung gefunden.", "No detour found.")
            else -> null
        }
        bsNoteKey = key; bsNoteVal = listOfNotNull(pre, warn).joinToString("\n").ifEmpty { null }
        return bsNoteVal
    }
    private var bsNoteKey = 0
    private var bsNoteVal: String? = null

    /** Fahne (Ziel), Nummern (Zwischenziele) und, falls gewählt, den Start auf die Karte setzen. */
    private fun pushPoints() {
        val st = style ?: return
        val feats = ArrayList<Feature>()
        pl.start?.let {
            ensureImage(st, "pinS") { pinBitmap("S", 0xFF35C46B.toInt()) }
            feats += feat(it.lat, it.lon, "pinS")
        }
        var nv = 0
        for (v in pl.vias) {
            if (v.shape) continue
            nv++
            ensureImage(st, "pinV$nv") { pinBitmap("$nv", 0xFFFF9F0A.toInt()) }
            feats += feat(v.lat, v.lon, "pinV$nv")
        }
        pl.dest?.let { d ->
            ensureImage(st, "flag") { flagBitmap() }
            feats += feat(d.lat, d.lon, "flag", "bottom")
        }
        src("pts-src")?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    /** Treffer als blaue Nummern auf der Karte (leere Liste entfernt sie). */
    private fun drawHits(hits: List<PlaceSearch.Hit>) {
        hitList = hits
        pushHits()
    }

    private fun pushHits() {
        val st = style ?: return
        val feats = hitList.mapIndexed { i, h ->
            ensureImage(st, "hit${i + 1}") { pinBitmap("${i + 1}", 0xFF4DA3FF.toInt()) }
            feat(h.lat, h.lon, "hit${i + 1}")
        }
        src("hits-src")?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    private fun pushHazards() {
        val st = style ?: return
        val feats = hazardWant.map { (h, lab) ->
            val n = "hz${h.kind}_${lab ?: ""}"
            ensureImage(st, n) { hazardBitmap(h.kind, lab) }
            feat(h.lat, h.lon, n)
        }
        src("hz-src")?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    /** Eigene Position: Pfeil (mit Fahrtrichtung) oder Punkt (ohne). */
    private fun pushMe() {
        val st = style ?: return
        val p = lastShown ?: return
        ensureImage(st, "arrow") { arrowBitmap(0f) }
        ensureImage(st, "dot") { arrowBitmap(null) }
        val h = heading
        src("me-src")?.setGeoJson(FeatureCollection.fromFeatures(listOf(
            feat(p.latitude, p.longitude, if (h == null) "dot" else "arrow", "center", (h ?: 0f).toDouble())
        )))
    }

    private fun showRoute(r: PilotRoute?) {
        curRoute = r
        pushRoute()
    }

    /** Wo der Pfeil sitzt (Pixel in der Karte). Ohne Navigation: feste Anteile. Mit Navigation: Hochformat auf Höhe der Mitte des Stopp-Knopfs,
     *  Querformat mittig zwischen linkem Rand und Beginn der Hinweiskarte, dort 5 mm höher als der feste Anteil. */
    private fun arrowPoint(w: Int, h: Int): Pair<Float, Float> {
        if (!PilotNav.guiding) return Pair(curCx * w, curCy * h)
        return if (landscape) {
            val hudLeft = w - dp(12) - bannerW()
            Pair(hudLeft / 2f, h * NAV_Y_LAND)
        } else {
            val step = dp(48 + 8)
            val fromBottom = dp(12) + dp(8) + step * 2 + dp(48) / 2   // so sitzt der Stopp-Knopf unten rechts (placeButtons)
            Pair(w / 2f, (h - fromBottom).toFloat().coerceIn(h * 0.5f, h * 0.9f))
        }
    }
    private fun mm(v: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_MM, v, resources.displayMetrics)

    /** Randabstände der Karte, damit die Kartenmitte (Kamera-Ziel) dort liegt, wo der Pfeil sitzen soll. */
    private fun padValues(): DoubleArray? {
        val w = mapView.width; val h = mapView.height
        if (w <= 0 || h <= 0) return null
        val (px, py) = arrowPoint(w, h)
        return doubleArrayOf(maxOf(0f, 2f * px - w).toDouble(), maxOf(0f, 2f * py - h).toDouble(), maxOf(0f, w - 2f * px).toDouble(), maxOf(0f, h - 2f * py).toDouble())
    }

    private fun applyPadding() {
        val m = map ?: return
        val v = padValues() ?: run { mapView.post { applyPadding() }; return }
        padKey = "${v[0].toInt()},${v[1].toInt()},${v[2].toInt()},${v[3].toInt()}"
        m.setPadding(v[0].toInt(), v[1].toInt(), v[2].toInt(), v[3].toInt())
    }
    private var padKey = ""
    private var simLed: LedTextView? = null
    private fun padOk(): Boolean {
        val m = map ?: return true
        val p = m.padding
        return p.size >= 4 && padKey == "${p[0].toInt()},${p[1].toInt()},${p[2].toInt()},${p[3].toInt()}"
    }

    private fun moveCam(p: LatLng, bearing: Double?, zoom: Double?, animate: Boolean) {
        val m = map ?: return
        // Sicherung: Rand der Routenvorschau darf die Pfeil-Position (unteres Drittel) nie überschreiben.
        if (!previewShown && !padOk()) applyPadding()
        val b = CameraPosition.Builder(m.cameraPosition).target(p).tilt(if (tilt3d) 55.0 else 0.0)
        // Rand in jede Kamerabewegung mitgeben: sonst bringt die Kamera den alten Rand (z. B. von der Routenvorschau) zurück und der Pfeil sitzt falsch
        padValues()?.let { b.padding(it[0], it[1], it[2], it[3]) }
        if (bearing != null) b.bearing(bearing)
        if (zoom != null) b.zoom(zoom)
        val upd = CameraUpdateFactory.newCameraPosition(b.build())
        if (animate) m.easeCamera(upd, 900) else m.moveCamera(upd)
    }


    /** Gewählte Kartendatei in den App-Speicher kopieren und die Karte neu laden. */
    private fun importMap(uri: Uri) {
        Toast.makeText(this, Tx.t("Kopiere Karte … (bei großen Dateien dauert das)", "Copying map … (takes a while for big files)"), Toast.LENGTH_LONG).show()
        fileExec.execute {
            val ok = try {
                val tmp = File(mapFile().parentFile, "map.tmp")
                contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out, 1 shl 20) } } ?: throw IllegalStateException("leer")
                mapFile().delete()
                tmp.renameTo(mapFile())
            } catch (e: Exception) { false }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    Toast.makeText(this, Tx.t("Karte geladen.", "Map loaded."), Toast.LENGTH_SHORT).show()
                    loadStyle(appliedDark)
                    if (lastPos == null && PilotSession.route == null) mapCenter()?.let { moveCam(it, null, 8.0, false) }
                } else Toast.makeText(this, Tx.t("Kopieren fehlgeschlagen.", "Copy failed."), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------- Anzeige (Overlay) ----------------

    // ---------------- Anzeige (Overlay) ----------------

    private fun box(radius: Int, color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    /** Breite der Hinweiskarte im Querformat: höchstens 215 dp, nie mehr als etwa ein Viertel des Bildschirms. */
    private fun bannerW(): Int = minOf(dp(215), (resources.displayMetrics.widthPixels * 0.27f).toInt())

    /** Schrift verkleinert sich selbst, wenn der Wert nicht in die Zeile passt (nur Querformat). */
    private fun fitText(t: TextView, minSp: Int, maxSp: Int) {
        t.maxLines = 1
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(t, minSp, maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    private fun label(text: String, sp: Float, color: Int = 0xFFFFFFFF.toInt(), bold: Boolean = false, heavy: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false // gleicher Abstand oben und unten
        if (heavy) typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        else if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    /** Bedienknöpfe und Anzeige neu aufbauen (beim Start und beim Drehen des Handys). */
    private fun buildUi() {
        for (v in ui) root.removeView(v)
        infoRow?.let { (it.parent as? ViewGroup)?.removeView(it) }
        ui.clear(); hitViews.clear(); overlayViews.clear(); cards.clear(); texts.clear()
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val baseCx = if (landscape) CENTER_X_LAND else 0.5f
        val baseCy = if (landscape) CENTER_Y_LAND else CENTER_Y
        curCx = baseCx
        curCy = baseCy
        mapView.post { applyPadding() }

        fun add(v: View, lp: FrameLayout.LayoutParams) { root.addView(v, lp); ui.add(v) }
        val bs = if (landscape) 40 else 48
        val step = dp(bs + 8)
        panelBtns.clear()
        fun button(text: String, size: Float, gravity: Int, top: Int, bottom: Int, endExtra: Int = 0, panel: Boolean = true, action: () -> Unit): TextView {
            val b = TextView(this).apply {
                this.text = text
                textSize = size
                this.gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundResource(R.drawable.bg_map_btn_round)
                isClickable = true
                isFocusable = true
                setOnClickListener { action(); if (panel && PilotNav.guiding) bumpPanel() }
            }
            add(b, FrameLayout.LayoutParams(dp(bs), dp(bs), gravity).apply {
                setMargins(dp(12), dp(12) + top, dp(12) + endExtra, dp(12) + bottom)
            })
            hitViews.add(b)
            if (panel) panelBtns.add(b)
            return b
        }
        val tr = Gravity.TOP or Gravity.END
        // Zahnrad = Menü (auch während der Navigation); langer Druck beendet die Navigation
        btnMain = button("", 20f, tr, 0, 0, panel = false) { chooseMenu() }.also { b ->
            setBtnIcon(b, R.drawable.ic_map_menu)
            b.setOnLongClickListener { if (PilotNav.guiding) { confirmStop(); true } else false }
        }
        btnHud = button("●", 18f, tr, step, 0) { showHudDialog() }
        btnNorth = button("", 20f, tr, step * 2, 0) { toggleNorth() }
        setBtnIcon(btnNorth, R.drawable.ic_view_north)
        // Stopp-Knopf (nur bei Navigation): Quadrat wird gezeichnet, damit es genau in der Mitte sitzt
        btnStop = if (landscape) button("", 20f, tr, step * 2, 0, step) { confirmStop() } else button("", 20f, tr, step * 3, 0) { confirmStop() }
        btnStop?.background = android.graphics.drawable.LayerDrawable(arrayOf(
            ContextCompat.getDrawable(this, R.drawable.bg_map_btn_round)!!,
            android.graphics.drawable.InsetDrawable(GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(3).toFloat() }, dp(if (landscape) 14 else 17))
        ))
        // Zentrieren: nur wenn die Karte nicht mehr folgt
        btnFollow = button("", 22f, tr, step * 3, 0) { refollow() }
        btnFollow.contentDescription = Tx.t("Auf meinen Standort zentrieren", "Centre on my location")
        followLook = null
        if (landscape) {
            // Querformat: Zoomtasten als zweite Spalte neben den anderen Tasten
            btnZoomIn = button("+", 24f, tr, 0, 0, step) { zoomManual(+1) }
            btnZoomOut = button("–", 24f, tr, step, 0, step) { zoomManual(-1) }
        } else {
            // Hochformat: abgesetzt weiter unten
            val br = Gravity.BOTTOM or Gravity.END
            btnZoomIn = button("+", 24f, br, 0, dp(198) + step) { zoomManual(+1) }
            btnZoomOut = button("–", 24f, br, 0, dp(198)) { zoomManual(-1) }
        }
        // Suche: oben links nur die Lupe, ein Tipp klappt Suchfeld und Kategorien auf
        btnSearch = button("🔍", 20f, Gravity.TOP or Gravity.START, 0, 0, panel = false) { openSearch() }
        // Zuhause: ein Tipp plant die Route nach Hause, langer Druck ändert oder löscht die Adresse
        btnHome = button("🏠", 18f, Gravity.TOP or Gravity.START, step, 0, panel = false) { goHome() }.also { b ->
            b.setOnLongClickListener { homeMenu(); true }
        }
        // Standort teilen (nur sichtbar, wenn es schon Freunde gibt): orange = Freunde sehen dich
        btnShare = button("📍", 18f, Gravity.TOP or Gravity.START, step * 2, 0, panel = false) { toggleShare() }
        updateShareBtn()

        // Oben: dunkle, leicht durchsichtige Karte mit Abbiegepfeil und Entfernung links, Ankunft und Restweg rechts (Tipp wechselt)
        glassViews.clear()
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val banner = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            visibility = View.GONE
        }
        cards.add(Pair(banner, 24)); glassViews.add(banner)
        val navCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        turnArrow = TurnArrowView(this)
        if (landscape) navCol.addView(turnArrow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        else navCol.addView(turnArrow, LinearLayout.LayoutParams(dp(76), dp(76)))
        val dist = label("", if (landscape) 36f else 34f, heavy = true)
        if (landscape) fitText(dist, 18, 36)
        val then = label("", 14f).apply { visibility = View.GONE }
        tvDist = dist; tvThen = then
        texts.add(dist); texts.add(then)
        if (landscape) navCol.addView(dist, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) else navCol.addView(dist)
        navCol.addView(then)
        val infoCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        fun line() = View(this).apply { setBackgroundColor(0xFF5A5A5F.toInt()) }
        // Ein Feld mit Wert und Beschriftung; rechts ein kleines "›" als Hinweis, dass ein Tipp umschaltet
        fun block(valueSp: Float, onTap: () -> Unit): Triple<View, TextView, TextView> {
            val v = label("", valueSp, bold = landscape, heavy = !landscape)
            if (landscape) fitText(v, 14, valueSp.toInt())
            val l = label("", 12f, 0xFFAAAAAF.toInt())
            texts.add(v)
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            if (landscape) inner.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) else inner.addView(v)
            inner.addView(l)
            val w = FrameLayout(this).apply { isClickable = true; setOnClickListener { onTap() } }
            w.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(14), dp(6), dp(14), dp(6))
            })
            w.addView(label("›", 20f, 0xFF8E8E93.toInt()), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
            hitViews.add(w)
            return Triple(w, v, l)
        }
        val etaB = block(if (landscape) 30f else 30f) {
            optEta = !optEta
            prefs.edit().putBoolean("ov_eta", optEta).apply()
            refreshOverlay()
        }
        val remB = block(if (landscape) 30f else 30f) {
            optRemTime = !optRemTime
            prefs.edit().putBoolean("ov_remtime", optRemTime).apply()
            refreshOverlay()
        }
        tvEta = etaB.second; tvEtaLbl = etaB.third; tvRem = remB.second; tvRemLbl = remB.third
        val bh = if (landscape) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
        val bw = if (landscape) 1f else 0f
        infoCol.addView(etaB.first, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bh, bw))
        infoCol.addView(line(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { setMargins(dp(8), 0, dp(8), 0) })
        infoCol.addView(remB.first, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bh, bw))
        if (landscape) {
            banner.addView(navCol, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f))
            banner.addView(line(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, dp(6), 0, dp(6)) })
            banner.addView(infoCol, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))
        } else {
            banner.addView(navCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            banner.addView(line(), LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            banner.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        hitViews.add(banner)
        bannerBox = banner
        leftCol.addView(banner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (landscape) 0 else ViewGroup.LayoutParams.WRAP_CONTENT, if (landscape) 1f else 0f))
        leftColView = leftCol
        add(leftCol, FrameLayout.LayoutParams(if (landscape) bannerW() else ViewGroup.LayoutParams.MATCH_PARENT, if (landscape) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            if (landscape) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.START).apply {
            setMargins(dp(12), dp(12), dp(12), if (landscape) dp(12) else 0)
        })
        // Zielflagge beim Ankommen (zuerst unsichtbar, showArrived() blendet sie ein)
        val flagIcon = android.widget.ImageView(this).apply { setImageDrawable(FlagDrawable()) }
        val flagText = label(Tx.t("Ziel erreicht", "Destination reached"), 17f, 0xFFFFFFFF.toInt(), bold = true)
        val arrived = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = box(20, 0xF21C1C1E.toInt()); setPadding(dp(14), dp(8), dp(18), dp(8)); visibility = View.GONE
            addView(flagIcon, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) })
            addView(flagText)
        }
        arrivedBar = arrived
        add(arrived, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(14) })
        overlayViews.add(leftCol)

        // Reihe darunter: Hinweis links, rechts das Tempolimit-Schild und daneben der Tempo-Kreis (gleich groß)
        val cs = dp(if (landscape) 68 else 80)
        val ls = (cs * 0.75f).toInt()   // Tempolimit-Schild 25 % kleiner als der Tempo-Kreis
        val info = FrameLayout(this)
        tvHint = label("", 15f, 0xFF000000.toInt(), bold = true).apply {
            background = box(12, 0xFFFF8C00.toInt()); setPadding(dp(12), dp(5), dp(12), dp(5)); visibility = View.GONE
            maxWidth = (resources.displayMetrics.widthPixels - 2 * cs - dp(60)).coerceAtLeast(dp(120))
        }
        info.addView(tvHint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        val circles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvLimit = label("", if (landscape) 18f else 21f, 0xFF000000.toInt(), heavy = true).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()); setStroke(dp(7), 0xFFD00000.toInt()) }
            visibility = View.GONE
        }
        val sBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            visibility = View.GONE
        }
        cards.add(Pair(sBox, 60)); glassViews.add(sBox)
        tvSpeed = label("–", if (landscape) 35f else 40f, heavy = true)
        sBox.addView(tvSpeed)
        val unit = label("km/h", 11f)
        texts.add(unit)
        sBox.addView(unit)
        speedBox = sBox
        circles.addView(sBox, LinearLayout.LayoutParams(cs, cs).apply { rightMargin = dp(10) })
        tvAhead = label("", if (landscape) 13f else 15f, 0xFF000000.toInt(), bold = true).apply {
            background = box(10, 0xFFFFC107.toInt()); setPadding(dp(9), dp(4), dp(9), dp(4)); visibility = View.GONE
        }
        circles.addView(tvAhead, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
        circles.addView(tvLimit, LinearLayout.LayoutParams(ls, ls))
        info.addView(circles, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, cs, Gravity.END or Gravity.CENTER_VERTICAL))
        infoRow = info
        infoDock = -1
        overlayViews.add(info)

        // Aufnahme-Kapsel (wie bei Calimoto): nur ohne Planung/Navigation; bleibt auch bei ausgeblendeter Anzeige sichtbar
        val rec = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 17f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), 0, dp(18), 0); isClickable = true; isFocusable = true
            setOnClickListener { toggleFreeRecording() }
        }
        btnRec = rec
        recLook = null
        // Aufnahme-Kapsel unten rechts: sitzt fest und springt nicht
        add(rec, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(12), dp(32))   // auf Höhe des Tempo-Kreises
        })
        hitViews.add(rec)
        barBox = null; tvBar = null; pbProgress = null; tvChips = null
        placedKey = ""
        mapView.post { dockInfo(PilotNav.guiding) }

        // Routenvorschau (unten): Strecke, Zeit, Punkteliste, Profil, Start
        val sh = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            visibility = View.GONE
            isClickable = true
        }
        cards.add(Pair(sh, 20))
        val handle = FrameLayout(this)
        handle.addView(View(this).apply { background = box(3, 0xFF8E8E93.toInt()) }, FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER))
        var handleY0 = 0f
        handle.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { handleY0 = ev.rawY; true }
                MotionEvent.ACTION_UP -> {
                    val dy = ev.rawY - handleY0
                    sheetLevel = when {
                        dy > dp(24) -> (sheetLevel - 1).coerceAtLeast(0)
                        dy < -dp(24) -> (sheetLevel + 1).coerceAtMost(2)
                        else -> when (sheetLevel) { 0 -> 1; 1 -> 2; else -> 1 }
                    }
                    refreshSheet()
                    refitPreview()
                    true
                }
                else -> true
            }
        }
        sh.addView(handle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply { topMargin = -dp(6) })
        val title = label("", 24f, heavy = true)
        val sub = label("", 15f)
        val note = label("", 14f, 0xFFFFB04A.toInt())
        texts.add(title); texts.add(sub)
        tvSheetTitle = title; tvSheetSub = sub; tvSheetNote = note
        val infoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sheetCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheetCol.addView(title)
        sheetCol.addView(sub)
        infoRow.addView(sheetCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pchip = label("", 14f, bold = true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = box(16, 0xFF3A3A3C.toInt())
            isClickable = true
            setOnClickListener { showProfileMenu(this) }
        }
        profChip = pchip
        infoRow.addView(pchip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        val fchip = label("☆", 20f, 0xFFFFB04A.toInt()).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6)); background = box(16, 0xFF3A3A3C.toInt()); isClickable = true
            setOnClickListener { toggleFav() }
        }
        favChip = fchip
        infoRow.addView(fchip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        sh.addView(infoRow)
        sh.addView(note)
        val pbox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pointsBox = pbox
        val pscroll = object : android.widget.ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(dp(if (landscape) 110 else 170), View.MeasureSpec.AT_MOST))
            }
        }
        pscroll.addView(pbox)
        pointsScroll = pscroll
        sh.addView(pscroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionsRow = actions
        val cancel = label("✕", 22f).apply {
            gravity = Gravity.CENTER; background = box(14, 0xFF3A3A3C.toInt()); isClickable = true
            setOnClickListener { clearAll() }
        }
        val start = label("START ▶", 20f, 0xFF000000.toInt(), heavy = true).apply {
            gravity = Gravity.CENTER; background = box(14, 0xFFFF7A00.toInt()); isClickable = true
            setOnClickListener { startGuidance() }
        }
        btnStart = start
        val saveBtn = android.widget.ImageView(this).apply {
            setImageDrawable(FloppyDrawable()); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14)); background = box(14, 0xFF3A3A3C.toInt()); isClickable = true
            contentDescription = Tx.t("Tour auf dem Server speichern", "Save tour on the server")
            setOnClickListener { saveTourToServer() }
        }
        val simBtn = LedTextView(this).apply {
            text = "SIM"; textSize = 16f; setTextColor(0xFFFFFFFF.toInt()); includeFontPadding = false
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; background = box(14, 0xFF3A3A3C.toInt()); isClickable = true
            setOnClickListener { startSim() }
        }
        simLed = simBtn
        actions.addView(cancel, LinearLayout.LayoutParams(dp(56), dp(52)))
        actions.addView(saveBtn, LinearLayout.LayoutParams(dp(56), dp(52)).apply { leftMargin = dp(10) })
        actions.addView(simBtn, LinearLayout.LayoutParams(dp(64), dp(52)).apply { leftMargin = dp(10) })
        actions.addView(start, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(10) })
        sh.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        sheet = sh
        add(sh, FrameLayout.LayoutParams(
            if (landscape) dp(380) else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START
        ).apply { setMargins(dp(12), 0, dp(12), dp(12)) })
        hitViews.add(sh)

        // Simulation: Tempo, Zeitraffer, Abweichen, Pause (nur während der Simulation)
        val sbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        simChips.clear()
        fun simChip(text: String, onTap: () -> Unit): TextView {
            val c = label(text, 14f, bold = true).apply {
                setPadding(dp(11), dp(7), dp(11), dp(7)); background = box(18, 0xE61C1C1E.toInt()); isClickable = true
                setOnClickListener { onTap(); markSim() }
            }
            sbar.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
            simChips += c
            return c
        }
        simChip("50") { setSimSpeed(50.0, 1.0) }
        simChip("100") { setSimSpeed(100.0, 1.0) }
        simChip("10×") { setSimSpeed(50.0, 10.0) }
        simChip("50×") { setSimSpeed(50.0, 50.0) }
        simChip("↔") { deviate() }
        simChip("⏸") { simPauseToggle() }
        simBar = sbar
        add(sbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, if (landscape) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START).apply {
            setMargins(dp(12), dp(if (landscape) 0 else 300), 0, dp(if (landscape) 12 else 0))
        })
        hitViews.add(sbar)
        markSim()

        // Kleiner roter Punkt, solange das GPS-Log (Werkzeuge) mitschreibt
        gpsLogDot = TextView(this).apply {
            text = "● GPS-Log"
            textSize = 11f
            setTextColor(0xFFFF453A.toInt())
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = box(10, 0x99000000.toInt())
            visibility = View.GONE
            isClickable = false
        }
        root.addView(gpsLogDot, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(if (landscape) 4 else 36), dp(8), 0) })

        // Suche (oben, nur ohne Navigation): Eingabe, Kategorien, Trefferliste
        val sb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val edit = android.widget.EditText(this).apply {
            hint = Tx.t("Suche: Adresse, Ort, Lokal", "Search: address, place, venue")
            setHintTextColor(0xFF9A9AA0.toInt()); setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
            setSingleLine(true)
            setOnFocusChangeListener { _, f ->
                if (f && sheetLevel != 0) { sheetLevel = 0; refreshSheet() }
                if (f && text.isNullOrEmpty()) showHistory()
            }
            setOnClickListener { if (text.isNullOrEmpty()) showHistory() }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) {
                    if (!e.isNullOrEmpty() && historyShown) showResults(null, null)
                }
            })
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            background = box(14, 0xE61C1C1E.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnEditorActionListener { _, _, _ -> doSearch(text.toString().trim(), null); true }
        }
        searchEdit = edit
        row.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val go = label("✕", 20f).apply {
            gravity = Gravity.CENTER; background = box(14, 0xE61C1C1E.toInt()); isClickable = true
            setOnClickListener { closeSearch() }
        }
        row.addView(go, LinearLayout.LayoutParams(dp(48), dp(46)).apply { leftMargin = dp(8) })
        sb.addView(row)
        val catScroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in PlaceSearch.Cat.values()) {
            val chip = label(c.label(), 14f, bold = true).apply {
                setPadding(dp(12), dp(7), dp(12), dp(7)); background = box(16, 0xE61C1C1E.toInt()); isClickable = true
                setOnClickListener { doSearch(null, c) }
            }
            catRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        }
        catScroll.addView(catRow)
        sb.addView(catScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        val rbox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        resultsBox = rbox
        val rscroll = object : android.widget.ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * (if (landscape) 0.45 else 0.40)).toInt(), View.MeasureSpec.AT_MOST))
            }
        }
        rscroll.background = box(14, 0xF01C1C1E.toInt())
        rscroll.visibility = View.GONE
        rscroll.addView(rbox)
        resultsScroll = rscroll
        sb.addView(rscroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        searchBox = sb
        val bs2 = if (landscape) 40 else 48
        val rightSpace2 = dp(12) + dp(bs2) + dp(8) + (if (landscape) dp(bs2 + 8) else 0)
        add(sb, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
        ).apply { setMargins(dp(12), dp(12), rightSpace2, 0) })
        hitViews.add(sb)

        val credit = TextView(this).apply {
            text = "© OpenStreetMap · Protomaps · MapLibre"
            textSize = 10f
            setTextColor(0xFFDDDDDD.toInt())
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setBackgroundColor(0x88000000.toInt())
        }
        add(credit, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        updateButtons()
        applyOverlayVisibility()
        applyColors()
        refreshSheet()
    }

    private fun fg(): Int = 0xFFFFFFFF.toInt()

    /** Farben der Anzeige: dunkelgraue Felder (wie in 0.5.1), weißer Text und weißer Pfeil. */
    private fun applyColors() {
        for ((v, r) in cards) v.background = GradientDrawable().apply {
            setColor(if (v in glassViews) 0xC8101012.toInt() else 0xE61C1C1E.toInt()); cornerRadius = dp(r).toFloat()
        }
        for (t in texts) t.setTextColor(fg())
        turnArrow?.setColor(fg())
        refreshOverlay()
    }

    private fun applyOverlayVisibility() {
        for (v in overlayViews) v.visibility = if (overlayOn) View.VISIBLE else View.INVISIBLE
    }

    private fun toggleOverlay() {
        overlayOn = !overlayOn
        applyOverlayVisibility()
    }

    /** Anzeige mit den aktuellen Werten füllen (Position, Führung, Tempolimit, Uhrzeit). */
    private fun refreshOverlay() {
        val arrow = turnArrow ?: return
        val st = lastState
        val guiding = PilotNav.guiding && st != null
        val next = st?.next
        // Abbiegepfeil
        if (guiding && next != null) {
            bannerBox?.visibility = View.VISIBLE
            arrow.set(next)
            tvDist?.text = MapOverlay.dist(st!!.nextDistM)
            val ins = curRoute?.instructions
            val idx = ins?.indexOf(next) ?: -1
            val nn = if (ins != null && idx >= 0 && idx + 1 < ins.size) ins[idx + 1] else null
            tvThen?.let {
                val lane = if (st.nextDistM <= LaneHint.SHOW_M) LaneHint.text(next.lanes, next.brCmd) else null
                val then = if (optThen && nn != null) Tx.t("danach ", "then ") + MapOverlay.glyph(nn.command, nn.exit) + " " + MapOverlay.dist((nn.atM - next.atM).coerceAtLeast(0)) else null
                val txt = listOfNotNull(lane, then).joinToString("\n")
                if (txt.isNotEmpty()) { it.text = txt; it.visibility = View.VISIBLE } else it.visibility = View.GONE
            }
        } else if (PilotNav.driving && st == null && curRoute?.instructions?.isNotEmpty() == true) {
            // Noch keine GPS-Position: erste Anweisung der Route schon zeigen (Entfernung ab Routenstart)
            val first = curRoute!!.instructions.first()
            bannerBox?.visibility = View.VISIBLE
            arrow.set(first)
            tvDist?.text = MapOverlay.dist(first.atM)
            tvThen?.visibility = View.GONE
        } else bannerBox?.visibility = View.GONE
        // Hinweis
        val m = PilotNav.message
        val hint = hintOverride ?: when {
            PilotNav.driving && st == null -> Tx.t("Warte auf GPS …", "Waiting for GPS …")
            !guiding -> null
            m.startsWith(Tx.t("Neuberechnung fehlgeschlagen", "Recalculation failed")) -> m
            m.isEmpty() && st!!.offRoute && PilotNav.parked -> Tx.t("Pause – abseits der Route", "Paused – off the route")
            m.isNotEmpty() || st!!.offRoute -> Tx.t("Neuberechnung …", "Recalculating …")
            else -> null
        }
        tvHint?.let { if (hint == null) it.visibility = View.GONE else { it.text = hint; it.visibility = View.VISIBLE } }
        // Geschwindigkeit und Tempolimit
        val limit = PilotBus.limit()
        val sheetUp = sheet?.visibility == View.VISIBLE
        speedBox?.visibility = if (haveFix && !sheetUp) View.VISIBLE else View.GONE
        tvSpeed?.let {
            it.text = "${lastKmh.roundToInt()}"
            it.setTextColor(if (MapOverlay.over(lastKmh, limit)) 0xFFFF4A4A.toInt() else fg())
        }
        tvLimit?.let { if (limit > 0 && !sheetUp) { it.text = "$limit"; it.visibility = View.VISIBLE } else it.visibility = View.GONE }
        tvAhead?.let { v ->
            val a = if (sheetUp || !guiding) null else PilotBus.limitAhead()
            if (a != null) { v.text = Tx.t("Gleich ", "Soon ") + a.first; v.visibility = View.VISIBLE } else v.visibility = View.GONE
        }
        // Ankunft und Restweg (oben rechts in der Karte)
        if (guiding) {
            val nowMs = System.currentTimeMillis()
            val zone = java.util.TimeZone.getDefault().getOffset(nowMs).toLong()
            val sec = st!!.remainingSec
            tvEta?.text = if (optEta) MapOverlay.eta(nowMs, sec, zone) else MapOverlay.remaining(sec)
            tvEtaLbl?.text = if (optEta) Tx.t("ANKUNFT", "ARRIVAL") else Tx.t("RESTZEIT", "TIME LEFT")
            tvRem?.text = if (optRemTime) MapOverlay.remaining(sec) else MapOverlay.km(st.remainingM)
            tvRemLbl?.text = if (optRemTime) Tx.t("RESTZEIT", "TIME LEFT") else Tx.t("VERBLEIBEND", "REMAINING")
        }
        dockInfo(PilotNav.guiding)
    }

    /** Tempo-Reihe: bei Navigation unter der Karte (Hochformat) bzw. links der Spalte (Querformat), sonst unten links. */
    private fun dockInfo(top: Boolean) {
        val row = infoRow ?: return
        val want = if (top) 1 else 0
        if (infoDock == want && row.parent != null) return
        infoDock = want
        (row.parent as? ViewGroup)?.removeView(row)
        if (top && !landscape) {
            (leftColView as? LinearLayout)?.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        } else {
            val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                if (top) Gravity.TOP or Gravity.END else Gravity.BOTTOM or Gravity.START)
            if (top) lp.setMargins(0, dp(12), dp(12 + 12) + bannerW(), 0) else lp.setMargins(dp(12), 0, 0, dp(30))   // über der Quellenzeile
            root.addView(row, lp)
        }
    }

    private fun placeBtn(v: View?, g: Int, top: Int, bottom: Int, endExtra: Int) {
        val lp = v?.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = g
        lp.setMargins(dp(12), dp(12) + top, dp(12) + endExtra, dp(12) + bottom)
        v.layoutParams = lp
    }

    /** Knopfplätze: ohne Navigation oben rechts wie bisher; bei Navigation ⚙ unten links, die übrigen unten rechts (die Karte oben ist breit). */
    private fun placeButtons() {
        if (!::btnFollow.isInitialized) return
        val key = "${PilotNav.guiding}$landscape"
        if (key == placedKey) return
        placedKey = key
        val step = dp((if (landscape) 40 else 48) + 8)
        val tr = Gravity.TOP or Gravity.END
        val br = Gravity.BOTTOM or Gravity.END
        if (PilotNav.guiding) {
            val side = if (landscape) bannerW() + dp(12) else 0
            val b0 = dp(8)
            placeBtn(btnMain, Gravity.BOTTOM or Gravity.START, 0, b0, 0)
            placeBtn(btnShare, Gravity.BOTTOM or Gravity.START, 0, b0 + step, 0)   // über dem Menü, nicht unter der Navigationsanzeige
            placeBtn(btnNorth, br, 0, b0, side)
            placeBtn(btnHud, br, 0, b0 + step, side)
            placeBtn(btnStop, br, 0, b0 + step * 2, side)
            placeBtn(btnFollow, br, 0, b0 + step * 3, side)
            placeBtn(btnZoomOut, br, 0, b0 + step * 4, side)
            placeBtn(btnZoomIn, br, 0, b0 + step * 5, side)
        } else {
            placeBtn(btnMain, tr, 0, 0, 0)
            placeBtn(btnShare, Gravity.TOP or Gravity.START, step * 2, 0, 0)   // links unter dem Haus-Knopf
            placeBtn(btnHud, tr, step, 0, 0)
            placeBtn(btnNorth, tr, step * 2, 0, 0)
            placeBtn(btnFollow, tr, step * 3, 0, 0)
            if (landscape) {
                placeBtn(btnStop, tr, step * 2, 0, step)
                placeBtn(btnZoomIn, tr, 0, 0, step)
                placeBtn(btnZoomOut, tr, step, 0, step)
            } else {
                placeBtn(btnStop, tr, step * 3, 0, 0)
                placeBtn(btnZoomIn, br, 0, dp(198) + step, 0)
                placeBtn(btnZoomOut, br, 0, dp(198), 0)
            }
        }
    }

    // ---------------- Menü als Blatt von unten ----------------

    private class MRow(
        val icon: String, val title: String, val value: String? = null, val chevron: Boolean = true,
        val sw: Boolean? = null, val onSw: (() -> Unit)? = null, val onTap: () -> Unit
    )

    private var menuLayer: FrameLayout? = null
    private var menuHeader: LinearLayout? = null
    private var menuHudDot: TextView? = null
    private var menuHudSwitch: com.google.android.material.materialswitch.MaterialSwitch? = null
    private var menuHudSyncing = false
    private var menuContent: LinearLayout? = null

    private fun closeMenu() {
        menuFriendsOpen = false
        menuLayer?.let { root.removeView(it) }
        menuLayer = null; menuHeader = null; menuContent = null; menuHudDot = null; menuHudSwitch = null
    }

    private fun menuIconBox(text: String, size: Int, round: Boolean, color: Int): TextView {
        val res = when (text) { "@route" -> R.drawable.ic_menu_route; "@map" -> R.drawable.ic_menu_map; "@friends" -> R.drawable.ic_menu_friends; "@pin" -> R.drawable.ic_menu_pin; "@eye" -> R.drawable.ic_menu_eye; else -> 0 }
        return label(if (res != 0) "" else text, if (size >= 44) 24f else 19f, color).apply {
            val bg = if (round) GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2C2C2E.toInt()) } else box(12, 0xFF2C2C2E.toInt())
            background = if (res == 0) bg else android.graphics.drawable.LayerDrawable(arrayOf(
                bg, android.graphics.drawable.InsetDrawable(ContextCompat.getDrawable(this@PilotMapLibreActivity, res)!!.mutate(), dp((size - 24) / 2))
            ))
        }
    }

    private fun menuSwitch(on: Boolean): View {
        val f = FrameLayout(this)
        f.background = box(14, if (on) 0xFFFF7A00.toInt() else 0xFF48484A.toInt())
        val knob = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) } }
        f.addView(knob, FrameLayout.LayoutParams(dp(22), dp(22), (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL).apply { setMargins(dp(3), 0, dp(3), 0) })
        f.layoutParams = LinearLayout.LayoutParams(dp(48), dp(28))
        return f
    }

    private fun menuRow(r: MRow, first: Boolean): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!first) col.addView(View(this).apply { setBackgroundColor(0xFF2C2C2E.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(62); isClickable = true
            setOnClickListener { r.onTap() }
        }
        row.addView(menuIconBox(r.icon, 40, false, 0xFFFF7A00.toInt()), LinearLayout.LayoutParams(dp(40), dp(40)))
        row.addView(label(r.title, 18f).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(14) })
        if (r.value != null) row.addView(label(r.value, 15f, 0xFF9A9AA0.toInt()).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        if (r.sw != null) row.addView(menuSwitch(r.sw).apply { if (r.onSw != null) { isClickable = true; setOnClickListener { r.onSw.invoke() } } }, LinearLayout.LayoutParams(dp(48), dp(28)).apply { leftMargin = dp(10) })
        else if (r.chevron) row.addView(label("›", 26f, 0xFF7A7A80.toInt()), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(10) })
        col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return col
    }

    /** Zeigt eine Menüseite; [back] gesetzt = oben links die runde "‹"-Taste. Ein Tipp neben das Blatt schließt es. */
    private fun showMenu(title: String, rows: List<MRow>, back: (() -> Unit)?, hudSwitch: Boolean = false) {
        if (menuLayer == null) {
            val layer = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()); isClickable = true; setOnClickListener { closeMenu() } }
            val r = dp(26).toFloat()
            val sheetBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { setColor(0xFF1C1C1E.toInt()); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
                setPadding(dp(18), dp(10), dp(18), dp(16)); isClickable = true
            }
            sheetBox.addView(View(this).apply { background = box(3, 0xFF555559.toInt()) },
                LinearLayout.LayoutParams(dp(44), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) })
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            sheetBox.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val maxH = (resources.displayMetrics.heightPixels * (if (landscape) 0.78 else 0.62)).toInt()
            val sc = object : android.widget.ScrollView(this) {
                override fun onMeasure(w: Int, h: Int) { super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST)) }
            }
            sc.addView(content)
            sheetBox.addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            layer.addView(sheetBox, FrameLayout.LayoutParams(
                if (landscape) dp(380) else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                if (landscape) Gravity.BOTTOM or Gravity.END else Gravity.BOTTOM
            ).apply { if (landscape) setMargins(0, 0, dp(8), 0) })
            root.addView(layer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            menuLayer = layer; menuHeader = header; menuContent = content
        }
        val h = menuHeader ?: return
        val c = menuContent ?: return
        h.removeAllViews(); c.removeAllViews()
        if (back != null) h.addView(menuIconBox("‹", 44, true, 0xFFFFFFFF.toInt()).apply { isClickable = true; setOnClickListener { back() } },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) })
        h.addView(label(title, 22f, bold = true))
        menuHudDot = null; menuHudSwitch = null
        if (hudSwitch) {
            h.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            val dot = label("●", 16f, 0xFF8E8E93.toInt())
            val lbl = label("HUD", 18f, bold = true)
            val sw = com.google.android.material.materialswitch.MaterialSwitch(this)
            sw.setOnCheckedChangeListener { _, on -> if (!menuHudSyncing) setHudFromMenu(on) }
            h.addView(dot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
            h.addView(lbl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
            h.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            menuHudDot = dot; menuHudSwitch = sw
            updateMenuHud()
        }
        for ((i, r) in rows.withIndex()) c.addView(menuRow(r, i == 0))
    }

    /** Schalter und Punkt im Menü folgen dem Zustand des HUDs (grün verbunden, orange verbindet, rot Fehler, grau aus). */
    private fun updateMenuHud() {
        val dot = menuHudDot ?: return
        val sw = menuHudSwitch ?: return
        val sp = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        val wanted = BridgeService.hudWanted(sp)
        if (sw.isChecked != wanted) { menuHudSyncing = true; sw.isChecked = wanted; menuHudSyncing = false }
        val hud = BridgeBus.hud
        dot.setTextColor(when {
            !wanted || !BridgeService.running -> 0xFF8E8E93.toInt()
            hud.contains("bereit") -> 0xFF35C46B.toInt()
            hud.contains("Fehler", true) || hud.contains("abgelehnt", true) -> 0xFFE53935.toInt()
            else -> 0xFFFF9F0A.toInt()
        })
    }

    /** HUD per Menü-Schalter verbinden oder trennen. Fehlt noch ein gewähltes Gerät oder die Bluetooth-Berechtigung, führt die Übersicht durch die Auswahl. */
    private fun setHudFromMenu(on: Boolean) {
        val sp = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        if (on) {
            val btOk = android.os.Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (sp.getString(BridgeService.KEY_ADDR, null) == null || !btOk) {
                closeMenu()
                MainActivity.pendingHud = true
                startActivity(Intent(this, MainActivity::class.java).putExtra("settings", true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                return
            }
            sp.edit().putBoolean(BridgeService.KEY_HUD_ON, true).apply()
            BridgeService.userStopped = false
            BridgeService.send(this)
        } else {
            sp.edit().putBoolean(BridgeService.KEY_HUD_ON, false).apply()
            if (BridgeService.running) BridgeService.send(this, BridgeService.ACTION_CONFIG)
        }
        menuHudSwitch?.postDelayed({ updateMenuHud() }, 1500)
    }

    // ---------------- Freunde und Live-Standort ----------------

    private val friendHandler = Handler(Looper.getMainLooper())
    private val friendExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var friendTick = 0
    private var friendSig = ""
    private var menuFriendsOpen = false
    private val friendImgs = HashMap<String, String>()

    /** Alle 5 Sekunden: Positionen der Freunde holen (nur wenn einer teilt), alle 30 Sekunden die Freundesliste. */
    private val friendRun = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            val app = applicationContext
            if (ServerProfile.has(app) && RegionSync.serverUrl(app).isNotEmpty()) {
                val n = friendTick++
                try {
                    friendExec.execute {
                        if (n % 6 == 0 || LiveShare.friends == null) FriendsApi.list(app)?.let { LiveShare.friends = it; onFriendList(it) }
                        if (n % 6 == 3) {   // Admin-Meldungen (nur wenn der Server dieses Profil dafür vorsieht), alle 30 Sekunden
                            val adm = try { io.github.th3s1nc.osmandhudbridge.pilot.AdminNotes.checkAndAnnounce(app) } catch (_: Exception) { emptyList() }
                            if (adm.isNotEmpty()) runOnUiThread { if (!isFinishing && !isDestroyed) android.widget.Toast.makeText(this@PilotMapLibreActivity, io.github.th3s1nc.osmandhudbridge.pilot.AdminNotesLogic.notifyText(adm, Tx.en), android.widget.Toast.LENGTH_LONG).show() }
                        }
                        val l = LiveShare.friends
                        val pos = if (l != null && l.anySharing) FriendsApi.positions(app) else emptyList()
                        if (pos != null) LiveShare.positions = pos
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            pushFriends(); updateShareBtn()
                            val sig = LiveShare.friends?.let { friendSigOf(it) } ?: ""
                            if (sig != friendSig) { friendSig = sig; if (menuFriendsOpen && menuLayer != null) menuFriends() }
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) { }
            }
            friendHandler.postDelayed(this, 5_000L)
        }
    }

    private fun friendSigOf(l: FriendsLogic.FriendList): String =
        l.friends.joinToString("|") { "${it.name}:${it.status}:${it.iAllow}:${it.sharing}:${it.spdKmh.toInt() / 10}" } + "#" + l.pendingIn

    private fun friendRefreshNow() { friendHandler.removeCallbacks(friendRun); friendTick = 0; friendHandler.post(friendRun) }

    /** Neue Anfragen kurz einblenden (im Hintergrund meldet sie FriendsAlarm als Benachrichtigung). */
    private fun onFriendList(l: FriendsLogic.FriendList) {
        val app = applicationContext
        val fresh = FriendsLogic.newIncoming(FriendsAlarm.seen(app), l.friends)
        FriendsAlarm.markSeen(app, FriendsLogic.incomingNames(l.friends))
        if (fresh.isNotEmpty()) runOnUiThread {
            if (!isFinishing && !isDestroyed) Toast.makeText(this, fresh.joinToString(", ") + Tx.t(" möchte dein Freund sein.", " wants to be your friend."), Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleShare() {
        val on = !LiveShare.enabled(this)
        LiveShare.setEnabled(this, on)
        updateShareBtn()
        val n = LiveShare.friends?.friends?.count { it.status == "friend" && it.iAllow } ?: 0
        Toast.makeText(this, FriendsLogic.visibleText(n, on, Tx.en), Toast.LENGTH_SHORT).show()
    }

    /** Knopf "Standort teilen": nur sichtbar, wenn es Freunde gibt; orange, solange geteilt wird. */
    private fun updateShareBtn() {
        val b = btnShare ?: return
        val app = applicationContext
        val show = ServerProfile.has(app) && RegionSync.serverUrl(app).isNotEmpty() && LiveShare.friends?.hasFriends == true
        b.visibility = if (show) View.VISIBLE else View.GONE
        val on = LiveShare.enabled(app)
        b.backgroundTintList = if (on) android.content.res.ColorStateList.valueOf(0xFFFF7A00.toInt()) else null
        b.alpha = if (on) 1f else 0.8f
        b.contentDescription = Tx.t("Standort teilen", "Share location")
    }

    /** Freunde auf der Karte: runder Marker mit Anfangsbuchstabe und Schild "Name · Tempo · vor x s". */
    private fun pushFriends() {
        val st = style ?: return
        val feats = ArrayList<Feature>()
        val pos = LiveShare.positions
        for (p in pos) {
            val label = FriendsLogic.label(p, Tx.en)
            val key = "fr_${p.name}_${label.hashCode()}"
            val old = friendImgs[p.name]
            if (old != null && old != key) { try { st.removeImage("hud-$old") } catch (_: Exception) {}; addedImages.remove(old) }
            friendImgs[p.name] = key
            ensureImage(st, key) { friendBitmap(FriendsLogic.initial(p.name), label) }
            feats += feat(p.lat, p.lon, key)
        }
        val names = pos.map { it.name }.toSet()
        for (n in friendImgs.keys.filter { it !in names }) friendImgs.remove(n)?.let { k -> try { st.removeImage("hud-$k") } catch (_: Exception) {}; addedImages.remove(k) }
        src("friends-src")?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    private fun friendBitmap(initial: String, label: String): Bitmap {
        val r = dp(19); val gap = dp(4); val lh = dp(22); val padX = dp(9)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = dp(12).toFloat(); typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val tw = tp.measureText(label)
        val w = maxOf(r * 2 + dp(6), (tw + padX * 2).toInt() + dp(2))
        val h = (r + gap + lh) * 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f; val cy = h / 2f
        c.drawCircle(cx, cy, r.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawCircle(cx, cy, (r - dp(3)).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1FB6C9.toInt() })
        val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = dp(17).toFloat(); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        c.drawText(initial, cx, cy + dp(6), ip)
        val top = cy + r + gap
        val rect = android.graphics.RectF(cx - tw / 2 - padX, top, cx + tw / 2 + padX, top + lh)
        c.drawRoundRect(rect, dp(11).toFloat(), dp(11).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE61C1C1E.toInt() })
        c.drawText(label, cx, top + lh / 2f + dp(4), tp)
        return bmp
    }

    private fun centerOnFriend(name: String) {
        val p = LiveShare.positions.firstOrNull { it.name == name }
        if (p == null) { Toast.makeText(this, Tx.t("$name teilt gerade nicht.", "$name is not sharing right now."), Toast.LENGTH_SHORT).show(); return }
        follow = false; updateButtons()
        moveCam(LatLng(p.lat, p.lon), null, 14.0, true)
    }

    /** Eine Aktion beim Server ausführen, danach die Liste neu holen und das Menü auffrischen. */
    private fun friendAction(okText: ((String) -> String)? = null, after: (() -> Unit)? = null, act: () -> FriendsApi.Out) {
        val app = applicationContext
        friendExec.execute {
            val o = act()
            FriendsApi.list(app)?.let { LiveShare.friends = it; friendSig = friendSigOf(it) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val msg = if (o.ok) okText?.invoke(o.text) else o.text
                if (!msg.isNullOrEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                updateShareBtn()
                if (after != null) after() else if (menuFriendsOpen && menuLayer != null) menuFriends()
            }
        }
    }

    private fun menuFriends() {
        menuFriendsOpen = true
        val app = applicationContext
        val title = Tx.t("Freunde", "Friends")
        if (!ServerProfile.has(app) || RegionSync.serverUrl(app).isEmpty()) {
            showMenu(title, listOf(MRow("⇅", Tx.t("Erst beim Server anmelden", "Sign in to the server first")) { closeMenu(); startActivity(Intent(this, ServerActivity::class.java)) }), { chooseMenu() })
            return
        }
        val l = LiveShare.friends
        val rows = ArrayList<MRow>()
        if (l == null) {
            rows += MRow("…", Tx.t("Lade …", "Loading …"), chevron = false) { }
            friendRefreshNow()
        } else {
            for (f in l.friends.filter { it.status == "incoming" })
                rows += MRow(FriendsLogic.initial(f.name), f.name, value = Tx.t("Anfrage", "Request")) { askFriendRequest(f.name) }
            for (f in l.friends.filter { it.status == "friend" })
                rows += MRow(FriendsLogic.initial(f.name), f.name, value = if (f.sharing) "● ${Math.round(f.spdKmh)} km/h" else null, chevron = false, sw = f.iAllow,
                    onSw = { friendAction { FriendsApi.permit(app, f.name, !f.iAllow) } }) { menuFriend(f.name) }
            for (f in l.friends.filter { it.status == "outgoing" })
                rows += MRow(FriendsLogic.initial(f.name), f.name, value = Tx.t("gesendet", "sent")) { askCancelRequest(f.name) }
        }
        rows += MRow("＋", Tx.t("Freund hinzufügen", "Add friend")) { askAddFriend() }
        rows += MRow("@pin", Tx.t("Standort teilen", "Share location"), chevron = false, sw = LiveShare.enabled(this)) { toggleShare(); menuFriends() }
        showMenu(title, rows, { chooseMenu() })
    }

    private fun menuFriend(name: String) {
        menuFriendsOpen = false
        val f = LiveShare.friends?.friends?.firstOrNull { it.name == name && it.status == "friend" } ?: run { menuFriends(); return }
        val app = applicationContext
        val rows = ArrayList<MRow>()
        if (f.sharing) rows += MRow("◎", Tx.t("Auf der Karte zeigen", "Show on map")) { closeMenu(); centerOnFriend(f.name) }
        rows += MRow("@eye", Tx.t("Darf mich sehen", "May see me"), chevron = false, sw = f.iAllow) {
            friendAction(after = { if (menuLayer != null) menuFriend(name) }) { FriendsApi.permit(app, f.name, !f.iAllow) }
        }
        rows += MRow("✕", Tx.t("Freund entfernen", "Remove friend")) {
            AlertDialog.Builder(this).setTitle(f.name)
                .setMessage(Tx.t("Freundschaft beenden? Ihr seht euch dann nicht mehr.", "End the friendship? You will no longer see each other."))
                .setPositiveButton(Tx.t("Entfernen", "Remove")) { _, _ -> friendAction(after = { if (menuLayer != null) menuFriends() }) { FriendsApi.remove(app, f.name) } }
                .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
        }
        showMenu(f.name + (if (f.sharing) "  ·  ${FriendsLogic.statusText(f, Tx.en)}" else ""), rows, { menuFriends() })
    }

    private fun askFriendRequest(name: String) {
        val app = applicationContext
        AlertDialog.Builder(this).setTitle(name)
            .setMessage(Tx.t("möchte dein Freund sein.", "wants to be your friend."))
            .setPositiveButton(Tx.t("Annehmen", "Accept")) { _, _ -> friendAction({ Tx.t("Ihr seid jetzt befreundet.", "You are friends now.") }) { FriendsApi.respond(app, name, true) } }
            .setNegativeButton(Tx.t("Ablehnen", "Decline")) { _, _ -> friendAction { FriendsApi.respond(app, name, false) } }
            .setNeutralButton(Tx.t("Später", "Later"), null).show()
    }

    private fun askCancelRequest(name: String) {
        val app = applicationContext
        AlertDialog.Builder(this).setTitle(name)
            .setMessage(Tx.t("Anfrage zurückziehen?", "Withdraw the request?"))
            .setPositiveButton(Tx.t("Zurückziehen", "Withdraw")) { _, _ -> friendAction { FriendsApi.remove(app, name) } }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
    }

    private fun askAddFriend() {
        val app = applicationContext
        val e = android.widget.EditText(this).apply { hint = Tx.t("Profilname", "Profile name"); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle(Tx.t("Freund hinzufügen", "Add friend"))
            .setMessage(Tx.t("Profilname deines Freundes auf dem Server eingeben. Er muss die Anfrage annehmen.", "Enter your friend's profile name on the server. They have to accept the request."))
            .setView(e)
            .setPositiveButton(Tx.t("Anfragen", "Request")) { _, _ ->
                val n = FriendsLogic.cleanName(e.text.toString())
                if (n == null) Toast.makeText(this, Tx.t("Name: 2 bis 24 Zeichen.", "Name: 2 to 24 characters."), Toast.LENGTH_SHORT).show()
                else friendAction({ st -> if (st == "befreundet") Tx.t("Ihr seid jetzt befreundet.", "You are friends now.") else Tx.t("Anfrage gesendet.", "Request sent.") }) { FriendsApi.request(app, n) }
            }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
    }

    private fun chooseMenu() {
        menuFriendsOpen = false
        showMenu(Tx.t("Menü", "Menu"), listOf(
            MRow("➤", Tx.t("Navigation", "Navigation")) { menuNav() },
            MRow("▣", Tx.t("Anzeige", "Display")) { menuDisplay() },
            MRow("@map", Tx.t("Karte", "Map")) { menuMap() },
            MRow("@route", Tx.t("Touren", "Tours")) {
                closeMenu()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_page", 5))
            },
            MRow("@friends", Tx.t("Freunde", "Friends"), value = (LiveShare.friends?.pendingIn ?: 0).let { if (it > 0) "● $it" else null }) { menuFriends() },
            MRow("⇅", Tx.t("Server", "Server"), value = ServerProfile.statusLine(this)) { closeMenu(); startActivity(Intent(this, ServerActivity::class.java)) },
            MRow("⚙", Tx.t("App-Einstellungen", "App settings")) { closeMenu(); openAppSettings() },

        ), null, true)
    }

    /** Konflikt beim Einstellungs-Abgleich: Server und dieses Handy haben beide Änderungen. */
    private fun askSettingsConflict(r: SettingsBackup.Result) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Einstellungen unterschiedlich", "Settings differ"))
            .setMessage(Tx.t("Auf dem Server liegen andere Einstellungen (", "The server has different settings (") + SettingsBackup.describe(r) + Tx.t(") als auf diesem Handy. Welche sollen gelten?", ") than this phone. Which should apply?"))
            .setPositiveButton(Tx.t("Vom Server", "From server")) { _, _ -> AutoSync.takeRemote(this, r); recreate() }
            .setNegativeButton(Tx.t("Von diesem Handy", "From this phone")) { _, _ -> fileExec.execute { AutoSync.takeLocal(this) } }
            .setNeutralButton(Tx.t("Später", "Later"), null)
            .show()
    }

    private fun menuMap() {
        showMenu(Tx.t("Karte", "Map"), listOf(
            MRow("⬇", Tx.t("Regionen laden …", "Load regions …")) { closeMenu(); startActivity(Intent(this, RegionsActivity::class.java)) },
            MRow("⇪", Tx.t("GPX-Datei öffnen …", "Open GPX file …")) { closeMenu(); pickGpx.launch(arrayOf("*/*")) },
            MRow("▤", Tx.t("Eigene Karte wählen (.pmtiles) …", "Choose own map (.pmtiles) …")) { closeMenu(); pickMap.launch(arrayOf("*/*")) }
        ), { chooseMenu() })
    }

    private fun menuNav() {
        val many = PilotVoice.many(this)
        val mode = prefs.getString("rec_nav_mode", "ask")
        val modeText = when (mode) { "always" -> Tx.t("immer", "always"); "never" -> Tx.t("nie", "never"); else -> Tx.t("fragen", "ask") }
        showMenu(Tx.t("Navigation", "Navigation"), listOf(
            MRow("⇄", Tx.t("Standard-Profil", "Default profile"), value = (PilotStore.profiles.firstOrNull { it.id == (prefs.getString("default_profile", null) ?: PilotSession.profileId) } ?: PilotStore.profiles.first()).label().substringBefore(" (")) {
                val cur = PilotStore.profiles.indexOfFirst { it.id == (prefs.getString("default_profile", null) ?: PilotSession.profileId) }.coerceAtLeast(0)
                AlertDialog.Builder(this)
                    .setTitle(Tx.t("Standard-Profil für neue Routen", "Default profile for new routes"))
                    .setSingleChoiceItems(PilotStore.profiles.map { it.label() }.toTypedArray(), cur) { d, i ->
                        val id = PilotStore.profiles[i].id
                        prefs.edit().putString("default_profile", id).apply()
                        PilotSession.profileId = id
                        if (pl.dest != null) changed() else profileChips()
                        d.dismiss(); menuNav()
                    }
                    .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
                    .show()
            },
            MRow("⊘", Tx.t("Vermeiden", "Avoid")) { menuAvoid() },
            MRow("↷", Tx.t("Sperrungen umfahren", "Avoid closures"), chevron = false, sw = Baustellen.avoidEnabled(this)) {
                Baustellen.setAvoid(this, !Baustellen.avoidEnabled(this)); menuNav()
            },
            MRow("↱", Tx.t("Danach-Hinweis", "Then-hint"), chevron = false, sw = optThen) {
                optThen = !optThen; prefs.edit().putBoolean("ov_then", optThen).apply(); refreshOverlay(); menuNav()
            },
            MRow("♪", Tx.t("Sprachansagen …", "Voice guidance …")) { closeMenu(); chooseVoice { menuNav() } },
            MRow("≋", Tx.t("Ansage-Häufigkeit", "Announcement frequency"), value = if (many) Tx.t("viele", "many") else Tx.t("normal", "normal")) {
                PilotVoice.setMany(this, !many); menuNav()
            },
            MRow("●", Tx.t("Aufzeichnung bei Navigation", "Recording during navigation"), value = modeText) { menuRecord() }
        ), { chooseMenu() })
    }

    /** Vermeiden: Autobahn, Fähren, Maut, unbefestigte Wege (gilt für alle Profile). */
    private fun menuAvoid() {
        val cur = PilotSession.avoid(this)
        showMenu(Tx.t("Vermeiden", "Avoid"), Avoid.KEYS.map { k ->
            MRow(if (cur.has(k)) "✓" else "○", Avoid.label(k), chevron = false) {
                PilotSession.planner.avoid?.let { a -> Avoid.KEYS.forEach { x -> Avoid.set(this, x, x in a) } }
                PilotSession.planner.dropAvoid()
                Avoid.set(this, k, !cur.has(k))
                if (pl.dest != null && !PilotNav.guiding) changed()
                menuAvoid()
            }
        }, { menuNav() })
    }

    private fun menuRecord() {
        val cur = prefs.getString("rec_nav_mode", "ask")
        fun opt(key: String, text: String) = MRow(if (cur == key) "✓" else "○", text, chevron = false) {
            prefs.edit().putString("rec_nav_mode", key).apply(); menuRecord()
        }
        showMenu(Tx.t("Aufzeichnung", "Recording"), listOf(
            opt("ask", Tx.t("Jedes Mal fragen", "Ask every time")),
            opt("always", Tx.t("Immer aufzeichnen", "Always record")),
            opt("never", Tx.t("Nie aufzeichnen", "Never record"))
        ), { menuNav() })
    }

    private fun menuDisplay() {
        val night = when (nightPref) { NightPref.AUTO -> Tx.t("automatisch", "automatic"); NightPref.DAY -> Tx.t("Tag", "day"); NightPref.NIGHT -> Tx.t("Nacht", "night") }
        fun sw(icon: String, title: String, cur: Boolean, key: String, set: (Boolean) -> Unit) = MRow(icon, title, chevron = false, sw = cur) {
            prefs.edit().putBoolean(key, !cur).apply()
            set(!cur)
            lastHazardAt = 0L; updateHazards(); refreshOverlay()
            menuDisplay()
        }
        val calmOn = prefs.getBoolean("map_calm", false)
        val theme = prefs.getString("map_theme", "standard") ?: "standard"
        val themeKeys = arrayOf("standard", "vivid", "contrast")
        val themeNames = arrayOf(Tx.t("Standard", "Standard"), Tx.t("Kräftig", "Vivid"), Tx.t("Kontrast", "High contrast"))
        val b3dOn = prefs.getBoolean("map_3d", false)
        showMenu(Tx.t("Anzeige", "Display"), listOf(
            MRow("☾", Tx.t("Nachtmodus", "Night mode"), value = night) { toggleNight(); menuDisplay() },
            MRow("▤", Tx.t("Kartenstil", "Map style"), value = themeNames[themeKeys.indexOf(theme).coerceAtLeast(0)]) {
                AlertDialog.Builder(this)
                    .setTitle(Tx.t("Kartenstil", "Map style"))
                    .setSingleChoiceItems(themeNames, themeKeys.indexOf(theme).coerceAtLeast(0)) { d, i ->
                        prefs.edit().putString("map_theme", themeKeys[i]).apply(); loadStyle(appliedDark); d.dismiss(); menuDisplay()
                    }
                    .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
                    .show()
            },
            sw("≋", Tx.t("Ruhig (weniger Beschriftung)", "Calm (fewer labels)"), calmOn, "map_calm") { loadStyle(appliedDark) },
            MRow("⌂", Tx.t("3D-Gebäude (ab Zoom 15)", "3D buildings (zoom 15+)"), chevron = false, sw = b3dOn) {
                prefs.edit().putBoolean("map_3d", !b3dOn).apply()
                if (!b3dOn && !tilt3d) { headUp = true; tilt3d = true; manualRot = false; saveView(); refollow() }   // einschalten: Karte gleich neigen
                loadStyle(appliedDark); updateButtons(); menuDisplay()
            },
            sw("⌖", Tx.t("Zoom vor Abbiegungen", "Zoom in before turns"), prefs.getBoolean("turn_zoom", true), "turn_zoom") { },
            MRow("⌕", Tx.t("Zoom bei Fahrt", "Zoom while driving"),
                value = when (prefs.getInt("drive_zoom", 1)) { 0 -> Tx.t("weit", "far"); 2 -> Tx.t("nah", "close"); else -> Tx.t("normal", "normal") }) {
                prefs.edit().putInt("drive_zoom", (prefs.getInt("drive_zoom", 1) + 1) % 3).apply()
                lastZoomChange = 0L
                menuDisplay()
            },
            sw("◧", Tx.t("Warnsymbole auf der Karte", "Warning symbols on the map"), optHazards, "ov_hazards") { optHazards = it },
            MRow("⚠", Tx.t("Baustellen und Sperrungen", "Roadworks and closures"), chevron = false, sw = Baustellen.enabled(this)) {
                Baustellen.setEnabled(this, !Baustellen.enabled(this)); pushBaustellen(); refreshSheet(); menuDisplay()
            },
        ), { chooseMenu() })
    }

    /** App-Einstellungen (HUD, Ansagen, …); die Navigation läuft im Hintergrund weiter. */
    private fun openAppSettings() {
        startActivity(Intent(this, MainActivity::class.java).putExtra("settings", true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /** Einstellungen der Sprachansagen: an/aus, Stimme, Lautstärke, Tempo, Probehören. */
    private fun chooseVoice(parent: (() -> Unit)? = null) {
        PilotVoice.ensure(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(0)) }
        val cb = android.widget.CheckBox(this).apply {
            text = Tx.t("Sprachansagen", "Voice guidance"); isChecked = PilotVoice.enabled(this@PilotMapLibreActivity)
            setOnCheckedChangeListener { _, on -> PilotVoice.setEnabled(this@PilotMapLibreActivity, on) }
        }
        col.addView(cb)
        fun slider(title: String, min: Int, max: Int, value: Int, onSet: (Int) -> Unit) {
            col.addView(TextView(this).apply { text = title; setPadding(0, dp(12), 0, 0) })
            col.addView(android.widget.SeekBar(this).apply {
                this.max = max - min
                progress = (value - min).coerceIn(0, max - min)
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onSet(p + min) }
                    override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { PilotVoice.test(this@PilotMapLibreActivity) }
                })
            })
        }
        slider(Tx.t("Lautstärke (im Verhältnis zur Handy-Lautstärke)", "Volume (relative to the phone volume)"), 10, 100, (PilotVoice.volume(this) * 100).toInt()) {
            PilotVoice.setVolume(this, it / 100f)
        }
        slider(Tx.t("Sprechtempo", "Speaking speed"), 60, 150, (PilotVoice.rate(this) * 100).toInt()) {
            PilotVoice.setRate(this, it / 100f)
        }
        fun button(text: String, action: () -> Unit) {
            col.addView(android.widget.Button(this).apply { this.text = text; setOnClickListener { action() } },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        fun boostText() = Tx.t("Verstärkung: ", "Boost: ") + when (PilotVoice.boost(this)) { 1 -> Tx.t("laut", "loud"); 2 -> Tx.t("sehr laut", "very loud"); else -> Tx.t("aus", "off") }
        val bBtn = android.widget.Button(this).apply {
            text = boostText()
            setOnClickListener {
                PilotVoice.setBoost(this@PilotMapLibreActivity, (PilotVoice.boost(this@PilotMapLibreActivity) + 1) % 3)
                text = boostText()
                PilotVoice.test(this@PilotMapLibreActivity)
            }
        }
        col.addView(bBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        button(Tx.t("Stimme wählen …", "Choose voice …")) { chooseVoiceName() }
        button(Tx.t("Test", "Test")) { PilotVoice.test(this) }
        button(Tx.t("Weitere Stimmen laden …", "Get more voices …")) {
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            catch (_: Exception) { Toast.makeText(this, Tx.t("In den Android-Einstellungen unter Sprachausgabe.", "In Android settings under text-to-speech."), Toast.LENGTH_LONG).show() }
        }
        if (PilotVoice.missing) col.addView(TextView(this).apply {
            text = Tx.t("Keine passende Stimme installiert. Bitte unter \"Weitere Stimmen laden\" eine deutsche Stimme installieren.", "No matching voice installed. Please install one under \"Get more voices\".")
            setPadding(0, dp(10), 0, 0)
        })
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Sprachansagen", "Voice guidance"))
            .setView(android.widget.ScrollView(this).apply { addView(col) })
            .setPositiveButton("OK", null)
            .setOnDismissListener { if (!isFinishing) parent?.invoke() }
            .show()
    }

    private fun chooseVoiceName() {
        val vs = PilotVoice.voices()
        if (vs.isEmpty()) {
            Toast.makeText(this, Tx.t("Keine Stimme gefunden. Unter \"Weitere Stimmen laden\" eine installieren.", "No voice found. Install one under \"Get more voices\"."), Toast.LENGTH_LONG).show()
            return
        }
        val cur = vs.indexOfFirst { it.name == PilotVoice.voiceName(this) }.coerceAtLeast(0)
        val names = vs.mapIndexed { i, v -> Tx.t("Stimme ", "Voice ") + (i + 1) + " (" + v.locale.displayCountry + ", " + v.name + ")" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Stimme", "Voice"))
            .setSingleChoiceItems(names, cur) { d, which ->
                PilotVoice.setVoiceName(this, vs[which].name)
                PilotVoice.test(this)
                d.dismiss()
            }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
            .show()
    }


    // ---------------- Warnsymbole auf der Karte ----------------

    private fun hazardBitmap(kind: Int, label: String?): Bitmap {
        val s = dp(32)
        val lab = if (label != null) dp(18) else 0
        val w = if (label != null) dp(64) else s
        val bmp = Bitmap.createBitmap(w, s + lab, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ox = (w - s) / 2f
        fun x(f: Float) = ox + s * f
        fun y(f: Float) = s * f
        val red = 0xFFD00000.toInt()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        when (kind) {
            HazardKind.CAMERA, HazardKind.RED_LIGHT -> {
                fill.color = if (kind == HazardKind.CAMERA) red else 0xFF222222.toInt()
                c.drawCircle(x(0.5f), y(0.5f), s * 0.46f, fill)
                line.color = Color.WHITE; line.strokeWidth = dp(2).toFloat()
                c.drawCircle(x(0.5f), y(0.5f), s * 0.46f, line)
                if (kind == HazardKind.CAMERA) {
                    fill.color = Color.WHITE
                    c.drawRoundRect(RectF(x(0.22f), y(0.36f), x(0.78f), y(0.70f)), dp(3).toFloat(), dp(3).toFloat(), fill)
                    c.drawRect(RectF(x(0.38f), y(0.27f), x(0.62f), y(0.36f)), fill)
                    fill.color = red
                    c.drawCircle(x(0.5f), y(0.53f), s * 0.09f, fill)
                } else {
                    for ((i, col) in intArrayOf(0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt()).withIndex()) {
                        fill.color = col
                        c.drawCircle(x(0.5f), y(0.28f + 0.22f * i), s * 0.08f, fill)
                    }
                }
            }
            else -> {
                val tri = Path().apply {
                    moveTo(x(0.5f), y(0.06f)); lineTo(x(0.95f), y(0.90f)); lineTo(x(0.05f), y(0.90f)); close()
                }
                fill.color = Color.WHITE
                c.drawPath(tri, fill)
                line.color = red; line.strokeWidth = dp(3).toFloat()
                c.drawPath(tri, line)
                fill.color = Color.BLACK
                line.color = Color.BLACK; line.strokeWidth = dp(2).toFloat()
                when (kind) {
                    HazardKind.LEVEL_CROSSING -> {
                        c.drawLine(x(0.36f), y(0.50f), x(0.64f), y(0.78f), line)
                        c.drawLine(x(0.64f), y(0.50f), x(0.36f), y(0.78f), line)
                    }
                    HazardKind.ZEBRA -> for (f in floatArrayOf(0.37f, 0.5f, 0.63f)) c.drawRect(RectF(x(f - 0.035f), y(0.50f), x(f + 0.035f), y(0.78f)), fill)
                    else -> {
                        val hump = Path().apply { moveTo(x(0.26f), y(0.78f)); quadTo(x(0.5f), y(0.36f), x(0.74f), y(0.78f)); close() }
                        c.drawPath(hump, fill)
                    }
                }
            }
        }
        if (label != null) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE61C1C1E.toInt() }
            c.drawRoundRect(RectF(0f, s + dp(2).toFloat(), w.toFloat(), (s + lab).toFloat()), dp(6).toFloat(), dp(6).toFloat(), bg)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(12).toFloat(); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            c.drawText(label, w / 2f, s + lab - dp(4).toFloat(), tp)
        }
        return bmp
    }

    /** Warnsymbole entlang der Route (oder vor dir) zeigen; nur wenn sich etwas ändert, wird die Ebene neu gefüllt. */
    private fun updateHazards() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHazardAt < 2000) return
        lastHazardAt = now
        val p = lastPos
        if (!optHazards || p == null) {
            if (hazardWant.isNotEmpty()) { hazardWant = emptyList(); hazardKey = ""; pushHazards() }
            return
        }
        val guiding = PilotNav.guiding
        val sel = MapHazards.select(
            PilotBus.freshHazards(), p.latitude, p.longitude, heading?.toDouble(),
            if (guiding) curRoute else null, lastState?.progressM ?: 0
        )
        val want = ArrayList<Pair<Hazard, String?>>()
        for ((i, sh) in sel.withIndex()) want += Pair(sh.hazard, if (i == 0) MapHazards.label(sh.distM) else null)
        val key = want.joinToString("|") { "${it.first.id}:${it.first.kind}:${it.second ?: ""}" }
        if (key != hazardKey) {
            hazardKey = key
            hazardWant = want
            pushHazards()
        }
    }

    // ---------------- Nachtmodus ----------------

    /** Ort für die Sonnenrechnung: letzte Position, sonst Routenanfang, sonst Mitteleuropa. */
    private fun sunPlace(): LatLng =
        lastPos ?: PilotSession.route?.points?.firstOrNull()?.let { LatLng(it.lat, it.lon) } ?: LatLng(50.0, 8.7)

    private fun isDarkNow(): Boolean {
        val p = sunPlace()
        val sunDark = Sun.isDark(p.latitude, p.longitude, System.currentTimeMillis())
        return MapNight.dark(nightPref, sunDark, false)
    }

    /** Alle Sekunde: Abend und Tunnel prüfen und den Kartenstil bei Bedarf wechseln. */
    private fun evaluateTheme() {
        val now = SystemClock.elapsedRealtime()
        val sim = PilotNav.simulated && PilotNav.guiding
        val fixAge = if (sim || !haveFix) 0L else now - lastFixAt
        val tunnel = tunnelWatch.update(now, fixAge, lastKmh, !sim && PilotBus.roadTunnel())
        val p = sunPlace()
        val dark = MapNight.dark(nightPref, Sun.isDark(p.latitude, p.longitude, System.currentTimeMillis()), tunnel)
        if (dark != appliedDark && map != null) loadStyle(dark)
    }

    private fun toggleNight() {
        nightPref = nightPref.next()
        prefs.edit().putString("map_night", nightPref.name).apply()
        updateButtons()
        Toast.makeText(
            this,
            when (nightPref) {
                NightPref.AUTO -> Tx.t("Nachtmodus: automatisch (Abend und Tunnel)", "Night mode: automatic (evening and tunnels)")
                NightPref.DAY -> Tx.t("Immer hell", "Always light")
                NightPref.NIGHT -> Tx.t("Immer dunkel", "Always dark")
            },
            Toast.LENGTH_SHORT
        ).show()
        evaluateTheme()
    }

    // ---------------- Position ----------------

    /** Pfeil für die Position. heading = null: nur ein Punkt. Der Pfeil zeigt nach oben, außer bei "Norden oben". */
    private fun arrowBitmap(heading: Float?): Bitmap {
        val s = dp(64)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        val cy = s / 2f
        // Kern schwarz, Rand in der Routenfarbe (tags Orange, nachts Magenta)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); style = Paint.Style.FILL }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = routeColorInt(); style = Paint.Style.STROKE; strokeWidth = dp(4).toFloat(); strokeJoin = Paint.Join.ROUND }
        if (heading == null) {
            c.drawCircle(cx, cy, s * 0.22f, edge)
            c.drawCircle(cx, cy, s * 0.22f, fill)
        } else {
            c.save()
            c.rotate(heading, cx, cy)
            val p = Path().apply {
                moveTo(cx, cy - s * 0.40f)
                lineTo(cx + s * 0.28f, cy + s * 0.34f)
                lineTo(cx, cy + s * 0.18f)
                lineTo(cx - s * 0.28f, cy + s * 0.34f)
                close()
            }
            c.drawPath(p, edge)
            c.drawPath(p, fill)
            c.restore()
        }
        return bmp
    }

    /** Beruhigt Pfeil und Richtung (Stillstand festhalten, Richtung erst ab 10 km/h, Sprünge bestätigen). */
    private val arrowFilter = io.github.th3s1nc.osmandhudbridge.pilot.ArrowFilter()

    private fun onPosition(lat: Double, lon: Double, hdg: Float?, kmh: Float, rawBearing: Float? = null, accuracyM: Float? = null, logIt: Boolean = false, estimated: Boolean = false) {
        if (!PilotNav.simulated && !estimated) LiveShare.onFix(this, lat, lon, kmh, hdg)   // Simulationen und geschätzte Positionen (Tunnel) werden nie geteilt
        if (hdg != null) heading = hdg
        lastKmh = kmh
        val p = LatLng(lat, lon)
        lastPos = p
        haveFix = true
        if (!estimated) { lastFixAt = SystemClock.elapsedRealtime(); saveLastPos(lat, lon) }
        if (pl.dest != null && !planning && PilotSession.route == null && planMsg == waitGps) recalc()
        refreshOverlay()
        // Beim Navigieren sitzt der Pfeil auf der Route, solange man höchstens 30 m davon entfernt ist
        var snapped: LatLng? = null
        if (PilotNav.guiding) PilotSession.route?.let { r -> RouteSnap.snap(r.points, lat, lon, 40.0)?.let { snapped = LatLng(it.lat, it.lon) } }
        else snapped = snapToRoad(lat, lon, hdg ?: heading, kmh)
        val shown: LatLng = if (PilotNav.simulated) (snapped ?: p) else {
            val sp = snapped
            val last = lastShown
            val r = arrowFilter.position(lat, lon, if (sp != null) Pair(sp.latitude, sp.longitude) else null, if (last != null) Pair(last.latitude, last.longitude) else null, kmh, accuracyM)
            LatLng(r.lat, r.lon)
        }
        lastShown = shown
        if (logIt) io.github.th3s1nc.osmandhudbridge.pilot.GpsLog.fix(
            this, if (estimated) "tunnel" else if (PilotNav.guiding) "nav" else "frei", lat, lon, accuracyM, kmh, rawBearing, arrowFilter.heading, shown.latitude, shown.longitude,
            snapped != null, arrowFilter.isStill, PilotNav.lastState?.lateralM
        )
        pushMe()
        if (follow) {
            val bearing = if (headUp && heading != null) heading!!.toDouble() else null
            moveCam(shown, bearing, if (autoZoom) wantZoom(kmh) else null, true)
        }
    }

    private var lastSaveAt = 0L
    /** Letzte echte Position nur auf dem Handy merken (für den Start der Karte, bevor GPS einen Treffer hat). */
    private fun saveLastPos(lat: Double, lon: Double) {
        val now = SystemClock.elapsedRealtime()
        if (io.github.th3s1nc.osmandhudbridge.pilot.SimGuard.ignoreGps(PilotNav.simulated, PilotNav.guiding)) return
        if (now - lastSaveAt < 15_000L) return
        lastSaveAt = now
        prefs.edit().putString("last_lat", lat.toString()).putString("last_lon", lon.toString()).apply()
    }

    private var gpsHintShown = false
    /**
     * Beim Öffnen sofort auf die letzte bekannte Position zentrieren (von Android, sonst die gemerkte), statt auf den ersten
     * GPS-Treffer zu warten. Ohne jede Position nach 4 Sekunden der Hinweis "GPS wird gesucht".
     */
    private fun centerOnLastKnown() {
        if (haveFix || lastPos != null || PilotNav.guiding || PilotSession.route != null) return
        if (map != null && follow) {
            var best: Location? = null
            val lm = locMgr
            if (lm != null) for (pv in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                try {
                    val l = lm.getLastKnownLocation(pv)
                    if (l != null && (best == null || l.time > best.time)) best = l
                } catch (_: Exception) {}
            }
            var p: LatLng? = best?.let { LatLng(it.latitude, it.longitude) }
            if (p == null) {
                val la = prefs.getString("last_lat", null)?.toDoubleOrNull()
                val lo = prefs.getString("last_lon", null)?.toDoubleOrNull()
                if (la != null && lo != null) p = LatLng(la, lo)
            }
            if (p != null) moveCam(p, null, 15.5, false)
        }
        if (!gpsHintShown) {
            gpsHintShown = true
            ticker.postDelayed({
                if (!haveFix && !isFinishing && !isDestroyed) Toast.makeText(this, Tx.t("GPS wird gesucht …", "Searching for GPS …"), Toast.LENGTH_LONG).show()
            }, 4_000L)
        }
    }

    private var roadIds: Array<String>? = null

    /** Straßenebenen der Karte (ohne Beschriftung, Rand, Bahn …), für das Einrasten beim freien Fahren. */
    private fun roadLayerIds(): Array<String> {
        roadIds?.let { return it }
        val st = style ?: return emptyArray()
        // "other" = Rad-, Fuß- und sonstige Wege (kind path/other): der Pfeil soll nicht auf einen Radweg neben der Straße springen
        val skip = listOf("casing", "labels", "shields", "pier", "runway", "taxiway", "rail", "oneway", "other")
        val ids = try {
            st.layers.map { it.id }.filter { id -> id.startsWith("roads_") && skip.none { id.contains(it) } }.toTypedArray()
        } catch (_: Exception) { emptyArray() }
        roadIds = ids
        return ids
    }

    /**
     * Freies Fahren: Pfeil auf die nächste Straße der Karte setzen, wenn eine in Fahrtrichtung höchstens 28 m entfernt ist (ab 3 km/h, mit der gemerkten Richtung).
     * Gibt null zurück (Pfeil bleibt auf der GPS-Position), wenn keine passt, im Stand oder ohne Karte.
     */
    private fun snapToRoad(lat: Double, lon: Double, hdg: Float?, kmh: Float): LatLng? {
        val m = map ?: return null
        if (kmh < io.github.th3s1nc.osmandhudbridge.pilot.ArrowFilter.SNAP_MIN_KMH || hdg == null) return null
        val ids = roadLayerIds()
        if (ids.isEmpty()) return null
        return try {
            val pt = m.projection.toScreenLocation(LatLng(lat, lon))
            val r = dp(40).toFloat()
            val feats = m.queryRenderedFeatures(RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r), *ids)
            val kLat = 111_320.0
            val kLon = 111_320.0 * Math.cos(Math.toRadians(lat))
            var best = Double.MAX_VALUE
            var bLat = 0.0
            var bLon = 0.0
            for (f in feats) {
                val g = f.geometry()
                val lines: List<LineString> = when (g) {
                    is LineString -> listOf(g)
                    is MultiLineString -> g.lineStrings()
                    else -> continue
                }
                for (ln in lines) {
                    val c = ln.coordinates()
                    for (i in 0 until c.size - 1) {
                        val ax = (c[i].longitude() - lon) * kLon; val ay = (c[i].latitude() - lat) * kLat
                        val bx = (c[i + 1].longitude() - lon) * kLon; val by = (c[i + 1].latitude() - lat) * kLat
                        val dx = bx - ax; val dy = by - ay
                        val len2 = dx * dx + dy * dy
                        if (len2 <= 0.0) continue
                        // Richtung der Straße (in beide Richtungen) muss ungefähr zur Fahrtrichtung passen
                        val segBrg = (Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0
                        var diff = Math.abs(segBrg - hdg) % 180.0
                        if (diff > 90.0) diff = 180.0 - diff
                        if (diff > 40.0) continue
                        val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                        val px = ax + t * dx; val py = ay + t * dy
                        val d = Math.sqrt(px * px + py * py)
                        if (d < best) { best = d; bLat = lat + py / kLat; bLon = lon + px / kLon }
                    }
                }
            }
            if (best <= 28.0) LatLng(bLat, bLon) else null
        } catch (_: Exception) { null }
    }

    /** Zoom nach Tempo (schneller = weiter weg); höchstens alle 8 Sekunden ändern. */
    private fun wantZoom(kmh: Float): Double? {
        val m = map ?: return null
        // Kurz vor einer Abbiegung, Ausfahrt oder Kehre näher heran (mehrspurig noch etwas näher)
        val gs = lastState
        val nx = gs?.next
        if (prefs.getBoolean("turn_zoom", true) && PilotNav.guiding && gs != null && nx != null && gs.nextDistM in 0..220 && LaneHint.zoomsIn(nx.brCmd)) {
            val want = LaneHint.zoomLevel(nx.brCmd, nx.lanes)
            val t = System.currentTimeMillis()
            if (abs(want - m.cameraPosition.zoom) > 0.3 && t - lastZoomChange > 2_500L) { lastZoomChange = t; return want }
            return null
        }
        // Stufe aus dem Menü: 0 weit (wie früher), 1 normal, 2 nah
        val extra = when (prefs.getInt("drive_zoom", 1)) { 0 -> -1.0; 2 -> 0.6; else -> 0.0 }
        val target = extra + when {
            kmh < 25 -> 17.5
            kmh < 60 -> 16.5
            kmh < 100 -> 15.5
            else -> 14.5
        }
        val now = System.currentTimeMillis()
        if (abs(target - m.cameraPosition.zoom) > 0.4 && now - lastZoomChange > 8_000L) {
            lastZoomChange = now
            return target
        }
        return null
    }

    private fun zoomManual(d: Int) {
        autoZoom = false
        map?.animateCamera(CameraUpdateFactory.zoomBy(d.toDouble()))
    }

    private fun refollow() {
        follow = true
        autoZoom = true
        applyPadding()
        (lastShown ?: lastPos)?.let { moveCam(it, if (headUp && heading != null) heading!!.toDouble() else null, null, true) }
        updateButtons()
    }

    /** Karte: Norden oben (N) → Fahrtrichtung 2D (↑) → Fahrtrichtung geneigt (3D) → wieder N. */
    private fun saveView() {
        prefs.edit().putString("view_mode", if (!headUp) "n" else if (tilt3d) "3d" else "up").apply()
    }

    private fun toggleNorth() {
        val m = map ?: return
        if (manualRot) {
            manualRot = false; headUp = false; tilt3d = false
        } else if (!headUp) {
            headUp = true; tilt3d = false
        } else if (!tilt3d) {
            tilt3d = true
        } else {
            headUp = false; tilt3d = false
        }
        saveView()
        val pv = padValues()
        fun cam() = CameraPosition.Builder(m.cameraPosition).also { b -> pv?.let { b.padding(it[0], it[1], it[2], it[3]) } }
        if (!headUp) m.easeCamera(CameraUpdateFactory.newCameraPosition(cam().bearing(0.0).tilt(0.0).build()))
        else m.easeCamera(CameraUpdateFactory.newCameraPosition(cam().tilt(if (tilt3d) 55.0 else 0.0).build()))
        updateButtons()
        lastPos?.let { onPosition(it.latitude, it.longitude, heading, lastKmh) }
    }

    /** Während der Navigation sind die Knöpfe zugeklappt; ein Tipp auf die Karte klappt sie für 8 Sekunden auf. */
    private fun applyPanel() {
        val open = !PilotNav.guiding || panelShown
        placeButtons()
        for (v in panelBtns) {
            if (v === btnStop && !PilotNav.guiding) { v.visibility = View.GONE; continue }
            if (v === btnNorth) { v.visibility = View.VISIBLE; continue } // Ansicht (N/↑/3D) bleibt immer erreichbar
            v.visibility = if (open) View.VISIBLE else View.GONE
        }
        // Zentrieren nur, wenn die Karte nicht folgt; während der Navigation unter dem Stopp-Knopf
        if (!::btnFollow.isInitialized) return
        btnFollow.visibility = if (open) View.VISIBLE else View.GONE
        updateFollowLook()
    }

    private var followLook: Boolean? = null
    /** Standort-Knopf: dunkel wie die anderen; nur das Zielkreuz ist orange, solange die Karte folgt, und grau, wenn man sie verschoben hat. */
    private fun updateFollowLook() {
        if (!::btnFollow.isInitialized || followLook == follow) return
        followLook = follow
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE61C1C1E.toInt())
            setStroke(dp(1), 0x55FFFFFF)
        }
        val cross = CrosshairDrawable(if (follow) 0xFFFF7A00.toInt() else 0xFF8E8E93.toInt())
        btnFollow.background = android.graphics.drawable.LayerDrawable(arrayOf(ring, android.graphics.drawable.InsetDrawable(cross, dp(11))))
    }

    private fun openSearch() {
        searchOpen = true
        refreshSheet()
        searchEdit?.let { e ->
            e.requestFocus()
            try { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(e, 0) } catch (_: Exception) {}
        }
    }

    /** Suche zuklappen: Tastatur und Liste weg, die Treffer bleiben auf der Karte. */
    private fun closeSearch() {
        if (homePick) mapView.postDelayed({ homePick = false }, 500)
        searchOpen = false
        historyShown = false
        hideKeyboard()
        resultsScroll?.visibility = View.GONE
        refreshSheet()
    }

    private fun bumpPanel() {
        mapView.removeCallbacks(panelHide)
        mapView.postDelayed(panelHide, 8000)
    }

    /** Zeichnet ein Symbol mittig in einen runden Kartenknopf (statt eines Textzeichens). */
    private fun setBtnIcon(b: TextView?, res: Int) {
        b ?: return
        val icon = ContextCompat.getDrawable(this, res)?.mutate() ?: return
        val size = if (landscape) 40 else 48
        val inset = dp((size - 22) / 2)   // Symbol 22 dp, rundum gleicher Abstand = genau mittig
        b.text = ""
        b.setCompoundDrawables(null, null, null, null)
        b.background = android.graphics.drawable.LayerDrawable(arrayOf(
            ContextCompat.getDrawable(this, R.drawable.bg_map_btn_round)!!.mutate(),
            android.graphics.drawable.InsetDrawable(icon, inset)
        ))
    }

    private fun updateButtons() {
        if (PilotNav.guiding && !overlayOn) { overlayOn = true; applyOverlayVisibility() }
        btnMain?.alpha = 1f
        applyPanel()
        val raw = BridgeBus.hud
        val ok = raw.contains("verbunden und bereit")
        val off = raw.contains("aus") || raw.contains("gestoppt") || raw.contains("–")
        btnHud?.setTextColor(if (ok) 0xFF35C46B.toInt() else if (off) 0xFF8E8E93.toInt() else 0xFFFF9F0A.toInt())
        applyPanel()
        setBtnIcon(btnNorth, if (!headUp) R.drawable.ic_view_north else if (tilt3d) R.drawable.ic_view_3d else R.drawable.ic_view_head)
        btnNorth.alpha = 1f
        simBar?.visibility = if (PilotNav.simulated && PilotNav.guiding) View.VISIBLE else View.GONE
        gpsLogDot?.visibility = if (io.github.th3s1nc.osmandhudbridge.pilot.GpsLog.enabled(this)) View.VISIBLE else View.GONE
        simLed?.lit = PilotNav.simulated
        updateRecUi()
        updateMenuHud()
    }

    /** Aufnahme-Kapsel: Ruhe = dunkel mit rotem Punkt und "REC", Aufnahme = rot mit Quadrat und Laufzeit. */
    private fun updateRecUi() {
        val rec = TrackRecorder.active
        val canShow = !PilotNav.guiding && pl.dest == null && PilotSession.route == null && !searchOpen
        val b = btnRec ?: return
        b.visibility = if (canShow) View.VISIBLE else View.GONE
        if (recLook != rec) {
            recLook = rec
            b.background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(if (rec) 0xFFE53935.toInt() else 0xE62B2B2E.toInt()) }
        }
        if (rec) {
            b.text = "■  REC"
        } else {
            val sp = android.text.SpannableString("●  REC")
            sp.setSpan(android.text.style.ForegroundColorSpan(0xFFFF3B30.toInt()), 0, 1, 0)
            b.text = sp
        }
    }

    /** Aufnahme starten oder beenden. Beenden speichert sofort als "Fahrt am …" (freie Aufzeichnung), ohne Namensfenster. */
    private fun toggleFreeRecording() {
        if (TrackRecorder.active) {
            val pts = TrackRecorder.finish()
            BridgeService.send(this, BridgeService.ACTION_CONFIG)
            val st = RideStore.save(this, RideStore.defaultName(pts.firstOrNull()?.timeMs ?: System.currentTimeMillis()), pts, true, RideStore.KIND_FREE)
            Toast.makeText(
                this,
                if (st != null) Tx.t("Aufzeichnung gespeichert (Einstellungen → Tracking)", "Recording saved (settings → Tracking)")
                else Tx.t("Zu kurz zum Speichern", "Too short to save"),
                Toast.LENGTH_LONG
            ).show()
            if (st == null) RideStore.discardUnfinished(this)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return
            }
            TrackRecorder.start(this)
            BridgeService.userStopped = false
            BridgeService.send(this)
        }
        updateRecUi()
    }

    private fun onUi(x: Float, y: Float): Boolean = hitViews.any { v ->
        v.visibility == View.VISIBLE && android.graphics.Rect().also { v.getGlobalVisibleRect(it) }.contains(x.toInt(), y.toInt())
    }

    private fun bearingNow(): Float = (map?.cameraPosition?.bearing ?: 0.0).toFloat()

    // Verschieben mit dem Finger beendet das Nachführen (Taste ⌖ schaltet es wieder ein); ein kurzer Tipp auf die Karte blendet die Anzeige aus/ein
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (menuLayer != null) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> rotAtPointerDown = bearingNow()
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; downAt = SystemClock.elapsedRealtime(); downOnUi = onUi(ev.rawX, ev.rawY) }
            MotionEvent.ACTION_MOVE -> if (ev.pointerCount >= 2 && !manualRot &&
                abs(bearingNow() - rotAtPointerDown).let { minOf(it, 360f - it) } > 3f) {
                // selbst gedreht: die automatische Drehung nach Fahrtrichtung hört auf
                manualRot = true
                headUp = false; tilt3d = false
                saveView()
                updateButtons()
            } else if (follow && ev.pointerCount == 1 && (abs(ev.x - downX) > dp(24) || abs(ev.y - downY) > dp(24))) {
                follow = false
                updateButtons()
            }
            MotionEvent.ACTION_UP -> {
                if (!downOnUi && SystemClock.elapsedRealtime() - downAt < 300 &&
                    abs(ev.x - downX) < dp(10) && abs(ev.y - downY) < dp(10)) {
                    if (!PilotNav.guiding) toggleOverlay()
                    else { panelShown = true; bumpPanel(); applyPanel(); updateButtons() }   // Tipp auf die Karte: Knöpfe für 8 s zeigen
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closeMenu()
        buildUi()
        refreshOverlay()
        mapView.post { applyPadding(); lastPos?.let { moveCam(lastShown ?: it, null, null, false) } }
    }

    // ---------------- Ziel, Zwischenziele und Routenvorschau ----------------

    private val waitGps get() = Tx.t("Warte auf GPS …", "Waiting for GPS …")

    private fun flagBitmap(): Bitmap {
        val w = dp(40); val h = dp(48)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1E.toInt(); strokeWidth = dp(4).toFloat(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        c.drawLine(w * 0.5f, h - dp(3).toFloat(), w * 0.5f, dp(3).toFloat(), pole)
        val flag = Path().apply {
            moveTo(w * 0.5f, dp(3).toFloat()); lineTo(w - dp(3).toFloat(), h * 0.25f); lineTo(w * 0.5f, h * 0.5f); close()
        }
        c.drawPath(flag, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD00000.toInt() })
        c.drawPath(flag, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat() })
        return bmp
    }

    /** Runde Marke mit Zahl oder Buchstabe (Zwischenziele orange, gewählter Start grün). */
    private fun pinBitmap(text: String, color: Int): Bitmap {
        val s = dp(30)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(s / 2f, s / 2f, s / 2f - dp(1), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
        c.drawCircle(s / 2f, s / 2f, s / 2f - dp(3), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFF000000.toInt(); textSize = dp(14).toFloat(); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        c.drawText(text, s / 2f, s / 2f + dp(5), tp)
        return bmp
    }

    /** Ein Punkt wurde gewählt (langer Druck auf die Karte oder Treffer der Suche). */
    private fun onMapPoint(lat: Double, lon: Double, name: String?) {
        if (homePick) {
            homePick = false
            Favs.setHome(this, (name ?: "").substringBefore(',').ifBlank { "Zuhause" }, lat, lon)
            Toast.makeText(this, Tx.t("Zuhause gespeichert.", "Home saved."), Toast.LENGTH_SHORT).show()
            return
        }
        val pt = PlanPoint(lat, lon, name ?: Plan.coordName(lat, lon))
        val mode = addMode
        addMode = 0
        when {
            pl.dest == null -> { pl.dest = pt; changed() }
            mode == 1 -> addViaPoint(pt)
            mode == 2 -> { pl.start = pt; changed() }
            else -> AlertDialog.Builder(this)
                .setTitle(pt.name)
                .setItems(arrayOf(Tx.t("Neues Ziel (ersetzt das alte)", "New destination (replaces the old one)"), Tx.t("Zwischenziel", "Waypoint"), Tx.t("Start", "Start"))) { _, which ->
                    when (which) {
                        0 -> { pl.dest = pt; changed() }
                        1 -> addViaPoint(pt)
                        else -> { pl.start = pt; changed() }
                    }
                }
                .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
                .show()
        }
    }

    private fun addViaPoint(pt: PlanPoint) {
        if (!pl.addVia(pt)) {
            Toast.makeText(this, Tx.t("Höchstens ${Plan.MAX_VIAS} Zwischenziele.", "At most ${Plan.MAX_VIAS} waypoints."), Toast.LENGTH_SHORT).show()
            return
        }
        changed()
    }

    /** Die Planung hat sich geändert: Route verwerfen, Marken neu setzen, kurz warten und neu rechnen. */
    private fun changed() {
        PilotSession.route = null
        showRoute(null)
        pushPoints()
        planMsg = null
        if (sheetLevel == 0) sheetLevel = 1
        planHandler.removeCallbacks(recalcRun)
        if (pl.dest != null) planHandler.postDelayed(recalcRun, 500)
        refreshSheet()
    }

    private fun clearAll() {
        planHandler.removeCallbacks(recalcRun)
        planSeq++
        pl.clear()
        addMode = 0
        sheetLevel = 1
        startAfterPlan = false
        planning = false; planMsg = null
        PilotSession.profileId = PilotSession.planner.end(PilotSession.profileId)
        PilotSession.route = null
        showRoute(null)
        pushPoints()
        previewShown = false
        padKey = ""
        applyPadding()
        refollow()
        refreshSheet()
    }

    /** Route berechnen: Start (Position oder gewählt), Zwischenziele, Ziel. Läuft im Hintergrund; ohne GPS-Fix wird gewartet. */
    private fun recalc() {
        if (pl.dest == null) return
        val here = lastPos?.let { PlanPoint(it.latitude, it.longitude, Tx.t("Aktuelle Position", "Current position")) }
        val wps = pl.waypoints(here)
        if (wps == null) { planning = false; startAfterPlan = false; planMsg = waitGps; refreshSheet(); return }
        planning = true
        planMsg = null
        val seq = ++planSeq
        refreshSheet()
        val profile = PilotSession.profileId
        planExec.execute {
            val out = PilotRouter.compute(this, wps, profile)
            runOnUiThread {
                if (seq != planSeq || isFinishing || isDestroyed) return@runOnUiThread
                planning = false
                when (out) {
                    is PilotRouter.Outcome.Ok -> {
                        PilotSession.name = pl.dest?.name ?: Tx.t("Ziel", "Destination")
                        PilotSession.waypoints = wps
                        PilotSession.routeWaypoints = wps
                        PilotSession.route = out.route
                        showRoute(out.route)
                        if (startAfterPlan) { startAfterPlan = false; refreshSheet(); doStart() } else fitPreview(out.route)
                    }
                    is PilotRouter.Outcome.Failed -> {
                        startAfterPlan = false
                        PilotSession.route = null
                        showRoute(null)
                        planMsg = if (out.missingTiles.isNotEmpty())
                            Tx.t("Routendaten fehlen. Unter ⚙ → Karte → Regionen laden die Region laden.", "Route data missing. Load the region under ⚙ → HUDPilot.")
                        else out.message
                    }
                }
                refreshSheet()
            }
        }
    }

    /** Ganze Route zeigen: zwischen Suchleiste oben und Vorschau unten, volle Breite, Norden oben, nicht geneigt. */
    private fun fitPreview(r: PilotRoute) {
        if (r.points.size < 2 || mapView.width <= 0) return
        follow = false
        previewShown = true
        previewRoute = r
        updateButtons()
        mapView.post { sheet?.post { refitPreview() } }
    }

    private fun refitPreview() {
        val r = previewRoute ?: return
        val m = map ?: return
        if (!previewShown || PilotNav.guiding || r.points.size < 2) return
        val sh = sheet
        mapView.post {
            try {
                if (!previewShown || PilotNav.guiding) return@post
                val w = mapView.width
                val h = mapView.height
                if (w <= 0 || h <= 0) return@post
                val b = LatLngBounds.Builder()
                for (p in r.points) b.include(LatLng(p.lat, p.lon))
                val sbBottom = searchBox?.let { if (it.visibility == View.VISIBLE && it.height > 0) it.bottom else 0 } ?: 0
                val top = (if (sbBottom > 0) sbBottom else dp(62)) + dp(12)
                val shUp = sh != null && sh.visibility == View.VISIBLE && sh.height > 0
                val land = w > h
                val left: Int
                val bottom: Int
                if (land) {
                    left = (if (shUp) sh!!.right else 0) + dp(16)
                    bottom = dp(24)
                } else {
                    left = dp(20)
                    bottom = if (shUp) (h - sh!!.top) + dp(12) else dp(24)
                }
                val right = dp(68)
                m.setPadding(0, 0, 0, 0)
                val pad = intArrayOf(left, top, right, bottom)
                val cp = m.getCameraForLatLngBounds(b.build(), pad, 0.0, 0.0)
                if (cp != null) m.moveCamera(CameraUpdateFactory.newCameraPosition(cp))
                else m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), left, top, right, bottom))
            } catch (_: Exception) {}
        }
    }

    private fun profileName(id: String): String =
        (PilotStore.profiles.firstOrNull { it.id == id } ?: PilotStore.profiles.firstOrNull())?.label()?.substringBefore(" (") ?: ""

    /** Kleiner Chip im Infoband mit dem aktuellen Profil. */
    private fun profileChips() {
        profChip?.text = profileName(PilotSession.profileId) + " ▾"
    }

    /** Liste aller Profile über dem Infoband; Auswahl rechnet die Route sofort neu. */
    private fun showProfileMenu(anchor: View) {
        profPopup?.dismiss()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = box(14, 0xFF2C2C2E.toInt())
        }
        var pw: android.widget.PopupWindow? = null
        for (p in PilotStore.profiles) {
            val on = p.id == PilotSession.profileId
            val row = label((if (on) "✓  " else "     ") + p.label().substringBefore(" ("), 16f, if (on) 0xFFFF7A00.toInt() else 0xFFFFFFFF.toInt(), bold = on).apply {
                setPadding(dp(14), dp(11), dp(18), dp(11))
                isClickable = true
                setOnClickListener {
                    pw?.dismiss()
                    if (p.id != PilotSession.profileId) {
                        PilotSession.planner.end(PilotSession.profileId)
                        PilotSession.profileId = p.id
                        if (pl.dest != null) changed()
                        profileChips()
                    }
                }
            }
            col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        col.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val popup = android.widget.PopupWindow(col, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.elevation = dp(8).toFloat()
        pw = popup
        profPopup = popup
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val sh = sheet
        val y = (if (sh != null) { sh.getLocationInWindow(loc); loc[1] } else loc[1]) - col.measuredHeight - dp(8)
        anchor.getLocationInWindow(loc)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, (loc[0] + anchor.width - col.measuredWidth).coerceAtLeast(dp(8)), y.coerceAtLeast(dp(8)))
    }

    /** Eine Zeile der Punkteliste; [tools] sind kleine Knöpfe rechts. */
    private fun pointRow(prefix: String, text: String, onTap: (() -> Unit)?, tools: List<Pair<String, () -> Unit>>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val t = label("$prefix  $text", 15f).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
            if (onTap != null) { isClickable = true; setOnClickListener { onTap() } }
        }
        row.addView(t, LinearLayout.LayoutParams(0, dp(38), 1f))
        for ((sym, act) in tools) {
            val b = label(sym, 17f).apply { gravity = Gravity.CENTER; isClickable = true; setOnClickListener { act() } }
            row.addView(b, LinearLayout.LayoutParams(dp(38), dp(38)))
        }
        return row
    }

    private fun buildPointList() {
        val box = pointsBox ?: return
        box.removeAllViews()
        if (pl.dest == null || sheetLevel != 2) { pointsScroll?.visibility = View.GONE; return }
        pointsScroll?.visibility = View.VISIBLE
        val cur = Tx.t("Aktuelle Position", "Current position")
        box.addView(pointRow(Tx.t("Start", "Start"), pl.start?.name ?: cur, { chooseStart() },
            buildList {
                if (pl.start != null && pl.vias.isNotEmpty()) add("▼" to { pl.startDown(); changed() })
                if (pl.start != null) add("✕" to { pl.start = null; changed() })
            }))
        var nv = 0
        val nShape = pl.vias.size - pl.visibleCount()
        if (nShape > 0) box.addView(label(Tx.t("Route folgt der gefahrenen Spur ($nShape Formpunkte)", "Route follows the recorded track ($nShape shape points)"), 13f, 0xFF9AA0A6.toInt()))
        for ((i, v) in pl.vias.withIndex()) {
            if (v.shape) continue
            nv++
            box.addView(pointRow("$nv", v.name, null, listOf(
                "▲" to { pl.moveViaUp(i); changed() },
                "▼" to { pl.moveViaDown(i); changed() },
                "✕" to { pl.removeVia(i); changed() }
            )))
        }
        box.addView(pointRow(Tx.t("Ziel", "End"), pl.dest!!.name, null,
            if (pl.vias.isNotEmpty()) listOf("▲" to { pl.destUp(); changed() }) else emptyList()))
        val add = label(
            if (pl.visibleCount() >= Plan.MAX_VIAS) Tx.t("Maximum erreicht (${Plan.MAX_VIAS})", "Maximum reached (${Plan.MAX_VIAS})")
            else Tx.t("＋ Zwischenziel (${pl.visibleCount()}/${Plan.MAX_VIAS})", "＋ Waypoint (${pl.visibleCount()}/${Plan.MAX_VIAS})"),
            15f, 0xFFFF9F0A.toInt(), bold = true
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; isClickable = true
            setOnClickListener {
                if (pl.visibleCount() < Plan.MAX_VIAS) {
                    addMode = 1
                    sheetLevel = 0
                    refreshSheet()
                    openSearch()
                    Toast.makeText(this@PilotMapLibreActivity, Tx.t("Suchen oder lange auf die Karte drücken.", "Search or long-press on the map."), Toast.LENGTH_SHORT).show()
                }
            }
        }
        box.addView(add, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        val rev = label(Tx.t("⇅ Start und Ziel tauschen", "⇅ Swap start and destination"), 15f, 0xFFFF9F0A.toInt(), bold = true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; isClickable = true
            setOnClickListener {
                val here = lastPos?.let { PlanPoint(it.latitude, it.longitude, Tx.t("Aktuelle Position", "Current position")) }
                if (pl.reverse(here)) changed()
                else Toast.makeText(this@PilotMapLibreActivity, waitGps, Toast.LENGTH_SHORT).show()
            }
        }
        box.addView(rev, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
    }

    private fun chooseStart() {
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Start", "Start"))
            .setItems(arrayOf(Tx.t("Aktuelle Position", "Current position"), Tx.t("Anderen Start wählen (Suche oder langer Druck)", "Choose another start (search or long press)"))) { _, which ->
                if (which == 0) { if (pl.start != null) { pl.start = null; changed() } }
                else {
                    addMode = 2
                    sheetLevel = 0
                    refreshSheet()
                    openSearch()
                    Toast.makeText(this, Tx.t("Suchen oder lange auf die Karte drücken.", "Search or long-press on the map."), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
            .show()
    }

    /** Vorschau nur zeigen, solange nicht navigiert wird und ein Ziel (oder eine geöffnete Route) da ist; die Suche ebenso. */
    private fun refreshSheet() {
        val sh = sheet ?: return
        val guiding = PilotNav.guiding
        if (guiding) searchOpen = false
        searchBox?.visibility = if (!guiding && searchOpen) View.VISIBLE else View.GONE
        btnSearch?.visibility = if (!guiding && !searchOpen) View.VISIBLE else View.GONE
        btnHome?.visibility = if (!guiding && !searchOpen) View.VISIBLE else View.GONE
        infoRow?.visibility = if (!guiding && searchOpen) View.GONE else View.VISIBLE   // Tempo/Rec machen der Suche Platz
        updateRecUi()
        val r = PilotSession.route
        val show = !guiding && (pl.dest != null || r != null)
        sh.visibility = if (show) View.VISIBLE else View.GONE
        refreshOverlay()
        if (!show) return
        val title = tvSheetTitle ?: return
        val sub = tvSheetSub ?: return
        val note = tvSheetNote ?: return
        if (r != null) {
            val ms = System.currentTimeMillis()
            title.text = MapOverlay.km(r.totalM) + " · " + MapOverlay.remaining(r.seconds)
            sub.text = Tx.t("Ankunft ", "Arrival ") + MapOverlay.eta(ms, r.seconds, java.util.TimeZone.getDefault().getOffset(ms).toLong()) +
                (if (pl.dest == null && PilotSession.name.isNotBlank()) " · " + PilotSession.name else "")
            val bn = baustellenNote(r)
            if (bn != null) { note.text = bn; note.visibility = View.VISIBLE } else note.visibility = View.GONE
        } else {
            title.text = if (planning) Tx.t("Berechne …", "Calculating …") else Tx.t("Ziel gesetzt", "Destination set")
            sub.text = ""
            val m = planMsg
            if (m != null) { note.text = m; note.visibility = View.VISIBLE } else note.visibility = View.GONE
        }
        btnStart?.alpha = if (r != null && !planning) 1f else 0.4f
        val lvl = sheetLevel
        title.visibility = if (lvl >= 1) View.VISIBLE else View.GONE
        sub.visibility = if (lvl >= 1 && sub.text.isNotEmpty()) View.VISIBLE else View.GONE
        if (lvl < 1) note.visibility = View.GONE
        actionsRow?.visibility = if (lvl >= 1) View.VISIBLE else View.GONE
        profChip?.visibility = if (lvl >= 1) View.VISIBLE else View.GONE
        favChip?.let { c -> val d = pl.dest; c.visibility = if (d != null) View.VISIBLE else View.GONE; c.text = if (d != null && Favs.at(this, d.lat, d.lon) != null) "★" else "☆" }
        buildPointList()
        profileChips()
    }

    // ---------------- Suche (online) ----------------

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            searchEdit?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0); it.clearFocus() }
        } catch (_: Exception) {}
    }

    // ---------------- Verlauf der Suche ----------------

    private class HistEntry(val query: String?, val name: String, val lat: Double, val lon: Double, val at: Long = 0L)

    private fun loadHistory(): MutableList<HistEntry> {
        val out = ArrayList<HistEntry>()
        try {
            val arr = org.json.JSONArray(prefs.getString("search_hist", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!o.has("q")) out += HistEntry(null, o.optString("n"), o.optDouble("la"), o.optDouble("lo"), o.optLong("t", 0L))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun saveHistory(list: List<HistEntry>) {
        val arr = org.json.JSONArray()
        for (e in list.take(HIST_MAX)) arr.put(org.json.JSONObject().apply {
            if (e.query != null) put("q", e.query) else { put("n", e.name); put("la", e.lat); put("lo", e.lon); put("t", e.at) }
        })
        prefs.edit().putString("search_hist", arr.toString()).apply()
    }

    private fun addHistory(e: HistEntry) {
        val l = loadHistory()
        l.removeAll { if (e.query != null) it.query == e.query else (it.query == null && it.name == e.name) }
        l.add(0, e)
        saveHistory(l)
    }

    /** Die letzten Suchen und Ziele unter dem leeren Suchfeld. */
    private fun showHistory() {
        val box = resultsBox ?: return
        val scroll = resultsScroll ?: return
        val l = loadHistory()
        val favs = Favs.live(this)
        if (l.isEmpty() && favs.isEmpty()) { showResults(null, null); return }
        showResults(null, null)
        box.removeAllViews()
        scroll.visibility = View.VISIBLE
        historyShown = true
        if (favs.isNotEmpty()) {
            box.addView(label(Tx.t("★ Favoriten  ·  lang drücken zum Ändern", "★ Favourites  ·  long press to edit"), 14f, 0xFFFFB04A.toInt()).apply { setPadding(dp(8), dp(4), dp(8), dp(6)) })
            for (fv in favs) {
                val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(9), dp(8), dp(9)); isClickable = true }
                item.addView(label((if (fv.id == Favs.HOME) "🏠  " else "★  ") + fv.name, 16f, bold = true).apply { setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END })
                item.setOnClickListener { closeSearch(); onMapPoint(fv.lat, fv.lon, fv.name) }
                item.setOnLongClickListener { favEditDialog(fv); true }
                box.addView(item)
            }
        }
        if (l.isEmpty()) return
        box.addView(label(Tx.t("Zuletzt gefahren  ·  Verlauf löschen", "Recently driven  ·  clear history"), 14f, 0xFFB0B0B8.toInt()).apply {
            setPadding(dp(8), dp(10), dp(8), dp(6)); isClickable = true
            setOnClickListener { saveHistory(emptyList()); showResults(null, null) }
        })
        for (e in l) {
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(9), dp(8), dp(9)); isClickable = true }
            val main = e.name.substringBefore(',').trim()
            val rest = e.name.substringAfter(',', "").trim()
            item.addView(label("📍  $main", 16f, bold = true).apply {
                setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val sub = listOf(rest, ageText(e.at)).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) item.addView(label(sub, 13f, 0xFFB0B0B8.toInt()).apply { setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END })
            item.setOnClickListener {
                closeSearch()
                onMapPoint(e.lat, e.lon, e.name)
            }
            item.setOnLongClickListener {
                saveHistory(loadHistory().filterNot { it.query == null && it.name == e.name && it.lat == e.lat && it.lon == e.lon })
                showHistory()
                true
            }
            box.addView(item)
        }
    }

    // ---------------- Zuhause und Favoriten ----------------

    /** Tipp auf 🏠: Route nach Hause planen (ohne Adresse zuerst festlegen). */
    private fun goHome() {
        val h = Favs.home(this)
        if (h == null) { homeSetup(); return }
        pl.dest = PlanPoint(h.lat, h.lon, h.name)
        changed()
        Toast.makeText(this, Tx.t("Route nach Hause …", "Route home …"), Toast.LENGTH_SHORT).show()
    }

    /** Zuhause festlegen: aktuelle Position, Kartenmitte oder Adresse suchen. */
    private fun homeSetup() {
        val items = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
        lastPos?.let { p -> items += Tx.t("Meine Position", "My position"); acts += { Favs.setHome(this, "Zuhause", p.latitude, p.longitude); homeSaved() } }
        map?.cameraPosition?.target?.let { c -> items += Tx.t("Mitte der Karte", "Map centre"); acts += { Favs.setHome(this, "Zuhause", c.latitude, c.longitude); homeSaved() } }
        items += Tx.t("Adresse suchen …", "Search address …")
        acts += {
            homePick = true
            Toast.makeText(this, Tx.t("Adresse suchen und antippen.", "Search an address and tap it."), Toast.LENGTH_LONG).show()
            openSearch()
        }
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Zuhause festlegen", "Set home"))
            .setItems(items.toTypedArray()) { _, i -> acts[i]() }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
            .show()
    }

    private fun homeSaved() = Toast.makeText(this, Tx.t("Zuhause gespeichert.", "Home saved."), Toast.LENGTH_SHORT).show()

    /** Langer Druck auf 🏠: Adresse ändern oder löschen. */
    private fun homeMenu() {
        val h = Favs.home(this)
        if (h == null) { homeSetup(); return }
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Zuhause: ", "Home: ") + h.name)
            .setItems(arrayOf(Tx.t("Adresse ändern …", "Change address …"), Tx.t("Zuhause löschen", "Delete home"))) { _, i ->
                if (i == 0) homeSetup() else { Favs.remove(this, Favs.HOME); Toast.makeText(this, Tx.t("Zuhause gelöscht.", "Home deleted."), Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
            .show()
    }

    /** Favorit speichern, mit Namen zum Ändern. */
    private fun favSaveDialog(name: String, lat: Double, lon: Double) {
        val e = android.widget.EditText(this).apply { setText(name); setSingleLine(true); setSelection(text.length) }
        val col = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        AlertDialog.Builder(this)
            .setTitle(Tx.t("★ Favorit speichern", "★ Save favourite"))
            .setView(col)
            .setPositiveButton(Tx.t("Speichern", "Save")) { _, _ ->
                Favs.add(this, e.text.toString().trim(), lat, lon); updateButtons()
                Toast.makeText(this, Tx.t("Favorit gespeichert.", "Favourite saved."), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(Tx.t("Als Zuhause", "As home")) { _, _ -> Favs.setHome(this, e.text.toString().trim(), lat, lon); updateButtons(); homeSaved() }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
            .show()
    }

    /** Favorit ändern: umbenennen, löschen oder als Zuhause setzen. */
    private fun favEditDialog(f: Favs.Fav) {
        val items = arrayOf(Tx.t("Umbenennen …", "Rename …"), Tx.t("Als Zuhause setzen", "Set as home"), Tx.t("Löschen", "Delete"))
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> {
                        val e = android.widget.EditText(this).apply { setText(f.name); setSingleLine(true); setSelection(text.length) }
                        val col = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
                        AlertDialog.Builder(this).setTitle(Tx.t("Neuer Name", "New name")).setView(col)
                            .setPositiveButton("OK") { _, _ -> Favs.rename(this, f.id, e.text.toString().trim()); if (historyShown) showHistory() }
                            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).show()
                    }
                    1 -> { Favs.setHome(this, f.name, f.lat, f.lon); homeSaved(); if (historyShown) showHistory() }
                    else -> { Favs.remove(this, f.id); updateButtons(); if (historyShown) showHistory() }
                }
            }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
            .show()
    }

    /** ☆/★ im Zielblatt: aktuelles Ziel als Favorit speichern oder wieder entfernen. */
    private fun toggleFav() {
        val d = pl.dest ?: return
        val ex = Favs.at(this, d.lat, d.lon)
        if (ex != null) {
            Favs.remove(this, ex.id); updateButtons()
            Toast.makeText(this, Tx.t("Favorit entfernt.", "Favourite removed."), Toast.LENGTH_SHORT).show()
        } else favSaveDialog(d.name.substringBefore(',').trim().ifBlank { d.name }, d.lat, d.lon)
    }

    /** "heute", "gestern", "vor 5 Tagen" (leer, wenn unbekannt). */
    private fun ageText(at: Long): String {
        if (at <= 0L) return ""
        val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
        return when {
            days <= 0 -> Tx.t("heute", "today")
            days == 1 -> Tx.t("gestern", "yesterday")
            else -> Tx.t("vor $days Tagen", "$days days ago")
        }
    }

    private fun showResults(hits: List<PlaceSearch.Hit>?, msg: String?) {
        historyShown = false
        val box = resultsBox ?: return
        val scroll = resultsScroll ?: return
        box.removeAllViews()
        drawHits(if (hits.isNullOrEmpty()) emptyList() else hits)
        if (hits == null && msg == null) { scroll.visibility = View.GONE; return }
        scroll.visibility = View.VISIBLE
        val head = label(msg ?: Tx.t("${hits!!.size} Treffer  ·  lang drücken: ★ Favorit", "${hits!!.size} results  ·  long press: ★ favourite"), 14f, 0xFFB0B0B8.toInt()).apply {
            setPadding(dp(8), dp(4), dp(8), dp(6)); isClickable = true; setOnClickListener { showResults(null, null) }
        }
        box.addView(head)
        for ((hi, h) in (hits ?: emptyList()).withIndex()) {
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(7), dp(8), dp(7)); isClickable = true }
            item.addView(label("${hi + 1}  " + h.name, 16f, bold = true).apply { setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END })
            val d = PlaceFormat.dist(h.distM) + (if (h.detail.isNotBlank()) " · " + h.detail else "")
            item.addView(label(d, 13f, 0xFFB0B0B8.toInt()).apply { setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END })
            item.setOnClickListener {
                showResults(null, null)
                searchEdit?.setText("")
                closeSearch()
                onMapPoint(h.lat, h.lon, h.label)
            }
            item.setOnLongClickListener { favSaveDialog(h.label, h.lat, h.lon); true }
            box.addView(item)
        }
    }

    /** Sichtbarer Kartenausschnitt in Grad. */
    private fun visibleBox(): PlaceSearch.Box? {
        val m = map ?: return null
        if (mapView.width <= 0 || mapView.height <= 0) return null
        return try {
            val b = m.projection.visibleRegion.latLngBounds
            PlaceSearch.Box(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeEast)
        } catch (_: Exception) { null }
    }

    private var updateChecked = false
    /** Einmal je Start: im WLAN die Regionen dem Server melden und auf Neueres hinweisen. */
    private fun checkRegionUpdates() {
        if (updateChecked || RegionSync.installedIds(this).isEmpty() || RegionSync.serverUrl(this).isEmpty()) return
        val wifi = try { !(getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered } catch (_: Exception) { false }
        if (!wifi) return
        updateChecked = true
        fileExec.execute {
            val names = RegionSync.checkUpdates(this)
            runOnUiThread {
                if (isFinishing || isDestroyed || names.isEmpty()) return@runOnUiThread
                Toast.makeText(this, Tx.t("Neue Kartendaten verfügbar: ", "New map data available: ") + names.joinToString(", ") + Tx.t(" (⚙ → Karte → Regionen laden)", " (⚙ → Map → Load regions)"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private var pendingTour: RegionSync.Outbox? = null

    /** Intent von der Touren-Seite: Tour in die Planung legen (nach dem Zurückkommen in onResume). */
    private fun handleTourIntent(i: Intent?) {
        val arr = i?.getDoubleArrayExtra("tour_pts") ?: return
        if (arr.size < 4) return
        val name = i.getStringExtra("tour_name") ?: "Tour"
        val prof = i.getStringExtra("tour_profile") ?: ""
        i.removeExtra("tour_pts")
        val av = i.getStringArrayExtra("tour_avoid")?.toList()
        i.removeExtra("tour_avoid")
        pendingTour = RegionSync.Outbox(0L, name, prof, (0 until arr.size / 2).map { Pair(arr[it * 2], arr[it * 2 + 1]) }, av)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTourIntent(intent)
        handleGpxIntent(intent)
        if (intent.getBooleanExtra("open_friends", false)) { intent.removeExtra("open_friends"); chooseMenu(); menuFriends() }
    }

    /** GPX von außen (Teilen / Öffnen mit): Route in die Planung legen. */
    @Suppress("DEPRECATION")
    private fun handleGpxIntent(i: Intent?) {
        i ?: return
        val uri: android.net.Uri? = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND ->
                if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                else i.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
            else -> null
        }
        if (uri != null) {
            i.action = null // nicht bei jedem Drehen erneut laden
            loadGpxUri(uri)
        }
    }

    private fun loadGpxUri(uri: android.net.Uri) {
        if (PilotNav.guiding) {
            Toast.makeText(this, Tx.t("Erst die Navigation beenden.", "End the navigation first."), Toast.LENGTH_LONG).show()
            return
        }
        fileExec.execute {
            val text = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > 30_000_000) null else bytes.toString(Charsets.UTF_8)
                }
            } catch (_: Exception) { null }
            val parsed: io.github.th3s1nc.osmandhudbridge.pilot.GpxRoute.Parsed? = text?.let { io.github.th3s1nc.osmandhudbridge.pilot.GpxRoute.parse(it) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (parsed == null || parsed.waypoints.size < 2) {
                    Toast.makeText(this, Tx.t("Diese Datei enthält keine Route oder keinen Track.", "This file contains no route or track."), Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val o = RegionSync.Outbox(0L, parsed.name.ifBlank { "GPX" }, "", parsed.waypoints.map { Pair(it.lat, it.lon) })
                if (map != null) loadPlannerTour(o, Tx.t("GPX geladen.", "GPX loaded.")) else pendingTour = o
            }
        }
    }

    private fun saveTourToServer() {
        val dest = pl.dest
        if (dest == null) { Toast.makeText(this, Tx.t("Erst eine Tour planen.", "Plan a tour first."), Toast.LENGTH_SHORT).show(); return }
        if (RegionSync.serverUrl(this).isEmpty()) { Toast.makeText(this, Tx.t("Erst die Serveradresse unter Menü → Server eintragen.", "Enter the server address under menu → server first."), Toast.LENGTH_LONG).show(); return }
        val first = pl.start?.let { Pair(it.lat, it.lon) } ?: lastPos?.let { Pair(it.latitude, it.longitude) }
        if (first == null) { Toast.makeText(this, Tx.t("Start unbekannt (kein GPS).", "Start unknown (no GPS)."), Toast.LENGTH_LONG).show(); return }
        val pts = ArrayList<Pair<Double, Double>>()
        pts += first
        pl.vias.forEach { pts += Pair(it.lat, it.lon) }
        pts += Pair(dest.lat, dest.lon)
        val name = dest.name; val prof = PilotSession.profileId
        fileExec.execute {
            val ok = TourSync.saveTour(this, name, prof, pts)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) fileExec.execute { try { TourSync.fetchPlanned(this) } catch (_: Exception) { } }
                val msg = if (ok) Tx.t("Tour auf dem Server gespeichert.", "Tour saved on the server.")
                    else if (!ServerProfile.has(this) || ServerProfile.authFailed) Tx.t("Bitte erst ein Profil anlegen (Menü → Server).", "Please create a profile first (menu → server).")
                    else Tx.t("Server nicht erreichbar.", "Server not reachable.")
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadPlannerTour(o: RegionSync.Outbox, msg: String = Tx.t("Route vom Planer geladen.", "Planner route loaded.")) {
        clearAll()
        val p = o.points
        pl.start = PlanPoint(p.first().first, p.first().second, Tx.t("Start (Planer)", "Start (planner)"))
        val dense = p.size > 27   // genau übernommene Fahrt: viele Punkte nur als Form der Strecke, nicht als Zwischenziele
        for (i in 1 until p.size - 1) pl.addVia(PlanPoint(p[i].first, p[i].second, Tx.t("Zwischenziel ", "Waypoint ") + i, dense))
        pl.dest = PlanPoint(p.last().first, p.last().second, o.name)
        if (o.profile.isNotEmpty() || o.avoid != null)   // GPX-Dateien haben beides nicht
            PilotSession.profileId = PilotSession.planner.begin(PilotSession.profileId, o.profile, o.avoid, PilotStore.profiles.map { it.id })
        moveCam(LatLng(p.first().first, p.first().second), null, 10.0, false)
        changed()
        Toast.makeText(this, if (PilotSession.planner.active) msg + "\n" + plannerHint() else msg, Toast.LENGTH_LONG).show()
    }

    /** Kurzer Hinweis, mit welchem Profil und welchen Vermeiden-Schaltern eine Planer-Route gerechnet wird. */
    private fun plannerHint(): String {
        val av = PilotSession.avoid(this).on
        val avTxt = if (av.isEmpty()) Tx.t("nichts vermeiden", "avoiding nothing") else Tx.t("ohne ", "without ") + Avoid.KEYS.filter { it in av }.joinToString(", ") { Avoid.label(it) }
        return Tx.t("Profil ", "Profile ") + profileName(PilotSession.profileId) + ", " + avTxt
    }

    private fun hasPoiFiles(): Boolean = RegionSync.installedIds(this).any { RegionSync.poiFileOf(this, it).isFile }

    /** Kategorie aus den heruntergeladenen Orte-Dateien (Server) im Ausschnitt: offline, auch bei weitem Zoom. */
    private fun searchCategoryFromFiles(cat: PlaceSearch.Cat, box0: PlaceSearch.Box?) {
        val c = map?.cameraPosition?.target ?: run { showResults(emptyList(), Tx.t("Die Karte ist noch nicht bereit.", "The map is not ready yet.")); return }
        val box = box0 ?: PlaceSearch.Box(c.latitude - 0.3, c.longitude - 0.45, c.latitude + 0.3, c.longitude + 0.45)
        // sehr weite Ausschnitte auf etwa 120 km um die Mitte begrenzen
        val dLat = minOf((box.north - box.south) / 2, 0.55); val dLon = minOf((box.east - box.west) / 2, 0.85)
        val kinds = cat.poiKinds() + (if (cat == PlaceSearch.Cat.FOOD) setOf("ice_cream") else emptySet()) +
            (if (cat == PlaceSearch.Cat.PARKING) setOf("motorcycle_parking") else emptySet())
        val seq = ++searchSeq
        showResults(emptyList(), Tx.t("Suche …", "Searching …"))
        searchExec.execute {
            val found = ArrayList<PoiIndex.Poi>()
            for (id in RegionSync.installedIds(this)) {
                val f = RegionSync.poiFileOf(this, id)
                if (!f.isFile) continue
                try {
                    java.util.zip.GZIPInputStream(f.inputStream().buffered()).bufferedReader(Charsets.UTF_8).use { r ->
                        found += PoiIndex.search(r, kinds, c.latitude - dLat, c.longitude - dLon, c.latitude + dLat, c.longitude + dLon)
                    }
                } catch (e: Exception) { BridgeBus.log("Orte lesen: " + (e.message ?: e.javaClass.simpleName)) }
            }
            val hits = PoiIndex.dedupe(found).map {
                PlaceSearch.Hit(it.name.ifBlank { cat.label().substringAfter(' ') }, listOf(it.street, it.city).filter { x -> x.isNotBlank() }.joinToString(", "), it.lat, it.lon)
            }
            val sorted = PlaceSearch.sorted(hits, c.latitude, c.longitude).take(25)
            runOnUiThread {
                if (seq != searchSeq || isFinishing || isDestroyed) return@runOnUiThread
                if (sorted.isEmpty()) showResults(emptyList(), Tx.t("Hier nichts gefunden. Karte verschieben oder weiter herauszoomen.", "Nothing found here. Move the map or zoom out."))
                else showResults(sorted, null)
            }
        }
    }

    /** Kategorie (Tanken, Essen, …) aus den POIs der geladenen Kartenkacheln im sichtbaren Ausschnitt: offline und sofort. */
    private fun searchCategoryOnMap(cat: PlaceSearch.Cat, box: PlaceSearch.Box?) {
        val m = map
        val st = style
        val c = m?.cameraPosition?.target
        if (m == null || st == null || box == null || c == null) {
            showResults(emptyList(), Tx.t("Die Karte ist noch nicht bereit.", "The map is not ready yet.")); return
        }
        if (m.cameraPosition.zoom < 11.0) {
            showResults(emptyList(), Tx.t("Bitte näher heranzoomen (Ortsansicht), dann nochmal tippen.", "Zoom in closer (town view), then tap again.")); return
        }
        val kinds = cat.poiKinds()
        val feats = try {
            st.getSourceAs<VectorSource>("protomaps")?.querySourceFeatures(arrayOf("pois"), null) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val seen = HashSet<String>()
        val hits = ArrayList<PlaceSearch.Hit>()
        for (f in feats) {
            val g = f.geometry() as? Point ?: continue
            val props = f.properties() ?: continue
            val kind = props.get("kind")?.takeIf { !it.isJsonNull }?.asString ?: continue
            if (kind !in kinds) continue
            val lat = g.latitude()
            val lon = g.longitude()
            if (lat < box.south || lat > box.north || lon < box.west || lon > box.east) continue
            val name = props.get("name")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: cat.label().substringAfter(' ')
            if (!seen.add(name + "|" + String.format(java.util.Locale.US, "%.4f,%.4f", lat, lon))) continue
            hits += PlaceSearch.Hit(name, "", lat, lon)
        }
        val sorted = PlaceSearch.sorted(hits, c.latitude, c.longitude).take(25)
        if (sorted.isEmpty()) showResults(emptyList(), Tx.t("Hier nichts gefunden. Näher heranzoomen oder verschieben; die Karte zeigt nur, was in ihr steht.", "Nothing found here. Zoom in or move; the map only knows what is in it."))
        else showResults(sorted, null)
    }

    private fun doSearch(q: String?, cat: PlaceSearch.Cat?) {
        if (cat == null && q.isNullOrBlank()) return
        hideKeyboard()
        if (sheetLevel != 0) { sheetLevel = 0; refreshSheet() }
        val camC = map?.cameraPosition?.target ?: LatLng(50.0, 8.7)
        val c = if (cat != null) camC else (lastPos ?: camC)
        val box = if (cat != null) visibleBox() else null
        if (cat != null && hasPoiFiles()) { searchCategoryFromFiles(cat, box); return }
        if (box != null && (box.widthKm > PlaceSearch.MAX_BOX_KM || box.heightKm > PlaceSearch.MAX_BOX_KM)) {
            showResults(emptyList(), Tx.t("Bitte näher heranzoomen, dann nochmal tippen.", "Zoom in closer, then tap again."))
            return
        }
        if (cat != null) { searchCategoryOnMap(cat, box); return }
        val seq = ++searchSeq
        showResults(emptyList(), Tx.t("Suche …", "Searching …"))
        searchExec.execute {
            val res = try {
                Pair(if (cat != null) PlaceSearch.searchCategory(cat, box!!, c.latitude, c.longitude) else PlaceSearch.searchText(q!!, c.latitude, c.longitude), null as String?)
            } catch (e: Exception) {
                Pair(null as List<PlaceSearch.Hit>?, Tx.t("Server antwortet nicht, bitte nochmal tippen.", "Server not answering, please tap again.") + (e.message?.let { "\n($it)" } ?: ""))
            }
            runOnUiThread {
                if (seq != searchSeq || isFinishing || isDestroyed) return@runOnUiThread
                val hits = res.first
                if (hits == null) showResults(emptyList(), res.second)
                else if (hits.isEmpty()) showResults(emptyList(), Tx.t("Nichts gefunden.", "Nothing found."))
                else showResults(hits, null)
            }
        }
    }

    // ---------------- Start der Navigation ----------------

    private fun startGuidance() {
        val r = PilotSession.route
        if (r == null || planning) {
            Toast.makeText(this, if (planning) Tx.t("Die Route wird noch berechnet.", "The route is still being calculated.") else Tx.t("Erst die Route berechnen.", "Calculate the route first."), Toast.LENGTH_SHORT).show()
            return
        }
        val cur = lastPos
        val s = pl.start
        if (pl.dest != null && cur != null) {
            if (s != null) {
                val d = Plan.distM(cur.latitude, cur.longitude, s.lat, s.lon)
                if (d > 500) {
                    AlertDialog.Builder(this)
                        .setTitle(Tx.t("Du bist ${PlaceFormat.dist(d)} vom Start entfernt.", "You are ${PlaceFormat.dist(d)} from the start."))
                        .setPositiveButton(Tx.t("Von hier aus rechnen", "Calculate from here")) { _, _ -> pl.start = null; startAfterPlan = true; changed() }
                        .setNeutralButton(Tx.t("Trotzdem starten", "Start anyway")) { _, _ -> doStart() }
                        .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
                        .show()
                    return
                }
            } else {
                val first = r.points.firstOrNull()
                if (first != null && Plan.distM(cur.latitude, cur.longitude, first.lat, first.lon) > 100) {
                    // Position hat sich seit der Berechnung verändert: frisch von hier aus rechnen, dann starten
                    startAfterPlan = true
                    changed()
                    return
                }
            }
        }
        doStart()
    }

    private fun doStart() {
        if (PilotSession.route == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        when {
            TrackRecorder.active -> beginNav(true) // läuft schon eine Aufzeichnung (freie Fahrt), sie geht einfach weiter
            prefs.getString("rec_nav_mode", "ask") == "always" -> beginNav(true)
            prefs.getString("rec_nav_mode", "ask") == "never" -> beginNav(false)
            else -> askRecord()
        }
    }

    /** Frage vor dem Start: Fahrt aufzeichnen? */
    private fun askRecord() {
        val cb = android.widget.CheckBox(this).apply { text = Tx.t("Nicht mehr fragen (in ⚙ änderbar)", "Don't ask again (change in ⚙)") }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        col.addView(cb)
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Fahrt aufzeichnen?", "Record the ride?"))
            .setView(col)
            .setPositiveButton(Tx.t("Ja, aufzeichnen", "Yes, record")) { _, _ ->
                if (cb.isChecked) prefs.edit().putString("rec_nav_mode", "always").apply()
                beginNav(true)
            }
            .setNegativeButton(Tx.t("Nein", "No")) { _, _ ->
                if (cb.isChecked) prefs.edit().putString("rec_nav_mode", "never").apply()
                beginNav(false)
            }
            .show()
    }


    /** Am Ende der Navigation: läuft eine Aufzeichnung der Fahrt, beenden und Speichern/Verwerfen anbieten. */
    private fun checkPendingRide() {
        val start = prefs.getLong("rec_nav_start", 0L)
        if (start == 0L || PilotNav.guiding || rideDialogOpen) return
        val pts = if (TrackRecorder.active) TrackRecorder.finish() else RideStore.unfinished(this)
        prefs.edit().putLong("rec_nav_start", 0L).apply()
        BridgeService.send(this, BridgeService.ACTION_CONFIG)
        if (pts.size < 2) { RideStore.discardUnfinished(this); return }
        rideDialogOpen = true
        RideDialogs.showEnd(this, pts, start) { rideDialogOpen = false }
    }

    /** Beenden nur nach Rückfrage (Zurück-Geste und ■). */
    private fun confirmStop() {
        if (!PilotNav.guiding || stopAsking) return
        stopAsking = true
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Navigation beenden?", "End the navigation?"))
            .setPositiveButton(Tx.t("Ja, beenden", "Yes, end")) { _, _ -> PilotNav.stop(reason = Tx.t("vom Nutzer beendet", "ended by user")) }
            .setNegativeButton(Tx.t("Nein", "No"), null)
            .setOnDismissListener { stopAsking = false }
            .show()
    }

    private fun beginNav(record: Boolean) {
        val r = PilotSession.route ?: return
        if (record) {
            if (!TrackRecorder.active) TrackRecorder.start(this)
            prefs.edit().putLong("rec_nav_start", System.currentTimeMillis()).apply()
        }
        showResults(null, null)
        if (PilotSession.planner.active) Toast.makeText(this, Tx.t("Route aus dem Planer: ", "Planner route: ") + plannerHint(), Toast.LENGTH_LONG).show()
        pl.dest?.let { addHistory(HistEntry(null, it.name, it.lat, it.lon, System.currentTimeMillis())) }
        PilotNav.start(this, r, PilotSession.routeWaypoints.ifEmpty { PilotSession.waypoints }, PilotSession.profileId, false)
        BridgeService.userStopped = false
        BridgeService.send(this)
        ticker.postDelayed({
            if (PilotVoice.enabled(this) && PilotVoice.missing)
                Toast.makeText(this, Tx.t("Keine Stimme für die Ansagen installiert (⚙ → Sprachansagen).", "No voice installed for guidance (⚙ → Voice guidance)."), Toast.LENGTH_LONG).show()
        }, 2500)
        previewShown = false
        applyPadding()
        refollow()
        updateButtons()
        refreshSheet()
        refreshOverlay()
    }

    /** Einstellungen (die bisherige Oberfläche) nach vorn holen; die Karte bleibt darunter. */
    private fun openSettings() {
        if (PilotNav.guiding) {
            confirmStop()
            return
        }
        startActivity(Intent(this, MainActivity::class.java).putExtra("settings", true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /** Fenster zum HUD: Zustand von HUD und GPS, Knopf zum Verbinden oder Trennen (der Schalter selbst sitzt in den Einstellungen). */
    private fun showHudDialog() {
        if (PilotNav.guiding) {
            Toast.makeText(this, StatusText.en(BridgeBus.hud), Toast.LENGTH_SHORT).show()
            return
        }
        val sp = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
        val wanted = BridgeService.hudWanted(sp)
        AlertDialog.Builder(this)
            .setTitle("HUD")
            .setMessage(StatusText.en(BridgeBus.hud) + "\n" + StatusText.en(BridgeBus.gps))
            .setPositiveButton(if (wanted) Tx.t("HUD trennen", "Disconnect HUD") else Tx.t("HUD verbinden", "Connect HUD")) { _, _ ->
                MainActivity.pendingHud = !wanted
                startActivity(Intent(this, MainActivity::class.java).putExtra("settings", true).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            .setNegativeButton(Tx.t("Schließen", "Close"), null)
            .show()
    }

    // ---------------- Simulation ----------------

    private val simTick = object : Runnable {
        override fun run() {
            if (!simRunning) return
            val s = sim ?: return
            val now = SystemClock.elapsedRealtime()
            s.advance((now - simLast) / 1000.0)
            simLast = now
            val st = s.state()
            PilotNav.onFix(st.lat, st.lon, simSpeed.toFloat(), st.headingDeg.roundToInt(), simFactor)
            if (!simRunning) return // Ziel erreicht: onStopped hat die Simulation beendet
            ticker.postDelayed(this, 500)
        }
    }

    private fun newSim(r: PilotRoute): RouteSimulator = RouteSimulator(r).also { it.speedKmh = simSpeed; it.timeFactor = simFactor }

    /** Fahrt auf der berechneten Route ohne echtes GPS abspielen. */
    private fun startSim() {
        val r = PilotSession.route
        if (r == null || planning || r.totalM < 50) {
            Toast.makeText(this, if (planning) Tx.t("Die Route wird noch berechnet.", "The route is still being calculated.") else Tx.t("Erst die Route berechnen.", "Calculate the route first."), Toast.LENGTH_SHORT).show()
            return
        }
        if (PilotNav.driving) {
            Toast.makeText(this, Tx.t("Es läuft gerade eine echte Navigation.", "A real navigation is running."), Toast.LENGTH_SHORT).show()
            return
        }
        showResults(null, null)
        sim = newSim(r)
        PilotNav.start(this, r, PilotSession.routeWaypoints.ifEmpty { PilotSession.waypoints }, PilotSession.profileId, true)
        BridgeService.userStopped = false
        BridgeService.send(this)
        simRunning = true
        simLast = SystemClock.elapsedRealtime()
        previewShown = false
        applyPadding()
        refollow()
        updateButtons()
        refreshSheet()
        refreshOverlay()
        markSim()
        ticker.removeCallbacks(simTick)
        ticker.post(simTick)
    }

    private fun setSimSpeed(kmh: Double, f: Double) {
        simSpeed = kmh; simFactor = f
        sim?.let { it.speedKmh = kmh; it.timeFactor = f }
    }

    private fun simPauseToggle() {
        if (!PilotNav.simulated || sim == null) return
        simRunning = !simRunning
        if (simRunning) { simLast = SystemClock.elapsedRealtime(); ticker.removeCallbacks(simTick); ticker.post(simTick) }
    }

    /** Position 200 m neben die Route setzen: die Führung muss die Abweichung selbst erkennen und neu rechnen. */
    private fun deviate() {
        val s = sim
        if (s == null || !PilotNav.simulated) return
        s.sideOffsetM = 200.0
        if (!simRunning) simPauseToggle()
    }

    private fun markSim() {
        val on = listOf(simSpeed == 50.0 && simFactor == 1.0, simSpeed == 100.0 && simFactor == 1.0, simFactor == 10.0, simFactor == 50.0, false, !simRunning)
        for ((i, c) in simChips.withIndex()) {
            c.alpha = if (on.getOrElse(i) { false }) 1f else 0.55f
            if (i == 5) c.text = if (simRunning) "⏸" else "▶"
        }
    }

    // ---------------- Führung ----------------

    override fun onGuide(state: Guide.State) {
        lastState = state
        hintOverride = null
        if (PilotNav.simulated) onPosition(PilotNav.lastLat, PilotNav.lastLon, PilotNav.lastHeading?.toFloat(), simSpeed.toFloat())
        else if (PilotNav.coasting) onPosition(PilotNav.lastLat, PilotNav.lastLon, PilotNav.lastHeading?.toFloat(), PilotNav.lastSpeedKmh, logIt = true, estimated = true)   // Tunnel: Pfeil läuft auf der Route weiter
        else refreshOverlay()
    }

    override fun onRerouted(route: PilotRoute) {
        showRoute(route)
        if (PilotNav.simulated) {
            sim = newSim(route)
            simLast = SystemClock.elapsedRealtime()
        }
        refreshOverlay()
    }

    /** Kleiner Balken oben in der Mitte mit Zielflagge, 8 Sekunden lang. */
    private fun showArrived() {
        val bar = arrivedBar ?: return
        bar.visibility = View.VISIBLE; bar.bringToFront()
        ticker.removeCallbacks(hideArrived); ticker.postDelayed(hideArrived, 8_000L)
    }

    override fun onStopped(arrived: Boolean) {
        lastState = null
        hintOverride = null
        if (arrived) showArrived()
        simRunning = false
        sim = null
        ticker.removeCallbacks(simTick)
        if (!PilotNav.simulated) {
            // Navigation zu Ende (Ziel erreicht oder beendet): Ziel und Route weg, Karte folgt wieder dem Standort.
            // Nach einer Simulation bleibt die Route stehen (Vorschau), damit man sie nochmal abspielen oder echt starten kann.
            pl.clear()
            addMode = 0
            pushPoints()
            PilotSession.route = null
            showRoute(null)
        }
        markSim()
        updateButtons()
        refreshSheet()
        refreshOverlay()
        if (!PilotNav.simulated) runOnUiThread { checkPendingRide() }
    }

    // ---------------- GPS und Lebenslauf ----------------

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (io.github.th3s1nc.osmandhudbridge.pilot.SimGuard.ignoreGps(PilotNav.simulated, PilotNav.guiding)) return // nur während einer laufenden Simulation kommt die Position von dort
            if (location.hasAccuracy() && location.accuracy > 200f) return
            altitude = if (location.hasAltitude()) location.altitude else null
            val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            val rawBearing = if (location.hasBearing() && location.hasSpeed()) location.bearing else null
            val hdg = arrowFilter.updateHeading(rawBearing?.toDouble(), kmh)?.toFloat()
            onPosition(location.latitude, location.longitude, hdg, kmh, rawBearing, if (location.hasAccuracy()) location.accuracy else null, true)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val lm = getSystemService(LocationManager::class.java) ?: return
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
            locMgr = lm
            centerOnLastKnown()
        } catch (_: Exception) {
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (map != null && shownSignature != mapsSignature()) {
            loadStyle(appliedDark)
            if (lastPos == null && PilotSession.route == null) mapCenter()?.let { moveCam(it, null, 8.0, false) }
        }
        checkRegionUpdates()
        Baustellen.loadCached(this); pushBaustellen()
        Baustellen.refresh(this) { ch -> if (ch) runOnUiThread { if (!isFinishing && !isDestroyed) { pushBaustellen(); refreshSheet() } } }
        AutoSync.onOpen(this) { o ->
            when (o) {
                is AutoSync.Outcome.Restored -> { Toast.makeText(this, Tx.t("Einstellungen vom Server übernommen.", "Settings taken from the server."), Toast.LENGTH_SHORT).show(); recreate() }
                is AutoSync.Outcome.Conflict -> askSettingsConflict(o.remote)
                else -> {}
            }
        }
        Favs.syncAsync(this, 60_000L)
        val pt = pendingTour
        if (pt != null) { pendingTour = null; loadPlannerTour(pt) } 
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ticker.post(tick)
        friendHandler.removeCallbacks(friendRun); friendTick = 0; friendHandler.post(friendRun); FriendsAlarm.schedule(this)
        if (intent?.getBooleanExtra("open_friends", false) == true) { intent.removeExtra("open_friends"); chooseMenu(); menuFriends() }
        PilotNav.addListener(this)
        PilotNav.lastState?.let { lastState = it }
        refreshOverlay()
        checkPendingRide()
        if (simRunning) { simLast = SystemClock.elapsedRealtime(); ticker.removeCallbacks(simTick); ticker.post(simTick) }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) { startLocation(); askNotifications() }
        else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onPause() {
        PilotNav.removeListener(this)
        friendHandler.removeCallbacks(friendRun)
        ticker.removeCallbacks(tick)
        ticker.removeCallbacks(simTick)
        try { locMgr?.removeUpdates(locListener) } catch (_: Exception) {}
        locMgr = null
        io.github.th3s1nc.osmandhudbridge.pilot.GpsLog.flush(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() { mapView.onStop(); AutoSync.pushIfChanged(this); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }

    override fun onDestroy() {
        friendExec.shutdownNow()
        planExec.shutdownNow()
        searchExec.shutdownNow()
        fileExec.shutdownNow()
        planHandler.removeCallbacksAndMessages(null)
        ticker.removeCallbacksAndMessages(null)
        mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val HIST_MAX = 10

        /** Wo auf dem Bildschirm die eigene Position sitzt (von oben gerechnet): unten, damit man mehr von der Strecke voraus sieht. */
        const val CENTER_Y = 0.72f

        /** Querformat: Position etwas rechts von der Mitte (links steht die Abbiegeanzeige) und weiter unten. */
        const val CENTER_X_LAND = 0.60f
        const val CENTER_Y_LAND = 0.68f

        /** Querformat bei Navigation: Höhe des Trennstrichs zwischen ANKUNFT und VERBLEIBEND (ca. 70 % von oben). */
        const val NAV_Y_LAND = 0.70f
    }
}
