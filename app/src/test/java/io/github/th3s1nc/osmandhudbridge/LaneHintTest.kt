package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd
import io.github.th3s1nc.osmandhudbridge.pilot.LaneHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneHintTest {
    @Test
    fun hinweisNurBeiMehrerenSpurenUndAbbiegen() {
        assertTrue(LaneHint.text(3, BrouterCmd.TR)!!.startsWith("3 "))
        assertNotNull(LaneHint.text(2, BrouterCmd.TL))
        assertNull(LaneHint.text(1, BrouterCmd.TR))
        assertNull(LaneHint.text(0, BrouterCmd.TR))
        assertNull(LaneHint.text(3, BrouterCmd.C))
        assertNull(LaneHint.text(3, BrouterCmd.RNDB))
    }

    @Test
    fun spurenFuersHud() {
        val r = LaneHint.hudLanes(3, BrouterCmd.TR, 300)
        assertEquals(3, r.size)
        assertEquals(io.github.th3s1nc.osmandhudbridge.protocol.LaneDirection.RIGHT, r[2].directions)
        assertEquals(io.github.th3s1nc.osmandhudbridge.protocol.LaneRecommendation.HIGHLY_RECOMMENDED, r[2].recommendation)
        assertEquals(io.github.th3s1nc.osmandhudbridge.protocol.LaneDirection.STRAIGHT, r[0].directions)
        val l = LaneHint.hudLanes(2, BrouterCmd.TL, 100)
        assertEquals(io.github.th3s1nc.osmandhudbridge.protocol.LaneDirection.LEFT, l[0].directions)
        assertTrue(LaneHint.hudLanes(3, BrouterCmd.TR, 401).isEmpty())
        assertTrue(LaneHint.hudLanes(1, BrouterCmd.TR, 100).isEmpty())
        assertTrue(LaneHint.hudLanes(3, BrouterCmd.C, 100).isEmpty())
    }

    @Test
    fun seiteUndZoom() {
        assertEquals(1, LaneHint.side(BrouterCmd.ER))
        assertEquals(-1, LaneHint.side(BrouterCmd.EL))
        assertTrue(LaneHint.zoomsIn(BrouterCmd.TR))
        assertFalse(LaneHint.zoomsIn(BrouterCmd.C))
        assertFalse(LaneHint.zoomsIn(BrouterCmd.END))
        assertFalse(LaneHint.zoomsIn(BrouterCmd.VIA))
        assertEquals(17.5, LaneHint.zoomLevel(BrouterCmd.TR, 2), 0.0)
        assertEquals(18.0, LaneHint.zoomLevel(BrouterCmd.TR, 3), 0.0)
        assertEquals(18.0, LaneHint.zoomLevel(BrouterCmd.KR, 1), 0.0)
    }
}
