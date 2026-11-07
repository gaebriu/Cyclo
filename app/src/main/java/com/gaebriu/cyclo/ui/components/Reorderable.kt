package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSpring
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Reordenar com long press e arrastar (Saved routines, Feed, Tracking). Beta 2.0: reescrito.
 *
 * Porque é que antes havia saltos:
 * - a ordem nova mudava o layout e o deslocamento que segurava cada vizinho no sítio antigo só
 *   entrava num fotograma seguinte (num `launch { snapTo }`): durante um fotograma o cartão via-se
 *   já no lugar novo e depois voltava atrás. O mesmo ao largar (`dropOffset`);
 * - agarrar um cartão a meio de um deslize ignorava esse deslize (saltava para o lugar do layout);
 * - no scroll automático, a posição do dedo na coluna lia coordenadas de um fotograma atrás;
 * - um só "lift" partilhado: largar um cartão e agarrar outro fazia o primeiro saltar; a mola de
 *   pousar passava de 0 e o cartão ficava, por instantes, mais pequeno do que o normal.
 *
 * Agora:
 * - Cada elemento tem a sua posição VISUAL absoluta (o topo na coluna), as suas molas (posição,
 *   levantar, carregar e gel) e um só relógio para todos ([ReorderState] corre a física fotograma a
 *   fotograma). O layout coloca cada elemento no seu lugar e a camada dele desloca-o até à posição
 *   visual, calculada no MESMO passo em que o lugar muda: nunca há um fotograma no sítio errado.
 * - Mudar de lugar (arrastar, apagar, repor, filtrar) só muda o alvo da mola: o elemento parte de
 *   onde está, com a velocidade que tem (um movimento contínuo, mesmo se interrompido).
 * - O lugar de destino é o mais perto da posição do arrastado (o bordo da frente a passar metade do
 *   vizinho), com histerese: o dedo a tremer na fronteira não troca e volta a trocar; um arrastar
 *   rápido salta vários lugares de uma vez, no mesmo fotograma.
 * - Toque: o cartão afunda ligeiramente enquanto se segura ([PressDepth]); ao confirmar o long
 *   press, levanta-se ([CycloSprings.Lift]) a partir daí, com sombra; ao largar, encaixa no lugar com
 *   [CycloSprings.Drop] a partir da velocidade do dedo e pousa sem passar do tamanho normal.
 * - O levantar, a sombra e o gel ficam na SUPERFÍCIE (o cartão: [surface]); o resto do elemento (o
 *   título do bloco do Feed, o espaço entre cartões) só se desloca.
 * - Sem animações no sistema: as trocas e o pousar são imediatos e nada cresce nem estica.
 */

/** Uma mola simples (posição, velocidade, alvo), passo a passo. */
internal class SpringValue(initial: Float) {
    var value by mutableFloatStateOf(initial)
    var velocity = 0f
    var target = initial

    val settled: Boolean get() = value == target && velocity == 0f

    fun snap(to: Float) {
        value = to
        target = to
        velocity = 0f
    }

    /** Avança [dt] segundos com [spring] (sub-passos de 4 ms, estável com qualquer frame rate). */
    fun step(dt: Float, spring: CycloSpring, restDelta: Float, restVelocity: Float) {
        if (settled) return
        val omega = (2.0 * PI / spring.response).toFloat()
        val k = omega * omega
        val c = 2f * spring.dampingFraction * omega
        var x = value
        var v = velocity
        var left = dt
        while (left > 0f) {
            val h = min(left, MaxSubstepS)
            v += (-k * (x - target) - c * v) * h
            x += v * h
            left -= h
        }
        if (abs(x - target) < restDelta && abs(v) < restVelocity) {
            x = target
            v = 0f
        }
        value = x
        velocity = v
    }
}

/** O estado de movimento de um elemento. */
internal class ItemMotion {
    /** O topo visual (coordenadas da coluna); NaN até ao primeiro layout. */
    val top = SpringValue(Float.NaN)

