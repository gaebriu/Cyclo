package com.gaebriu.cyclo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Toques durante o movimento do scroll (Beta 2.2, ver CycloScroll.kt), com o relógio manual do
 * Compose (o `adb shell input` atrasa 0,3–0,5 s e não acerta nestas janelas):
 * - com o lançamento já lento (a cauda), um toque chega ao elemento (antes era engolido);
 * - com o lançamento ainda rápido, um toque só o agarra (não carrega em nada sem querer);
 * - a mola do bounce, já lenta na ponta, não engole o toque.
 */
@RunWith(AndroidJUnit4::class)
class ScrollTapThroughTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun setList(items: Int) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(
                modifier = Modifier
                    .size(300.dp, 400.dp)
                    .testTag("scroll")
                    .cycloVerticalScroll(rememberScrollState()),
            ) {
                repeat(items) {
                    Box(Modifier.fillMaxWidth().height(80.dp).clickable { clicks++ })
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
    }

    private fun fling() {
        rule.onNodeWithTag("scroll").performTouchInput {
            swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80)
        }
    }

    private fun tap() {
        rule.onNodeWithTag("scroll").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(400)
    }

    @Test
    fun tapDuringTheSlowTailReachesTheElement() {
        setList(items = 60)
        fling()
        // Um lançamento de ~10 000 px/s fica abaixo de 300 dp/s em ~1,3 s e ainda anda aos 2 s.
        rule.mainClock.advanceTimeBy(2_000)
        tap()
        assertEquals(1, clicks)
    }

    @Test
    fun tapDuringAFastFlingOnlyCatchesIt() {
        setList(items = 60)
        fling()
        rule.mainClock.advanceTimeBy(60)
        tap()
        assertEquals(0, clicks)
    }

    @Test
    fun tapWhileTheEdgeBounceSettlesReachesTheElement() {
        setList(items = 8)
        fling()
        // Bateu no fundo logo e está a voltar com a mola, já devagar.
        rule.mainClock.advanceTimeBy(600)
        tap()
        assertEquals(1, clicks)
    }
}
