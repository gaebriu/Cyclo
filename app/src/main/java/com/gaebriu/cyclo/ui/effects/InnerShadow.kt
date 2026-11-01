package com.gaebriu.cyclo.ui.effects

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.abs

/**
 * Sombra interior ("inner shadow" do Figma).
 *
 * @param cssBlurPx raio de desfoque tal como o Figma o indica (em px, 1 px = 1 dp ao desenhar).
 */
fun DrawScope.drawInnerShadow(
    shape: Path,
    color: Color,
    offset: Offset,
    cssBlurPx: Float,
) {
    // O BlurMaskFilter usa um "raio" que não é o desvio-padrão; convertemos para
    // ficar com o mesmo aspeto do desfoque do Figma.
    val sigma = cssBlurPx / 2f
    val maskRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val reach = cssBlurPx * 2f + abs(offset.x) + abs(offset.y)

    // Tudo o que está fora da forma, deslocado por "offset": é isto que projeta a sombra para dentro.
    val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(shape.getBounds().inflate(reach))
        addPath(shape, offset)
    }
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
        asFrameworkPaint().maskFilter = BlurMaskFilter(maskRadius, BlurMaskFilter.Blur.NORMAL)
    }
    clipPath(shape) {
        drawIntoCanvas { canvas -> canvas.drawPath(outside, paint) }
    }
}

/**
 * Círculo desfocado, para as sombras exteriores ("drop shadow" do Figma) da bolinha da barra de progresso.
 *
 * @param cssBlurPx raio de desfoque tal como o Figma o indica.
 */
fun DrawScope.drawBlurredCircle(
    center: Offset,
    radius: Float,
    color: Color,
    cssBlurPx: Float,
) {
    val sigma = cssBlurPx / 2f
    val maskRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
        asFrameworkPaint().maskFilter = BlurMaskFilter(maskRadius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas -> canvas.drawCircle(center, radius, paint) }
}

/**
 * Forma desfocada (sombra exterior / brilho do Figma), para quando a forma é um caminho qualquer.
 * Desenha-se por baixo da forma opaca, por isso só se vê o que sai para fora dela.
 *
 * @param cssBlurPx raio de desfoque tal como o Figma o indica.
 */
fun DrawScope.drawBlurredPath(
    path: Path,
    color: Color,
    cssBlurPx: Float,
) {
    val sigma = cssBlurPx / 2f
    val maskRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
        asFrameworkPaint().maskFilter = BlurMaskFilter(maskRadius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas -> canvas.drawPath(path, paint) }
}
