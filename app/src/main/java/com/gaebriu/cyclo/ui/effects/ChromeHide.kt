package com.gaebriu.cyclo.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Os botões de navegação saem de cena enquanto o dedo arrasta na vertical, para cima ou para baixo
 * (Cyclo 3.1): no Feed e nas rotinas guardadas o mini-menu-group e a navigation-bar; nas Settings o
 * close-b e o back-b. Voltam
 * assim que o dedo levanta. É um "ecrã inteiro" passageiro: só a opacidade muda, o layout fica.
 *
 * Um só estado por ecrã: quem tem o gesto ([trackChromeHide], no contentor) não o consome, só o vê
 * passar (passagem Initial), por isso o scroll, os gestos das páginas e os toques continuam iguais.
 * Os elementos leem a opacidade só ao desenhar ([chromeHidden]): nada se recompõe.
 */
@Stable
class ChromeHide internal constructor(private val scope: CoroutineScope) {
    internal val hidden = Animatable(0f)

    internal fun hide() {
        scope.launch { hidden.animateTo(1f, CycloSprings.ContentOut.float) }
    }

    internal fun show() {
        // Voltar é mais calmo do que sair (como os ecrãs: sair é mais rápido do que entrar).
        scope.launch { hidden.animateTo(0f, CycloSprings.VeilOut.float) }
    }
}

@Composable
fun rememberChromeHide(): ChromeHide {
    val scope = rememberCoroutineScope()
    return remember(scope) { ChromeHide(scope) }
}

/**
 * Vê os gestos do contentor: um arrasto vertical, para cima ou para baixo (passado o touch slop, mais
 * vertical do que horizontal), esconde os botões até o último dedo levantar. Não consome nada.
 */
fun Modifier.trackChromeHide(state: ChromeHide): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var hiding = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!hiding) {
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (abs(dy) > slop && abs(dy) > abs(dx)) {
                        hiding = true
                        state.hide()
                    }
                }
            }
            if (event.changes.none { it.pressed }) break
        }
        if (hiding) state.show()
    }
}

/** Um botão que sai de cena com [state] (só a opacidade, lida ao desenhar). */
fun Modifier.chromeHidden(state: ChromeHide): Modifier = graphicsLayer { alpha = 1f - state.hidden.value }
