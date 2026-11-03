package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign

/**
 * O motor de animações do Cyclo: as molas de todas as animações, num só sítio.
 *
 * Como no iOS (UIKit / SpringBoard), cada mola descreve-se pela [CycloSpring.response] (quanto tempo
 * leva, em segundos, um ciclo da mola sem amortecimento: o "peso" do movimento) e pela
 * [CycloSpring.dampingFraction] (1 = sem ressalto; abaixo de 1, um ressalto controlado). A rigidez
 * do Compose é (2π / response)².
 *
 * Regras da linguagem de movimento:
 * - Tudo o que o dedo larga parte com a velocidade dele (initialVelocity): sem saltos.
 * - Cada interação tem a sua mola (não a mesma para tudo): tocar responde depressa com um ressalto
 *   pequeno; ecrãs inteiros não ressaltam; o que entra pode passar um pouco e voltar; o que sai sai
 *   depressa e sem ressalto.
 * - Sem animações no sistema ([com.gaebriu.cyclo.ui.effects.VisualEffects.animationsEnabled]),
 *   usa-se [motionSpec], que dá [snap]: os movimentos passam a cortes ou fades.
 *
 * As molas que já existiam ([CycloMotion]) são estes presets, com os mesmos valores.
 */
class CycloSpring(val response: Float, val dampingFraction: Float) {
    /** Rigidez do Compose para esta resposta. */
    val stiffness: Float get() = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingFraction, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

    /** A mola para valores Float (a mais usada). */
    val float: SpringSpec<Float> by lazy { spec() }

    /** Para tamanhos e deslocações em píxeis (AnimatedVisibility, animateContentSize...). */
    val intSize: SpringSpec<IntSize> by lazy { spec(IntSize.VisibilityThreshold) }
    val intOffset: SpringSpec<IntOffset> by lazy { spec(IntOffset.VisibilityThreshold) }
}

/** Os presets, do mais rápido ao mais lento. */
object CycloSprings {
    /** Uma mola a partir da rigidez do Compose (para os valores que já estavam assim). */
    fun fromStiffness(stiffness: Float, dampingRatio: Float) =
        CycloSpring((2.0 * PI / kotlin.math.sqrt(stiffness.toDouble())).toFloat(), dampingRatio)

    /** Sair (menus a fechar, cartões apagados): depressa e sem ressalto. */
    val Exit = fromStiffness(700f, Spring.DampingRatioNoBouncy)

    /** Pegar num elemento (long press do arrastar): cresce logo, com um ressalto curto. */
    val Lift = CycloSpring(response = 0.25f, dampingFraction = 0.7f)

    /** Resposta ao toque (zoom dos botões): imediata, a assentar com um ligeiro ressalto. */
    val Press = fromStiffness(500f, 0.65f)

    /** Entrar (menus a abrir, cartões a voltar): rápido, com um ressalto muito leve. */
    val Enter = fromStiffness(520f, 0.78f)

    /** Pequenas mudanças de estado (interruptores, contadores). */
    val Snappy = fromStiffness(420f, 0.78f)

    /** Largar um elemento no seu lugar (fim do arrastar): encaixa com um ressalto subtil. */
    val Drop = CycloSpring(response = 0.32f, dampingFraction = 0.78f)

    /** Os vizinhos a abrir espaço (reordenar, apagar, repor): coordenado, quase sem ressalto. */
    val Reflow = CycloSpring(response = 0.34f, dampingFraction = 0.86f)

    /** Repor (o "Undo"): zoom com um overshoot controlado. */
    val PopIn = CycloSpring(response = 0.42f, dampingFraction = 0.66f)

    /** Mudar de página com o dedo (Feed ⇄ rotinas guardadas): segue a velocidade, sem ressalto visível. */
    val Page = CycloSpring(response = 0.36f, dampingFraction = 0.94f)

    /** Menus, folhas, teclado, a barra de baixo: suave. */
    val Overlay = fromStiffness(Spring.StiffnessMediumLow, 0.85f)

    /** Fades e mudanças de estado sem movimento. */
    val Gentle = fromStiffness(Spring.StiffnessMediumLow, Spring.DampingRatioNoBouncy)

    /**
     * action-menu-big a abrir (todas as folhas de baixo): sobe com peso e assenta com um ressalto
     * quase invisível. Começa com a velocidade do dedo quando volta depois de um arrastar.
     */
    val SheetOpen = CycloSpring(response = 0.42f, dampingFraction = 0.88f)

    /** action-menu-big a fechar: um pouco mais rápido do que a abrir, sem ressalto, com a velocidade do dedo. */
    val SheetClose = CycloSpring(response = 0.34f, dampingFraction = 1f)

    /** Ecrãs inteiros a subir e a descer (Introduction 1, editor, definições): sem ressalto. */
    val Sheet = fromStiffness(280f, Spring.DampingRatioNoBouncy)

    /** Regresso do scroll esticado (rubber band). */
    val Bounce = fromStiffness(200f, Spring.DampingRatioNoBouncy)
}

/** [spec] com as animações ligadas; um corte sem elas. */
fun <T> motionSpec(animationsEnabled: Boolean, spec: FiniteAnimationSpec<T>): AnimationSpec<T> =
    if (animationsEnabled) spec else snap()

/**
 * Resistência do iOS para lá de um limite (rubber band): cada vez anda menos do que o dedo.
 * [overflow] é o que o dedo passou do limite e [dimension] o tamanho de referência (ex.: a largura).
 */
fun rubberBand(overflow: Float, dimension: Float, coefficient: Float = 0.55f): Float {
    if (dimension <= 0f) return 0f
    return sign(overflow) * (1f - 1f / (abs(overflow) * coefficient / dimension + 1f)) * dimension
}

/**
 * Para onde vai um lançamento: a posição mais a distância que a velocidade ainda percorreria a
 * desacelerar como no iOS (taxa 0,998/ms), para escolher a página ou o lugar onde assentar.
 */
fun projectedEnd(position: Float, velocity: Float, decelerationRate: Float = 0.998f): Float =
    position + velocity / 1000f * decelerationRate / (1f - decelerationRate)
