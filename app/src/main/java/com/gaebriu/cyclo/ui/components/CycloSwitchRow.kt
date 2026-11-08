package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType

// Medidas do Figma (as linhas com y/n das definições e do Introduction 6: 345 x 48 dp).
/** O texto a 15 dp da esquerda; o interruptor a 15 dp da direita. */
private val RowInset = 15.dp

/** Espaço mínimo entre o nome e o interruptor. */
private val RowLabelGap = 9.dp

/** Margem de cima e de baixo do texto (só conta com letra grande: a 1x a linha tem 48 dp). */
private val RowTextVertical = 6.dp

/**
 * Uma linha com o interruptor (y/n): "Bedtime", "Log sleep", "Advanced textures", "Beta updates"
 * (definições, Cyclo-2 2039:40) e as do Introduction 6 (2039:150). 345 x 48 dp, raio 15, #101011
 * em Luminosity. Toda a linha liga e desliga.
 *
 * Beta 2.3: vinha das definições (privada lá); passou para aqui para o Introduction 6 usar a mesma.
 *
 * [toggleHaptic] false: o toque não muda já o interruptor (abre uma confirmação ou o pedido de
 * autorização do sistema), por isso tem o háptico de botão e não o de ligar.
 */
@Composable
internal fun SwitchRow(
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    toggleHaptic: Boolean = true,
    pressHaptic: Boolean = !toggleHaptic,
    shake: () -> Float = { 0f },
) {
    val view = LocalView.current
    val stateText = stringResource(if (on) R.string.saved_state_on else R.string.saved_state_off)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            // A recusa: a linha toda abana, como a caixa da idade (ver playRejectShake nas definições).
            .graphicsLayer { translationX = shake().dp.toPx() }
            .cycloPressable(
                onClick = {
                    if (toggleHaptic) CycloHaptics.toggle(view, !on)
                    onToggle()
                },
                haptic = pressHaptic,
                role = Role.Switch,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = stateText
                toggleableState = ToggleableState(on)
                role = Role.Switch
            }
            .cycloCard(shadowColor = null),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = label,
            style = CycloType.CardTitle,
            modifier = Modifier.padding(start = RowInset, end = RowInset + 42.dp + RowLabelGap, top = RowTextVertical, bottom = RowTextVertical),
        )
        CycloSwitch(on = on, modifier = Modifier.align(Alignment.CenterEnd).padding(end = RowInset))
    }
}

/**
 * Interruptor (y/n): 42 x 23 dp, raio 15. Ligado: #4A3C99 com a bola à direita; desligado:
 * #A3A2A7 a 15 % com a bola à esquerda a 63 %. A bola tem 15 dp, a 4 dp das pontas, com
 * gradiente #FCFCFE a #9F9FA0 em Luminosity.
 *
 * A mudança é uma mola com um ressalto muito leve: a bola desliza e, a meio do caminho, estica um
 * pouco no sentido do movimento (e volta a ser redonda ao chegar); a cor do fundo e a opacidade da
 * bola acompanham. Igual a ligar e a desligar. Sem animações, muda logo.
 */
@Composable
internal fun CycloSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    // Beta 2.2: a mola vem dos presets ([CycloSprings.Toggle]) e o esticão da bola segue a
    // velocidade a que ela anda mesmo (como a bola do medidor), e não a posição.
    val motion = remember { Animatable(if (on) 1f else 0f) }
    LaunchedEffect(on, effects.animationsEnabled) {
        val target = if (on) 1f else 0f
        if (effects.animationsEnabled) motion.animateTo(target, CycloSprings.Toggle.float) else motion.snapTo(target)
    }
    Box(
        modifier = modifier
            .size(width = 42.dp, height = 23.dp)
            .drawBehind {
                val p = motion.value
                val radius = CornerRadius(15.dp.toPx())
                drawRoundRect(
                    color = lerp(CycloColors.TextSecondary.copy(alpha = 0.15f), CycloColors.Accent, p.coerceIn(0f, 1f)),
                    cornerRadius = radius,
                )
                val knob = 15.dp.toPx()
                val inset = 4.dp.toPx()
                // Esticão: até 5 dp, proporcional à velocidade da bola (0 parada).
                val stretch = 5.dp.toPx() * (kotlin.math.abs(motion.velocity) / ToggleFullStretchSpeed).coerceIn(0f, 1f)
                val travel = size.width - inset * 2f - knob
                val x = inset + travel * p - stretch * p
                val alpha = lerpFloat(0.63f, 1f, p.coerceIn(0f, 1f))
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(CycloColors.TextPrimary.copy(alpha = alpha), Color(0xFF9F9FA0).copy(alpha = alpha)),
                        startY = inset,
                        endY = inset + knob,
                    ),
                    topLeft = Offset(x, inset),
                    size = androidx.compose.ui.geometry.Size(knob + stretch, knob),
                    cornerRadius = radius,
                    blendMode = effects.luminosity,
                )
            },
    )
}

/** A esta velocidade (percursos por segundo) a bola do interruptor já estica os 5 dp todos. */
private const val ToggleFullStretchSpeed = 6f
