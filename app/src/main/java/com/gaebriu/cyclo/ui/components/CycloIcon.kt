package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects

/**
 * Um ícone vetorial do Figma (ver [com.gaebriu.cyclo.ui.icons.CycloAppIconPaths]), desenhado numa
 * grelha de [size] dp (1 unidade do vetor = 1 dp).
 *
 * @param luminosity se o Figma o mistura em Luminosity (passa a normal sem efeitos).
 */
@Composable
fun CycloIcon(
    path: String,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    luminosity: Boolean = true,
    alpha: () -> Float = { 1f },
) {
    val effects = LocalVisualEffects.current
    val icon = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier = modifier.size(size)) {
        val blend = if (luminosity && effects.textures) BlendMode.Luminosity else BlendMode.SrcOver
        val pxPerDp = density
        withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
            drawPath(path = icon, color = tint, alpha = alpha().coerceIn(0f, 1f), blendMode = blend)
        }
    }
}
