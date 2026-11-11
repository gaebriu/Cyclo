package com.gaebriu.cyclo.ui.main

import com.gaebriu.cyclo.ui.theme.motionSpec
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.FeedSection
import com.gaebriu.cyclo.data.TrackingSection
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.SleepFactor
import com.gaebriu.cyclo.ui.effects.BackGestureVelocity
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.sideBySidePage
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.rubberBand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** O que o Feed e as rotinas guardadas podem pedir. */
class MainActions(
    val onCreateRoutine: () -> Unit,
    val onEditRoutine: (Routine) -> Unit,
    val onDeleteRoutine: (Routine) -> Unit,
    val onToggleRoutine: (Routine) -> Unit,
    /** "Add as a widget" de uma rotina guardada: pede ao ecrã principal para fixar o widget dela. */
    val onAddWidget: (Routine) -> Unit,
    /** "Add as a widget" do menu do Wake Time do Feed: fixa o Wake Time Widget. */
    val onAddWakeWidget: () -> Unit,
    val onCaffeineChange: (Int) -> Unit,
    /** O "+" e o "−" da água (passos de 250 mL). */
    val onWaterChange: (Int) -> Unit,
    val onToggleFactor: (SleepFactor) -> Unit,
    val onMoodChange: (Float) -> Unit,
    val onOpenSettings: () -> Unit,
    /** Nova ordem das rotinas (arrastadas nas rotinas guardadas). */
    val onReorderRoutines: (List<Routine>) -> Unit,
    /** Nova ordem dos blocos do Feed. */
    val onReorderFeed: (List<FeedSection>) -> Unit,
    /** "Log sleep" do sleep-evaluation-prompt: abre o action-menu-log-sleep. */
    val onLogSleep: () -> Unit,
    /** O X do sleep-evaluation-prompt (esconde-o até a app voltar a abrir). */
    val onDismissSleepPrompt: () -> Unit,
    /** Nova ordem dos cartões da Tracking. */
    val onReorderTracking: (List<TrackingSection>) -> Unit,
)

private const val FeedPage = 0
private const val SavedPage = 1
private const val TrackingPage = 2

/**
 * O Feed, as rotinas guardadas e a Tracking, lado a lado (por esta ordem). Só o conteúdo muda entre
 * eles; o brilho do topo, as faixas do topo e do fundo, o mini-menu e a barra de navegação são FIXOS.
 *
 * - Uma só posição contínua ([page], 0 = Feed, 1 = rotinas guardadas, 2 = Tracking) guia tudo: as
 *   páginas (deslizam lado a lado e a que sai escurece um pouco), a pílula da barra de navegação, os
 *   botões do mini-menu (a lupa só nas rotinas guardadas, o "+" fora da Tracking) e o gradiente do
 *   brilho (a Tracking tem o das rotinas guardadas). Assim, o gesto, o toque na barra e o voltar são
 *   o mesmo movimento.
 * - Swipe: para a esquerda vai para a página seguinte, para a direita para a anterior. A página
 *   segue o dedo sem atraso; para lá das pontas há resistência (rubber band). Ao largar, muda para a
 *   vizinha se passou de metade ou se o lançamento foi rápido; senão volta. A mola
 *   [CycloSprings.Page] parte com a velocidade do dedo. Um háptico marca o momento em que passar
 *   o limite (e voltar atrás dele).
 * - O swipe só começa com um movimento claramente horizontal, fora das zonas de gesto do sistema,
 *   e não com a pesquisa aberta; o scroll, o medidor de emoções e o arrastar para reordenar ficam
 *   com os seus gestos (consomem-nos primeiro).
 * - Voltar recua uma página a seguir ao gesto (voltar preditivo): Tracking → rotinas guardadas →
 *   Feed (decisão da Beta 2.0: a pré-visualização mostra a página que vem mesmo); no Feed, sai da app.
 * - O brilho do topo segue a hora do aparelho ([skyColorsAt]) e, ao mudar de página, volta a
 *   crescer e a acender um pouco enquanto passa para o gradiente complementar.
 * - A pesquisa: vazia, fecha quando se acaba de escrever (Enter, teclado fechado ou fim do ditado);
 *   com texto, fica aberta com os resultados até se mudar de página ou tocar outra vez na lupa.
 * - Sem animações no sistema, as páginas trocam com um fade (sem deslizar).
 * - Unified view (beta-3.0, [CycloData.unifiedView]): a Tracking junta-se ao Feed e deixa de ser
 *   uma página; ficam o Feed e as rotinas guardadas, e a barra de navegação encolhe para os dois
 *   botões (2066:395, 177 dp). Ligar com a Tracking escolhida leva as páginas para o Feed.
 *
 * Camadas de fundo (ver effects/Backdrop.kt): o fundo com o brilho fica por baixo de tudo; o
 * conteúdo das páginas fica por baixo das faixas, do mini-menu e da barra de navegação.
 *
 * @param reveal entrada em cascata da primeira vez que o Feed aparece (ver [FeedRevealItems]).
 * @param glow de 0 a 1, o brilho do topo a nascer.
 */
