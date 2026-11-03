package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.main.excludeFromPageSwipe
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.projectedEnd
import com.gaebriu.cyclo.ui.theme.rubberBand
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Menu que aparece ancorado a um campo: parte do canto de cima à esquerda do [anchor] com zoom
 * e fade (mola), e sai do mesmo modo. Tocar fora fecha-o ([onDismiss]). Se não couber por baixo,
 * sobe o suficiente para ficar [bottomMargin] acima do fundo do ecrã.
 *
 * Tem de estar num sítio que ocupe o ecrã todo. As coordenadas do [anchor] são as da janela; o
 * menu desconta onde o próprio host está (ex.: por baixo de uma barra de estado mais alta).
 *
 * @param alignEnd o menu alinha pela direita do [anchor] (e cresce a partir do canto de cima à
 *                 direita), para os botões encostados à direita do ecrã.
 */
@Composable
fun AnchoredMenuHost(
    visible: Boolean,
    anchor: () -> Rect,
    onDismiss: () -> Unit,
    bottomMargin: Dp,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effects = LocalVisualEffects.current
    // Largura (e opacidade) e altura têm molas próprias: a abrir, o menu escorre do campo (a altura
    // chega um pouco depois da largura e assenta com um ressalto pequeno, como um líquido); a
    // fechar, recolhe primeiro na altura, depressa e sem ressalto.
    val progress = remember { Animatable(0f) }
    val progressY = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        launch { progressY.animateTo(target, if (visible) CycloSprings.MenuPour.float else CycloSprings.MenuRetract.float) }
        // A abrir, uma mola com um ressalto muito leve; a fechar, mais rápida e sem ressalto.
        progress.animateTo(target, if (visible) CycloMotion.MenuOpenSpring else CycloMotion.MenuCloseSpring)
    }
    // Onde o host está na janela, para passar o [anchor] para as coordenadas dele.
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    // Já saiu por completo: não existe.
    if (!visible && progress.value <= 0f && progressY.value <= 0f) return

    // Aberto, o ecrã todo é do menu: arrastar na horizontal não muda de página por baixo dele
    // (antes, um dedo a deslizar no menu do Feed levava a página para as rotinas guardadas).
    Box(
        modifier = modifier
            .fillMaxSize()
            .excludeFromPageSwipe()
            .onGloballyPositioned { hostOrigin = it.positionInWindow() },
    ) {
        // Apanha os toques fora do menu.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visible) { detectTapGestures { if (visible) onDismiss() } },
        )
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    modifier = Modifier.graphicsLayer {
                        // A mola pode passar um pouco de 1: o zoom acompanha (ressalto subtil), o
                        // fade e o desfoque não.
                        val raw = progress.value
                        val p = raw.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
                        alpha = p
                        if (effects.animationsEnabled) {
                            scaleX = lerp(CycloMotion.MenuStartScale, 1f, raw.coerceAtLeast(0f))
                            scaleY = lerp(MenuStartScaleY, 1f, progressY.value.coerceAtLeast(0f))
                            // Ligeiro desfoque a abrir e a fechar (6 a 0), como os textos a aparecer.
                            val sigma = figmaBlurPx(FigmaBlur.MenuReveal) * (1f - p)
                            renderEffect = if (effects.blurEnabled && sigma > 0.5f) {
                                BlurEffect(sigma, sigma, TileMode.Decal)
                            } else {
                                null
                            }
                        }
                    },
                ) { content() }
            },
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val rect = anchor().translate(-hostOrigin)
                val lowest = constraints.maxHeight - bottomMargin.roundToPx() - placeable.height
                val x = if (alignEnd) rect.right.roundToInt() - placeable.width else rect.left.roundToInt()
                placeable.place(
                    x = x.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)),
                    y = rect.top.roundToInt().coerceAtMost(lowest).coerceAtLeast(0),
                )
            }
        }
    }
}

/**
 * Folha que sobe de baixo (todos os action-menu-big: abrir o GitHub, repor os dados, os dias da
 * repetição) e se fecha arrastando-a para baixo ou tocando fora. Fica [bottomMargin] acima do
 * fundo do ecrã, como o botão Next.
 *
 * A física é a mesma em todas: uma só posição contínua ([position]: 0 = aberta, 1 = escondida)
 * que o dedo move diretamente e as molas continuam.
 * - Abre com [CycloSprings.SheetOpen] (peso, ressalto quase invisível) e fecha com
 *   [CycloSprings.SheetClose] (mais rápida, sem ressalto).
 * - Ao largar, a mola parte com a velocidade do dedo, e o sítio para onde o lançamento iria
 *   ([projectedEnd]) decide se fecha ou volta: um gesto rápido e curto fecha, um arrastar lento a
 *   meio volta ao lugar; arrastada um terço do caminho, fecha (a não ser que o dedo a atire para cima).
 * - Para cima, resiste (rubber band) em vez de parar de repente.
 * - A opacidade só acompanha o início da subida (a folha não fica transparente a meio caminho).
 * - Sem animações, abre e fecha com um fade.
 *
 * Tem de estar num sítio que ocupe o ecrã todo.
 */
