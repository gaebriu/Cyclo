package com.gaebriu.cyclo.ui.effects

import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.exp
import kotlin.math.roundToInt

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

/**
 * A mesma mancha branca de [drawGaussianGlow], como um shader do Android, para se juntar a outros
 * num só desenho (o brilho do Feed, Cyclo 3.2.1; ver FeedTopGlow). Os mesmos gradientes, pelas mesmas
 * contas: as paragens são as de [unitGlowBrush] (8 bits, como as cores do Compose) e a opacidade do
 * desenho, que lá vinha do Paint (também 8 bits), entra aqui nas paragens. Fora da elipse não há
 * nada (DECAL), como fora do círculo de lá. null = nada a desenhar.
 */
internal fun gaussianGlowShader(center: Offset, sigmaX: Float, sigmaY: Float, peakAlpha: Float): Shader? {
    if (peakAlpha <= 0f || sigmaX <= 0f || sigmaY <= 0f) return null
    val peak = (peakAlpha.coerceIn(0f, 1f) * 255f).roundToInt() / 255f
    val colors = IntArray(GlowStops + 1) { i ->
        val d = i / GlowStops.toFloat() * GlowReach
        val stop = Color.White.copy(alpha = exp(-d * d / 2f)).alpha
        Color.White.copy(alpha = stop * peak).toArgb()
    }
    val positions = FloatArray(GlowStops + 1) { it / GlowStops.toFloat() }
    val radius = GlowReach * sigmaX
    return RadialGradient(0f, 0f, 1f, colors, positions, Shader.TileMode.DECAL).apply {
        setLocalMatrix(
            Matrix().apply {
                setScale(radius, radius * sigmaY / sigmaX)
                postTranslate(center.x, center.y)
            },
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
