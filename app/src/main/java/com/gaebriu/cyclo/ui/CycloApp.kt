package com.gaebriu.cyclo.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gaebriu.cyclo.data.CycloViewModel
import com.gaebriu.cyclo.data.RoutineDraft
import com.gaebriu.cyclo.ui.components.CycloMessageBar
import androidx.compose.runtime.CompositionLocalProvider
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.settings.SettingsScreen
import com.gaebriu.cyclo.ui.time.ProvideTimeFormat
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.main.FeedRevealItems
import com.gaebriu.cyclo.ui.main.MainActions
import com.gaebriu.cyclo.ui.main.MainScreen
import com.gaebriu.cyclo.ui.main.NavigationBarHeight
import com.gaebriu.cyclo.ui.screens.IntroductionFlow
import com.gaebriu.cyclo.ui.screens.IntroductionMode
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** O que está por cima do Feed: nada, o tutorial ou o editor de rotinas. */
private const val OverlayNone = 0
private const val OverlayTutorial = 1
private const val OverlayEditor = 2
private const val OverlaySettings = 3

/**
 * A app: o Feed e as rotinas guardadas por baixo e, por cima, o tutorial (só até ser feito), o
 * editor de rotinas (Introduction 2 a 5, ao criar ou editar) ou as definições.
 *
 * - O que está por cima sobe de baixo e desce para sair, como o Introduction 1 sobre o 0: o Feed
 *   recua por baixo (zoom out, desfoque e véu escuro) e volta a avançar quando ele desce.
 * - No fim do tutorial, o Introduction 6 desce e o Feed aparece com a sua entrada em cascata
 *   (o mesmo acontece ao abrir a app depois do tutorial). Só acontece uma vez por sessão.
 * - No primeiro passo do editor, e nas definições, o gesto de voltar faz o ecrã descer a seguir ao dedo.
 * - As horas seguem o formato escolhido nas definições ou, sem escolha, o do sistema.
 * - Sem animações, os movimentos passam a fades.
 *
 * Só se mostra depois de os dados estarem lidos (o ecrã de abertura espera, ver MainActivity).
 */
