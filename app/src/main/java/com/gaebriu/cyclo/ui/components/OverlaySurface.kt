package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.drawBackdrop
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.effects.rememberBackdropSampler
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.FigmaBlur

/**
 * Fundo dos menus, das folhas e da barra de baixo (undo-action, e os derivados action-menu,
 * action-menu-when-to-show e action-menu-big): #101011 em Luminosity sobre o desfoque de fundo
 * 39, brilho exterior (0, 0, desfoque 9) e duas sombras interiores (0, ±2, desfoque 2), todos em
 * #A3A2A7. Sem contorno (a borda de 1 dp foi retirada a pedido).
 *
 * O desfoque usa o que está por trás ([LocalBackdrop]); sem desfoque nas definições, fica sólido.
 */
@Composable
fun Modifier.cycloOverlaySurface(cornerRadius: Dp = CycloDimens.CornerRadius): Modifier {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    return backdropSampler(sampler).drawBehind {
        val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
        val edge = CycloColors.TextSecondary

        // Brilho exterior, só fora da forma.
        clipPath(shape, clipOp = ClipOp.Difference) {
            drawBlurredPath(shape, edge.copy(alpha = 0.15f), 9.dp.toPx())
        }

        // Fundo desfocado e preenchimento. Sem efeitos, a mistura Luminosity passa a normal.
        if (effects.blurEnabled) drawBackdrop(sampler, backdrop, shape, FigmaBlur.Overlay)
        val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
        drawPath(shape, CycloColors.GlassFill, blendMode = blend)

        // Sombras interiores, em cima e em baixo.
        drawInnerShadow(shape, edge.copy(alpha = 0.15f), Offset(0f, -2.dp.toPx()), 2.dp.toPx())
        drawInnerShadow(shape, edge.copy(alpha = 0.15f), Offset(0f, 2.dp.toPx()), 2.dp.toPx())
    }
}
