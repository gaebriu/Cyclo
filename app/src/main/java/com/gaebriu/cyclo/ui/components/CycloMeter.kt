package com.gaebriu.cyclo.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.main.excludeFromPageSwipe
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.rubberBand
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * O aspeto do medidor. A bola é a mesma em todos (ball-b, 16 dp desde a beta-3.0).
 * - [Emotion] (emotion-meter, Cyclo-2 40:131): o fundo numa camada a 70 %, com o gradiente a 63 %,
 *   uma sombra interior #FCFCFE a 15 % (0, -4, desfoque 9) e um brilho #FCFCFE a 15 % por cima
 *   (0, -6, desfoque 9, só fora da forma); os pontos a #FCFCFE a 15 % em Luminosity.
 * - [Sleep] (sleep-quality-meter, Componentes 5 2013:81, mudado na beta-3.0): como o [Emotion], mas
 *   sem a camada a 70 % (o gradiente a 63 %, a sombra interior, o brilho e os pontos #FCFCFE).
 */
enum class MeterStyle { Emotion, Sleep }

/**
 * O medidor de pontos do Cyclo: o emotion-meter do Feed ("How are you feeling tonight?") e o
 * sleep-quality-meter do action-menu-log-sleep (Componentes 5, 2013:81): o gradiente (violeta, azul,
 * céu, menta), cinco pontos de 9 dp e a bola de 16 dp. O aspeto de cada um vem de [style].
 *
 * Física (Beta 2.2):
 * - A bola segue o dedo (com resistência para lá das pontas e um encaixe suave nos pontos) e, ao
 *   largar, escolhe o ponto pela posição e pelo lançamento e vai para lá com uma mola que parte
 *   com a velocidade do dedo ([CycloSprings.MoodSettle]: assenta com peso, sem ressalto de
 *   brinquedo). Tocar num sítio leva a bola para o ponto mais perto. Começa sempre onde está
 *   desenhada (a meio de uma mola, se for o caso) e não salta para o dedo.
 * - Peso: a bola cresce um pouco enquanto se segura e, a andar, estica no sentido do movimento e
 *   achata (gel, como os botões; até [MaxStretch]), proporcional à velocidade a que se mexe mesmo.
 * - Háptico: um tique quando a bola DESENHADA passa ou chega a um ponto (também durante a mola e
 *   no fim dela, quando assenta), e não quando o ponto mais perto muda a meio do caminho; com
 *   histerese, para não repetir no mesmo ponto com o ressalto final ou um dedo a tremer.
 * - Desenho: o fundo é uma camada própria, gravada uma vez (não se redesenha ao arrastar); a bola
 *   é uma imagem feita uma vez (os desfoques são caros) e só se move, escala e estica.
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
    style: MeterStyle = MeterStyle.Sleep,
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
    // State (sem "by"): o tamanho e o esticão da bola só se leem ao desenhar, por isso redesenham a
    // bola sem recompor o medidor.
    val ballScale = animateFloatAsState(
        targetValue = if ((held || dragging) && effects.animationsEnabled) CycloMotion.MoodBallHeldScale else 1f,
        animationSpec = CycloMotion.MoodBallScaleSpring,
        label = "meterBall",
    )
    val stretch = remember { mutableFloatStateOf(0f) }
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

    /** O ponto mais perto da bola (o texto do medidor do sono e o TalkBack seguem-no). Sem háptico. */
    fun setStep(newStep: Int) {
        if (newStep != step) {
            step = newStep
            latestOnStepChange(newStep)
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

    /** Onde a bola está desenhada agora. */
    fun drawnPosition(): Float = if (dragging) dragPosition() else ball.value

    val ballInset = with(density) { 11.5.dp.toPx() }
    val trackWidth = remember { mutableFloatStateOf(0f) }

    // O movimento da bola, fotograma a fotograma, enquanto ela anda: a velocidade a que se mexe
    // mesmo (filtrada), que dá o esticão, e os tiques quando passa ou chega a um ponto. Parado,
    // espera sem trabalho nenhum.
    LaunchedEffect(effects.animationsEnabled) {
        var lastPos = drawnPosition()
        var lastNanos = -1L
        var velocity = 0f
        var lastTickAt = 0L
        var tickedDot = -1
        while (true) {
            if (!dragging && !ball.isRunning && abs(stretch.floatValue) < 0.001f) {
                stretch.floatValue = 0f
                velocity = 0f
                snapshotFlow { dragging || ball.isRunning }.first { it }
                lastNanos = -1L
                lastPos = drawnPosition()
            }
            withFrameNanos { now ->
                val pos = drawnPosition()
                if (lastNanos >= 0L) {
                    val dt = (now - lastNanos) / 1_000_000_000f
                    if (dt > 0f) {
                        val instant = (pos - lastPos) / dt
                        velocity += (instant - velocity) * (1f - exp(-dt / VelocitySmoothingS))
                    }
                    // Histerese: o ponto do último tique só volta a contar depois de a bola se afastar dele.
                    if (tickedDot >= 0 && abs(pos * last - tickedDot) > TickRearm) tickedDot = -1
                    val dot = crossedDot(lastPos * last, pos * last, last)
                    if (dot >= 0 && dot != tickedDot) {
                        tickedDot = dot
                        val t = SystemClock.uptimeMillis()
                        if (t - lastTickAt >= MinTickIntervalMs) {
                            lastTickAt = t
                            CycloHaptics.pickerTick(view, manyChoices = false)
                        }
                    }
                }
                lastPos = pos
                lastNanos = now
                // A velocidade em dp/s dá o esticão (0 a 1); sem animações, nada estica.
                val trackDp = trackWidth.floatValue / density.density
                val speedDp = abs(velocity) * trackDp
                stretch.floatValue = if (effects.animationsEnabled) {
                    (speedDp / FullStretchSpeedDp).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }
    }

    val ballSprite = remember(density) { ballSprite(density) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { trackWidth.floatValue = it.width - 2f * ballInset }
            // O medidor arrasta na horizontal: o swipe entre páginas não começa aqui.
            .excludeFromPageSwipe()
            .semantics {
                contentDescription = description
                stateDescription = levels[step]
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..last.toFloat(), steps - 2)
                setProgress { target ->
                    setStep(target.roundToInt().coerceIn(0, last))
                    settle(ball.value)
                    true
                }
            },
    ) {
        // O fundo e os pontos: não mudam, por isso gravam-se uma vez (arrastar só redesenha a bola).
        MeterTrack(style = style, steps = steps, ballInset = ballInset, luminosity = effects.luminosity)
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
                        setStep((((offset.x - ballInset) / trackPx).coerceIn(0f, 1f) * last).roundToInt())
                        settle(drawnPosition())
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
                        val shown = drawnPosition()
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
                        setStep((dragPosition().coerceIn(0f, 1f) * last).roundToInt())
                        horizontalDrag(start.id) { change ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragFinger = rawFractionAt(change.position.x)
                            setStep((dragPosition().coerceIn(0f, 1f) * last).roundToInt())
                        }
                        // Larga-se onde a bola está desenhada; a mola parte daí, com a velocidade do dedo.
                        grabJob?.cancel()
                        val released = dragPosition()
                        val velocity = tracker.calculateVelocity().x / trackPx
                        val projected = (released + velocity * CycloMotion.MoodFlingProjectionS).coerceIn(0f, 1f)
                        setStep((projected * last).roundToInt())
                        settle(released, velocity)
                    }
                },
        ) {
            val y = size.height / 2f
            // Enquanto arrasta: o dedo mais o que falta fechar da distância inicial (lido aqui, a cada fotograma).
            val center = Offset(lerp(ballInset, size.width - ballInset, drawnPosition()), y)
            val s = stretch.floatValue
            val scale = ballScale.value
            drawBallSprite(
                sprite = ballSprite,
                center = center,
                scaleX = scale * (1f + MaxStretch * s),
                scaleY = scale * (1f - MaxStretch * StretchFlatten * s),
            )
        }
    }
}

