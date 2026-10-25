package com.gaebriu.cyclo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Fluxo dos ecrãs de introdução. Por agora: Introduction 0, 1 e 2.
 *
 * - O Introduction 1 sobe de baixo para cima e sobrepõe-se ao 0, com uma mola sem ressalto,
 *   enquanto o 0 recua (zoom out, desfoque, véu escuro). O X e o gesto/botão de voltar do
 *   Android fazem-no descer de volta.
 * - Do Introduction 2 em diante, os ecrãs deslizan na horizontal (o que sai desloca-se um pouco
 *   menos). A seta de voltar e o gesto de voltar do Android (com a pré-visualização do sistema,
 *   a seguir o dedo) fazem o caminho inverso.
 * - Sem animações no sistema, os movimentos passam a fades.
 *
 * @param onIntroduction2Next ação do Next do Introduction 2 (o Introduction 3 ainda não existe).
 */
@Composable
fun IntroductionFlow(
    modifier: Modifier = Modifier,
    onIntroduction2Next: () -> Unit = {},
) {
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableIntStateOf(0) }

    // Hora de acordar escolhida no Introduction 2 (06:00 é o que está no design).
    var wakeHour by rememberSaveable { mutableIntStateOf(6) }
    var wakeMinute by rememberSaveable { mutableIntStateOf(0) }
    var timeChanged by rememberSaveable { mutableStateOf(false) }

    // enter: o Introduction 1 sobe (0 a 1). position: página horizontal, de 1 (Introduction 1) em diante.
    val enter = remember { Animatable(if (step >= 1) 1f else 0f) }
    val position = remember { Animatable(maxOf(step, 1).toFloat()) }

    LaunchedEffect(step) {
        launch { enter.animateTo(if (step >= 1) 1f else 0f, CycloMotion.ScreenRiseSpring) }
        if (step >= 1) {
            launch { position.animateTo(step.toFloat(), CycloMotion.ScreenRiseSpring) }
        }
    }

    BackHandler(enabled = step == 1) { step = 0 }

    // Gesto de voltar do Android: o ecrã acompanha o dedo; ao confirmar volta, ao cancelar regressa.
    PredictiveBackHandler(enabled = step >= 2) { progress ->
        val from = step
        try {
            progress.collect { event -> position.snapTo(from - event.progress) }
            step = from - 1
        } catch (e: CancellationException) {
            scope.launch { position.animateTo(from.toFloat(), CycloMotion.ScreenRiseSpring) }
        }
    }

    // Cada ecrã só existe enquanto estiver (ou a entrar/sair) em cima do anterior.
    val showIntroduction1 by remember { derivedStateOf { step >= 1 || enter.value > 0f } }
    val showIntroduction2 by remember { derivedStateOf { step >= 2 || position.value > 1.001f } }

    Box(modifier = modifier.fillMaxSize().background(CycloColors.Background)) {
        // Introduction 0: enquanto o 1 sobe, recua com zoom out, desfoque e um véu escuro.
        // Ao voltar atrás, tudo se desfaz ao mesmo tempo que o 1 desce: seguem o mesmo progresso.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (effects.animationsEnabled) {
                        val e = enter.value
                        val s = 1f - (1f - CycloMotion.BackgroundScale) * e
                        scaleX = s
                        scaleY = s
                        renderEffect = if (effects.blurEnabled && e > 0f) {
                            val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * e
                            BlurEffect(sigma, sigma, TileMode.Clamp)
                        } else {
                            null
                        }
                    }
                }
                // Tapado pelo Introduction 1: fica fora do alcance do TalkBack.
                .then(if (showIntroduction1) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            Introduction0Screen(onGetStarted = { step = 1 })

            // Véu #010101 a 24 % de opacidade quando o ecrã está recuado.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = CycloMotion.BackgroundOverlayAlpha * enter.value }
                    .background(CycloColors.Background),
            )
        }

        if (showIntroduction1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val e = enter.value
                        if (effects.animationsEnabled) {
                            translationY = (1f - e) * size.height
                            // Quando o Introduction 2 entra, este desloca-se um pouco para a esquerda.
                            translationX = -(position.value - 1f).coerceAtLeast(0f) *
                                size.width * CycloMotion.PageParallax
                        } else {
                            alpha = e
                        }
                    }
                    .background(CycloColors.Background)
                    // Impede que os toques passem para os ecrãs que estão por baixo.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(if (showIntroduction2) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                Introduction1Screen(
                    enter = { enter.value },
                    onClose = { step = 0 },
                    onNext = { step = 2 },
                )
            }
        }

        if (showIntroduction2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 1 = fora, à direita; 0 = no sítio.
                        val offscreen = (2f - position.value).coerceIn(0f, 1f)
                        if (effects.animationsEnabled) {
                            translationX = offscreen * size.width
                        } else {
                            alpha = 1f - offscreen
                        }
                    }
                    .background(CycloColors.Background)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Introduction2Screen(
                    wakeHour = wakeHour,
                    wakeMinute = wakeMinute,
                    hintVisible = !timeChanged,
                    onHourChange = {
                        if (it != wakeHour) {
                            wakeHour = it
                            timeChanged = true
                        }
                    },
                    onMinuteChange = {
                        if (it != wakeMinute) {
                            wakeMinute = it
                            timeChanged = true
                        }
                    },
                    onBack = { step = 1 },
                    onNext = onIntroduction2Next,
                )
            }
        }
    }
}
