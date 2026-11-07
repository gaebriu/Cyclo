package com.gaebriu.cyclo.ui.effects

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * O ruído ("Noise") dos gradientes do Figma (Cyclo-2, Beta 2.0): MONOTONE, tamanho 2, densidade
 * 0,15, #FCFCFE a 39 %, só dentro da forma desfocada a que pertence. Está nos brilhos do topo do
 * Introduction 0, do Feed, das rotinas guardadas e da Tracking, e nos "fx spotlight" dos Introduction
 * 1 a 5.
 *
 * O export SVG do Figma diz exatamente o que o efeito é:
 *   feTurbulence type="fractalNoise" baseFrequency="0.5" numOctaves="3" stitchTiles="stitch"
 *   → feColorMatrix luminanceToAlpha
 *   → feFuncA discrete: só a faixa de luminância [0,21; 0,29) fica (as entradas 21 a 28 de 100)
 *   → a cor #FCFCFE a 39 %, composta "in" a forma desfocada, por cima dela.
 * Aqui faz-se o mesmo: o algoritmo de turbulência da especificação SVG 1.1 (com o mesmo gerador
 * de números e a mesma rede), avaliado no centro de cada píxel do aparelho em unidades do Figma (dp),
 * numa telha que se repete sem costuras (stitchTiles). A telha calcula-se uma vez por processo e
 * densidade, em segundo plano (~40 ms), e desenha-se como textura repetida: o grão fica parado no
 * ecrã (é a superfície, não se mexe com as animações).
 *
 * Desenhar: [drawFigmaNoise] põe o grão por cima do que já se desenhou, só onde a forma tem
 * opacidade (o "in" do Figma): numa camada, desenha a telha e multiplica-a pela opacidade da forma
 * ([mask], desenhada com BlendMode.DstIn).
 */
object FigmaNoise {
    /** O tamanho da telha, em dp (múltiplo de 2: a frequência 0,5 cabe um número inteiro de vezes). */
    const val TileDp = 128

    /** A semente do Figma no SVG do Introduction 0 (o padrão não importa, só o tipo de grão). */
    private const val Seed = 9000

    private const val BaseFrequency = 0.5
    private const val Octaves = 3

    /** A faixa de luminância que fica (entradas 21 a 28 da tabela discreta de 100). */
    private const val BandLow = 21
    private const val BandHigh = 28

    /** #FCFCFE a 39 %. */
    private const val GrainAlpha = 0.39f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tiles = HashMap<Float, State<ImageBitmap?>>()

    /** A telha para esta densidade (null enquanto se calcula; quem desenha volta a desenhar quando chega). */
    @Synchronized
    fun tile(density: Float): State<ImageBitmap?> = tiles.getOrPut(density) {
        val state = mutableStateOf<ImageBitmap?>(null)
        scope.launch {
            val image = render(density)
            state.value = image
        }
        state
    }

    /** Começa a calcular já (no arranque), para estar pronta no primeiro fotograma. */
    fun prewarm(density: Float) {
        tile(density)
    }

    private fun render(density: Float): ImageBitmap {
        val size = (TileDp * density).roundToInt().coerceAtLeast(1)
        val mask = grainMask(size, size, size.toDouble() / TileDp)
        val alpha = (GrainAlpha * 255f).roundToInt()
        val white = (alpha shl 24) or 0xFCFCFE
        val pixels = IntArray(size * size) { if (mask[it]) white else 0 }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return bitmap.asImageBitmap()
    }