/**
 * O fundo do medidor (gradiente, sombras do [MeterStyle.Emotion]) e os pontos. Lê só o tamanho:
 * desenha-se uma vez por tamanho, e a bola por cima não o volta a desenhar.
 */
@Composable
private fun MeterTrack(style: MeterStyle, steps: Int, ballInset: Float, luminosity: BlendMode) {
    val last = steps - 1
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            // A camada a 70 % corta o que sai dela: fica maior do que o medidor (o brilho de cima
            // sai até ~20 dp), e o fundo desenha-se no meio dela.
            .layout { measurable, constraints ->
                val bleed = TrackBleed.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minWidth + 2 * bleed,
                        maxWidth = constraints.maxWidth + 2 * bleed,
                        minHeight = constraints.minHeight + 2 * bleed,
                        maxHeight = constraints.maxHeight + 2 * bleed,
                    ),
                )
                layout(placeable.width - 2 * bleed, placeable.height - 2 * bleed) { placeable.place(-bleed, -bleed) }
            }
            .then(
                if (style != MeterStyle.Sleep) {
                    // emotion-meter-background: a opacidade de 70 % do Figma é do nó inteiro (o
                    // gradiente, as sombras e o brilho juntos).
                    Modifier.graphicsLayer {
                        alpha = EmotionTrackAlpha
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                } else {
                    Modifier
                },
            )
            .drawWithCache {
                val bleed = TrackBleed.toPx()
                val track = Rect(bleed, bleed, size.width - bleed, size.height - bleed)
                val radius = track.height / 2f
                val pill = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(track, CornerRadius(radius))) }
                val gradient = Brush.horizontalGradient(
                    0f to CycloColors.MoodViolet.copy(alpha = MoodGradientAlpha),
                    0.25f to CycloColors.MoodBlue.copy(alpha = MoodGradientAlpha),
                    0.75f to CycloColors.MoodSky.copy(alpha = MoodGradientAlpha),
                    1f to CycloColors.MoodMint.copy(alpha = MoodGradientAlpha),
                    startX = track.left,
                    endX = track.right,
                )
                val glow = CycloColors.TextPrimary.copy(alpha = 0.15f)
                val lifted = Path().apply { addPath(pill, Offset(0f, -6.dp.toPx())) }
                onDrawBehind {
                    // O brilho de cima (0, -6, desfoque 9), só fora da forma ("show behind" desligado).
                    drawBlurredPath(lifted, glow, 9.dp.toPx())
                    drawPath(pill, Color.Black, blendMode = BlendMode.Clear)
                    drawPath(pill, gradient)
                    drawInnerShadow(pill, glow, Offset(0f, -4.dp.toPx()), cssBlurPx = 9.dp.toPx())
                }
            },
    )
    // Os pontos (9 dp, a 15 % em Luminosity), nas posições da bola. Fora da camada do fundo.
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val dot = CycloColors.TextPrimary
                onDrawBehind {
                    val y = size.height / 2f
                    for (i in 0 until steps) {
                        val x = lerp(ballInset, size.width - ballInset, i / last.toFloat())
                        drawCircle(
                            color = dot.copy(alpha = 0.15f),
                            radius = 4.5.dp.toPx(),
                            center = Offset(x, y),
                            blendMode = luminosity,
                        )
                    }
                }
            },
    )
}

