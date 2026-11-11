package com.gaebriu.cyclo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gaebriu.cyclo.MainActivity
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.logsFor
import com.gaebriu.cyclo.data.wakeOn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * O lembrete "Log sleep" (Beta 2.3, reminders-group das definições): UMA notificação por dia,
 * [Delay] depois da hora de acordar desse dia, a pedir para registar como se dormiu. Tocar nela abre
 * o Cyclo na Tracking, com o action-menu-log-sleep aberto ([ActionOpen]).
 *
 * - A hora de acordar do dia é a mesma que começa o dia da Tracking (e mostra o prompt do sono): a
 *   mais cedo das rotinas ligadas que acordam nesse dia ([wakeOn]). Dias sem rotina não avisam.
 * - Usa a mesma infraestrutura do lembrete "Bedtime" ([BedtimeReminder]): o AlarmManager do Cyclo,
 *   INEXATO ([AlarmManager.setAndAllowWhileIdle]: o Android pode entregá-lo até ~1 h depois, para
 *   juntar alarmes e poupar bateria), o mesmo recetor ([BedtimeAlarmReceiver]) e o mesmo
 *   reagendamento ([CycloSync]: dados mudados, arranque do telemóvel, hora ou fuso mudados).
 * - Um só PendingIntent (código fixo): reagendar substitui o anterior. O dia avisado (ou dado como
 *   tratado) fica guardado, e um dia com o sono já registado não avisa: nunca há duas notificações
 *   no mesmo dia.
 * - Um lembrete já devido mas ainda por entregar (até [LateGrace] depois da hora) continua de pé
 *   quando se reagenda: abrir a app, ou outra mudança nos dados, nessa espera não o perde (o
 *   alarme dispara logo). Mais atrasado do que isso (ex.: telemóvel desligado), espera pelo dia
 *   seguinte, sem avisos atrasados. Um lembrete que passaria da meia-noite não conta (o registo é
 *   do dia em que se acorda).
 * - A notificação sai sozinha no fim do dia (o registo é só do próprio dia) e quando o sono é
 *   registado ou o lembrete é desligado.
 * - Sem o interruptor ligado ou sem autorização de notificações (também a do canal), cancela-se.
 */
object LogSleepReminder {
    const val ChannelId = "log_sleep"
    private const val NotificationId = 2
    private const val AlarmRequestCode = 3
    private const val OpenRequestCode = 4

    /** Quanto depois da hora de acordar chega o lembrete. */
    val Delay: Duration = Duration.ofMinutes(30)

    /**
     * Um lembrete devido há menos do que isto, e ainda por entregar, continua de pé (a janela com que
     * o Android pode atrasar um alarme inexato).
     */
    val LateGrace: Duration = Duration.ofHours(1)

    /** Até quantos dias à frente se procura o próximo dia com uma rotina a acordar. */
    private const val LookAheadDays = 8L

    internal const val ActionRemind = "com.gaebriu.cyclo.action.LOG_SLEEP_REMINDER"
    internal const val ExtraDay = "day"

    /** O toque na notificação: a [MainActivity] abre a Tracking com o action-menu-log-sleep. */
    const val ActionOpen = "com.gaebriu.cyclo.action.OPEN_LOG_SLEEP"

    private const val Prefs = "log_sleep_reminder"
    private const val NotifiedDayKey = "notified_day"

    /** O interruptor como se mostra: ligado nas definições E com autorização (da app e do canal). */
    fun isOn(context: Context, data: CycloData): Boolean =
        data.logSleepReminder && BedtimeReminder.isPermitted(context) && BedtimeReminder.isChannelEnabled(context, ChannelId)

