package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.main.excludeFromPageSwipe
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.rubberBand
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * O medidor de pontos do Cyclo: o emotion-meter do Feed ("How are you feeling tonight?") e o
 * sleep-quality-meter do action-menu-log-sleep (Componentes 5, 2013:81). Os dois são o mesmo
 * componente do Figma: gradiente a 39 % (violeta, azul, céu, menta), cinco pontos de 9 dp (#A3A2A7 a
 * 15 % em Luminosity) e a bola de 15 dp.
 *
 * - A bola segue o dedo (com resistência para lá das pontas e um encaixe suave nos pontos) e, ao
 *   largar, escolhe o ponto pela posição e pelo lançamento, e assenta lá com uma mola que parte com
 *   a velocidade do dedo. Cresce um pouco enquanto se segura. Um tique em cada ponto que passa.
 * - Tocar num sítio leva a bola para o ponto mais perto.
 * - Começa onde está (a meio de uma mola, se for o caso) e não salta para o dedo.
 *
 * @param value o valor guardado, de 0 (ponto da esquerda) a 1 (o da direita).
 * @param levels o nome de cada ponto (TalkBack); o número de pontos é o tamanho da lista.
 * @param onValueChange a escolha, quando a bola assenta (para a recomposição de quem guarda não cair
 *        a meio da mola).
 * @param onStepChange o ponto mais perto da bola, enquanto ela anda (ex.: o texto por cima do
 *        medidor do sono muda com ela).
 */
@Composable
fun CycloMeter(
    value: Float,
    levels: List<String>,
    description: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onStepChange: (Int) -> Unit = {},
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val steps = levels.size
    val last = steps - 1
    val latestOnStepChange by rememberUpdatedState(onStepChange)
    val stored = (value * last).roundToInt().coerceIn(0, last)
    var step by remember { mutableIntStateOf(stored) }
    // A posição da bola (0 a 1; passa um pouco das pontas com a resistência e a mola).
    val ball = remember { Animatable(stored / last.toFloat()) }
    // Enquanto o dedo arrasta, a bola está no dedo ([dragFinger]) mais a distância a que estava
    // dele quando o arrasto começou ([grabOffset], que se fecha sozinha em poucos fotogramas): sem
    // corrotinas a cada movimento e sem saltar para o dedo.
    var dragging by remember { mutableStateOf(false) }
    var dragFinger by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var grabJob by remember { mutableStateOf<Job?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var held by remember { mutableStateOf(false) }
    // State (sem "by"): o tamanho da bola só se lê ao desenhar, por isso a mola dela redesenha o
    // medidor sem o recompor.
    val ballScale = animateFloatAsState(
        targetValue = if ((held || dragging) && effects.animationsEnabled) CycloMotion.MoodBallHeldScale else 1f,
        animationSpec = CycloMotion.MoodBallScaleSpring,
        label = "meterBall",
    )
    // A última escolha feita aqui (e quando): um valor guardado mais antigo que chegue depois (duas
    // escolhas seguidas) não leva a bola para trás.
    var localStep by remember { mutableIntStateOf(-1) }
    var localAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(stored) {
        if (localStep >= 0) {
            if (stored == localStep) {
                localStep = -1
                return@LaunchedEffect
            }
            if (System.currentTimeMillis() - localAt < PendingMeterMs) return@LaunchedEffect
            localStep = -1
        }
        // Mudou fora daqui (outro dia, restauro): a bola vai para lá.
        if (!dragging && stored != step) {
            step = stored
            latestOnStepChange(stored)
            if (effects.animationsEnabled) ball.animateTo(stored / last.toFloat(), CycloMotion.MoodSettleSpring) else ball.snapTo(stored / last.toFloat())
        }
    }

    fun setStep(newStep: Int, haptic: Boolean) {
        if (newStep != step) {
            step = newStep
            latestOnStepChange(newStep)
            if (haptic) CycloHaptics.pickerTick(view, manyChoices = false)
        }
    }

    /**
     * Leva a bola para o ponto escolhido, a partir de [from] e com [velocity] (fração/s). A bola
     * passa do dedo para a mola no mesmo fotograma ([dragging] só desliga depois de a mola estar na
     * posição do dedo) e a escolha só se grava quando a bola assenta.
     */
    fun settle(from: Float, velocity: Float = 0f) {
        val chosen = step
        val target = chosen / last.toFloat()
        localStep = chosen
        localAt = System.currentTimeMillis()
        settleJob = scope.launch {
            try {
                ball.snapTo(from)
                dragging = false
                if (effects.animationsEnabled) {
                    ball.animateTo(target, CycloMotion.MoodSettleSpring, initialVelocity = velocity)
                } else {
                    ball.snapTo(target)
                }
            } finally {
                // Também se um arrasto novo interromper a mola (grava-se depois a escolha dele).
                onValueChange(target)
            }
        }
    }

    /**
     * A posição da bola enquanto o dedo arrasta: o dedo com o encaixe suave dos pontos
     * ([meterDetent]) mais o que falta fechar da distância inicial, com resistência para lá das pontas.
     */
    fun dragPosition(): Float = rubberBandFraction(meterDetent(dragFinger, last) + grabOffset)

    val ballInset = with(density) { 11.5.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            // O medidor arrasta na horizontal: o swipe entre páginas não começa aqui.
            .excludeFromPageSwipe()
            .semantics {
                contentDescription = description
                stateDescription = levels[step]
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..last.toFloat(), steps - 2)
                setProgress { target ->
                    setStep(target.roundToInt().coerceIn(0, last), haptic = false)
                    settle(ball.value)
                    true
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                    ) { offset ->
                        val trackPx = (size.width - 2f * ballInset).coerceAtLeast(1f)
                        setStep((((offset.x - ballInset) / trackPx).coerceIn(0f, 1f) * last).roundToInt(), haptic = true)
                        settle(if (dragging) dragPosition() else ball.value)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val trackPx = (size.width - 2f * ballInset).coerceAtLeast(1f)
                        fun rawFractionAt(x: Float) = (x - ballInset) / trackPx
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        } ?: return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(start.uptimeMillis, start.position)
                        // A mola anterior pára já (antes, ao acabar, devolvia a bola a meio do arrasto).
                        settleJob?.cancel()
                        val shown = if (dragging) dragPosition() else ball.value
                        val finger = rawFractionAt(start.position.x)
                        dragFinger = finger
                        // Medida a partir do dedo já com o encaixe: a bola não salta no primeiro frame.
                        grabOffset = shown - meterDetent(finger, last)
                        dragging = true
                        grabJob?.cancel()
                        grabJob = if (effects.animationsEnabled) {
                            scope.launch { closeGrab(grabOffset) { grabOffset = it } }
                        } else {
                            grabOffset = 0f
                            null
                        }
                        setStep((dragPosition().coerceIn(0f, 1f) * last).roundToInt(), haptic = true)
                        horizontalDrag(start.id) { change ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragFinger = rawFractionAt(change.position.x)
                            setStep((dragPosition().coerceIn(0f, 1f) * last).roundToInt(), haptic = true)
                        }
                        // Larga-se onde a bola está desenhada; a mola parte daí, com a velocidade do dedo.
                        grabJob?.cancel()
                        val released = dragPosition()
                        val velocity = tracker.calculateVelocity().x / trackPx
                        val projected = (released + velocity * CycloMotion.MoodFlingProjectionS).coerceIn(0f, 1f)
                        setStep((projected * last).roundToInt(), haptic = true)
                        settle(released, velocity)
                    }
                },
        ) {
            val blend = effects.luminosity
            // emotion-meter-background: gradiente a 39 %, de violeta a menta.
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to CycloColors.MoodViolet.copy(alpha = 0.39f),
                    0.25f to CycloColors.MoodBlue.copy(alpha = 0.39f),
                    0.75f to CycloColors.MoodSky.copy(alpha = 0.39f),
                    1f to CycloColors.MoodMint.copy(alpha = 0.39f),
                ),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            // Os pontos (9 dp, #A3A2A7 a 15 % em Luminosity), nas posições da bola.
            val y = size.height / 2f
            for (i in 0 until steps) {
                val x = lerp(ballInset, size.width - ballInset, i / last.toFloat())
                drawCircle(
                    color = CycloColors.TextSecondary.copy(alpha = 0.15f),
                    radius = 4.5.dp.toPx(),
                    center = Offset(x, y),
                    blendMode = blend,
                )
            }
            // Enquanto arrasta: o dedo mais o que falta fechar da distância inicial (lido aqui, a cada fotograma).
            val position = if (dragging) dragPosition() else ball.value
            drawCycloBall(Offset(lerp(ballInset, size.width - ballInset, position), y), ballScale.value)
        }
    }
}

