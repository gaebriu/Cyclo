package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import com.gaebriu.cyclo.ui.theme.smoothStep
import com.gaebriu.cyclo.ui.theme.springProgress
import kotlinx.coroutines.flow.first
import kotlin.math.floor

/**
 * Estado da animação letra a letra do "header-big" do Introduction 0 ("Hello!"), também usada no
 * texto do Introduction 7 ("You’re all set!") e do loading-data.
 *
 * O ritmo é sempre o do "Hello!": cada letra demora [LetterDurationMs] a aparecer e começa
 * [CycloMotion.LetterStaggerMs] depois da anterior, seja qual for o comprimento do texto (um texto
 * mais comprido demora mais, em vez de as letras ficarem mais rápidas).
 *
 * Cada letra (Beta 2.2) tem três movimentos com tempos próprios, que juntos a fazem "condensar":
 * - [letterAlpha]: a opacidade chega primeiro (nos primeiros [AlphaShare] do tempo da letra);
 * - [letterFocus]: o desfoque desfaz-se devagar, durante a letra toda, e só no fim fica nítida;
 * - [letterRise]: a letra pousa de [RiseEm] mais abaixo, com uma mola sem ressalto.
 * O espaçamento entre letras fecha por cima disto ([spacing]). Antes, opacidade e desfoque seguiam
 * a mesma curva, ao mesmo tempo, e a letra parecia só acender.
 *
 * Tudo sai de UM relógio ([elapsedMs], ao frame). Por omissão é um relógio próprio ([play]); o
 * "Hello!" usa o relógio da cena do brilho do Introduction 0 ([clockMs]), para as letras e o
 * gradiente serem a mesma composição (ver Introduction0Screen).
 *
 * @param spacingEm espaçamento extra entre letras no início, em fração do tamanho da letra.
 * @param settles false: o espaçamento fecha com a curva do "Hello!" (Introduction 0, a referência).
 *                true: fecha com uma mola criticamente amortecida ([settle]), que assenta quando a
 *                última letra acaba (Introduction 7 e loading-data).
 * @param clockMs um relógio de fora (ms desde o início, lido em estado do Compose); null = o próprio.
 */
@Stable
class LetterRevealState(
    val text: String,
    val spacingEm: Float = CycloMotion.HelloLetterSpacingEm,
    val settles: Boolean = false,
    private val clockMs: (() -> Float)? = null,
) {
    private val clock = Animatable(0f)

    /** Do início da primeira letra ao fim da última ([CycloMotion.HelloDurationMs] no "Hello!"). */
    val revealMs: Int = CycloMotion.LetterStaggerMs * (text.length - 1).coerceAtLeast(0) + LetterDurationMs

    /** Duração total de [play], com o atraso inicial. */
    val durationMs: Int = CycloMotion.LettersStartDelayMs + revealMs

    /** Milissegundos desde o início (lê-se só ao desenhar, para não recompor). */
    val elapsedMs: Float get() = clockMs?.invoke() ?: clock.value

    private val finished: Boolean get() = elapsedMs >= durationMs

    /**
     * A mola do espaçamento: criticamente amortecida (sem ressalto), a
     * assentar quando a última letra acaba, e a começar já com alguma velocidade (não parte parada).
     */
    private val settleSpring = CycloSpring(
        response = (2f * Math.PI.toFloat()) / (SettleOmegaSeconds / (revealMs / 1000f)),
        dampingFraction = 1f,
    )

    /** O tempo da letra [index], de 0 (ainda não começou) a 1 (acabou). */
    private fun letterTime(index: Int): Float {
        val start = CycloMotion.LettersStartDelayMs + index * CycloMotion.LetterStaggerMs
        return ((elapsedMs - start) / LetterDurationMs).coerceIn(0f, 1f)
    }

    /** A opacidade da letra [index]: chega cedo, com uma curva suave nas duas pontas. */
    fun letterAlpha(index: Int): Float = AlphaEasing.transform((letterTime(index) / AlphaShare).coerceIn(0f, 1f))

    /** Quanto a letra [index] já está focada (0 = o desfoque todo, 1 = nítida): devagar, até ao fim dela. */
    fun letterFocus(index: Int): Float = FocusEasing.transform(letterTime(index))

    /** Quanto falta à letra [index] para pousar (1 = [RiseEm] abaixo, 0 = no sítio). */
    fun letterRise(index: Int): Float {
        val t = letterTime(index)
        if (t >= 1f) return 0f
        return 1f - springProgress(t * LetterDurationMs / 1000f, RiseSpring)
    }

    /** Progresso (0 a 1) da letra [index]: a opacidade (sem animações, as letras só aparecem assim). */
    fun letter(index: Int): Float = letterAlpha(index)

    /** O espaçamento a fechar: 0 (aberto, [spacingEm]) a 1 (o do Figma). */
    val spacing: Float
        get() {
            if (finished) return 1f
            val t = elapsedMs - CycloMotion.LettersStartDelayMs
            return if (settles) {
                settle
            } else {
                CycloMotion.Decelerate.transform((t / revealMs).coerceIn(0f, 1f))
            }
        }

    /** O espaçamento com [settles]: uma mola de 0 a 1 do início da primeira letra ao fim da última. */
    val settle: Float
        get() {
            if (finished) return 1f
            val seconds = (elapsedMs - CycloMotion.LettersStartDelayMs) / 1000f
            return springProgress(seconds, settleSpring, velocity = SettleStartVelocity / (revealMs / 1000f))
        }

    /**
     * Corre a animação inteira: começa ao fim de [CycloMotion.LettersStartDelayMs]; o espaçamento
     * fecha durante [revealMs] e a última letra acaba nesse tempo. Com um relógio de fora, só espera
     * que ele chegue ao fim.
     */
    suspend fun play() {
        if (clockMs != null) {
            snapshotFlow { elapsedMs >= durationMs }.first { it }
            return
        }
        clock.snapTo(0f)
        clock.animateTo(durationMs.toFloat(), tween(durationMs, easing = LinearEasing))
    }

    companion object {
        /**
         * Quanto demora cada letra a aparecer: a das letras do "Hello!" (6 letras em
         * [CycloMotion.HelloDurationMs], com [CycloMotion.LetterStaggerMs] entre elas).
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

        /** A parte do tempo de cada letra em que a opacidade chega ao fim. */
        private const val AlphaShare = 0.62f

        /** Opacidade: entra devagar, sobe e assenta sem esquinas. */
        private val AlphaEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)

        /** Foco: o desfoque fica um pouco no início e resolve-se com calma até ao fim da letra. */
        private val FocusEasing = CubicBezierEasing(0.4f, 0f, 0.15f, 1f)

        /** De onde a letra pousa (fração do tamanho da letra, para baixo). */
        const val RiseEm = 0.06f

        /** A letra a pousar: sem ressalto, assenta em ~70 % do tempo dela. */
        private val RiseSpring = CycloSpring(response = 0.62f, dampingFraction = 1f)
    }
}

