package com.gaebriu.cyclo.ui.components

import kotlin.math.min
import kotlin.math.exp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * O estado partilhado de uma lista reordenável: quem está a ser arrastado e onde, e as posições
 * de cada elemento (medidas no layout).
 */
@Stable
class ReorderState internal constructor() {
    /** A chave do elemento arrastado (null = nenhum). */
    var draggingKey by mutableStateOf<Any?>(null)
        internal set

    val isDragging: Boolean get() = draggingKey != null

    /** Onde o topo do elemento arrastado deve estar (coordenadas da coluna): segue o dedo. */
    internal var desiredTop by mutableFloatStateOf(0f)

    /** Quanto o elemento arrastado está "levantado" (0 a 1): escala e sombra. */
    internal val lift = Animatable(0f)

    /**
     * O gel do elemento arrastado: quanto se estica na direção do movimento (fração da altura; < 0
     * achata). Segue a velocidade do dedo com a mola [CycloSprings.GelPressY] e, ao largar, volta a 0
     * com [CycloSprings.GelReleaseY] (oscila um pouco e assenta), como o toque em [cycloPressable].
     */
    internal val stretch = Animatable(0f)
    internal var stretchVelocity = 0f

    /** Depois de largar: a distância que falta até ao lugar final (vai a 0 com a mola de encaixe). */
    internal val dropOffset = Animatable(0f)
    internal var droppingKey by mutableStateOf<Any?>(null)

    /** A ordem durante o arrastar (e logo depois de largar, até os dados chegarem). */
    internal var dragOrder by mutableStateOf<List<Any>?>(null)

    internal val heights = HashMap<Any, Float>()
    internal var columnCoordinates: LayoutCoordinates? = null
    internal var lastFingerWindow = Offset.Zero
    internal var grabOffset = 0f

    /** O topo de cada elemento na ordem [order], somando as alturas (sem esperar pelo layout). */
    internal fun topOf(order: List<Any>, key: Any): Float {
        var top = 0f
        for (k in order) {
            if (k == key) return top
            top += heights[k] ?: 0f
        }
        return top
    }

    internal fun fingerInColumn(): Float =
        columnCoordinates?.takeIf { it.isAttached }?.windowToLocal(lastFingerWindow)?.y ?: 0f
}

@Composable
fun rememberReorderState(): ReorderState = remember { ReorderState() }

