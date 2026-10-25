package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloGlassButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.GlassGlow
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.VisualEffects
import com.gaebriu.cyclo.ui.effects.drawGaussianGlow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Medidas do frame do Figma (393 x 852).
private val GreetingTop = 229.dp
private val TopShadeHeight = 130.dp

// Brilho do topo: elipse de 432 x 267 dp, gradiente vertical (roxo a 39 % em cima, 100 % em baixo),
// desfoque 267 do Figma e 63 % de opacidade do grupo.
private const val GlowGroupAlpha = 0.63f

/** Média da opacidade do gradiente do preenchimento da elipse (de 39 % a 100 %). */
private const val GlowMeanFill = 0.695f

/** O gradiente do preenchimento desloca o centro de massa da elipse para baixo (fração do semieixo vertical). */
private const val GlowCentroidShift = 0.1097f

// Fade do brilho quando o ecrã recua: a frente do fade desce do topo e o brilho some de cima para baixo.
private val GlowFadeReach = 480.dp
private val GlowFadeFeather = 180.dp

private typealias Progress = Animatable<Float, AnimationVector1D>

/**
 * Introduction 0.
 *
 * @param onGetStarted ação do botão Get started (a navegação liga-se por fora).
 * O botão "What's new?" é só visual, por decisão tua, e não tem ação.
 * @param recede de 0 a 1: quanto este ecrã já recuou por baixo do Introduction 1. O brilho roxo
 *               desaparece com um fade de cima para baixo (e reaparece de baixo para cima ao
 *               voltar), para não ficar cortado no topo quando o ecrã encolhe.
 */
@Composable
fun Introduction0Screen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    recede: () -> Float = { 0f },
) {
    val effects = LocalVisualEffects.current
    val greeting = stringResource(R.string.introduction_0_greeting)

    val glow = remember { Animatable(0f) }
    val letters = remember(greeting) { List(greeting.length) { Animatable(0f) } }
    val letterSpacing = remember { Animatable(0f) }
    val buttons = remember { Animatable(0f) }

    LaunchedEffect(greeting) {
        // O "Hello" dura HelloDurationMs: cada letra começa com um pequeno intervalo e
        // a última acaba exatamente ao fim desse tempo.
        val letterDuration = (CycloMotion.HelloDurationMs - CycloMotion.LetterStaggerMs * (letters.size - 1))
            .coerceAtLeast(CycloMotion.LetterStaggerMs)
        coroutineScope {
            launch {
                glow.animateTo(1f, tween(CycloMotion.GlowDurationMs, easing = CycloMotion.Decelerate))
            }
            launch {
                delay(CycloMotion.LettersStartDelayMs.toLong())
                letterSpacing.animateTo(1f, tween(CycloMotion.HelloDurationMs, easing = CycloMotion.Decelerate))
            }
            letters.forEachIndexed { index, letter ->
                launch {
                    delay((CycloMotion.LettersStartDelayMs + index * CycloMotion.LetterStaggerMs).toLong())
                    letter.animateTo(1f, tween(letterDuration, easing = CycloMotion.Decelerate))
                }
            }
            launch {
                delay(CycloMotion.ButtonsDelayMs.toLong())
                buttons.animateTo(1f, tween(CycloMotion.ButtonsDurationMs, easing = CycloMotion.Decelerate))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CycloColors.Background)) {
        TopGlow(glow = glow, recede = recede, effects = effects)

        // "fx top dark gradient": preto a 15 % em cima, transparente em baixo.
        Box(
            modifier = Modifier
                .offset(y = (-1).dp)
                .fillMaxWidth()
                .height(TopShadeHeight)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent)),
                ),
        )

        AnimatedGreeting(
            greeting = greeting,
            letters = letters,
            letterSpacingEm = {
                // Sem animações no sistema, as letras aparecem só com fade, sem espaçamento.
                if (effects.animationsEnabled) {
                    CycloMotion.HelloLetterSpacingEm * (1f - letterSpacing.value)
                } else {
                    0f
                }
            },
            effects = effects,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = GreetingTop),
        )

        IntroButtons(
            buttons = buttons,
            effects = effects,
            onGetStarted = onGetStarted,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Brilho do topo. Nasce do topo e expande-se até à forma do design.
 *
 * É desenhado só com gradientes (ver [drawGaussianGlow]), SEM RenderEffect, por isso aparece
 * igual em qualquer aparelho. Tem camada própria para a máscara do fade só afetar o brilho.
 */
@Composable
private fun TopGlow(glow: Progress, recede: () -> Float, effects: VisualEffects) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                // Sem animações: aparece com um fade em vez de crescer.
                val growth = if (effects.animationsEnabled) glow.value else 1f
                val intensity = if (effects.animationsEnabled) 1f else glow.value
                drawTopGlow(growth, intensity)
                drawRecedeFade(recede())
            },
    )
}

