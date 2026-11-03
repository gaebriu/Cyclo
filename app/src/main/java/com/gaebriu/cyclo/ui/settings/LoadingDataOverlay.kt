package com.gaebriu.cyclo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.LetterRevealState
import com.gaebriu.cyclo.ui.components.LetterRevealText
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSampler
import com.gaebriu.cyclo.ui.effects.drawBackdrop
import com.gaebriu.cyclo.ui.effects.drawLayers
import com.gaebriu.cyclo.ui.effects.rememberBackdropSampler
import com.gaebriu.cyclo.ui.screens.BlurredRing
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** "fx circle-with-blur" do loading-data: #101011 a 63 % em Luminosity sobre o desfoque de fundo 39. */
private const val VeilAlpha = 0.63f
private val RingBox = 267.dp

/**
 * Posições do Figma (loading-data, 80:726, frame de 852): o anel (fx blur, 267 dp) tem o centro
 * 36,5 dp acima do centro do ecrã (389,5 contra 426), e o texto fica 4,5 dp acima do centro do anel
 * (como no Introduction 6: anel a 145 e texto a 265 da área das páginas).
 */
private val RingAboveCenter = 36.5.dp
private val TextAboveRingCenter = 4.5.dp

/** Duração das entradas e saídas do véu (o tempo de o Introduction 6 assentar no ecrã). */
private const val VeilFadeMs = 320

/** A pausa no fim, com o texto todo à vista, antes de o loading-data sair. */
private const val ReadHoldMs = 450L

/**
 * Quanto tempo o loading-data fica à vista, no mínimo, com o texto [text]: o véu a entrar, as
 * letras com o ritmo do "Hello!" (mais letras, mais tempo) e uma pausa curta para se ler. O
 * ViewModel espera isto antes de dar a mensagem de fim ([com.gaebriu.cyclo.data.CycloViewModel.confirmRestore]).
 */
fun loadingDataMinMs(text: String): Long = VeilFadeMs + LetterRevealState(text).durationMs + ReadHoldMs

/**
 * loading-data (Componentes, 80:726): enquanto os dados são repostos, tapa o ecrã com um véu
 * desfocado, o anel desfocado do Introduction 6 e "Bringing your data back…".
 *
 * A animação é a do "Hello!" do Introduction 0, como no Introduction 6: primeiro o véu entra
 * (como a página a assentar); depois as letras aparecem uma a uma com o ritmo e o desfoque do
 * "Hello!" ([LetterRevealText]) e o anel aparece como uma letra ([BlurredRing]). Só sai depois de a
 * animação acabar (mesmo que os dados fiquem prontos antes): o texto, o anel e o véu desaparecem
 * juntos, devagar.
 *
 * O fundo desfocado é tirado UMA vez, ao abrir (uma imagem do ecrã por baixo, desfocada uma vez):
 * repor os dados muda o ecrã por baixo, e antes isso refazia o desfoque do ecrã inteiro a cada
 * frame, a meio da animação. Bloqueia os toques e o voltar enquanto está à vista.
 */
@Composable
fun LoadingDataOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    val fade = remember { Animatable(0f) }
    // Cada vez que abre, a animação recomeça do zero.
    var round by remember { mutableIntStateOf(0) }
    var revealDone by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (visible) {
            round++
            revealDone = false
            fade.animateTo(1f, tween(VeilFadeMs, easing = CycloMotion.Decelerate))
        } else {
            // Os dados já estão: espera o fim da animação do Introduction 6, e só depois sai.
            snapshotFlow { revealDone }.first { it }
            fade.animateTo(0f, tween(VeilFadeMs + 80, easing = CycloMotion.Decelerate))
        }
    }
    val showing = visible || fade.value > 0f
    if (!showing) return

    val text = stringResource(R.string.restore_loading)
    val reveal = remember(round, text) { LetterRevealState(text) }
    val ring = remember(round) { Animatable(0f) }
    LaunchedEffect(round) {
        if (round == 0) return@LaunchedEffect
        // Como o Introduction 6 quando a página assenta: espera o véu, depois letras e anel juntos.
        snapshotFlow { fade.value }.first { it >= 0.98f }
        coroutineScope {
            launch { reveal.play() }
            launch {
                if (effects.animationsEnabled) {
                    delay(CycloMotion.LettersStartDelayMs.toLong())
                    ring.animateTo(1f, tween(LetterRevealState.LetterDurationMs, easing = CycloMotion.Decelerate))
                } else {
                    ring.animateTo(1f, tween(CycloMotion.HintFadeDurationMs))
                }
            }
        }
        revealDone = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value.coerceIn(0f, 1f) }
            // Não deixa os toques passarem para baixo.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        FrozenVeil(round = round)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = -RingAboveCenter),
            contentAlignment = Alignment.Center,
        ) {
            // Cada um na sua camada: a frente do anel e o espaçamento das letras só redesenham o próprio.
            BlurredRing(
                reveal = { ring.value },
                sharpOnly = !effects.animationsEnabled,
                modifier = Modifier.size(RingBox).graphicsLayer(),
            )
            LetterRevealText(
                state = reveal,
                style = CycloType.Body,
                modifier = Modifier
                    .offset(y = -TextAboveRingCenter)
                    .graphicsLayer()
                    .padding(horizontal = 24.dp),
            )
        }
    }
    BackHandler(enabled = showing) { }
}

/**
 * O véu: o ecrã por baixo desfocado (desfoque de fundo 39 do Figma) e #101011 a 63 % em
 * Luminosity. No primeiro frame desenha o fundo ao vivo e tira-lhe uma imagem; daí em diante usa a
 * imagem, desfocada uma só vez, e nada do que muda por baixo o volta a desenhar.
 */
@Composable
private fun FrozenVeil(round: Int) {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    val capture = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var frozen by remember(round) { mutableStateOf<ImageBitmap?>(null) }
    val capturing = remember(round) { booleanArrayOf(false) }
    val blurredFor = remember { arrayOf<ImageBitmap?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer()
            .backdropSampler(sampler)
            .drawBehind {
                val shape = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
                val image = frozen
                if (effects.blurEnabled) {
                    if (image != null) {
                        // A imagem desfoca-se uma vez; depois é só desenhar a camada (em cache).
                        if (blurredFor[0] !== image) {
                            val sigma = figmaBlurPx(FigmaBlur.Overlay)
                            blurred.renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp)
                            blurred.record(IntSize(size.width.toInt(), size.height.toInt())) { drawImage(image) }
                            blurredFor[0] = image
                        }
                        drawLayer(blurred)
                    } else {
                        drawBackdrop(sampler, backdrop, shape, FigmaBlur.Overlay)
                        val self = sampler.coordinates
                        if (!capturing[0] && self != null && self.isAttached) {
                            capturing[0] = true
                            capture.record(IntSize(size.width.toInt(), size.height.toInt())) { drawLayers(backdrop, self) }
                            scope.launch { frozen = runCatching { capture.toImageBitmap() }.getOrNull() }
                        }
                    }
                }
                drawRect(
                    color = CycloColors.GlassFill.copy(alpha = if (effects.blurEnabled) VeilAlpha else 0.9f),
                    topLeft = Offset.Zero,
                    blendMode = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver,
                )
            },
    )
}
