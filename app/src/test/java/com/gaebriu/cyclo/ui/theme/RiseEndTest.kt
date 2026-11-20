package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cyclo 3.2.2: o fim da subida de um ecrã (e das páginas) não pode saltar. A mola acaba quando fica a
 * menos de [CycloMotion.PageThreshold] do alvo e, nesse fotograma, salta o que falta: esse salto tem
 * de ficar abaixo de um quarto de píxel, mesmo num ecrã alto (com 0,0005 era ~1,3 px em 2400 px,
 * depois de passos de ~0,17 px: o "teletransporte" no topo).
 */
class RiseEndTest {
    @Test
    fun theLastFrameOfARiseDoesNotJump() {
        for (heightPx in listOf(2400f, 3200f)) {
            for (frameNs in listOf(16_666_667L, 8_333_333L)) {
                val spec = CycloSprings.Sheet.spec(CycloMotion.PageThreshold)
                val rise = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f, 0f)
                var t = 0L
                var last = 0f
                while (t + frameNs < rise.durationNanos) {
                    t += frameNs
                    last = rise.getValueFromNanos(t)
                }
                val snapPx = (1f - last) * heightPx
                assertTrue("salto final de $snapPx px em $heightPx px", snapPx < 0.25f)
            }
        }
    }
}
