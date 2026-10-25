package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloGlassButton
import com.gaebriu.cyclo.ui.components.CycloProgressBar
import com.gaebriu.cyclo.ui.components.CycloTimeUnitPicker
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.PickerHeight
import com.gaebriu.cyclo.ui.components.PickerWidth
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType

private const val StepNumber = 2
private const val StepCount = 6

/** Espaço entre as duas caixas, onde ficam os dois pontos. */
private val PickerGap = 45.dp

/**
 * Introduction 2: "What time do you want to wake up?".
 *
 * Só formato 24 h por agora (o AM/PM fica para quando houver design das 3 caixas).
 * O estado da hora vive em quem usa o ecrã, para sobreviver a voltar atrás.
 *
 * @param hintVisible o texto "Scroll or tap to interact" desaparece com um fade só depois
 *                    de a hora ou os minutos mudarem; só tocar não o faz desaparecer.
 * @param onBack seta de voltar: volta ao Introduction 1.
 * @param onNext ação do Next (a ligar ao Introduction 3, que ainda não existe).
 */
@Composable
fun Introduction2Screen(
    wakeHour: Int,
    wakeMinute: Int,
    hintVisible: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A barra cresce de 1/6 (onde ficou no ecrã anterior) até 2/6.
    val progress = remember { Animatable(1f / StepCount) }
    LaunchedEffect(Unit) {
        progress.animateTo(StepNumber / StepCount.toFloat(), CycloMotion.ProgressSpring)
    }

    val hintAlpha by animateFloatAsState(
        targetValue = if (hintVisible) 1f else 0f,
        animationSpec = tween(CycloMotion.HintFadeDurationMs, easing = CycloMotion.Decelerate),
        label = "hint",
    )

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val closeTop = maxOf(CycloDimens.TopBarTop, statusTop + 24.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    // O círculo tem 39 dp mas a zona de toque 48 dp: compensamos os 4,5 dp de cada lado.
    val outset = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(closeTop - outset))
        CycloCircleButton(
            iconPath = CycloIconPaths.Back,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.offset(x = -outset),
        )

        Spacer(Modifier.height(CycloDimens.ScreenMargin - outset))
        CycloProgressBar(
            progress = { progress.value },
            description = stringResource(R.string.introduction_progress, StepNumber, StepCount),
        )

        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Sun, modifier = Modifier.align(Alignment.CenterHorizontally))

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
                    onValueChange = onHourChange,
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
                    onValueChange = onMinuteChange,
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

        Spacer(Modifier.height(CycloDimens.Spacing))
        CycloGlassButton(
            text = stringResource(R.string.next),
            onClick = onNext,
        )
        Spacer(Modifier.height(bottomInset))
    }
}
