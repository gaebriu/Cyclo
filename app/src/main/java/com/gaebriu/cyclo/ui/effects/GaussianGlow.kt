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
    blendMode: androidx.compose.ui.graphics.BlendMode = DrawScope.DefaultBlendMode,
) {
    if (peakAlpha <= 0f || sigmaX <= 0f || sigmaY <= 0f) return
    // O gradiente é sempre o mesmo (um círculo de raio 1 com o perfil gaussiano): faz-se uma vez por
    // cor e escala-se aqui. Antes refazia-se a cada desenho (25 cores), e um brilho animado refazia-o
    // a cada fotograma. O pico entra pela opacidade do desenho (sempre ≤ 1 nestes brilhos).
    val brush = unitGlowBrush(color)
    val radius = GlowReach * sigmaX
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius * sigmaY / sigmaX, Offset.Zero)
    }) {
        drawCircle(
            brush = brush,
            radius = 1f,
            center = Offset.Zero,
            alpha = peakAlpha.coerceIn(0f, 1f),
            blendMode = blendMode,
        )
    }
}

private val glowBrushes = HashMap<Color, Brush>()

/** O perfil gaussiano num círculo de raio 1 (opaco no centro), para [color]. */
private fun unitGlowBrush(color: Color): Brush = glowBrushes.getOrPut(color) {
    val stops = Array(GlowStops + 1) { i ->
        val t = i / GlowStops.toFloat()
        val d = t * GlowReach
        t to color.copy(alpha = exp(-d * d / 2f))
    }
    Brush.radialGradient(*stops, center = Offset.Zero, radius = 1f)
}
