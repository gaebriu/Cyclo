package com.gaebriu.cyclo.ui.effects

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Uma sombra do Figma (exterior ou interior) de uma superfície, desfocada UMA vez numa imagem pequena
 * e desenhada pronta a cada fotograma (beta-3.0, otimização).
 *
 * Porquê: o vidro dos cartões, da barra de navegação e do mini-menu volta a desenhar-se a cada
 * fotograma do scroll e das páginas (lê o que tem por trás). As sombras eram um caminho com
 * BlurMaskFilter a cada desenho, e o Skia desfoca no CPU a máscara de um caminho que não é um
 * retângulo simples (os cantos suaves do Figma não são): um cartão de 345 dp com desfoque 39 era uma
 * máscara de ~1200 x 600 px desfocada por fotograma, por cartão, e o Unified view tem vários no ecrã.
 *
 * Agora a forma desfocada faz-se uma vez por tamanho e por desfoque, a branco, numa imagem a uma
 * escala menor (um desfoque largo não tem detalhe fino: com o desvio-padrão a [MaskSigmaPx] px na
 * imagem, ampliar com filtro bilinear não se distingue do original); a cor entra só ao desenhar
 * (tint SrcIn), por isso pode mudar (a cor do céu, a sombra a apagar-se numa rotina desligada) sem
 * voltar a desfocar. O recorte pela forma continua a ser feito no tamanho real, nítido.
 */
class ShadowCache {
    private var image: ImageBitmap? = null
    private var key: Key? = null
    private var area = Rect.Zero
    private var tintColor = Color.Unspecified
    private var tint: ColorFilter? = null

    private data class Key(val size: Size, val cssBlurPx: Float, val inner: Boolean, val offset: Offset)

    /**
     * A sombra exterior de [shape] (do tamanho [DrawScope.size]): a forma desfocada ([cssBlurPx],
     * como o Figma o indica), deslocada [offset], a [color], só FORA da forma ("show behind" desligado).
     */
    fun drawDropShadow(scope: DrawScope, shape: Path, cssBlurPx: Float, color: Color, offset: Offset) = with(scope) {
        if (color.alpha <= 0f) return
        prepare(this, shape, cssBlurPx, inner = false, offset = Offset.Zero)
        clipPath(shape, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawMask(this, color, offset)
        }
    }

    /**
     * A sombra interior de [shape]: o que fica fora da forma, deslocado [offset] e desfocado, visto
     * por dentro (como [drawInnerShadow]).
     */
    fun drawInnerShadow(scope: DrawScope, shape: Path, cssBlurPx: Float, color: Color, offset: Offset) = with(scope) {
        if (color.alpha <= 0f) return
        prepare(this, shape, cssBlurPx, inner = true, offset = offset)
        clipPath(shape) { drawMask(this, color, Offset.Zero) }
    }

    private fun drawMask(scope: DrawScope, color: Color, shift: Offset) = with(scope) {
        val mask = image ?: return
        if (tint == null || color != tintColor) {
            tintColor = color
            tint = ColorFilter.tint(color, BlendMode.SrcIn)
        }
        drawImage(
            image = mask,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(mask.width, mask.height),
            dstOffset = IntOffset((area.left + shift.x).toInt(), (area.top + shift.y).toInt()),
            dstSize = IntSize(area.width.toInt(), area.height.toInt()),
            colorFilter = tint,
        )
    }

    private fun prepare(scope: DrawScope, shape: Path, cssBlurPx: Float, inner: Boolean, offset: Offset) {
        val size = scope.size
        val newKey = Key(size, cssBlurPx, inner, offset)
        if (image != null && key == newKey) return
        key = newKey
        val sigma = (cssBlurPx / 2f).coerceAtLeast(0.5f)
        // Até onde a sombra chega (3 desvios-padrão) mais o deslocamento, em píxeis inteiros.
        val reach = ceil(sigma * 3f + abs(offset.x) + abs(offset.y))
        area = Rect(-reach, -reach, ceil(size.width) + reach, ceil(size.height) + reach)
        val scale = (MaskSigmaPx / sigma).coerceIn(MinScale, 1f)
        val w = ceil(area.width * scale).toInt().coerceAtLeast(1)
        val h = ceil(area.height * scale).toInt().coerceAtLeast(1)
        // A área em píxeis da imagem é inteira: o desenho usa exatamente a área que a imagem cobre.
        area = Rect(area.left, area.top, area.left + w / scale, area.top + h / scale)
        val bitmap = ImageBitmap(w, h)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-area.left, -area.top)
        // O BlurMaskFilter usa um "raio" que não é o desvio-padrão (ver drawBlurredPath); segue a
        // escala do canvas, por isso o desfoque fica o mesmo depois de ampliado.
        val maskRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
        val paint = Paint().apply {
            color = Color.White
            isAntiAlias = true
            asFrameworkPaint().maskFilter = BlurMaskFilter(maskRadius, BlurMaskFilter.Blur.NORMAL)
        }
        if (inner) {
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(area.inflate(reach))
                addPath(shape, offset)
            }
            canvas.drawPath(outside, paint)
        } else {
            canvas.drawPath(shape, paint)
        }
        image = bitmap
    }

    private companion object {
        /** O desvio-padrão do desfoque dentro da imagem pequena: largo o bastante para não haver degraus. */
        const val MaskSigmaPx = 6f

        /** A escala mínima da imagem (desfoques muito largos não ficam com imagens enormes nem minúsculas). */
        const val MinScale = 0.2f
    }
}
