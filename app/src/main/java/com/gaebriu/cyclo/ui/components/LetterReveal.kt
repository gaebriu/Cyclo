package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSpring
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import com.gaebriu.cyclo.ui.theme.springProgress
import kotlin.math.floor

/**
 * Estado da animação letra a letra do "header-big" do Introduction 0 ("Hello!"): cada letra
 * aparece com fade e desfoque 9 a 0, com um pequeno intervalo entre letras, enquanto o espaçamento
 * fecha até ao do Figma. Também é usada no texto do Introduction 6 e do loading-data.
 *
 * O ritmo é sempre o do "Hello!": cada letra demora [LetterDurationMs] a aparecer e começa
 * [CycloMotion.LetterStaggerMs] depois da anterior, seja qual for o comprimento do texto (um texto
 * mais comprido demora mais, em vez de as letras ficarem mais rápidas). O "Hello!" (6 letras)
 * dura exatamente o mesmo: [CycloMotion.HelloDurationMs].
 *
 * Tudo sai de UM relógio ([elapsedMs], ao frame): as letras, o espaçamento e o que se sincroniza
 * com eles (a argola do Introduction 6 e do loading-data, com [focus] e [settle]). Antes, cada
 * letra tinha a sua animação, lançada com um delay, e os começos não calhavam no mesmo frame.
 *
 * @param spacingEm espaçamento extra entre letras no início, em fração do tamanho da letra.
 * @param settles false: o espaçamento fecha com a curva do "Hello!" (Introduction 0, a referência).
 *                true: fecha com uma mola ([settle]) que acompanha a argola, para o texto e a argola
 *                assentarem como um só movimento (Introduction 6 e loading-data).
 */
@Stable
class LetterRevealState(
    val text: String,
    val spacingEm: Float = CycloMotion.HelloLetterSpacingEm,
    val settles: Boolean = false,
) {
    private val clock = Animatable(0f)

    /** Do início da primeira letra ao fim da última (2 000 ms no "Hello!"). */
    val revealMs: Int = CycloMotion.LetterStaggerMs * (text.length - 1).coerceAtLeast(0) + LetterDurationMs

    /** Duração total de [play], com o atraso inicial. */
    val durationMs: Int = CycloMotion.LettersStartDelayMs + revealMs

    /** Milissegundos desde o início de [play] (lê-se só ao desenhar, para não recompor). */
    val elapsedMs: Float get() = clock.value

    private val finished: Boolean get() = clock.value >= durationMs

    /**
     * A mola do espaçamento e do movimento da argola: criticamente amortecida (sem ressalto), a
     * assentar quando a última letra acaba, e a começar já com alguma velocidade (não parte parada).
     */
    private val settleSpring = CycloSpring(
        response = (2f * Math.PI.toFloat()) / (SettleOmegaSeconds / (revealMs / 1000f)),
        dampingFraction = 1f,
    )

    /** Progresso (0 a 1) da letra [index], com a curva e a duração de uma letra do "Hello!". */
    fun letter(index: Int): Float {
        val start = CycloMotion.LettersStartDelayMs + index * CycloMotion.LetterStaggerMs
        return CycloMotion.Decelerate.transform(((clock.value - start) / LetterDurationMs).coerceIn(0f, 1f))
    }

    /** O espaçamento a fechar: 0 (aberto, [spacingEm]) a 1 (o do Figma). */
    val spacing: Float
        get() {
            if (finished) return 1f
            val t = clock.value - CycloMotion.LettersStartDelayMs
            return if (settles) {
                settle
            } else {
                CycloMotion.Decelerate.transform((t / revealMs).coerceIn(0f, 1f))
            }
        }

    /**
     * O movimento que acompanha as letras (a argola a assentar): uma mola de 0 a 1 do início da
     * primeira letra ao fim da última.
     */
    val settle: Float
        get() {
            if (finished) return 1f
            val seconds = (clock.value - CycloMotion.LettersStartDelayMs) / 1000f
            return springProgress(seconds, settleSpring, velocity = SettleStartVelocity / (revealMs / 1000f))
        }

    /**
     * Um elemento a entrar em foco com as letras (a argola): opacidade e desfoque, como uma letra,
     * mas com a física de uma mola ([CycloSprings.Focus]) em vez de uma curva.
     */
    val focus: Float
        get() {
            if (finished) return 1f
            val seconds = (clock.value - CycloMotion.LettersStartDelayMs) / 1000f
            return springProgress(seconds, CycloSprings.Focus, velocity = FocusStartVelocity).coerceIn(0f, 1f)
        }

    /**
     * Corre a animação inteira: começa ao fim de [CycloMotion.LettersStartDelayMs]; o espaçamento
     * fecha durante [revealMs] e a última letra acaba nesse tempo.
     */
    suspend fun play() {
        clock.snapTo(0f)
        clock.animateTo(durationMs.toFloat(), tween(durationMs, easing = LinearEasing))
    }

    companion object {
        /**
         * Quanto demora cada letra a aparecer: a das letras do "Hello!" (6 letras em
         * [CycloMotion.HelloDurationMs], com [CycloMotion.LetterStaggerMs] entre elas = 1 500 ms).
         */
        const val LetterDurationMs = CycloMotion.HelloDurationMs - CycloMotion.LetterStaggerMs * 5

        /** O tamanho do "Hello!" (header-big, 39): o desfoque das letras é proporcional a ele. */
        const val HelloFontSizeSp = 39f

        /**
         * Rigidez da mola do [settle], em "ω × duração": 7,4 deixa-a a 0,2 % do fim quando a
         * última letra acaba (o resto não se vê).
         */
        private const val SettleOmegaSeconds = 7.4f

        /** Velocidade inicial do [settle] (em durações por segundo): parte já a andar, sem arranque. */
        private const val SettleStartVelocity = 2.6f

        /** Velocidade inicial do [focus] (por segundo): a argola começa a aparecer logo. */
        private const val FocusStartVelocity = 2.4f
    }
}

