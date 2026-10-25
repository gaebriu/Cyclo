package com.gaebriu.cyclo.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Feedback háptico com as constantes oficiais do Android (View.performHapticFeedback).
 * Respeita as definições do sistema (feedback ao toque) e as capacidades do aparelho.
 */
object CycloHaptics {
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
