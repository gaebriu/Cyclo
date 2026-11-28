package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
            drawGlassFill(shape, blend)
            // A pílula (raio ≥ meia altura) desenha os fios em fatias quando muda de largura (o
            // mini-menu no deslize das páginas; ver ShadowCache).
            drawGlassRim(this, shape, innerTop, innerBottom, blend, pill = cornerRadius.toPx() * 2f >= size.height)
        }
    }.cycloLightSheen(cornerRadius, strength = 0.8f)
}

/**
 * O fio de luz do vidro (Cyclo 3.2.5): sombras interiores #A3A2A7 a 9 %, 0,2 e 0,-2, desfoque 2
 * (antes 15 %, 0,±1). Barra de navegação, mini-menu, close-b e back-b.
 */
internal fun drawGlassRim(scope: DrawScope, shape: Path, top: ShadowCache, bottom: ShadowCache, blend: BlendMode, pill: Boolean = false) = with(scope) {
    top.drawInnerShadow(this, shape, 2.dp.toPx(), GlassRimColor, Offset(0f, 2.dp.toPx()), blend, pill)
    bottom.drawInnerShadow(this, shape, 2.dp.toPx(), GlassRimColor, Offset(0f, -2.dp.toPx()), blend, pill)
}

/** A cor dos fios de luz do vidro e dos menus (Cyclo 3.2.5): #A3A2A7 a 9 %. */
internal val GlassRimColor = CycloColors.TextSecondary.copy(alpha = 0.09f)

/**
 * O fundo de vidro (Cyclo 3.2.5): #101011 em [blend] (Luminosity) e, por cima, uma segunda camada
 * #101011 a 39 % em Normal. Todas as superfícies #101011 do Figma passaram a ter as duas.
 */
fun DrawScope.drawGlassFill(shape: Path, blend: BlendMode) {
    drawPath(shape, CycloColors.GlassFill, blendMode = blend)
    drawPath(shape, GlassFillTop)
}

/** A camada de cima do vidro: #101011 a 39 %. */
val GlassFillTop = CycloColors.GlassFill.copy(alpha = 0.39f)

/**
 * Cartão do Feed e das rotinas guardadas ("background" dos grupos): #101011 em Luminosity, raio 15.
 * Não tem desfoque de fundo; desenha o que está por trás só para a mistura.
 *
 * @param shadowColor cor da sombra exterior (0, 4, desfoque [shadowBlur]); null = sem sombra.
 * @param rimTop / [rimBottom] beta-3.2 (wake-time-container, Cyclo 3): sombras interiores 0,2 e 0,-2,
 *        desfoque 2, com estas cores (as do céu, a 15 %); null = sem elas.
 */
@Composable
fun Modifier.cycloCard(
    shadowColor: Color? = Color.Black.copy(alpha = 0.24f),
    shadowBlur: Dp = 39.dp,
    cornerRadius: Dp = 15.dp,
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
            drawGlassFill(shape, blend)
            if (rimTop != null) innerTop.drawInnerShadow(this, shape, 2.dp.toPx(), rimTop, Offset(0f, 2.dp.toPx()))
            if (rimBottom != null) innerBottom.drawInnerShadow(this, shape, 2.dp.toPx(), rimBottom, Offset(0f, -2.dp.toPx()))
        }
    }
}

/**
 * Botão pequeno quadrado dos cartões (add-b, remove-b: 33 dp, raio 9): #A3A2A7 a 15 % em
 * Luminosity (Cyclo 3.2.5: sem a sombra interior 0, 6, desfoque 15).
 */
fun DrawScope.drawCardControl(cornerRadius: Float, blend: BlendMode) {
    val shape = roundRectPath(size.width, size.height, cornerRadius)
    drawPath(shape, CycloColors.TextSecondary.copy(alpha = 0.15f), blendMode = blend)
}

/** Borda de [width] (1 dp por omissão) por dentro da forma. */
fun DrawScope.drawInsideBorder(radius: Float, brush: Brush, blend: BlendMode = BlendMode.SrcOver, width: Dp = 1.dp) {
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2f
    val border = Path().apply {
        addSmoothRoundRect(inset, inset, size.width - inset, size.height - inset, (radius - inset).coerceAtLeast(0f))
    }
    drawPath(border, brush = brush, style = Stroke(width = strokeWidth), blendMode = blend)
}
