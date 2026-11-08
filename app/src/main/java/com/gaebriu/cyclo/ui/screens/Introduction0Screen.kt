package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.LetterRevealState
import com.gaebriu.cyclo.ui.components.LetterRevealText
import com.gaebriu.cyclo.ui.components.rememberLetterReveal
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
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.drawFigmaNoise
import com.gaebriu.cyclo.ui.effects.rememberFigmaNoise
import com.gaebriu.cyclo.ui.theme.CycloSpring
import com.gaebriu.cyclo.ui.theme.smoothStep
import com.gaebriu.cyclo.ui.theme.springProgress
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

// Medidas do frame do Figma (393 x 852).
private val GreetingTop = 229.dp
private val TopShadeHeight = 130.dp

// "fx gradient with blur" (Cyclo-2 23:8, Beta 2.0), grupo a 63 %:
// - a luz: elipse de 432 x 267 dp com o topo no topo do ecrã (centro a 133,5 dp), gradiente vertical
//   #4A3C99 de 39 % (em cima) a 100 % (em baixo), desfoque 267;
// - por cima, a sombra: elipse de 267 x 165 dp, também com o topo no topo do ecrã (centro a 82,5 dp),
//   preto de 39 % a 100 %, desfoque 165;
// - cada uma com o Noise do Figma (MONOTONE 2, #FCFCFE a 39 %), recortado por ela.
private const val GlowGroupAlpha = 0.63f
private val LightRadiusX = 216.dp
private val LightRadiusY = 133.5.dp
private val ShadeRadiusX = 133.5.dp
private val ShadeRadiusY = 82.5.dp
private const val ShadeFigmaBlur = 165f
private const val FillTopAlpha = 0.39f

// Fade do brilho quando o ecrã recua: a frente do fade desce do topo e o brilho some de cima para baixo.
private val GlowFadeReach = 480.dp
private val GlowFadeFeather = 180.dp

private typealias Progress = Animatable<Float, AnimationVector1D>

/**
 * Introduction 0.
 *
 * @param onGetStarted ação do botão Get started (a navegação liga-se por fora).
 * O botão "What's new?" é só visual, por decisão tua, e não tem ação.
 * @param showButtons false na abertura depois de uma atualização: só o "Hello!" e o brilho, sem o
 *                    "Get started" (a app passa sozinha para o Feed).
 * @param recede de 0 a 1: quanto este ecrã já recuou por baixo do Introduction 1. O brilho roxo
 *               desaparece com um fade de cima para baixo (e reaparece de baixo para cima ao
 *               voltar), para não ficar cortado no topo quando o ecrã encolhe.
 */
