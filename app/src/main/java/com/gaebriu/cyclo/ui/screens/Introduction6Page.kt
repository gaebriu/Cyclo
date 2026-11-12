package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.SwitchRow
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Introduction 6 (Beta 2.3, Cyclo-2 2039:150): "Almost there, want to receive reminders?", só no
 * tutorial (só o conteúdo; o botão de cima, a barra de progresso e o Next são fixos e vivem em
 * [IntroductionFlow]).
 *
 * As medidas são as do Introduction 5 (o mesmo holofote, título, subtítulo e o grupo a 454 dp do
 * topo do frame); no lugar do nome e do botão do widget, as linhas "Bedtime" e "Log sleep" com o
 * interruptor (as mesmas das definições, [SwitchRow]), a 15 dp uma da outra.
 *
 * Os dois começam DESLIGADOS (no Figma estão ligados só como exemplo). Ligar abre o pedido de
 * autorização nativo do Android, quando falta; recusar não mostra nada e o interruptor fica
 * desligado. Quem decide e guarda é o [IntroductionFlow] (o Next guarda tudo de uma vez).
 *
 * @param bedtimeOn / logSleepOn o interruptor como se vê: escolhido E com as notificações autorizadas.
 * @param asks ligar abre primeiro o pedido do sistema (o toque tem o háptico de botão).
 */
@Composable
fun Introduction6Page(
    bedtimeOn: Boolean,
    onToggleBedtime: () -> Unit,
    logSleepOn: Boolean,
    onToggleLogSleep: () -> Unit,
    asks: Boolean,
    modifier: Modifier = Modifier,
    iconMotion: com.gaebriu.cyclo.ui.components.IconMotion? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Notifications, motion = iconMotion, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(CycloDimens.Spacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .cycloVerticalScroll(rememberScrollState())
                .padding(horizontal = CycloDimens.ScreenMargin),
        ) {
            BasicText(
                text = stringResource(R.string.introduction_6_title),
                style = CycloType.HeaderMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Spacer(Modifier.height(24.dp))
            BasicText(
                text = stringResource(R.string.introduction_6_subtitle),
                style = CycloType.Body,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(63.dp))

            // group (2039:164): bedtime e log-sleep, 345 x 48 dp, 15 dp entre eles.
            SwitchRow(
                label = stringResource(R.string.settings_bedtime_reminder),
                on = bedtimeOn,
                onToggle = onToggleBedtime,
                toggleHaptic = bedtimeOn || !asks,
            )
            Spacer(Modifier.height(CycloDimens.Spacing))
            SwitchRow(
                label = stringResource(R.string.settings_log_sleep_reminder),
                on = logSleepOn,
                onToggle = onToggleLogSleep,
                toggleHaptic = logSleepOn || !asks,
            )
        }
    }
}
