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

    /** O "Hello" dura 2 s, do início da primeira letra ao fim da última. */
    const val HelloDurationMs = 2000
    const val LetterStaggerMs = 100

    /** Espaçamento máximo entre letras do "Hello" (15 % do tamanho da letra), a fechar até zero. */
    const val HelloLetterSpacingEm = 0.15f

    const val ButtonsDelayMs = 1600
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

    /** Ecrã que recua por baixo de outro: zoom out até 92 % e um véu #010101 a 24 %. */
    const val BackgroundScale = 0.92f
    const val BackgroundOverlayAlpha = 0.24f

    /** Paginação horizontal (Introduction 2...): o ecrã que sai desloca-se 30 % da largura. */
    const val PageParallax = 0.3f

    /** Desaparecimento do texto "Scroll or tap to interact". */
    const val HintFadeDurationMs = 350

    /** Crescimento da barra de progresso. */
    val ProgressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 130f)
}