@Composable
fun Introduction0Screen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    recede: () -> Float = { 0f },
    showButtons: Boolean = true,
) {
    val effects = LocalVisualEffects.current
    val greeting = stringResource(R.string.introduction_0_greeting)

    val glow = remember { Animatable(0f) }
    // O relógio da cena (s desde que o ecrã abre): o brilho E as letras do "Hello!" leem-no ao
    // desenhar, por isso são uma só composição, ao mesmo fotograma (Beta 2.2: antes eram duas
    // animações lançadas juntas). Sem animações, o "Hello!" usa o seu próprio relógio (só fade).
    val sceneClock = remember { mutableFloatStateOf(0f) }
    val reveal = rememberLetterReveal(
        greeting,
        clockMs = if (effects.animationsEnabled) ({ sceneClock.floatValue * 1000f }) else null,
    )
    val buttons = remember { Animatable(0f) }

    LaunchedEffect(reveal) {
        coroutineScope {
            launch {
                // Sem animações, a luz só aparece (fade); com elas, o relógio da cena (TopGlow) faz o resto.
                glow.animateTo(1f, tween(CycloMotion.GlowDurationMs, easing = CycloMotion.Decelerate))
            }
            // O "Hello" dura HelloDurationMs: cada letra começa com um pequeno intervalo e
            // a última acaba exatamente ao fim desse tempo.
            launch { reveal.play() }
            launch {
                delay(CycloMotion.ButtonsDelayMs.toLong())
                buttons.animateTo(1f, tween(CycloMotion.ButtonsDurationMs, easing = CycloMotion.Decelerate))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CycloColors.Background)) {
        TopGlow(glow = glow, clock = sceneClock, recede = recede, effects = effects)

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
            reveal = reveal,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = GreetingTop),
        )

        if (showButtons) {
            IntroButtons(
                buttons = buttons,
                effects = effects,
                onGetStarted = onGetStarted,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * O brilho do topo (Cyclo-2 23:8): a luz roxa e a sombra por cima dela, com o grão do Figma.
 *
 * A entrada (uma só vez, ao abrir): a luz nasce de baixo e abre (escala, subida e intensidade numa
 * mola lenta, [LightRise], com um ressalto quase invisível); a sombra assenta de cima um pouco
 * depois ([ShadeSettle]) e dá à luz a forma de horizonte do Figma. Depois, a cena respira: a luz e a
 * sombra mexem-se muito devagar (escala, intensidade e um pequeno desvio lateral), desfasadas, sem
 * nunca sair do desenho do Figma mais do que ~2 %. O grão fica parado (é a superfície). O relógio
 * pára com o ecrã tapado pelo Introduction 1.
 *
 * Desenhado só com gradientes (ver [drawGaussianGlow]), SEM RenderEffect: igual em qualquer
 * aparelho. Sem animações: a cena final aparece com um fade.
 */
@Composable
private fun TopGlow(glow: Progress, clock: MutableFloatState, recede: () -> Float, effects: VisualEffects) {
    val noise = rememberFigmaNoise()
    // O relógio da cena (s): a entrada e a respiração leem-no ao desenhar (nada se recompõe), e as
    // letras do "Hello!" também (ver Introduction0Screen).
    LaunchedEffect(effects.animationsEnabled) {
        if (!effects.animationsEnabled) return@LaunchedEffect
        var start = -1L
        while (true) {
            // Tapado pelo Introduction 1: não há nada a mexer (espera que volte a ver-se).
            if (recede() >= 0.999f) {
                snapshotFlow { recede() < 0.999f }.first { it }
                start = -1L
            }
            withFrameNanos { now ->
                if (start < 0) start = now - (clock.floatValue * 1e9f).toLong()
                clock.floatValue = (now - start) / 1e9f
            }
        }
    }
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            // O grupo a 63 % (o Figma junta a luz e a sombra antes da opacidade).
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = GlowGroupAlpha
            }
            .drawBehind {
                val scene = if (effects.animationsEnabled) glowScene(clock.floatValue) else GlowScene.Final
                val fade = if (effects.animationsEnabled) 1f else glow.value
                val tile = if (effects.textures) noise.value else null
                drawLight(scene, fade, tile)
                drawShade(scene, fade, tile)
                drawRecedeFade(recede())
            },
    )
}

/** A cena num instante: escala, deslocamento (px a multiplicar por dp) e intensidade das duas elipses. */
private class GlowScene(
    val lightScale: Float,
    val lightDx: Float,
    val lightDy: Float,
    val lightIntensity: Float,
    val shadeScale: Float,
    val shadeDx: Float,
    val shadeDy: Float,
    val shadeIntensity: Float,
) {
    companion object {
        val Final = GlowScene(1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f)
    }
}

/** A luz a nascer: lenta, com um ressalto quase invisível. */
private val LightRise = CycloSpring(response = 1.5f, dampingFraction = 0.9f)

/** A sombra a assentar: um pouco mais lenta e sem ressalto. */
private val ShadeSettle = CycloSpring(response = 1.7f, dampingFraction = 1f)

/** Quando a sombra começa (s depois da luz). */
private const val ShadeDelayS = 0.22f

/**
 * A respiração entra devagar entre estes dois instantes (s): começa quando a última letra do
 * "Hello!" acaba (a palavra completa é o sinal de que a cena está viva) e fica inteira 2 s depois.
 */
private const val BreathFromS = (CycloMotion.LettersStartDelayMs + CycloMotion.HelloDurationMs) / 1000f
private const val BreathFullS = BreathFromS + 2f

private fun glowScene(t: Float): GlowScene {
    val light = springProgress(t, LightRise)
    val shade = if (t > ShadeDelayS) springProgress(t - ShadeDelayS, ShadeSettle) else 0f
    val breath = smoothStep(t, BreathFromS, BreathFullS)
    val tau = 2f * PI.toFloat()
    return GlowScene(
        lightScale = lerp(0.6f, 1f, light) * (1f + 0.022f * breath * sin(tau * t / 7.6f)),
        lightDx = 5f * breath * sin(tau * t / 11.3f),
        lightDy = lerp(64f, 0f, light),
        lightIntensity = light.coerceIn(0f, 1.05f) * (1f + 0.05f * breath * sin(tau * t / 7.6f + 0.7f)),
        shadeScale = lerp(1.22f, 1f, shade) * (1f + 0.018f * breath * sin(tau * t / 8.9f + 2.1f)),
        shadeDx = -7f * breath * sin(tau * t / 9.7f + 1.3f),
        shadeDy = lerp(-28f, 0f, shade),
        shadeIntensity = shade,
    )
}

/**
 * Uma elipse desfocada com gradiente vertical (de [topAlpha] em cima a 100 % em baixo) e o grão do
 * Figma, numa camada própria (o grão é recortado só por ela). O desfoque gaussiano de uma elipse de
 * semieixos a e b é, com muito boa aproximação, uma gaussiana com sigma_x² = sigma² + a²/4 e
 * sigma_y² = sigma² + b²/4, de pico a·b / (2·sigma_x·sigma_y); o gradiente pinta-a por cima (SrcIn).
 */
private fun DrawScope.drawBlurredEllipse(
    color: Color,
    center: Offset,
    a: Float,
    b: Float,
    sigma: Float,
    intensity: Float,
    tile: androidx.compose.ui.graphics.ImageBitmap?,
) {
    if (intensity <= 0f || a <= 0f || b <= 0f) return
    val sigmaX = sqrt(sigma * sigma + a * a / 4f)
    val sigmaY = sqrt(sigma * sigma + b * b / 4f)
    val peak = (a * b / (2f * sigmaX * sigmaY) * intensity).coerceIn(0f, 1f)
    val fill = Brush.verticalGradient(
        0f to color.copy(alpha = FillTopAlpha),
        1f to color,
        startY = center.y - b,
        endY = center.y + b,
    )
    val canvas = drawContext.canvas
    canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), androidx.compose.ui.graphics.Paint())
    drawGaussianGlow(Color.White, center, sigmaX, sigmaY, peak)
    drawRect(brush = fill, blendMode = BlendMode.SrcIn)
    drawFigmaNoise(tile) {
        drawGaussianGlow(Color.White, center, sigmaX, sigmaY, peak)
        drawRect(brush = fill, blendMode = BlendMode.DstIn)
    }
    canvas.restore()
}

