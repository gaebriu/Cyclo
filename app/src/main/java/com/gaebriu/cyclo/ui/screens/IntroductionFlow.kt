package com.gaebriu.cyclo.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
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

/**
 * Fluxo dos ecrãs de introdução. Por agora: Introduction 0 e 1.
 *
 * O Introduction 1 sobe de baixo para cima e sobrepõe-se ao 0, com uma mola sem ressalto,
 * enquanto o 0 recua (zoom out, desfoque, véu escuro).
 * O X e o gesto/botão de voltar do Android fazem-no descer de volta.
 * Sem animações no sistema, a subida passa a um fade com a mesma duração.
 *
 * @param onIntroduction1Next ação do Next do Introduction 1 (o Introduction 2 ainda não existe).
 */
@Composable
fun IntroductionFlow(
    modifier: Modifier = Modifier,
    onIntroduction1Next: () -> Unit = {},
) {
    val effects = LocalVisualEffects.current

    var step by rememberSaveable { mutableIntStateOf(0) }
    val enter = remember { Animatable(if (step >= 1) 1f else 0f) }

    LaunchedEffect(step) {
        enter.animateTo(if (step >= 1) 1f else 0f, CycloMotion.ScreenRiseSpring)
    }

    BackHandler(enabled = step >= 1) { step = 0 }

    // O Introduction 1 só existe enquanto estiver (ou a entrar/sair) em cima do 0.
    val showIntroduction1 by remember { derivedStateOf { step >= 1 || enter.value > 0f } }

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
                        } else {
                            alpha = e
                        }
                    }
                    .background(CycloColors.Background)
                    // Impede que os toques passem para os botões do Introduction 0 que estão por baixo.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Introduction1Screen(
                    enter = { enter.value },
                    onClose = { step = 0 },
                    onNext = onIntroduction1Next,
                )
            }
        }
    }
}