    /** Agenda (ou cancela) o próximo lembrete conforme os dados atuais. */
    fun reschedule(context: Context, data: CycloData, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val on = isOn(context, data)
        // Desligado, ou o sono de hoje já registado: a notificação de hoje (se estiver à vista) sai.
        if (!on || sleepLogged(data, now.toLocalDate())) cancelNotification(context)
        if (!on) {
            alarms.cancel(alarmIntent(context, null))
            return
        }
        val next = nextReminder(data, now, notifiedDay(context), zone)
        if (next == null) {
            alarms.cancel(alarmIntent(context, null))
            return
        }
        val (day, at) = next
        // Já devido (dentro de [LateGrace]): dispara já; o notify marca o dia e não se repete.
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            maxOf(at, now).atZone(zone).toInstant().toEpochMilli(),
            alarmIntent(context, day),
        )
    }

    /**
     * O próximo lembrete: o primeiro dia (a partir de hoje) com uma rotina a acordar, ainda não
     * avisado e sem o sono registado, cuja hora (acordar + [Delay], no próprio dia) ainda está para
     * vir ou passou há menos de [LateGrace].
     */
    internal fun nextReminder(
        data: CycloData,
        now: LocalDateTime,
        notifiedDay: Long,
        zone: ZoneId,
    ): Pair<LocalDate, LocalDateTime>? {
        val today = now.toLocalDate()
        for (offset in 0..LookAheadDays) {
            val day = today.plusDays(offset)
            if (day.toEpochDay() <= notifiedDay) continue
            if (sleepLogged(data, day)) continue
            val at = reminderTime(data.routines, day, zone) ?: continue
            if (at.toLocalDate() != day) continue
            if (at.isAfter(now.minus(LateGrace))) return day to at
        }
        return null
    }

    /** A hora do lembrete do dia [day] (null = nenhuma rotina acorda nesse dia). */
    internal fun reminderTime(routines: List<Routine>, day: LocalDate, zone: ZoneId): LocalDateTime? =
        routines.mapNotNull { it.wakeOn(day, zone) }.minOrNull()?.plus(Delay)

    /**
     * Mostra a notificação do dia [epochDay] (chamado pelo alarme), se ainda fizer sentido. Quando
     * esse dia já não deve avisar (passou, o sono já está registado, já não há rotina nele), fica dado como tratado: o reagendamento a seguir passa ao dia seguinte, em
     * vez de voltar a pôr um alarme no passado (que dispararia outra vez, em ciclo).
     */
    internal fun notify(context: Context, data: CycloData, epochDay: Long) {
        if (!isOn(context, data)) return
        if (epochDay <= notifiedDay(context)) return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val day = LocalDate.ofEpochDay(epochDay)
        fun skipDay() = prefs(context).edit().putLong(NotifiedDayKey, epochDay).apply()
        // Um alarme de outro dia não avisa: o registo é só do próprio dia.
        if (day != now.toLocalDate()) return skipDay()
        if (sleepLogged(data, day)) return skipDay()
        // As rotinas mudaram entretanto (outra hora, desligada, sem rotina hoje): a hora certa é a
        // de agora. Se ainda não chegou, o reagendamento (CycloSync) já pôs o alarme certo.
        val at = reminderTime(data.routines, day, zone) ?: return skipDay()
        if (now.isBefore(at.minusMinutes(1))) return


        BedtimeReminder.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            OpenRequestCode,
            Intent(context, MainActivity::class.java)
                .setAction(ActionOpen)
                // SINGLE_TOP: com a app aberta, a mesma atividade recebe o pedido (onNewIntent), sem
                // ser recriada; fechada, abre de raiz e trata-o no onCreate.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Os textos do Figma são os do sleep-evaluation-prompt da Tracking (os mesmos strings).
        val body = context.getString(R.string.sleep_prompt_text)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_bedtime)
            .setContentTitle(context.getString(R.string.sleep_prompt_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Sai sozinha à meia-noite: amanhã o registo já seria de outro dia.
            .setTimeoutAfter(Duration.between(now, day.plusDays(1).atStartOfDay()).toMillis().coerceAtLeast(60_000L))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        } catch (e: SecurityException) {
            return // a autorização foi tirada entretanto
        }
        prefs(context).edit().putLong(NotifiedDayKey, epochDay).apply()
    }

    /** Tira a notificação, se estiver à vista (o sono foi registado, ou o lembrete desligado). */
    fun cancelNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationId) }
    }

    private fun sleepLogged(data: CycloData, day: LocalDate): Boolean =
        data.logsFor(day).current.sleep != null

    private fun notifiedDay(context: Context): Long = prefs(context).getLong(NotifiedDayKey, Long.MIN_VALUE)

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    private fun alarmIntent(context: Context, day: LocalDate?): PendingIntent {
        val intent = Intent(context, BedtimeAlarmReceiver::class.java).setAction(ActionRemind)
        if (day != null) intent.putExtra(ExtraDay, day.toEpochDay())
        return PendingIntent.getBroadcast(
            context,
            AlarmRequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
