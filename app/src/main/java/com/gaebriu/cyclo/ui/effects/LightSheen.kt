package com.gaebriu.cyclo.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.components.roundRectPath
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Reflexo de luz das superfícies de vidro (menus, folhas, barra de baixo, barra de navegação, Next):
 * enquanto o dedo está na superfície, uma luz suave e difusa acende-se por baixo dele e segue-o.
 *
 * - É luz do próprio material: um gradiente radial largo, #FCFCFE a poucos por cento, recortado à
 *   forma, desenhado por cima do vidro e por baixo do conteúdo (os textos ficam iguais).
 * - Segue o dedo com uma mola quase imediata ([CycloSprings.LightFollow]): alisa o movimento, sem
 *   atraso percetível e sem parecer um cursor. Acende com [CycloSprings.LightIn] e dissipa-se mais
 *   devagar com [CycloSprings.LightOut].
 * - Só observa os toques (passagem Initial, sem os consumir): os botões, o arrastar das folhas e o
 *   scroll continuam a receber tudo como antes.
 * - Fica numa camada própria (graphicsLayer): mexer a luz só volta a gravar essa camada, não o
 *   desfoque de fundo da superfície, que é o mais caro.
 * - Sem animações no sistema, a luz salta para o dedo e acende/apaga sem mola.
 */

/** O estado da luz de uma superfície. */
@Stable
private class SheenState {
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    val intensity = Animatable(0f)
}

/**
 * Luz que segue o dedo sobre esta superfície (ver o início do ficheiro).
 *
 * @param cornerRadius o raio da forma, para a luz não sair dela.
 * @param strength 1 = a intensidade das superfícies grandes (menus, folhas); menos nas pequenas.
 */
@Composable
fun Modifier.cycloLightSheen(cornerRadius: Dp, strength: Float = 1f): Modifier {
    val effects = LocalVisualEffects.current
    val state = remember { SheenState() }
    val scope = rememberCoroutineScope()
    val animate = effects.animationsEnabled
    return this
        .pointerInput(animate) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { state.position.snapTo(down.position) }
                scope.fade(state, 1f, animate)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val target = change.position
                    scope.launch {
                        if (animate) {
                            state.position.animateTo(target, CycloSprings.LightFollow.spec(Offset.VisibilityThreshold))
                        } else {
                            state.position.snapTo(target)
                        }
                    }
                }
                scope.fade(state, 0f, animate)
            }
        }
        // Camada própria: só ela se volta a gravar enquanto a luz se mexe.
        .graphicsLayer()
        .drawBehind {
            val amount = state.intensity.value
            if (amount <= 0.001f) return@drawBehind
            val center = state.position.value
            val radius = maxOf(LightRadius.toPx(), size.minDimension * 0.9f)
            val alpha = LightAlpha * strength * amount
            val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
            clipPath(shape) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to CycloColors.TextPrimary.copy(alpha = alpha),
                        0.45f to CycloColors.TextPrimary.copy(alpha = alpha * 0.35f),
                        1f to CycloColors.TextPrimary.copy(alpha = 0f),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
}

private fun CoroutineScope.fade(state: SheenState, target: Float, animate: Boolean) {
    launch {
        if (animate) {
            val spring = if (target > 0f) CycloSprings.LightIn else CycloSprings.LightOut
            state.intensity.animateTo(target, spring.float)
        } else {
            state.intensity.snapTo(target)
        }
    }
}

/** Raio mínimo da luz: larga e difusa, nunca um ponto. */
private val LightRadius = 120.dp

/** Opacidade do centro da luz (#FCFCFE) nas superfícies grandes. */
private const val LightAlpha = 0.09f
