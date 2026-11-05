package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import androidx.compose.ui.graphics.BlendMode
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.drawBackdrop
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.rememberBackdropSampler
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Máximo de caracteres do nome de uma rotina. */
const val MaxRoutineNameLength = 39

// Medidas da caixa de 345 x 48 dp (Figma): o texto começa a 15 dp e o ícone de 24 dp fica a 15 dp da direita.
private val TextStart = 15.dp
private val ActionTarget = 48.dp
private val ActionTargetEnd = 3.dp

/**
 * Caixa do nome da rotina (tap-to-write-box), que se transforma na caixa a ouvir
 * (tap-to-write-while-listening-box) com uma mola:
 * - o microfone dá lugar ao ícone do teclado, no MESMO sítio;
 * - "Tap to write" (ou o texto escrito) dá lugar a "Listening...";
 * - aparecem a borda e o brilho, e os gradientes do texto e da borda correm da esquerda para a direita.
 *
 * Quando o ditado COMPLETA ([completionKey] muda), a troca para a caixa de escrever faz um zoom
 * com mola: a de escrever entra com zoom in e a de ouvir sai com zoom out, em sincronia.
 * O háptico dessa conclusão é dado por quem usa a caixa; os botões desta caixa não têm háptico.
 *
 * @param micVisible falso quando o ditado não está disponível (ex.: microfone recusado): o botão some.
 * @param completionKey sobe uma unidade de cada vez que o ditado completa com um nome.
 * @param onSwitchToKeyboard ícone do teclado (só ao ouvir): deixa de ouvir e passa a escrever.
 * @param placeholder o texto da caixa vazia ("Tap to write"; "What are you looking for?" na
 *                    pesquisa, search-box).
 * @param search o fundo de vidro da caixa de pesquisa (search-box), em vez do da caixa do nome.
 */
@Composable
fun CycloNameField(
    value: String,
    onValueChange: (String) -> Unit,
    isListening: Boolean,
    micVisible: Boolean,
    completionKey: Int,
    onMicClick: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.introduction_3_placeholder),
    search: Boolean = false,
) {
    val listening = animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = CycloMotion.ListeningSpring,
        label = "listening",
    )
    val shimmer = rememberListeningShimmer(listening)
    val zoom = rememberCompletionZoom(isListening, completionKey)

    Box(modifier = modifier.fillMaxWidth().height(CycloDimens.InputHeight)) {
        NameFieldFill(search)
        ListeningLayer(listening = listening, shimmer = shimmer, zoom = zoom)
        NameFieldInput(
            value = value,
            onValueChange = onValueChange,
            enabled = !isListening,
            micVisible = micVisible,
            listening = listening,
            zoom = zoom,
            focusRequester = focusRequester,
            onDone = onDone,
            placeholder = placeholder,
        )
        NameFieldAction(
            isListening = isListening,
            micVisible = micVisible,
            listening = listening,
            zoom = zoom,
            onMicClick = onMicClick,
            onSwitchToKeyboard = onSwitchToKeyboard,
        )
    }
}

/** Os dois zooms da conclusão do ditado (1 = em repouso). */
@Stable
private class CompletionZoom {
    /** Caixa de escrever: sai de um pouco menor e cresce até 1 (zoom in). */
    val typing = Animatable(1f)

    /** Caixa de ouvir: de 1 encolhe um pouco (zoom out) enquanto desaparece. */
    val overlay = Animatable(1f)
}

@Composable
private fun rememberCompletionZoom(isListening: Boolean, completionKey: Int): CompletionZoom {
    val effects = LocalVisualEffects.current
    val zoom = remember { CompletionZoom() }

    // Ao voltar a ouvir, a caixa de ouvir recomeça com o tamanho normal.
    LaunchedEffect(isListening) {
        if (isListening) zoom.overlay.snapTo(1f)
    }
    // Só quando o ditado COMPLETA (e só com animações ligadas). Os dois zooms arrancam juntos.
    LaunchedEffect(completionKey) {
        if (completionKey > 0 && effects.animationsEnabled) {
            coroutineScope {
                launch {
                    zoom.typing.snapTo(CycloMotion.CompletionZoom)
                    zoom.typing.animateTo(1f, CycloMotion.CompletionSpring)
                }
                launch {
                    zoom.overlay.snapTo(1f)
                    zoom.overlay.animateTo(CycloMotion.CompletionZoom, CycloMotion.CompletionSpring)
                }
            }
        }
    }
    return zoom
}

