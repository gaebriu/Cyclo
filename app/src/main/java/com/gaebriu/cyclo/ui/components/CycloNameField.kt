package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType

/** Máximo de caracteres do nome de uma rotina. */
const val MaxRoutineNameLength = 39

// Posições dentro da caixa de 345 x 48 dp (Figma): ícones de 24 dp, com 15 dp à direita.
private val IconTargetWidth = 39.dp
private val CloseTargetEnd = 7.5.dp
private val KeyboardTargetEnd = 46.5.dp
private val TextStart = 15.dp

/**
 * Caixa do nome da rotina (tap-to-write-box), que se transforma na caixa a ouvir
 * (tap-to-write-while-listening-box) com uma mola:
 * - o microfone vira X, o ícone do teclado entra da direita;
 * - "Tap to write" (ou o texto escrito) dá lugar a "Listening...";
 * - aparecem a borda e o brilho, e os gradientes do texto e da borda correm da esquerda para a direita.
 *
 * @param micVisible falso quando o ditado não está disponível (ex.: microfone recusado): o botão some.
 * @param onSwitchToKeyboard ícone do teclado: deixa de ouvir e passa a escrever.
 */
@Composable
fun CycloNameField(
    value: String,
    onValueChange: (String) -> Unit,
    isListening: Boolean,
    micVisible: Boolean,
    onMicClick: () -> Unit,
    onCancelListening: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val listening = animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = CycloMotion.ListeningSpring,
        label = "listening",
    )

    // Os gradientes só correm (e só gastam) enquanto a caixa está a ouvir.
    val shimmerActive by remember { derivedStateOf { listening.value > 0.001f } }
    val phase: State<Float> = if (shimmerActive && effects.animationsEnabled) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(CycloMotion.ListeningShimmerMs, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val mic = remember { PathParser().parsePathString(CycloIconPaths.Mic).toPath() }
    val close = remember { PathParser().parsePathString(CycloIconPaths.CloseSmall).toPath() }
    val keyboard = remember { PathParser().parsePathString(CycloIconPaths.Keyboard).toPath() }

    val textMeasurer = rememberTextMeasurer()
    val listeningLabel = stringResource(R.string.introduction_3_listening)
    val listeningLayout = remember(listeningLabel) { textMeasurer.measure(listeningLabel, CycloType.InputHint) }
    val placeholder = stringResource(R.string.introduction_3_placeholder)
    val switchToKeyboardDescription = stringResource(R.string.introduction_3_switch_to_keyboard)
    val cancelDictationDescription = stringResource(R.string.introduction_3_cancel_dictation)
    val dictateDescription = stringResource(R.string.introduction_3_dictate)

    Box(modifier = modifier.fillMaxWidth().height(CycloDimens.InputHeight)) {
        // Fundo; ao ouvir, brilho exterior e borda em gradiente por cima.
        Canvas(modifier = Modifier.matchParentSize()) {
            val l = listening.value.coerceIn(0f, 1f)
            val radius = CycloDimens.InputRadius.toPx()
            val shape = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
            }
            if (l > 0f) {
                // Sombra exterior do Figma: 0, 0, desfoque 9, #A3A2A7 a 15 %.
                drawBlurredPath(shape, CycloColors.TextSecondary.copy(alpha = 0.15f * l), 9.dp.toPx())
            }
            drawPath(shape, CycloColors.GlassFill)
            if (l > 0f) drawListeningBorder(radius, phase.value, l)
        }

        // Texto escrito (ou "Tap to write"): desvanece-se ao ouvir.
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = CycloColors.TextPrimary,
                backgroundColor = CycloColors.Accent.copy(alpha = 0.5f),
            ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.replace("\n", "").take(MaxRoutineNameLength)) },
                enabled = !isListening,
                singleLine = true,
                textStyle = CycloType.Input,
                cursorBrush = SolidColor(CycloColors.TextPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = TextStart, end = if (micVisible) 54.dp else 15.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .graphicsLayer {
                        val l = listening.value.coerceIn(0f, 1f)
                        alpha = 1f - l
                        translationX = -8.dp.toPx() * l
                    },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) BasicText(text = placeholder, style = CycloType.InputHint)
                        innerTextField()
                    }
                },
            )
        }

        // "Listening...": gradiente (#A3A2A7, 63 % no meio, #A3A2A7) a correr da esquerda para a direita.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val l = listening.value.coerceIn(0f, 1f)
                    if (l <= 0f) return@drawBehind
                    val left = TextStart.toPx() + (1f - l) * 8.dp.toPx()
                    val top = (size.height - listeningLayout.size.height) / 2f
                    val period = listeningLayout.size.width.toFloat()
                    val start = left + phase.value * period
                    val c = CycloColors.TextSecondary
                    drawText(
                        textLayoutResult = listeningLayout,
                        brush = Brush.linearGradient(
                            0f to c,
                            0.476f to c.copy(alpha = 0.63f),
                            1f to c,
                            start = Offset(start, 0f),
                            end = Offset(start + period, 0f),
                            tileMode = TileMode.Repeated,
                        ),
                        topLeft = Offset(left, top),
                        alpha = l,
                    )
                },
        )

        // Teclado (entra da direita ao ouvir).
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = KeyboardTargetEnd)
                .width(IconTargetWidth)
                .height(CycloDimens.InputHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = isListening,
                    role = Role.Button,
                    onClick = onSwitchToKeyboard,
                )
                .semantics { contentDescription = switchToKeyboardDescription },
            contentAlignment = Alignment.Center,
        ) {
            FieldIcon(
                path = keyboard,
                alphaOf = { listening.value.coerceIn(0f, 1f) },
                translationXPx = { (1f - listening.value) * 18.dp.toPx() },
            )
        }

        // Microfone (ao escrever), que vira X (ao ouvir) no mesmo sítio.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = CloseTargetEnd)
                .width(IconTargetWidth)
                .height(CycloDimens.InputHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = isListening || micVisible,
                    role = Role.Button,
                    onClick = { if (isListening) onCancelListening() else onMicClick() },
                )
                .semantics {
                    contentDescription = if (isListening) cancelDictationDescription else dictateDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            FieldIcon(
                path = mic,
                alphaOf = { if (micVisible) 1f - listening.value.coerceIn(0f, 1f) else 0f },
                scaleOf = { 1f - 0.3f * listening.value.coerceIn(0f, 1f) },
            )
            FieldIcon(
                path = close,
                alphaOf = { listening.value.coerceIn(0f, 1f) },
                scaleOf = { 0.7f + 0.3f * listening.value.coerceIn(0f, 1f) },
            )
        }
    }
}