/**
 * Uma coluna de elementos reordenáveis com long press e arrastar, com a física do Cyclo:
 *
 * - Long press: o elemento "levanta-se" (cresce [CycloMotion.DragLiftScale] e ganha sombra, com a
 *   mola [CycloSprings.Lift]) e o háptico DRAG_START; depois segue o dedo sem atraso (só uma
 *   translação na camada, sem recompor).
 * - Os outros abrem espaço com a mola [CycloSprings.Reflow] quando o arrastado passa o meio deles,
 *   com um tique háptico a cada troca de lugar.
 * - Perto do topo ou do fundo do ecrã, a página faz scroll sozinha (mais depressa quanto mais perto).
 * - Ao largar, o elemento encaixa no lugar com [CycloSprings.Drop], a partir de onde está e com o
 *   háptico do fim do gesto; a nova ordem vai para [onReorder].
 * - Quando a lista muda por outra razão (apagar, repor), os elementos que mudam de lugar deslizam
 *   para o novo lugar (técnica FLIP), em cascata subtil a partir do sítio da mudança.
 * - Sem animações no sistema, as trocas são imediatas e o elemento não cresce.
 * - TalkBack: cada elemento tem as ações "Mover para cima" e "Mover para baixo".
 *
 * @param itemContent o conteúdo de cada elemento; [handle] é o modificador que começa o arrastar
 *        (long press) e deve ir na área que se pode agarrar.
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
    itemContent: @Composable (item: T, handle: Modifier) -> Unit,
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestItems by rememberUpdatedState(items)
    val latestOnReorder by rememberUpdatedState(onReorder)

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
    val shownKeys = state.dragOrder?.filter { it in byKey }?.let { kept -> kept + itemKeys.filterNot { it in kept } } ?: itemKeys

    // Onde a lista mudou (para a cascata das deslocações).
    val previousKeys = remember { arrayOfNulls<List<Any>>(1) }
    val changeStart = remember(shownKeys) {
        val before = previousKeys[0]
        previousKeys[0] = shownKeys
        if (before == null) 0 else shownKeys.indices.firstOrNull { it >= before.size || before[it] != shownKeys[it] } ?: 0
    }

    fun move(from: Int, to: Int) {
        val order = (state.dragOrder ?: shownKeys).toMutableList()
        if (from !in order.indices || to !in order.indices || from == to) return
        order.add(to, order.removeAt(from))
        state.dragOrder = order
    }

    /** Um pequeno abanão no gel quando o arrastado muda de lugar ([direction] +1 desce, -1 sobe). */
    fun swapImpulse(direction: Int) {
        if (effects.animationsEnabled) state.stretchVelocity -= direction * SwapStretchImpulse
    }

    /** Troca o arrastado com o vizinho cujo meio ele passou (uma troca de cada vez). */
    fun checkSwap() {
        val dragged = state.draggingKey ?: return
        val order = state.dragOrder ?: return
        val index = order.indexOf(dragged)
        if (index < 0) return
        val height = state.heights[dragged] ?: return
        val center = state.desiredTop + height / 2f
        if (index < order.lastIndex) {
            val next = order[index + 1]
            val nextTop = state.topOf(order, next)
            if (center > nextTop + (state.heights[next] ?: 0f) / 2f) {
                move(index, index + 1)
                CycloHaptics.reorderStep(view)
                swapImpulse(+1)
                return
            }
        }
        if (index > 0) {
            val previous = order[index - 1]
            val previousTop = state.topOf(order, previous)
            if (center < previousTop + (state.heights[previous] ?: 0f) / 2f) {
                move(index, index - 1)
                CycloHaptics.reorderStep(view)
                swapImpulse(-1)
            }
        }
    }

    // Scroll automático perto das pontas, frame a frame, enquanto se arrasta.
    val topEdge = with(density) { autoScrollEdges.first.toPx() }
    val bottomEdge = with(density) { autoScrollEdges.second.toPx() }
    val maxSpeed = with(density) { CycloMotion.DragAutoScrollMaxDp.dp.toPx() }
    LaunchedEffect(state.draggingKey, scrollState) {
        if (state.draggingKey == null || scrollState == null) return@LaunchedEffect
        var lastFrame = 0L
        while (state.draggingKey != null) {
            val frame = withFrameNanos { it }
            val dt = if (lastFrame == 0L) 1f else ((frame - lastFrame) / 16_666_667f).coerceIn(0.5f, 3f)
            lastFrame = frame
            val y = state.lastFingerWindow.y
            val screen = view.height.toFloat()
            val speed = when {
                y < topEdge -> -maxSpeed * ((topEdge - y) / topEdge).coerceIn(0f, 1f)
                y > screen - bottomEdge -> maxSpeed * ((y - (screen - bottomEdge)) / bottomEdge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (abs(speed) > 0.5f) {
                scrollState.scrollBy(speed * dt)
                state.desiredTop = state.fingerInColumn() - state.grabOffset
                checkSwap()
            }
        }
    }

    // O gel enquanto se arrasta, fotograma a fotograma: a velocidade do elemento (suavizada) dá o
    // esticão alvo, limitado em dp como em cycloPressable, e uma mola (GelPressY) leva-o até lá. A
    // translação continua exatamente no dedo: o gel é só escala, à volta do ponto agarrado.
    val maxStretchPx = with(density) { MaxDragStretchDp.dp.toPx() }
    val velocityForMax = with(density) { StretchVelocityForMaxDp.dp.toPx() }
    LaunchedEffect(state.draggingKey, effects.animationsEnabled) {
        val dragged = state.draggingKey ?: return@LaunchedEffect
        if (!effects.animationsEnabled) {
            state.stretch.snapTo(0f)
            state.stretchVelocity = 0f
            return@LaunchedEffect
        }
        val omega = (2.0 * Math.PI / CycloSprings.GelPressY.response).toFloat()
        val zeta = CycloSprings.GelPressY.dampingFraction
        var x = state.stretch.value
        var lastNanos = 0L
        var lastTop = state.desiredTop
        var smoothVelocity = 0f
        while (state.draggingKey == dragged) {
            val now = withFrameNanos { it }
            if (lastNanos == 0L) {
                lastNanos = now
                lastTop = state.desiredTop
                continue
            }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = now
            val instant = (state.desiredTop - lastTop) / dt
            lastTop = state.desiredTop
            smoothVelocity += (instant - smoothVelocity) * (1f - exp(-dt / StretchVelocitySmoothingS))
            val height = state.heights[dragged]?.takeIf { it > 0f } ?: break
            val cap = min(MaxDragStretch, maxStretchPx / height)
            val target = (smoothVelocity / velocityForMax).coerceIn(-1f, 1f) * cap
            var v = state.stretchVelocity
            v += (-omega * omega * (x - target) - 2f * zeta * omega * v) * dt
            x = (x + v * dt).coerceIn(-cap * 1.5f, cap * 1.5f)
            state.stretchVelocity = v
            state.stretch.snapTo(x)
        }
    }

    Column(modifier = modifier.onGloballyPositioned { state.columnCoordinates = it }) {
        shownKeys.forEachIndexed { index, itemKey ->
            val item = byKey[itemKey] ?: return@forEachIndexed
            key(itemKey) {
                ReorderableItem(
                    itemKey = itemKey,
                    index = index,
                    stagger = (index - changeStart).coerceAtLeast(0),
                    state = state,
                    animationsEnabled = effects.animationsEnabled,
                    moveUp = if (index > 0) CustomAccessibilityAction(moveUpLabel) {
                        reorderTo(shownKeys, byKey, index, index - 1) { latestOnReorder(it) }
                        true
                    } else null,
                    moveDown = if (index < shownKeys.lastIndex) CustomAccessibilityAction(moveDownLabel) {
                        reorderTo(shownKeys, byKey, index, index + 1) { latestOnReorder(it) }
                        true
                    } else null,
                ) {
                    // As posições do dedo vêm no espaço da área que se agarra (já com a translação e a
                    // escala da camada): passam para a janela pelas coordenadas dela.
                    val handleCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    val itemCoordinates = { handleCoordinates[0]?.takeIf { it.isAttached } }
                    val handle = if (!enabled) Modifier else Modifier.onGloballyPositioned { handleCoordinates[0] = it }.pointerInput(itemKey) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val coordinates = itemCoordinates() ?: return@detectDragGesturesAfterLongPress
                                state.lastFingerWindow = coordinates.localToWindow(offset)
                                state.dragOrder = latestItems.map(key)
                                val order = state.dragOrder!!
                                val top = state.topOf(order, itemKey)
                                state.grabOffset = state.fingerInColumn() - top
                                state.desiredTop = top
                                state.draggingKey = itemKey
                                CycloHaptics.dragStart(view)
                                scope.launch { state.dropOffset.snapTo(0f) }
                                scope.launch {
                                    if (effects.animationsEnabled) state.lift.animateTo(1f, CycloSprings.Lift.float) else state.lift.snapTo(1f)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val coordinates = itemCoordinates() ?: return@detectDragGesturesAfterLongPress
                                state.lastFingerWindow = coordinates.localToWindow(change.position)
                                state.desiredTop = state.fingerInColumn() - state.grabOffset
                                checkSwap()
                            },
                            onDragEnd = {
                                finishDrag(state, itemKey, scope, effects.animationsEnabled, view) { order ->
                                    latestOnReorder(order.mapNotNull { k -> latestItems.firstOrNull { key(it) == k } })
                                }
                            },
                            onDragCancel = {
                                finishDrag(state, itemKey, scope, effects.animationsEnabled, view) { order ->
                                    latestOnReorder(order.mapNotNull { k -> latestItems.firstOrNull { key(it) == k } })
                                }
                            },
                        )
                    }
                    itemContent(item, handle)
                }
            }
        }
    }
}

