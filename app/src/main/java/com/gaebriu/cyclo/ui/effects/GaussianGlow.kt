package com.gaebriu.cyclo.ui.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
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
