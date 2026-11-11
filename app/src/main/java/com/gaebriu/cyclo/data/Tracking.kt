package com.gaebriu.cyclo.data

import androidx.compose.runtime.Immutable
import com.gaebriu.cyclo.ui.routine.RepeatOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * A Tracking (Figma Cyclo-2, 2001:115 e 2006:449): o histórico de cada dia e as contas dos três
 * cartões (Restfulness, Caffeine intake, Mood). Tudo puro e testado (TrackingTest).
 *
 * Regras (decididas com o utilizador, Beta 2.0):
 * - Um dia "começa" à hora de acordar mais cedo das rotinas ligadas que acordam nesse dia
 *   ([trackingDayStart]); sem nenhuma nesse dia (ou sem rotinas), começa à meia-noite.
 * - A app conta como aberta num dia ([DailyLog.opened]) se foi aberta depois desse início. Só então a
 *   cafeína e o humor desse dia contam; senão o dia fica "por registar" (cinzento).
 * - Aberta e sem tocar no medidor de emoções, o humor conta como Okay (o valor de omissão).
 * - O sono regista-se no próprio dia (a noite que acabou nessa manhã), uma vez por dia.
 * - Semana = os últimos 7 dias (hoje incluído). Mês = o mês do calendário (blocos 1..28-31).
 * - As médias só contam os dias com dados; sem nenhum, o ícone da média não aparece.
 */

/** Quando esta rotina acorda o utilizador no dia [date] (null = não acorda nesse dia, ou está desligada). */
fun Routine.wakeOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
    if (!active) return null
    if (repeat == RepeatOption.JustOnce) {
        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchorMillis), zone)
        var wake = anchor.toLocalDate().atTime(wakeHour, wakeMinute)
        if (!wake.isAfter(anchor)) wake = wake.plusDays(1)
        return wake.takeIf { it.toLocalDate() == date }
    }
    return date.atTime(wakeHour, wakeMinute).takeIf { repeatMask and date.weekdayBit() != 0 }
}

/**
 * O início do dia [date] para a Tracking: a hora de acordar mais cedo das rotinas ligadas que
 * acordam nesse dia; sem nenhuma, a meia-noite (qualquer abertura conta).
 */