@Composable
fun BottomSheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    bottomMargin: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val effects = LocalVisualEffects.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // 0 = aberta, 1 = escondida (abaixo do ecrã); < 0 = puxada para cima (com resistência).
    val position = remember { Animatable(1f) }
    // A distância de aberta a escondida, em píxeis (a altura da folha mais a margem de baixo).
    var travel by remember { mutableFloatStateOf(0f) }
    // A velocidade do dedo ao largar (fração por segundo), para a mola seguinte continuar dela.
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val marginPx = with(density) { bottomMargin.toPx() }

    LaunchedEffect(visible) {
        val target = if (visible) 0f else 1f
        val velocity = releaseVelocity
        releaseVelocity = 0f
        if (!effects.animationsEnabled) {
            position.snapTo(target)
        } else {
            val spring = if (visible) CycloSprings.SheetOpen.float else CycloSprings.SheetClose.float
            position.animateTo(target, spring, initialVelocity = velocity)
        }
    }
    // Sem animações, a folha entra e sai com um fade (a posição salta).
    val fade = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, effects.animationsEnabled) {
        if (!effects.animationsEnabled) fade.animateTo(if (visible) 1f else 0f, CycloMotion.FadeSpring) else fade.snapTo(1f)
    }
    if (!visible && position.value >= 1f && (effects.animationsEnabled || fade.value <= 0f)) return

    // Aberta, o ecrã todo é da folha (como nos menus): o swipe das páginas não começa por baixo dela.
    Box(modifier = modifier.fillMaxSize().excludeFromPageSwipe()) {
        // Apanha os toques fora da folha.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visible) { detectTapGestures { if (visible) onDismiss() } },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(bottom = bottomMargin)
                .onSizeChanged { travel = it.height + marginPx }
                .graphicsLayer {
                    val distance = size.height + marginPx
                    val p = position.value
                    if (p >= 0f) {
                        translationY = p * distance
                    } else {
                        // Para cima (dedo ou o ressalto da abertura): parte sobe, parte estica a
                        // partir da base, e afina um pouco na largura (o volume mantém-se).
                        val pull = rubberBand(-p * distance, size.height)
                        val stretch = if (effects.animationsEnabled && size.height > 0f) {
                            (pull * SheetStretchShare / size.height).coerceAtMost(SheetMaxStretch)
                        } else {
                            0f
                        }
                        translationY = -(pull - stretch * size.height)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = 1f + stretch
                        scaleX = 1f - stretch * 0.3f
                    }
                    // Opaca a partir de um terço da subida: o vidro não fica meio transparente.
                    alpha = ((1f - p.coerceAtLeast(0f)) / 0.33f).coerceIn(0f, 1f) * fade.value
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = visible,
                    state = rememberDraggableState { delta ->
                        if (travel > 0f) scope.launch { position.snapTo(position.value + delta / travel) }
                    },
                    onDragStarted = { position.stop() },
                    onDragStopped = { velocityPx ->
                        val velocity = if (travel > 0f) velocityPx / travel else 0f
                        val end = projectedEnd(position.value, velocity)
                        // Fecha: arrastada um terço (~80 dp, a não ser que o dedo a lance para cima), um
                        // lançamento que a levaria a meio, ou um gesto rápido para baixo.
                        val dragged = position.value > DismissFraction && velocity > -0.5f
                        if (dragged || end > 0.5f || velocity > 2.4f) {
                            releaseVelocity = velocity
                            onDismiss()
                        } else {
                            launch {
                                position.animateTo(0f, CycloSprings.SheetOpen.float, initialVelocity = velocity)
                            }
                        }
                    },
                ),
        ) { content() }
    }
}

/** Altura de que os menus partem (a largura parte de [CycloMotion.MenuStartScale]): escorrem do campo. */
private const val MenuStartScaleY = 0.8f

/**
 * Quanto da resistência de uma folha puxada para cima (ou do ressalto ao abrir) é esticar em vez
 * de subir: a folha estica-se a partir da base, como gel, em vez de só deslizar.
 */
private const val SheetStretchShare = 0.45f

/** Quanto do ecrã a folha pode esticar, no máximo (fração da altura dela). */
private const val SheetMaxStretch = 0.04f

/** Quanto do caminho arrastar uma folha para baixo a fecha ao largar (~80 dp na folha do GitHub). */
private const val DismissFraction = 0.3f
