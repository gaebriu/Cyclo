package com.gaebriu.cyclo.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gaebriu.cyclo.MainActivity
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.BedtimeEngine
import com.gaebriu.cyclo.data.BedtimePlan
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.WindDownReason
import com.gaebriu.cyclo.ui.time.cycloTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * O lembrete "Bedtime": UMA notificação por noite, no início do wind-down (ver [BedtimeEngine]),
 * com a hora a que deve ir para a cama e, quando há, o porquê de começar já.
 *
 * - Só a rotina que acorda primeiro conta (ligada, com a repetição e os dias dela), como no Feed.
 * - Agenda-se com o AlarmManager do próprio Cyclo, INEXATO ([AlarmManager.setAndAllowWhileIdle]):
 *   não cria alarmes no relógio do sistema nem usa a app de alarmes, e não pede SCHEDULE_EXACT_ALARM.
 *   Pode chegar uns minutos depois, o que não importa para começar a abrandar.
 * - Um só PendingIntent (código fixo): reagendar substitui o anterior, nunca há dois lembretes.
 *   A noite já avisada fica guardada, para não repetir a notificação.
 * - Sem o interruptor ligado ou sem autorização de notificações, cancela-se.
 */
object BedtimeReminder {
    const val ChannelId = "bedtime"
    private const val NotificationId = 1
    private const val ReminderRequestCode = 1
    private const val RefreshRequestCode = 2

    internal const val ExtraRoutineId = "routine_id"
    internal const val ExtraWakeMillis = "wake_millis"
    internal const val ActionRemind = "com.gaebriu.cyclo.action.BEDTIME_REMINDER"
    internal const val ActionRefresh = "com.gaebriu.cyclo.action.REFRESH"

    /** Horas de acordar a menos de 12 h da última avisada contam como a mesma noite. */
    private const val SameNightMillis = 12L * 60 * 60 * 1000

    private const val Prefs = "bedtime_reminder"
    private const val NotifiedWakeKey = "notified_wake"

    /** A autorização de notificações (Android 13+) e as notificações da app ligadas no sistema. */
    fun isPermitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** O interruptor como se mostra: ligado nas definições E com autorização. */
    fun isOn(context: Context, data: CycloData): Boolean = data.bedtimeReminder && isPermitted(context)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ChannelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(ChannelId, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    /**
     * Agenda (ou cancela) o próximo lembrete conforme os dados atuais, e o refrescamento do widget
     * para quando a próxima hora de acordar passar.
     */
    fun reschedule(context: Context, data: CycloData, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()

        // Refrescar quando a próxima hora de acordar passar (o widget passa para a seguinte).
        val refresh = refreshIntent(context)
        val nextWake = BedtimeEngine.nextWake(data.routines, now)?.second
        if (nextWake != null) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, nextWake.plusMinutes(1).atZone(zone).toInstant().toEpochMilli(), refresh)
        } else {
            alarms.cancel(refresh)
        }