    /** O lugar do layout atual (topo) e o índice na ordem mostrada. */
    var slot = Float.NaN
    var index = -1

    /** Quando a mola do topo pode começar (cascata das mudanças da lista), em ns do relógio de frames. */
    var startAtNanos = 0L
    var topSpring: CycloSpring = CycloSprings.Reflow

    /** Levantado (0 a 1): escala e sombra. */
    val lift = SpringValue(0f)

    /** Carregado (0 a 1): o cartão afunda enquanto se segura, antes do long press. */
    val press = SpringValue(0f)

    /** Quando o "carregar" começa a aparecer (depois de um instante, para o scroll não o acender). */
    var pressAtNanos = Long.MAX_VALUE

    /** O gel: quanto estica na direção do movimento (fração da altura). */
    val stretch = SpringValue(0f)

    /** O ponto agarrado, na superfície (para a escala e o gel ficarem à volta do dedo). */
    var grabOrigin = TransformOrigin.Center

    var surface: LayoutCoordinates? = null

    /** A área que se agarra (as posições do dedo vêm no espaço dela). */
    var handle: LayoutCoordinates? = null
}

/**
 * O estado partilhado de uma lista reordenável: quem está a ser arrastado e onde, a ordem durante o
 * arrastar e a física de todos os elementos.
 */
@Stable
class ReorderState internal constructor() {
    /** A chave do elemento arrastado (null = nenhum). */
    var draggingKey by mutableStateOf<Any?>(null)
        internal set

    val isDragging: Boolean get() = draggingKey != null

    /** O elemento acabado de largar (fica por cima dos outros até assentar). */
    internal var droppingKey by mutableStateOf<Any?>(null)

    /** O topo do arrastado (coordenadas da coluna): segue o dedo. */
    internal var desiredTop by mutableFloatStateOf(0f)

    /** A ordem durante o arrastar (e logo depois de largar, até os dados chegarem). */
    internal var dragOrder by mutableStateOf<List<Any>?>(null)

    internal val motions = HashMap<Any, ItemMotion>()
    internal val heights = HashMap<Any, Float>()

    /** A ordem mostrada no último layout. */
    internal var shownOrder: List<Any> = emptyList()

    // O dedo: na janela, e o que é preciso para o passar para a coluna sem esperar pelo layout.
    internal var fingerWindowY = 0f
    internal var columnWindowTopAtStart = 0f
    internal var scrollAtStart = 0
    internal var grabOffset = 0f
    internal var columnCoordinates: LayoutCoordinates? = null

    /** Acorda o relógio da física (um elemento tem uma mola por acabar, ou começou um arrastar). */
    internal val wake = Channel<Unit>(Channel.CONFLATED)

    internal fun motion(key: Any): ItemMotion = motions.getOrPut(key) { ItemMotion() }

    internal fun requestFrame() {
        wake.trySend(Unit)
    }
}

@Composable
fun rememberReorderState(): ReorderState = remember { ReorderState() }

