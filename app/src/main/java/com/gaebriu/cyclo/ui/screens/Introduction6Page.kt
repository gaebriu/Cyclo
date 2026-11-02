package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

/** Resolução das imagens do anel desfocado (1/4): o desfoque é largo, por isso não se nota. */
private const val RingRasterScale = 0.25f

/**
 * O anel desfocado. O desfoque é feito com BlurMaskFilter (não RenderEffect), por isso aparece igual
 * em qualquer aparelho. Não corta: o desfoque passa para fora da caixa de 267 dp, como no Figma.
 *
 * Os dois anéis (muito desfocado e no desfoque do Figma) não mudam: desenham-se UMA vez, em imagens
 * de baixa resolução, e cada frame só desloca a máscara da frente. Antes os dois desfoques largos
 * (e duas camadas do tamanho do ecrã) refaziam-se a cada frame, o que fazia a animação perder frames.
 *
 * @param reveal de 0 (todo muito desfocado) a 1 (todo no desfoque do Figma); a frente entre os dois
 *               anda da esquerda para a direita.
 */
@Composable
internal fun BlurredRing(reveal: () -> Float, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier.drawWithCache {
            // Cada imagem cobre também o desfoque que sai da caixa: 3 sigmas do seu próprio desfoque
            // (o anel final, menos desfocado, precisa de uma margem três vezes mais pequena).
            fun bleedOf(figmaBlur: Float) = figmaBlur.dp.toPx() / 2f * 3f
            val start = RingLayer(this, FigmaBlur.CircleRevealStart, bleedOf(FigmaBlur.CircleRevealStart))
            val end = RingLayer(this, FigmaBlur.Circle, bleedOf(FigmaBlur.Circle))
            val feather = size.width * RevealFeather
            onDrawBehind {
                val p = reveal().coerceIn(0f, 1f)
                // A frente sai de fora à esquerda e acaba fora à direita.
                val front = -feather + p * (size.width + 2f * feather)
                drawIntoCanvas { canvas ->
                    if (p < 1f) start.draw(canvas.nativeCanvas, front, feather, keepLeft = false)
                    if (p > 0f) end.draw(canvas.nativeCanvas, front, feather, keepLeft = true)
                }
            }
        },
    )
}

/** Desenha o anel desfocado com [figmaBlur] numa imagem a [RingRasterScale] da resolução. */
private fun rasterRing(scope: CacheDrawScope, bleed: Float, figmaBlur: Float): android.graphics.Bitmap = with(scope) {
    val s = RingRasterScale
    val width = ((size.width + bleed * 2f) * s).toInt().coerceAtLeast(1)
    val height = ((size.height + bleed * 2f) * s).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(s, s)
    canvas.translate(bleed, bleed)
    val center = Offset(size.width / 2f, size.height / 2f)
    val ring = android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        addCircle(center.x, center.y, (RingRadius + RingStroke / 2f).dp.toPx(), android.graphics.Path.Direction.CW)
        addCircle(center.x, center.y, (RingRadius - RingStroke / 2f).dp.toPx(), android.graphics.Path.Direction.CW)
    }
    // Como em drawBlurredPath: o raio do Figma é o dobro do sigma.
    val sigma = figmaBlur.dp.toPx() / 2f
    val maskRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = CycloColors.Accent.toArgb()
        maskFilter = android.graphics.BlurMaskFilter(maskRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(ring, paint)
    bitmap
}

/**
 * Uma das imagens do anel, desenhada com a máscara da frente (sem camadas à parte). Só se pinta o
 * lado da frente onde a imagem se vê: cada frame pinta pouco mais do que a área do anel uma vez.
 */
private class RingLayer(scope: CacheDrawScope, figmaBlur: Float, bleed: Float) {
    private val bounds = Rect(-bleed, -bleed, scope.size.width + bleed, scope.size.height + bleed)
    private val image = android.graphics.BitmapShader(
        rasterRing(scope, bleed, figmaBlur),
        android.graphics.Shader.TileMode.CLAMP,
        android.graphics.Shader.TileMode.CLAMP,
    ).apply {
        setLocalMatrix(
            android.graphics.Matrix().apply {
                setScale(1f / RingRasterScale, 1f / RingRasterScale)
                postTranslate(-bleed, -bleed)
            },
        )
    }
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: android.graphics.Canvas, front: Float, feather: Float, keepLeft: Boolean) {
        // Fora da faixa da frente a máscara é 0 deste lado: não vale a pena pintar.
        val left = if (keepLeft) bounds.left else maxOf(bounds.left, front - feather / 2f)
        val right = if (keepLeft) minOf(bounds.right, front + feather / 2f) else bounds.right
        if (right <= left) return
        val visible = android.graphics.Color.BLACK
        val hidden = android.graphics.Color.TRANSPARENT
        val mask = android.graphics.LinearGradient(
            front - feather / 2f, 0f, front + feather / 2f, 0f,
            if (keepLeft) visible else hidden,
            if (keepLeft) hidden else visible,
            android.graphics.Shader.TileMode.CLAMP,
        )
        paint.shader = android.graphics.ComposeShader(image, mask, android.graphics.PorterDuff.Mode.DST_IN)
        canvas.drawRect(left, bounds.top, right, bounds.bottom, paint)
    }
}
