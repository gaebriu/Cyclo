package com.gaebriu.cyclo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gaebriu.cyclo.data.CycloRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Recebe os alarmes dos lembretes "Bedtime" e "Log sleep" (mostram a notificação), o de refrescar
 * (a hora de acordar passou) e
 * os eventos do sistema que desfazem os alarmes ou mudam as horas (arranque, app atualizada, hora
 * ou fuso mudados). Em todos os casos volta a agendar e a atualizar os widgets.
 */
class BedtimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val data = CycloRepository(app).data.first()
                if (intent.action == BedtimeReminder.ActionRemind) {
                    val routineId = intent.getLongExtra(BedtimeReminder.ExtraRoutineId, -1L)
                    val wake = intent.getLongExtra(BedtimeReminder.ExtraWakeMillis, -1L)
                    if (routineId >= 0 && wake > 0) BedtimeReminder.notify(app, data, routineId, wake)
                }
                if (intent.action == LogSleepReminder.ActionRemind && intent.hasExtra(LogSleepReminder.ExtraDay)) {
                    LogSleepReminder.notify(app, data, intent.getLongExtra(LogSleepReminder.ExtraDay, Long.MIN_VALUE))
                }
                if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
                    CycloSync.languageChanged(app, data)
                } else {
                    CycloSync.apply(app, data, animateWidgets = false)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
