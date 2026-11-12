package com.gaebriu.cyclo.ui.components

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloSpring
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** À volta de que ponto o ícone roda: o centro, o topo (pendurado) ou a ponta esquerda (a etiqueta). */
enum class IconPivot { Center, Top, Left }

/**
 * A entrada de cada ícone das Introduction (Beta 3.1, escolhida pelo utilizador): de onde parte a
 * rotação (graus) e a escala, com que mola, e o empurrão de uma interação ([IconMotion.nudge], em °/s).
 */
enum class IconMotionStyle(
    val pivot: IconPivot,
    val fromRotation: Float,
    val fromScale: Float,
    val spring: CycloSpring?,
    val scaleSpring: CycloSpring?,
    val nudgeVelocity: Float,
) {
    /** Cama: só o holofote. */
    SpotlightOnly(IconPivot.Center, 0f, 1f, null, null, 0f),

    /** Sol: "dar corda", mais um quarto de volta a abrandar até ao ângulo das rodas. */
    WindUp(IconPivot.Center, -90f, 0.85f, CycloSprings.IconWindUp, CycloSprings.IconWindUp, 0f),

    /** Etiqueta (um marcador): pendurada pelo topo, baloiça e assenta; a primeira letra dá-lhe um toque (~3°). */
    Swing(IconPivot.Top, 10f, 1f, CycloSprings.IconSwing, null, 30f),

    /** Calendário: pendurado num prego, oscila ±4°; abrir o "When to show" volta a mexê-lo (~2,5°). */
    Wobble(IconPivot.Top, -4f, 0.9f, CycloSprings.IconWobble, CycloSprings.Enter, 30f),

    /** Widgets: encaixa na grelha (0,8 → 1, −12° → 0). */
    Snap(IconPivot.Center, -12f, 0.8f, CycloSprings.Drop, CycloSprings.Drop, 0f),

    /** Sino: toca, 2 a 3 oscilações de ±14°; ligar um lembrete fá-lo tocar outra vez, suave (~6°). */
    Ring(IconPivot.Top, 14f, 1f, CycloSprings.IconSwing, null, 60f),
}

/**
 * O movimento de um ícone com holofote ([IntroIconHeader]): três canais lidos só ao desenhar
 * (rotação e escala do ícone, luz do holofote), por cima do que o ecrã já faz (o sol continua a
 * rodar com as rodas). Em repouso: 0°, 1 e 1.
 *
 * - [enter]: só ao avançar (quem chama decide pela direção). O holofote entra igual em todas as
 *   páginas: abre de 90 % e acende ([CycloSprings.Reveal]). Um tique quando o ícone chega ao lugar
 *   pela primeira vez (passa pelo repouso).
 * - [settle]: uma interrupção (sair da página, mexer no controlo) nunca corta: cada canal continua
 *   de onde está, com a velocidade que tem, numa mola rápida e sem ressalto até ao repouso.
 * - [nudge]: uma interação dá um empurrão à rotação, somado à velocidade que já tiver.
 *
 * As corrotinas correm no [scope] do fluxo (não no de um efeito nem de uma página): avançar
 * depressa não deixa nenhuma animação congelada a meio.
 */
@Stable
class IconMotion(val style: IconMotionStyle, private val scope: CoroutineScope, private val view: View) {
    val rotation = Animatable(0f)
    val scale = Animatable(1f)
    val light = Animatable(1f)

    // Já a assentar: chamadas seguidas (o sol, a cada fotograma da roda) não relançam nada.
    private var settling = false

    fun enter() {
        settling = false
        scope.launch {
            light.snapTo(0f)
            light.animateTo(1f, CycloSprings.Reveal.float)
        }
        style.scaleSpring?.let { spring ->
            scope.launch {
                scale.snapTo(style.fromScale)
                scale.animateTo(1f, spring.float)
            }
        }
        val spring = style.spring ?: return
        scope.launch {
            rotation.snapTo(style.fromRotation)
            var settled = false
            rotation.animateTo(0f, spring.float) {
                if (!settled && value * style.fromRotation <= 0f) {
                    settled = true
                    CycloHaptics.iconSettle(view)
                }
            }
        }
    }

    fun settle() {
        if (settling) return
        settling = true
        if (rotation.isRunning) scope.launch { rotation.animateTo(0f, CycloSprings.Exit.float) }
        if (scale.isRunning) scope.launch { scale.animateTo(1f, CycloSprings.Exit.float) }
        if (light.isRunning) scope.launch { light.animateTo(1f, CycloSprings.Exit.float) }
    }

    fun nudge() {
        val spring = style.spring ?: return
        if (style.nudgeVelocity == 0f) return
        settling = false
        scope.launch { rotation.animateTo(0f, spring.float, initialVelocity = rotation.velocity + style.nudgeVelocity) }
    }
}
