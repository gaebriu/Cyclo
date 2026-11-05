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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.icons.HeaderIcon
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Onde fica a imagem do fx spotlight em relação ao canto de cima à esquerda do círculo de 63 dp.
// Têm de coincidir com LEFT, TOP, WIDTH e HEIGHT de tools/bake_spotlight.py.
private val SpotlightLeft = (-152).dp
private val SpotlightTop = (-20).dp
private val SpotlightWidth = 367.dp
private val SpotlightHeight = 235.dp

/** Centro da caixa de 63 x 63 do ícone, nas unidades do SVG (o sol fica centrado aqui). */
private const val IconCenter = 31.5f

/** Este ponto rodado [degrees] graus (sentido horário no ecrã) à volta de [pivot]. */
private fun Offset.rotateAround(pivot: Offset, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val dx = x - pivot.x
    val dy = y - pivot.y
    return Offset(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
}

/**
 * Ícone com o holofote roxo por trás (sleep-icon, sun-icon...), 63 x 95 dp.
 *
 * O holofote ("fx spotlight") é, no Figma, um círculo de 63 dp (#4A3C99, 63 %) com desfoque
 * PROGRESSIVO, de 9 em cima a 100 em baixo: quase nítido no topo e a espalhar-se para baixo.
 * Como o desfoque progressivo não existe no Android (nem no SVG do Figma), vem pré-calculado na
 * imagem `fx_spotlight` (ver tools/bake_spotlight.py). Desenhar uma imagem funciona em qualquer
 * aparelho, sem RenderEffect.
 * O ícone leva gradiente vertical #FCFCFE a #9F9FA0 e mistura Luminosity, como no design.
 *
 * @param rotation rotação do ícone em graus (sentido horário), à volta do centro da caixa de 63 dp.
 *        É lida só ao desenhar, por isso animá-la não recompõe nada. O holofote não roda.
 */
@Composable
fun IntroIconHeader(
    icon: HeaderIcon,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
) {
    val effects = LocalVisualEffects.current
    val shape = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    val spotlight: ImageBitmap = ImageBitmap.imageResource(R.drawable.fx_spotlight)

    Box(modifier = modifier.size(63.dp, 95.dp)) {
        Spacer(
            modifier = Modifier
                .size(63.dp)
                .drawBehind {
                    drawImage(
                        image = spotlight,
                        dstOffset = IntOffset(SpotlightLeft.toPx().roundToInt(), SpotlightTop.toPx().roundToInt()),
                        dstSize = IntSize(SpotlightWidth.toPx().roundToInt(), SpotlightHeight.toPx().roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                },
        )

        Spacer(
            modifier = Modifier
                .offset(y = 32.dp)
                .size(63.dp)
                .drawBehind {
                    val blend = effects.luminosity
                    val pxPerDp = density
                    val degrees = rotation()
                    val pivot = Offset(IconCenter, IconCenter) +
                        Offset(CycloIconPaths.BedOriginX, CycloIconPaths.BedOriginY)
                    withTransform({
                        scale(pxPerDp, pxPerDp, Offset.Zero)
                        translate(-CycloIconPaths.BedOriginX, -CycloIconPaths.BedOriginY)
                        if (degrees != 0f) rotate(degrees, pivot)
                    }) {
                        // O gradiente roda ao contrário, para a luz continuar a vir de cima.
                        val start = Offset(pivot.x, icon.gradientTop).rotateAround(pivot, -degrees)
                        val end = Offset(pivot.x, icon.gradientBottom).rotateAround(pivot, -degrees)
                        drawPath(
                            path = shape,
                            brush = Brush.linearGradient(
                                0f to Color(0xFFFCFCFE),
                                1f to Color(0xFF9F9FA0),
                                start = start,
                                end = end,
                            ),
                            blendMode = blend,
                        )
                    }
                },
        )
    }
}