@Composable
fun CycloApp(viewModel: CycloViewModel) {
    val data = viewModel.data.collectAsState().value ?: return
    val message by viewModel.message.collectAsState()
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()

    // O tutorial decide-se uma vez, ao abrir: a marca "feito" é escrita no Introduction 5, mas o
    // tutorial continua até ao fim do 6.
    var overlay by rememberSaveable { mutableIntStateOf(if (data.onboarded) OverlayNone else OverlayTutorial) }
    var overlayOpen by rememberSaveable { mutableStateOf(!data.onboarded) }
    // Cada abertura do editor tem uma chave nova, para começar sempre do zero.
    var editorKey by rememberSaveable { mutableLongStateOf(0L) }
    var editorRoutineId by rememberSaveable { mutableLongStateOf(-1L) }

    // cover: 0 = nada por cima, 1 = o que está por cima tapa tudo.
    val cover = remember { Animatable(if (overlayOpen) 1f else 0f) }
    LaunchedEffect(overlayOpen) {
        cover.animateTo(if (overlayOpen) 1f else 0f, CycloMotion.ScreenRiseSpring)
        if (!overlayOpen) overlay = OverlayNone
    }

    // Entrada do Feed: uma vez por sessão, quando ele fica à vista pela primeira vez.
    var revealPlayed by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { StaggeredReveal(FeedRevealItems.Count, startRevealed = revealPlayed) }
    val glow = remember { Animatable(if (revealPlayed) 1f else 0f) }
    val feedVisible = !(overlay == OverlayTutorial && overlayOpen)
    LaunchedEffect(feedVisible) {
        if (feedVisible && !revealPlayed) {
            revealPlayed = true
            launch { glow.animateTo(1f, tween(CycloMotion.FeedGlowDurationMs, easing = CycloMotion.Decelerate)) }
            reveal.play()
        }
    }

    fun openEditor(routineId: Long) {
        editorRoutineId = routineId
        editorKey += 1
        overlay = OverlayEditor
        overlayOpen = true
    }

    val actions = remember(viewModel) {
        MainActions(
            onCreateRoutine = { openEditor(-1L) },
            onEditRoutine = { openEditor(it.id) },
            onDeleteRoutine = viewModel::deleteRoutine,
            onToggleRoutine = { viewModel.setRoutineActive(it.id, !it.active) },
            onCaffeineChange = viewModel::changeCaffeine,
            onCaffeineReset = viewModel::resetCaffeine,
            onToggleFactor = viewModel::toggleFactor,
            onMoodChange = viewModel::setMood,
            onOpenSettings = {
                overlay = OverlaySettings
                overlayOpen = true
            },
        )
    }

    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    // O Feed só precisa de existir quando se vê (durante o tutorial está todo tapado).
    val showMain by remember { derivedStateOf { overlay != OverlayTutorial || cover.value < 1f } }
    // Tudo o que fica por baixo da barra de baixo (undo-action), que o desfoca.
    val screens = rememberBackdropLayer()

    ProvideTimeFormat(data.use24Hour) {
    Box(modifier = Modifier.fillMaxSize().background(CycloColors.Background)) {
        Box(modifier = Modifier.fillMaxSize().backdropSource(screens)) {
        if (showMain) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (effects.animationsEnabled) {
                            val c = cover.value
                            val s = 1f - (1f - CycloMotion.BackgroundScale) * c
                            scaleX = s
                            scaleY = s
                            renderEffect = if (effects.blurEnabled && c > 0f) {
                                val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * c
                                BlurEffect(sigma, sigma, TileMode.Clamp)
                            } else {
                                null
                            }
                        }
                    }
                    // Tapado pelo tutorial ou pelo editor: fica fora do alcance do TalkBack.
                    .then(if (overlayOpen) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                MainScreen(
                    data = data,
                    actions = actions,
                    reveal = reveal,
                    glow = { glow.value },
                )
                // Véu #010101 a 24 % quando o Feed está recuado.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = CycloMotion.BackgroundOverlayAlpha * cover.value }
                        .background(CycloColors.Background),
                )
            }
        }

        // Vem DEPOIS do Feed (para ter prioridade sobre o voltar das rotinas guardadas) e ANTES do
        // editor: no primeiro passo, o gesto de voltar faz o editor descer a seguir ao dedo. Os passos
        // seguintes e os menus abertos têm os seus próprios gestos, declarados depois (com prioridade).
        PredictiveBackHandler(enabled = (overlay == OverlayEditor || overlay == OverlaySettings) && overlayOpen) { progress ->
            try {
                progress.collect { event -> cover.snapTo(1f - event.progress * BackGestureReach) }
                overlayOpen = false
            } catch (e: CancellationException) {
                scope.launch { cover.animateTo(1f, CycloMotion.ScreenRiseSpring) }
            }
        }

        if (overlay != OverlayNone) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val c = cover.value
                        if (effects.animationsEnabled) {
                            translationY = (1f - c) * size.height
                        } else {
                            alpha = c
                        }
                    },
            ) {
                if (overlay == OverlayTutorial) {
                    IntroductionFlow(
                        mode = IntroductionMode.Tutorial,
                        nextRoutineNumber = data.routines.size + 1,
                        onSave = { draft, onSaved, onFailed ->
                            viewModel.addRoutine(draft, completeOnboarding = true, onSaved = onSaved, onFailed = onFailed)
                        },
                        onFinished = { overlayOpen = false },
                    )
                } else if (overlay == OverlaySettings) {
                    SettingsScreen(
                        onSetUse24Hour = viewModel::setUse24Hour,
                        onClose = { overlayOpen = false },
                    )
                } else {
                    key(editorKey) {
                        val editing = data.routines.firstOrNull { it.id == editorRoutineId }
                        IntroductionFlow(
                            mode = IntroductionMode.Editor,
                            initial = editing?.let {
                                RoutineDraft(it.name, it.wakeHour, it.wakeMinute, it.repeat, it.daysMask)
                            },
                            nextRoutineNumber = data.routines.size + 1,
                            onSave = { draft, onSaved, onFailed ->
                                if (editing != null) {
                                    viewModel.updateRoutine(editing.id, draft, onSaved, onFailed)
                                } else {
                                    viewModel.addRoutine(draft, completeOnboarding = false, onSaved = onSaved, onFailed = onFailed)
                                }
                            },
                            onFinished = { overlayOpen = false },
                            onClose = { overlayOpen = false },
                        )
                    }
                }
            }
        }

        }

        // Barra de baixo (undo-action): por cima da barra de navegação, ou do Next/Done quando o
        // tutorial ou o editor estão por cima. Desfoca o que está por trás.
        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(screens)) {
            CycloMessageBar(
                message = message,
                onDismiss = viewModel::dismissMessage,
                bottomMargin = bottomInset + CycloDimens.Spacing +
                    if (overlayOpen) CycloDimens.ButtonHeight else NavigationBarHeight,
            )
        }
    }
    }
}

/** Quanto o editor desce com o gesto de voltar completo (o resto faz a mola ao largar). */
private const val BackGestureReach = 0.4f
