package com.gaebriu.cyclo.ui

import com.gaebriu.cyclo.ui.effects.FigmaNoise
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FigmaNoiseTest {
    @Test
    fun grainIsSparseLikeTheFigmaRender() {
        // A "densidade 0,15" do Figma não é a fração de píxeis: a faixa [0,21; 0,29) da luminância
        // da turbulência fica na cauda da distribuição e dá grãos soltos, ~1 % dos píxeis, como no
        // render a 4x do fx spotlight (Cyclo-2 8:133). Medido: 1,1 %.
        val size = 256
        val mask = FigmaNoise.grainMask(size, size, pxPerUnit = 2.0)
        val fraction = mask.count { it }.toDouble() / mask.size
        assertTrue("fraction $fraction", fraction in 0.005..0.03)
    }

    @Test
    fun sameSeedSameGrain() {
        assertArrayEquals(FigmaNoise.grainMask(64, 64, 2.625), FigmaNoise.grainMask(64, 64, 2.625))
    }
}
