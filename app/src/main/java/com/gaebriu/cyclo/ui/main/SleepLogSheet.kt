package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.SleepQuality
import com.gaebriu.cyclo.ui.components.BottomSheetHost
import com.gaebriu.cyclo.ui.components.CycloBigSheet
import com.gaebriu.cyclo.ui.components.CycloMeter
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * action-menu-log-sleep (Cyclo-2, Componentes 5, 2013:40): a folha "How well did you sleep last
 * night?" com o nível escolhido por cima do sleep-quality-meter (Really well → Very poor, da esquerda
 * para a direita) e o "Done" roxo. É o [CycloBigSheet] (mesmas medidas: pega, 15 dp entre tudo) com o
 * [CycloMeter] do Feed, dentro do [BottomSheetHost] de todas as folhas.
 *
 * - A bola começa em [start] (a média, ou "Really well"); o texto muda com ela, num fade curto.
 * - "Done" devolve o nível; fechar a folha (arrastar, tocar fora, voltar) não regista nada.
 */
@Composable
fun SleepLogSheet(
    visible: Boolean,
    start: SleepQuality,
    bottomMargin: Dp,
    onDismiss: () -> Unit,
    onDone: (SleepQuality) -> Unit,
) {
    val effects = LocalVisualEffects.current
    // A escolha recomeça cada vez que a folha abre.
    var chosen by remember(visible, start) { mutableStateOf(start) }
    val last = SleepQuality.entries.size - 1
    BottomSheetHost(visible = visible, onDismiss = onDismiss, bottomMargin = bottomMargin) {
        CycloBigSheet(
            title = stringResource(R.string.sleep_sheet_title),
            texts = emptyList(),
            buttonText = stringResource(R.string.sleep_sheet_done),
            onButtonClick = { onDone(chosen) },
            textContent = {
                AnimatedContent(
                    targetState = chosen,
                    transitionSpec = {
                        val ms = if (effects.animationsEnabled) LabelFadeMs else 0
                        fadeIn(tween(ms, delayMillis = ms / 3)) togetherWith fadeOut(tween(ms))
                    },
                    contentAlignment = Alignment.Center,
                    label = "sleepLabel",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 18.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) { quality ->
                    BasicText(
                        text = stringResource(quality.label),
                        style = CycloType.Body.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            extra = {
                CycloMeter(
                    value = start.ordinal / last.toFloat(),
                    levels = SleepQuality.entries.map { stringResource(it.label) },
                    description = stringResource(R.string.sleep_quality_meter),
                    onValueChange = { value -> chosen = SleepQuality.entries[kotlin.math.round(value * last).toInt().coerceIn(0, last)] },
                    onStepChange = { step -> chosen = SleepQuality.entries[step.coerceIn(0, last)] },
                )
            },
        )
    }
}

/** O texto do nível a trocar enquanto a bola passa pelos pontos. */
private const val LabelFadeMs = 180
