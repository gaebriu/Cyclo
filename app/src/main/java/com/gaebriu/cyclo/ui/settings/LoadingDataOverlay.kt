package com.gaebriu.cyclo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
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
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.VisualEffects
import com.gaebriu.cyclo.ui.screens.AllSetTextSpacingEm
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import com.gaebriu.cyclo.ui.theme.smoothStep
import kotlinx.coroutines.Job
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import com.gaebriu.cyclo.ui.theme.CycloMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** "fx blur" do loading-data (80:717): #101011 a 63 % em Luminosity sobre o desfoque de fundo 39. */
private const val VeilAlpha = 0.63f

/**
 * Posição do Figma (Cyclo-2 loading-data, 80:726, frame de 852): o texto (18 dp de altura) começa a
 * 376 dp, por isso o centro dele fica 41 dp acima do centro do ecrã (385 contra 426). Beta 2.1: a
 * argola de fundo saiu do design; o texto ficou onde estava.
 */
private val TextAboveCenter = 41.dp

/**
 * O relógio das letras começa com o véu (Beta 2.2; antes esperava que o véu fosse a meio): com o
 * atraso das letras ([CycloMotion.LettersStartDelayMs]), a primeira pousa quando o véu já vai a
 * ~70 % ([CycloSprings.VeilIn]), e as seguintes entram com ele a assentar, num só movimento.
 */
private const val ContentLeadMs = 0

/** A pausa no fim, com o texto todo à vista, antes de o loading-data sair. */
private const val ReadHoldMs = 450L

/**
 * A saída das letras (uma a uma, ver [LetterRevealText]): um relógio linear, porque cada letra já
 * tem a sua curva; com uma mola, as últimas ficavam presas na cauda dela.
 */
private const val TextExitMs = 520

/** À saída, o véu espera pelo texto (o que está à frente sai primeiro) e acompanha o fim dele. */
private const val VeilExitDelayMs = 300L

/**
 * Quanto tempo o loading-data fica à vista, no mínimo, com o texto [text]: o véu a entrar, as
 * letras com o ritmo do "Hello!" (mais letras, mais tempo) e uma pausa curta para se ler. O
 * ViewModel espera isto antes de dar a mensagem de fim ([com.gaebriu.cyclo.data.CycloViewModel.confirmRestore]).
 */
fun loadingDataMinMs(text: String): Long = ContentLeadMs + LetterRevealState(text).durationMs + ReadHoldMs

/**
 * O estado do loading-data, partilhado entre o overlay ([LoadingDataOverlay]) e o desfoque do
 * ecrã por baixo ([loadingDataBlur], aplicado pelo CycloApp ao próprio conteúdo da app).
 */
@Stable
class LoadingDataState internal constructor() {
    /** O véu: 0 (sem desfoque) a 1 (desfoque 39 e véu a 63 %). */
    internal val veil = Animatable(0f)

    /** O texto a sair: 0 (à vista) a 1 (já saiu). */
    internal val exit = Animatable(0f)

    /** As letras da vez que está aberto (null = fechado). */
    internal var reveal by mutableStateOf<LetterRevealState?>(null)

    /** Enquanto está à vista (a entrar, parado ou a sair): bloqueia toques e o voltar. */
    val showing: Boolean get() = reveal != null

    /** A sair (o texto já começou a desaparecer). */
    internal var leaving by mutableStateOf(false)

    /**
     * Enquanto as mensagens da barra de baixo esperam: o fim do restauro ("Your data is back") só
     * aparece quando o loading-data começa a sair, e não por cima da animação (o ViewModel conta o
     * tempo mínimo em tempo real, e com a escala de animações do sistema acima de 1 a animação dura
     * mais). O tempo da barra só começa quando ela aparece.
     */
    val holdsMessages: Boolean get() = reveal != null && !leaving
}

/**
 * Liga o loading-data a [visible] (o ViewModel a repor os dados).
 *
 * Abrir: o desfoque do ecrã e o véu sobem com uma mola ([CycloSprings.VeilIn]) e, com eles, as letras
 * começam (a animação do Introduction 6). Fechar: espera o fim dessa animação (mesmo que os dados
 * fiquem prontos antes), e depois o texto sai, letra a letra ([TextExitMs]), e o véu e o desfoque
 * descem logo atrás ([CycloSprings.VeilOut]). A entrada corre num âmbito próprio,
 * para não ser cortada a meio se os dados ficarem prontos.
 */
