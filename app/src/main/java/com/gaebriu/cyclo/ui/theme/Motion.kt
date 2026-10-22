package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * Tempos e curvas das animações. O Figma não os define, por isso são PROPOSTAS
 * para afinares à vista na app a correr.
 */
object CycloMotion {
    const val GlowDurationMs = 1800
    const val LettersStartDelayMs = 250
    const val LetterStaggerMs = 90
    const val LetterDurationMs = 650
    const val ButtonsDelayMs = 1150
    const val ButtonsDurationMs = 700

    /** Distância de onde os botões sobem. */
    val ButtonsRise = 72.dp

    /** Desaceleração suave (Material 3, "emphasized decelerate"). */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