    /**
     * Onde há grão numa telha de [width] x [height] píxeis, com [pxPerUnit] píxeis por unidade do
     * Figma. Puro (testado em FigmaNoiseTest): a fração de píxeis com grão é a "densidade".
     */
    fun grainMask(width: Int, height: Int, pxPerUnit: Double, seed: Int = Seed): BooleanArray {
        val turbulence = Turbulence(seed)
        val tileUnitsX = width / pxPerUnit
        val tileUnitsY = height / pxPerUnit
        val out = BooleanArray(width * height)
        val point = DoubleArray(2)
        for (y in 0 until height) {
            point[1] = (y + 0.5) / pxPerUnit
            for (x in 0 until width) {
                point[0] = (x + 0.5) / pxPerUnit
                val r = channel(turbulence, 0, point, tileUnitsX, tileUnitsY)
                val g = channel(turbulence, 1, point, tileUnitsX, tileUnitsY)
                val b = channel(turbulence, 2, point, tileUnitsX, tileUnitsY)
                // feColorMatrix luminanceToAlpha (sRGB, valores não pré-multiplicados).
                val luminance = 0.2125 * r + 0.7154 * g + 0.0721 * b
                // feFuncA discrete, 100 entradas: k = floor(C × 100), e C = 1 dá a última.
                val k = floor(luminance * 100.0).toInt().coerceIn(0, 99)
                out[y * width + x] = k in BandLow..BandHigh
            }
        }
        return out
    }

