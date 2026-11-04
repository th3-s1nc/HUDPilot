package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Laufende Führung: nimmt Positionen (echtes GPS aus dem HUD-Dienst oder die Simulation), lässt die [Guide]-Logik
 * rechnen, schickt Pfeil und Abstand ans HUD und berechnet bei Abweichung neu. Alles auf dem Hauptthread aufrufen.
 */
object PilotNav {
    interface Listener {
        /** Neue Anzeigedaten (Hauptthread). */
        fun onGuide(state: Guide.State)
        /** Nach Abweichung wurde eine neue Route berechnet. */
        fun onRerouted(route: PilotRoute)
        /** Führung beendet (Ziel erreicht oder gestoppt). */
        fun onStopped(arrived: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()

    @Volatile private var guide: Guide? = null
    @Volatile var simulated = false
        private set
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    /** Letzte Position, die der Führung gemeldet wurde (echt oder simuliert). */
    @Volatile var lastLat = 0.0
        private set
    @Volatile var lastLon = 0.0
        private set
    @Volatile var lastHeading: Int? = null
        private set
    var lastState: Guide.State? = null
        private set
    /** Kurzer Hinweistext (z. B. "Neuberechnung …"). */
    @Volatile var message: String = ""
        private set

    private var ctx: Context? = null
    private var profileId = ""
    private var rerouting = false
    private var lastNext: Instruction? = null
    private var goalSent = false
    private val voice = VoiceLogic()
    private var wasOff = false
    private var lastLogMs = 0L
    private var startMs = 0L

    val guiding: Boolean get() = guide != null

    /** Echte Fahrt (nicht Simulation): der HUD-Dienst hält dafür das GPS auf jede Sekunde. */
    val driving: Boolean get() = guide != null && !simulated

    fun start(context: Context, route: PilotRoute, wps: List<Waypoint>, profile: String, simulate: Boolean) {
        ctx = context.applicationContext
        profileId = profile
        simulated = simulate
        guide = Guide(route, wps)
        lastState = null
        parked = false
        gpsWarned = false
        lastNext = null
        goalSent = false
        rerouting = false
        message = ""
        wasOff = false
        lastLogMs = 0L
        startMs = android.os.SystemClock.elapsedRealtime()
        voice.reset()
        PilotVoice.ensure(context)
        BridgeBus.log(
            Tx.t("Navigation gestartet: ", "Navigation started: ") + "${route.totalM} m, ${route.instructions.size} " +
                Tx.t("Hinweise", "instructions") + ", ${wps.size} " + Tx.t("Wegpunkte", "waypoints") + ", " +
                Tx.t("Profil ", "profile ") + profile + if (simulate) Tx.t(", Simulation", ", simulation") else ""
        )
    }

    private var gpsWarned = false

    /** Abseits der Route, aber stehend oder im Schritttempo (Parkplatz): keine Neuberechnung. */
    @Volatile var parked = false
        private set

    /** Echte Fahrt ohne erste Position: nach 20 s eine Logzeile (einmal). Vom Dienst-Takt aufgerufen. */
    fun watchGps() {
        if (!driving || lastState != null || gpsWarned) return
        if (android.os.SystemClock.elapsedRealtime() - startMs < 20_000) return
        gpsWarned = true
        BridgeBus.log(Tx.t("Navigation: seit über 20 s keine GPS-Position empfangen", "Navigation: no GPS position received for over 20 s"))
    }

    fun stop(arrived: Boolean = false, reason: String = "") {
        val had = guide != null
        if (had) {
            val mins = (android.os.SystemClock.elapsedRealtime() - startMs) / 60_000
            BridgeBus.log(
                (if (arrived) Tx.t("Navigation beendet: Ziel erreicht", "Navigation ended: arrived")
                else Tx.t("Navigation beendet", "Navigation ended") + if (reason.isNotEmpty()) " ($reason)" else "") +
                    Tx.t(" nach $mins min", " after $mins min")
            )
        }
        guide = null
        rerouting = false
        message = ""
        if (had && !arrived) { PilotBus.clear(); PilotVoice.silence() }
        if (had) for (l in listeners) l.onStopped(arrived)
        // Erst nach den Listenern (sie fragen noch "war es eine Simulation?"). Ein hängengebliebener Schalter ließ das echte GPS ignorieren.
        simulated = false
    }

    /**
     * Eine Position. speedKmh/headingDeg können fehlen (null). timeFactor: Zeitraffer der Simulation (für die Ankunftszeit).
     */
    fun onFix(
        lat: Double, lon: Double, speedKmh: Float?, headingDeg: Int?, timeFactor: Double = 1.0,
        nowMs: Long = android.os.SystemClock.elapsedRealtime(), accuracyM: Float? = null
    ) {
        val g = guide ?: return
        val st = g.update(lat, lon, accuracyM)
        lastState = st
        logProgress(g, st, speedKmh, accuracyM, nowMs)
        lastLat = lat; lastLon = lon
        if (headingDeg != null) lastHeading = headingDeg
        if (st.finished) {
            if (!goalSent) {
                goalSent = true
                PilotBus.send(PilotBus.Update(null, null, null, null, null, null, null, goal = true))
            }
            for (l in listeners) l.onGuide(st)
            for (t in voice.step(null, 0, true, speedKmh, false, nowMs)) speak(t, 0, speedKmh)
            stop(arrived = true)
            return
        }
        // Abseits der Route und (fast) stehend, z. B. auf einem Parkplatz: nicht neu berechnen, erst wenn es wieder losgeht
        parked = st.offRoute && speedKmh != null && speedKmh < 8f
        if (g.shouldReroute(nowMs) && !rerouting && !parked) reroute(g, lat, lon, nowMs, if (speedKmh != null && speedKmh >= 10f) (headingDeg ?: lastHeading) else null)
        voice.many = PilotVoice.many()
        val ins = g.route.instructions
        val ni = if (st.next != null) ins.indexOf(st.next) else -1
        val after = if (ni >= 0 && ni + 1 < ins.size) ins[ni + 1] else null
        for (t in voice.step(st.next, st.nextDistM, false, speedKmh, rerouting, nowMs, after)) speak(t, st.nextDistM, speedKmh)
        val next = st.next
        val via = lastNext?.brCmd == BrouterCmd.VIA && next != lastNext
        lastNext = next
        val etaSec = (st.remainingSec / timeFactor).toInt()
        val arr = Calendar.getInstance().apply { add(Calendar.SECOND, etaSec) }
        PilotBus.send(
            PilotBus.Update(
                meters = st.nextDistM,
                command = next?.command,
                exit = if (next != null && next.command?.hasExit == true) next.exit else null,
                routeDistM = st.remainingM,
                routeMinutes = (etaSec / 60.0).roundToInt(),
                speedKmh = speedKmh,
                headingDeg = headingDeg,
                via = via,
                arrivalHour = arr.get(Calendar.HOUR_OF_DAY),
                arrivalMinute = arr.get(Calendar.MINUTE),
                laneCount = next?.lanes ?: 0,
                brCmd = next?.brCmd ?: 0
            )
        )
        for (l in listeners) l.onGuide(st)
    }

    /** Jede Ansage kommt mit Text, Abstand zur Abbiegung und Tempo ins Protokoll. */
    private fun speak(text: String, distM: Int, speedKmh: Float?) {
        BridgeBus.log(Tx.t("Ansage: \"$text\" (Abbiegung in $distM m, ", "Announcement: \"$text\" (turn in $distM m, ") + "${speedKmh?.roundToInt() ?: 0} km/h)")
        PilotVoice.say(text)
    }

    /** Protokoll der Fahrt: Wechsel auf/neben der Route sofort, sonst alle 15 Sekunden eine Zeile. */
    private fun logProgress(g: Guide, st: Guide.State, speedKmh: Float?, accuracyM: Float?, nowMs: Long) {
        val acc = accuracyM?.roundToInt()?.let { Tx.t(", Genauigkeit $it m", ", accuracy $it m") } ?: ""
        if (st.offRoute != wasOff) {
            wasOff = st.offRoute
            BridgeBus.log(
                if (st.offRoute) Tx.t("Neben der Route: ${st.lateralM} m$acc", "Off route: ${st.lateralM} m$acc")
                else Tx.t("Wieder auf der Route: ${st.lateralM} m$acc", "Back on route: ${st.lateralM} m$acc")
            )
        }
        if (nowMs - lastLogMs >= 15_000L) {
            lastLogMs = nowMs
            val nx = st.next
            BridgeBus.log(
                Tx.t("Fahrt: ", "Ride: ") + "${st.progressM}/${g.route.totalM} m, " +
                    Tx.t("Abstand zur Route ", "distance to route ") + "${st.lateralM} m$acc, " +
                    (if (nx != null) Tx.t("nächster Hinweis: ", "next: ") + nx.text() + Tx.t(" in ", " in ") + "${st.nextDistM} m, " +
                        (nx.command?.let { Tx.t("HUD-Pfeil ", "HUD arrow ") + it.name + " (" + it.id + "), " } ?: "") else "") +
                    "${speedKmh?.roundToInt() ?: 0} km/h" +
                    if (g.offFixes > 0) ", " + Tx.t("daneben-Zähler ", "off-count ") + g.offFixes else ""
            )
        }
    }

    private fun reroute(g: Guide, lat: Double, lon: Double, nowMs: Long, heading: Int?) {
        val c = ctx ?: return
        val wps = g.remainingWaypoints(lat, lon)
        BridgeBus.log(
            Tx.t("Neuberechnung gestartet: ", "Recalculation started: ") + "${wps.size} " + Tx.t("Wegpunkte", "waypoints") +
                Tx.t(", nächste Wartezeit ", ", next wait ") + "${g.cooldownMs() / 1000} s" +
                (if (heading != null) Tx.t(", Fahrtrichtung $heading°", ", heading $heading°") else "")
        )
        rerouting = true
        message = Tx.t("Abweichung – berechne neu …", "Off route – recalculating …")
        val profile = profileId
        val t0 = android.os.SystemClock.elapsedRealtime()
        exec.execute {
            val out = PilotRouter.compute(c, wps, profile, startDirection = if (heading != null) ((heading % 360) + 360) % 360 else -1)
            main.post {
                val took = android.os.SystemClock.elapsedRealtime() - t0
                rerouting = false
                if (guide !== g) return@post // inzwischen beendet
                when (out) {
                    is PilotRouter.Outcome.Ok -> {
                        g.setRoute(out.route, wps, android.os.SystemClock.elapsedRealtime())
                        voice.newRoute()
                        BridgeBus.log(
                            Tx.t("Neue Route: ", "New route: ") + "${out.route.totalM} m, ${out.route.instructions.size} " +
                                Tx.t("Hinweise", "instructions") + ", $took ms"
                        )
                        PilotSession.route = out.route
                        PilotSession.routeWaypoints = wps
                        lastNext = null
                        message = ""
                        for (l in listeners) l.onRerouted(out.route)
                    }
                    is PilotRouter.Outcome.Failed -> {
                        g.rerouteFailed(android.os.SystemClock.elapsedRealtime())
                        BridgeBus.log(Tx.t("Neuberechnung fehlgeschlagen: ", "Recalculation failed: ") + out.message + " ($took ms)")
                        message = Tx.t("Neuberechnung fehlgeschlagen: ", "Recalculation failed: ") + out.message
                    }
                }
            }
        }
    }
}
