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
import com.gaebriu.cyclo.data.DailyLog
import com.gaebriu.cyclo.data.Hydration
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.logsFor
import com.gaebriu.cyclo.data.wakeOn
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * O lembrete "Hydration" (Cyclo 3.1, Settings → Reminders e Introduction 6): lembra de beber água ao
 * longo do dia, conforme o que o utilizador registou, e não a intervalos fixos.
 *
 * - Só durante o dia ativo ([HydrationPlan.window]): de pouco depois da hora de acordar até umas
 *   horas antes de deitar (beber muito perto da cama acorda a meio da noite).
 * - Quem vai registando com regularidade não é incomodado: depois de um copo há sempre uma folga, e o
 *   lembrete só chega quando o dia fica um copo atrás do ritmo, ou quando passou muito tempo sem
 *   registos ([HydrationPlan.next]). Chegado ao objetivo do dia, não há mais lembretes.
 * - O objetivo é o do próprio utilizador: o que costuma beber (a mediana dos últimos dias com água
 *   registada), dentro de uma faixa à volta da quantidade adequada para a idade ([Hydration.targetMl]);
 *   sem histórico, essa quantidade. Não há uma quantidade "certa" igual para todos.
 * - Nunca mais do que [HydrationPlan.MaxPerDay] por dia, com pelo menos [HydrationPlan.MinGapBetween]
 *   entre eles. Os textos informam, não insistem.
 * - A mesma infraestrutura dos outros lembretes: um alarme INEXATO ([AlarmManager.setAndAllowWhileIdle]),
 *   o mesmo recetor ([BedtimeAlarmReceiver]) e o reagendamento do [CycloSync] a cada mudança nos dados
 *   (um copo registado adia logo o próximo). À hora, volta a ver tudo antes de avisar.
 * - Sem o interruptor ligado ou sem autorização de notificações (também a do canal), cancela-se.
 */
object HydrationReminder {
    const val ChannelId = "hydration"
    private const val NotificationId = 3
    private const val AlarmRequestCode = 5
    private const val OpenRequestCode = 6

    internal const val ActionRemind = "com.gaebriu.cyclo.action.HYDRATION_REMINDER"

    private const val Prefs = "hydration_reminder"
    private const val LastAtKey = "last_at"
    private const val DayKey = "day"
    private const val CountKey = "count"

    /** O interruptor como se mostra: ligado nas definições E com autorização (da app e do canal). */
    fun isOn(context: Context, data: CycloData): Boolean =
        data.hydrationReminder && BedtimeReminder.isPermitted(context) && BedtimeReminder.isChannelEnabled(context, ChannelId)

