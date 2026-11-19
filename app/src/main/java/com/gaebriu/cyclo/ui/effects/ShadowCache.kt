package com.gaebriu.cyclo.ui.effects

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
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
 *
 * Cyclo 3.1 (desempenho): o mini-menu muda de largura em cada fotograma do deslize Feed ↔ Saved (a
 * lupa entra e sai), e cada largura nova voltava a desfocar as três sombras no CPU e a criar três
 * imagens novas, a cada fotograma. Agora:
 * - [pill] (uma pílula: as pontas são meios círculos, ver addSmoothRoundRect): a sombra interior de
 *   desfoque pequeno é IGUAL em todas as colunas do meio (só as pontas mudam, e só de sítio). Uma
 *   máscara serve para todas as larguras, desenhada em três fatias (ponta esquerda, meio repetido,
 *   ponta direita), píxel a píxel igual à de cada largura.
 * - As últimas máscaras usadas ficam guardadas ([MaxRecentBytes]): as sombras exteriores, muito
 *   desfocadas, são imagens pequenas (~12 % da escala); voltar a uma largura já vista não desfoca nada.
 */
class ShadowCache {
    private var image: ImageBitmap? = null
    private var key: Key? = null
    private var area = Rect.Zero
    private var tintColor = Color.Unspecified
    private var tint: ColorFilter? = null

    /** A pílula desenhada em fatias: quanto a largura pedida difere da da máscara (px). 0 = sem fatias. */
    private var stretch = 0f
    private var sliceCap = 0

    private data class Key(val size: Size, val cssBlurPx: Float, val inner: Boolean, val offset: Offset)

    private class Entry(val image: ImageBitmap, val area: Rect)

    /** As máscaras usadas há pouco (a mais antiga sai primeiro), até [MaxRecentBytes]. */
    private val recent = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private var recentBytes = 0L

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
     * por dentro (como [drawInnerShadow]). [blendMode]: beta-3.2, as sombras interiores em
     * Luminosity do vidro (navigation-bar, mini-menu).
     */
    fun drawInnerShadow(
        scope: DrawScope,
        shape: Path,
        cssBlurPx: Float,
        color: Color,
        offset: Offset,
        blendMode: BlendMode = BlendMode.SrcOver,
        pill: Boolean = false,
    ) = with(scope) {
        if (color.alpha <= 0f) return
        prepare(this, shape, cssBlurPx, inner = true, offset = offset, pill = pill)
        clipPath(shape) { drawMask(this, color, Offset.Zero, blendMode) }
    }

    private fun drawMask(scope: DrawScope, color: Color, shift: Offset, blendMode: BlendMode = BlendMode.SrcOver) = with(scope) {
        val mask = image ?: return
        if (tint == null || color != tintColor) {
            tintColor = color
            tint = ColorFilter.tint(color, BlendMode.SrcIn)
        }
        val left = (area.left + shift.x).toInt()
        val top = (area.top + shift.y).toInt()
        if (stretch == 0f) {
            drawImage(
                image = mask,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(mask.width, mask.height),
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(area.width.toInt(), area.height.toInt()),
                colorFilter = tint,
                blendMode = blendMode,
            )
            return
        }
        // Pílula noutra largura (escala 1, sem filtros): as duas pontas tal como estão, e o meio
        // (colunas todas iguais) repetido até à largura pedida.
        val extra = stretch.toInt()
        val cap = sliceCap
        val h = mask.height
        val middleDst = mask.width - cap * 2 + extra
        drawImage(mask, IntOffset.Zero, IntSize(cap, h), IntOffset(left, top), IntSize(cap, h), colorFilter = tint, blendMode = blendMode, filterQuality = FilterQuality.None)
        if (middleDst > 0) {
            drawImage(mask, IntOffset(cap, 0), IntSize(1, h), IntOffset(left + cap, top), IntSize(middleDst, h), colorFilter = tint, blendMode = blendMode, filterQuality = FilterQuality.None)
        }
        drawImage(mask, IntOffset(mask.width - cap, 0), IntSize(cap, h), IntOffset(left + cap + middleDst, top), IntSize(cap, h), colorFilter = tint, blendMode = blendMode, filterQuality = FilterQuality.None)
    }

    private fun prepare(scope: DrawScope, shape: Path, cssBlurPx: Float, inner: Boolean, offset: Offset, pill: Boolean = false) {
        val size = scope.size
        val newKey = Key(size, cssBlurPx, inner, offset)
        if (image != null && key == newKey) {
            stretch = 0f
            return
        }
        val sigma = (cssBlurPx / 2f).coerceAtLeast(0.5f)
        // Pílula com a mesma altura, desfoque e deslocamento, e largura inteira: a máscara que já
        // existe serve, em fatias, se o meio dela e o pedido tiverem colunas fora do alcance das pontas.
        val old = key
        if (pill && old != null && image != null && old.inner && inner && old.cssBlurPx == cssBlurPx && old.offset == offset &&
            old.size.height == size.height && old.size.width % 1f == 0f && size.width % 1f == 0f
        ) {
            val reachPx = ceil(sigma * 3f + abs(offset.x) + abs(offset.y))
            // Alcance das pontas na imagem: a margem, a meia altura da ponta redonda e o desfoque.
            val cap = (reachPx * 2f + ceil(size.height / 2f)).toInt() + 1
            val mask = image!!
            val maskScale = mask.width / area.width
            if (maskScale == 1f && mask.width - cap * 2 >= 1 && (size.width + reachPx * 2f).toInt() - cap * 2 >= 0) {
                stretch = size.width - old.size.width
                sliceCap = cap
                return
            }
        }
        stretch = 0f
        key = newKey
        recent[newKey]?.let {
            image = it.image
            area = it.area
            return
        }
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
        remember(newKey, Entry(bitmap, area))
    }

    private fun remember(key: Key, entry: Entry) {
        val bytes = entry.image.width.toLong() * entry.image.height * 4
        recent[key] = entry
        recentBytes += bytes
        val it = recent.entries.iterator()
        while (recentBytes > MaxRecentBytes && recent.size > 1 && it.hasNext()) {
            val oldest = it.next()
            if (oldest.key == key) continue
            recentBytes -= oldest.value.image.width.toLong() * oldest.value.image.height * 4
            it.remove()
        }
    }

    private companion object {
        /** Quanta memória as máscaras guardadas podem ocupar, por sombra (~1 MB). */
        const val MaxRecentBytes = 1L shl 20

        /** O desvio-padrão do desfoque dentro da imagem pequena: largo o bastante para não haver degraus. */
        const val MaskSigmaPx = 6f

        /** A escala mínima da imagem (desfoques muito largos não ficam com imagens enormes nem minúsculas). */
        const val MinScale = 0.2f
    }
}
