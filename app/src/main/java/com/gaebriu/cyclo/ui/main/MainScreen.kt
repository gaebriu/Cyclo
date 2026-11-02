package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.SleepFactor
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion

private const val FeedRoute = "feed"
private const val SavedRoute = "saved"

/** O que o Feed e as rotinas guardadas podem pedir. */
class MainActions(
    val onCreateRoutine: () -> Unit,
    val onEditRoutine: (Routine) -> Unit,
    val onDeleteRoutine: (Routine) -> Unit,
    val onToggleRoutine: (Routine) -> Unit,
    /** "Add as a widget" de uma rotina guardada: pede ao ecrã principal para fixar o widget dela. */
    val onAddWidget: (Routine) -> Unit,
    val onCaffeineChange: (Int) -> Unit,
    val onCaffeineReset: () -> Unit,
    val onToggleFactor: (SleepFactor) -> Unit,
    val onMoodChange: (Float) -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * O Feed e as rotinas guardadas. Só o conteúdo muda entre os dois (Navigation Compose, com um fade
 * cruzado e um ligeiro deslize); o brilho do topo, as faixas do topo e do fundo, o mini-menu e a
 * barra de navegação são FIXOS.
 *
 * - O brilho do topo segue a hora do aparelho ([skyColorsAt]) e, ao mudar de página, volta a
 *   crescer e a acender um pouco (o "Fade" do Introduction 0) enquanto passa para o gradiente
 *   complementar das rotinas guardadas (ou volta ao do Feed). As cores mudam com uma transição lenta.
 * - Voltar nas rotinas guardadas vai para o Feed; no Feed, sai da app.
 * - A pesquisa: vazia, fecha quando se acaba de escrever (Enter, teclado fechado ou fim do ditado);
 *   com texto, fica aberta com os resultados até se mudar de página ou tocar outra vez na lupa.
 * - O "+" do mini-menu cria uma rotina; a roda dentada abre as definições.
 *
 * Camadas de fundo (ver effects/Backdrop.kt): o fundo com o brilho fica por baixo de tudo; o
 * conteúdo das páginas fica por baixo das faixas, do mini-menu e da barra de navegação.
 *
 * @param reveal entrada em cascata da primeira vez que o Feed aparece (ver [FeedRevealItems]).
 * @param glow de 0 a 1, o brilho do topo a nascer.
 */
@Composable
fun MainScreen(
    data: CycloData,
    actions: MainActions,
    reveal: StaggeredReveal,
    glow: () -> Float,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val tab = if (entry?.destination?.route == SavedRoute) MainTab.Saved else MainTab.Feed

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    fun closeSearch() {
        searchOpen = false
        query = ""
        focusManager.clearFocus()
    }

    // O frame do Figma tem 24 dp de barra de estado e 48 dp de barra de navegação.
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtra = (statusTop - 24.dp).coerceAtLeast(0.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val bottomReserve = navigationBarReserve(bottomInset)

    // A lupa só existe nas rotinas guardadas: o mini-menu alarga e ela aparece.
    val searchSlot by animateFloatAsState(
        targetValue = if (tab == MainTab.Saved) 1f else 0f,
        animationSpec = CycloMotion.NavIndicatorSpring,
        label = "searchSlot",
    )

    // Cores do brilho conforme a hora, com transição lenta quando a faixa horária muda.
    val now by rememberMinuteClock()
    val sky = skyColorsAt(now.hour)
    val skyTop by animateColorAsState(sky.top, tween(CycloMotion.SkyChangeDurationMs), label = "skyTop")
    val skyBase by animateColorAsState(sky.base, tween(CycloMotion.SkyChangeDurationMs), label = "skyBase")
    // Feed (0) ou rotinas guardadas (1): o gradiente complementar.
    val savedGlow by animateFloatAsState(
        targetValue = if (tab == MainTab.Saved) 1f else 0f,
        animationSpec = tween(CycloMotion.GlowTabDurationMs, easing = FastOutSlowInEasing),
        label = "savedGlow",
    )
    // Ao mudar de página o brilho volta a crescer e a acender (não na primeira vez).
    val pulse = remember { Animatable(1f) }
    var lastTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) {
        if (tab == lastTab) return@LaunchedEffect
        lastTab = tab
        // Sair das rotinas guardadas (pela barra ou pelo voltar) fecha a pesquisa.
        if (tab == MainTab.Feed) closeSearch()
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(CycloMotion.GlowTabDurationMs, easing = CycloMotion.Decelerate))
    }

    fun selectTab(target: MainTab) {
        if (target == tab) return
        if (target == MainTab.Saved) {
            navController.navigate(SavedRoute) {
                popUpTo(FeedRoute)
                launchSingleTop = true
            }
        } else {
            navController.popBackStack(FeedRoute, inclusive = false)
        }
    }

    val shift = with(density) { CycloMotion.TabSwitchShift.roundToPx() }
    val spec = tween<Float>(CycloMotion.TabSwitchDurationMs, easing = FastOutSlowInEasing)
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val toSaved = targetState.destination.route == SavedRoute
        if (effects.animationsEnabled) {
            fadeIn(spec) + slideInHorizontally(tween(CycloMotion.TabSwitchDurationMs, easing = FastOutSlowInEasing)) {
                if (toSaved) shift else -shift
            }
        } else {
            fadeIn(spec)
        }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val toSaved = targetState.destination.route == SavedRoute
        if (effects.animationsEnabled) {
            fadeOut(spec) + slideOutHorizontally(tween(CycloMotion.TabSwitchDurationMs, easing = FastOutSlowInEasing)) {
                if (toSaved) -shift else shift
            }
        } else {
            fadeOut(spec)
        }
    }

    val background = rememberBackdropLayer()
    val pages = rememberBackdropLayer()

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
                pulse = { pulse.value },
                sky = { SkyColors(skyTop, skyBase) },
                saved = { savedGlow },
            )
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(background)) {
            NavHost(
                navController = navController,
                startDestination = FeedRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(pages)
                    .padding(top = topExtra),
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = enter,
                popExitTransition = exit,
            ) {
                composable(FeedRoute) {
                    FeedScreen(
                        now = now,
                        routines = data.routines,
                        dailyLog = data.dailyLog,
                        fallAsleepMinutes = data.fallAsleep.planMinutes.toLong(),
                        reveal = reveal,
                        bottomReserve = bottomReserve,
                        wakeShadow = skyBase,
                        onEditRoutine = actions.onEditRoutine,
                        onRemoveRoutine = actions.onDeleteRoutine,
                        onCaffeineChange = actions.onCaffeineChange,
                        onCaffeineReset = actions.onCaffeineReset,
                        onToggleFactor = actions.onToggleFactor,
                        onMoodChange = actions.onMoodChange,
                    )
                }
                composable(SavedRoute) {
                    SavedRoutinesScreen(
                        routines = data.routines,
                        searchOpen = searchOpen,
                        query = query,
                        onQueryChange = { query = it },
                        onSearchFinished = {
                            // Vazia, a caixa desaparece; com texto, fica com os resultados.
                            if (query.isBlank()) closeSearch() else focusManager.clearFocus()
                        },
                        bottomReserve = bottomReserve,
                        onDelete = actions.onDeleteRoutine,
                        onEdit = actions.onEditRoutine,
                        onToggleActive = actions.onToggleRoutine,
                        onAddWidget = actions.onAddWidget,
                    )
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
                firstSlot = { searchSlot },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = maxOf(MiniMenuTop, statusTop + 24.dp), end = CycloDimens.ScreenMargin)
                    .staggeredReveal(reveal, FeedRevealItems.MiniMenu, rise = (-12).dp),
            )

            MainNavigationBar(
                selected = tab,
                onSelect = ::selectTab,
                feedDescription = stringResource(R.string.main_tab_feed),
                savedDescription = stringResource(R.string.main_tab_saved),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset)
                    .staggeredReveal(reveal, FeedRevealItems.NavigationBar, rise = CycloMotion.FeedRevealRise * 2),
            )
        }
    }
}
