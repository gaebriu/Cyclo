package com.gaebriu.cyclo.ui.screens

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

    // Rotação do sol: cada valor que muda na hora ou nos minutos roda-o em proporção (uma volta
    // inteira do seletor = uma volta do sol): no sentido horário quando o valor aumenta e no
    // sentido anti-horário quando diminui. A mola sem ressalto segue o
    // alvo à medida que ele avança, por isso deslizar depressa dá uma rotação contínua, sem saltos.
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()
    val sunRotation = remember { Animatable(0f) }
    var sunTarget by remember { mutableFloatStateOf(0f) }
    var lastHour by remember { mutableIntStateOf(wakeHour) }
    val use24Hour = LocalTimeFormat.current.use24Hour
    // 12 h: a caixa das horas tem 12 valores (0 mostra-se "12") e a do AM/PM dois.
    val hourCount = if (use24Hour) 24 else 12
    val shownHour = if (use24Hour) wakeHour else wakeHour % 12
    val pm = wakeHour >= 12
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
                                    turnSun(lastHour, it, hourCount)
                                    lastHour = it
                                    onHourChange(if (use24Hour) it else it + if (pm) 12 else 0)
                                },
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
                            onValueChange = {
                                turnSun(lastMinute, it, 60)
                                lastMinute = it
                                onMinuteChange(it)
                            },
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

/**
 * Quantos valores separam [from] de [to] num seletor que dá a volta, pelo caminho mais curto e
 * com sinal: positivo se o valor aumentou, negativo se diminuiu (23 para 00 é +1, 00 para 23 é -1).
 */
private fun circularSteps(from: Int, to: Int, valueCount: Int): Int {
    val forward = ((to - from) % valueCount + valueCount) % valueCount
    return if (forward <= valueCount / 2) forward else forward - valueCount
}

/** "06", "23": dois algarismos (as horas de 24 h e os minutos). */
private val TwoDigits: (Int) -> String = { String.format(java.util.Locale.ROOT, "%02d", it) }

/** As horas de 12 h: 0 mostra-se "12", o resto com dois algarismos ("06"), como no Figma. */
private fun hour12Label(value: Int): String = if (value == 0) "12" else String.format(java.util.Locale.ROOT, "%02d", value)