    private fun channel(t: Turbulence, channel: Int, point: DoubleArray, tileW: Double, tileH: Double): Double {
        val sum = t.turbulence(channel, point, BaseFrequency, BaseFrequency, Octaves, fractalSum = true, stitch = true, 0.0, 0.0, tileW, tileH)
        // fractalNoise: (soma + 1) / 2, limitado a [0, 1].
        return ((sum + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * A turbulência de Perlin da especificação SVG 1.1 (feTurbulence), linha a linha: o mesmo
     * gerador (Park–Miller), a mesma rede de 256 e os mesmos gradientes, com costura de telhas.
     */
    private class Turbulence(seed: Int) {
        private val lattice = IntArray(BSize + BSize + 2)
        private val gradient = Array(4) { Array(BSize + BSize + 2) { DoubleArray(2) } }

        init {
            var s = setupSeed(seed.toLong())
            for (k in 0 until 4) {
                for (i in 0 until BSize) {
                    lattice[i] = i
                    for (j in 0 until 2) {
                        s = random(s)
                        gradient[k][i][j] = ((s % (BSize + BSize)) - BSize).toDouble() / BSize
                    }
                    val g = gradient[k][i]
                    val len = sqrt(g[0] * g[0] + g[1] * g[1])
                    g[0] /= len
                    g[1] /= len
                }
            }
            var i = BSize - 1
            while (i > 0) {
                val k = lattice[i]
                s = random(s)
                val j = (s % BSize).toInt()
                lattice[i] = lattice[j]
                lattice[j] = k
                i--
            }
            for (n in 0 until BSize + 2) {
                lattice[BSize + n] = lattice[n]
                for (k in 0 until 4) for (j in 0 until 2) gradient[k][BSize + n][j] = gradient[k][n][j]
            }
        }

        private class Stitch(var width: Int, var height: Int, var wrapX: Int, var wrapY: Int)

        private fun noise2(channel: Int, vx: Double, vy: Double, stitch: Stitch?): Double {
            var t = vx + PerlinN
            var bx0 = t.toInt() and BM
            var bx1 = (bx0 + 1) and BM
            val rx0 = t - t.toInt()
            val rx1 = rx0 - 1.0
            t = vy + PerlinN
            var by0 = t.toInt() and BM
            var by1 = (by0 + 1) and BM
            val ry0 = t - t.toInt()
            val ry1 = ry0 - 1.0
            if (stitch != null) {
                if (bx0 >= stitch.wrapX) bx0 -= stitch.width
                if (bx1 >= stitch.wrapX) bx1 -= stitch.width
                if (by0 >= stitch.wrapY) by0 -= stitch.height
                if (by1 >= stitch.wrapY) by1 -= stitch.height
            }
            bx0 = bx0 and BM
            bx1 = bx1 and BM
            by0 = by0 and BM
            by1 = by1 and BM
            val i = lattice[bx0]
            val j = lattice[bx1]
            val b00 = lattice[i + by0]
            val b10 = lattice[j + by0]
            val b01 = lattice[i + by1]
            val b11 = lattice[j + by1]
            val sx = sCurve(rx0)
            val sy = sCurve(ry0)
            val g = gradient[channel]
            var q = g[b00]
            var u = rx0 * q[0] + ry0 * q[1]
            q = g[b10]
            var v = rx1 * q[0] + ry0 * q[1]
            val a = u + sx * (v - u)
            q = g[b01]
            u = rx0 * q[0] + ry1 * q[1]
            q = g[b11]
            v = rx1 * q[0] + ry1 * q[1]
            val b = u + sx * (v - u)
            return a + sy * (b - a)
        }

        fun turbulence(
            channel: Int,
            point: DoubleArray,
            baseFreqX: Double,
            baseFreqY: Double,
            octaves: Int,
            fractalSum: Boolean,
            stitch: Boolean,
            tileX: Double,
            tileY: Double,
            tileWidth: Double,
            tileHeight: Double,
        ): Double {
            var fx = baseFreqX
            var fy = baseFreqY
            var info: Stitch? = null
            if (stitch) {
                if (fx != 0.0) {
                    val lo = floor(tileWidth * fx) / tileWidth
                    val hi = kotlin.math.ceil(tileWidth * fx) / tileWidth
                    fx = if (fx / lo < hi / fx) lo else hi
                }
                if (fy != 0.0) {
                    val lo = floor(tileHeight * fy) / tileHeight
                    val hi = kotlin.math.ceil(tileHeight * fy) / tileHeight
                    fy = if (fy / lo < hi / fy) lo else hi
                }
                val w = (tileWidth * fx + 0.5).toInt()
                val h = (tileHeight * fy + 0.5).toInt()
                info = Stitch(w, h, (tileX * fx + PerlinN + w).toInt(), (tileY * fy + PerlinN + h).toInt())
            }
            var sum = 0.0
            var vx = point[0] * fx
            var vy = point[1] * fy
            var ratio = 1.0
            repeat(octaves) {
                val n = noise2(channel, vx, vy, info)
                sum += if (fractalSum) n / ratio else kotlin.math.abs(n) / ratio
                vx *= 2.0
                vy *= 2.0
                ratio *= 2.0
                info?.let {
                    it.width *= 2
                    it.wrapX = 2 * it.wrapX - PerlinN
                    it.height *= 2
                    it.wrapY = 2 * it.wrapY - PerlinN
                }
            }
            return sum
        }

        private companion object {
            const val BSize = 0x100
            const val BM = 0xff
            const val PerlinN = 0x1000
            const val RandM = 2147483647L
            const val RandA = 16807L
            const val RandQ = 127773L
            const val RandR = 2836L

            fun setupSeed(seed: Long): Long {
                var s = seed
                if (s <= 0) s = -(s % (RandM - 1)) + 1
                if (s > RandM - 1) s = RandM - 1
                return s
            }

            fun random(seed: Long): Long {
                var result = RandA * (seed % RandQ) - RandR * (seed / RandQ)
                if (result <= 0) result += RandM
                return result
            }

            fun sCurve(t: Double) = t * t * (3.0 - 2.0 * t)
        }
    }
}

/** A telha do ruído para a densidade do ecrã (lida ao desenhar; null até estar pronta). */
@Composable
fun rememberFigmaNoise(): State<ImageBitmap?> = FigmaNoise.tile(LocalDensity.current.density)

/**
 * Põe o grão do Figma por cima do que já está desenhado, só onde a forma tem opacidade (o "in" do
 * Figma): numa camada, [mask] desenha a opacidade da forma (a cor não conta; pode multiplicar várias
 * com [BlendMode.DstIn]) e a telha entra por cima com [BlendMode.SrcIn], com a opacidade dela. A
 * camada junta-se ao que estava por baixo (o grão por cima da forma). [amount] escala o grão.
 */
fun DrawScope.drawFigmaNoise(
    tile: ImageBitmap?,
    amount: Float = 1f,
    size: androidx.compose.ui.geometry.Size = this.size,
    mask: DrawScope.() -> Unit,
) {
    if (tile == null || amount <= 0f) return
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { alpha = amount.coerceIn(0f, 1f) })
    mask()
    drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), size = size, blendMode = BlendMode.SrcIn)
    canvas.restore()
}
