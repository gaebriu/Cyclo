package com.gaebriu.cyclo.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidPath
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import com.gaebriu.cyclo.ui.effects.FigmaNoise
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * O fundo dos widgets (Componentes 4, background 244:49 e 244:108, beta-3.0): #101011 a 100 %, em
 * Normal (sem Luminosity), cantos de 24 com o corner smoothing do Figma, e o noise do Figma por cima.
 * Um só aspeto para todos os widgets: o Widget Studio saiu (decisão do utilizador), com ele o vidro
 * simulado e as cores da wallpaper.
 */
internal object WidgetBackground {
    /** #101011. */
    private const val Fill = 0xFF101011.toInt()

    /** O raio dos cantos (era 15 até à beta-3.0). */
    const val CornerRadius = 24f

    /**
     * Desenha o fundo do cartão ([width] x [height] unidades do Figma) em [canvas], que já está em
     * unidades do Figma, com [pxPerUnit] píxeis por unidade.
     */
    fun draw(canvas: Canvas, width: Float, height: Float, pxPerUnit: Float) {
        val path = smoothRoundRectPath(width, height, CornerRadius).asAndroidPath()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Fill })
        // O noise (MONOTONE, #000000 a 15 %, "in" a forma).
        val noise = BitmapShader(noiseTile(pxPerUnit), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1f / pxPerUnit, 1f / pxPerUnit) })
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = noise
                alpha = (NoiseAlpha * 255f).roundToInt()
            },
        )
    }

    /**
     * O noise dos widgets, do Figma (244:49: NOISE MONOTONE, semente 9078, #000000 a 15 %; no SVG,
     * feTurbulence fractalNoise com frequência 10 por unidade, 3 oitavas, e a luminância abaixo de 0,51,
     * as entradas 0 a 50 da tabela discreta). Uma telha de [NoiseTileUnits] unidades por densidade,
     * feita uma vez.
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
}
