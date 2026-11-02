package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloTimeUnitPicker
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.PickerHeight
import com.gaebriu.cyclo.ui.components.PickerWidth
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.launch

/** Espaço entre as duas caixas, onde ficam os dois pontos. */
private val PickerGap = 45.dp

/**
 * Introduction 2: "What time do you want to wake up?" (só o conteúdo; o botão de cima, a barra
 * de progresso e o Next são fixos e vivem em [IntroductionFlow]).
 *
 * Só formato 24 h por agora (o AM/PM fica para quando houver design das 3 caixas).
 * O estado da hora vive em quem usa o ecrã, para sobreviver a voltar atrás.
 *
 * @param hintVisible o texto "Scroll or tap to interact" desaparece com um fade só depois
 *                    de a hora ou os minutos mudarem; só tocar não o faz desaparecer.
 */
@Composable
fun Introduction2Page(
    wakeHour: Int,
    wakeMinute: Int,
    hintVisible: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintVisible) 1f else 0f,
        animationSpec = tween(CycloMotion.HintFadeDurationMs, easing = CycloMotion.Decelerate),
        label = "hint",
    )

    // Rotação do sol: cada valor que muda na hora ou nos minutos roda-o no sentido horário, em
    // proporção (uma volta inteira do seletor = uma volta do sol). A mola sem ressalto segue o
    // alvo à medida que ele avança, por isso deslizar depressa dá uma rotação contínua, sem saltos.
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()
    val sunRotation = remember { Animatable(0f) }
    var sunTarget by remember { mutableFloatStateOf(0f) }
    var lastHour by remember { mutableIntStateOf(wakeHour) }
    var lastMinute by remember { mutableIntStateOf(wakeMinute) }

    fun turnSun(from: Int, to: Int, valueCount: Int) {
        if (!effects.animationsEnabled) return
        val steps = circularSteps(from, to, valueCount)
        if (steps == 0) return
        sunTarget += steps * CycloMotion.SunDegreesPerTurn / valueCount
        val target = sunTarget
        scope.launch { sunRotation.animateTo(target, CycloMotion.SunRotationSpring) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(
            icon = CycloHeaderIcons.Sun,
            rotation = { sunRotation.value },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(CycloDimens.Spacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = stringResource(R.string.introduction_2_title),
                style = CycloType.HeaderMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )

            Spacer(Modifier.height(63.dp))

            // "hour" e "min" por cima das caixas.
            Row {
                BasicText(
                    text = stringResource(R.string.introduction_2_hour),
                    style = CycloType.Body,
                    modifier = Modifier.width(PickerWidth),
                )
                Spacer(Modifier.width(PickerGap))
                BasicText(
                    text = stringResource(R.string.introduction_2_minute),
                    style = CycloType.Body,
                    modifier = Modifier.width(PickerWidth),
                )
            }

            Spacer(Modifier.height(CycloDimens.Spacing))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CycloTimeUnitPicker(
                    value = wakeHour,
                    valueCount = 24,
                    contentDescription = stringResource(R.string.introduction_2_hour),
                    onValueChange = {
                        turnSun(lastHour, it, 24)
                        lastHour = it
                        onHourChange(it)
                    },
                )
                Box(
                    modifier = Modifier
                        .width(PickerGap)
                        .height(PickerHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    // No Figma os dois pontos ficam 2 dp abaixo do centro das caixas.
                    BasicText(
                        text = ":",
                        style = CycloType.PickerColon,
                        modifier = Modifier.offset(y = 2.dp),
                    )
                }
                CycloTimeUnitPicker(
                    value = wakeMinute,
                    valueCount = 60,
                    contentDescription = stringResource(R.string.introduction_2_minute),
                    onValueChange = {
                        turnSun(lastMinute, it, 60)
                        lastMinute = it
                        onMinuteChange(it)
                    },
                    manyChoices = true,
                )
            }

            Spacer(Modifier.height(CycloDimens.Spacing))

            BasicText(
                text = stringResource(R.string.introduction_2_hint),
                style = CycloType.Body,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = hintAlpha },
            )
        }
    }
}

/**
 * Quantos valores separam [from] de [to] num seletor que dá a volta, pelo caminho mais curto
 * (23 para 00 é 1 passo, não 23).
 */
private fun circularSteps(from: Int, to: Int, valueCount: Int): Int {
    val forward = ((to - from) % valueCount + valueCount) % valueCount
    return minOf(forward, valueCount - forward)
}
