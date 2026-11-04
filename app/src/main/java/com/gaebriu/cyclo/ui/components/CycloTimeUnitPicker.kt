package com.gaebriu.cyclo.ui.components

import com.gaebriu.cyclo.ui.theme.CycloSprings
import android.os.SystemClock
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

val PickerWidth: Dp = 79.dp
val PickerHeight: Dp = 47.dp

/** Intervalo mínimo entre dois hápticos, para uma deslizada rápida não virar uma vibração contínua. */
private const val MinTickIntervalMs = 35L

/**
 * Caixa de hora ou de minuto (hours-slider / minutes-slider): mostra só o valor atual.
 * Desliza-se na vertical, sem teclado: o número acompanha o dedo, rola para o seguinte,
 * continua com inércia ao largar e assenta sempre num valor inteiro. Dá a volta (23 volta a 00).
 * Tocar sem deslizar não muda nada.
 *
 * @param valueCount quantos valores há (24 para horas, 60 para minutos).
 * @param manyChoices háptico mais leve, para seletores com muitos valores (minutos).
 * @param label o texto de cada valor (por omissão, dois algarismos: "06"); ex.: "12" para o 0
 *              das horas de 12 h, ou "AM" / "PM" (AM-PM-Slider, Figma 244:187).
 * @param onValueChange chamado cada vez que o valor mostrado muda.
 */
@Composable
fun CycloTimeUnitPicker(
    value: Int,
    valueCount: Int,
    contentDescription: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    manyChoices: Boolean = false,
    label: (Int) -> String = ::format,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val stepPx = with(density) { PickerHeight.toPx() }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val increaseLabel = stringResource(R.string.increase)
    val decreaseLabel = stringResource(R.string.decrease)

    // Posição da "roda", em unidades de valor (sem limites; o valor mostrado é o resto da divisão).
    var position by remember { mutableFloatStateOf(value.toFloat()) }
    var motion by remember { mutableStateOf<Job?>(null) }

    // Se o valor vier de fora com outro número (ex.: estado restaurado), a roda salta para lá.
    LaunchedEffect(value) {
        if (position.roundToInt().mod(valueCount) != value) position = value.toFloat()
    }

    // Cada vez que o valor muda: avisa e dá um toque háptico curto.
    LaunchedEffect(valueCount) {
        var last = position.roundToInt()
        var lastTick = 0L
        snapshotFlow { position.roundToInt() }.collect { current ->
            if (current != last) {
                last = current
                currentOnValueChange(current.mod(valueCount))
                val now = SystemClock.uptimeMillis()
                if (now - lastTick >= MinTickIntervalMs) {
                    lastTick = now
                    CycloHaptics.pickerTick(view, manyChoices)
                }
            }
        }
    }

    val dragState = rememberDraggableState { delta -> position -= delta / stepPx }

    fun nudge(direction: Int) {
        motion?.cancel()
        motion = scope.launch {
            animate(
                initialValue = position,
                targetValue = position.roundToInt() + direction.toFloat(),
                animationSpec = CycloSprings.fromStiffness(Spring.StiffnessMedium, 0.8f).float,
            ) { v, _ -> position = v }
        }
    }

    // Número de baixo da roda: o valor logo abaixo da posição atual; o seguinte entra por baixo.
    val base by remember { derivedStateOf { floor(position).toInt() } }

    Box(
        modifier = modifier
            .size(PickerWidth, PickerHeight)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = label(value)
                customActions = listOf(
                    CustomAccessibilityAction(increaseLabel) { nudge(+1); true },
                    CustomAccessibilityAction(decreaseLabel) { nudge(-1); true },
                )
            }
            .clip(SmoothRoundedCornerShape(15.dp))
            .background(CycloColors.GlassFill)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { motion?.cancel() },
                onDragStopped = { velocity ->
                    // Dedo para cima = valores a subir; a velocidade passa de px/s para valores/s.
                    val unitsPerSecond = -velocity / stepPx
                    motion = scope.launch {
                        if (abs(unitsPerSecond) > 1f) {
                            animateDecay(
                                initialValue = position,
                                initialVelocity = unitsPerSecond,
                                animationSpec = FloatExponentialDecaySpec(
                                    frictionMultiplier = 0.6f,
                                    absVelocityThreshold = 0.6f,
                                ),
                            ) { v, _ -> position = v }
                        }
                        // Assenta no valor inteiro mais próximo, com uma mola suave.
                        animate(
                            initialValue = position,
                            targetValue = position.roundToInt().toFloat(),
                            animationSpec = CycloSprings.fromStiffness(Spring.StiffnessMedium, 0.8f).float,
                        ) { v, _ -> position = v }
                    }
                },
            ),
    ) {
        // O número atual sobe e desvanece-se; o seguinte entra por baixo. Em repouso só se vê um.
        BasicText(
            text = label(base.mod(valueCount)),
            style = CycloType.Picker,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val fraction = position - floor(position)
                    translationY = -fraction * stepPx
                    alpha = 1f - fraction
                },
        )
        BasicText(
            text = label((base + 1).mod(valueCount)),
            style = CycloType.Picker,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val fraction = position - floor(position)
                    translationY = (1f - fraction) * stepPx
                    alpha = fraction
                },
        )
    }
}

private fun format(number: Int): String = String.format(Locale.ROOT, "%02d", number)