/** Mexe um elemento (ações do TalkBack) e guarda logo a nova ordem. */
private fun <T> reorderTo(keys: List<Any>, byKey: Map<Any, T>, from: Int, to: Int, onReorder: (List<T>) -> Unit) {
    val order = keys.toMutableList()
    order.add(to, order.removeAt(from))
    onReorder(order.mapNotNull { byKey[it] })
}

/** Largou: encaixa no lugar a partir de onde está, pousa (sem sombra) e guarda a ordem se mudou. */
private fun finishDrag(
    state: ReorderState,
    itemKey: Any,
    scope: kotlinx.coroutines.CoroutineScope,
    animationsEnabled: Boolean,
    view: android.view.View,
    onReorder: (List<Any>) -> Unit,
) {
    if (state.draggingKey != itemKey) return
    val order = state.dragOrder ?: emptyList()
    val finalTop = state.topOf(order, itemKey)
    val from = state.desiredTop - finalTop
    state.droppingKey = itemKey
    state.draggingKey = null
    CycloHaptics.drop(view)
    scope.launch {
        state.dropOffset.snapTo(from)
        if (animationsEnabled) state.dropOffset.animateTo(0f, CycloSprings.Drop.float) else state.dropOffset.snapTo(0f)
        if (state.draggingKey == null) state.droppingKey = null
    }
    scope.launch {
        if (animationsEnabled) state.lift.animateTo(0f, CycloSprings.Drop.float) else state.lift.snapTo(0f)
    }
    // O gel solta-se: volta à forma com a mola de largar do toque (GelReleaseY), a partir da
    // velocidade que tinha, por isso abana um pouco e assenta.
    scope.launch {
        val velocity = state.stretchVelocity
        state.stretchVelocity = 0f
        if (animationsEnabled) {
            state.stretch.animateTo(0f, CycloSprings.GelReleaseY.float, initialVelocity = velocity)
        } else {
            state.stretch.snapTo(0f)
        }
    }
    onReorder(order)
}

