package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** As regras dos prompts da Cyclo 3.2.3 ([PromptRules]), com um relógio dado. */
class PromptsTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val routine = Routine(1, "R1", 7, 0, RepeatOption.Everyday, 0, active = true, anchorMillis = 0L)
    private val today = LocalDate.of(2026, 11, 24)

    /** Às 10:00 de hoje (depois de acordar, às 07:00). */
    private val morning = today.atTime(10, 0)

    private fun ms(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()

    private fun sleepNights(vararg qualities: SleepQuality?): List<DailyLog> =
        qualities.mapIndexed { i, q -> DailyLog(date = today.minusDays(i.toLong()), sleep = q, opened = true) }

    private fun logs(days: List<DailyLog>, on: LocalDate = today): DayLogs {
        val current = days.firstOrNull { it.date == on } ?: DailyLog(on)
        return DayLogs(current, days.filter { it.date != on }.sortedBy { it.date })
    }

    private fun top(
        days: List<DailyLog>,
        memory: PromptMemory = PromptMemory(),
        reminderOn: Boolean = false,
        at: LocalDateTime = morning,
        routines: List<Routine> = listOf(routine),
    ) = PromptRules.feedTop(routines, logs(days, at.toLocalDate()), at, ms(at), memory, reminderOn, zone)

    // --- sleep-evaluation-prompt -----------------------------------------------------------------

    @Test
    fun sleepPromptShowsUntilSleepIsLogged() {
        assertEquals(listOf(CycloPrompt.SleepEvaluation), top(emptyList(), reminderOn = true))
        assertEquals(emptyList<CycloPrompt>(), top(sleepNights(SleepQuality.Well), reminderOn = true))
    }

    @Test
    fun closedSleepPromptComesBackTheNextCycloDay() {
        val closedToday = PromptMemory(sleepDismissedDay = today)
        assertEquals(emptyList<CycloPrompt>(), top(emptyList(), closedToday, reminderOn = true))
        // Mais tarde no mesmo dia (antes da meia-noite) continua fechado.
        assertEquals(emptyList<CycloPrompt>(), top(emptyList(), closedToday, reminderOn = true, at = today.atTime(23, 30)))
        // De madrugada ainda é o dia da Cyclo de hoje (o dia muda à hora de acordar): sem prompt.
        assertEquals(emptyList<CycloPrompt>(), top(emptyList(), closedToday, reminderOn = true, at = today.plusDays(1).atTime(5, 0)))
        // Depois de acordar no dia seguinte, volta.
        assertEquals(listOf(CycloPrompt.SleepEvaluation), top(emptyList(), closedToday, reminderOn = true, at = today.plusDays(1).atTime(8, 0)))
    }

    // --- never-miss-bedtime ----------------------------------------------------------------------

    @Test
    fun sleepingPoorlyNeedsThreeNightsAndAMehAverage() {
        val map = { d: List<DailyLog> -> dayLogs(d, null) }
        assertFalse(PromptRules.sleepsPoorly(map(sleepNights(SleepQuality.VeryPoor, SleepQuality.VeryPoor)), today))
        assertTrue(PromptRules.sleepsPoorly(map(sleepNights(SleepQuality.Meh, SleepQuality.Meh, SleepQuality.Meh)), today))
        assertFalse(PromptRules.sleepsPoorly(map(sleepNights(SleepQuality.Meh, SleepQuality.Well, SleepQuality.Meh)), today))
        // Noites com mais de 7 dias não contam.
        val old = (7..9).map { DailyLog(today.minusDays(it.toLong()), sleep = SleepQuality.VeryPoor) }
        assertFalse(PromptRules.sleepsPoorly(map(old), today))
    }

    @Test
    fun neverMissBedtimeNeedsPoorSleepAndTheReminderOff() {
        val poor = sleepNights(SleepQuality.Poor, SleepQuality.Meh, SleepQuality.Poor)
        assertEquals(listOf(CycloPrompt.NeverMissBedtime), top(poor))
        assertEquals(emptyList<CycloPrompt>(), top(poor, reminderOn = true))
        // Sem rotina ligada, o lembrete não tem hora para lembrar.
        assertEquals(emptyList<CycloPrompt>(), top(poor, routines = listOf(routine.copy(active = false))).filter { it == CycloPrompt.NeverMissBedtime })
        val good = sleepNights(SleepQuality.Well, SleepQuality.ReallyWell, SleepQuality.Meh)
        assertEquals(emptyList<CycloPrompt>(), top(good))
    }

    @Test
    fun sleepPromptAndNeverMissShareTheirPlace() {
        // Hoje por registar, as três noites antes más: os dois, pela ordem do lugar.
        val nights = listOf(null, SleepQuality.Poor, SleepQuality.VeryPoor, SleepQuality.Meh)
        assertEquals(listOf(CycloPrompt.SleepEvaluation, CycloPrompt.NeverMissBedtime), top(sleepNights(*nights.toTypedArray())))
    }

    @Test
    fun closedNeverMissRestsAWeekThenOnlyReturnsWhileHabitsStayBad() {
        val poor = sleepNights(SleepQuality.Poor, SleepQuality.Meh, SleepQuality.Poor)
        val closed = PromptMemory(neverMissDismissedAt = ms(morning))
        assertEquals(emptyList<CycloPrompt>(), top(poor, closed))
        assertEquals(emptyList<CycloPrompt>(), top(poor, closed, at = morning.plusDays(6)).filter { it == CycloPrompt.NeverMissBedtime })
        // Uma semana depois, ainda a dormir mal: volta.
        val stillPoor = (0..6).map { DailyLog(today.plusDays(7).minusDays(it.toLong()), sleep = SleepQuality.Poor) }
        assertTrue(CycloPrompt.NeverMissBedtime in top(stillPoor, closed, at = morning.plusDays(7)))
        // Uma semana depois, a dormir bem: não volta (mesmo com o lembrete desligado).
        val better = (0..6).map { DailyLog(today.plusDays(7).minusDays(it.toLong()), sleep = SleepQuality.Well) }
        assertFalse(CycloPrompt.NeverMissBedtime in top(better, closed, at = morning.plusDays(7)))
    }

    // --- keep-the-water-flowing ------------------------------------------------------------------

    private fun water(vararg ml: Int): List<DailyLog> =
        ml.mapIndexed { i, v -> DailyLog(date = today.minusDays(i + 1L), water = v) }

    private fun waterPrompts(days: List<DailyLog>, memory: PromptMemory = PromptMemory(), reminderOn: Boolean = false, at: LocalDateTime = morning) =
        PromptRules.feedWater(logs(days, at.toLocalDate()), age = 30, nowMs = ms(at), memory = memory, hydrationReminderOn = reminderOn)

    @Test
    fun drinkingLittleNeedsThreeLoggedDaysWithALowMedian() {
        // Adulto: 2000 mL adequados; 70 % = 1400 mL.
        assertEquals(listOf(CycloPrompt.KeepWaterFlowing), waterPrompts(water(500, 750, 1000)))
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(water(500, 750)))
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(water(1500, 2000, 1250)))
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(water(500, 750, 1000), reminderOn = true))
        // Dias sem água registada não contam como "pouca".
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(water(0, 0, 0, 0, 500)))
    }

    @Test
    fun todayDoesNotCountForWaterYet() {
        val days = listOf(DailyLog(today, water = 250)) + water(2000, 2250, 1750)
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(days))
    }

    @Test
    fun closedWaterPromptRestsAWeek() {
        val closed = PromptMemory(waterDismissedAt = ms(morning))
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(water(500, 750, 1000), closed))
        val later = morning.plusDays(7)
        val stillLow = (1..3).map { DailyLog(later.toLocalDate().minusDays(it.toLong()), water = 500) }
        assertEquals(listOf(CycloPrompt.KeepWaterFlowing), waterPrompts(stillLow, closed, at = later))
        val better = (1..3).map { DailyLog(later.toLocalDate().minusDays(it.toLong()), water = 2000) }
        assertEquals(emptyList<CycloPrompt>(), waterPrompts(better, closed, at = later))
    }

    // --- keep-your-progress-safe e new-update-group ----------------------------------------------

    private fun week(days: Int, log: (LocalDate) -> DailyLog): List<DailyLog> =
        (1..days).map { log(today.minusDays(it.toLong())) }

    private fun data(history: List<DailyLog>, autoBackup: Boolean = false) =
        CycloData(onboarded = true, routines = listOf(routine), dailyLog = null, history = history, autoBackup = autoBackup)

    @Test
    fun aWeekOfProgressNeedsSevenDaysWithRealLogs() {
        assertTrue(PromptRules.hasWeekOfProgress(week(7) { DailyLog(it, caffeine = 1) }, null))
        assertFalse(PromptRules.hasWeekOfProgress(week(6) { DailyLog(it, caffeine = 1) }, null))
        // Só abrir a app (e o humor por mexer) não é progresso.
        assertFalse(PromptRules.hasWeekOfProgress(week(10) { DailyLog(it, opened = true) }, null))
        assertTrue(PromptRules.hasWeekOfProgress(week(6) { DailyLog(it, mood = 0.25f) }, DailyLog(today, sleep = SleepQuality.Meh)))
    }

    @Test
    fun keepYourProgressSafeFollowsVersionAndBackups() {
        val progress = week(7) { DailyLog(it, water = 500) }
        val version = 57
        assertEquals(listOf(CycloPrompt.KeepProgressSafe), PromptRules.settings(false, data(progress), PromptMemory(), version))
        // Cópias automáticas ligadas: não.
        assertEquals(emptyList<CycloPrompt>(), PromptRules.settings(false, data(progress, autoBackup = true), PromptMemory(), version))
        // Fechado nesta versão: não volta.
        val closed = PromptMemory(backupDismissedVersion = version)
        assertEquals(emptyList<CycloPrompt>(), PromptRules.settings(false, data(progress), closed, version))
        // Numa versão nova, volta a poder aparecer (ainda com uma semana de progresso).
        assertEquals(listOf(CycloPrompt.KeepProgressSafe), PromptRules.settings(false, data(progress), closed, version + 1))
        assertEquals(emptyList<CycloPrompt>(), PromptRules.settings(false, data(progress.take(3)), closed, version + 1))
    }

    @Test
    fun updateAndBackupShareTheirPlaceUpdateFirst() {
        val progress = week(7) { DailyLog(it, water = 500) }
        assertEquals(
            listOf(CycloPrompt.NewUpdate, CycloPrompt.KeepProgressSafe),
            PromptRules.settings(true, data(progress), PromptMemory(), 57),
        )
    }
}
