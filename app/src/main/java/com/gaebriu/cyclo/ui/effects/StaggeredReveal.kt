package com.gaebriu.cyclo.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entrada em cascata de vários blocos (a primeira vez que o Feed aparece): cada bloco aparece com
 * fade, desfoque 6 a 0 e uma subida curta, um pouco depois do anterior.
 *
 * @param count quantos blocos há; [play] corre-os pela ordem dos índices.
 * @param startRevealed já começa no fim (sem animação), ex.: quando o Feed volta a aparecer.
 */
@Stable
class StaggeredReveal(count: Int, startRevealed: Boolean) {
    private val items: List<Animatable<Float, AnimationVector1D>> =
        List(count) { Animatable(if (startRevealed) 1f else 0f) }

    fun progress(index: Int): Float = items.getOrNull(index)?.value ?: 1f

    suspend fun play() = coroutineScope {
        items.forEachIndexed { index, item ->
            launch {
                delay((index * CycloMotion.FeedRevealStaggerMs).toLong())
                // Beta 2.2: uma mola crítica (sem passar do fim: a opacidade e o desfoque não podem
                // passar de 1), em vez de uma curva de 900 ms; arranca e assenta sem esquinas.
                item.animateTo(1f, CycloSprings.Reveal.float)
            }
        }
    }

    suspend fun snapToEnd() = items.forEach { it.snapTo(1f) }
}

/**
 * Aplica o bloco [index] de [reveal]. Sem animações, só fade; sem desfoques, sem desfoque.
 *
 * @param rise de onde o bloco sobe (positivo: vem de baixo).
 */
@Composable
fun Modifier.staggeredReveal(reveal: StaggeredReveal, index: Int, rise: Dp = CycloMotion.FeedRevealRise): Modifier {
    val effects = LocalVisualEffects.current
    return graphicsLayer {
        val p = reveal.progress(index).coerceIn(0f, 1f)
        alpha = p
        if (effects.animationsEnabled) {
            translationY = (1f - p) * rise.toPx()
            renderEffect = if (effects.blurEnabled && p < 1f) {
                val sigma = figmaBlurPx(FigmaBlur.TextReveal) * (1f - p)
                if (sigma > 0f) BlurEffect(sigma, sigma, TileMode.Decal) else null
            } else {
                null
            }
        }
    }
}
