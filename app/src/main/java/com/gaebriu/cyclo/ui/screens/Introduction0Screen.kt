package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloGlassButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.VisualEffects
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Medidas do frame do Figma (393 x 852).
private val GlowOffset = DpOffset((-19).dp, (-134).dp)
private val GlowSize = DpSize(432.dp, 267.dp)
private const val GlowGroupAlpha = 0.63f
private val GreetingTop = 229.dp
private val TopShadeHeight = 130.dp
private val PurpleFxHeight = 19.dp
private val PurpleFxInset = 24.dp

private typealias Progress = Animatable<Float, AnimationVector1D>

/**
 * Introduction 0.
 *
 * @param onGetStarted ação do botão Get started (a navegação liga-se por fora).
 * O botão "What's new?" é só visual, por decisão tua, e não tem ação.
 */
@Composable
fun Introduction0Screen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val greeting = stringResource(R.string.introduction_0_greeting)

    val glow = remember { Animatable(0f) }
    val letters = remember(greeting) { List(greeting.length) { Animatable(0f) } }
    val buttons = remember { Animatable(0f) }

    LaunchedEffect(greeting) {
        coroutineScope {
            launch {
                glow.animateTo(1f, tween(CycloMotion.GlowDurationMs, easing = CycloMotion.Decelerate))
            }
            letters.forEachIndexed { index, letter ->
                launch {
                    delay((CycloMotion.LettersStartDelayMs + index * CycloMotion.LetterStaggerMs).toLong())
                    letter.animateTo(1f, tween(CycloMotion.LetterDurationMs, easing = CycloMotion.Decelerate))
                }
            }
            launch {
                delay(CycloMotion.ButtonsDelayMs.toLong())
                buttons.animateTo(1f, tween(CycloMotion.ButtonsDurationMs, easing = CycloMotion.Decelerate))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CycloColors.Background)) {
        TopGlow(glow = glow, effects = effects)

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
 * Brilho do topo: elipse com gradiente vertical (roxo a 39 % em cima, 100 % em baixo),
 * desfocada, a 63 % de opacidade. Nasce do topo e expande-se até à forma do design.
 */
@Composable
private fun TopGlow(glow: Progress, effects: VisualEffects) {
    Spacer(
        modifier = Modifier
            // Coloca a elipse nas coordenadas exatas do Figma, mesmo saindo do ecrã.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints.fixed(GlowSize.width.roundToPx(), GlowSize.height.roundToPx()),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(GlowOffset.x.roundToPx(), GlowOffset.y.roundToPx())
                }
            }
            .graphicsLayer {
                // Sem animações: aparece com um fade em vez de crescer.
                alpha = if (effects.animationsEnabled) GlowGroupAlpha else GlowGroupAlpha * glow.value
                renderEffect = if (effects.blurEnabled) {
                    val sigma = figmaBlurPx(FigmaBlur.Glow)
                    BlurEffect(sigma, sigma, TileMode.Decal)
                } else {
                    null
                }
                clip = false
            }
            .drawBehind {
                val p = if (effects.animationsEnabled) glow.value else 1f
                if (p <= 0f) return@drawBehind
                val w = size.width * p
                val h = size.height * p
                val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(CycloColors.Accent.copy(alpha = 0.39f), CycloColors.Accent),
                        startY = topLeft.y,
                        endY = topLeft.y + h,
                    ),
                    topLeft = topLeft,
                    size = Size(w, h),
                )
            },
    )
}

/** "Hello!" letra a letra: cada uma aparece com fade e desfoque 6 a 0. */
@Composable
private fun AnimatedGreeting(
    greeting: String,
    letters: List<Progress>,
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
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = greeting }) {
        greeting.forEachIndexed { index, letter ->
            BasicText(
                text = letter.toString(),
                style = style,
                modifier = Modifier.graphicsLayer {
                    val p = letters[index].value
                    alpha = p
                    renderEffect = if (effects.blurEnabled && effects.animationsEnabled && p < 1f) {
                        val sigma = figmaBlurPx(FigmaBlur.Letter) * (1f - p)
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
            backdropBlurFigma = FigmaBlur.NewsButton,
            underlay = { drawPurpleFx() },
        )
        CycloPrimaryButton(
            text = stringResource(R.string.introduction_0_get_started),
            onClick = onGetStarted,
        )
    }
}

/** "purple fx" do news-b: faixa roxa a 24 % colada à base, com 24 dp de margem de cada lado. */
private fun DrawScope.drawPurpleFx() {
    val inset = PurpleFxInset.toPx()
    val height = PurpleFxHeight.toPx()
    drawRect(
        color = CycloColors.Accent.copy(alpha = 0.24f),
        topLeft = Offset(inset, size.height - height),
        size = Size(size.width - inset * 2f, height),
    )
}