/** A bola (ball-b) desenhada uma vez, já à maior escala a que pode aparecer, com margem para os brilhos. */
private class BallSprite(val image: ImageBitmap, val scale: Float)

private fun ballSprite(density: Density): BallSprite {
    val scale = SpriteScale
    val side = with(density) { (BallSpriteSide * scale).dp.toPx() }.roundToInt()
    val image = ImageBitmap(side, side)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        density,
        LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image),
        androidx.compose.ui.geometry.Size(side.toFloat(), side.toFloat()),
    ) {
        drawCycloBall(center, scale, BallDiameterDp)
    }
    return BallSprite(image, scale)
}

/** A bola em [center], a [scaleX] x [scaleY] do tamanho do Figma (o esticão do gel). */
private fun DrawScope.drawBallSprite(sprite: BallSprite, center: Offset, scaleX: Float, scaleY: Float) {
    val w = sprite.image.width * scaleX / sprite.scale
    val h = sprite.image.height * scaleY / sprite.scale
    drawImage(
        image = sprite.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(sprite.image.width, sprite.image.height),
        dstOffset = IntOffset((center.x - w / 2f).roundToInt(), (center.y - h / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.High,
    )
}

/**
 * O ponto (0 a [last]) por onde a bola passou ou a que chegou entre [from] e [to] (posições em
 * passos); -1 se nenhum. Com vários no mesmo fotograma, o último (o mais perto de [to]).
 */
private fun crossedDot(from: Float, to: Float, last: Int): Int {
    if (from == to) return -1
    return if (to > from) {
        // (from, to]
        val k = floor(to).toInt()
        if (k > from && k in 0..last) k else -1
    } else {
        // [to, from)
        val k = ceil(to).toInt()
        if (k < from && k in 0..last) k else -1
    }
}

/** Quanto tempo uma escolha feita no medidor manda sobre valores guardados diferentes (ms). */
private const val PendingMeterMs = 2_000L

/** Margem da camada do fundo para o brilho de cima (6 de deslocamento + o desfoque 9). */
private val TrackBleed = 24.dp

/** Opacidades do fundo: a camada a 70 % (emotion-meter) e o gradiente a 63 %. */
private const val EmotionTrackAlpha = 0.7f
private const val MoodGradientAlpha = 0.63f

/** A bola (ball-b) dos medidores: 16 dp desde a beta-3.0 (era 15). */
private const val BallDiameterDp = 16f

/** O esticão máximo da bola a andar (+12 % na largura) e quanto ela achata em proporção (metade). */
private const val MaxStretch = 0.12f
private const val StretchFlatten = 0.5f

/** A esta velocidade (dp/s) a bola já estica tudo. */
private const val FullStretchSpeedDp = 1_400f

/** O filtro da velocidade da bola (s): tira o tremor dos eventos do dedo e devolve a forma com suavidade. */
private const val VelocitySmoothingS = 0.045f

/** Depois de um tique, o mesmo ponto só volta a contar a esta distância dele (em passos). */
private const val TickRearm = 0.3f

/** Intervalo mínimo entre dois tiques (uma passagem rápida por vários pontos não vira um zumbido). */
private const val MinTickIntervalMs = 35L

/** A imagem da bola: 16 dp mais os brilhos (6 de deslocamento e o desfoque 9), à maior escala possível. */
private const val BallSpriteSide = BallDiameterDp + 2f * (6f + 9f * 1.5f)
private val SpriteScale = CycloMotion.MoodBallHeldScale * (1f + MaxStretch)

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
    val u = (abs(d) / DetentReach).coerceIn(0f, 1f)
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
