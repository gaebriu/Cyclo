package com.gaebriu.cyclo.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import com.gaebriu.cyclo.ui.effects.FigmaNoise
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * O aspeto dos widgets escolhido no Widget Studio (beta-3.0): global, vale para os dois widgets e
 * para todas as instâncias. Os valores são os do Figma (Widget studio 2054:143 e Componentes 5):
 * - Opacity (opacity-slider 2055:308): os pontos "39%", "63%" e "100%".
 * - Frosted glass (frosted-glass-slider 2056:57): os pontos "24 dp", "39 dp" e "63 dp".
 * - Corner style (action-menu-corner-style 2064:278): Subtle = 15 dp, Smooth = 24 dp.
 * As predefinições são as dos widgets do Figma (Componentes 4: #101011 a 63 %, desfoque 39, raio
 * 15), que são também onde as bolas dos sliders estão no Widget Studio.
 *
 * Guarda-se como preferência do aparelho (SharedPreferences), fora das cópias de segurança, como as
 * ligações dos widgets às rotinas.
 */
data class WidgetStyle(val opacity: Int = 1, val frost: Int = 1, val corner: Int = 0) {
    val opacityValue: Float get() = Opacities[opacity.coerceIn(Opacities.indices)]
    val frostDp: Float get() = Frosts[frost.coerceIn(Frosts.indices)]
    val cornerRadius: Float get() = Corners[corner.coerceIn(Corners.indices)]

    /** Opacidade no máximo: o fundo deixa de ser vidro (Normal, sem desfoque; regra do utilizador). */
    val isSolid: Boolean get() = opacityValue >= 1f

    companion object {
        val Opacities = floatArrayOf(0.39f, 0.63f, 1f)
        val Frosts = floatArrayOf(24f, 39f, 63f)
        val Corners = floatArrayOf(15f, 24f)
        val Default = WidgetStyle()
    }
}

/** Onde o [WidgetStyle] fica guardado. */
object WidgetStyles {
    private const val Prefs = "widget_studio"
    private const val OpacityKey = "opacity"
    private const val FrostKey = "frost"
    private const val CornerKey = "corner"

    fun load(context: Context): WidgetStyle {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        return WidgetStyle(
            opacity = prefs.getInt(OpacityKey, WidgetStyle.Default.opacity).coerceIn(WidgetStyle.Opacities.indices),
            frost = prefs.getInt(FrostKey, WidgetStyle.Default.frost).coerceIn(WidgetStyle.Frosts.indices),
            corner = prefs.getInt(CornerKey, WidgetStyle.Default.corner).coerceIn(WidgetStyle.Corners.indices),
        )
    }

    fun save(context: Context, style: WidgetStyle) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
            .putInt(OpacityKey, style.opacity)
            .putInt(FrostKey, style.frost)
            .putInt(CornerKey, style.corner)
            .apply()
    }
}

/**
 * As cores da wallpaper do ecrã principal: a principal, a secundária e a terciária
 * ([WallpaperManager.getWallpaperColors], que não pede autorização nenhuma). Não dizem ONDE está cada
 * cor, só quais são, por ordem de importância. null = o sistema não as dá (ex.: algumas wallpapers
 * animadas).
 */
class WallpaperPalette(val primary: Int, val secondary: Int?, val tertiary: Int?) {
    override fun equals(other: Any?): Boolean =
        other is WallpaperPalette && other.primary == primary && other.secondary == secondary && other.tertiary == tertiary

    override fun hashCode(): Int = (primary * 31 + (secondary ?: 0)) * 31 + (tertiary ?: 0)

    companion object {
        fun current(context: Context): WallpaperPalette? = runCatching {
            val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return@runCatching null
            WallpaperPalette(
                primary = colors.primaryColor.toArgb(),
                secondary = colors.secondaryColor?.toArgb(),
                tertiary = colors.tertiaryColor?.toArgb(),
            )
        }.getOrNull()
    }
}

/** O que o fundo de um widget precisa: o estilo do Widget Studio e as cores da wallpaper. */
class WidgetLook(val style: WidgetStyle, val palette: WallpaperPalette?) {
    companion object {
        fun current(context: Context): WidgetLook = WidgetLook(WidgetStyles.load(context), WallpaperPalette.current(context))
    }
}

/**
 * O fundo dos widgets (Componentes 4, background 244:49 e 244:108): #101011 a 63 % em Luminosity
 * por cima da wallpaper desfocada (39), com o noise do Figma.
 *
 * O lançador não deixa uma app ver a wallpaper atrás do widget (e o Android não tem desfoque de
 * widgets), por isso o vidro é SIMULADO (opção D, aprovada pelo utilizador): em vez da wallpaper
 * desfocada, um campo de cor feito com as cores da wallpaper ([WallpaperPalette]), com manchas
 * grandes e suaves cuja suavidade é o "Frosted glass" (o desvio-padrão do desfoque do Figma, metade
 * do valor: 39 → 19,5). Por cima, o #101011 em Luminosity à opacidade escolhida, calculado aqui
 * píxel a píxel (é a fórmula da especificação de composição do W3C, a mesma do Figma), e o noise.
 * O resultado é opaco, como o vidro do Figma (o desfoque tapa tudo o que está atrás).
 *
 * Com a opacidade no máximo, ou sem cores da wallpaper, o fundo é #101011 sólido, em Normal.
 *
 * O preview do Widget Studio usa este mesmo desenho: o que se vê lá é o que fica no ecrã principal.
 */