/** A luz roxa (432 x 267, desfoque 267). */
private fun DrawScope.drawLight(scene: GlowScene, fade: Float, tile: androidx.compose.ui.graphics.ImageBitmap?) {
    val dp = 1.dp.toPx()
    val a = LightRadiusX.toPx() * scene.lightScale
    val b = LightRadiusY.toPx() * scene.lightScale
    drawBlurredEllipse(
        color = CycloColors.Accent,
        center = Offset(size.width / 2f + scene.lightDx * dp, LightRadiusY.toPx() + scene.lightDy * dp),
        a = a,
        b = b,
        sigma = figmaBlurPx(FigmaBlur.Glow),
        intensity = scene.lightIntensity * fade,
        tile = tile,
    )
}

/** A sombra preta por cima da luz (267 x 165, desfoque 165). */
private fun DrawScope.drawShade(scene: GlowScene, fade: Float, tile: androidx.compose.ui.graphics.ImageBitmap?) {
    val dp = 1.dp.toPx()
    val a = ShadeRadiusX.toPx() * scene.shadeScale
    val b = ShadeRadiusY.toPx() * scene.shadeScale
    drawBlurredEllipse(
        color = Color.Black,
        center = Offset(size.width / 2f + scene.shadeDx * dp, ShadeRadiusY.toPx() + scene.shadeDy * dp),
        a = a,
        b = b,
        sigma = figmaBlurPx(ShadeFigmaBlur),
        intensity = scene.shadeIntensity * fade,
        tile = tile,
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
 * "Hello!" letra a letra (ver [LetterRevealText]), com a sombra do texto do Figma
 * (preto a 24 %, desfoque 15).
 */
@Composable
private fun AnimatedGreeting(
    reveal: LetterRevealState,
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
    LetterRevealText(state = reveal, style = style, modifier = modifier)
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
    ) {
        CycloPrimaryButton(
            text = stringResource(R.string.introduction_0_get_started),
            onClick = onGetStarted,
        )
    }
}
