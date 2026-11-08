package com.gaebriu.cyclo.ui.scroll

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 *
 * **Toques durante o movimento (Beta 2.2).** O `scrollable` do Compose consome o toque (o "down")
 * sempre que o scroll está "em curso" (`isScrollInProgress`) ou o overscroll está "em curso"
 * (`OverscrollEffect.isInProgress`): esse toque só agarra o conteúdo e nunca chega aos botões. Com a
 * desaceleração do iOS, a cauda lenta de um lançamento dura 1–2 s (o conteúdo quase parado) e a mola
 * do bounce, 0,5 s; antes, o overscroll podia até ficar preso a 0,3 px (a mola do bounce era
 * cancelada a meio) e aí NENHUM toque passava. Agora:
 * - o lançamento corre dentro do scroll só enquanto é rápido; abaixo de [TapThroughVelocity] passa,
 *   no mesmo frame e com a mesma curva, para uma cauda fora do scroll ([ScrollGlide.tail], com
 *   `dispatchRawDelta`), que não conta como "em curso": um toque nela pára o conteúdo E chega ao
 *   elemento debaixo do dedo. Rápido de mais para se ler, um toque só o agarra (como no iOS);
 * - o bounce corre numa tarefa própria: só agarra o toque enquanto se mexe depressa; um arrasto
 *   apanha-o onde estiver (continua dali, sem dois a escrever no deslocamento) e um toque deixa-o
 *   acabar sozinho;
 * - um toque pára a cauda logo na passagem Initial (antes dos filhos), sem consumir nada.
 */
@Composable
fun Modifier.cycloVerticalScroll(state: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    val handoff = with(LocalDensity.current) { TapThroughVelocity.toPx() }
    val glide = remember(state, handoff) { ScrollGlide(state, scope, handoff) }
    return this
        // Um toque pára a cauda do lançamento (vê-se antes do scroll e dos filhos e não consome).
        .pointerInput(glide) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                glide.stopTail()
            }
        }
        .verticalScroll(state = state, overscrollEffect = glide.overscroll, flingBehavior = glide.fling)
}

/**
 * Sobe a página até ao topo com a mola das páginas ([CycloSprings.Page]) e volta quando ela já está
 * a chegar (a [near] px do topo: a cauda da mola, os últimos píxeis, não atrasa quem espera), ou
 * quando o dedo a interrompe. Sem animações, salta. É o movimento da release-1.1 (o aviso de
 * atualização a aparecer depois de ligar as betas), partilhado: a pesquisa das rotinas guardadas
 * usa-o para trazer a caixa à vista antes de ela abrir.
 */
suspend fun ScrollState.climbToTop(scope: CoroutineScope, animationsEnabled: Boolean, near: Int) {
    if (value <= 0) return
    if (!animationsEnabled) {
        scrollTo(0)
        return
    }
    // A subida corre em [scope] (continua depois de quem espera seguir em frente).
    val arrived = CompletableDeferred<Unit>()
    scope.launch {
        try {
            animateScrollTo(0, CycloSprings.Page.float)
        } finally {
            arrived.complete(Unit)
        }
    }
    coroutineScope {
        val watcher = launch {
            snapshotFlow { value <= near }.first { it }
            arrived.complete(Unit)
        }
        arrived.await()
        watcher.cancel()
    }
}

/** A distância ao topo a partir da qual [climbToTop] já deixa quem espera continuar. */
val ClimbNearTop: Dp = 4.dp

/**
 * Abaixo desta velocidade, o conteúdo lê-se enquanto se mexe e um toque chega ao que está debaixo
 * do dedo (e pára o movimento); acima, o toque só o agarra. ~5 dp por fotograma a 60 Hz.
 */
private val TapThroughVelocity: Dp = 300.dp

/** Constante do rubber band do UIScrollView: quanto mais pequena, mais resistente. */
private const val RubberBandCoefficient = 0.55f