    /** Agenda (ou cancela) o próximo lembrete conforme os dados atuais. */
    fun reschedule(context: Context, data: CycloData, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        if (!isOn(context, data)) {
            alarms.cancel(alarmIntent(context))
            cancelNotification(context)
            return
        }
        val next = nextFor(context, data, now, zone)
        if (next == null) {
            alarms.cancel(alarmIntent(context))
            return
        }
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            maxOf(next.at, now).atZone(zone).toInstant().toEpochMilli(),
            alarmIntent(context),
        )
    }

    /** O próximo lembrete, hoje ou (sem mais nenhum hoje) amanhã. */
    private fun nextFor(context: Context, data: CycloData, now: LocalDateTime, zone: ZoneId): HydrationPlan.Next? {
        val prefs = prefs(context)
        // Um lembrete "no futuro" (a hora do telemóvel recuou) não conta: não atrasa os seguintes.
        val lastAt = prefs.getLong(LastAtKey, 0L).takeIf { it > 0L }
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            ?.takeIf { !it.isAfter(now) }
        for (offset in 0L..1L) {
            val day = now.toLocalDate().plusDays(offset)
            val logs = data.logsFor(day)
            val sentToday = if (prefs.getLong(DayKey, Long.MIN_VALUE) == day.toEpochDay()) prefs.getInt(CountKey, 0) else 0
            val next = HydrationPlan.next(
                now = if (offset == 0L) now else day.atStartOfDay(),
                window = HydrationPlan.window(data.routines, day, zone),
                log = logs.current,
                goalMl = HydrationPlan.goal(logs.history, day, data.age),
                lastReminder = lastAt,
                remindersToday = sentToday,
                zone = zone,
            )
            if (next != null) return next
        }
        return null
    }

    /** Mostra a notificação (chamado pelo alarme), só se ainda fizer sentido agora. */
    internal fun notify(context: Context, data: CycloData) {
        if (!isOn(context, data)) return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        // Entretanto pode ter registado água: o que vale é o plano de agora.
        val next = nextFor(context, data, now, zone) ?: return
        if (next.at.isAfter(now.plusMinutes(1))) return

        BedtimeReminder.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            OpenRequestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (next.kind) {
            HydrationPlan.Kind.First -> R.string.hydration_title_first to R.string.hydration_text_first
            HydrationPlan.Kind.Gap -> R.string.hydration_title_gap to R.string.hydration_text_gap
            HydrationPlan.Kind.Pace -> R.string.hydration_title_pace to R.string.hydration_text_pace
        }
        val body = context.getString(text)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_water)
            .setContentTitle(context.getString(title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Um lembrete da água perde o sentido depressa: sai sozinho se ficar por ver.
            .setTimeoutAfter(HydrationPlan.MinGapBetween.toMillis())
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        } catch (e: SecurityException) {
            return // a autorização foi tirada entretanto
        }
        val today = now.toLocalDate().toEpochDay()
        val prefs = prefs(context)
        val count = if (prefs.getLong(DayKey, Long.MIN_VALUE) == today) prefs.getInt(CountKey, 0) else 0
        prefs.edit()
            .putLong(LastAtKey, now.atZone(zone).toInstant().toEpochMilli())
            .putLong(DayKey, today)
            .putInt(CountKey, count + 1)
            .apply()
    }

    /** Tira a notificação, se estiver à vista (o lembrete foi desligado). */
    fun cancelNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NotificationId) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        AlarmRequestCode,
        Intent(context, BedtimeAlarmReceiver::class.java).setAction(ActionRemind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * As contas do lembrete da água (puras, HydrationPlanTest). Horas locais; os registos trazem as horas
 * de cada copo ([DailyLog.waterTimes], epoch ms).
 */
internal object HydrationPlan {
    /** Depois de um copo registado, esta folga sem lembretes (quem bebe com regularidade não é incomodado). */
    val AfterSip: Duration = Duration.ofMinutes(90)

    /** Sem registos há tanto tempo, lembra mesmo sem estar atrás do ritmo do dia. */
    val LongGap: Duration = Duration.ofHours(4)

    /** Entre dois lembretes, pelo menos isto. */
    val MinGapBetween: Duration = Duration.ofMinutes(150)

    /** No máximo, por dia. */
    const val MaxPerDay = 3

    /** O dia ativo começa este tempo depois de acordar… */
    val AfterWake: Duration = Duration.ofMinutes(30)

    /** …e dura no máximo isto, sem passar de [LatestEnd]: a água das últimas horas fica para o dia seguinte. */
    val ActiveSpan: Duration = Duration.ofHours(14)
    val LatestEnd: LocalTime = LocalTime.of(21, 30)

    /** Sem rotina a acordar nesse dia, o dia ativo começa a esta hora. */
    val DefaultStart: LocalTime = LocalTime.of(8, 30)

    /** O último lembrete do dia chega até este tempo antes do fim do dia ativo. */
    val EndMargin: Duration = Duration.ofMinutes(30)

    /** Quantos dias do histórico contam para o hábito, e quantos com água são precisos para valer. */
    const val HabitDays = 14L
    const val HabitMinDays = 3

    /** O hábito vale dentro desta faixa à volta da quantidade adequada para a idade. */
    const val HabitLow = 0.7f
    const val HabitHigh = 1.4f

    enum class Kind {
        /** Ainda nada registado hoje. */
        First,

        /** Muito tempo sem registos. */
        Gap,

        /** O dia ficou um copo atrás do ritmo. */
        Pace,
    }

    data class Window(val start: LocalDateTime, val end: LocalDateTime)

    data class Next(val at: LocalDateTime, val kind: Kind)

    /** O dia ativo de [day]: de [AfterWake] depois da primeira rotina a acordar (ou [DefaultStart]) a [ActiveSpan] depois, sem passar de [LatestEnd]. */
    fun window(routines: List<Routine>, day: LocalDate, zone: ZoneId): Window {
        val wake = routines.mapNotNull { it.wakeOn(day, zone) }.minOrNull()
        val start = wake?.plus(AfterWake) ?: day.atTime(DefaultStart)
        val end = minOf(start.plus(ActiveSpan), day.atTime(LatestEnd))
        return Window(start, end)
    }

    /**
     * O objetivo do dia [day], em mL: a mediana dos últimos [HabitDays] dias com água registada (com
     * pelo menos [HabitMinDays]), entre [HabitLow] e [HabitHigh] da quantidade adequada para a idade;
     * sem hábito, essa quantidade.
     */
    fun goal(history: List<DailyLog>, day: LocalDate, age: Int?): Int {
        val target = Hydration.targetMl(age)
        val from = day.minusDays(HabitDays)
        val recent = history.filter { it.date.isBefore(day) && !it.date.isBefore(from) && it.water > 0 }.map { it.water }.sorted()
        if (recent.size < HabitMinDays) return target
        val median = if (recent.size % 2 == 1) recent[recent.size / 2] else (recent[recent.size / 2 - 1] + recent[recent.size / 2]) / 2
        return median.coerceIn((target * HabitLow).toInt(), (target * HabitHigh).toInt())
    }

    /**
     * O próximo lembrete a partir de [now] dentro de [window] (null = nenhum neste dia):
     * - nenhum depois do objetivo, nem passado [MaxPerDay];
     * - quando o dia fica um copo ([Hydration.StepMl]) atrás de um ritmo constante até ao objetivo, ou
     *   [LongGap] depois do último copo (ou do início do dia), o que vier primeiro;
     * - nunca antes de [AfterSip] depois do último copo nem de [MinGapBetween] depois do último lembrete;
     * - nunca depois de [EndMargin] antes do fim do dia ativo.
     */
    fun next(
        now: LocalDateTime,
        window: Window,
        log: DailyLog,
        goalMl: Int,
        lastReminder: LocalDateTime?,
        remindersToday: Int,
        zone: ZoneId,
    ): Next? {
        if (remindersToday >= MaxPerDay || goalMl <= 0 || log.water >= goalMl) return null
        val lastSip = log.waterTimes.lastOrNull()
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            ?.takeIf { !it.isBefore(window.start.minus(ActiveSpan)) }
        val since = maxOf(lastSip ?: window.start, window.start)
        val span = Duration.between(window.start, window.end).toMillis().coerceAtLeast(1L)
        // A hora em que um ritmo constante já teria chegado a mais um copo do que o registado.
        val behind = window.start.plus(Duration.ofMillis(span * (log.water + Hydration.StepMl).coerceAtMost(goalMl) / goalMl))
        val longGap = since.plus(LongGap)
        var at = minOf(behind, longGap)
        lastSip?.let { at = maxOf(at, it.plus(AfterSip)) }
        lastReminder?.let { at = maxOf(at, it.plus(MinGapBetween)) }
        at = maxOf(at, window.start, now)
        if (at.isAfter(window.end.minus(EndMargin))) return null
        val kind = when {
            log.water <= 0 -> Kind.First
            !longGap.isAfter(behind) -> Kind.Gap
            else -> Kind.Pace
        }
        return Next(at, kind)
    }
}
