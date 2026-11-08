package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.cycloLightSheen
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.effects.drawOpaqueBackdrop
import com.gaebriu.cyclo.ui.effects.rememberBackdropSampler
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurRadiusExact

/**
 * Fundo dos dois menus da repetição, action-menu-when-to-show (159:291) e
 * action-menu-big-choose-days (178:98), tal como no Figma (Componentes 2, 160:313):
 * - raio 24;
 * - "background blur" 39 (backdrop-blur 19,5: sigma 19,5 dp, com a força exata, ver
 *   [figmaBlurRadiusExact]);
 * - preenchimento #101011 a 100 % em Luminosity;
 * - brilho exterior #A3A2A7 a 15 % (0, 0, desfoque 9);
 * - sombras interiores #A3A2A7 a 15 % (0, 2, desfoque 2) e (0, −2, desfoque 2).
 *
 * Uma só composição opaca: o que se desfoca é o fundo da app (#010101) com o ecrã por cima
 * ([drawOpaqueBackdrop]), por isso nada do que está por trás aparece nítido através do vidro. A
 * Luminosity mistura-se com essa imagem desfocada e não com o ecrã verdadeiro.
 *
 * Sem as texturas avançadas: sem desfoque, o #101011 sólido em mistura normal e o gradiente #010101
 * por cima (transparente em cima, 50 % em baixo).
 */
@Composable
internal fun Modifier.repeatMenuSurface(): Modifier {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    return backdropSampler(sampler, backdrop).drawWithCache {
        val shape = roundRectPath(size.width, size.height, CycloDimens.CornerRadius.toPx())
        val edge = CycloColors.TextSecondary.copy(alpha = EdgeAlpha)
        val glowBlur = 9.dp.toPx()
        val innerBlur = 2.dp.toPx()
        val innerOffset = 2.dp.toPx()
        val blurRadius = figmaBlurRadiusExact(FigmaBlur.Overlay)
        val fallbackShade = Brush.verticalGradient(
            listOf(CycloColors.Background.copy(alpha = 0f), CycloColors.Background.copy(alpha = FallbackShadeAlpha)),
        )
        onDrawBehind {
            // Brilho exterior, só fora da forma.
            clipPath(shape, clipOp = ClipOp.Difference) { drawBlurredPath(shape, edge, glowBlur) }

            if (effects.textures) {
                drawOpaqueBackdrop(sampler, backdrop, shape, base = CycloColors.Background, blurRadius = blurRadius)
                drawPath(shape, CycloColors.GlassFill, blendMode = effects.luminosity)
            } else {
                drawPath(shape, CycloColors.GlassFill)
                drawPath(shape, fallbackShade)
            }

            drawInnerShadow(shape, edge, Offset(0f, innerOffset), innerBlur)
            drawInnerShadow(shape, edge, Offset(0f, -innerOffset), innerBlur)
        }
    }.cycloLightSheen(CycloDimens.CornerRadius)
}

/** Opacidade do #A3A2A7 no brilho exterior e nas sombras interiores (Figma: 15 %). */
private const val EdgeAlpha = 0.15f

/** Gradiente #010101 sem as texturas avançadas: de transparente em cima até isto em baixo. */
private const val FallbackShadeAlpha = 0.5f