/** Taxa de desaceleração "normal" do UIScrollView: a velocidade fica a 0,998 a cada milissegundo. */
private const val IosDecelerationRate = 0.998f

/** Constante da exponencial da desaceleração, por segundo (negativa). */
private val DecayK = ln(IosDecelerationRate) * 1000f

/** Abaixo disto (px/s) o lançamento considera-se parado. */
private const val FlingStopVelocity = 20f

/**
 * Desaceleração exponencial do iOS: v(t) = v0 · 0,998^(t em ms). Percorre mais e trava mais
 * suavemente do que a curva do Android. Não tem memória: continuar com a velocidade de agora dá
 * exatamente a mesma curva (é o que deixa a cauda continuar o lançamento sem se notar).
 */
private class IosDecay : FloatDecayAnimationSpec {
    override val absVelocityThreshold: Float get() = FlingStopVelocity

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float {
        val t = playTimeNanos / 1_000_000_000f
        return initialValue + initialVelocity / DecayK * (exp(DecayK * t) - 1f)
    }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, initialVelocity: Float): Float {
        val t = playTimeNanos / 1_000_000_000f
        return initialVelocity * exp(DecayK * t)
    }

    override fun getDurationNanos(initialValue: Float, initialVelocity: Float): Long {
        if (abs(initialVelocity) <= FlingStopVelocity) return 0L
        val seconds = ln(FlingStopVelocity / abs(initialVelocity)) / DecayK
        return (seconds * 1_000_000_000L).toLong()
    }

    override fun getTargetValue(initialValue: Float, initialVelocity: Float): Float =
        initialValue - initialVelocity / DecayK
}

/**
 * O movimento de uma área com scroll depois de largar: o lançamento (dentro do scroll), a cauda
 * lenta (fora dele) e o rubber band/bounce. Velocidades e deltas no sentido do valor do scroll
 * (positivo = o conteúdo sobe), exceto no overscroll, que usa o do dedo (positivo = desce).
 */
private class ScrollGlide(
    private val state: ScrollState,
    private val scope: CoroutineScope,
    private val handoff: Float,
) {
    private var tailJob: Job? = null

    val overscroll = IosOverscrollEffect(state, scope, handoff)
    val fling: FlingBehavior = IosFlingBehavior()

    /** Pára a cauda onde está (um toque, ou outro scroll que começa). */
    fun stopTail() {
        tailJob?.cancel()
        tailJob = null
    }

    /**
     * Continua um lançamento fora do scroll, a partir do frame [fromNanos] com [velocity] px/s, com
     * a mesma curva (sem um frame parado na passagem). Pára num toque, quando outro scroll começa
     * (arrasto, [ScrollState.animateScrollTo], trazer um campo à vista) ou na ponta, onde o que
     * sobra da velocidade passa para o bounce.
     */
    fun tail(velocity: Float, fromNanos: Long) {
        stopTail()
        tailJob = scope.launch {
            var travelled = 0f
            var edgeVelocity = 0f
            var frames = 0
            while (true) {
                val keepGoing = withFrameNanos { now ->
                    val t = ((now - fromNanos).coerceAtLeast(0L)) / 1_000_000_000f
                    val v = velocity * exp(DecayK * t)
                    // Outro scroll começou (nos primeiros frames ainda pode ser o fim do lançamento).
                    if (++frames > 2 && state.isScrollInProgress) return@withFrameNanos false
                    val position = velocity / DecayK * (exp(DecayK * t) - 1f)
                    val delta = position - travelled
                    travelled = position
                    val consumed = state.dispatchRawDelta(delta)
                    if (abs(delta - consumed) > 0.5f) {
                        edgeVelocity = v
                        return@withFrameNanos false
                    }
                    abs(v) > FlingStopVelocity
                }
                if (!keepGoing) break
            }
            // Bateu na ponta: passa-a um pouco e volta (no sentido do dedo, ao contrário do valor).
            if (edgeVelocity != 0f) overscroll.bounce(-edgeVelocity)
        }
    }

    /** Lançamento com a desaceleração do iOS; rápido, dentro do scroll; lento, na cauda. */
    private inner class IosFlingBehavior : FlingBehavior {
        private val decay = IosDecay().generateDecayAnimationSpec<Float>()

        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            stopTail()
            if (abs(initialVelocity) <= FlingStopVelocity) return initialVelocity
            // Já lento ao largar: todo o lançamento é cauda (os toques passam logo).
            if (abs(initialVelocity) <= handoff) {
                tail(initialVelocity, withFrameNanos { it })
                return 0f
            }
            var velocityLeft = initialVelocity
            var lastValue = 0f
            var handoffAt = -1L
            AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = velocity
                when {
                    // Chegou à ponta: pára aqui e a velocidade que sobra passa para o bounce.
                    abs(delta - consumed) > 0.5f -> cancelAnimation()
                    // Já se lê: o resto corre na cauda, a partir deste frame.
                    abs(velocity) <= handoff -> {
                        handoffAt = lastFrameTimeNanos
                        cancelAnimation()
                    }
                }
            }
            if (handoffAt >= 0L) {
                tail(velocityLeft, handoffAt)
                return 0f
            }
            return velocityLeft
        }
    }
}