/** Quanto tempo uma escolha feita no medidor manda sobre valores guardados diferentes (ms). */
private const val PendingMeterMs = 2_000L

/**
 * Para lá das pontas, a bola anda cada vez menos do que o dedo (a resistência do iOS, [rubberBand]):
 * começa com [CycloMotion.MoodRubberBand] do movimento e aproxima-se de [CycloMotion.MoodRubberBandMax]
 * sem nunca bater num limite seco.
 */
private fun rubberBandFraction(f: Float): Float = when {
    f < 0f -> rubberBand(f, CycloMotion.MoodRubberBandMax, CycloMotion.MoodRubberBand)
    f > 1f -> 1f + rubberBand(f - 1f, CycloMotion.MoodRubberBandMax, CycloMotion.MoodRubberBand)
    else -> f
}

/**
 * Encaixe suave nos pontos enquanto se arrasta: perto de um ponto a bola anda um pouco menos do que
 * o dedo (até [DetentPull] da distância, a [DetentReach] de um passo), e longe deles acompanha-o por
 * inteiro. A curva é contínua e sempre a subir (a bola nunca anda para trás nem salta). [f] é a
 * posição do dedo (0 a 1, pode passar das pontas) e [last] o índice do último ponto.
 */
private fun meterDetent(f: Float, last: Int): Float {
    val s = f * last
    val nearest = s.roundToInt().coerceIn(0, last)
    val d = s - nearest
    val u = (kotlin.math.abs(d) / DetentReach).coerceIn(0f, 1f)
    val smooth = u * u * (3f - 2f * u)
    val eased = d * (1f - DetentPull * (1f - smooth))
    return (nearest + eased) / last
}

/** Quanto do movimento o encaixe segura junto a um ponto (0 = nada). */
private const val DetentPull = 0.35f

/** Até onde chega o encaixe, em frações de um passo entre pontos. */
private const val DetentReach = 0.28f

/**
 * Fecha a distância entre a bola e o dedo no início de um arrasto: uma mola crítica (sem passar
 * do dedo) com a resposta de [CycloSprings.Snappy], fotograma a fotograma, a partir de [from].
 */
private suspend fun closeGrab(from: Float, set: (Float) -> Unit) {
    val omega = (2.0 * Math.PI / CycloSprings.Snappy.response).toFloat()
    var startNanos = -1L
    while (true) {
        val done = withFrameNanos { now ->
            if (startNanos < 0) startNanos = now
            val t = (now - startNanos) / 1_000_000_000f
            val k = (1f + omega * t) * exp(-omega * t)
            set(from * k)
            k < 0.001f
        }
        if (done) {
            set(0f)
            return
        }
    }
}
