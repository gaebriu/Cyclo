package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Estado da animação letra a letra do "header-big" do Introduction 0 ("Hello!"): cada letra
 * aparece com fade e desfoque 9 a 0, com um pequeno intervalo entre letras, enquanto o espaçamento
 * fecha de 15 % até zero. Também é usada no texto do Introduction 6.
 */
@Stable
class LetterRevealState(val text: String) {
    internal val letters: List<Animatable<Float, AnimationVector1D>> = List(text.length) { Animatable(0f) }
    internal val spacing = Animatable(0f)

    /**
     * Corre a animação inteira: começa ao fim de [CycloMotion.LettersStartDelayMs] e dura
     * [CycloMotion.HelloDurationMs] (a última letra acaba exatamente nesse tempo).
     */
    suspend fun play() {
        val letterDuration = (CycloMotion.HelloDurationMs - CycloMotion.LetterStaggerMs * (letters.size - 1))
            .coerceAtLeast(CycloMotion.LetterStaggerMs)
        coroutineScope {
            launch {
                delay(CycloMotion.LettersStartDelayMs.toLong())
                spacing.animateTo(1f, tween(CycloMotion.HelloDurationMs, easing = CycloMotion.Decelerate))
            }
            letters.forEachIndexed { index, letter ->
                launch {
                    delay((CycloMotion.LettersStartDelayMs + index * CycloMotion.LetterStaggerMs).toLong())
                    letter.animateTo(1f, tween(letterDuration, easing = CycloMotion.Decelerate))
                }
            }
        }
    }

    companion object {
        /** Duração total de [play], do início ao fim da última letra. */
        const val TotalDurationMs = CycloMotion.LettersStartDelayMs + CycloMotion.HelloDurationMs
    }
}

@Composable
fun rememberLetterReveal(text: String): LetterRevealState = remember(text) { LetterRevealState(text) }

/**
 * O texto de [state] letra a letra. Sem animações no sistema, as letras aparecem só com fade,
 * sem espaçamento nem desfoque; sem desfoques, só com fade e espaçamento.
 * Para o TalkBack, lê-se o texto inteiro, e não letra a letra.
 */
@Composable
fun LetterRevealText(
    state: LetterRevealState,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val density = LocalDensity.current
    // Um espaço sozinho mede zero; mede-se pela diferença entre "x x" e "xx".
    val measurer = rememberTextMeasurer()
    val spaceWidth = remember(style, density) {
        with(density) {
            (measurer.measure("x x", style).size.width - measurer.measure("xx", style).size.width).toDp()
        }
    }

    SpacedLetters(
        letterSpacingPx = {
            if (effects.animationsEnabled) {
                CycloMotion.HelloLetterSpacingEm * (1f - state.spacing.value) * style.fontSize.toPx()
            } else {
                0f
            }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = state.text },
    ) {
        state.text.forEachIndexed { index, letter ->
            if (letter == ' ') {
                Spacer(Modifier.width(spaceWidth))
            } else {
                BasicText(
                    text = letter.toString(),
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        val p = state.letters[index].value
                        alpha = p
                        renderEffect = if (effects.blurEnabled && effects.animationsEnabled && p < 1f) {
                            val sigma = figmaBlurPx(FigmaBlur.HelloReveal) * (1f - p)
                            BlurEffect(sigma, sigma, TileMode.Decal)
                        } else {
                            null
                        }
                        clip = false
                    },
                )
            }
        }
    }
}

/**
 * Põe as letras lado a lado com um espaçamento que se anima sem recompor: o valor só é lido ao
 * medir, por isso cada frame é só uma nova medição.
 */
@Composable
private fun SpacedLetters(
    letterSpacingPx: Density.() -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val gap = letterSpacingPx().roundToInt()
        val width = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + gap
            }
        }
    }
}
