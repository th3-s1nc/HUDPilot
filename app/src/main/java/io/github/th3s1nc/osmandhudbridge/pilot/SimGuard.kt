package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Wann das echte GPS ignoriert werden darf: nur, solange wirklich eine Simulation läuft (Simulation an UND Navigation aktiv).
 * Ein hängengebliebener Simulations-Schalter ohne laufende Navigation darf das GPS nie blockieren.
 */
object SimGuard {
    fun ignoreGps(simulated: Boolean, guiding: Boolean): Boolean = simulated && guiding
}
