package com.gaebriu.cyclo.ui.scroll

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.gaebriu.cyclo.ui.theme.CycloMotion
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Scroll vertical com a física do iOS (UIScrollView), para todas as áreas com scroll da app:
 * - desaceleração do iOS ao largar ([IosDecay], taxa "normal" de 0,998 por milissegundo);
 * - nas pontas, o conteúdo estica com resistência (rubber band) enquanto o dedo arrasta, e volta
 *   com uma mola ao largar; um lançamento que bate na ponta passa-a um pouco e volta (bounce).
 *
 * Como no iOS, só estica quando há mesmo conteúdo para percorrer: uma página que cabe no ecrã
 * fica quieta. Os seletores de horas e minutos do Introduction 2 não usam isto (têm a sua física).
 */
@Composable
fun Modifier.cycloVerticalScroll(state: ScrollState): Modifier {
    val overscroll = remember(state) { IosOverscrollEffect(state) }
    val fling = remember { IosFlingBehavior() }
    return verticalScroll(state = state, overscrollEffect = overscroll, flingBehavior = fling)
}

/** Constante do rubber band do UIScrollView: quanto mais pequena, mais resistente. */
private const val RubberBandCoefficient = 0.55f

/** Taxa de desaceleração "normal" do UIScrollView: a velocidade fica a 0,998 a cada milissegundo. */
private const val IosDecelerationRate = 0.998f

/** Abaixo disto (px/s) o lançamento considera-se parado. */
private const val FlingStopVelocity = 20f

/**
 * Desaceleração exponencial do iOS: v(t) = v0 · 0,998^(t em ms). Percorre mais e trava mais
 * suavemente do que a curva do Android.
 */
private class IosDecay : FloatDecayAnimationSpec {
    /** Constante da exponencial, por segundo (negativa). */
    private val k = ln(IosDecelerationRate) * 1000f

    override val absVelocityThreshold: Float get() = FlingStopVelocity

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float {
        val t = playTimeNanos / 1_000_000_000f
        return initialValue + initialVelocity / k * (exp(k * t) - 1f)
    }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float {
        val t = playTimeNanos / 1_000_000_000f
        return initialVelocity * exp(k * t)
    }

    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long {
        if (abs(initialVelocity) <= FlingStopVelocity) return 0L
        val seconds = ln(FlingStopVelocity / abs(initialVelocity)) / k
        return (seconds * 1_000_000_000L).toLong()
    }

    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        initialValue - initialVelocity / k
}

/** Lançamento com a desaceleração do iOS; o que sobra ao bater na ponta vai para o bounce. */
private class IosFlingBehavior : FlingBehavior {
    private val decay = IosDecay().generateDecayAnimationSpec<Float>()

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= FlingStopVelocity) return initialVelocity
        var velocityLeft = initialVelocity
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            velocityLeft = velocity
            // Chegou à ponta: pára aqui e a velocidade que sobra passa para o bounce.
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return velocityLeft
    }
}

/**
 * Rubber band e bounce do iOS. O conteúdo desloca-se [offset] px (positivo = para baixo).
 * Enquanto o dedo puxa para lá da ponta, a distância "real" passa pela curva do UIScrollView
 * (1 - 1 / (x · c / d + 1)) · d, com d a altura da área visível: cada vez estica menos.
 */
private class IosOverscrollEffect(private val state: ScrollState) : OverscrollEffect {
    private var offset by mutableFloatStateOf(0f)

    /** Altura da área visível: é o "d" da curva do rubber band. */
    private val viewport: Float get() = state.viewportSize.toFloat()

    private val canScroll: Boolean get() = state.canScrollForward || state.canScrollBackward

    /** Distância do dedo (sem resistência) → deslocação mostrada. */
    private fun rubber(raw: Float): Float {
        val d = viewport
        if (d <= 0f) return 0f
        return sign(raw) * (1f - 1f / (abs(raw) * RubberBandCoefficient / d + 1f)) * d
    }

    /** O inverso de [rubber]: a distância do dedo que dá esta deslocação. */
    private fun unrubber(shown: Float): Float {
        val d = viewport
        if (d <= 0f) return 0f
        val ratio = (abs(shown) / d).coerceAtMost(0.999f)
        return sign(shown) * (d / RubberBandCoefficient) * (1f / (1f - ratio) - 1f)
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val fromUser = source == NestedScrollSource.UserInput

        // Já esticado e o dedo volta para trás: primeiro desfaz o esticão, só depois faz scroll.
        var consumedByRelax = 0f
        if (fromUser && abs(offset) > 0.5f && sign(delta.y) != sign(offset)) {
            val raw = unrubber(offset)
            val newRaw = raw + delta.y
            if (sign(newRaw) != sign(raw)) {
                consumedByRelax = -raw
                offset = 0f
            } else {
                consumedByRelax = delta.y
                offset = rubber(newRaw)
            }
        }

        val left = delta.y - consumedByRelax
        val consumedByScroll = performScroll(Offset(0f, left)).y
        val overflow = left - consumedByScroll

        // O dedo continua a puxar para lá da ponta: estica com resistência.
        if (fromUser && abs(overflow) > 0.5f && canScroll) {
            offset = rubber(unrubber(offset) + overflow)
        }
        return Offset(0f, consumedByRelax + consumedByScroll)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (abs(offset) > 0.5f) {
            // Largou esticado: volta à ponta com uma mola, levando a velocidade do dedo.
            animate(offset, 0f, velocity.y, CycloMotion.ScrollBounceSpring) { value, _ -> offset = value }
            offset = 0f
            return
        }
        val consumed = performFling(velocity)
        val remaining = velocity.y - consumed.y
        // O lançamento bateu na ponta: passa-a um pouco e volta (bounce).
        if (canScroll && abs(remaining) > FlingStopVelocity) {
            animate(0f, 0f, remaining, CycloMotion.ScrollBounceSpring) { value, _ -> offset = value }
            offset = 0f
        }
    }

    override val isInProgress: Boolean get() = offset != 0f

    // Desloca o conteúdo só na colocação (com camada): não recompõe nem volta a medir.
    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeRelativeWithLayer(0, offset.roundToInt())
            }
        }
    }
}
