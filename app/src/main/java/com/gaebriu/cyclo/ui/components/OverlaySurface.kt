package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens

/**
 * Fundo dos menus e das folhas que aparecem por cima do ecrã (action-menu, action-menu-big):
 * #101011 (Luminosity), borda de 1 dp em gradiente, brilho exterior (0, 0, desfoque 9) e duas
 * sombras interiores (0, ±2, desfoque 2), todos em #A3A2A7.
 *
 * O desfoque do que está por trás não se aplica: seria RenderEffect, e o fundo é escuro e liso.
 */
@Composable
fun Modifier.cycloOverlaySurface(cornerRadius: Dp = CycloDimens.CornerRadius): Modifier {
    val effects = LocalVisualEffects.current
    return drawBehind {
        val radius = cornerRadius.toPx()
        val shape = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
        }
        val edge = CycloColors.TextSecondary

        // Brilho exterior, só fora da forma.
        clipPath(shape, clipOp = ClipOp.Difference) {
            drawBlurredPath(shape, edge.copy(alpha = 0.15f), 9.dp.toPx())
        }

        // Fundo. Sem efeitos, a mistura Luminosity passa a normal.
        val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
        drawPath(shape, CycloColors.GlassFill, blendMode = blend)

        // Sombras interiores, em cima e em baixo.
        drawInnerShadow(shape, edge.copy(alpha = 0.15f), Offset(0f, -2.dp.toPx()), 2.dp.toPx())
        drawInnerShadow(shape, edge.copy(alpha = 0.15f), Offset(0f, 2.dp.toPx()), 2.dp.toPx())

        // Borda de 1 dp, por dentro: 63 % / 39 % / 63 % a 24 % de opacidade da tinta.
        val strokeWidth = 1.dp.toPx()
        val inset = strokeWidth / 2f
        val borderShape = Path().apply {
            addRoundRect(
                RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius - inset)),
            )
        }
        drawPath(
            borderShape,
            brush = Brush.verticalGradient(
                0f to edge.copy(alpha = 0.63f * 0.24f),
                0.5f to edge.copy(alpha = 0.39f * 0.24f),
                1f to edge.copy(alpha = 0.63f * 0.24f),
                startY = 0f,
                endY = size.height,
            ),
            style = Stroke(width = strokeWidth),
            blendMode = blend,
        )
    }
}
