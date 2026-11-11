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
        val blend = effects.luminosity
        val p = progress().coerceIn(0f, 1f)
        val width = size.width
        val height = size.height
        val fillWidth = width * p
        val corner = CornerRadius(height / 2f)

        // Parte vazia, à direita da preenchida, separada por 6 dp.
        // Perto dos 100 %, quando já não cabe uma pílula inteira (6 dp + a altura da barra), a parte
        // vazia passa a um círculo que encolhe e desvanece, e o espaço de 6 dp fecha na mesma
        // proporção: tudo chega a zero ao mesmo tempo que a barra enche, sem cortes nem saltos.
        val gap = 6.dp.toPx()
        val remaining = width - fillWidth
        if (remaining > 0f) {
            val shrink = (remaining / (gap + height)).coerceIn(0f, 1f)
            val trackLeft = fillWidth + gap * shrink
            val trackWidth = width - trackLeft
            val trackHeight = height * shrink
            if (trackWidth > 0f && trackHeight > 0f) {
                drawRoundRect(
                    color = CycloColors.GlassFill,
                    topLeft = Offset(trackLeft, (height - trackHeight) / 2f),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                    alpha = shrink,
                    blendMode = blend,
                )
            }
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
        drawCycloBall(
            center = Offset(fillWidth - 12.5.dp.toPx(), height / 2f),
            scale = ballScale,
        )
    }
}

/**
 * Bolinha branca (ball-b): brilhos por cima e por baixo e sombras interiores. 15 dp na barra de
 * progresso; [diameterDp] 16 nos medidores (beta-3.0: emotion-meter, sleep-quality-meter e os
 * sliders do Widget Studio).
 */
fun DrawScope.drawCycloBall(center: Offset, scale: Float = 1f, diameterDp: Float = 15f) {
    val radius = (diameterDp / 2f).dp.toPx() * scale
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
