package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import com.gaebriu.cyclo.ui.theme.projectedEnd
import kotlin.math.abs

/** Velocidade (páginas por segundo) a partir da qual um lançamento muda de página, mesmo curto. */
internal const val IntroFlingVelocity = 0.5f

/** Para onde o lançamento levaria a página (ver [projectedEnd]): passa daqui, muda. */
internal const val IntroCommitAt = 0.5f

/**
 * O que um gesto decide ao largar (beta-3.2): 1 = a página seguinte, -1 = a anterior, 0 = volta ao
 * sítio. [offset] é quanto o dedo levou a página (em páginas, positivo = para a seguinte) e
 * [velocity] a velocidade ao largar (páginas/s, o mesmo sentido). Um lançamento rápido no sentido em
 * que a página já ia muda sempre; senão, conta onde a página pararia a desacelerar como no iOS
 * ([projectedEnd]). Largar a voltar para trás (velocidade contra o deslocamento) cancela.
 */
internal fun introSwipeDecision(offset: Float, velocity: Float): Int {
    if (velocity > IntroFlingVelocity && offset > 0f) return 1
    if (velocity < -IntroFlingVelocity && offset < 0f) return -1
    val projected = projectedEnd(offset, velocity)
    return when {
        projected > IntroCommitAt -> 1
        projected < -IntroCommitAt -> -1
        else -> 0
    }
}

/**
 * O gesto vertical das Introduction e do editor de rotinas (beta-3.2): arrastar para cima leva à
 * página seguinte, para baixo à anterior, sempre a seguir o dedo e a acabar numa página inteira.
 *
 * Quem tem o gesto, por ordem:
 * 1. Os controlos com física vertical própria (as rodas da hora do Introduction 2) ficam com ele:
 *    consomem os eventos e nada daqui os vê.
 * 2. Nas áreas com scroll, o conteúdo anda primeiro; a página só se mexe com o que SOBRA na ponta
 *    (nested scroll) e só se o arrasto começou já na ponta: um arrasto que leva o texto até ao fim
 *    nunca passa de página no mesmo gesto. Com a página a meio, o dedo move a página (não o texto).
 * 3. Fora do scroll (o "swipe up to continue", as margens), um detetor que só reclama o arrasto se
 *    nenhum filho o consumiu (como o swipe do Feed). Não começa na zona do gesto "início" do sistema.
 *
 * A física e as regras (que página, resistência, mola) ficam em quem o usa ([Target]).
 */
@Stable
class IntroSwipe(private val target: Target) {
    /** Quem move as páginas. Distâncias em páginas (a altura do ecrã), positivas para a seguinte. */
    interface Target {
        /** O dedo começou a levar as páginas: pára as molas. Falso = agora não se navega. */
        fun begin(): Boolean

        /** O dedo já levou as páginas [pages] desde o início do gesto. */
        fun drag(pages: Float)

        /** O dedo largou, a [pagesPerSecond]. */
        fun release(pagesPerSecond: Float)
    }

    private var height = 1f
    private var active = false
    private var travelled = 0f
    private var scrolledThisDrag = false

    private fun start(): Boolean {
        if (!target.begin()) return false
        active = true
        travelled = 0f
        return true
    }

    /** [fingerDy] em px, no sentido do dedo (negativo = para cima, para a seguinte). */
    private fun move(fingerDy: Float) {
        travelled -= fingerDy
        target.drag(travelled / height)
    }

    private fun end(fingerVy: Float) {
        if (!active) return
        active = false
        target.release(-fingerVy / height)
    }

    private val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!active || source != NestedScrollSource.UserInput) return Offset.Zero
            move(available.y)
            return Offset(0f, available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (consumed.y != 0f) scrolledThisDrag = true
            if (active || scrolledThisDrag || abs(available.y) < 0.5f || !start()) return Offset.Zero
            move(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            scrolledThisDrag = false
            if (!active) return Velocity.Zero
            end(available.y)
            return available
        }
    }

    /**
     * Liga o gesto a um contentor que ocupa o ecrã: o nested scroll das páginas e o arrasto fora
     * delas. Vai no contentor das páginas (pai das áreas com scroll e dos controlos fixos).
     */
    @Composable
    fun Modifier.introSwipe(): Modifier {
        val gestureInsets = WindowInsets.systemGestures
        return this
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .nestedScroll(connection)
            .pointerInput(this@IntroSwipe) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A zona do gesto "início"/voltar do sistema não se pode excluir: é dele.
                    val bottomZone = gestureInsets.getBottom(this@pointerInput)
                    if (down.position.y > size.height - bottomZone) return@awaitEachGesture
                    if (active) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var claimed = false
                    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (start()) {
                            claimed = true
                            change.consume()
                            move(overSlop)
                        }
                    } ?: return@awaitEachGesture
                    if (!claimed) return@awaitEachGesture
                    tracker.addPosition(drag.uptimeMillis, drag.position)
                    // Até largar. Conta também o movimento que chega no próprio "levantar" (com o
                    // sistema atrasado, o fim do gesto pode vir todo nele).
                    var completed = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == drag.id }
                        if (change == null || (change.isConsumed && change.pressed)) {
                            completed = false
                            break
                        }
                        val dy = change.positionChange().y
                        if (dy != 0f) move(dy)
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        if (!change.pressed) break
                    }
                    end(if (completed) tracker.calculateVelocity().y else 0f)
                }
            }
    }
}
