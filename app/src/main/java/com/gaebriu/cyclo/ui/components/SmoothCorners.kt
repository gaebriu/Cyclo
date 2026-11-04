package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Corner smoothing do Figma (60 % em toda a Cyclo): o canto deixa de ser um quarto de círculo e
 * passa a ser uma curva contínua (superelipse / squircle) — um arco de círculo mais curto ao meio,
 * ligado aos lados por duas Bézier cúbicas que começam mais longe do canto. O raio continua o mesmo
 * (o tamanho do arredondamento não muda); só a suavidade da curva muda.
 *
 * A geometria é a que o Figma descreve ("Desperately seeking squircles", figura 11–12): com
 * suavidade s e raio R, a curva começa a p = (1 + s) × R do canto e o arco central mede 90 × (1 − s)
 * graus. Quando não há espaço (p maior do que metade do lado mais curto), o Figma reduz a suavidade
 * em vez do raio: uma pílula (R = altura / 2) e um círculo ficam exatamente iguais.
 */

/** O corner smoothing do Figma usado em todos os cantos da Cyclo (60 %). */
const val CycloCornerSmoothing = 0.6f

/** Os parâmetros de um canto (a, b, c, d, p e o arco), como na figura 11.1 do artigo do Figma. */
private class CornerParams(
    val radius: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val p: Float,
    /** Metade do ângulo que fica fora do arco (graus): o arco vai de 45 − medida/2 a 45 + medida/2. */
    val arcStartOffset: Float,
    val arcSweep: Float,
)

private fun cornerParams(radius: Float, smoothing: Float, budget: Float): CornerParams {
    val r = radius.coerceIn(0f, budget)
    if (r <= 0f) return CornerParams(0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f)
    // Sem espaço para toda a suavidade: reduz-se a suavidade (preserveSmoothing = false do Figma).
    val s = min(smoothing, (budget / r - 1f).coerceAtLeast(0f))
    val p = min((1f + s) * r, budget)
    val arcMeasure = 90f * (1f - s)
    val arcSectionLength = sin(Math.toRadians(arcMeasure / 2.0)).toFloat() * r * sqrt(2f)
    val angleAlpha = (90f - arcMeasure) / 2f
    val p3ToP4 = r * tan(Math.toRadians(angleAlpha / 2.0)).toFloat()
    val angleBeta = 45f * s
    val c = p3ToP4 * cos(Math.toRadians(angleBeta.toDouble())).toFloat()
    val d = c * tan(Math.toRadians(angleBeta.toDouble())).toFloat()
    val b = (p - arcSectionLength - c - d) / 3f
    val a = 2f * b
    return CornerParams(r, a, b, c, d, p, angleAlpha, arcMeasure)
}

/**
 * Junta a [Path] um retângulo de cantos suaves (raio [radius], suavidade [smoothing]) entre
 * ([left], [top]) e ([right], [bottom]), no sentido dos ponteiros do relógio.
 */
fun Path.addSmoothRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    smoothing: Float = CycloCornerSmoothing,
) {
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return
    val k = cornerParams(radius, smoothing, min(w, h) / 2f)
    if (k.radius <= 0f) {
        addRect(Rect(left, top, right, bottom))
        return
    }
    val r = k.radius
    val (a, b, c, d, p) = listOf(k.a, k.b, k.c, k.d, k.p)
    val start = k.arcStartOffset
    val sweep = k.arcSweep

    // Canto superior direito.
    moveTo(right - p, top)
    cubicTo(right - p + a, top, right - p + a + b, top, right - p + a + b + c, top + d)
    arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f + start, sweep, false)
    cubicTo(right, top + p - a - b, right, top + p - a, right, top + p)
    // Canto inferior direito.
    lineTo(right, bottom - p)
    cubicTo(right, bottom - p + a, right, bottom - p + a + b, right - d, bottom - p + a + b + c)
    arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f + start, sweep, false)
    cubicTo(right - p + a + b, bottom, right - p + a, bottom, right - p, bottom)
    // Canto inferior esquerdo.
    lineTo(left + p, bottom)
    cubicTo(left + p - a, bottom, left + p - a - b, bottom, left + p - a - b - c, bottom - d)
    arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f + start, sweep, false)
    cubicTo(left, bottom - p + a + b, left, bottom - p + a, left, bottom - p)
    // Canto superior esquerdo.
    lineTo(left, top + p)
    cubicTo(left, top + p - a, left, top + p - a - b, left + d, top + p - a - b - c)
    arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f + start, sweep, false)
    cubicTo(left + p - a - b, top, left + p - a, top, left + p, top)
    close()
}

/** Um retângulo de cantos suaves com [width] x [height] a partir de (0, 0). */
fun smoothRoundRectPath(width: Float, height: Float, radius: Float, smoothing: Float = CycloCornerSmoothing): Path =
    Path().apply { addSmoothRoundRect(0f, 0f, width, height, radius, smoothing) }

/**
 * O RoundedCornerShape da Cyclo, com o corner smoothing do Figma (60 %). Usa-se em vez de
 * RoundedCornerShape em clip, background e sombras, com os mesmos raios. Cantos iguais (os de toda
 * a app); com cantos diferentes, cada um usa o seu raio sobre o mesmo cálculo.
 */
class SmoothRoundedCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val smoothing: Float = CycloCornerSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(radius: Dp, smoothing: Float = CycloCornerSmoothing) :
        this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        // Todos os cantos da Cyclo são iguais: usa-se o maior (um só raio por forma).
        val radius = maxOf(topStart, topEnd, bottomEnd, bottomStart)
        return Outline.Generic(smoothRoundRectPath(size.width, size.height, radius, smoothing))
    }

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        SmoothRoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun equals(other: Any?): Boolean =
        other is SmoothRoundedCornerShape && other.topStart == topStart && other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd && other.bottomStart == bottomStart && other.smoothing == smoothing

    companion object {
        /** A mesma forma com o raio em píxeis (ex.: o raio já medido de um graphicsLayer). */
        fun px(radius: Float, smoothing: Float = CycloCornerSmoothing): SmoothRoundedCornerShape {
            val corner = CornerSize(radius)
            return SmoothRoundedCornerShape(corner, corner, corner, corner, smoothing)
        }
    }

    override fun hashCode(): Int = ((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 + bottomStart.hashCode()
}
