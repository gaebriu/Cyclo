package com.gaebriu.cyclo.ui.components

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gaebriu.cyclo.R
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

    /** A caixa está a ser usada: a ouvir, ou a passar do ditado para o teclado. */
    val isBusy: Boolean get() = dictation.isListening || pendingKeyboard

    /** Dá o foco à caixa e abre o teclado (ex.: ao abrir a pesquisa). */
    fun openKeyboard() {
        pendingKeyboard = true
    }
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

/** Quanto tempo, no máximo, se espera que o teclado apareça depois de o pedir (ms). */
private const val KeyboardWaitMs = 1_500L

/**
 * A caixa do nome com tudo ligado: sobe sozinha para cima do teclado ([KeyboardLift]), o Enter
 * ("Done") fecha o teclado, e o ícone do teclado volta do ditado para a escrita.
 *
 * @param onEnter chamado no Enter (tecla do teclado no ecrã ou física), antes de o teclado fechar;
 *        a pesquisa usa-o para decidir se a caixa fecha. null = só fecha o teclado (o nome da rotina).
 */
@Composable
fun RoutineNameInput(
    state: RoutineNameInputState,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    lift: Boolean = true,
    search: Boolean = false,
    onEnter: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime

    // "Voltar ao teclado": deixa de ouvir e, quando a caixa já aceita foco, abre o teclado. O pedido
    // só acaba quando o teclado está mesmo à vista (ou ao fim de [KeyboardWaitMs]): antes acabava logo,
    // e no intervalo até o teclado aparecer a caixa não contava como ativa, por isso a pesquisa vazia
    // fechava-se sozinha quando o teclado demorava mais do que a espera da pesquisa.
    LaunchedEffect(state.pendingKeyboard, state.dictation.isListening) {
        if (state.pendingKeyboard && !state.dictation.isListening) {
            withFrameNanos { } // espera que a caixa volte a estar ativa
            // Se a caixa já tiver saído do ecrã, não há nada a focar.
            runCatching { state.focusRequester.requestFocus() }
            keyboard?.show()
            withTimeoutOrNull(KeyboardWaitMs) {
                snapshotFlow { ime.getBottom(density) > 0 }.first { it }
            }
            state.pendingKeyboard = false
        }
    }

    val field: @Composable () -> Unit = {
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
                onEnter?.invoke()
                focusManager.clearFocus()
                keyboard?.hide()
            },
            focusRequester = state.focusRequester,
            placeholder = placeholder ?: stringResource(R.string.introduction_3_placeholder),
            search = search,
        )
    }
    // Sem [lift], a caixa fica onde está (ex.: a pesquisa, já no topo do ecrã).
    if (lift) KeyboardLift(modifier = modifier) { field() } else Box(modifier = modifier) { field() }
}
