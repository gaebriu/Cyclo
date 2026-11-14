package com.gaebriu.cyclo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaebriu.cyclo.ui.screens.IntroductionFlow
import com.gaebriu.cyclo.ui.screens.IntroductionMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O gesto das páginas do editor (beta-3.2) com velocidade a sério (o `adb shell input swipe` larga a
 * 0 e só testa a distância): um lançamento curto e rápido para cima avança, tanto a partir da área
 * com scroll de uma página (nested scroll) como do indicador em baixo (o detetor fora do scroll);
 * e as rodas da hora do Introduction 2 ficam com o gesto delas (a página não muda).
 */
@RunWith(AndroidJUnit4::class)
class IntroSwipeFlingTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun title(id: Int) = context.getString(id)

    private fun setEditor() {
        // O relógio à mão: o indicador oscila para sempre e nunca deixaria o Compose ficar parado.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize().testTag("flow")) {
                IntroductionFlow(
                    onSave = { _, _, onSaved, _ -> onSaved() },
                    onFinished = {},
                    mode = IntroductionMode.Editor,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
    }

    private fun shows(id: Int): Boolean =
        rule.onAllNodesWithText(title(id)).fetchSemanticsNodes().isNotEmpty()

    /** Um lançamento curto (um quinto do ecrã) e rápido, a começar em [fromY] (fração da altura). */
    private fun flick(fromY: Float) {
        rule.onNodeWithTag("flow").performTouchInput {
            val start = height * fromY
            swipeUp(startY = start, endY = start - height * 0.2f, durationMillis = 90)
        }
        rule.mainClock.advanceTimeBy(1_500)
    }

    @Test
    fun flickFromTheHintAdvances() {
        setEditor()
        assertTrue(shows(R.string.introduction_2_title))
        flick(fromY = 0.93f)
        assertTrue("Introduction 3 depois do lançamento no indicador", shows(R.string.introduction_3_title))
    }

    @Test
    fun flickInsideAScrollAreaAdvances() {
        setEditor()
        flick(fromY = 0.93f)
        assertTrue(shows(R.string.introduction_3_title))
        // No meio da página do nome (área com scroll): a página só leva o que sobra do scroll.
        flick(fromY = 0.6f)
        assertTrue("Introduction 4 depois do lançamento na área com scroll", shows(R.string.introduction_4_title))
    }

    @Test
    fun theHourWheelKeepsItsGesture() {
        setEditor()
        rule.onNodeWithContentDescription(title(R.string.introduction_2_hour)).performTouchInput {
            swipeUp(startY = bottom - 2f, endY = top - height, durationMillis = 90)
        }
        rule.mainClock.advanceTimeBy(1_500)
        assertTrue("a página não mudou ao rodar a hora", shows(R.string.introduction_2_title))
    }
}