@Composable
fun rememberLetterReveal(
    text: String,
    spacingEm: Float = CycloMotion.HelloLetterSpacingEm,
    settles: Boolean = false,
    clockMs: (() -> Float)? = null,
): LetterRevealState = remember(text, spacingEm, settles, clockMs != null) { LetterRevealState(text, spacingEm, settles, clockMs) }

/**
 * O texto de [state] letra a letra. Sem animações no sistema, as letras aparecem só com fade,
 * sem espaçamento, sem desfoque e sem pousar; sem desfoques, sem desfoque.
 * Para o TalkBack, lê-se o texto inteiro, e não letra a letra.
 *
 * As letras ficam SEMPRE nas posições do texto inteiro (medido de uma vez, com o kerning e o
 * alinhamento do estilo, e a quebrar em linhas se não couber): o espaçamento anima-se só com um
 * deslocamento de cada letra em relação ao centro da sua linha, abaixo do píxel. Nada se volta a
 * medir durante a animação, a largura não muda e o texto não treme.
 *
 * @param exit a saída (0 = à vista, 1 = saiu; lida só ao desenhar): as letras saem uma a uma, da
 *        primeira à última, em [ExitSpread] da saída, cada uma a desfocar-se, a apagar-se e a descer
 *        um pouco (o loading-data). A saída de cada letra é o inverso da entrada, mais rápida.
 */
@Composable
fun LetterRevealText(
    state: LetterRevealState,
    style: TextStyle,
    modifier: Modifier = Modifier,
    exit: () -> Float = { 0f },
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
            glyphs.forEachIndexed { order, index ->
                BasicText(
                    text = text[index].toString(),
                    style = style,
                    modifier = Modifier.graphicsLayer {
                        val animated = effects.animationsEnabled
                        // A saída desta letra (0 a 1), em onda da primeira à última.
                        val leave = letterExit(exit(), order, glyphs.size)
                        alpha = state.letterAlpha(index) * (1f - leave)
                        val fontPx = style.fontSize.toPx()
                        renderEffect = if (effects.blurEnabled && animated) {
                            // O desfoque 9 do "Hello!" à escala desta letra: num texto de 15 parece
                            // igual ao do "Hello!".
                            val scale = style.fontSize.value / LetterRevealState.HelloFontSizeSp
                            val haze = maxOf(1f - state.letterFocus(index), leave)
                            val sigma = figmaBlurPx(FigmaBlur.HelloReveal) * scale * haze
                            if (sigma > 0.05f) BlurEffect(sigma, sigma, TileMode.Decal) else null
                        } else {
                            null
                        }
                        // O que falta do espaçamento: cada letra afasta-se do centro da sua linha.
                        translationX = if (animated) {
                            val gap = state.spacingEm * (1f - state.spacing) * fontPx
                            fractionX[index] + slot[index] * gap
                        } else {
                            fractionX[index]
                        }
                        // A letra pousa ao entrar e desce um pouco ao sair.
                        translationY = if (animated) {
                            LetterRevealState.RiseEm * fontPx * (state.letterRise(index) + leave)
                        } else {
                            0f
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

/** A parte da saída em que as letras vão começando (a primeira em 0, a última aqui). */
private const val ExitSpread = 0.45f

/** A saída da letra [order] (de [count]), quando a saída do texto vai em [exit]. */
private fun letterExit(exit: Float, order: Int, count: Int): Float {
    if (exit <= 0f) return 0f
    if (exit >= 1f) return 1f
    val start = if (count > 1) ExitSpread * order / (count - 1) else 0f
    return smoothStep(exit, start, start + (1f - ExitSpread))
}
