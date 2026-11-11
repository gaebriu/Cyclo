package com.gaebriu.cyclo.widget

import androidx.compose.ui.graphics.asAndroidPath
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.PathParser
import com.gaebriu.cyclo.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Desenha os widgets com as medidas do Figma, à escala que cabe no espaço do lançador.
 *
 * O lançador infla os RemoteViews com um contexto restrito: os TextViews ignoram as fontes da app
 * (sairiam em Roboto), as formas não têm Luminosity nem desfoque e as medidas em dp não escalam com
 * o widget. Por isso o cartão inteiro é desenhado num bitmap, em unidades do Figma (1 unidade =
 * 1 dp do Figma), com [pxPerUnit] píxeis por unidade: o widget fica igual ao Figma em qualquer
 * tamanho, com a proporção dele (nada se deforma nem se reorganiza).
 *
 * Os textos não seguem o tamanho de letra do sistema (exceção à regra da app): escalam com o
 * widget, para caberem sempre no desenho do Figma.
 */
internal class WidgetCanvas(context: Context, val pxPerUnit: Float, val look: WidgetLook) {
    private val medium: Typeface = context.resources.getFont(R.font.inter_medium)
    private val semiBold: Typeface = context.resources.getFont(R.font.inter_semibold)

    /** Um bitmap de [width] x [height] unidades, com o canvas já em unidades do Figma. */
    fun bitmap(width: Float, height: Float, draw: Canvas.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(px(width), px(height), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(pxPerUnit, pxPerUnit)
        canvas.draw()
        return bitmap
    }

    fun px(units: Float): Int = max(1, ceil(units * pxPerUnit).toInt())

    /**
     * Inter Medium ou SemiBold, 15 (os textos dos widgets no Figma), #FCFCFE. Todos os textos têm a
     * sombra 0, 0, 15, preto a 24 % (Componentes 4, beta-3.0).
     */
    fun paint(semiBold: Boolean, size: Float = TextSize, color: Int = White, shadow: Boolean = true) =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (semiBold) this@WidgetCanvas.semiBold else medium
            textSize = size
            this.color = color
            // text-shadow 0, 0, 15, preto a 24 % (as horas do wake-time-container).
            if (shadow) setShadowLayer(ShadowBlur / 2f, 0f, 0f, ShadowColor)
        }

    fun width(text: CharSequence, paint: TextPaint): Float = Layout.getDesiredWidth(text, paint)

    /**
     * Uma linha de texto com o topo da caixa de [LineHeight] em ([x], [top]), cortada com "…"
     * se passar de [maxWidth]. [centerX] centra-a nesse x em vez de a alinhar à esquerda.
     */
    fun Canvas.text(text: CharSequence, x: Float, top: Float, paint: TextPaint, maxWidth: Float, centerX: Boolean = false) {
        val fitted = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END)
        val w = ceil(width(fitted, paint)).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(fitted, 0, fitted.length, paint, w)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
        save()
        translate(if (centerX) x - w / 2f else x, top + (LineHeight - layout.height) / 2f)
        layout.draw(this)
        restore()
    }

    /**
     * Um ícone do Figma (CycloAppIconPaths) numa grelha de 15 unidades, em ([x], [y]), com a sombra
     * 0, 0, 15, preto a 24 % (beta-3.0) se [shadow].
     */
    fun Canvas.icon(pathData: String, x: Float, y: Float, color: Int, alpha: Float = 1f, shadow: Boolean = true) {
        val path = paths.getOrPut(pathData) { PathParser.createPathFromPathData(pathData) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.alpha = (alpha * 255).roundToInt()
            if (shadow) setShadowLayer(ShadowBlur / 2f, 0f, 0f, ShadowColor)
        }
        save()
        translate(x, y)
        drawPath(path, paint)
        restore()
    }

    /**
     * O fundo do cartão (background dos dois widgets, Componentes 4): o vidro do Widget Studio
     * ([WidgetGlass], com o [look] de agora), com os cantos escolhidos e o corner smoothing do Figma.
     */
    fun Canvas.card(width: Float, height: Float) {
        WidgetGlass.draw(this, width, height, look, pxPerUnit)
    }

    /**
     * Um círculo de 39 com a borda de 1 (os botões das rotinas). [fill] e [border] em Luminosity
     * quando [luminosity] (como no Figma: o vidro de baixo dá-lhes o tom); a sombra do grupo
     * (0, 0, 15, preto a 24 %) fica só por fora, como no Figma (não se vê através do botão).
     */
    fun Canvas.circleButton(x: Float, y: Float, fill: Int, border: Int, luminosity: Boolean) {
        val r = ButtonSize / 2f
        val cx = x + r
        val cy = y + r
        val bleed = ShadowBlur * 2f
        saveLayer(cx - r - bleed, cy - r - bleed, cx + r + bleed, cy + r + bleed, null)
        drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ShadowColor
            maskFilter = BlurMaskFilter(ShadowBlur / 2f, BlurMaskFilter.Blur.NORMAL)
        })
        drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        restore()
        drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            if (luminosity) blendMode = BlendMode.LUMINOSITY
        })
        drawCircle(
            cx,
            cy,
            r - 0.5f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = border
                style = Paint.Style.STROKE
                strokeWidth = 1f
                blendMode = BlendMode.LUMINOSITY
            },
        )
    }

    private val paths = HashMap<String, Path>()

    companion object {
        const val White = 0xFFFCFCFE.toInt()

        const val TextSize = 15f

        /** A altura de uma linha de Inter 15 com line-height "normal" no Figma. */
        const val LineHeight = 18f
        const val ShadowBlur = 15f
        const val ShadowColor = 0x3D000000
        const val ButtonSize = 39f
    }
}
