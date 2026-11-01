package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.RoutineNameInput
import com.gaebriu.cyclo.ui.components.rememberRoutineNameInput
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.keyboard.rememberFadeWithKeyboard
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Introduction 3: "Give it a name" (só o conteúdo; o botão de cima, a barra de progresso e o
 * Next são fixos e vivem em [IntroductionFlow]).
 *
 * - Escrever: ao tocar na caixa abre o teclado e SÓ a caixa vai para 15 dp acima dele. O subtítulo
 *   desvanece-se com o teclado aberto e reaparece quando ele fecha, mas nunca se esconde durante
 *   o ditado. O Enter ("Done") e tocar fora fecham o teclado.
 * - Ditado e teclado estão em [RoutineNameInput], partilhado com o Introduction 4.
 * - O nome vazio não é tratado aqui: o [IntroductionFlow] guarda "Routine [Nº]" em segundo plano,
 *   sem nunca o escrever nesta caixa.
 */
@Composable
fun Introduction3Page(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val nameInput = rememberRoutineNameInput(onNameChange)
    val subtitleFade = rememberFadeWithKeyboard(keepVisible = nameInput.isDictating)

    Column(
        modifier = modifier
            .fillMaxSize()
            // Tocar fora da caixa fecha o teclado.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Label, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(CycloDimens.Spacing))
        // O padding horizontal vai por dentro, para a área com scroll ocupar o ecrã todo
        // e o brilho da caixa a ouvir não ser cortado nas laterais.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin)) {
                BasicText(
                    text = stringResource(R.string.introduction_3_title),
                    style = CycloType.HeaderMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                )
                Spacer(Modifier.height(24.dp))
                BasicText(
                    text = stringResource(R.string.introduction_3_subtitle),
                    style = CycloType.Body,
                    // Mantém o espaço: só muda a opacidade, por isso nada salta.
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = subtitleFade.value },
                )
                Spacer(Modifier.height(64.dp))
            }

            RoutineNameInput(
                state = nameInput,
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin),
            )
        }
    }
}