/**
 * Um elemento da coluna: mede-se, desliza para o novo lugar quando a ordem muda (FLIP) e, se for o
 * arrastado, segue o dedo por cima dos outros.
 */
@Composable
private fun ReorderableItem(
    itemKey: Any,
    index: Int,
    stagger: Int,
    state: ReorderState,
    animationsEnabled: Boolean,
    moveUp: CustomAccessibilityAction?,
    moveDown: CustomAccessibilityAction?,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val placement = remember { Animatable(0f) }
    var layoutTop by remember { mutableFloatStateOf(Float.NaN) }
    var lastIndex by remember { mutableIntStateOf(index) }
    val latestStagger by rememberUpdatedState(stagger)
    val dragging = state.draggingKey == itemKey
    val dropping = state.droppingKey == itemKey
    val radius = with(LocalDensity.current) { 15.dp.toPx() }

    Box(
        modifier = Modifier
            .onPlaced { placed ->
                val top = placed.positionInParent().y
                state.heights[itemKey] = placed.size.height.toFloat()
                val previousTop = layoutTop
                layoutTop = top
                // Mudou de lugar na lista (não só de tamanho): desliza do sítio antigo para o novo.
                if (!previousTop.isNaN() && previousTop != top && index != lastIndex &&
                    state.draggingKey != itemKey && state.droppingKey != itemKey && animationsEnabled
                ) {
                    val delta = previousTop - top
                    scope.launch {
                        placement.snapTo(placement.value + delta)
                        if (!state.isDragging) delay(latestStagger.coerceAtMost(MaxStaggerSteps) * StaggerMs)
                        placement.animateTo(0f, CycloSprings.Reflow.float)
                    }
                }
                lastIndex = index
            }
            .zIndex(if (dragging || dropping) 1f else 0f)
            .graphicsLayer {
                val lift = state.lift.value.coerceIn(0f, 1.2f)
                translationY = when {
                    dragging -> state.desiredTop - layoutTop
                    dropping -> state.dropOffset.value
                    else -> placement.value
                }
                if (dragging || dropping) {
                    val scale = 1f + (CycloMotion.DragLiftScale - 1f) * lift
                    // Gel: estica na direção do movimento e estreita metade disso (volume quase
                    // constante), à volta do ponto agarrado, que fica debaixo do dedo.
                    val s = if (animationsEnabled) state.stretch.value else 0f
                    scaleX = scale * (1f - s * 0.5f)
                    scaleY = scale * (1f + s)
                    transformOrigin = TransformOrigin(0.5f, (state.grabOffset / size.height).coerceIn(0f, 1f))
                    shadowElevation = 18.dp.toPx() * lift.coerceIn(0f, 1f)
                    shape = SmoothRoundedCornerShape.px(radius)
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                } else if (animationsEnabled && placement.isRunning) {
                    // Os vizinhos que abrem espaço também são gel: alongam-se um pouco enquanto
                    // deslizam (pela velocidade da mola Reflow) e voltam à forma quando param.
                    val cap = min(NeighbourStretch, NeighbourMaxStretchDp.dp.toPx() / size.height.coerceAtLeast(1f))
                    val s = abs(placement.velocity / StretchVelocityForMaxDp.dp.toPx()).coerceAtMost(1f) * cap
                    scaleX = 1f - s * 0.5f
                    scaleY = 1f + s
                }
            }
            .semantics {
                customActions = listOfNotNull(moveUp, moveDown)
            },
    ) {
        content()
    }
}

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
private const val StaggerMs = 18L
private const val MaxStaggerSteps = 6
