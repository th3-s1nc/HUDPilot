package io.github.th3s1nc.osmandhudbridge.pilot

import io.github.th3s1nc.osmandhudbridge.protocol.NavCommand

/**
 * Brücke von HUDPilot (Simulation, später echte Führung) zum HUD-Dienst.
 * Der Dienst trägt sich als sink ein. Der Dienst zeigt auf dem HUD, was Pilot schickt.
 */
object PilotBus {
    data class Update(
        val meters: Int?, val command: NavCommand?, val exit: Int?,
        val routeDistM: Int?, val routeMinutes: Int?,
        val speedKmh: Float?, val headingDeg: Int?,
        val goal: Boolean = false, val via: Boolean = false, val clear: Boolean = false,
        val arrivalHour: Int? = null, val arrivalMinute: Int? = null,
        /** Spuranzahl und BRouter-Befehl der nächsten Anweisung (für die Spurinfo am HUD); 0 = unbekannt. */
        val laneCount: Int = 0, val brCmd: Int = 0
    )

    @Volatile var sink: ((Update) -> Unit)? = null
    @Volatile private var lastAt = 0L

    /** Pilot hat in den letzten 20 s etwas geschickt. */
    fun active(now: Long = System.currentTimeMillis()): Boolean = now - lastAt < 20_000L

    fun send(u: Update) {
        lastAt = if (u.clear) 0L else System.currentTimeMillis()
        sink?.invoke(u)
    }

    // Tunnel-Kennzeichen der Straße, auf der das Tempolimit-Modul das Fahrzeug gerade sieht (für den Nachtmodus der Karte)
    @Volatile private var tunnelRoad = false
    @Volatile private var tunnelAt = 0L

    fun setRoadTunnel(v: Boolean, now: Long = System.currentTimeMillis()) { tunnelRoad = v; tunnelAt = now }

    /** true, wenn die zugeordnete Straße ein Tunnel ist und die Angabe frisch ist (höchstens 6 s alt). */
    fun roadTunnel(now: Long = System.currentTimeMillis()): Boolean = tunnelRoad && now - tunnelAt < 6_000L

    // Tempolimit der Straße (vom Tempolimit-Modul) und Warnpunkte in der Nähe, für die Anzeige in der Karte
    @Volatile private var limitKmh = 0
    @Volatile private var limitAt = 0L

    fun setLimit(kmh: Int, now: Long = System.currentTimeMillis()) { limitKmh = kmh; limitAt = now }

    /** Aktuelles Tempolimit in km/h; 0 = unbekannt oder die Angabe ist älter als 6 s. */
    fun limit(now: Long = System.currentTimeMillis()): Int = if (now - limitAt < 6_000L) limitKmh else 0

    @Volatile var hazards: List<Hazard> = emptyList()
        private set
    @Volatile private var hazardsAt = 0L

    fun setHazards(list: List<Hazard>, now: Long = System.currentTimeMillis()) { hazards = list; hazardsAt = now }

    /** Warnpunkte, solange die Angabe frisch ist (höchstens 15 s alt). */
    fun freshHazards(now: Long = System.currentTimeMillis()): List<Hazard> = if (now - hazardsAt < 15_000L) hazards else emptyList()

    fun clear() = send(Update(null, null, null, null, null, null, null, clear = true))
}
