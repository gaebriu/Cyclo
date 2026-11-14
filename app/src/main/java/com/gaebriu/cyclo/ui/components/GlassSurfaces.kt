package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.runtime.remember
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.ShadowCache
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.cycloLightSheen
import com.gaebriu.cyclo.ui.effects.drawBackdrop
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.effects.rememberBackdropSampler
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.FigmaBlur

/*
 * Fundos de vidro do Feed, das rotinas guardadas e das definições, tal como no Figma. Cada um
 * desenha por baixo o que está por trás dele (ver effects/Backdrop.kt): desfocado, nos que têm
 * "background blur" no Figma, e sempre como base da mistura Luminosity, que assim fica certa
 * também durante as animações. Sem desfoque nas definições, ficam cores sólidas.
 */

/** A forma de todas as superfícies: raio [radius] com o corner smoothing do Figma (60 %). */
internal fun roundRectPath(width: Float, height: Float, radius: Float) = smoothRoundRectPath(width, height, radius)

/**
 * Barra de navegação e mini-menu (navigation-bar, mini-menu-group): #101011 em Luminosity sobre o
 * desfoque de fundo [figmaBlur] (39 na barra, 63 no mini-menu) e sombra 0, 4, desfoque 39 (preto a
 * 24 %). Beta-3.2 (Figma): sem borda; em vez da sombra interior 0, -6, desfoque 15, duas finas em
 * Luminosity, #A3A2A7 a 15 % deslocadas 1 e -1 com desfoque 2 (um fio de luz em cima e em baixo).
 */
@Composable
fun Modifier.cycloGlassPill(cornerRadius: Dp, figmaBlur: Float = FigmaBlur.NavigationBar): Modifier {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    // As sombras desfocam-se uma vez por tamanho (ver ShadowCache), não a cada fotograma.
    val dropShadow = remember { ShadowCache() }
    val innerTop = remember { ShadowCache() }
    val innerBottom = remember { ShadowCache() }
    return backdropSampler(sampler, backdrop).drawWithCache {
        // A forma só se refaz quando o tamanho muda (antes, a cada desenho).
        val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
        onDrawBehind {
            val blend = effects.luminosity

            // Sombra exterior, só fora da forma.
            dropShadow.drawDropShadow(this, shape, 39.dp.toPx(), Color.Black.copy(alpha = 0.24f), Offset(0f, 4.dp.toPx()))
            if (effects.textures) drawBackdrop(sampler, backdrop, shape, figmaBlur)
            drawPath(shape, CycloColors.GlassFill, blendMode = blend)
            drawGlassRim(this, shape, innerTop, innerBottom, blend)
        }
    }.cycloLightSheen(cornerRadius, strength = 0.8f)
}

/** O fio de luz do vidro (beta-3.2): sombras interiores #A3A2A7 a 15 %, 0,1 e 0,-1, desfoque 2. */
internal fun drawGlassRim(scope: DrawScope, shape: Path, top: ShadowCache, bottom: ShadowCache, blend: BlendMode) = with(scope) {
    val rim = CycloColors.TextSecondary.copy(alpha = 0.15f)
    top.drawInnerShadow(this, shape, 2.dp.toPx(), rim, Offset(0f, 1.dp.toPx()), blend)
    bottom.drawInnerShadow(this, shape, 2.dp.toPx(), rim, Offset(0f, -1.dp.toPx()), blend)
}

/**
 * Cartão do Feed e das rotinas guardadas ("background" dos grupos): #101011 em Luminosity, raio 15.
 * Não tem desfoque de fundo; desenha o que está por trás só para a mistura.
 *
 * @param shadowColor cor da sombra exterior (0, 4, desfoque [shadowBlur]); null = sem sombra.
 * @param underFill desenhado por cima do fundo e por baixo do vidro (recortado à forma).
 * @param rimTop / [rimBottom] beta-3.2 (wake-time-container, Cyclo 3): sombras interiores 0,2 e 0,-2,
 *        desfoque 2, com estas cores (as do céu, a 15 %); null = sem elas.
 */
@Composable
fun Modifier.cycloCard(
    shadowColor: Color? = Color.Black.copy(alpha = 0.24f),
    shadowBlur: Dp = 39.dp,
    cornerRadius: Dp = 15.dp,
    underFill: (DrawScope.() -> Unit)? = null,
    rimTop: Color? = null,
    rimBottom: Color? = null,
): Modifier {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    // A sombra desfoca-se uma vez por tamanho (ver ShadowCache): o cartão redesenha-se a cada
    // fotograma do scroll, e antes cada desenho desfocava a forma no CPU.
    val shadow = remember { ShadowCache() }
    val innerTop = remember { ShadowCache() }
    val innerBottom = remember { ShadowCache() }
    return backdropSampler(sampler, backdrop).drawWithCache {
        // A forma só se refaz quando o tamanho muda (antes, a cada desenho: no scroll, a cada frame).
        val shape = roundRectPath(size.width, size.height, cornerRadius.toPx())
        onDrawBehind {
            val blend = effects.luminosity
            if (shadowColor != null) {
                shadow.drawDropShadow(this, shape, shadowBlur.toPx(), shadowColor, Offset(0f, 4.dp.toPx()))
            }
            if (effects.textures) drawBackdrop(sampler, backdrop, shape)
            // O que fica entre o fundo e o vidro (ex.: o brilho roxo do new-update-group).
            if (underFill != null) clipPath(shape) { underFill() }
            drawPath(shape, CycloColors.GlassFill, blendMode = blend)
            if (rimTop != null) innerTop.drawInnerShadow(this, shape, 2.dp.toPx(), rimTop, Offset(0f, 2.dp.toPx()))
            if (rimBottom != null) innerBottom.drawInnerShadow(this, shape, 2.dp.toPx(), rimBottom, Offset(0f, -2.dp.toPx()))
        }
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
        addSmoothRoundRect(inset, inset, size.width - inset, size.height - inset, (radius - inset).coerceAtLeast(0f))
    }
    drawPath(border, brush = brush, style = Stroke(width = strokeWidth), blendMode = blend)
}
