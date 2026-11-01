package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredCircle
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens

/**
 * Barra de progresso dos ecrãs de introdução: uma pílula preenchida (gradiente roxo, com uma
 * bolinha na ponta) e, à direita, a pílula vazia, separadas por 6 dp. Sem contorno, como no Figma.
 *
 * @param progress de 0 a 1. É lido só ao desenhar, por isso animar o valor não recompõe nada.
 */
@Composable
fun CycloProgressBar(
    progress: () -> Float,
    description: String,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CycloDimens.ProgressBarHeight)
            .semantics { contentDescription = description },
    ) {
        val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
        val p = progress().coerceIn(0f, 1f)
        val width = size.width
        val height = size.height
        val fillWidth = width * p
        val corner = CornerRadius(height / 2f)

        // Parte vazia, à direita da preenchida.
        val trackLeft = fillWidth + 6.dp.toPx()
        if (width - trackLeft > 0f) {
            drawRoundRect(
                color = CycloColors.GlassFill,
                topLeft = Offset(trackLeft, 0f),
                size = Size(width - trackLeft, height),
                cornerRadius = corner,
                blendMode = blend,
            )
        }

        if (fillWidth <= 0f) return@Canvas

        // Parte preenchida: #4A3C99 a 100 % até #4A3C99 a 63 %, da esquerda para a direita.
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(CycloColors.Accent, CycloColors.Accent.copy(alpha = 0.63f)),
                startX = 0f,
                endX = fillWidth,
            ),
            size = Size(fillWidth, height),
            cornerRadius = corner,
        )

        // Bolinha: 15 dp, a 5 dp da ponta. Nasce pequena enquanto a barra ainda é curta.
        val ballScale = (fillWidth / 25.dp.toPx()).coerceAtMost(1f)
        drawBall(
            center = Offset(fillWidth - 12.5.dp.toPx(), height / 2f),
            scale = ballScale,
        )
    }
}

private fun DrawScope.drawBall(center: Offset, scale: Float) {
    val radius = 7.5.dp.toPx() * scale
    if (radius <= 0f) return

    // Brilho por cima e por baixo: #FCFCFE a 15 %, deslocado 6, desfoque 9.
    val glow = CycloColors.TextPrimary.copy(alpha = 0.15f)
    drawBlurredCircle(center + Offset(0f, -6.dp.toPx()), radius, glow, cssBlurPx = 9.dp.toPx())
    drawBlurredCircle(center + Offset(0f, 6.dp.toPx()), radius, glow, cssBlurPx = 9.dp.toPx())

    drawCircle(color = CycloColors.TextPrimary, radius = radius, center = center)

    // Sombras interiores: preto a 15 %, deslocadas -2 e 2, desfoque 2.
    val shape = Path().apply { addOval(Rect(center, radius)) }
    val shade = Color.Black.copy(alpha = 0.15f)
    drawInnerShadow(shape, shade, Offset(0f, -2.dp.toPx()), cssBlurPx = 2.dp.toPx())
    drawInnerShadow(shape, shade, Offset(0f, 2.dp.toPx()), cssBlurPx = 2.dp.toPx())
}
