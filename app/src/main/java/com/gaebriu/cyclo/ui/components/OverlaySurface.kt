package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.cycloLightSheen
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
 * #A3A2A7. Sem contorno (a borda de 1 dp foi retirada a pedido). Com o dedo em cima, a luz
 * segue-o ([cycloLightSheen]).
 *
 * O desfoque usa o que está por trás ([LocalBackdrop]). Sem as texturas avançadas não há desfoque
 * e o #101011 fica em mistura normal: uma superfície sólida, sem nada transparente.
 *
 * @param exactBlur o desfoque 39 com a força exata do Figma (sigma 19,5 dp, ver
 *                  [com.gaebriu.cyclo.ui.theme.figmaBlurRadiusExact]): action-menu-when-to-show e
 *                  action-menu-big-choose-days. Os outros menus ficam como estavam.
 */
@Composable
fun Modifier.cycloOverlaySurface(cornerRadius: Dp = CycloDimens.CornerRadius, exactBlur: Boolean = false): Modifier {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    return backdropSampler(sampler).drawWithCache {
        // A forma só se refaz quando o tamanho muda (os menus a abrir mudam de tamanho; parados, não).
        val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
        onDrawBehind {
            val edge = CycloColors.TextSecondary

            // Brilho exterior, só fora da forma.
            clipPath(shape, clipOp = ClipOp.Difference) {
                drawBlurredPath(shape, edge.copy(alpha = 0.15f), 9.dp.toPx())
            }

            // Fundo desfocado e preenchimento. Sem as texturas avançadas, a mistura Luminosity passa a normal.
            if (effects.textures) drawBackdrop(sampler, backdrop, shape, FigmaBlur.Overlay, exact = exactBlur)
            val blend = effects.luminosity
            when {
                // Menus de vidro (beta-1.4.1): o #101011 deixa passar parte do fundo desfocado, para o
                // desfoque se ver. A 100 %, a mistura Luminosity troca o brilho de cada píxel pelo do
                // #101011 e, sobre o fundo neutro da app, o desfoque ficava invisível (um cinzento liso).
                exactBlur && effects.textures ->
                    drawPath(shape, CycloColors.GlassFill, alpha = FrostedFillAlpha, blendMode = blend)
                // Sem as texturas avançadas: sólido, com o gradiente #010101 por cima (mais escuro em baixo).
                exactBlur -> {
                    drawPath(shape, CycloColors.GlassFill, blendMode = blend)
                    drawPath(
                        shape,
                        Brush.verticalGradient(listOf(CycloColors.Background.copy(alpha = 0f), CycloColors.Background.copy(alpha = FallbackGradientAlpha))),
                    )
                }
                else -> drawPath(shape, CycloColors.GlassFill, blendMode = blend)
            }

            // Sombras interiores, em cima e em baixo.
            drawInnerShadow(shape, edge.copy(alpha = 0.15f), Offset(0f, -2.dp.toPx()), 2.dp.toPx())
            drawInnerShadow(shape, edge.copy(alpha = 0.15f), Offset(0f, 2.dp.toPx()), 2.dp.toPx())
        }
    }.cycloLightSheen(cornerRadius)
}

/**
 * Opacidade do #101011 nos menus de vidro (action-menu-when-to-show, action-menu-big-choose-days):
 * o resto é o conteúdo por trás, desfocado (39 do Figma, sigma exato). Escolhida à vista no
 * emulador: o que está por trás lê-se como formas desfocadas e o texto do menu continua legível.
 */
private const val FrostedFillAlpha = 0.45f

/** Gradiente #010101 desses menus sem as texturas avançadas (transparente em cima, até isto em baixo). */
private const val FallbackGradientAlpha = 0.5f
