package com.gaebriu.cyclo.ui.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.exp

/*
 * Brilhos desenhados só com gradientes, SEM RenderEffect.
 *
 * O desfoque gaussiano de uma forma suave (círculo, elipse) dá uma mancha com perfil gaussiano,
 * e um gradiente radial com esse perfil desenha-a diretamente. Funciona igual em qualquer
 * aparelho, porque não depende do suporte do GPU a RenderEffect, que falhava no S24.
 * Conferido contra o desfoque exato: o erro máximo é de ~0,01 de opacidade (2/255).
 */

private const val GlowStops = 24
private const val GlowReach = 3.2f
private const val StripStops = 40
private const val StripReach = 3f

/**
 * Mancha com perfil gaussiano: opacidade [peakAlpha] no centro, a cair com os desvios-padrão
 * [sigmaX] e [sigmaY] (em px). Com sigmas diferentes fica uma elipse.
 */
fun DrawScope.drawGaussianGlow(
    color: Color,
    center: Offset,
    sigmaX: Float,
    sigmaY: Float,
    peakAlpha: Float,
) {
    if (peakAlpha <= 0f || sigmaX <= 0f || sigmaY <= 0f) return

    val stops = Array(GlowStops + 1) { i ->
        val t = i / GlowStops.toFloat()
        val d = t * GlowReach
        t to color.copy(alpha = (peakAlpha * exp(-d * d / 2f)).coerceIn(0f, 1f))
    }
    val radius = GlowReach * sigmaX
    withTransform({
        translate(center.x, center.y)
        scale(1f, sigmaY / sigmaX, Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(*stops, center = Offset.Zero, radius = radius),
            radius = radius,
            center = Offset.Zero,
        )
    }
}

/**
 * O que um desfoque gaussiano ([sigma], em px) faz a uma faixa retangular que vai de [left] a [right]
 * e de [top] para baixo (a borda de baixo estende-se, como o desfoque com TileMode.Clamp).
 * A opacidade em cada ponto é o produto de um perfil vertical por um horizontal, e ambos têm
 * fórmula exata, por isso desenha-se com dois gradientes. As coordenadas são as do DrawScope.
 */
fun DrawScope.drawBlurredStripGlow(
    color: Color,
    left: Float,
    right: Float,
    top: Float,
    sigma: Float,
) {
    if (color.alpha <= 0f || sigma <= 0f || right <= left) return

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())

        // 1) Perfil vertical: sobe de 0 para a opacidade da cor à volta da borda de cima da faixa.
        val verticalStops = Array(StripStops + 1) { i ->
            val t = i / StripStops.toFloat()
            val y = top + (t * 2f - 1f) * StripReach * sigma
            t to color.copy(alpha = color.alpha * normalCdf((y - top) / sigma))
        }
        drawRect(
            brush = Brush.verticalGradient(
                *verticalStops,
                startY = top - StripReach * sigma,
                endY = top + StripReach * sigma,
            ),
        )

        // 2) Perfil horizontal, applied como máscara (DstIn): 1 dentro da faixa, a cair nas pontas.
        val x0 = left - StripReach * sigma
        val x1 = right + StripReach * sigma
        val horizontalStops = Array(StripStops + 1) { i ->
            val t = i / StripStops.toFloat()
            val x = x0 + t * (x1 - x0)
            val h = normalCdf((x - left) / sigma) - normalCdf((x - right) / sigma)
            t to Color.Black.copy(alpha = h.coerceIn(0f, 1f))
        }
        drawRect(
            brush = Brush.horizontalGradient(*horizontalStops, startX = x0, endX = x1),
            blendMode = BlendMode.DstIn,
        )

        canvas.restore()
    }
}

/** Função de distribuição da normal padrão. */
private fun normalCdf(z: Float): Float = 0.5f * (1f + erf(z * 0.70710678f))

/** erf pela aproximação 7.1.26 de Abramowitz e Stegun (erro < 1,5e-7). */
private fun erf(x: Float): Float {
    val sign = if (x < 0f) -1f else 1f
    val ax = abs(x)
    val t = 1f / (1f + 0.3275911f * ax)
    val poly = (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t
    return sign * (1f - poly * exp(-ax * ax))
}
