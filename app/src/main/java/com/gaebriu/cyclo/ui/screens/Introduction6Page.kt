package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.LetterRevealState
import com.gaebriu.cyclo.ui.components.LetterRevealText
import com.gaebriu.cyclo.ui.components.rememberLetterReveal
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Medidas do Figma, a partir do topo da área das páginas (135 dp do topo do frame de 852 dp):
// o círculo de 267 dp começa a 145 dp e o texto a 265 dp.
private val CircleTop = 145.dp
private val CircleBox = 267.dp
private val TextTop = 265.dp

// "fx circle-with-blur": anel de raio 129 e traço 9 (#4A3C99).
private const val RingRadius = 129f
private const val RingStroke = 9f

/** Largura da frente do desfoque progressivo do círculo, em fração da largura da caixa. */
private const val RevealFeather = 0.6f

/**
 * Introduction 6: "You’re all set!" (o botão de cima e o Next já saíram).
 *
 * Quando a página assenta ([active]):
 * - o texto aparece letra a letra, com a mesma animação do "Hello!" do Introduction 0;
 * - o círculo (fx circle-with-blur) fica parado e o desfoque vai-se revelando da esquerda para a
 *   direita: começa muito desfocado e acaba no desfoque do Figma. Começa e acaba com o texto;
 * - no fim, espera 1 s e chama [onFinished] (vai para o Feed).
 *
 * Sem animações no sistema, o texto só aparece com fade e o círculo já está no desfoque final.
 */
@Composable
fun Introduction6Page(
    active: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val text = stringResource(R.string.introduction_6_text)
    val reveal = rememberLetterReveal(text)
    val circle = remember { Animatable(0f) }
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(active, reveal) {
        if (!active) return@LaunchedEffect
        coroutineScope {
            launch { reveal.play() }
            launch {
                if (effects.animationsEnabled) {
                    delay(CycloMotion.LettersStartDelayMs.toLong())
                    circle.animateTo(1f, tween(CycloMotion.HelloDurationMs, easing = CycloMotion.Decelerate))
                } else {
                    circle.snapTo(1f)
                }
            }
        }
        delay(CycloMotion.AllSetHoldMs)
        latestOnFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        BlurredRing(
            reveal = { if (effects.animationsEnabled) circle.value else 1f },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = CircleTop)
                .size(CircleBox),
        )
        AllSetText(
            reveal = reveal,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = TextTop)
                .semantics { heading() },
        )
    }
}

@Composable
private fun AllSetText(reveal: LetterRevealState, modifier: Modifier = Modifier) {
    // Inter Medium 15, #A3A2A7, centrado (sem sombra, como no Figma).
    LetterRevealText(state = reveal, style = CycloType.Body, modifier = modifier)
}

/**
 * O anel desfocado. O desfoque é feito com BlurMaskFilter (não RenderEffect), por isso aparece igual
 * em qualquer aparelho. Não corta: o desfoque passa para fora da caixa de 267 dp, como no Figma.
 *
 * @param reveal de 0 (todo muito desfocado) a 1 (todo no desfoque do Figma); a frente entre os dois
 *               anda da esquerda para a direita.
 */
@Composable
private fun BlurredRing(reveal: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = (RingRadius + RingStroke / 2f).dp.toPx()
            val inner = (RingRadius - RingStroke / 2f).dp.toPx()
            addOval(Rect(center, outer))
            addOval(Rect(center, inner))
        }
        val p = reveal().coerceIn(0f, 1f)
        val feather = size.width * RevealFeather
        // A frente sai de fora à esquerda e acaba fora à direita.
        val front = -feather + p * (size.width + 2f * feather)

        if (p < 1f) {
            maskedLayer(front, feather, keepLeft = false) {
                drawBlurredPath(ring, CycloColors.Accent, FigmaBlur.CircleRevealStart.dp.toPx())
            }
        }
        if (p > 0f) {
            maskedLayer(front, feather, keepLeft = true) {
                drawBlurredPath(ring, CycloColors.Accent, FigmaBlur.Circle.dp.toPx())
            }
        }
    }
}

/**
 * Desenha [block] numa camada à parte e deixa só o lado esquerdo (ou direito) de uma frente
 * vertical em [front], com uma transição suave de [feather] px.
 */
private fun DrawScope.maskedLayer(front: Float, feather: Float, keepLeft: Boolean, block: DrawScope.() -> Unit) {
    // A camada cobre também o desfoque que sai da caixa.
    val bleed = FigmaBlur.CircleRevealStart.dp.toPx() * 1.5f
    val bounds = Rect(-bleed, -bleed, size.width + bleed, size.height + bleed)
    drawIntoCanvas { it.saveLayer(bounds, Paint()) }
    block()
    val visible = Color.Black
    val hidden = Color.Transparent
    drawRect(
        brush = Brush.horizontalGradient(
            0f to if (keepLeft) visible else hidden,
            1f to if (keepLeft) hidden else visible,
            startX = front - feather / 2f,
            endX = front + feather / 2f,
        ),
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = BlendMode.DstIn,
    )
    drawIntoCanvas { it.restore() }
}