/**
 * Fase (0 a 1) dos gradientes animados. Só corre, e só gasta, enquanto a caixa está a ouvir;
 * sem animações no sistema fica parada.
 */
@Composable
private fun rememberListeningShimmer(listening: State<Float>): State<Float> {
    val effects = LocalVisualEffects.current
    val active by remember { derivedStateOf { listening.value > 0.001f } }
    return if (active && effects.animationsEnabled) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(CycloMotion.ListeningShimmerMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
}

/**
 * Fundo da caixa (#101011, raio 15).
 *
 * Na pesquisa ([search], search-box do Figma) é vidro: #101011 em Luminosity sobre o desfoque de
 * fundo 39, brilho exterior (0, 0, desfoque 9, #A3A2A7 a 15 %) e borda de 1 dp em gradiente da
 * esquerda para a direita (#A3A2A7 a 24 %, 9 % a meio, 0 %).
 */
@Composable
private fun BoxScope.NameFieldFill(search: Boolean) {
    if (!search) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawPath(roundedShape(CycloDimens.InputRadius.toPx()), CycloColors.GlassFill)
        }
        return
    }
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    Canvas(modifier = Modifier.matchParentSize().backdropSampler(sampler)) {
        val radius = CycloDimens.InputRadius.toPx()
        val shape = roundedShape(radius)
        val edge = CycloColors.TextSecondary
        val blend = effects.luminosity
        clipPath(shape, clipOp = ClipOp.Difference) {
            drawBlurredPath(shape, edge.copy(alpha = 0.15f), 9.dp.toPx())
        }
        if (effects.textures) drawBackdrop(sampler, backdrop, shape, FigmaBlur.Overlay)
        drawPath(shape, CycloColors.GlassFill, blendMode = blend)
        drawInsideBorder(
            radius,
            Brush.horizontalGradient(
                0f to edge.copy(alpha = 0.24f),
                0.5f to edge.copy(alpha = 0.09f),
                1f to edge.copy(alpha = 0f),
            ),
            blend,
        )
    }
}

private fun DrawScope.roundedShape(radius: Float): Path = smoothRoundRectPath(size.width, size.height, radius)

/** Tudo o que só existe ao ouvir: brilho exterior, borda em gradiente e o texto "Listening...". */
@Composable
private fun BoxScope.ListeningLayer(
    listening: State<Float>,
    shimmer: State<Float>,
    zoom: CompletionZoom,
) {
    val textMeasurer = rememberTextMeasurer()
    val label = stringResource(R.string.introduction_3_listening)
    val layout = remember(label) { textMeasurer.measure(label, CycloType.InputHint) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = zoom.overlay.value
                scaleY = zoom.overlay.value
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val amount = listening.value.coerceIn(0f, 1f)
            if (amount > 0f) {
                val radius = CycloDimens.InputRadius.toPx()
                // Sombra exterior do Figma (0, 0, desfoque 9, #A3A2A7 a 15 %): só fora da caixa.
                clipPath(roundedShape(radius), clipOp = ClipOp.Difference) {
                    drawBlurredPath(
                        roundedShape(radius),
                        CycloColors.TextSecondary.copy(alpha = 0.15f * amount),
                        9.dp.toPx(),
                    )
                }
                drawListeningBorder(radius, shimmer.value, amount)
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val amount = listening.value.coerceIn(0f, 1f)
                    if (amount > 0f) drawListeningLabel(layout, shimmer.value, amount)
                },
        )
    }
}

/** Borda de 1 dp em gradiente (#A3A2A7 a 24 %, 9 % no meio, 24 %), que corre da esquerda para a direita. */
private fun DrawScope.drawListeningBorder(radius: Float, phase: Float, amount: Float) {
    val color = CycloColors.TextSecondary
    val inset = 0.5.dp.toPx()
    val start = phase * size.width
    drawPath(
        Path().apply { addSmoothRoundRect(inset, inset, size.width - inset, size.height - inset, radius - inset) },
        brush = Brush.linearGradient(
            0f to color.copy(alpha = 0.24f),
            0.5f to color.copy(alpha = 0.09f),
            1f to color.copy(alpha = 0.24f),
            start = Offset(start, 0f),
            end = Offset(start + size.width, 0f),
            tileMode = TileMode.Repeated,
        ),
        style = Stroke(width = 1.dp.toPx()),
        alpha = amount,
    )
}