/**
 * Uma coluna de elementos reordenáveis com long press e arrastar (ver o comentário no início do
 * ficheiro para a física).
 *
 * - Perto do topo ou do fundo do ecrã, a página faz scroll sozinha (mais depressa quanto mais perto);
 *   o arrastado fica exatamente debaixo do dedo.
 * - Hápticos: DRAG_START ao levantar, um tique por cada lugar trocado, GESTURE_END ao pousar.
 * - Quando a lista muda por outra razão (apagar, repor, filtrar), os elementos deslizam para o novo
 *   lugar, em cascata subtil a partir do sítio da mudança.
 * - TalkBack: cada elemento tem as ações "Mover para cima" e "Mover para baixo".
 *
 * @param itemContent o conteúdo de cada elemento: [handle] vai na área que se pode agarrar (long
 *        press) e [surface] no cartão que se levanta (escala, sombra e gel). Podem ser o mesmo nó.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    state: ReorderState = rememberReorderState(),
    enabled: Boolean = true,
    scrollState: ScrollState? = null,
    autoScrollEdges: Pair<Dp, Dp> = 160.dp to 160.dp,
    itemContent: @Composable (item: T, handle: Modifier, surface: Modifier) -> Unit,
) {
    val effects = LocalVisualEffects.current
    val animations by rememberUpdatedState(effects.animationsEnabled)
    val view = LocalView.current
    val density = LocalDensity.current
    val latestItems by rememberUpdatedState(items)
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestScroll by rememberUpdatedState(scrollState)

    // Depois de largar, mantém-se a nova ordem até os dados a trazerem (ou até um tempo limite, se
    // a escrita falhar: aí a lista volta à ordem antiga, a deslizar).
    val itemKeys = items.map(key)
    val pending = state.dragOrder
    if (pending != null && !state.isDragging && pending == itemKeys) state.dragOrder = null
    LaunchedEffect(pending, state.isDragging) {
        if (pending != null && !state.isDragging) {
            delay(PendingOrderTimeoutMs)
            state.dragOrder = null
        }
    }
    val byKey = items.associateBy(key)

    val px = with(density) { 1.dp.toPx() }
    val topEdge = with(density) { autoScrollEdges.first.toPx() }
    val bottomEdge = with(density) { autoScrollEdges.second.toPx() }
    val maxScrollSpeed = with(density) { CycloMotion.DragAutoScrollMaxDp.dp.toPx() }
    val hysteresis = with(density) { SwapHysteresisDp.dp.toPx() }
    val maxStretchPx = with(density) { MaxDragStretchDp.dp.toPx() }
    val velocityForMax = with(density) { StretchVelocityForMaxDp.dp.toPx() }

    /** O dedo em coordenadas da coluna, sem esperar pelo layout (o scroll entra pela diferença). */
    fun fingerInColumn(): Float =
        state.fingerWindowY - state.columnWindowTopAtStart + ((latestScroll?.value ?: 0) - state.scrollAtStart)

    /**
     * O lugar do arrastado: o índice cujo topo (com os outros pela ordem) fica mais perto do topo
     * dele, com histerese ([hysteresis]): só muda se o lugar novo estiver claramente mais perto.
     */
    fun updateTarget() {
        val dragged = state.draggingKey ?: return
        val order = state.dragOrder ?: return
        val current = order.indexOf(dragged)
        if (current < 0) return
        val others = order.filter { it != dragged }
        var best = current
        var top = 0f
        var bestDistance = Float.MAX_VALUE
        var currentDistance = Float.MAX_VALUE
        for (i in 0..others.size) {
            val d = abs(state.desiredTop - top)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
            if (i == current) currentDistance = d
            if (i < others.size) top += state.heights[others[i]] ?: 0f
        }
        if (best == current || bestDistance + hysteresis >= currentDistance) return
        state.dragOrder = others.toMutableList().apply { add(best, dragged) }
        CycloHaptics.reorderStep(view)
        // Um pequeno abanão no gel a cada troca (para baixo estica, para cima achata).
        if (animations) state.motion(dragged).stretch.velocity -= (if (best > current) 1 else -1) * SwapStretchImpulse
        state.requestFrame()
    }

    // O relógio da física: corre enquanto houver molas por acabar ou um arrastar (gel, scroll).
    LaunchedEffect(state) {
        var lastNanos = 0L
        var lastDesired = Float.NaN
        var smoothVelocity = 0f
        while (true) {
            state.wake.receive()
            lastNanos = 0L
            lastDesired = Float.NaN
            smoothVelocity = 0f
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0.001f, 0.05f)
                lastNanos = now
                val dragged = state.draggingKey

                // Scroll automático perto das pontas (rampa suave, mais depressa quanto mais perto).
                val scroll = latestScroll
                if (dragged != null && scroll != null) {
                    val y = state.fingerWindowY
                    val screen = view.height.toFloat()
                    val raw = when {
                        y < topEdge -> -((topEdge - y) / topEdge).coerceIn(0f, 1f)
                        y > screen - bottomEdge -> ((y - (screen - bottomEdge)) / bottomEdge).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val speed = maxScrollSpeed * raw * abs(raw) * (dt * 60f)
                    if (abs(speed) > 0.5f) {
                        scroll.scrollBy(speed)
                        state.desiredTop = fingerInColumn() - state.grabOffset
                        updateTarget()
                    }
                }

                var busy = dragged != null
                state.motions.forEach { (itemKey, m) ->
                    if (m.top.value.isNaN()) return@forEach
                    // Posição: o arrastado está no dedo; os outros seguem a mola até ao lugar.
                    if (itemKey == dragged) {
                        m.top.snap(state.desiredTop)
                    } else if (!m.top.settled) {
                        if (now >= m.startAtNanos) m.top.step(dt, m.topSpring, RestPx * px, RestVelocityPx * px)
                        busy = true
                    }
                    // Carregar: aparece depois de um instante (o scroll não acende os cartões).
                    if (m.pressAtNanos != Long.MAX_VALUE && now >= m.pressAtNanos) {
                        m.press.target = 1f
                        m.pressAtNanos = Long.MAX_VALUE
                    }
                    if (!m.press.settled) {
                        m.press.step(dt, PressSpring, 0.001f, 0.01f)
                        busy = true
                    }
                    if (!m.lift.settled) {
                        m.lift.step(dt, if (m.lift.target > 0f) CycloSprings.Lift else LiftDownSpring, 0.001f, 0.01f)
                        busy = true
                    }
                    if (m.pressAtNanos != Long.MAX_VALUE) busy = true
                }

                // Gel do arrastado: a velocidade (suavizada) dá o esticão; a mola GelPressY leva-o lá.
                if (dragged != null) {
                    val m = state.motion(dragged)
                    val height = state.heights[dragged]?.takeIf { it > 0f }
                    if (animations && height != null) {
                        if (!lastDesired.isNaN()) {
                            val instant = (state.desiredTop - lastDesired) / dt
                            smoothVelocity += (instant - smoothVelocity) * (1f - exp(-dt / StretchVelocitySmoothingS))
                        }
                        val cap = min(MaxDragStretch, maxStretchPx / height)
                        m.stretch.target = (smoothVelocity / velocityForMax).coerceIn(-1f, 1f) * cap
                        m.stretch.step(dt, CycloSprings.GelPressY, 0.0001f, 0.001f)
                        m.stretch.value = m.stretch.value.coerceIn(-cap * 1.5f, cap * 1.5f)
                    }
                    lastDesired = state.desiredTop
                }
                // Os outros: o gel volta à forma (GelReleaseY, um pequeno abanão a partir da velocidade).
                state.motions.forEach { (itemKey, m) ->
                    if (itemKey != dragged && !m.stretch.settled) {
                        m.stretch.target = 0f
                        m.stretch.step(dt, CycloSprings.GelReleaseY, 0.0001f, 0.001f)
                        busy = true
                    }
                }
                // O que acabou de pousar deixa de estar por cima quando assenta.
                state.droppingKey?.let { k ->
                    val m = state.motions[k]
                    if (m == null || (m.top.settled && m.lift.settled)) state.droppingKey = null
                }
                if (!busy) break
            }
        }
    }

    fun finishDrag(itemKey: Any, releaseVelocity: Float) {
        if (state.draggingKey != itemKey) return
        val m = state.motion(itemKey)
        val order = state.dragOrder ?: state.shownOrder
        state.droppingKey = itemKey
        state.draggingKey = null
        CycloHaptics.drop(view)
        if (animations) {
            // Encaixa a partir de onde está e com a velocidade do dedo.
            m.top.snap(state.desiredTop)
            m.top.velocity = releaseVelocity
            m.top.target = if (m.slot.isNaN()) state.desiredTop else m.slot
            m.topSpring = CycloSprings.Drop
            m.startAtNanos = 0L
            m.lift.target = 0f
            m.press.target = 0f
        } else {
            m.top.snap(if (m.slot.isNaN()) state.desiredTop else m.slot)
            m.lift.snap(0f)
            m.press.snap(0f)
            m.stretch.snap(0f)
        }
        state.requestFrame()
        latestOnReorder(order.mapNotNull { k -> latestItems.firstOrNull { key(it) == k } })
    }

    Layout(
        modifier = modifier.onGloballyPositioned { state.columnCoordinates = it },
        content = {
            items.forEachIndexed { index, item ->
                val itemKey = key(item)
                key(itemKey) {
                    val m = remember(itemKey) { state.motion(itemKey) }
                    val moveUp = if (index > 0) CustomAccessibilityAction(moveUpLabel) {
                        reorderTo(itemKeys, byKey, index, index - 1) { latestOnReorder(it) }
                        true
                    } else null
                    val moveDown = if (index < items.lastIndex) CustomAccessibilityAction(moveDownLabel) {
                        reorderTo(itemKeys, byKey, index, index + 1) { latestOnReorder(it) }
                        true
                    } else null
                    val handle = if (!enabled) Modifier else Modifier.pointerInput(itemKey) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // O cartão afunda um pouco enquanto se segura (não sobre um botão, que
                            // já tem o seu gel), depois de um instante para o scroll não o acender.
                            if (animations && !down.isConsumed && state.draggingKey == null) {
                                m.pressAtNanos = System.nanoTime() + PressDelayNanos
                                state.requestFrame()
                            }
                            val longPress = awaitLongPressOrCancellation(down.id)
                            val column = state.columnCoordinates?.takeIf { it.isAttached }
                            val handleCoordinates = m.handle?.takeIf { it.isAttached }
                            if (longPress == null || state.draggingKey != null || column == null ||
                                handleCoordinates == null || m.top.value.isNaN()
                            ) {
                                releasePress(m)
                                state.requestFrame()
                                return@awaitEachGesture
                            }
                            // Levanta a partir de onde o cartão está (mesmo a meio de um deslize). As
                            // posições do dedo vêm no espaço do handle; passam pela janela.
                            val fingerWindow = handleCoordinates.localToWindow(longPress.position)
                            state.fingerWindowY = fingerWindow.y
                            state.columnWindowTopAtStart = column.positionInWindow().y
                            state.scrollAtStart = latestScroll?.value ?: 0
                            state.dragOrder = state.shownOrder.ifEmpty { latestItems.map(key) }
                            val visualTop = m.top.value
                            state.grabOffset = fingerInColumn() - visualTop
                            state.desiredTop = visualTop
                            // O ponto agarrado na superfície: a escala e o gel ficam à volta dele.
                            m.surface?.takeIf { it.isAttached }?.let { surface ->
                                val inSurface = surface.windowToLocal(fingerWindow)
                                m.grabOrigin = TransformOrigin(
                                    (inSurface.x / surface.size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    (inSurface.y / surface.size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                )
                            }
                            state.draggingKey = itemKey
                            CycloHaptics.dragStart(view)
                            if (animations) {
                                m.lift.target = 1f
                                m.press.target = 0f
                                m.pressAtNanos = Long.MAX_VALUE
                            } else {
                                m.lift.snap(1f)
                                m.press.snap(0f)
                            }
                            state.requestFrame()

                            val tracker = VelocityTracker()
                            tracker.addPosition(longPress.uptimeMillis, Offset(0f, state.fingerWindowY))
                            val ended = drag(longPress.id) { change ->
                                change.consume()
                                val window = m.handle?.takeIf { it.isAttached }?.localToWindow(change.position) ?: return@drag
                                state.fingerWindowY = window.y
                                tracker.addPosition(change.uptimeMillis, Offset(0f, window.y))
                                state.desiredTop = fingerInColumn() - state.grabOffset
                                updateTarget()
                            }
                            val velocity = if (ended) tracker.calculateVelocity().y else 0f
                            finishDrag(itemKey, velocity)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .layoutId(itemKey)
                            .semantics { customActions = listOfNotNull(moveUp, moveDown) },
                    ) {
                        itemContent(
                            item,
                            handle.then(Modifier.onGloballyPositioned { m.handle = it }),
                            surfaceModifier(m, animations),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.associate { (it.layoutId ?: Unit) to it.measure(childConstraints) }
        val keys = placeables.keys.toList()
        // A ordem mostrada: a do arrastar (com os que entretanto apareceram no fim) ou a dos dados.
        val drag = state.dragOrder
        val order = drag?.filter { it in placeables }?.let { kept -> kept + keys.filterNot { it in kept } } ?: keys
        val width = placeables.values.maxOfOrNull { it.width }?.coerceIn(constraints.minWidth, constraints.maxWidth) ?: constraints.minWidth
        var total = 0
        val slots = HashMap<Any, Int>(order.size)
        order.forEach { k ->
            slots[k] = total
            val h = placeables.getValue(k).height
            state.heights[k] = h.toFloat()
            total += h
        }
        // Onde a lista mudou (para a cascata das deslocações que não vêm do arrastar).
        val previous = state.shownOrder
        val changeStart = if (previous == order) 0 else order.indices.firstOrNull { it >= previous.size || previous[it] != order[it] } ?: 0
        val structural = previous != order
        state.shownOrder = order
        state.motions.keys.retainAll(placeables.keys)
        state.heights.keys.retainAll(placeables.keys)
        if (state.draggingKey != null && state.draggingKey !in placeables) state.draggingKey = null

        layout(width, total.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            val dragged = state.draggingKey
            val dropping = state.droppingKey
            val nowNanos = System.nanoTime()
            var anyMoving = false
            order.forEachIndexed { index, k ->
                val placeable = placeables.getValue(k)
                val slot = slots.getValue(k)
                val m = state.motion(k)
                val slotF = slot.toFloat()
                when {
                    // Primeiro layout deste elemento: aparece logo no lugar.
                    m.top.value.isNaN() -> m.top.snap(slotF)
                    m.slot != slotF && k != dragged -> {
                        if (animations && (structural || state.isDragging) && !m.slot.isNaN()) {
                            // Mudou de lugar: a posição visual fica onde está e a mola leva-a ao novo
                            // lugar, a partir da velocidade que tinha (nada salta).
                            m.top.target = slotF
                            if (m.top.value == slotF && m.top.velocity == 0f) {
                                m.top.snap(slotF)
                            } else {
                                m.topSpring = if (k == dropping) CycloSprings.Drop else CycloSprings.Reflow
                                val stagger = if (state.isDragging || k == dropping) 0 else (index - changeStart).coerceIn(0, MaxStaggerSteps)
                                m.startAtNanos = if (stagger == 0) 0L else nowNanos + stagger * StaggerNanos
                                anyMoving = true
                            }
                        } else {
                            // Só mudou o tamanho de algo acima (ou sem animações): acompanha logo.
                            m.top.snap(slotF)
                        }
                    }
                }
                m.slot = slotF
                m.index = index
                val z = if (k == dragged || k == dropping) 1f else 0f
                placeable.placeWithLayer(0, slot, zIndex = z) {
                    val visual = if (k == state.draggingKey) state.desiredTop else m.top.value
                    translationY = visual - slot
                }
            }
            if (anyMoving) state.requestFrame()
        }
    }
}


/** Desiste do toque: o cartão volta à superfície. */
private fun releasePress(m: ItemMotion) {
    m.pressAtNanos = Long.MAX_VALUE
    m.press.target = 0f
}

/**
 * O que se levanta: a superfície escala (afunda ao carregar, cresce ao levantar), ganha sombra e
 * estica como gel, à volta do ponto agarrado. Os vizinhos a deslizar também esticam um pouco, pela
 * velocidade da mola deles. Lida só na camada (sem recompor nem refazer o layout).
 */
@Composable
private fun surfaceModifier(m: ItemMotion, animations: Boolean): Modifier {
    val radius = with(LocalDensity.current) { 15.dp.toPx() }
    return Modifier
        .onGloballyPositioned { m.surface = it }
        .graphicsLayer {
            val lift = m.lift.value.coerceIn(0f, 1.2f)
            val press = m.press.value.coerceIn(0f, 1f)
            // Gel: o do arrastado (e o abanão ao pousar) ou, nos vizinhos, o da velocidade do deslize
            // (ler a posição faz esta camada acompanhar a mola fotograma a fotograma).
            @Suppress("UNUSED_VARIABLE") val position = m.top.value
            val own = if (animations) m.stretch.value else 0f
            val neighbour = if (animations && lift <= 0f && own == 0f) {
                val cap = min(NeighbourStretch, NeighbourMaxStretchDp.dp.toPx() / size.height.coerceAtLeast(1f))
                (abs(m.top.velocity) / StretchVelocityForMaxDp.dp.toPx()).coerceAtMost(1f) * cap
            } else {
                0f
            }
            val s = own + neighbour
            if (lift <= 0f && press <= 0f && s == 0f) return@graphicsLayer
            val scale = (1f - PressDepth * press) * (1f + (CycloMotion.DragLiftScale - 1f) * lift)
            // Estica na direção do movimento e estreita metade disso (o volume quase constante).
            scaleX = scale * (1f - s * 0.5f)
            scaleY = scale * (1f + s)
            transformOrigin = if (lift > 0f || press > 0f || own != 0f) m.grabOrigin else TransformOrigin.Center
            if (lift > 0f) {
                shadowElevation = 18.dp.toPx() * lift.coerceIn(0f, 1f)
                shape = SmoothRoundedCornerShape.px(radius)
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
            }
        }
}

/** Mexe um elemento (ações do TalkBack) e guarda logo a nova ordem. */
private fun <T> reorderTo(keys: List<Any>, byKey: Map<Any, T>, from: Int, to: Int, onReorder: (List<T>) -> Unit) {
    val order = keys.toMutableList()
    order.add(to, order.removeAt(from))
    onReorder(order.mapNotNull { byKey[it] })
}

/** O cartão afunda isto (fração) enquanto se segura antes do long press. */
private const val PressDepth = 0.018f

/** O "afundar" começa só depois disto (o scroll e os toques rápidos não acendem os cartões). */
private const val PressDelayNanos = 110_000_000L

/** A mola do afundar: lenta o suficiente para acompanhar a espera do long press, sem ressalto. */
private val PressSpring = CycloSpring(response = 0.45f, dampingFraction = 1f)

/** Pousar: a escala volta ao normal sem passar abaixo dele. */
private val LiftDownSpring = CycloSpring(response = 0.32f, dampingFraction = 1f)

/** Sub-passo máximo da física (s). */
private const val MaxSubstepS = 0.004f

/** Repouso: a menos de meio dp e a menos de 6 dp/s, a mola acaba. */
private const val RestPx = 0.5f
private const val RestVelocityPx = 6f

/** Histerese do lugar de destino (dp): o lugar novo tem de estar isto mais perto do que o atual. */
private const val SwapHysteresisDp = 12f

/** Gel do arrastado: no máximo 5 % da altura e 6 dp (como os limites em dp de cycloPressable). */
private const val MaxDragStretch = 0.05f
private const val MaxDragStretchDp = 6f

/** A velocidade (dp/s) a que o gel chega ao máximo. */
private const val StretchVelocityForMaxDp = 2200f

/** Suavização da velocidade do arrastado (s), para o gel não tremer com o ruído do toque. */
private const val StretchVelocitySmoothingS = 0.06f

/** O abanão de cada troca de lugar (fração da altura por segundo). */
private const val SwapStretchImpulse = 0.45f

/** Gel dos vizinhos a deslizar: no máximo 2 % e 3 dp. */
private const val NeighbourStretch = 0.02f
private const val NeighbourMaxStretchDp = 3f

/** Depois de largar, quanto tempo se espera pelos dados com a nova ordem. */
private const val PendingOrderTimeoutMs = 1500L

/** Cascata das deslocações: o atraso entre um elemento e o seguinte, e quantos contam. */
private const val StaggerNanos = 18_000_000L
private const val MaxStaggerSteps = 6
