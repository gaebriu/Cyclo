package com.gaebriu.cyclo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.gaebriu.cyclo.ui.dictation.SpeechDictationState
import com.gaebriu.cyclo.ui.dictation.rememberSpeechDictation
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.keyboard.KeyboardLift

/**
 * Estado da entrada do nome da rotina: ditado por voz, foco e a passagem do ditado para o
 * teclado. Partilhado pelos ecrãs que têm a caixa do nome (Introduction 3 e 4).
 */
@Stable
class RoutineNameInputState internal constructor(
    internal val dictation: SpeechDictationState,
    internal val focusRequester: FocusRequester,
    internal val completions: MutableIntState,
) {
    /** Se o ditado por voz está ativo (a ouvir). */
    val isDictating: Boolean get() = dictation.isListening

    internal var pendingKeyboard by mutableStateOf(false)
}

/**
 * @param onNameChange recebe o nome ditado (já cortado ao máximo), que substitui o texto atual.
 * Quando o ditado completa dá um háptico de confirmação; entrar e sair do ditado pelos botões não dá háptico.
 */
@Composable
fun rememberRoutineNameInput(onNameChange: (String) -> Unit): RoutineNameInputState {
    val view = LocalView.current
    val latestOnNameChange by rememberUpdatedState(onNameChange)
    val completions = remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val dictation = rememberSpeechDictation { spoken ->
        latestOnNameChange(spoken.trim().take(MaxRoutineNameLength))
        completions.intValue++
        CycloHaptics.confirm(view)
    }
    return remember(dictation) { RoutineNameInputState(dictation, focusRequester, completions) }
}

/**
 * A caixa do nome com tudo ligado: sobe sozinha para cima do teclado ([KeyboardLift]), o Enter
 * ("Done") fecha o teclado, e o ícone do teclado volta do ditado para a escrita.
 */
@Composable
fun RoutineNameInput(
    state: RoutineNameInputState,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // "Voltar ao teclado": deixa de ouvir e, quando a caixa já aceita foco, abre o teclado.
    LaunchedEffect(state.pendingKeyboard, state.dictation.isListening) {
        if (state.pendingKeyboard && !state.dictation.isListening) {
            withFrameNanos { } // espera que a caixa volte a estar ativa
            // Se a caixa já tiver saído do ecrã, não há nada a focar.
            runCatching { state.focusRequester.requestFocus() }
            keyboard?.show()
            state.pendingKeyboard = false
        }
    }

    KeyboardLift(modifier = modifier) {
        CycloNameField(
            value = value,
            onValueChange = onValueChange,
            isListening = state.dictation.isListening,
            micVisible = state.dictation.isMicVisible,
            completionKey = state.completions.intValue,
            onMicClick = {
                focusManager.clearFocus()
                state.dictation.start()
            },
            onSwitchToKeyboard = {
                state.dictation.cancel()
                state.pendingKeyboard = true
            },
            onDone = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
            focusRequester = state.focusRequester,
        )
    }
}
