package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.data.AppMessage
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.delay

// Medidas do Figma (undo-action: 345 x 66 dp, texto a 24 dp das pontas).
private val BarHeight = 66.dp
private val TextInset = 24.dp

/**
 * Beta 2.4: um texto que não cabe numa linha (ex.: "Não conseguimos exportar os teus dados" ao lado
 * do "Tentar de novo") passa à linha seguinte e a barra cresce PARA CIMA (está presa ao fundo), com
 * 15 dp por cima e por baixo do texto; antes ficava cortado com reticências. Com uma linha continua
 * com os 66 dp do Figma, e o botão fica sempre ao centro da altura da barra.
 */
private val TextVerticalMargin = 15.dp

/**
 * Barra de baixo (undo-action): um texto e, à direita, um botão ("Undo", ou "Try again" depois de
 * um erro). Usa o mesmo fundo dos menus ([cycloOverlaySurface]). Entra de baixo com uma mola e fade,
 * e sai sozinha ao fim de [CycloMotion.MessageDurationMs] (ou ao tocar no botão).
 *
 * Tem de estar num sítio que ocupe o ecrã todo.
 *
 * @param bottomMargin distância ao fundo do ecrã (fica por cima da barra de navegação ou do Next).
 */
@Composable
fun CycloMessageBar(
    message: AppMessage?,
    onDismiss: (id: Long) -> Unit,
    bottomMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val progress = remember { Animatable(0f) }
    // A última mensagem mostrada fica desenhada enquanto a barra sai.
    var shown by remember { mutableStateOf(message) }
    if (message != null) shown = message
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(message?.id) {
        if (message != null) {
            if (message.isError) CycloHaptics.reject(view)
            progress.animateTo(1f, CycloMotion.MessageSpring)
            delay(CycloMotion.MessageDurationMs)
            latestOnDismiss(message.id)
        } else {
            progress.animateTo(0f, CycloMotion.MessageSpring)
        }
    }

    val current = shown ?: return
    if (message == null && progress.value <= 0f) return

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(bottom = bottomMargin)
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = p
                    if (effects.animationsEnabled) translationY = (1f - progress.value) * (size.height + 24.dp.toPx())
                }
                .fillMaxWidth()
                .heightIn(min = BarHeight)
                .cycloOverlaySurface()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(TextInset))
            BasicText(
                text = current.text,
                style = CycloType.MenuItem,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = TextVerticalMargin),
            )
            val action = current.action
            if (current.actionLabel != null && action != null) {
                // A área de toque tem pelo menos 48 dp, o texto fica a 24 dp da ponta.
                Box(
                    modifier = Modifier
                        .height(CycloDimens.TouchTarget)
                        .cycloPressable(onClick = {
                            latestOnDismiss(current.id)
                            action()
                        })
                        .padding(start = 15.dp, end = TextInset),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(text = current.actionLabel, style = CycloType.MenuAction)
                }
            } else {
                Spacer(Modifier.width(TextInset))
            }
        }
    }
}
