package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O fim da subida de um ecrã (e das páginas e das folhas) não pode saltar. A mola acaba quando fica
 * perto do alvo e, nesse fotograma, salta o que falta: esse salto tem de ficar abaixo de um quarto
 * de píxel, mesmo num ecrã alto.
 *
 * Cyclo 3.2.3: testa as molas que a app USA (antes construía-se aqui uma mola com o limiar, mas a
 * app passava ao animateTo a mola sem ele, e o 0,01 do Compose fazia o "corte" de ~24 px no topo).
 */
class RiseEndTest {
    private fun lastJumpPx(spec: FiniteAnimationSpec<Float>, heightPx: Float, frameNs: Long): Float {
        val rise = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f, 0f)
        var t = 0L
        var last = 0f
        while (t + frameNs < rise.durationNanos) {
            t += frameNs
            last = rise.getValueFromNanos(t)
        }
        return (1f - last) * heightPx
    }

    @Test
    fun theLastFrameOfARiseDoesNotJump() {
        // O caminho de cada uma: um ecrã inteiro (alto) ou uma folha (a altura dela e a margem de baixo).
        val screens = listOf(2400f, 3200f)
        val sheets = listOf(900f, 1600f)
        val springs = mapOf(
            "ScreenRiseSpring" to (CycloMotion.ScreenRiseSpring to screens),
            "Page" to (CycloSprings.Page.precise to screens),
            "SheetOpen" to (CycloSprings.SheetOpen.precise to sheets),
            "SheetClose" to (CycloSprings.SheetClose.precise to sheets),
        )
        for ((name, pair) in springs) {
            val (spec, travels) = pair
            for (heightPx in travels) {
                for (frameNs in listOf(16_666_667L, 8_333_333L)) {
                    val jump = lastJumpPx(spec, heightPx, frameNs)
                    assertTrue("$name: salto final de $jump px em $heightPx px", jump < 0.25f)
                }
            }
        }
    }

    /** A mola sem limiar (o que a app usava até à 3.2.2) salta mesmo: o teste apanha-o. */
    @Test
    fun theDefaultThresholdWouldJump() {
        assertTrue(lastJumpPx(CycloSprings.Sheet.float, 2400f, 8_333_333L) > 5f)
    }
}
