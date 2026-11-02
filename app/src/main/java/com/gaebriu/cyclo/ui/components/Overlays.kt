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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
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
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        // A abrir, uma mola com um ressalto muito leve; a fechar, mais rápida e sem ressalto.
        progress.animateTo(
            if (visible) 1f else 0f,
            if (visible) CycloMotion.MenuOpenSpring else CycloMotion.MenuCloseSpring,
        )
    }
    // Onde o host está na janela, para passar o [anchor] para as coordenadas dele.
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    // Já saiu por completo: não existe.
    if (!visible && progress.value <= 0f) return

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { hostOrigin = it.positionInWindow() }) {
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
                            val scale = lerp(CycloMotion.MenuStartScale, 1f, raw.coerceAtLeast(0f))
                            scaleX = scale
                            scaleY = scale
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
 * Folha que sobe de baixo com uma mola e se fecha arrastando-a para baixo (ou tocando fora).
 * Fica [bottomMargin] acima do fundo do ecrã, como o botão Next.
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, CycloMotion.OverlaySpring)
        if (!visible) dragOffset = 0f
    }
    if (!visible && progress.value <= 0f) return

    val dismissDistance = with(density) { 80.dp.toPx() }
    val marginPx = with(density) { bottomMargin.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
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
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    translationY = (1f - p) * (size.height + marginPx) + dragOffset
                    alpha = p
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    },
                    onDragStopped = { velocity ->
                        if (dragOffset > dismissDistance || velocity > 1200f) {
                            onDismiss()
                        } else {
                            scope.launch {
                                animate(
                                    initialValue = dragOffset,
                                    targetValue = 0f,
                                    animationSpec = CycloMotion.OverlaySpring,
                                ) { value, _ -> dragOffset = value }
                            }
                        }
                    },
                ),
        ) { content() }
    }
}
