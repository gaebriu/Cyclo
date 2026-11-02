package com.gaebriu.cyclo.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
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
    val onCaffeineChange: (Int) -> Unit,
    val onCaffeineReset: () -> Unit,
    val onToggleFactor: (SleepFactor) -> Unit,
    val onMoodChange: (Float) -> Unit,
)

/**
 * O Feed e as rotinas guardadas. Só o conteúdo muda entre os dois (Navigation Compose, com um fade
 * cruzado e um ligeiro deslize); o brilho do topo, as faixas escuras, o mini-menu e a barra de
 * navegação são FIXOS.
 *
 * - Voltar nas rotinas guardadas vai para o Feed; no Feed, sai da app. Com a pesquisa aberta,
 *   voltar fecha-a primeiro.
 * - O "+" do mini-menu cria uma rotina; o botão das definições ainda não faz nada.
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

    fun selectTab(target: MainTab) {
        if (target == tab) return
        if (target == MainTab.Saved) {
            navController.navigate(SavedRoute) {
                popUpTo(FeedRoute)
                launchSingleTop = true
            }
        } else {
            closeSearch()
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

    Box(modifier = modifier.fillMaxSize().background(CycloColors.Background)) {
        FeedTopGlow(growth = glow)

        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.fillMaxSize().padding(top = topExtra),
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = enter,
            popExitTransition = exit,
        ) {
            composable(FeedRoute) {
                FeedScreen(
                    routines = data.routines,
                    dailyLog = data.dailyLog,
                    reveal = reveal,
                    bottomReserve = bottomReserve,
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
                    bottomReserve = bottomReserve,
                    onDelete = actions.onDeleteRoutine,
                    onEdit = actions.onEditRoutine,
                    onToggleActive = actions.onToggleRoutine,
                )
            }
        }

        MainEdgeFades()

        MiniMenu(
            actions = listOf(
                MiniMenuAction(CycloAppIconPaths.Search, stringResource(R.string.main_search)) {
                    if (searchOpen) closeSearch() else searchOpen = true
                },
                MiniMenuAction(CycloAppIconPaths.Add, stringResource(R.string.main_add_routine), actions.onCreateRoutine),
                // As definições ainda não têm design: o botão é só visual por agora.
                MiniMenuAction(CycloAppIconPaths.Settings, stringResource(R.string.main_settings)) {},
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

    // Vem DEPOIS do NavHost, por isso tem prioridade: voltar fecha primeiro a pesquisa.
    BackHandler(enabled = searchOpen) { closeSearch() }
}