@Composable
fun rememberLetterReveal(
    text: String,
    spacingEm: Float = CycloMotion.HelloLetterSpacingEm,
    settles: Boolean = false,
): LetterRevealState = remember(text, spacingEm, settles) { LetterRevealState(text, spacingEm, settles) }

/**
 * O texto de [state] letra a letra. Sem animações no sistema, as letras aparecem só com fade,
 * sem espaçamento nem desfoque; sem desfoques, só com fade e espaçamento.
 * Para o TalkBack, lê-se o texto inteiro, e não letra a letra.
 *
 * As letras ficam SEMPRE nas posições do texto inteiro (medido de uma vez, com o kerning e o
 * alinhamento do estilo, e a quebrar em linhas se não couber): o espaçamento anima-se só com um
 * deslocamento de cada letra em relação ao centro da sua linha, abaixo do píxel. Nada se volta a
 * medir durante a animação, a largura não muda e o texto não treme (antes o espaçamento andava em
 * píxeis inteiros e o texto voltava a centrar-se em cada frame).
 */
@Composable
fun LetterRevealText(
    state: LetterRevealState,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val measurer = rememberTextMeasurer()
    val text = state.text
    // As letras visíveis (os espaços só contam para as posições).
    val glyphs = remember(text) { text.indices.filter { !text[it].isWhitespace() } }
    // Calculados ao medir e lidos pelas camadas das letras (sem recompor): a parte do píxel da
    // posição de cada letra e o seu lugar em relação ao centro da linha.
    val fractionX = remember(text) { FloatArray(text.length) }
    val slot = remember(text) { FloatArray(text.length) }

    Layout(
        content = {
            glyphs.forEach { index ->
                BasicText(
                    text = text[index].toString(),
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        val p = state.letter(index)
                        alpha = p
                        renderEffect = if (effects.blurEnabled && effects.animationsEnabled && p < 1f) {
                            // O desfoque 9 do "Hello!" à escala desta letra: num texto de 15 parece
                            // igual ao do "Hello!".
                            val scale = style.fontSize.value / LetterRevealState.HelloFontSizeSp
                            val sigma = figmaBlurPx(FigmaBlur.HelloReveal) * scale * (1f - p)
                            if (sigma > 0.05f) BlurEffect(sigma, sigma, TileMode.Decal) else null
                        } else {
                            null
                        }
                        // O que falta do espaçamento: cada letra afasta-se do centro da sua linha.
                        translationX = if (effects.animationsEnabled) {
                            val gap = state.spacingEm * (1f - state.spacing) * style.fontSize.toPx()
                            fractionX[index] + slot[index] * gap
                        } else {
                            fractionX[index]
                        }
                        clip = false
                    },
                )
            }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
    ) { measurables, constraints ->
        val layout = measurer.measure(
            text = text,
            style = style,
            softWrap = true,
            constraints = Constraints(maxWidth = constraints.maxWidth),
        )
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = layout.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = layout.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        // Posição de cada letra no texto inteiro e o seu lugar em relação ao centro da linha.
        for (line in 0 until layout.lineCount) {
            val inLine = glyphs.filter { layout.getLineForOffset(it) == line }
            val center = (inLine.size - 1) / 2f
            inLine.forEachIndexed { i, index -> slot[index] = i - center }
        }
        layout(width, height) {
            glyphs.forEachIndexed { i, index ->
                val x = layout.getHorizontalPosition(index, usePrimaryDirection = true)
                val whole = floor(x)
                fractionX[index] = x - whole
                placeables[i].place(whole.toInt(), layout.getLineTop(layout.getLineForOffset(index)).toInt())
            }
        }
    }
}

