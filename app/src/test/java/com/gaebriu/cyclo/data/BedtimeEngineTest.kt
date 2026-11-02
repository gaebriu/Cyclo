package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class BedtimeEngineTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val now = today.atTime(18, 0)
    private val wake = today.plusDays(1).atTime(7, 0)

    private fun plan(
        age: Int? = null,
        chronotype: Chronotype = Chronotype.Hummingbird,
        fallAsleep: FallAsleepTime = FallAsleepTime.Fifteen,
        log: DailyLog? = null,
        at: LocalDateTime = now,
        wakeAt: LocalDateTime = wake,
    ) = BedtimeEngine.plan(wakeAt, at, age, chronotype, fallAsleep, log)

    @Test
    fun adultGetsFiveCyclesPlusLatency() {
        val p = plan(age = 30)
        assertEquals(5, p.cycles)
        // 07:00 − 7 h 30 − 15 min = 23:15.
        assertEquals(today.atTime(23, 15), p.bedtime)
        assertFalse(p.shortfall)
    }

    @Test
    fun ageMinimumsRaiseTheCycles() {
        assertEquals(7, BedtimeEngine.targetCycles(5)) // 10 h → 7 ciclos (10 h 30)
        assertEquals(6, BedtimeEngine.targetCycles(10)) // 9 h → 6 ciclos
        assertEquals(6, BedtimeEngine.targetCycles(16)) // 8 h → 6 ciclos
        assertEquals(5, BedtimeEngine.targetCycles(40))
        assertEquals(5, BedtimeEngine.targetCycles(null))
    }

    @Test
    fun latencyMovesBedtimeEarlier() {
        assertEquals(today.atTime(23, 0), plan(fallAsleep = FallAsleepTime.ThirtyOrMore).bedtime)
        assertEquals(today.atTime(23, 25), plan(fallAsleep = FallAsleepTime.UnderFive).bedtime)
    }

    @Test
    fun dailyFactorsNeverMoveBedtimeOnlyWindDown() {
        val calm = plan()
        val heavy = plan(
            log = DailyLog(today, caffeine = 6, factors = SleepFactor.entries.toSet(), mood = 1f),
            chronotype = Chronotype.NightOwl,
        )
        assertEquals(calm.bedtime, heavy.bedtime)
        assertTrue(heavy.windDownMinutes > calm.windDownMinutes)
        assertEquals(BedtimeEngine.MaxWindDownMinutes, heavy.windDownMinutes)
        assertEquals(WindDownReason.Stress, heavy.reasons.first())
    }

    @Test
    fun yesterdaysLogIsIgnored() {
        val p = plan(log = DailyLog(today.minusDays(1), caffeine = 5))
        assertEquals(BedtimeEngine.BaseWindDownMinutes, p.windDownMinutes)
    }

    @Test
    fun noTimeLeftRecommendsNowWithoutFewerCycles() {
        val late = today.plusDays(1).atTime(2, 3)
        val p = plan(at = late)
        assertTrue(p.shortfall)
        assertEquals(5, p.cycles)
        assertEquals(today.plusDays(1).atTime(2, 5), p.bedtime)
    }

    /** O invariante: nunca menos do que o mínimo (idade e 5 ciclos) mais o tempo a adormecer. */
    @Test
    fun neverBelowMinimumSleepOpportunity() {
        val ages = listOf(null, 5, 8, 12, 13, 17, 18, 40, 65, 100)
        val logs = listOf(
            null,
            DailyLog(today),
            DailyLog(today, caffeine = 1),
            DailyLog(today, caffeine = 12, mood = 0.9f),
            DailyLog(today, factors = setOf(SleepFactor.IntenseExercise)),
            DailyLog(today, caffeine = 3, factors = SleepFactor.entries.toSet(), mood = 1f),
        )
        for (age in ages) for (c in Chronotype.entries) for (f in FallAsleepTime.entries) for (log in logs) {
            val p = plan(age, c, f, log)
            val minimum = maxOf(BedtimeEngine.ageMinimumSleepMinutes(age), 450) + f.planMinutes
            assertFalse(p.shortfall)
            assertTrue("age=$age $c $f $log", p.sleepOpportunity >= Duration.ofMinutes(minimum.toLong()))
            assertTrue(p.windDownMinutes in BedtimeEngine.BaseWindDownMinutes..BedtimeEngine.MaxWindDownMinutes)
            assertTrue(p.windDownStart.isBefore(p.bedtime))
        }
    }

    @Test
    fun nextWakeRespectsRepeatDays() {
        // 2 de novembro de 2026 é segunda-feira: uma rotina só de fim de semana acorda no sábado.
        val weekend = Routine(1, "Weekend", 9, 0, RepeatOption.DuringWeekend, 0, active = true, anchorMillis = 0)
        val weekdays = Routine(2, "Work", 7, 0, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 0)
        val off = Routine(3, "Off", 5, 0, RepeatOption.Everyday, 0, active = false, anchorMillis = 0)
        val next = BedtimeEngine.nextWake(listOf(weekend, weekdays, off), now)
        assertEquals(weekdays, next?.first)
        assertEquals(today.plusDays(1).atTime(7, 0), next?.second)
        assertEquals(LocalDate.of(2026, 11, 7).atTime(9, 0), weekend.nextWake(now))
    }
}
