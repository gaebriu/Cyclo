package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Tempos e curvas das animações. O Figma não os define, por isso são PROPOSTAS
 * para afinares à vista na app a correr.
 */
object CycloMotion {
    // Introduction 0
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

    /**
     * Subida e descida de ecrãs inteiros (Introduction 1...). Mola sem ressalto, ao estilo
     * de uma folha do iOS / Material You: reage ao toque e pode ser interrompida sem saltos.
     * O fade e o desfoque dos textos seguem o progresso desta mola, por isso têm a mesma duração.
     */
    val ScreenRiseSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 280f)

    /** Crescimento da barra de progresso. */
    val ProgressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 130f)
}
