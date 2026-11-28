package com.gaebriu.cyclo.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.roundToInt

/*
 * Um cartão a sair (apagar uma rotina, Cyclo 3.2.5): recolhe para o botão de apagar (zoom out com o
 * pivô nele, a linguagem do recuo da Cyclo) e apaga-se. Com as texturas avançadas, desfoca-se ao
 * mesmo tempo (0 a 9 do Figma, o desfoque do recuo); sem elas, o mesmo movimento sem desfoque.
 *
 * O cartão desenha-se na sua própria camada (GraphicsLayer); ao apagar, a camada passa para aqui,
 * dentro de uma camada maior ([BleedDp] à volta: o desfoque e a sombra do cartão não ficam cortados
 * nas bordas), e continua a desenhar-se por cima da lista mesmo depois de o cartão sair dela: a lista
 * pode fechar o espaço a meio da saída. Só propriedades da camada mudam a cada fotograma (escala,
 * opacidade e o desfoque): nada se fotografa nem se redesenha.
 */

/** Uma saída a decorrer. [progress] vai de 0 a 1; quem a começa anima-o e chama [CardExitState.finish]. */
@Stable
class CardExit internal constructor(
    internal val frame: GraphicsLayer,
    internal val topLeft: Offset,
    internal val blur: Boolean,
) {
    val progress = Animatable(0f)
}

/** As saídas a decorrer (desenhadas por [CardExitOverlay]). */
@Stable
class CardExitState(private val graphicsContext: GraphicsContext) {
    internal val exits = mutableStateListOf<CardExit>()

    /**
     * Começa a saída da camada [card] (o cartão, já gravado, com [size]) que está em [topLeft]
     * (coordenadas do [CardExitOverlay]), a recolher para [origin] (relativo ao cartão: o botão de
     * apagar). [blur]: com o desfoque (as texturas avançadas).
     */
    fun start(card: GraphicsLayer, size: IntSize, topLeft: Offset, origin: Offset, blur: Boolean, density: Density): CardExit {
        val bleed = with(density) { BleedDp.dp.toPx() }.roundToInt()
        val frame = graphicsContext.createGraphicsLayer()
        frame.record(density, LayoutDirection.Ltr, IntSize(size.width + bleed * 2, size.height + bleed * 2)) {
            translate(bleed.toFloat(), bleed.toFloat()) { drawLayer(card) }
        }
        frame.pivotOffset = Offset(origin.x + bleed, origin.y + bleed)
        val exit = CardExit(frame, Offset(topLeft.x - bleed, topLeft.y - bleed), blur)
        exits += exit
        return exit
    }

    /** A saída acabou: deixa de se desenhar e a camada de fora liberta-se. */
    fun finish(exit: CardExit) {
        exits -= exit
        graphicsContext.releaseGraphicsLayer(exit.frame)
    }
}

/** Onde as saídas se desenham: por cima da lista, do tamanho dela. Não desenha nada sem saídas. */
@Composable
fun CardExitOverlay(state: CardExitState, modifier: Modifier = Modifier) {
    if (state.exits.isEmpty()) return
    Canvas(modifier = modifier) {
        state.exits.forEach { exit ->
            val p = exit.progress.value.coerceIn(0f, 1f)
            val frame = exit.frame
            val s = 1f - (1f - CollapseScale) * p
            frame.scaleX = s
            frame.scaleY = s
            // A opacidade cai com uma curva suave (quem se lê é o recolher, não um fade).
            frame.alpha = 1f - smooth(p)
            frame.renderEffect = if (exit.blur) {
                val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * p
                if (sigma >= 0.5f) BlurEffect(sigma, sigma, TileMode.Decal) else null
            } else {
                null
            }
            translate(exit.topLeft.x, exit.topLeft.y) { drawLayer(frame) }
        }
    }
}

/** smoothstep de 0 a 1. */
private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** O tamanho a que o cartão recolhe (o recuo da Cyclo vai a 0,92; um cartão que sai vai mais longe). */
private const val CollapseScale = 0.85f

/** A margem da camada de fora: o desfoque (9 do Figma) e a sombra do cartão cabem nela. */
private const val BleedDp = 48f