/** "Listening...": gradiente (#A3A2A7, 63 % no meio, #A3A2A7) a correr da esquerda para a direita. */
private fun DrawScope.drawListeningLabel(layout: TextLayoutResult, phase: Float, amount: Float) {
    val color = CycloColors.TextSecondary
    val left = TextStart.toPx() + (1f - amount) * 8.dp.toPx()
    val top = (size.height - layout.size.height) / 2f
    val period = layout.size.width.toFloat()
    val start = left + phase * period
    drawText(
        textLayoutResult = layout,
        brush = Brush.linearGradient(
            0f to color,
            0.476f to color.copy(alpha = 0.63f),
            1f to color,
            start = Offset(start, 0f),
            end = Offset(start + period, 0f),
            tileMode = TileMode.Repeated,
        ),
        topLeft = Offset(left, top),
        alpha = amount,
    )
}

/** Texto escrito (ou "Tap to write"): desvanece-se ao ouvir e faz zoom in ao completar o ditado. */
@Composable
private fun BoxScope.NameFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    micVisible: Boolean,
    listening: State<Float>,
    zoom: CompletionZoom,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
    placeholder: String,
) {

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = CycloColors.TextPrimary,
            backgroundColor = CycloColors.Accent.copy(alpha = 0.5f),
        ),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("\n", "").take(MaxRoutineNameLength)) },
            enabled = enabled,
            singleLine = true,
            textStyle = CycloType.Input,
            cursorBrush = SolidColor(CycloColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .align(Alignment.CenterStart)
                // Deixa o espaço do ícone (24 dp + 15 dp de cada lado) livre quando ele existe.
                .padding(start = TextStart, end = if (micVisible) 54.dp else TextStart)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .graphicsLayer {
                    val amount = listening.value.coerceIn(0f, 1f)
                    alpha = 1f - amount
                    translationX = -8.dp.toPx() * amount
                    scaleX = zoom.typing.value
                    scaleY = zoom.typing.value
                },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) BasicText(text = placeholder, style = CycloType.InputHint)
                    innerTextField()
                }
            },
        )
    }
}

/**
 * O botão do canto direito: microfone ao escrever; ao ouvir, o microfone dá lugar ao ícone do
 * teclado no mesmo sítio (e o toque passa a voltar ao teclado). Não há X: o ícone do teclado já
 * é a forma de parar de ouvir.
 *
 * Tem o zoom out dos botões mas NÃO tem háptico: entrar e sair do ditado por este botão é
 * silencioso; o háptico só acontece quando o ditado completa.
 */
@Composable
private fun BoxScope.NameFieldAction(
    isListening: Boolean,
    micVisible: Boolean,
    listening: State<Float>,
    zoom: CompletionZoom,
    onMicClick: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
) {
    val mic = remember { PathParser().parsePathString(CycloIconPaths.Mic).toPath() }
    val keyboard = remember { PathParser().parsePathString(CycloIconPaths.Keyboard).toPath() }
    val dictateDescription = stringResource(R.string.introduction_3_dictate)
    val keyboardDescription = stringResource(R.string.introduction_3_switch_to_keyboard)

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = ActionTargetEnd)
            .size(ActionTarget)
            .cycloPressable(
                onClick = { if (isListening) onSwitchToKeyboard() else onMicClick() },
                enabled = isListening || micVisible,
                haptic = false,
                pressedScale = CycloMotion.IconButtonPressScale,
            )
            .semantics { contentDescription = if (isListening) keyboardDescription else dictateDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldIcon(
            path = mic,
            alphaOf = { if (micVisible) 1f - listening.value.coerceIn(0f, 1f) else 0f },
            scaleOf = { (1f - 0.3f * listening.value.coerceIn(0f, 1f)) * zoom.typing.value },
        )
        FieldIcon(
            path = keyboard,
            alphaOf = { listening.value.coerceIn(0f, 1f) },
            scaleOf = { (0.7f + 0.3f * listening.value.coerceIn(0f, 1f)) * zoom.overlay.value },
        )
    }
}

/** Ícone de 24 dp da caixa; as animações só se leem ao desenhar. */
@Composable
private fun FieldIcon(
    path: Path,
    alphaOf: () -> Float,
    scaleOf: () -> Float,
) {
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                alpha = alphaOf()
                scaleX = scaleOf()
                scaleY = scaleOf()
            },
    ) {
        val pxPerDp = density
        withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
            drawPath(path = path, color = CycloColors.TextSecondary)
        }
    }
}
