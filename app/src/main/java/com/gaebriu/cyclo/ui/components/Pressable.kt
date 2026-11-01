package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloMotion

/**
 * O toque de todos os botões da Cyclo, num só sítio:
 * - zoom out subtil com uma mola enquanto está carregado;
 * - háptico suave ao clicar ([CycloHaptics.buttonTap]), a menos que [haptic] seja falso
 *   (ex.: botões que mudam o modo de ditado, que por decisão não têm háptico).
 *
 * A área de toque não encolhe (só o que está dentro): por isso o dedo não "cai" do botão.
 * Tudo o que o botão desenha tem de vir DEPOIS deste modificador.
 */
@Composable
fun Modifier.cycloPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressedScale: Float = CycloMotion.ButtonPressScale,
    role: Role? = Role.Button,
): Modifier {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = CycloMotion.PressSpring,
        label = "press",
    )
    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                if (haptic) CycloHaptics.buttonTap(view)
                onClick()
            },
        )
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