@Composable
fun rememberLoadingDataState(visible: Boolean): LoadingDataState {
    val state = remember { LoadingDataState() }
    val text = stringResource(R.string.restore_loading)
    val latestText by rememberUpdatedState(text)
    val scope = rememberCoroutineScope()
    val entry = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(visible) {
        if (visible) {
            entry[0]?.join()
            val reveal = LetterRevealState(latestText, spacingEm = AllSetTextSpacingEm, settles = true)
            state.exit.snapTo(0f)
            state.leaving = false
            state.reveal = reveal
            entry[0] = scope.launch {
                coroutineScope {
                    launch { state.veil.animateTo(1f, CycloSprings.VeilIn.float) }
                    reveal.play()
                }
            }
        } else if (state.reveal != null) {
            entry[0]?.join()
            state.leaving = true
            coroutineScope {
                launch { state.exit.animateTo(1f, tween(TextExitMs, easing = LinearEasing)) }
                launch {
                    delay(VeilExitDelayMs)
                    state.veil.animateTo(0f, CycloSprings.VeilOut.float)
                }
            }
            state.reveal = null
        }
    }
    return state
}

/**
 * O desfoque de fundo do loading-data (39 do Figma), aplicado ao conteúdo da app por baixo do
 * overlay. É o próprio conteúdo a desfocar-se na GPU, por isso é em tempo real (o que muda por
 * baixo, como os dados repostos, vê-se a mudar) e cresce e diminui com o véu, sem imagens
 * congeladas nem cópias do ecrã. Fechado, não há efeito nenhum.
 *
 * É a exceção às "Advanced textures": desligadas, este desfoque continua (lê só [VisualEffects.blurEnabled]).
 */
fun Modifier.loadingDataBlur(state: LoadingDataState, effects: VisualEffects): Modifier = graphicsLayer {
    val v = state.veil.value
    val sigma = figmaBlurPx(FigmaBlur.Overlay) * v
    renderEffect = if (effects.blurEnabled && sigma > 0.3f) BlurEffect(sigma, sigma, TileMode.Clamp) else null
}

/**
 * loading-data (Componentes, 80:726): enquanto os dados são repostos, tapa o ecrã com o véu
 * (#101011 em Luminosity sobre o ecrã desfocado, ver [loadingDataBlur]) e "Bringing your data
 * back…", com a mesma animação do texto do Introduction 6 (Beta 2.1: sem a argola de fundo).
 *
 * O véu não está numa camada à parte (sem alpha no grupo): a mistura Luminosity faz-se com o ecrã
 * desfocado que está mesmo por baixo, também durante a entrada e a saída. Bloqueia os toques e o
 * voltar enquanto está à vista.
 */
@Composable
fun LoadingDataOverlay(state: LoadingDataState, modifier: Modifier = Modifier) {
    val reveal = state.reveal ?: return
    val effects = LocalVisualEffects.current

    Box(
        modifier = modifier
            .fillMaxSize()
            // Não deixa os toques passarem para baixo.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val v = state.veil.value.coerceIn(0f, 1f)
                    if (v <= 0f) return@drawBehind
                    if (effects.blurEnabled) {
                        // O desfoque por baixo fica sempre (também sem as texturas avançadas, a
                        // exceção do loading-data); só a mistura passa a normal sem elas.
                        drawRect(CycloColors.GlassFill.copy(alpha = VeilAlpha * v), blendMode = effects.luminosity)
                    } else {
                        drawRect(CycloColors.GlassFill.copy(alpha = 0.9f * v))
                    }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = -TextAboveCenter),
            contentAlignment = Alignment.Center,
        ) {
            // A saída: o texto sai antes do véu, letra a letra (Beta 2.2), cada uma a voltar a
            // desfocar-se como no início, a apagar-se e a descer um pouco. Sem animações, um fade.
            LetterRevealText(
                state = reveal,
                style = CycloType.Body,
                exit = { if (effects.animationsEnabled) state.exit.value else 0f },
                modifier = Modifier
                    .graphicsLayer {
                        if (!effects.animationsEnabled) alpha = 1f - state.exit.value
                        clip = false
                    }
                    .padding(horizontal = 24.dp),
            )
        }
    }
    BackHandler { }
}
