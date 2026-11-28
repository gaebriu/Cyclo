package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.ShadowCache
import com.gaebriu.cyclo.ui.effects.drawBlurredCircle
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens

/**
 * O anel de progresso das Introduction e do editor (progress-bar, Cyclo 3, beta-3.2; antes uma barra
 * horizontal): um círculo de 39 dp #101011 com a sombra 0, 4, desfoque 39 (preto a 24 %), o arco
 * #4A3C99 de 4 dp por dentro da borda (Cyclo 3.2.5: o "fx" com o fio de luz por cima saiu do Figma).
 *
 * O arco começa às 12 horas e enche no sentido dos ponteiros (o Figma desenha o círculo inteiro e
 * não diz onde começa). As duas pontas são SEMPRE redondas, também quase a 100 %, quando a ponta
 * da frente encosta à de trás; a 100 % é um círculo fechado. Muito perto de 0 o arco não aparece
 * de repente como um ponto: acende nos primeiros graus.
 *
 * @param progress de 0 a 1. É lido só ao desenhar, por isso animar o valor não recompõe nada.
 */
@Composable
fun CycloProgressRing(
    progress: () -> Float,
    description: String,
    modifier: Modifier = Modifier,
) {
    val dropShadow = remember { ShadowCache() }
    Spacer(
        modifier = modifier
            .size(CycloDimens.CircleButtonSize)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
            }
            .drawWithCache {
                val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
                val stroke = RingStroke.toPx()
                val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)
                onDrawBehind {
                    dropShadow.drawDropShadow(this, circle, 39.dp.toPx(), Color.Black.copy(alpha = 0.24f), Offset(0f, 4.dp.toPx()))
                    drawPath(circle, CycloColors.GlassFill)
                    val sweep = 360f * progress().coerceIn(0f, 1f)
                    if (sweep > 0f) {
                        drawArc(
                            color = CycloColors.Accent,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            alpha = (sweep / RingFadeInDegrees).coerceIn(0f, 1f),
                            style = arcStyle,
                        )
                    }
                }
            },
    )
}

/** A espessura do arco (stroke de 4, INSIDE no Figma). */
private val RingStroke = 4.dp

/** Nos primeiros graus o arco acende em vez de aparecer como um ponto. */
private const val RingFadeInDegrees = 10f

/**
 * Bolinha branca (ball-b): brilhos por cima e por baixo e sombras interiores. 15 dp na barra de
 * progresso; [diameterDp] 16 nos medidores (beta-3.0: emotion-meter, sleep-quality-meter e os
 * sliders do Widget Studio).
 */
fun DrawScope.drawCycloBall(
    center: Offset,
    scale: Float = 1f,
    diameterDp: Float = 15f,
    color: Color = CycloColors.TextPrimary,
    shade: Color = Color.Black.copy(alpha = 0.15f),
    shadeBlurDp: Float = 2f,
) {
    val radius = (diameterDp / 2f).dp.toPx() * scale
    if (radius <= 0f) return

    // Brilho por cima e por baixo: a cor da bola a 15 %, deslocado 6, desfoque 9.
    val glow = color.copy(alpha = 0.15f)
    drawBlurredCircle(center + Offset(0f, -6.dp.toPx()), radius, glow, cssBlurPx = 9.dp.toPx())
    drawBlurredCircle(center + Offset(0f, 6.dp.toPx()), radius, glow, cssBlurPx = 9.dp.toPx())

    drawCircle(color = color, radius = radius, center = center)

    // Sombras interiores: [shade], deslocadas -2 e 2, desfoque [shadeBlurDp] (2 na barra; 4 nos medidores, Cyclo 3.2).
    val shape = Path().apply { addOval(Rect(center, radius)) }
    drawInnerShadow(shape, shade, Offset(0f, -2.dp.toPx()), cssBlurPx = shadeBlurDp.dp.toPx())
    drawInnerShadow(shape, shade, Offset(0f, 2.dp.toPx()), cssBlurPx = shadeBlurDp.dp.toPx())
}