internal object WidgetGlass {
    /** #101011. */
    private const val Fill = 0xFF101011.toInt()

    /** A luminância do #101011 (Lum do W3C: 0,3 R + 0,59 G + 0,11 B). */
    private val FillLum = lum(0x10 / 255f, 0x10 / 255f, 0x11 / 255f)

    /**
     * Desenha o fundo do cartão ([width] x [height] unidades do Figma) em [canvas], que já está em
     * unidades do Figma, com [pxPerUnit] píxeis por unidade.
     */
    fun draw(canvas: Canvas, width: Float, height: Float, look: WidgetLook, pxPerUnit: Float) {
        val path = smoothRoundRectPath(width, height, look.style.cornerRadius).asAndroidPath()
        val palette = look.palette
        if (look.style.isSolid || palette == null) {
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Fill })
        } else {
            val field = field(width, height, palette, look.style, pxPerUnit)
            val shader = BitmapShader(field, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(1f / pxPerUnit, 1f / pxPerUnit) })
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        }
        // O noise (MONOTONE, #000000 a 15 %, "in" a forma: com a opacidade da forma).
        val noiseAlpha = NoiseAlpha * look.style.opacityValue.let { if (look.style.isSolid || palette == null) 1f else it }
        val tile = noiseTile(pxPerUnit)
        val noise = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / pxPerUnit, 1f / pxPerUnit) })
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = noise
                alpha = (noiseAlpha * 255f).roundToInt().coerceIn(0, 255)
            },
        )
    }

    // --- O campo de cor -----------------------------------------------------------------------

    /** Uma mancha: a cor, o centro e o raio (frações da largura e da altura do cartão). */
    private class Blob(val color: FloatArray, val cx: Float, val cy: Float, val radius: Float, val strength: Float)

    /**
     * O campo do cartão, já com o #101011 em Luminosity, em píxeis do aparelho. Guardam-se os
     * últimos (o preview pede o mesmo várias vezes, e os dois tamanhos de cada widget também).
     */
    private fun field(width: Float, height: Float, palette: WallpaperPalette, style: WidgetStyle, pxPerUnit: Float): Bitmap {
        val w = max(1, (width * pxPerUnit).roundToInt())
        val h = max(1, (height * pxPerUnit).roundToInt())
        val key = FieldKey(w, h, palette, style.opacity, style.frost)
        synchronized(fields) { fields[key]?.let { return it } }

        val p = rgb(palette.primary)
        val s = palette.secondary?.let(::rgb) ?: shade(p, 0.16f)
        val t = palette.tertiary?.let(::rgb) ?: palette.secondary?.let { mix(p, rgb(it), 0.5f).let { m -> shade(m, -0.1f) } } ?: shade(p, -0.12f)
        // A composição de uma wallpaper atrás do vidro: a cor principal ocupa quase tudo, a
        // secundária entra por cima à direita, a terciária por baixo à esquerda, e formas mais
        // pequenas (uma luz da principal, pedaços das outras) dão-lhe detalhe. É esse detalhe que o
        // "Frosted glass" desfaz: a 24 as formas ainda se leem, a 63 é uma névoa de cor. Em unidades
        // do Figma, para a mesma suavidade em qualquer tamanho.
        val blobs = listOf(
            Blob(s, 0.88f * width, -0.08f * height, 0.30f * width, 0.95f),
            Blob(t, 0.06f * width, 1.12f * height, 0.27f * width, 0.9f),
            Blob(shade(p, 0.14f), 0.36f * width, 0.30f * height, 0.13f * width, 0.8f),
            Blob(s, 0.58f * width, 1.02f * height, 0.12f * width, 0.85f),
            Blob(t, 0.96f * width, 0.72f * height, 0.10f * width, 0.8f),
            Blob(shade(s, 0.1f), 0.18f * width, 0.10f * height, 0.08f * width, 0.7f),
        )
        // O desfoque do Figma é o desvio-padrão do CSS × 2 (blur 39 = 19,5).
        val sigma = style.frostDp / 2f
        val a = style.opacityValue
        val pixels = IntArray(w * h)
        val random = java.util.Random(0x5eed)
        val c = FloatArray(3)
        for (y in 0 until h) {
            val uy = (y + 0.5f) / pxPerUnit
            for (x in 0 until w) {
                val ux = (x + 0.5f) / pxPerUnit
                c[0] = p[0]; c[1] = p[1]; c[2] = p[2]
                for (blob in blobs) {
                    val dx = ux - blob.cx
                    val dy = uy - blob.cy
                    val d = sqrt(dx * dx + dy * dy)
                    // Um disco desfocado por uma gaussiana: a borda segue a função de erro, e um
                    // disco pequeno para o desfoque também perde força no meio (espalha-se).
                    val reach = 1f - exp(-(blob.radius * blob.radius) / (2f * sigma * sigma))
                    val k = blob.strength * reach * phi((blob.radius - d) / sigma)
                    if (k <= 0f) continue
                    c[0] += (blob.color[0] - c[0]) * k
                    c[1] += (blob.color[1] - c[1]) * k
                    c[2] += (blob.color[2] - c[2]) * k
                }
                // #101011 em Luminosity a [a]: o tom e a saturação do campo com a luminância do preto
                // do Cyclo, misturado à opacidade escolhida.
                val lr: Float
                val lg: Float
                val lb: Float
                run {
                    val d = FillLum - lum(c[0], c[1], c[2])
                    var r = c[0] + d
                    var g = c[1] + d
                    var b = c[2] + d
                    // ClipColor (W3C).
                    val l = lum(r, g, b)
                    val n = min(r, min(g, b))
                    val m = max(r, max(g, b))
                    if (n < 0f) {
                        r = l + (r - l) * l / (l - n)
                        g = l + (g - l) * l / (l - n)
                        b = l + (b - l) * l / (l - n)
                    }
                    if (m > 1f) {
                        r = l + (r - l) * (1f - l) / (m - l)
                        g = l + (g - l) * (1f - l) / (m - l)
                        b = l + (b - l) * (1f - l) / (m - l)
                    }
                    lr = r; lg = g; lb = b
                }
                // Um pontilhado de meio nível: sem ele, os gradientes escuros fazem degraus.
                val dither = (random.nextFloat() - 0.5f) / 255f
                val r = (c[0] + (lr - c[0]) * a + dither).coerceIn(0f, 1f)
                val g = (c[1] + (lg - c[1]) * a + dither).coerceIn(0f, 1f)
                val b = (c[2] + (lb - c[2]) * a + dither).coerceIn(0f, 1f)
                pixels[y * w + x] = (0xFF shl 24) or
                    ((r * 255f + 0.5f).toInt() shl 16) or
                    ((g * 255f + 0.5f).toInt() shl 8) or
                    (b * 255f + 0.5f).toInt()
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        synchronized(fields) {
            if (fields.size >= MaxFields) fields.remove(fields.keys.first())
            fields[key] = bitmap
        }
        return bitmap
    }

    private data class FieldKey(val w: Int, val h: Int, val palette: WallpaperPalette, val opacity: Int, val frost: Int)

    private val fields = LinkedHashMap<FieldKey, Bitmap>()
    private const val MaxFields = 12

    // --- O noise -----------------------------------------------------------------------------

    /**
     * O noise dos widgets, do SVG do Figma (244:49): feTurbulence fractalNoise, frequência 10 por
     * unidade, 3 oitavas, semente 9078; fica a luminância abaixo de 0,51 (as entradas 0 a 50 da
     * tabela discreta), a #000000 a 15 %. Uma telha de [NoiseTileUnits] unidades por densidade.
     */
    private fun noiseTile(pxPerUnit: Float): Bitmap {
        val size = max(1, (NoiseTileUnits * pxPerUnit).roundToInt())
        synchronized(noiseTiles) { noiseTiles[size]?.let { return it } }
        val mask = FigmaNoise.grainMask(
            size,
            size,
            size.toDouble() / NoiseTileUnits,
            seed = NoiseSeed,
            baseFrequency = NoiseFrequency,
            bandLow = 0,
            bandHigh = 50,
        )
        val pixels = IntArray(size * size) { if (mask[it]) 0xFF000000.toInt() else 0 }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        synchronized(noiseTiles) { noiseTiles[size] = bitmap }
        return bitmap
    }

    private val noiseTiles = HashMap<Int, Bitmap>()
    private const val NoiseTileUnits = 64
    private const val NoiseSeed = 9078
    private const val NoiseFrequency = 10.0
    private const val NoiseAlpha = 0.15f

    // --- Cor ---------------------------------------------------------------------------------

    private fun rgb(color: Int) = floatArrayOf(
        ((color shr 16) and 0xFF) / 255f,
        ((color shr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
    )

    private fun lum(r: Float, g: Float, b: Float) = 0.3f * r + 0.59f * g + 0.11f * b

    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + (b[it] - a[it]) * t }

    /** A mesma cor, mais clara ([amount] > 0) ou mais escura. */
    private fun shade(c: FloatArray, amount: Float): FloatArray =
        if (amount >= 0f) FloatArray(3) { c[it] + (1f - c[it]) * amount } else FloatArray(3) { c[it] * (1f + amount) }

    /** A função de distribuição da normal padrão (aproximação de Abramowitz e Stegun, erro < 1e-4). */
    private fun phi(x: Float): Float {
        val z = abs(x) / 1.4142135f
        val t = 1f / (1f + 0.3275911f * z)
        val poly = t * (0.2548296f + t * (-0.28449672f + t * (1.4214138f + t * (-1.4531521f + t * 1.0614054f))))
        val erf = 1f - poly * exp(-z * z)
        return if (x >= 0f) 0.5f * (1f + erf) else 0.5f * (1f - erf)
    }
}
