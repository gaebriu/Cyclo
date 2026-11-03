package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.delay
import kotlin.math.sqrt

// Medidas do Figma, a partir do topo da área das páginas (135 dp do topo do frame de 852 dp):
// o círculo de 267 dp começa a 145 dp e o texto a 265 dp.
private val CircleTop = 145.dp
private val CircleBox = 267.dp
private val TextTop = 265.dp

// "fx circle-with-blur": anel de raio 129 e traço 9 (#4A3C99).
private const val RingRadius = 129f
private const val RingStroke = 9f

/**
 * Espaçamento extra das letras no início, no texto do Introduction 6 e do loading-data (fração do
 * tamanho da letra). Num texto de 15 abre o mesmo, a olho, que os 15 % do "Hello!" de 39.
 */
internal const val RingTextSpacingEm = 0.3f

/** A argola começa um pouco maior e assenta no tamanho do Figma, enquanto as letras se juntam. */
private const val RingStartScale = 1.08f

/**
 * Introduction 6: "You’re all set!" (o botão de cima e o Next já saíram).
 *
 * Quando a página assenta ([active]), um só movimento, com a filosofia do "Hello!" do
 * Introduction 0 (tudo entra em foco a juntar-se):
 * - o texto aparece letra a letra, com o ritmo e o desfoque do "Hello!" (à escala do texto), e o
 *   espaçamento fecha de [RingTextSpacingEm] até ao do Figma;
 * - a argola (fx circle-with-blur) entra em foco: a opacidade sobe e o desfoque desce de forma
 *   contínua de CircleRevealStart até ao do Figma, com a física de uma mola; ao mesmo tempo
 *   encolhe de [RingStartScale] para o tamanho do Figma, na mesma mola do espaçamento das letras;
 * - no fim, espera 1 s e chama [onFinished] (vai para o Feed).
 *
 * Sem animações no sistema, o texto e a argola só aparecem com fade.
 */
@Composable
fun Introduction6Page(
    active: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val text = stringResource(R.string.introduction_6_text)
    val reveal = rememberLetterReveal(text, spacingEm = RingTextSpacingEm, settles = true)
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(active, reveal) {
        if (!active) return@LaunchedEffect
        reveal.play()
        delay(CycloMotion.AllSetHoldMs)
        latestOnFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        BlurredRing(
            alpha = { reveal.focus },
            blur = { if (effects.animationsEnabled) 1f - reveal.focus else 0f },
            scale = { ringScale(reveal, effects.animationsEnabled) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = CircleTop)
                .size(CircleBox),
        )
        LetterRevealText(
            state = reveal,
            // Inter Medium 15, #A3A2A7, centrado (sem sombra, como no Figma).
            style = CycloType.Body,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = TextTop)
                .semantics { heading() },
        )
    }
}

/** O tamanho da argola: de [RingStartScale] ao do Figma, com o espaçamento das letras. */
internal fun ringScale(reveal: LetterRevealState, animationsEnabled: Boolean): Float =
    if (animationsEnabled) RingStartScale + (1f - RingStartScale) * reveal.settle else 1f

/** Resolução da imagem do anel (1/4): o desfoque é largo, por isso não se nota. */
private const val RingRasterScale = 0.25f

/**
 * O anel desfocado (fx circle-with-blur). O desfoque do Figma (39) está numa imagem desenhada UMA
 * vez com BlurMaskFilter, por isso em repouso é igual em qualquer aparelho, com ou sem desfoques
 * ligados. Não corta: o desfoque passa para fora da caixa de 267 dp, como no Figma.
 *
 * Para entrar em foco, a imagem é desfocada mais um pouco na GPU (RenderEffect), e esse desfoque
 * extra desce continuamente até zero: os desfoques gaussianos somam-se em quadratura, por isso o
 * desfoque total vai de [FigmaBlur.CircleRevealStart] a [FigmaBlur.Circle] sem saltos (antes eram
 * duas imagens a trocar, e a meio viam-se duas argolas). O desfoque extra corre na resolução da
 * imagem (1/16 dos píxeis), por isso é leve; em repouso não há desfoque extra nenhum.
 *
 * @param alpha opacidade (0 a 1).
 * @param blur 0 = o desfoque do Figma; 1 = o desfoque de [FigmaBlur.CircleRevealStart].
 * @param scale tamanho em relação ao do Figma, à volta do centro.
 */
@Composable
internal fun BlurredRing(
    alpha: () -> Float,
    blur: () -> Float,
    scale: () -> Float,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    Spacer(
        modifier = modifier.drawWithCache {
            val s = RingRasterScale
            // A imagem cobre também o desfoque mais largo: 3 sigmas do desfoque inicial.
            val bleed = FigmaBlur.CircleRevealStart.dp.toPx() / 2f * 3f
            val bitmap = rasterRing(this, bleed, FigmaBlur.Circle)
            val image = bitmap.asImageBitmap()
            val layer = obtainGraphicsLayer()
            val layerSize = IntSize(bitmap.width, bitmap.height)
            layer.record(this, layoutDirection, layerSize) { drawImage(image) }
            // O desfoque extra (sigma, na resolução da imagem) que leva o do Figma ao inicial.
            val finalSigma = FigmaBlur.Circle.dp.toPx() / 2f
            val startSigma = FigmaBlur.CircleRevealStart.dp.toPx() / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            var lastSigma = -1f
            onDrawBehind {
                val a = alpha().coerceIn(0f, 1f)
                if (a <= 0.002f) return@onDrawBehind
                val b = if (effects.blurEnabled) blur().coerceIn(0f, 1f) else 0f
                // O desfoque total vai de final a start; o extra é o que falta, em quadratura.
                val total = finalSigma + (startSigma - finalSigma) * b
                val extra = sqrt((total * total - finalSigma * finalSigma).coerceAtLeast(0f)) * s
                // Só se cria um efeito novo quando o desfoque muda o suficiente para se ver.
                if (extra < 0.25f) {
                    if (lastSigma != 0f) {
                        layer.renderEffect = null
                        lastSigma = 0f
                    }
                } else if (kotlin.math.abs(extra - lastSigma) > 0.05f) {
                    layer.renderEffect = BlurEffect(extra, extra, TileMode.Decal)
                    lastSigma = extra
                }
                layer.alpha = a
                val k = scale() / s
                translate(center.x, center.y) {
                    scale(k, k, pivot = Offset.Zero) {
                        translate(-layerSize.width / 2f, -layerSize.height / 2f) { drawLayer(layer) }
                    }
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
    // Centrado na imagem (a imagem pode ter arredondado um píxel).
    canvas.translate(width / 2f, height / 2f)
    canvas.scale(s, s)
    val ring = android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        addCircle(0f, 0f, (RingRadius + RingStroke / 2f).dp.toPx(), android.graphics.Path.Direction.CW)
        addCircle(0f, 0f, (RingRadius - RingStroke / 2f).dp.toPx(), android.graphics.Path.Direction.CW)
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
