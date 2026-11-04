package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.SimGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimGuardTest {
    @Test fun nurWaehrendLaufenderSimulationIgnorieren() {
        assertTrue(SimGuard.ignoreGps(simulated = true, guiding = true))
    }
    @Test fun haengengebliebenerSchalterBlockiertGpsNicht() {
        assertFalse(SimGuard.ignoreGps(simulated = true, guiding = false))
    }
    @Test fun echteFahrtUndFreiesFahren() {
        assertFalse(SimGuard.ignoreGps(simulated = false, guiding = true))
        assertFalse(SimGuard.ignoreGps(simulated = false, guiding = false))
    }
}