/**
 * Rubber band e bounce do iOS. O conteúdo desloca-se [offset] px (positivo = para baixo).
 * Enquanto o dedo puxa para lá da ponta, a distância "real" passa pela curva do UIScrollView
 * (1 - 1 / (x · c / d + 1)) · d, com d a altura da área visível: cada vez estica menos.
 *
 * O bounce (a volta à ponta) corre numa tarefa própria ([bounce]): um arrasto que começa a meio
 * pára-o e continua do sítio onde ele estava; um toque deixa-o acabar.
 */
private class IosOverscrollEffect(
    private val state: ScrollState,
    private val scope: CoroutineScope,
    private val handoff: Float,
) : OverscrollEffect {
    private var offset by mutableFloatStateOf(0f)

    /** A velocidade da mola do bounce agora (px/s; 0 parada). Lê-se só ao tocar. */
    private var bounceVelocity = 0f
    private var bounceJob: Job? = null

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

    /** Volta à ponta com a mola do bounce, a partir de onde está e com [velocity] (sentido do dedo). */
    fun bounce(velocity: Float) {
        bounceJob?.cancel()
        bounceJob = scope.launch {
            try {
                animate(offset, 0f, velocity, CycloMotion.ScrollBounceSpring) { value, v ->
                    offset = value
                    bounceVelocity = v
                }
                offset = 0f
            } finally {
                bounceVelocity = 0f
            }
        }
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val fromUser = source == NestedScrollSource.UserInput

        // O dedo apanha o bounce a meio: pára-o e continua daqui (só o dedo escreve no deslocamento).
        if (fromUser && bounceJob?.isActive == true) {
            bounceJob?.cancel()
            bounceJob = null
        }

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
            bounce(velocity.y)
            return
        }
        val consumed = performFling(velocity)
        val remaining = velocity.y - consumed.y
        // O lançamento bateu na ponta: passa-a um pouco e volta (bounce).
        if (canScroll && abs(remaining) > FlingStopVelocity) bounce(remaining)
    }

    /**
     * "Em curso" para o scroll (que então agarra o toque em vez de o deixar chegar aos filhos): só
     * enquanto o bounce se mexe depressa e longe da ponta. A cauda lenta da mola deixa tocar.
     */
    override val isInProgress: Boolean
        get() = bounceJob?.isActive == true && abs(offset) > 1f && abs(bounceVelocity) > handoff

    // Desloca o conteúdo só na colocação (com camada): não recompõe nem volta a medir.
    override val node: DelegatableNode = object : androidx.compose.ui.Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeRelativeWithLayer(0, offset.roundToInt())
            }
        }
    }
}
