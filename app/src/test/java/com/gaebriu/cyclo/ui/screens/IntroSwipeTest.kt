package com.gaebriu.cyclo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A decisão do gesto das Introduction ao largar (beta-3.2) e a curva do indicador. */
class IntroSwipeTest {
    @Test
    fun slowShortDragGoesBack() {
        // Um quinto do ecrã, a largar devagar: volta.
        assertEquals(0, introSwipeDecision(offset = 0.2f, velocity = 0.1f))
        assertEquals(0, introSwipeDecision(offset = -0.2f, velocity = -0.1f))
    }

    @Test
    fun pastHalfChanges() {
        assertEquals(1, introSwipeDecision(offset = 0.6f, velocity = 0f))
        assertEquals(-1, introSwipeDecision(offset = -0.6f, velocity = 0f))
    }

    @Test
    fun flickChangesEvenWhenShort() {
        assertEquals(1, introSwipeDecision(offset = 0.05f, velocity = 1.2f))
        assertEquals(-1, introSwipeDecision(offset = -0.05f, velocity = -1.2f))
    }

    @Test
    fun releasingWhileGoingBackCancels() {
        // Passou de meia página mas largou a voltar para trás: a projeção fica antes do meio.
        assertEquals(0, introSwipeDecision(offset = 0.6f, velocity = -0.4f))
        // Um lançamento contra o deslocamento nunca muda para o outro lado.
        assertEquals(0, introSwipeDecision(offset = 0.1f, velocity = -0.8f))
    }

    @Test
    fun projectionCountsMomentum() {
        // 0,35 de página a 0,4 páginas/s: projeta ~0,55, muda.
        assertEquals(1, introSwipeDecision(offset = 0.35f, velocity = 0.4f))
    }

    @Test
    fun hintLiftRestsAndPeaks() {
        assertEquals(0f, hintLift(0f), 1e-4f)
        assertEquals(1f, hintLift(0.35f), 1e-3f)
        assertEquals(0f, hintLift(0.8f), 1e-3f)
        assertEquals(0f, hintLift(0.9f), 0f)
        // Periódica.
        assertEquals(hintLift(0.2f), hintLift(1.2f), 1e-4f)
        for (i in 0..100) {
            val v = hintLift(i / 100f)
            assertTrue(v in 0f..1f)
        }
    }
}
