package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.RegionLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFmtTest {
    @Test fun tempoText() {
        assertEquals("12.4 MB/s", RegionLogic.fmtSpeed(12_400_000))
        assertEquals("850 kB/s", RegionLogic.fmtSpeed(850_000))
        assertEquals("", RegionLogic.fmtSpeed(0))
    }
    @Test fun glaettung() {
        assertEquals(5_000_000L, RegionLogic.smoothSpeed(0, 5_000_000))
        assertEquals(4_000_000L, RegionLogic.smoothSpeed(2_000_000, 6_000_000))
    }
}
