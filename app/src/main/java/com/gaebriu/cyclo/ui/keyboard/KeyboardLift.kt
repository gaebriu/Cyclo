package com.gaebriu.cyclo.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import kotlin.math.roundToInt

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
 * Faz só este conteúdo ir da sua posição em repouso para [gap] acima do teclado, e voltar quando o
 * teclado fecha. O resto do ecrã não se mexe.
 *
 * Beta 2.2: o conteúdo anda COLADO ao teclado, fotograma a fotograma (como no iOS): a deslocação é a
 * final ([gap] acima da altura final do teclado) vezes a fração do teclado já aberta, lida da
 * animação do próprio sistema (`WindowInsets.ime`, que o Compose anima com o WindowInsetsAnimation
 * da janela edge-to-edge com adjustResize). Antes ia numa mola própria até à altura final: o
 * conteúdo e o teclado andavam com curvas diferentes e um adiantava-se ao outro. Lido só na
 * colocação (o teclado a andar não recompõe nada).
 */
@Composable
fun KeyboardLift(
    modifier: Modifier = Modifier,
    gap: Dp = CycloDimens.Spacing,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboardHeight = rememberKeyboardTargetHeight()
    val ime = WindowInsets.ime

    // Posição em repouso do conteúdo, medida ANTES da deslocação.
    var restBottom by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableFloatStateOf(0f) }
    // A última altura final do teclado aberto (ao fechar, a final é 0, e a fração conta-se desta).
    var openHeight by remember { mutableIntStateOf(0) }
    if (keyboardHeight > 0) openHeight = keyboardHeight

    val gapPx = with(density) { gap.toPx() }
    val shift: () -> Float = {
        val full = openHeight
        if (full <= 0 || rootHeight <= 0f) {
            0f
        } else {
            val fraction = (ime.getBottom(density).toFloat() / full).coerceIn(0f, 1f)
            (rootHeight - full - gapPx - restBottom) * fraction
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                restBottom = coordinates.positionInRoot().y + coordinates.size.height
                rootHeight = coordinates.findRootCoordinates().size.height.toFloat()
            }
            // Deslocação no LAYOUT (e não só no desenho, com graphicsLayer): assim o cursor, as pegas de
            // seleção e o que o teclado sabe da posição do texto acompanham a caixa a cada frame.
            .offset { IntOffset(0, shift().roundToInt()) },
    ) {
        content()
    }
}

/**
 * Opacidade (1 = visível) de um elemento que se esconde enquanto o teclado está aberto, com um
 * fade fluido, e que reaparece quando ele fecha. Nunca se esconde se [keepVisible] for verdadeiro
 * (ex.: durante o ditado por voz).
 */
@Composable
fun rememberFadeWithKeyboard(keepVisible: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (!rememberIsKeyboardOpen() || keepVisible) 1f else 0f,
        animationSpec = CycloMotion.FadeSpring,
        label = "fadeWithKeyboard",
    )
