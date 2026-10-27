package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloNameField
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.MaxRoutineNameLength
import com.gaebriu.cyclo.ui.dictation.rememberSpeechDictation
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.keyboard.KeyboardLift
import com.gaebriu.cyclo.ui.keyboard.rememberIsKeyboardOpen
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Introduction 3: "Give it a name" (só o conteúdo; o botão de cima, a barra de progresso e o
 * Next são fixos e vivem em [IntroductionFlow]).
 *
 * - Escrever: ao tocar na caixa abre o teclado e SÓ a caixa vai para 15 dp acima dele ([KeyboardLift]).
 *   O Enter ("Done") e tocar fora fecham o teclado. O subtítulo desvanece-se com o teclado aberto
 *   e reaparece quando ele fecha, mas nunca se esconde durante o ditado.
 * - Ditado: o microfone usa o reconhecimento de voz do Google em segundo plano; o texto dito
 *   substitui o que está na caixa. Se a permissão for recusada, o microfone desaparece.
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
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val dictation = rememberSpeechDictation { spoken ->
        // O que foi dito substitui o texto atual.
        onNameChange(spoken.trim().take(MaxRoutineNameLength))
    }

    // "Voltar ao teclado": deixa de ouvir e, quando a caixa já aceita foco, abre o teclado.
    var pendingKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(pendingKeyboard, dictation.isListening) {
        if (pendingKeyboard && !dictation.isListening) {
            withFrameNanos { } // espera que a caixa volte a estar ativa
            // Se a caixa já tiver saído do ecrã, não há nada a focar.
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            pendingKeyboard = false
        }
    }

    // O subtítulo desvanece-se com o teclado aberto, exceto durante o ditado.
    val subtitleVisible = !rememberIsKeyboardOpen() || dictation.isListening
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (subtitleVisible) 1f else 0f,
        animationSpec = CycloMotion.FadeSpring,
        label = "subtitle",
    )

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
                        .graphicsLayer { alpha = subtitleAlpha },
                )
                Spacer(Modifier.height(64.dp))
            }

            KeyboardLift(modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin)) {
                CycloNameField(
                    value = name,
                    onValueChange = onNameChange,
                    isListening = dictation.isListening,
                    micVisible = dictation.isMicVisible,
                    onMicClick = {
                        focusManager.clearFocus()
                        dictation.start()
                    },
                    onSwitchToKeyboard = {
                        dictation.cancel()
                        pendingKeyboard = true
                    },
                    onDone = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    focusRequester = focusRequester,
                )
            }
        }
    }
}