/**
 * O desfoque gaussiano de uma elipse com semieixos a e b (432 x 267 dp a escala 1) é, com muito
 * boa aproximação, uma gaussiana com sigma_x² = sigma² + a²/4 e sigma_y² = sigma² + b²/4,
 * de pico 0,63 x média do preenchimento x a·b / (2·sigma_x·sigma_y).
 */
private fun DrawScope.drawTopGlow(growth: Float, intensity: Float) {
    if (growth <= 0f || intensity <= 0f) return
    val sigma = figmaBlurPx(FigmaBlur.Glow)
    val a = 216.dp.toPx() * growth
    val b = 133.5.dp.toPx() * growth
    val sigmaX = sqrt(sigma * sigma + a * a / 4f)
    val sigmaY = sqrt(sigma * sigma + b * b / 4f)
    drawGaussianGlow(
        color = CycloColors.Accent,
        // A elipse está centrada no topo do ecrã (a 0,5 dp acima dele).
        center = Offset(size.width / 2f, -0.5.dp.toPx() + GlowCentroidShift * b),
        sigmaX = sigmaX,
        sigmaY = sigmaY,
        peakAlpha = GlowGroupAlpha * GlowMeanFill * a * b / (2f * sigmaX * sigmaY) * intensity,
    )
}

/**
 * Máscara do fade: acima da frente o brilho some, abaixo dela mantém-se. A frente desce com
 * [recede] (fade out de cima para baixo) e sobe quando ele volta a 0 (fade in de baixo para cima).
 */
private fun DrawScope.drawRecedeFade(recede: Float) {
    if (recede <= 0f) return
    val reach = GlowFadeReach.toPx()
    val feather = GlowFadeFeather.toPx()
    val front = recede * (reach + feather)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0f),
            1f to Color.Black,
            startY = front - feather,
            endY = front,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * "Hello!" letra a letra: cada uma aparece com fade e desfoque 9 a 0 (do Figma),
 * enquanto o espaçamento entre letras fecha de 15 % até zero.
 */
@Composable
private fun AnimatedGreeting(
    greeting: String,
    letters: List<Progress>,
    letterSpacingEm: () -> Float,
    effects: VisualEffects,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shadow = remember(density) {
        Shadow(
            color = Color.Black.copy(alpha = 0.24f),
            offset = Offset.Zero,
            blurRadius = with(density) { 15.dp.toPx() },
        )
    }
    val style = remember(shadow) { CycloType.HeaderBig.copy(shadow = shadow) }

    // Para o TalkBack ler a palavra inteira, e não letra a letra.
    SpacedLetters(
        letterSpacingEm = letterSpacingEm,
        modifier = modifier.clearAndSetSemantics { contentDescription = greeting },
    ) {
        greeting.forEachIndexed { index, letter ->
            BasicText(
                text = letter.toString(),
                style = style,
                modifier = Modifier.graphicsLayer {
                    val p = letters[index].value
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

/**
 * Põe as letras lado a lado com um espaçamento (em múltiplos do tamanho da letra) que se
 * anima sem recompor: o valor só é lido ao medir, por isso cada frame é só uma nova medição.
 */
@Composable
private fun SpacedLetters(
    letterSpacingEm: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val gap = (letterSpacingEm() * CycloType.HeaderBig.fontSize.toPx()).roundToInt()
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

@Composable
private fun IntroButtons(
    buttons: Progress,
    effects: VisualEffects,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // O frame do Figma tem 48 dp de barra de navegação; reservamos pelo menos isso.
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = maxOf(CycloDimens.MinBottomInset, navigationInset)

    // "purple fx" do news-b: faixa roxa a 24 % colada à base, com 24 dp de margem de cada lado.
    val purpleFx = remember {
        GlassGlow(
            color = CycloColors.Accent.copy(alpha = 0.24f),
            insetHorizontal = 24.dp,
            height = 19.dp,
            blurFigma = FigmaBlur.NewsButton,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CycloDimens.ScreenMargin)
            .padding(bottom = bottom)
            .graphicsLayer {
                val p = buttons.value
                alpha = p
                // Sem animações: só fade, sem subir.
                translationY = if (effects.animationsEnabled) (1f - p) * CycloMotion.ButtonsRise.toPx() else 0f
            },
        verticalArrangement = Arrangement.spacedBy(CycloDimens.Spacing),
    ) {
        CycloGlassButton(
            text = stringResource(R.string.introduction_0_whats_new),
            glow = purpleFx,
        )
        CycloPrimaryButton(
            text = stringResource(R.string.introduction_0_get_started),
            onClick = onGetStarted,
        )
    }
}
