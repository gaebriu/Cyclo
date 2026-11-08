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
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
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
 * @param onWheelMove o movimento contínuo da roda, em valores (ex.: +0,4 a meio do caminho para o
 *        seguinte), enquanto o dedo arrasta, a inércia corre e a mola assenta. Um valor que chega de
 *        fora (estado restaurado) não conta. É o que faz rodar o sol do Introduction 2.
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
    onWheelMove: ((Float) -> Unit)? = null,
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

    // A última posição avisada a [onWheelMove] (um salto de fora não conta como movimento).
    val reported = remember { floatArrayOf(position) }
    val latestOnWheelMove by rememberUpdatedState(onWheelMove)

    // Se o valor vier de fora com outro número (ex.: estado restaurado), a roda salta para lá.
    LaunchedEffect(value) {
        if (position.roundToInt().mod(valueCount) != value) {
            position = value.toFloat()
            reported[0] = position
        }
    }

    // O movimento contínuo da roda (no máximo um aviso por fotograma).
    LaunchedEffect(Unit) {
        snapshotFlow { position }.collect { p ->
            val delta = p - reported[0]
            reported[0] = p
            if (delta != 0f) latestOnWheelMove?.invoke(delta)
        }
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
                animationSpec = CycloSprings.PickerSettle.float,
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
                    motion = scope.launch { land(position, unitsPerSecond) { position = it } }
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

/**
 * A roda a assentar depois de largar (Beta 2.2: um só movimento, como o UIPickerView do iOS).
 * Antes, a inércia corria até abrandar e depois uma mola, a partir de velocidade zero, levava-a ao
 * valor mais perto: uma travagem visível mesmo antes de o número pousar, e o destino só se sabia no
 * fim. Agora o destino calcula-se logo ao largar: onde a inércia ([PickerFriction]) a deixaria,
 * arredondado ao valor mais perto. A roda vai até lá numa curva de travagem que acaba EXATAMENTE no
 * valor, em tempo finito: posição = destino - d·(1 - t/T)^n, com T = n·d/v, por isso parte com a
 * velocidade do dedo e chega com a velocidade e a aceleração a zero (sem a cauda lenta de uma
 * exponencial, sem segunda fase). Largada devagar (ou com o valor mais perto para trás), uma mola
 * que parte da velocidade do dedo ([CycloSprings.PickerSettle]).
 */
private suspend fun land(from: Float, velocity: Float, set: (Float) -> Unit) {
    val projected = from + velocity / PickerFriction
    val target = projected.roundToInt().toFloat()
    val distance = target - from
    if (abs(velocity) > PickerGlideVelocity && abs(distance) >= 0.5f && sign(distance) == sign(velocity)) {
        val duration = PickerGlideExponent * distance / velocity
        var start = -1L
        while (true) {
            val done = withFrameNanos { now ->
                if (start < 0L) start = now
                val t = (now - start) / 1_000_000_000f
                val u = (1f - t / duration).coerceAtLeast(0f)
                set(target - distance * u.pow(PickerGlideExponent))
                u <= 0f
            }
            if (done) break
        }
        set(target)
    } else {
        animate(
            initialValue = from,
            targetValue = target,
            initialVelocity = velocity,
            animationSpec = CycloSprings.PickerSettle.float,
        ) { v, _ -> set(v) }
    }
}

/** O atrito da inércia da roda (por segundo): a roda anda velocidade / atrito até parar. */
private const val PickerFriction = 2.52f

/**
 * A forma da travagem: 2 = travar sempre com a mesma força (pára seco); 3 = muito suave no fim
 * (demora mais); 2,5 assenta com suavidade em ~1 s.
 */
private const val PickerGlideExponent = 2.5f

/** Acima disto (valores/s) a roda faz a travagem até ao valor; abaixo, só a mola. */
private const val PickerGlideVelocity = 1.5f
