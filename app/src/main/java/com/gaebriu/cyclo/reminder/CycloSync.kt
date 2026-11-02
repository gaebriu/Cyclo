package com.gaebriu.cyclo.reminder

import android.content.Context
import android.os.SystemClock
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.CycloRepository
import com.gaebriu.cyclo.widget.BedtimeWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Mantém o lembrete e os widgets em dia com os dados, enquanto o processo da app existe (fica no
 * Application, não num ecrã). Fora disso, o próprio alarme e os eventos do sistema tratam do resto.
 *
 * Espera que a escrita assente antes de recalcular (debounce adaptativo): uma mudança isolada
 * espera [SettleMs]; mudanças seguidas (ex.: vários cafés, o medidor a ser arrastado) esperam
 * [BurstSettleMs] desde a última, e cada mudança nova recomeça a espera. Assim o widget só anima
 * uma vez, para a hora final.
 */
object CycloSync {
    private const val SettleMs = 900L
    private const val BurstSettleMs = 1800L
    private const val BurstGapMs = 2500L

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        BedtimeReminder.ensureChannel(app)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var lastChange = 0L
            var first = true
            CycloRepository(app).data
                .distinctUntilChanged()
                .collectLatest { data ->
                    val now = SystemClock.elapsedRealtime()
                    val gap = now - lastChange
                    lastChange = now
                    if (!first) delay(if (gap < BurstGapMs) BurstSettleMs else SettleMs)
                    apply(app, data, animateWidgets = !first)
                    first = false
                }
        }
    }

    /** Reagenda o lembrete e atualiza os widgets com estes dados. */
    fun apply(context: Context, data: CycloData, animateWidgets: Boolean) {
        runCatching { BedtimeReminder.reschedule(context, data) }
        runCatching { BedtimeWidgets.updateAll(context, data, animate = animateWidgets) }
    }
}
