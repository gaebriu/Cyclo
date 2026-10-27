package com.gaebriu.cyclo.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion

/**
 * Altura FINAL do teclado, em px (0 = fechado). Muda logo quando o teclado começa a abrir ou a
 * fechar, e não a cada frame da animação do sistema, por isso serve para arrancar animações em sincronia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun rememberKeyboardTargetHeight(): Int {
    val density = LocalDensity.current
    return WindowInsets.imeAnimationTarget.getBottom(density)
}

/** Se o teclado está aberto (ou a abrir). */
@Composable
fun rememberIsKeyboardOpen(): Boolean = rememberKeyboardTargetHeight() > 0

/**
 * Faz só este conteúdo ir da sua posição em repouso para [gap] acima do teclado, com uma mola
 * suave (ao estilo iOS / Material You), e voltar quando o teclado fecha. O resto do ecrã não se mexe.
 *
 * O destino conta-se com a altura final do teclado, por isso a mola arranca logo a direito,
 * sem esperar que o sistema acabe de abrir o teclado.
 */
@Composable
fun KeyboardLift(
    modifier: Modifier = Modifier,
    gap: Dp = CycloDimens.Spacing,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboardHeight = rememberKeyboardTargetHeight()

    // Posição em repouso do conteúdo, medida ANTES da deslocação.
    var restBottom by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableFloatStateOf(0f) }

    val gapPx = with(density) { gap.toPx() }
    val targetShift = if (keyboardHeight > 0 && rootHeight > 0f) {
        rootHeight - keyboardHeight - gapPx - restBottom
    } else {
        0f
    }
    val shift by animateFloatAsState(
        targetValue = targetShift,
        animationSpec = CycloMotion.KeyboardSpring,
        label = "keyboardLift",
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                restBottom = coordinates.positionInRoot().y + coordinates.size.height
                rootHeight = coordinates.findRootCoordinates().size.height.toFloat()
            }
            .graphicsLayer { translationY = shift },
    ) {
        content()
    }
}
