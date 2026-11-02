package com.gaebriu.cyclo.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Feedback háptico com as constantes oficiais do Android (View.performHapticFeedback).
 * Respeita as definições do sistema (feedback ao toque) e as capacidades do aparelho.
 * Todos os hápticos da app passam por aqui, para serem consistentes.
 */
object CycloHaptics {
    /**
     * Toque num botão: o tique mais suave (CONTEXT_CLICK). Todos os botões usam este.
     */
    fun buttonTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /**
     * Algo ficou completo com sucesso (ex.: o nome ditado chegou à caixa): CONFIRM, que é o
     * efeito do Android para "confirmação / conclusão com êxito".
     */
    fun confirm(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Algo falhou (ex.: não se conseguiu guardar): REJECT, o efeito do Android para recusa / erro. */
    fun reject(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /**
     * Ligar ou desligar uma opção (ex.: um dia da semana). TOGGLE_ON / TOGGLE_OFF a partir do
     * Android 14; antes usa o tique suave dos botões.
     */
    fun toggle(view: View, isOn: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (isOn) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(constant)
    }

    /**
     * Passagem por um valor de um seletor.
     *
     * Segundo a documentação do Android: SEGMENT_TICK é para uma série de escolhas (ex.: pontos de
     * um slider) e SEGMENT_FREQUENT_TICK para muitas escolhas seguidas (ex.: minutos de um relógio),
     * onde o háptico deve ser mais leve. Antes do Android 14 usa-se CLOCK_TICK.
     */
    fun pickerTick(view: View, manyChoices: Boolean) {
        val constant = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                if (manyChoices) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.SEGMENT_TICK
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }
}
