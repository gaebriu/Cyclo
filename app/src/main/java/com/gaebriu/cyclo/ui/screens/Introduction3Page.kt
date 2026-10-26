package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Introduction 3: "Give it a name" (só o conteúdo; o botão de cima, a barra de progresso e o
 * Next são fixos e vivem em [IntroductionFlow]).
 *
 * - Escrever: ao tocar na caixa abre o teclado e SÓ a caixa vai para 15 dp acima dele, com uma
 *   mola suave. O Enter ("Done") e tocar fora fecham o teclado.
 * - Ditado: o microfone usa o reconhecimento de voz do Google em segundo plano; o texto dito
 *   substitui o que está na caixa. Se a permissão for recusada, o microfone desaparece.
 * - O nome vazio é tratado em [IntroductionFlow] (fica "Routine [Nº]").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Introduction3Page(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
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
            focusRequester.requestFocus()
            keyboard?.show()
            pendingKeyboard = false
        }
    }

    // Só a caixa se desloca: da sua posição em repouso para 15 dp acima do teclado.
    // O destino conta-se com a altura FINAL do teclado, por isso a mola começa logo a direito.
    val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
    var restBottom by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableFloatStateOf(0f) }
    val gap = with(density) { CycloDimens.Spacing.toPx() }
    val targetShift = if (imeTarget > 0 && rootHeight > 0f) rootHeight - imeTarget - gap - restBottom else 0f
    val shift by animateFloatAsState(
        targetValue = targetShift,
        animationSpec = CycloMotion.KeyboardSpring,
        label = "keyboardShift",
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(64.dp))
            }

            CycloNameField(
                value = name,
                onValueChange = onNameChange,
                isListening = dictation.isListening,
                micVisible = dictation.isMicVisible,
                onMicClick = {
                    focusManager.clearFocus()
                    dictation.start()
                },
                onCancelListening = { dictation.cancel() },
                onSwitchToKeyboard = {
                    dictation.cancel()
                    pendingKeyboard = true
                },
                onDone = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
                focusRequester = focusRequester,
                modifier = Modifier
                    .padding(horizontal = CycloDimens.ScreenMargin)
                    // Mede a posição em repouso (antes da deslocação, que vem a seguir).
                    .onGloballyPositioned { coordinates ->
                        restBottom = coordinates.positionInRoot().y + coordinates.size.height
                        rootHeight = coordinates.findRootCoordinates().size.height.toFloat()
                    }
                    .graphicsLayer { translationY = shift },
            )
        }
    }
}
