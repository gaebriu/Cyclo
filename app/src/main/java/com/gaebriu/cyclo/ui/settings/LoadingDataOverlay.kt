package com.gaebriu.cyclo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.LetterRevealState
import com.gaebriu.cyclo.ui.components.LetterRevealText
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.drawBackdrop
import com.gaebriu.cyclo.ui.effects.rememberBackdropSampler
import com.gaebriu.cyclo.ui.screens.BlurredRing
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "fx circle-with-blur" do loading-data: #101011 a 63 % em Luminosity sobre o desfoque de fundo 39. */
private const val VeilAlpha = 0.63f
private val RingBox = 267.dp

/**
 * loading-data (Componentes 3): enquanto os dados são repostos, tapa o ecrã com um véu desfocado,
 * o anel desfocado do Introduction 6 ao centro e "Bringing your data back..." por dentro.
 *
 * A animação é a do Introduction 6: o texto aparece letra a letra (como o "Hello!") e o desfoque do
 * anel revela-se da esquerda para a direita, ao mesmo tempo. O véu entra e sai com fade.
 * Bloqueia os toques e o voltar enquanto está à vista.
 */
@Composable
fun LoadingDataOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    val fade = remember { Animatable(0f) }
    // Cada vez que abre, a animação recomeça do zero.
    var round by remember { mutableStateOf(0) }
    LaunchedEffect(visible) {
        if (visible) round++
        fade.animateTo(if (visible) 1f else 0f, CycloMotion.FadeSpring)
    }
    if (!visible && fade.value <= 0f) return

    val text = stringResource(R.string.restore_loading)
    val reveal = remember(round, text) { LetterRevealState(text) }
    val ring = remember(round) { Animatable(0f) }
    LaunchedEffect(round) {
        if (round == 0) return@LaunchedEffect
        coroutineScope {
            launch { reveal.play() }
            launch {
                if (effects.animationsEnabled) {
                    delay(CycloMotion.LettersStartDelayMs.toLong())
                    ring.animateTo(1f, tween(CycloMotion.HelloDurationMs, easing = CycloMotion.Decelerate))
                } else {
                    ring.snapTo(1f)
                }
            }
        }
    }

    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value.coerceIn(0f, 1f) }
            // Não deixa os toques passarem para baixo.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        // O véu (desfoque de fundo do ecrã inteiro) vive numa camada sua, sem filhos: o anel e as
        // letras animam noutra, por isso não voltam a pedir o desfoque (nem a medir a posição do
        // véu) a cada frame. Antes, cada frame da animação refazia o desfoque do ecrã inteiro.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .backdropSampler(sampler)
                .drawBehind {
                    val shape = Path().apply { addRect(size.toRect()) }
                    if (effects.blurEnabled) drawBackdrop(sampler, backdrop, shape, FigmaBlur.Overlay)
                    drawRect(
                        color = CycloColors.GlassFill.copy(alpha = if (effects.blurEnabled) VeilAlpha else 0.9f),
                        blendMode = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver,
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(),
            contentAlignment = Alignment.Center,
        ) {
            // Cada um na sua camada: a frente do anel e o espaçamento das letras só redesenham o próprio.
            BlurredRing(
                reveal = { if (effects.animationsEnabled) ring.value else 1f },
                modifier = Modifier.size(RingBox).graphicsLayer(),
            )
            LetterRevealText(
                state = reveal,
                style = CycloType.Body,
                modifier = Modifier.graphicsLayer().padding(horizontal = 24.dp),
            )
        }
    }
    BackHandler(enabled = visible) { }
}

private fun androidx.compose.ui.geometry.Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)
