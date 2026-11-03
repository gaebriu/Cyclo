package com.gaebriu.cyclo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A solução exata das molas, usada para sincronizar a argola e as letras num só relógio. */
class SpringProgressTest {
    @Test
    fun startsAtZeroAndSettlesAtOne() {
        val spring = CycloSprings.Focus
        assertEquals(0f, springProgress(0f, spring), 0f)
        assertEquals(1f, springProgress(5f, spring), 1e-4f)
    }

    @Test
    fun criticallyDampedNeverOvershoots() {
        var last = 0f
        for (i in 1..400) {
            val p = springProgress(i / 100f, CycloSprings.Focus, velocity = 2.4f)
            assertTrue("t=${i / 100f}: $p", p <= 1f + 1e-6f && p >= last - 1e-6f)
            last = p
        }
    }

    @Test
    fun matchesTheInitialVelocity() {
        // Logo no início, anda à velocidade pedida.
        val dt = 1e-4f
        assertEquals(3f, springProgress(dt, CycloSprings.Focus, velocity = 3f) / dt, 0.05f)
    }

    @Test
    fun underdampedSpringOvershootsALittle() {
        val p = (1..300).maxOf { springProgress(it / 100f, CycloSprings.PopIn) }
        assertTrue(p > 1f && p < 1.2f)
    }

    @Test
    fun smoothStepHasFlatEnds() {
        assertEquals(0f, smoothStep(0f), 0f)
        assertEquals(0.5f, smoothStep(0.5f), 1e-6f)
        assertEquals(1f, smoothStep(2f), 0f)
    }
}