/** Borda de 1 dp em gradiente (#A3A2A7 a 24 %, 9 % no meio, 24 %), que corre da esquerda para a direita. */
private fun DrawScope.drawListeningBorder(radius: Float, phase: Float, amount: Float) {
    val c = CycloColors.TextSecondary
    val inset = 0.5.dp.toPx()
    val start = phase * size.width
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to c.copy(alpha = 0.24f),
            0.5f to c.copy(alpha = 0.09f),
            1f to c.copy(alpha = 0.24f),
            start = Offset(start, 0f),
            end = Offset(start + size.width, 0f),
            tileMode = TileMode.Repeated,
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        cornerRadius = CornerRadius(radius - inset),
        style = Stroke(width = 1.dp.toPx()),
        alpha = amount,
    )
}

/** Ícone de 24 dp da caixa; as animações só se leem ao desenhar. */
@Composable
private fun FieldIcon(
    path: Path,
    alphaOf: () -> Float,
    scaleOf: () -> Float = { 1f },
    translationXPx: Density.() -> Float = { 0f },
) {
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                alpha = alphaOf()
                scaleX = scaleOf()
                scaleY = scaleOf()
                translationX = translationXPx()
            },
    ) {
        val pxPerDp = density
        withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
            drawPath(path = path, color = CycloColors.TextSecondary)
        }
    }
}