/** A sessão (o processo) já mostrou a entrada do estado vazio das rotinas guardadas. */
private object SavedEmptySession {
    @Volatile
    var revealed = false
}

@Composable
fun MainScreen(
    data: CycloData,
    actions: MainActions,
    reveal: StaggeredReveal,
    glow: () -> Float,
    modifier: Modifier = Modifier,
    sleepPromptDismissed: Boolean = false,
    trackingRequest: Int = 0,
) {
    val effects = LocalVisualEffects.current
    val density = LocalDensity.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // A página escolhida (guardada ao rodar o ecrã) e a posição contínua das duas.
    // Aberta pela notificação do "Log sleep" (Beta 2.3, [trackingRequest] > 0 ao nascer): começa já
    // na Tracking, sem passar pelo Feed.
    val unified = data.unifiedView
    // A última página: a Tracking, ou as rotinas guardadas com o Unified view (a Tracking está no Feed).
    val lastPage = if (unified) SavedPage else TrackingPage
    // O separador onde vive o registo do sono: a Tracking, ou o Feed com o Unified view.
    val sleepPage = if (unified) FeedPage else TrackingPage
    var selected by rememberSaveable { mutableIntStateOf(if (trackingRequest > 0) sleepPage else FeedPage) }
    // Limiar fino (Beta 2.2): com o de omissão (0,01 de página) a mola parava ~10 px antes e saltava.
    val page = remember { Animatable(selected.toFloat(), visibilityThreshold = 0.0005f) }
    // Enquanto o dedo arrasta, a posição está aqui (sem corrotinas a cada movimento).
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val position: () -> Float = { if (dragging) dragPosition else page.value }
    // Beta 2.4: o mini-menu, a barra e o brilho num salto por cima de uma página (ver PageChrome).
    val chrome = remember(lastPage) { PageChrome(lastPage = lastPage, searchPage = SavedPage, trackingPage = TrackingPage) }
    val tab = MainTab.entries[selected.coerceIn(FeedPage, lastPage)]

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // O estado vazio das rotinas guardadas entra em cascata só na primeira vez desta sessão (do
    // processo: voltar a entrar nas rotinas guardadas, rodar ou recriar a atividade não repete).
    val savedEmptyReveal = remember { StaggeredReveal(2, startRevealed = SavedEmptySession.revealed) }
    val onSavedEmptyShown = { SavedEmptySession.revealed = true }
    // A Tracking entra em cascata (cartões e gráficos a crescer) só na primeira vez desta sessão.
    val trackingReveal = remember { StaggeredReveal(TrackingRevealItems.Count, startRevealed = TrackingSession.revealed) }
    val onTrackingShown = { TrackingSession.revealed = true }
    val searchAvailable = animateFloatAsState(
        targetValue = if (data.routines.isNotEmpty()) 1f else 0f,
        animationSpec = motionSpec(effects.animationsEnabled, CycloSprings.Snappy.float),
        label = "searchAvailable",
    )
    fun closeSearch() {
        searchOpen = false
        query = ""
        focusManager.clearFocus()
    }
    // A última rotina saiu com a pesquisa aberta: a caixa fecha (já não há o que procurar).
    LaunchedEffect(data.routines.isEmpty()) {
        if (data.routines.isEmpty() && searchOpen) closeSearch()
    }

    // O frame do Figma tem 24 dp de barra de estado e 48 dp de barra de navegação.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtra = (statusTop - 24.dp).coerceAtLeast(0.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val bottomReserve = navigationBarReserve(bottomInset)
    // As zonas de gesto do sistema (voltar pelas bordas): o swipe não começa lá.
    val gestureInsets = WindowInsets.systemGestures
    val layoutDirection = LocalLayoutDirection.current

    // Cores do brilho conforme a hora, com transição lenta quando a faixa horária muda.
    val now by rememberMinuteClock()
    val sky = skyColorsAt(now.hour, now.minute)
    val skyTop by animateColorAsState(sky.top, tween(CycloMotion.SkyChangeDurationMs), label = "skyTop")
    val skyBase by animateColorAsState(sky.base, tween(CycloMotion.SkyChangeDurationMs), label = "skyBase")
    // Ao passar de página o brilho encolhe e apaga-se um pouco a meio do caminho e volta a crescer
    // ao assentar (Beta 2.2: segue a MESMA posição contínua das páginas, como a pílula da barra; antes
    // saltava para trás no fotograma da mudança e voltava num relógio próprio, recomeçando do zero
    // em cada mudança rápida). O dedo, o toque na barra e o voltar dão o mesmo movimento.
    val pagePulse: () -> Float = {
        if (!effects.animationsEnabled) {
            1f
        } else {
            1f - chrome.value(position(), PageChrome.Stretch)
        }
    }
    var lastTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab == lastTab) return@LaunchedEffect
        lastTab = tab
        // Sair das rotinas guardadas (para qualquer lado: barra, swipe ou voltar) fecha a pesquisa.
        if (tab != MainTab.Saved) closeSearch()
    }

    /** Leva as páginas a [target], a partir de onde estão e com a velocidade que tiverem. */
    fun settleTo(target: Int, velocity: Float = page.velocity) {
        selected = target
        chrome.onSettle(page.value, target.toFloat())
        scope.launch {
            if (effects.animationsEnabled) {
                page.animateTo(target.toFloat(), CycloSprings.Page.float, initialVelocity = velocity)
            } else {
                page.animateTo(target.toFloat(), tween(CycloMotion.TabSwitchDurationMs))
            }
            // Só se chegou ao fim (interrompida, a mola seguinte parte do que se vê).
            chrome.onSettled()
        }
    }

    fun selectTab(target: MainTab) {
        if (target.ordinal != selected) settleTo(target.ordinal)
    }

    // Cada novo [trackingRequest] (a notificação do "Log sleep" com a app já aberta) leva as páginas
    // à Tracking, com a mola das páginas, como tocar no separador. Os já tratados não se repetem
    // (nem ao recriar a atividade).
    var handledTrackingRequest by rememberSaveable { mutableIntStateOf(trackingRequest) }
    LaunchedEffect(trackingRequest) {
        if (trackingRequest == handledTrackingRequest) return@LaunchedEffect
        handledTrackingRequest = trackingRequest
        if (selected != sleepPage) settleTo(sleepPage)
    }

    // Unified view ligado com a Tracking escolhida (as definições estão por cima): as páginas passam
    // logo para o Feed, onde a Tracking agora está. Sem animação: não se vê.
    LaunchedEffect(lastPage) {
        if (selected > lastPage) {
            selected = FeedPage
            chrome.onSettled()
            page.snapTo(FeedPage.toFloat())
        }
    }

    // Voltar: a página anterior entra a seguir ao gesto e, ao largar, a mola acaba (Beta 2.2: com a
    // velocidade do dedo, [BackGestureVelocity]; antes recomeçava parada).
    PredictiveBackHandler(enabled = selected > FeedPage && !dragging) { progress ->
        val from = selected
        val gesture = BackGestureVelocity()
        try {
            progress.collect { event ->
                gesture.add(event.progress)
                page.snapTo(from - event.progress * CycloMotion.PageBackReach)
            }
            settleTo(from - 1, -gesture.progressPerSecond * CycloMotion.PageBackReach)
        } catch (e: CancellationException) {
            settleTo(from, -gesture.progressPerSecond * CycloMotion.PageBackReach)
        }
    }

    val latestSearchOpen by rememberUpdatedState(searchOpen)
    val latestLastPage by rememberUpdatedState(lastPage)
    val swipeExclusions = remember { PageSwipeExclusions() }
    val swipeCoordinates = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    // O gesto vê os eventos antes dos filhos (passagem Initial), mas só os reclama se ninguém os
    // reclamou: o scroll vertical, o arrastar para reordenar e o medidor de emoções (que tem um
    // limiar menor) consomem-nos primeiro, e o swipe desiste ao ver isso na passagem Final.
    val swipe = Modifier.pointerInput(effects.animationsEnabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // O tamanho lê-se a cada gesto (no início do pointerInput ainda pode ser 0).
            val width = size.width.toFloat()
            if (latestSearchOpen || width <= 0f) return@awaitEachGesture
            val left = gestureInsets.getLeft(this@pointerInput, layoutDirection)
            val right = gestureInsets.getRight(this@pointerInput, layoutDirection)
            if (down.position.x < left || down.position.x > width - right) return@awaitEachGesture
            // Num elemento com gesto horizontal próprio (o medidor de emoções), o gesto é dele.
            val window = swipeCoordinates[0]?.takeIf { it.isAttached }?.localToWindow(down.position)
            if (window != null && swipeExclusions.contains(window)) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop * SwipeSlopFactor

            // 1. Esperar por um movimento claramente horizontal (ou desistir).
            var claimed: PointerInputChange? = null
            while (claimed == null) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                val total = change.position - down.position
                if (abs(total.x) > slop && abs(total.x) > abs(total.y) * HorizontalDominance) {
                    change.consume()
                    claimed = change
                } else {
                    // Os filhos tratam deste evento; se algum o consumir, o gesto é dele.
                    val final = awaitPointerEvent(PointerEventPass.Final)
                    if (final.changes.any { it.isConsumed }) return@awaitEachGesture
                }
            }

            // 2. A página segue o dedo até largar.
            val from = page.value
            val fromPage = selected
            val tracker = VelocityTracker()
            tracker.addPosition(claimed.uptimeMillis, claimed.position)
            scope.launch { page.stop() }
            chrome.onDragStart(from)
            dragging = true
            var beyond = false

            fun follow(x: Float) {
                val raw = from - (x - down.position.x) / width
                // Para lá das pontas, resistência do iOS.
                dragPosition = when {
                    raw < 0f -> -rubberBand(-raw * width, width) / width
                    raw > latestLastPage -> latestLastPage + rubberBand((raw - latestLastPage) * width, width) / width
                    else -> raw
                }
                val past = abs(dragPosition - fromPage) > CycloMotion.PageSwipeThreshold
                if (past != beyond) {
                    beyond = past
                    CycloHaptics.thresholdCrossed(view, past)
                }
            }
            follow(claimed.position.x)
            var completed = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    completed = false
                    break
                }
                change.consume()
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                follow(change.position.x)
            }
            // Velocidade em páginas por segundo (positiva = para a página seguinte).
            val velocity = if (completed) -tracker.calculateVelocity().x / width else 0f
            // Um gesto muda no máximo para a vizinha (a da direita ou a da esquerda de onde começou).
            val target = when {
                !completed -> fromPage
                abs(velocity) > CycloMotion.PageFlingVelocity -> fromPage + if (velocity > 0f) 1 else -1
                else -> dragPosition.roundToInt()
            }.coerceIn((fromPage - 1).coerceAtLeast(FeedPage), (fromPage + 1).coerceAtMost(latestLastPage))
            val release = dragPosition
            scope.launch {
                page.snapTo(release)
                dragging = false
                settleTo(target, velocity)
            }
        }
    }

    val background = rememberBackdropLayer()
    val pages = rememberBackdropLayer()
    val saveable = rememberSaveableStateHolder()
    // Uma página só existe enquanto se vê (ou é a escolhida); o estado dela (scroll) guarda-se.
    val showFeed by remember { derivedStateOf { selected == FeedPage || position() < 0.999f } }
    val showSaved by remember { derivedStateOf { selected == SavedPage || abs(position() - SavedPage) < 0.999f } }
    val showTracking by remember(unified) { derivedStateOf { !unified && (selected == TrackingPage || position() > SavedPage + 0.001f) } }
    // A barra de navegação com (1) ou sem (0) o botão da Tracking, a mudar com uma mola.
    val trackingTab = animateFloatAsState(
        targetValue = if (unified) 0f else 1f,
        animationSpec = motionSpec(effects.animationsEnabled, CycloSprings.Reflow.float),
        label = "trackingTab",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Fundo e brilho: o que está por baixo de tudo.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(background)
                .background(CycloColors.Background),
        ) {
            FeedTopGlow(
                growth = glow,
                pulse = pagePulse,
                sky = { SkyColors(skyTop, skyBase) },
                saved = { chrome.value(position(), PageChrome.Saved) },
            )
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(background), LocalPageSwipeExclusions provides swipeExclusions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(pages)
                    .padding(top = topExtra)
                    .onGloballyPositioned { swipeCoordinates[0] = it }
                    .then(swipe),
            ) {
                if (showFeed) {
                    MainPage(index = FeedPage, position = position, animationsEnabled = effects.animationsEnabled, active = selected == FeedPage) {
                        saveable.SaveableStateProvider("feed") {
                            FeedScreen(
                                now = now,
                                routines = data.routines,
                                dailyLog = data.dailyLog,
                                history = data.history,
                                age = data.age,
                                chronotype = data.chronotype,
                                fallAsleep = data.fallAsleep,
                                feedOrder = data.feedOrder,
                                reveal = reveal,
                                bottomReserve = bottomReserve,
                                wakeShadow = skyBase,
                                onEditRoutine = actions.onEditRoutine,
                                onRemoveRoutine = actions.onDeleteRoutine,
                                onAddWakeWidget = actions.onAddWakeWidget,
                                onCaffeineChange = actions.onCaffeineChange,
                                onWaterChange = actions.onWaterChange,
                                onToggleFactor = actions.onToggleFactor,
                                onMoodChange = actions.onMoodChange,
                                onReorder = actions.onReorderFeed,
                                isCurrentPage = selected == FeedPage,
                                unified = unified,
                                promptDismissed = sleepPromptDismissed,
                                promptGlow = { skyBase },
                                onLogSleep = actions.onLogSleep,
                                onDismissPrompt = actions.onDismissSleepPrompt,
                            )
                        }
                    }
                }
                if (showSaved) {
                    MainPage(index = SavedPage, position = position, animationsEnabled = effects.animationsEnabled, active = selected == SavedPage) {
                        saveable.SaveableStateProvider("saved") {
                            SavedRoutinesScreen(
                                routines = data.routines,
                                searchOpen = searchOpen,
                                query = query,
                                onQueryChange = { query = it },
                                onSearchFinished = {
                                    // Vazia, a caixa desaparece; com texto, fica com os resultados.
                                    if (query.isBlank()) closeSearch() else focusManager.clearFocus()
                                },
                                onSearchEnter = { hasResults ->
                                    // Enter: vazia ou sem resultados, a caixa fecha e voltam todas as
                                    // rotinas; com resultados, fica aberta com o texto e só eles.
                                    if (query.isBlank() || !hasResults) closeSearch()
                                },
                                emptyReveal = savedEmptyReveal,
                                onEmptyShown = onSavedEmptyShown,
                                bottomReserve = bottomReserve,
                                onDelete = actions.onDeleteRoutine,
                                onEdit = actions.onEditRoutine,
                                onToggleActive = actions.onToggleRoutine,
                                onAddWidget = actions.onAddWidget,
                                onReorder = actions.onReorderRoutines,
                            )
                        }
                    }
                }
                if (showTracking) {
                    MainPage(index = TrackingPage, position = position, animationsEnabled = effects.animationsEnabled, active = selected == TrackingPage) {
                        saveable.SaveableStateProvider("tracking") {
                            TrackingScreen(
                                now = now,
                                routines = data.routines,
                                dailyLog = data.dailyLog,
                                history = data.history,
                                age = data.age,
                                order = data.trackingOrder,
                                promptDismissed = sleepPromptDismissed,
                                promptGlow = { skyBase },
                                reveal = trackingReveal,
                                onShown = onTrackingShown,
                                bottomReserve = bottomReserve,
                                onLogSleep = actions.onLogSleep,
                                onDismissPrompt = actions.onDismissSleepPrompt,
                                onReorder = actions.onReorderTracking,
                                isCurrentPage = selected == TrackingPage,
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(background, pages)) {
            MainEdgeFades(topExtra = topExtra)

            MiniMenu(
                actions = listOf(
                    MiniMenuAction(CycloAppIconPaths.Search, stringResource(R.string.main_search)) {
                        if (searchOpen) closeSearch() else searchOpen = true
                    },
                    MiniMenuAction(CycloAppIconPaths.Add, stringResource(R.string.main_add_routine), actions.onCreateRoutine),
                    MiniMenuAction(CycloAppIconPaths.Settings, stringResource(R.string.main_settings)) {
                        focusManager.clearFocus()
                        actions.onOpenSettings()
                    },
                ),
                // A lupa só existe nas rotinas guardadas, e só com rotinas (o estado vazio do Figma,
                // 255:40, tem só o + e as definições); o "+" não existe na Tracking (2001:255, só a
                // roda dentada). O mini-menu alarga e encolhe com a página.
                slot = { index ->
                    val p = position()
                    when (index) {
                        0 -> chrome.value(p, PageChrome.Search) * searchAvailable.value
                        1 -> chrome.value(p, PageChrome.Plus)
                        else -> 1f
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = maxOf(MiniMenuTop, statusTop + 24.dp), end = CycloDimens.ScreenMargin)
                    .staggeredReveal(reveal, FeedRevealItems.MiniMenu, rise = (-12).dp),
            )

            MainNavigationBar(
                selected = tab,
                pagePosition = position,
                stretch = { chrome.value(position(), PageChrome.Stretch) },
                iconAmount = { index -> chrome.value(position(), PageChrome.Icon0 + index) },
                onSelect = ::selectTab,
                trackingTab = { trackingTab.value },
                descriptions = listOf(
                    stringResource(R.string.main_tab_feed),
                    stringResource(R.string.main_tab_saved),
                    stringResource(R.string.main_tab_tracking),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset)
                    .staggeredReveal(reveal, FeedRevealItems.NavigationBar, rise = CycloMotion.FeedRevealRise * 2),
            )
        }
    }
}

/**
 * Uma das duas páginas: desliza com a posição (a outra entra ao lado) e escurece um pouco enquanto
 * sai. Sem animações, só o fade. Fora de vista, sai do alcance do TalkBack.
 */
@Composable
private fun MainPage(
    index: Int,
    position: () -> Float,
    animationsEnabled: Boolean,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .sideBySidePage(index, position, animationsEnabled)
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        content()
    }
}

/**
 * O limiar do swipe, em múltiplos do touch slop: maior do que o dos filhos com gestos horizontais
 * (o medidor de emoções), para eles ganharem quando o gesto começa neles.
 */
private const val SwipeSlopFactor = 1.5f

/** O swipe só começa quando o movimento é pelo menos isto mais horizontal do que vertical. */
private const val HorizontalDominance = 1.2f

