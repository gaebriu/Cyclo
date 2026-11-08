package com.gaebriu.cyclo.ui.screens

import com.gaebriu.cyclo.ui.components.SpringValue
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.components.CycloTimeUnitPicker
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.PickerHeight
import com.gaebriu.cyclo.ui.components.PickerWidth
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import androidx.compose.runtime.key
import com.gaebriu.cyclo.ui.time.LocalTimeFormat
import kotlinx.coroutines.launch

/** Espaço entre as duas caixas, onde ficam os dois pontos. */
private val PickerGap = 45.dp

/** Dos minutos ao AM-PM-Slider no formato de 12 h (Figma 244:150: 247 → 271). */
private val PeriodGap = 24.dp

/**
 * Introduction 2: "What time do you want to wake up?" (só o conteúdo; o botão de cima, a barra
 * de progresso e o Next são fixos e vivem em [IntroductionFlow]).
 *
 * O formato segue o das horas da app ([LocalTimeFormat]: o das definições ou, sem escolha, o do
 * sistema): 24 h com as horas e os minutos (Figma 8:146), 12 h com as horas (12, 01 ... 11), os
 * minutos e o AM-PM-Slider (Figma 244:150). As três caixas são o mesmo [CycloTimeUnitPicker] e
 * cada uma muda só a sua parte; a hora guarda-se sempre em 24 h ([onHourChange] recebe 0 a 23).
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

    // O sol roda com as rodas da hora, com o movimento contínuo delas (o dedo, a inércia, a mola
    // a assentar), e não aos saltos por valor: cada roda empurra o alvo ([turnSun]) e o sol vai
    // atrás com uma mola ([CycloSprings.SunFollow]) que tem peso e, quando a roda pára, chega com
    // um nadinha de ressalto. Rodar depressa enche-o um pouco (até [SunMaxSwell]). Um só ciclo de
    // fotogramas, parado quando tudo assenta; sem animações no sistema, o sol fica quieto.
    val effects = LocalVisualEffects.current
    val sun = remember { SpringValue(0f) }
    val sunSwell = remember { SpringValue(1f) }
    val sunWake = remember { Channel<Unit>(Channel.CONFLATED) }
    fun turnSun(degrees: Float) {
        if (!effects.animationsEnabled || degrees == 0f) return
        sun.target += degrees
        sunWake.trySend(Unit)
    }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            if (sun.settled && sunSwell.settled) {
                sunWake.receive()
                last = -1L
            }
            withFrameNanos { now ->
                val dt = if (last < 0L) FrameS else ((now - last) / 1_000_000_000f).coerceIn(0f, MaxFrameS)
                last = now
                sun.step(dt, CycloSprings.SunFollow, restDelta = 0.02f, restVelocity = 0.5f)
                sunSwell.target = 1f + SunMaxSwell * (abs(sun.velocity) / SunFullSwellSpeed).coerceIn(0f, 1f)
                sunSwell.step(dt, CycloSprings.SunSwell, restDelta = 0.0005f, restVelocity = 0.005f)
            }
        }
    }
    val use24Hour = LocalTimeFormat.current.use24Hour
    // 12 h: a caixa das horas tem 12 valores (0 mostra-se "12") e a do AM/PM dois.
    val hourCount = if (use24Hour) 24 else 12
    val shownHour = if (use24Hour) wakeHour else wakeHour % 12
    val pm = wakeHour >= 12

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(
            icon = CycloHeaderIcons.Sun,
            rotation = { sun.value },
            scale = { sunSwell.value },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(CycloDimens.Spacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .cycloVerticalScroll(rememberScrollState()),
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

            // As medidas do Figma (ecrã de 393 dp): 24 h = 79 + 45 + 79 = 203 dp; 12 h = 203 + 24 + 79
            // = 306 dp. Num ecrã mais estreito do que isso (ex.: 320 dp), uma Row com larguras fixas
            // espremia a última caixa (o AM/PM ficava com 45 dp). Aqui caixas e espaços encolhem
            // juntos, na proporção do Figma, até caberem; com espaço, ficam exatamente as do Figma.
            BoxWithConstraints {
                val figmaRow = PickerWidth * (if (use24Hour) 2 else 3) + PickerGap + (if (use24Hour) 0.dp else PeriodGap)
                val fit = if (maxWidth < figmaRow) maxWidth / figmaRow else 1f
                val boxWidth = PickerWidth * fit
                val gap = PickerGap * fit
                val periodGap = PeriodGap * fit
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // "hour" e "min" por cima das caixas (nada por cima do AM/PM, como no Figma).
                    Row {
                        BasicText(
                            text = stringResource(R.string.introduction_2_hour),
                            style = CycloType.Body,
                            modifier = Modifier.width(boxWidth),
                        )
                        Spacer(Modifier.width(gap))
                        BasicText(
                            text = stringResource(R.string.introduction_2_minute),
                            style = CycloType.Body,
                            modifier = Modifier.width(boxWidth),
                        )
                        if (!use24Hour) Spacer(Modifier.width(periodGap + boxWidth))
                    }

                    Spacer(Modifier.height(CycloDimens.Spacing))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Uma caixa nova ao mudar de formato (outro número de valores).
                        key(use24Hour) {
                            CycloTimeUnitPicker(
                                value = shownHour,
                                valueCount = hourCount,
                                contentDescription = stringResource(R.string.introduction_2_hour),
                                onValueChange = {
                                    onHourChange(if (use24Hour) it else it + if (pm) 12 else 0)
                                },
                                onWheelMove = { turnSun(it * CycloMotion.SunDegreesPerHour) },
                                label = if (use24Hour) TwoDigits else ::hour12Label,
                                modifier = Modifier.width(boxWidth),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(gap)
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
                            onValueChange = { onMinuteChange(it) },
                            onWheelMove = { turnSun(it * CycloMotion.SunDegreesPerMinute) },
                            manyChoices = true,
                            modifier = Modifier.width(boxWidth),
                        )
                        if (!use24Hour) {
                            Spacer(Modifier.width(periodGap))
                            // AM-PM-Slider: 0 = AM, 1 = PM; muda só a metade do dia (as horas e os minutos ficam).
                            CycloTimeUnitPicker(
                                value = if (pm) 1 else 0,
                                valueCount = 2,
                                contentDescription = stringResource(R.string.introduction_2_period),
                                onValueChange = { period ->
                                    val hour = wakeHour % 12 + period * 12
                                    if (hour != wakeHour) onHourChange(hour)
                                },
                                label = { if (it == 1) "PM" else "AM" },
                                onWheelMove = { turnSun(it * CycloMotion.SunDegreesPerPeriod) },
                                modifier = Modifier.width(boxWidth),
                            )
                        }
                    }
                }
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

/** "06", "23": dois algarismos (as horas de 24 h e os minutos). */
private val TwoDigits: (Int) -> String = { String.format(java.util.Locale.ROOT, "%02d", it) }

/** As horas de 12 h: 0 mostra-se "12", o resto com dois algarismos ("06"), como no Figma. */
private fun hour12Label(value: Int): String = if (value == 0) "12" else String.format(java.util.Locale.ROOT, "%02d", value)

/** O sol cresce no máximo isto (+3 %) a rodar depressa, a partir de [SunFullSwellSpeed] graus/s. */
private const val SunMaxSwell = 0.03f
private const val SunFullSwellSpeed = 540f

/** O primeiro passo do ciclo do sol (um fotograma a 60 Hz) e o maior (um fotograma perdido não o lança). */
private const val FrameS = 1f / 60f
private const val MaxFrameS = 0.05f
