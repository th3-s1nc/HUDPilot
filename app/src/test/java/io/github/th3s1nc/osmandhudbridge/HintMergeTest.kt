package io.github.th3s1nc.osmandhudbridge

import io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd
import io.github.th3s1nc.osmandhudbridge.pilot.HintMerge
import io.github.th3s1nc.osmandhudbridge.pilot.Instruction
import org.junit.Assert.assertEquals
import org.junit.Test

class HintMergeTest {
    private fun ins(at: Int, cmd: Int) = Instruction(0, at, cmd, 0, BrouterCmd.toHud(cmd, 0))

    @Test
    fun leichteKurvenVorKreisverkehrFallenWeg() {
        val l = listOf(ins(1000, BrouterCmd.TSLR), ins(1070, BrouterCmd.TSLR), ins(1100, BrouterCmd.RNDB))
        val r = HintMerge.simplify(l)
        assertEquals(listOf(BrouterCmd.RNDB), r.map { it.brCmd })
    }

    @Test
    fun einzelneLeichteKurveBleibt() {
        val l = listOf(ins(1000, BrouterCmd.TSLR), ins(3000, BrouterCmd.TL))
        assertEquals(2, HintMerge.simplify(l).size)
    }

    @Test
    fun zweiGleicheLeichteKurvenWerdenEine() {
        val l = listOf(ins(1000, BrouterCmd.TSLR), ins(1080, BrouterCmd.TSLR), ins(3000, BrouterCmd.TL))
        val r = HintMerge.simplify(l)
        assertEquals(listOf(1000, 3000), r.map { it.atM })
    }

    @Test
    fun verschiedeneRichtungenBleiben() {
        val l = listOf(ins(1000, BrouterCmd.TSLR), ins(1080, BrouterCmd.TSLL), ins(5000, BrouterCmd.TL))
        assertEquals(3, HintMerge.simplify(l).size)
    }

    @Test
    fun leichteKurveVorAbbiegungWeitWegBleibt() {
        val l = listOf(ins(1000, BrouterCmd.TSLR), ins(1400, BrouterCmd.TR))
        assertEquals(2, HintMerge.simplify(l).size)
    }

    @Test
    fun leichteKurveVorAbbiegungDichtFaelltWeg() {
        val l = listOf(ins(1000, BrouterCmd.TSLL), ins(1200, BrouterCmd.TR))
        assertEquals(listOf(BrouterCmd.TR), HintMerge.simplify(l).map { it.brCmd })
    }

    @Test
    fun geradeausUndZielBleiben() {
        val l = listOf(ins(1000, BrouterCmd.TSLR), ins(1100, BrouterCmd.C), ins(1150, BrouterCmd.END))
        assertEquals(3, HintMerge.simplify(l).size)
    }
}