fun trackingDayStart(routines: List<Routine>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    routines.mapNotNull { it.wakeOn(date, zone) }.minOrNull() ?: date.atStartOfDay()

/** Se abrir a app agora ([now]) conta como "aberta" no dia de hoje. */
fun countsAsOpened(routines: List<Routine>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    !now.isBefore(trackingDayStart(routines, now.toLocalDate(), zone))

/**
 * O dia da Cyclo a que [now] pertence (beta-3.0): o dia do calendário, mas só a partir do início
 * dele ([trackingDayStart], a primeira hora de acordar); antes disso (da meia-noite até acordar)
 * ainda é o dia anterior. É a data do registo do Feed ([DailyLog]): a cafeína, os fatores e as
 * emoções da noite não voltam a zero à meia-noite, e o wake-time-container (e o Wake Time Widget e o
 * lembrete) só passa para o dia seguinte quando passa a hora de acordar.
 */
fun cycloDay(routines: List<Routine>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    if (countsAsOpened(routines, now, zone)) now.toLocalDate() else now.toLocalDate().minusDays(1)

/**
 * O prompt do sono (sleep-evaluation-prompt) aparece desde o início do dia (a hora de acordar dessa
 * manhã) até o sono de hoje estar registado; o X esconde-o até a app voltar a abrir ([dismissed]).
 */
fun showsSleepPrompt(
    routines: List<Routine>,
    today: DailyLog?,
    now: LocalDateTime,
    dismissed: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (dismissed) return false
    // Antes de acordar (da meia-noite à hora de acordar) ainda é o dia anterior: sem prompt.
    if (!countsAsOpened(routines, now, zone)) return false
    val log = today?.takeIf { it.date == now.toLocalDate() }
    return log?.sleep == null
}

/** Os registos de cada dia: o histórico mais o registo atual (o mais recente ganha). */
fun dayLogs(history: List<DailyLog>, current: DailyLog?): Map<LocalDate, DailyLog> {
    val map = HashMap<LocalDate, DailyLog>(history.size + 1)
    history.forEach { map[it.date] = it }
    current?.let { map[it.date] = it }
    return map
}

/** Opacidade de um bloco do Restfulness: Really well a 100 %, Very poor a 9 %, os outros pelo meio. */
fun SleepQuality.blockAlpha(): Float = 1f - ordinal * (1f - MinBlockAlpha) / (SleepQuality.entries.size - 1)

/** A opacidade mínima dos blocos (o "0 cafés" e o "Very poor"). */
const val MinBlockAlpha = 0.09f

/** A média (arredondada ao nível mais perto) de uma lista de níveis; null sem nenhum. */
fun averageQuality(values: List<SleepQuality>): SleepQuality? =
    if (values.isEmpty()) null else SleepQuality.entries[values.map { it.ordinal }.average().roundToInt().coerceIn(0, 4)]

/** Um dia de um gráfico semanal ou de uma grelha mensal. */
@Immutable
data class TrackedDay<T>(val date: LocalDate, val value: T?)

/** Tudo o que os cartões da Tracking mostram, calculado de uma vez. */
@Immutable
data class TrackingSummary(
    /** Os últimos 7 dias (o último é hoje): como dormiu em cada um (null = por registar). */
    val sleepWeek: List<TrackedDay<SleepQuality>>,
    /** Os dias do mês atual (1 ao último), com o nível do sono (null = por registar ou ainda por vir). */
    val sleepMonth: List<TrackedDay<SleepQuality>>,
    /** A média do mês (ícone "average" do Restfulness); null sem registos. */
    val sleepMonthAverage: SleepQuality?,

    /** Os últimos 7 dias: os cafés de cada um (null = a app não foi aberta nesse dia). */
    val caffeineWeek: List<TrackedDay<Int>>,
    /** O máximo da semana (a linha de cima do gráfico); 0 sem cafés ou sem dados. */
    val caffeineWeekMax: Int,
    /** A média diária da semana, arredondada (o ícone ao lado do máximo); null sem dados. */
    val caffeineWeekAverage: Int?,
    /** Os dias do mês atual: os cafés de cada um (null = por registar ou ainda por vir). */
    val caffeineMonth: List<TrackedDay<Int>>,
    /** O máximo do mês (100 % de opacidade); 0 sem cafés. */
    val caffeineMonthMax: Int,
    /** A média diária do mês, arredondada (o ícone "average"); null sem dados. */
    val caffeineMonthAverage: Int?,

    /** Os dias do mês atual: o nível do medidor de emoções (0 Great ... 4 Bad; null = por registar). */
    val moodMonth: List<TrackedDay<Int>>,
    /**
     * A média do mês do humor (average-mood, Cyclo-2 2032:48), arredondada ao nível mais perto;
     * conta os dias abertos (também os "Okay" de quem não mexeu no medidor, como a grelha); null
     * sem dias abertos.
     */
    val moodMonthAverage: Int?,
) {
    /** Opacidade de um bloco da cafeína no mês: 0 cafés a 9 %, o máximo do mês a 100 %. */
    fun caffeineBlockAlpha(count: Int): Float =
        if (caffeineMonthMax <= 0) MinBlockAlpha else MinBlockAlpha + (1f - MinBlockAlpha) * (count.toFloat() / caffeineMonthMax).coerceIn(0f, 1f)
}

/** Monta o [TrackingSummary] para o dia [today]. */
fun trackingSummary(history: List<DailyLog>, current: DailyLog?, today: LocalDate): TrackingSummary {
    val logs = dayLogs(history, current)
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val month = YearMonth.from(today)
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }

    fun sleepOf(date: LocalDate) = logs[date]?.sleep
    fun caffeineOf(date: LocalDate) = logs[date]?.takeIf { it.opened && !date.isAfter(today) }?.caffeine
    fun moodOf(date: LocalDate) = logs[date]?.takeIf { it.opened && !date.isAfter(today) }?.moodLevel

    val sleepMonth = monthDays.map { TrackedDay(it, sleepOf(it)) }
    val caffeineWeek = week.map { TrackedDay(it, caffeineOf(it)) }
    val caffeineMonth = monthDays.map { TrackedDay(it, caffeineOf(it)) }
    val weekCounts = caffeineWeek.mapNotNull { it.value }
    val monthCounts = caffeineMonth.mapNotNull { it.value }
    val moodMonth = monthDays.map { TrackedDay(it, moodOf(it)) }
    return TrackingSummary(
        sleepWeek = week.map { TrackedDay(it, sleepOf(it)) },
        sleepMonth = sleepMonth,
        sleepMonthAverage = averageQuality(sleepMonth.mapNotNull { it.value }),
        caffeineWeek = caffeineWeek,
        caffeineWeekMax = weekCounts.maxOrNull() ?: 0,
        caffeineWeekAverage = weekCounts.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        caffeineMonth = caffeineMonth,
        caffeineMonthMax = monthCounts.maxOrNull() ?: 0,
        caffeineMonthAverage = monthCounts.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        moodMonth = moodMonth,
        moodMonthAverage = moodMonth.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
    )
}

/** Quantas noites registadas, nos últimos [SleepAverageWindowDays] dias, a bola precisa para começar na média. */
const val SleepAverageMinNights = 7
const val SleepAverageWindowDays = 30L

/**
 * Onde começa a bola do sleep-quality-meter: na média dos últimos 30 dias, com pelo menos 7 noites
 * registadas; senão em Really well (o estado do Figma).
 */
fun sleepMeterStart(history: List<DailyLog>, current: DailyLog?, today: LocalDate): SleepQuality {
    val from = today.minusDays(SleepAverageWindowDays - 1)
    val nights = dayLogs(history, current).values
        .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        .mapNotNull { it.sleep }
    if (nights.size < SleepAverageMinNights) return SleepQuality.ReallyWell
    return averageQuality(nights) ?: SleepQuality.ReallyWell
}
