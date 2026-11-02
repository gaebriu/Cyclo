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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.theme.CycloColors

/*
 * Fundos de vidro do Feed e das rotinas guardadas, tal como no Figma. O "background blur" do Figma
 * (o desfoque do que passa por trás) não se aplica: o preenchimento #101011 já é opaco.
 */

private fun roundRectPath(width: Float, height: Float, radius: Float) = Path().apply {
    addRoundRect(RoundRect(0f, 0f, width, height, CornerRadius(radius)))
}

/**
 * Barra de navegação e mini-menu (navigation-bar, mini-menu-group): #101011 em Luminosity, sombra
 * 0, 4, desfoque 39 (preto a 24 %), sombra interior 0, -6, desfoque 15 (preto a 15 %) e borda de
 * 1 dp #313233 a 63 %.
 */
@Composable
fun Modifier.cycloGlassPill(cornerRadius: Dp): Modifier {
    val effects = LocalVisualEffects.current
    return drawBehind {
        val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
        val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver

        // Sombra exterior, só fora da forma.
        clipPath(shape, clipOp = ClipOp.Difference) {
            translate(top = 4.dp.toPx()) {
                drawBlurredPath(shape, Color.Black.copy(alpha = 0.24f), 39.dp.toPx())
            }
        }
        drawPath(shape, CycloColors.GlassFill, blendMode = blend)
        drawInnerShadow(shape, Color.Black.copy(alpha = 0.15f), Offset(0f, -6.dp.toPx()), 15.dp.toPx())
        drawInsideBorder(cornerRadius.toPx(), Brush.linearGradient(listOf(BorderColor, BorderColor)), blend)
    }
}

private val BorderColor = CycloColors.GlassBorder.copy(alpha = 0.63f)

/**
 * Cartão do Feed e das rotinas guardadas ("background" dos grupos): #101011 em Luminosity, raio 15.
 *
 * @param shadowColor cor da sombra exterior (0, 4, desfoque [shadowBlur]); null = sem sombra.
 */
@Composable
fun Modifier.cycloCard(
    shadowColor: Color? = Color.Black.copy(alpha = 0.24f),
    shadowBlur: Dp = 39.dp,
    cornerRadius: Dp = 15.dp,
): Modifier {
    val effects = LocalVisualEffects.current
    return drawBehind {
        val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
        val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
        if (shadowColor != null) {
            clipPath(shape, clipOp = ClipOp.Difference) {
                translate(top = 4.dp.toPx()) {
                    drawBlurredPath(shape, shadowColor, shadowBlur.toPx())
                }
            }
        }
        drawPath(shape, CycloColors.GlassFill, blendMode = blend)
    }
}

/**
 * Botão pequeno quadrado dos cartões (add-b, remove-b: 33 dp, raio 9) e opções da lista
 * (intense-exercise...: raio 15): #A3A2A7 a 15 % em Luminosity com sombra interior 0, 6,
 * desfoque 15 (preto a 15 %).
 *
 * @param innerShadow as opções não escolhidas não têm sombra interior.
 */
fun DrawScope.drawCardControl(cornerRadius: Float, blend: BlendMode, innerShadow: Boolean = true) {
    val shape = roundRectPath(size.width, size.height, cornerRadius)
    drawPath(shape, CycloColors.TextSecondary.copy(alpha = 0.15f), blendMode = blend)
    if (innerShadow) {
        drawInnerShadow(shape, Color.Black.copy(alpha = 0.15f), Offset(0f, 6.dp.toPx()), 15.dp.toPx())
    }
}

/** Borda de 1 dp por dentro da forma. */
fun DrawScope.drawInsideBorder(radius: Float, brush: Brush, blend: BlendMode = BlendMode.SrcOver) {
    val strokeWidth = 1.dp.toPx()
    val inset = strokeWidth / 2f
    val border = Path().apply {
        addRoundRect(
            RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius((radius - inset).coerceAtLeast(0f))),
        )
    }
    drawPath(border, brush = brush, style = Stroke(width = strokeWidth), blendMode = blend)
}