        val pending = reminderIntent(context, null)
        if (!isOn(context, data)) {
            alarms.cancel(pending)
            return
        }
        val notified = prefs(context).getLong(NotifiedWakeKey, 0L)
        val next = nextReminder(data, now, notified, zone)
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        val (routine, plan) = next
        val at = plan.windDownStart.atZone(zone).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at,
            reminderIntent(context, routine.id to plan.wake.atZone(zone).toInstant().toEpochMilli()),
        )
    }

    /**
     * O próximo lembrete: entre as próximas horas de acordar de cada rotina, o primeiro cujo
     * wind-down ainda está para vir e cuja noite não foi avisada. Duas rotinas que acordam na mesma
     * manhã (ex.: 07:00 e 08:00) são a mesma noite: só a primeira avisa.
     */
    internal fun nextReminder(data: CycloData, now: LocalDateTime, notifiedWakeMillis: Long, zone: ZoneId): Pair<Routine, BedtimePlan>? =
        data.routines
            .flatMap { routine -> BedtimeEngine.upcomingWakes(routine, now, 3).map { routine to it } }
            .filter { (_, wake) -> wake.atZone(zone).toInstant().toEpochMilli() > notifiedWakeMillis + SameNightMillis }
            .map { (routine, wake) ->
                routine to BedtimeEngine.plan(wake, now, data.age, data.chronotype, data.fallAsleep, data.dailyLog)
            }
            .filter { (_, plan) -> !plan.shortfall && plan.windDownStart.isAfter(now) }
            .minByOrNull { (_, plan) -> plan.windDownStart }

    /** Mostra a notificação da noite que acorda em [wakeMillis] (chamado pelo alarme). */
    internal fun notify(context: Context, data: CycloData, routineId: Long, wakeMillis: Long) {
        if (!isOn(context, data)) return
        // Esta noite já foi avisada (ex.: por outra rotina que acorda na mesma manhã).
        if (wakeMillis <= prefs(context).getLong(NotifiedWakeKey, 0L) + SameNightMillis) return
        val zone = ZoneId.systemDefault()
        val routine = data.routines.firstOrNull { it.id == routineId } ?: return
        val now = LocalDateTime.now()
        val wake = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(wakeMillis), zone)
        // A rotina mudou entretanto (desligada, outra hora, outros dias): não avisa desta noite.
        if (BedtimeEngine.upcomingWakes(routine, now, 3).none { it == wake }) return
        val plan = BedtimeEngine.plan(wake, now, data.age, data.chronotype, data.fallAsleep, data.dailyLog)
        val formatter = timeFormatter(context, data.use24Hour)
        val bed = plan.bedtime.format(formatter)
        val wakeText = plan.wake.format(formatter)

        val title: String
        val body: String
        if (plan.shortfall) {
            title = context.getString(R.string.reminder_title_now)
            body = context.getString(R.string.reminder_body_now, wakeText)
        } else {
            val reason = plan.reasons.firstOrNull()
            title = context.getString(if (reason == null) R.string.reminder_title else R.string.reminder_title_early)
            val tip = when (reason) {
                WindDownReason.Stress -> context.getString(R.string.reminder_tip_stress)
                WindDownReason.Caffeine -> context.getString(R.string.reminder_tip_caffeine)
                WindDownReason.Exercise -> context.getString(R.string.reminder_tip_exercise)
                WindDownReason.Meal -> context.getString(R.string.reminder_tip_meal)
                WindDownReason.Nap -> context.getString(R.string.reminder_tip_nap)
                WindDownReason.NightOwl -> context.getString(R.string.reminder_tip_night_owl)
                WindDownReason.EarlyBird -> context.getString(R.string.reminder_tip_early_bird)
                WindDownReason.BodyClock -> context.getString(R.string.reminder_tip_body_clock)
                // Sem nada de especial: uma dica diferente por noite.
                null -> context.resources.getStringArray(R.array.reminder_tips_default).let { tips ->
                    tips[plan.night.dayOfYear % tips.size]
                }
            }
            body = context.getString(R.string.reminder_body, bed, plan.sleepOnset.format(formatter), wakeText, tip)
        }

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_bedtime)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Some sozinha quando chega a hora de ir para a cama.
            .setTimeoutAfter(java.time.Duration.between(now, plan.bedtime).toMillis().coerceAtLeast(60_000L))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        } catch (e: SecurityException) {
            return // a autorização foi tirada entretanto
        }
        prefs(context).edit().putLong(NotifiedWakeKey, wakeMillis).apply()
    }

    /** Formato das horas: o escolhido nas definições ou, sem escolha, o do sistema. */
    fun timeFormatter(context: Context, use24Hour: Boolean?): DateTimeFormatter {
        val is24 = use24Hour ?: DateFormat.is24HourFormat(context)
        return cycloTimeFormatter(is24)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    private fun reminderIntent(context: Context, target: Pair<Long, Long>?): PendingIntent {
        val intent = Intent(context, BedtimeAlarmReceiver::class.java).setAction(ActionRemind)
        if (target != null) {
            intent.putExtra(ExtraRoutineId, target.first).putExtra(ExtraWakeMillis, target.second)
        }
        return PendingIntent.getBroadcast(
            context,
            ReminderRequestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        RefreshRequestCode,
        Intent(context, BedtimeAlarmReceiver::class.java).setAction(ActionRefresh),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
