package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawGaussianGlow
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.icons.HeaderIcon
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.sqrt

/**
 * Ícone com o holofote roxo por trás (sleep-icon, sun-icon...), 63 x 95 dp.
 *
 * O holofote é um círculo de 63 dp (#4A3C99, 63 % de opacidade) com desfoque 100 do Figma,
 * desenhado como mancha gaussiana (sem RenderEffect).
 * O ícone leva gradiente vertical #FCFCFE a #9F9FA0 e mistura Luminosity, como no design.
 */
@Composable
fun IntroIconHeader(icon: HeaderIcon, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    val shape = remember(icon) { PathParser().parsePathString(icon.path).toPath() }

    Box(modifier = modifier.size(63.dp, 95.dp)) {
        // Holofote: círculo de 63 dp desfocado. Desenha-se como uma mancha gaussiana equivalente
        // (sem RenderEffect), por isso aparece igual em qualquer aparelho.
        Spacer(
            modifier = Modifier
                .size(63.dp)
                .drawBehind {
                    val radius = 31.5.dp.toPx()
                    val sigma = figmaBlurPx(FigmaBlur.Spotlight)
                    val effectiveSigma = sqrt(sigma * sigma + radius * radius / 4f)
                    drawGaussianGlow(
                        color = CycloColors.Accent,
                        center = Offset(size.width / 2f, size.height / 2f),
                        sigmaX = effectiveSigma,
                        sigmaY = effectiveSigma,
                        peakAlpha = 0.63f * radius * radius / (2f * effectiveSigma * effectiveSigma),
                    )
                },
        )

        Spacer(
            modifier = Modifier
                .offset(y = 32.dp)
                .size(63.dp)
                .drawBehind {
                    val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
                    val pxPerDp = density
                    withTransform({
                        scale(pxPerDp, pxPerDp, Offset.Zero)
                        translate(-CycloIconPaths.BedOriginX, -CycloIconPaths.BedOriginY)
                    }) {
                        drawPath(
                            path = shape,
                            brush = Brush.verticalGradient(
                                0f to Color(0xFFFCFCFE),
                                1f to Color(0xFF9F9FA0),
                                startY = icon.gradientTop,
                                endY = icon.gradientBottom,
                            ),
                            blendMode = blend,
                        )
                    }
                },
        )
    }
}
