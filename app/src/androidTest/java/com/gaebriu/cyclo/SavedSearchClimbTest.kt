package com.gaebriu.cyclo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.ui.effects.ProvideVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.main.SavedRoutinesScreen
import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saved routines (Beta 2.2): a lupa com a página lá em baixo sobe a página até ao topo e só então
 * abre a caixa de pesquisa, já à vista (antes abria no sítio dela, fora do ecrã).
 */
@RunWith(AndroidJUnit4::class)
class SavedSearchClimbTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun searchFromTheBottomClimbsToTheTopFirst() {
        var searchOpen by mutableStateOf(false)
        val routines = (1L..12L).map { Routine(it, "Routine $it", 7, 0, RepeatOption.Everyday, 0, true, 0L) }
        rule.setContent {
            ProvideVisualEffects {
                Box(Modifier.fillMaxSize().background(Color(0xFF010101)).testTag("root")) {
                    SavedRoutinesScreen(
                        routines = routines,
                        searchOpen = searchOpen,
                        query = "",
                        onQueryChange = {},
                        onSearchFinished = {},
                        onSearchEnter = {},
                        bottomReserve = 120.dp,
                        emptyReveal = StaggeredReveal(2, startRevealed = true),
                        onEmptyShown = {},
                        onDelete = {},
                        onEdit = {},
                        onToggleActive = {},
                        onAddWidget = {},
                        onReorder = {},
                    )
                }
            }
        }
        // Até ao fundo.
        repeat(4) {
            rule.onNodeWithTag("root").performTouchInput { swipeUp(durationMillis = 300) }
            rule.waitForIdle()
        }
        assertTrue(rule.onNodeWithText("Routine 1").getUnclippedBoundsInRoot().top < 0.dp)
        searchOpen = true
        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
        // A página voltou ao topo e a caixa está à vista, no topo do conteúdo.
        val field = rule.onNodeWithText("What are you looking for?").getUnclippedBoundsInRoot()
        val first = rule.onNodeWithText("Routine 1").getUnclippedBoundsInRoot()
        assertTrue("caixa em ${field.top}", field.top > 100.dp && field.top < 160.dp)
        assertTrue("a primeira rotina por baixo da caixa", first.top > field.bottom)
    }
}
