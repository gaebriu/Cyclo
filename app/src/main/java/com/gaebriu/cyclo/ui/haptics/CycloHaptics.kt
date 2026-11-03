package com.gaebriu.cyclo.ui.haptics

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Feedback háptico do Cyclo. Todos os hápticos da app passam por aqui, para serem consistentes e
 * acompanharem o movimento (cada um dispara no momento visual a que pertence: pegar, passar um
 * limite, encaixar, apagar...).
 *
 * - Sempre que há uma constante do Android para o momento, usa-se View.performHapticFeedback:
 *   respeita a definição "Feedback tátil" do sistema e o que o aparelho sabe fazer.
 * - Os dois momentos sem constante (apagar e repor) usam primitivas de vibração compostas (como o
 *   Taptic Engine), só se o aparelho as suportar, e com USAGE_TOUCH: o sistema aplica-lhes a mesma
 *   intensidade e o mesmo interruptor do feedback ao toque. Sem primitivas, uma constante parecida.
 * - Nenhum háptico espera por nada: são pedidos ao sistema e a animação segue.
 * - Intensidades baixas e um só impulso por momento; nos gestos contínuos, só nos momentos que
 *   contam (passar o limite, mudar de lugar), nunca a cada frame.
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

    /** Um gesto passou o limite a partir do qual a ação acontece ao largar (ex.: mudar de página). */
    fun thresholdCrossed(view: View, activated: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (activated) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    /** Pegar num elemento para o arrastar (long press): DRAG_START (Android 14+) ou LONG_PRESS. */
    fun dragStart(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.DRAG_START
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    /** O elemento arrastado trocou de lugar com um vizinho: um tique de segmento, leve. */
    fun reorderStep(view: View) = pickerTick(view, manyChoices = false)

    /** Largou-se o elemento e ele encaixou: o fim do gesto. */
    fun drop(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    }

    /**
     * Um cartão apagado parte-se em partículas: um clique seco e dois tiques que se apagam, ao ritmo
     * das partículas a espalharem-se. Sem primitivas, o tique de confirmação.
     */
    fun shatter(view: View) {
        if (!compose(view) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f, 28)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.18f, 34)
            }
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    /** Um cartão reposto volta com zoom: uma subida curta e um clique leve quando passa o tamanho. */
    fun popIn(view: View) {
        if (!compose(view) {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.3f)
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.45f, 40)
            }
        ) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /**
     * Toca uma composição de primitivas, se o aparelho as suportar todas. Com USAGE_TOUCH: o
     * sistema trata-a como feedback ao toque (respeita o interruptor e a intensidade).
     */
    private fun compose(view: View, build: VibrationEffect.Composition.() -> Unit): Boolean {
        if (!view.isHapticFeedbackEnabled) return true
        val vibrator = view.context.getSystemService(Vibrator::class.java) ?: return false
        if (!vibrator.hasVibrator()) return true
        val composition = VibrationEffect.startComposition()
        return runCatching {
            val primitives = intArrayOf(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            )
            if (!vibrator.areAllPrimitivesSupported(*primitives)) return false
            composition.build()
            vibrator.vibrate(
                composition.compose(),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
            true
        }.getOrDefault(false)
    }
}
