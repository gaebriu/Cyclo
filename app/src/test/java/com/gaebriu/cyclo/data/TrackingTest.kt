package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class TrackingTest {
    private val zone = ZoneOffset.UTC

    // Sábado, 7 de novembro de 2026.
    private val today = LocalDate.of(2026, 11, 7)

    private fun routine(id: Long, h: Int, m: Int, repeat: RepeatOption, mask: Int = 0, active: Boolean = true) =
        Routine(id, "R$id", h, m, repeat, mask, active, anchorMillis = 0L)

    @Test
    fun dayStartsAtTheEarliestWakeOfThatDay() {
        val work = routine(1, 7, 0, RepeatOption.DuringWeek)
        val weekend = routine(2, 9, 30, RepeatOption.DuringWeekend)
        val gym = routine(3, 6, 15, RepeatOption.Everyday, active = false)
        val routines = listOf(work, weekend, gym)
        // Sábado: só a de fim de semana acorda (a do ginásio está desligada).
        assertEquals(today.atTime(9, 30), trackingDayStart(routines, today, zone))
        // Segunda: só a do trabalho.
        assertEquals(LocalDate.of(2026, 11, 9).atTime(7, 0), trackingDayStart(routines, LocalDate.of(2026, 11, 9), zone))
        // Com as duas no mesmo dia, a mais cedo.
        val early = routine(4, 5, 45, RepeatOption.SelectedDays, mask = 1 shl 6)
        assertEquals(today.atTime(5, 45), trackingDayStart(routines + early, today, zone))
    }

    @Test
    fun withoutRoutinesThatDayAnyOpenCounts() {
        assertEquals(today.atStartOfDay(), trackingDayStart(emptyList(), today, zone))
        val weekdays = listOf(routine(1, 7, 0, RepeatOption.DuringWeek))
        assertTrue(countsAsOpened(weekdays, today.atTime(0, 5), zone))
    }

    @Test
    fun openingBeforeTheWakeDoesNotCount() {
        val routines = listOf(routine(1, 7, 0, RepeatOption.Everyday))
        assertFalse(countsAsOpened(routines, today.atTime(6, 59), zone))
        assertTrue(countsAsOpened(routines, today.atTime(7, 0), zone))
    }

    @Test
    fun cycloDayEndsAtTheWakeNotAtMidnight() {
        val routines = listOf(routine(1, 7, 0, RepeatOption.Everyday))
        val yesterday = today.minusDays(1)
        // Da meia-noite até à hora de acordar ainda é o dia anterior.
        assertEquals(yesterday, cycloDay(routines, yesterday.atTime(23, 30), zone))
        assertEquals(yesterday, cycloDay(routines, today.atTime(0, 0), zone))
        assertEquals(yesterday, cycloDay(routines, today.atTime(6, 59), zone))
        assertEquals(today, cycloDay(routines, today.atTime(7, 0), zone))
        // Sem rotina nesse dia, muda à meia-noite.
        assertEquals(today, cycloDay(emptyList(), today.atTime(0, 0), zone))
    }

    @Test
    fun feedBedtimesDoNotChangeAtMidnight() {
        val routine = routine(1, 7, 0, RepeatOption.Everyday)
        val routines = listOf(routine)
        val evening = today.atTime(21, 0)
        // Dois cafés às 20:00 e "Bad": a noite precisa de mais tempo para adormecer.
        val coffee = today.atTime(20, 0).toInstant(zone).toEpochMilli()
        val log = DailyLog(today, caffeine = 2, caffeineTimes = listOf(coffee, coffee), mood = 1f, opened = true)
        val wake = today.plusDays(1).atTime(7, 0)
        fun latency(now: LocalDateTime) =
            BedtimeEngine.plan(wake, now, 30, Chronotype.Hummingbird, FallAsleepTime.Fifteen, log, zone, cycloDay(routines, now, zone)).fallAsleepMinutes
        val before = latency(evening)
        assertTrue(before > FallAsleepTime.Fifteen.planMinutes)
        assertEquals(before, latency(today.plusDays(1).atTime(0, 30)))
        assertEquals(before, latency(today.plusDays(1).atTime(6, 59)))
    }

    @Test
    fun justOnceRoutineOnlyStartsItsOwnDay() {
        // Criada às 22:00 de sexta: acorda no sábado às 7:00 e mais nenhum dia.
        val anchor = LocalDateTime.of(2026, 11, 6, 22, 0).toInstant(zone).toEpochMilli()
        val once = routine(1, 7, 0, RepeatOption.JustOnce).copy(anchorMillis = anchor)
        assertEquals(today.atTime(7, 0), trackingDayStart(listOf(once), today, zone))
        assertEquals(today.plusDays(1).atStartOfDay(), trackingDayStart(listOf(once), today.plusDays(1), zone))
    }

    @Test
    fun promptShowsFromTheWakeUntilLoggedOrDismissed() {
        val routines = listOf(routine(1, 7, 0, RepeatOption.Everyday))
        val log = DailyLog(today, opened = true)
        assertFalse(showsSleepPrompt(routines, log, today.atTime(6, 0), dismissed = false, zone = zone))
        assertTrue(showsSleepPrompt(routines, log, today.atTime(7, 30), dismissed = false, zone = zone))
        assertTrue(showsSleepPrompt(routines, null, today.atTime(23, 59), dismissed = false, zone = zone))
        assertFalse(showsSleepPrompt(routines, log, today.atTime(7, 30), dismissed = true, zone = zone))
        assertFalse(showsSleepPrompt(routines, log.copy(sleep = SleepQuality.Well), today.atTime(7, 30), dismissed = false, zone = zone))
        // Um registo de ontem não conta para hoje.
        val yesterday = log.copy(date = today.minusDays(1), sleep = SleepQuality.Well)
        assertTrue(showsSleepPrompt(routines, yesterday, today.atTime(8, 0), dismissed = false, zone = zone))
    }

    @Test
    fun weekIsTheLastSevenDaysEndingToday() {
        val s = trackingSummary(emptyList(), null, today)
        assertEquals(7, s.sleepWeek.size)
        assertEquals(today, s.sleepWeek.last().date)
        assertEquals(today.minusDays(6), s.sleepWeek.first().date)
        assertEquals(30, s.sleepMonth.size) // novembro
        assertEquals(1, s.sleepMonth.first().date.dayOfMonth)
    }

    @Test
    fun noDataMeansNoAveragesAndNoMax() {
        val s = trackingSummary(emptyList(), null, today)
        assertNull(s.sleepMonthAverage)
        assertNull(s.caffeineWeekAverage)
        assertNull(s.caffeineMonthAverage)
        assertEquals(0, s.caffeineWeekMax)
        assertTrue(s.caffeineMonth.all { it.value == null })
        assertTrue(s.moodMonth.all { it.value == null })
        assertNull(s.moodMonthAverage)
    }

    @Test
    fun caffeineAndMoodOnlyCountOpenedDays() {
        val history = listOf(
            DailyLog(today.minusDays(2), caffeine = 4, mood = 1f, opened = true),
            // Aberta só antes da hora de acordar: não conta (fica cinzento).
            DailyLog(today.minusDays(1), caffeine = 2, mood = 0f, opened = false),
        )
        val current = DailyLog(today, caffeine = 0, opened = true)
        val s = trackingSummary(history, current, today)
        val week = s.caffeineWeek.associate { it.date to it.value }
        assertEquals(4, week[today.minusDays(2)])
        assertNull(week[today.minusDays(1)])
        assertEquals(0, week[today])
        assertEquals(4, s.caffeineWeekMax)
        assertEquals(2, s.caffeineWeekAverage) // (4 + 0) / 2
        val mood = s.moodMonth.associate { it.date to it.value }
        assertEquals(4, mood[today.minusDays(2)]) // Bad
        assertNull(mood[today.minusDays(1)])
        assertEquals(2, mood[today]) // aberta sem mexer = Okay
        // average-mood: só os dias abertos, arredondada ao nível mais perto ((4 + 2) / 2 = Not great).
        assertEquals(3, s.moodMonthAverage)
    }

    @Test
    fun moodAverageRoundsToTheNearestLevel() {
        val history = listOf(
            DailyLog(today.minusDays(2), mood = 0f, opened = true), // Great
            DailyLog(today.minusDays(1), mood = 0f, opened = true), // Great
        )
        val current = DailyLog(today, mood = 0.25f, opened = true) // Good
        // (0 + 0 + 1) / 3 = 0,33: Great.
        assertEquals(0, trackingSummary(history, current, today).moodMonthAverage)
    }

    @Test
    fun caffeineBlocksGoFromNinePercentToTheMonthMax() {
        val history = listOf(
            DailyLog(today.minusDays(3), caffeine = 0, opened = true),
            DailyLog(today.minusDays(2), caffeine = 3, opened = true),
            DailyLog(today.minusDays(1), caffeine = 6, opened = true),
        )
        val s = trackingSummary(history, null, today)
        assertEquals(6, s.caffeineMonthMax)
        assertEquals(MinBlockAlpha, s.caffeineBlockAlpha(0), 1e-6f)
        assertEquals(1f, s.caffeineBlockAlpha(6), 1e-6f)
        assertEquals(MinBlockAlpha + (1 - MinBlockAlpha) / 2, s.caffeineBlockAlpha(3), 1e-6f)
    }

    @Test
    fun sleepOpacityAndAverage() {
        assertEquals(1f, SleepQuality.ReallyWell.blockAlpha(), 1e-6f)
        assertEquals(MinBlockAlpha, SleepQuality.VeryPoor.blockAlpha(), 1e-6f)
        assertEquals(SleepQuality.Well, averageQuality(listOf(SleepQuality.ReallyWell, SleepQuality.Well, SleepQuality.Meh)))
        // 0, 0, 1, 4 → média 1,25 → Well.
        assertEquals(
            SleepQuality.Well,
            averageQuality(listOf(SleepQuality.ReallyWell, SleepQuality.ReallyWell, SleepQuality.Well, SleepQuality.VeryPoor)),
        )
    }

    @Test
    fun futureDaysOfTheMonthStayEmpty() {
        val s = trackingSummary(emptyList(), DailyLog(today, caffeine = 2, opened = true), today)
        assertTrue(s.caffeineMonth.filter { it.date.isAfter(today) }.all { it.value == null })
        assertEquals(2, s.caffeineMonth.first { it.date == today }.value)
    }

    @Test
    fun meterStartsAtTheAverageOnlyWithSevenNights() {
        val six = (1L..6L).map { DailyLog(today.minusDays(it), sleep = SleepQuality.Poor) }
        assertEquals(SleepQuality.ReallyWell, sleepMeterStart(six, null, today))
        val seven = six + DailyLog(today.minusDays(7), sleep = SleepQuality.Poor)
        assertEquals(SleepQuality.Poor, sleepMeterStart(seven, null, today))
        // Noites com mais de 30 dias não contam.
        val old = (31L..40L).map { DailyLog(today.minusDays(it), sleep = SleepQuality.VeryPoor) }
        assertEquals(SleepQuality.ReallyWell, sleepMeterStart(old, null, today))
    }

    @Test
    fun moodLevelRoundsToTheNearestDot() {
        assertEquals(0, DailyLog(today, mood = 0f).moodLevel)
        assertEquals(2, DailyLog(today, mood = 0.5f).moodLevel)
        assertEquals(3, DailyLog(today, mood = 0.75f).moodLevel)
        assertEquals(4, DailyLog(today, mood = 1f).moodLevel)
    }

    // --- Correção da meia-noite (beta-3.0 refeita / release-2.4) --------------------------------

    /** O que o repositório guarda (registo atual + histórico), escrito como o updateDailyLog. */
    private class Store(var stored: DailyLog? = null, var history: List<DailyLog> = emptyList()) {
        fun write(day: LocalDate, change: (DailyLog) -> DailyLog) {
            val logs = resolveDayLogs(stored, history, day)
            history = logs.history
            stored = change(logs.current)
        }

        /** O que o Feed mostra para o dia [day]. */
        fun shown(day: LocalDate): DailyLog = resolveDayLogs(stored, history, day).current
    }

    private fun millis(at: LocalDateTime) = at.toInstant(zone).toEpochMilli()
    private fun addCoffee(at: LocalDateTime): (DailyLog) -> DailyLog =
        { it.copy(caffeine = it.caffeine + 1, caffeineTimes = it.caffeineTimes + millis(at)) }

    @Test
    fun coffeesAfterMidnightShowAtOnceAndStayWithTheNight() {
        // O caso do utilizador: acorda às 08:00, à 00:30 junta três cafés.
        val routines = listOf(routine(1, 8, 0, RepeatOption.Everyday))
        val store = Store()
        val evening = today.atTime(22, 0)
        store.write(cycloDay(routines, evening, zone), addCoffee(evening))
        val night = today.plusDays(1).atTime(0, 30)
        val day = cycloDay(routines, night, zone)
        assertEquals(today, day)
        repeat(3) { store.write(day, addCoffee(night)) }
        assertEquals(4, store.shown(day).caffeine)
        // Tirar um: desce logo.
        store.write(day) { it.copy(caffeine = it.caffeine - 1, caffeineTimes = it.caffeineTimes.dropLast(1)) }
        assertEquals(3, store.shown(day).caffeine)
        // À hora de acordar começa um dia novo, vazio; a noite fica inteira no histórico, uma vez.
        val wake = today.plusDays(1).atTime(8, 0)
        val next = cycloDay(routines, wake, zone)
        assertEquals(0, store.shown(next).caffeine)
        store.write(next) { it.copy(opened = true) }
        assertEquals(listOf(today), store.history.map { it.date })
        assertEquals(3, store.history.single().caffeine)
    }

    @Test
    fun aRoutineAddedAfterMidnightBringsTheLogsBackToTheNight() {
        // O que se reproduziu no emulador: à 00:30 sem nenhuma rotina a acordar no dia 8, o dia já
        // tinha mudado à meia-noite (registo do dia 8 com um café). Cria-se uma rotina das 06:00:
        // a noite volta a ser a do dia 7, e o Feed (e o toque seguinte) têm de ver o mesmo registo.
        val evening = today.atTime(21, 0)
        val store = Store(stored = DailyLog(today, caffeine = 1, caffeineTimes = listOf(millis(evening)), factors = setOf(SleepFactor.entries.first()), opened = true))
        val night = today.plusDays(1).atTime(0, 30)
        store.write(cycloDay(emptyList(), night, zone)) { addCoffee(night)(it).copy(opened = true, mood = 0.75f) }
        assertEquals(today.plusDays(1), store.stored!!.date)

        val routines = listOf(routine(1, 6, 0, RepeatOption.Everyday))
        val day = cycloDay(routines, night.plusMinutes(1), zone)
        assertEquals(today, day)
        // O Feed mostra já a noite toda (o café da tarde + o da madrugada), antes de qualquer escrita.
        val shown = store.shown(day)
        assertEquals(2, shown.caffeine)
        assertEquals(setOf(SleepFactor.entries.first()), shown.factors)
        assertEquals(0.75f, shown.mood)
        // E o toque seguinte cai nesse mesmo registo.
        store.write(day, addCoffee(night))
        assertEquals(3, store.shown(day).caffeine)
        assertEquals(today, store.stored!!.date)
        assertTrue(store.history.none { it.date == today.plusDays(1) })
        // Às 06:00 o dia 8 começa vazio; a noite fica no histórico uma única vez.
        val next = cycloDay(routines, today.plusDays(1).atTime(6, 0), zone)
        assertEquals(0, store.shown(next).caffeine)
        store.write(next) { it.copy(opened = true) }
        assertEquals(1, store.history.count { it.date == today })
        assertEquals(3, store.history.single { it.date == today }.caffeine)
    }

    @Test
    fun aSleepAlreadyLoggedStaysOnItsDay() {
        val store = Store(stored = DailyLog(today.plusDays(1), sleep = SleepQuality.Well, opened = true), history = listOf(DailyLog(today, caffeine = 2)))
        val shown = store.shown(today)
        assertEquals(2, shown.caffeine)
        assertNull(shown.sleep)
        store.write(today) { it.copy(opened = true) }
        // Quando o dia 8 chega, o sono registado está lá.
        assertEquals(SleepQuality.Well, store.shown(today.plusDays(1)).sleep)
    }

    @Test
    fun aLogFarAheadIsNotMergedIntoToday() {
        // O relógio andou dias para trás: esse registo fica no seu dia, não se junta a hoje.
        val far = DailyLog(today.plusDays(5), caffeine = 4)
        val store = Store(stored = far)
        assertEquals(0, store.shown(today).caffeine)
        store.write(today, addCoffee(today.atTime(10, 0)))
        assertEquals(1, store.shown(today).caffeine)
        assertEquals(listOf(far), store.history)
    }
}
