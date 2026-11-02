package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BedtimeEngineTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private val now = today.atTime(18, 0)
    private val wake = today.plusDays(1).atTime(7, 0)

    /** 07:00 − 5 ciclos = 23:30 a adormecer; com 15 min para adormecer, 23:15 na cama. */
    private val baseBedtime = today.atTime(23, 15)

    private fun plan(
        age: Int? = null,
        chronotype: Chronotype = Chronotype.Hummingbird,
        fallAsleep: FallAsleepTime = FallAsleepTime.Fifteen,
        log: DailyLog? = null,
        at: LocalDateTime = now,
        wakeAt: LocalDateTime = wake,
    ) = BedtimeEngine.plan(wakeAt, at, age, chronotype, fallAsleep, log, zone)

    private fun ms(hour: Int, minute: Int = 0) = today.atTime(hour, minute).toInstant(zone).toEpochMilli()

    private fun coffees(vararg hours: Int) = DailyLog(today, caffeine = hours.size, caffeineTimes = hours.map { ms(it) })

    private fun factor(factor: SleepFactor, hour: Int?, minute: Int = 0) = DailyLog(
        today,
        factors = setOf(factor),
        factorTimes = if (hour == null) emptyMap() else mapOf(factor to ms(hour, minute)),
    )

    @Test
    fun adultGetsFiveCyclesPlusLatency() {
        val p = plan(age = 30)
        assertEquals(5, p.cycles)
        assertEquals(baseBedtime, p.bedtime)
        assertEquals(today.atTime(23, 30), p.sleepOnset)
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
    fun caffeineCutoffFollowsTheReferencePoints() {
        assertEquals(8.8, BedtimeEngine.caffeineCutoffHours(107.0), 0.001)
        assertEquals(13.2, BedtimeEngine.caffeineCutoffHours(217.5), 0.001)
        assertTrue(BedtimeEngine.caffeineCutoffHours(321.0) > 13.2)
    }

    @Test
    fun eveningCoffeeMovesBedtimeEarlierNeverLater() {
        val calm = plan()
        val p = plan(log = coffees(20, 20))
        assertTrue(p.bedtime.isBefore(calm.bedtime))
        assertTrue(p.fallAsleepMinutes > calm.fallAsleepMinutes)
        assertTrue(p.windDownMinutes > calm.windDownMinutes)
        // O sono planeado não muda: adormece-se à mesma hora.
        assertEquals(calm.sleepOnset, p.sleepOnset)
        assertEquals(WindDownReason.Caffeine, p.reasons.first())
    }

    @Test
    fun moreCaffeineCloserToBedWeighsMore() {
        val one = plan(log = coffees(20))
        val two = plan(log = coffees(20, 20))
        val earlier = plan(log = coffees(16))
        assertTrue(two.fallAsleepMinutes > one.fallAsleepMinutes)
        assertTrue(one.windDownMinutes > earlier.windDownMinutes)
    }

    @Test
    fun oneMorningCoffeeHasClearedButAHighDoseHasNot() {
        // Um café às 10:00: 8,8 h depois (18:48) já não conta para as 23:15.
        val single = plan(log = coffees(10))
        assertEquals(baseBedtime, single.bedtime)
        assertTrue(WindDownReason.Caffeine !in single.reasons)
        // Três às 10:00 (321 mg): mais de 13 h, ainda atua à hora de deitar.
        val high = plan(log = coffees(10, 10, 10))
        assertTrue(high.bedtime.isBefore(baseBedtime))
    }

    @Test
    fun caffeineWithoutTimeOnlyAddsPreparation() {
        val p = plan(log = DailyLog(today, caffeine = 2))
        assertEquals(baseBedtime, p.bedtime)
        assertEquals(BedtimeEngine.BaseWindDownMinutes + 10, p.windDownMinutes)
    }

    @Test
    fun stressfulMoodAddsLatencyAndPreparation() {
        val okay = plan(log = DailyLog(today, mood = 0.5f))
        val notGreat = plan(log = DailyLog(today, mood = 0.75f))
        val bad = plan(log = DailyLog(today, mood = 1f))
        assertEquals(baseBedtime, okay.bedtime)
        assertEquals(baseBedtime.minusMinutes(5), notGreat.bedtime)
        assertEquals(baseBedtime.minusMinutes(10), bad.bedtime)
        assertTrue(bad.windDownMinutes > notGreat.windDownMinutes)
        assertTrue(notGreat.windDownMinutes > okay.windDownMinutes)
    }

    @Test
    fun exerciseCountsByHowCloseItIs() {
        val close = plan(log = factor(SleepFactor.IntenseExercise, 22, 45))
        val afternoon = plan(log = factor(SleepFactor.IntenseExercise, 20))
        val morning = plan(log = factor(SleepFactor.IntenseExercise, 9))
        assertEquals(baseBedtime.minusMinutes(10), close.bedtime)
        assertEquals(baseBedtime, afternoon.bedtime)
        assertTrue(afternoon.windDownMinutes > morning.windDownMinutes)
        assertEquals(BedtimeEngine.BaseWindDownMinutes, morning.windDownMinutes)
    }

    @Test
    fun mealAndNapChangeThePlan() {
        assertEquals(baseBedtime.minusMinutes(5), plan(log = factor(SleepFactor.HeavyLateMeal, 21)).bedtime)
        assertEquals(baseBedtime.minusMinutes(10), plan(log = factor(SleepFactor.LongLateNap, 16)).bedtime)
        // Sem a hora (registo antigo): só preparação no caso da refeição.
        val meal = plan(log = factor(SleepFactor.HeavyLateMeal, null))
        assertEquals(baseBedtime, meal.bedtime)
        assertTrue(meal.windDownMinutes > BedtimeEngine.BaseWindDownMinutes)
    }

    @Test
    fun everyChronotypeShapesThePlan() {
        val bird = plan(chronotype = Chronotype.EarlyBird)
        val humming = plan(chronotype = Chronotype.Hummingbird)
        val owl = plan(chronotype = Chronotype.NightOwl)
        // Night owl: deitar às 23:15 é 75 min antes da sua hora; adormece mais devagar.
        assertTrue(owl.bedtime.isBefore(humming.bedtime))
        assertTrue(owl.windDownMinutes > humming.windDownMinutes)
        // Early bird: a hora de deitar não muda, mas começa a abrandar mais cedo.
        assertEquals(humming.bedtime, bird.bedtime)
        assertTrue(bird.windDownStart.isBefore(humming.windDownStart))
        // Hummingbird a deitar bem antes das 23:00 (acordar às 05:00): mais latência.
        val early = plan(chronotype = Chronotype.Hummingbird, wakeAt = today.plusDays(1).atTime(5, 0))
        assertTrue(early.fallAsleepMinutes > FallAsleepTime.Fifteen.planMinutes)
        assertEquals(today.plusDays(1).atTime(5, 0).minusMinutes(450), early.sleepOnset)
    }

    @Test
    fun yesterdaysLogIsIgnored() {
        val p = plan(log = DailyLog(today.minusDays(1), caffeine = 5, mood = 1f))
        assertEquals(BedtimeEngine.BaseWindDownMinutes, p.windDownMinutes)
        assertEquals(baseBedtime, p.bedtime)
    }

    @Test
    fun noTimeLeftRecommendsNowWithoutFewerCycles() {
        val late = today.plusDays(1).atTime(2, 3)
        val p = plan(at = late)
        assertTrue(p.shortfall)
        assertEquals(5, p.cycles)
        assertEquals(today.plusDays(1).atTime(2, 5), p.bedtime)
    }

    /**
     * O invariante: o sono planeado (da hora estimada de adormecer até acordar) nunca fica abaixo do
     * mínimo (idade e 5 ciclos), e a latência só soma, nunca tira.
     */
    @Test
    fun neverBelowMinimumSleep() {
        val ages = listOf(null, 5, 8, 12, 13, 17, 18, 40, 65, 100)
        val logs = listOf(
            null,
            DailyLog(today),
            DailyLog(today, caffeine = 1),
            coffees(21),
            coffees(8, 12, 15, 17, 19, 21),
            DailyLog(today, caffeine = 12, mood = 0.9f),
            factor(SleepFactor.IntenseExercise, 22),
            DailyLog(
                today,
                caffeine = 3,
                caffeineTimes = listOf(ms(20), ms(21), ms(22)),
                factors = SleepFactor.entries.toSet(),
                factorTimes = SleepFactor.entries.associateWith { ms(22) },
                mood = 1f,
            ),
        )
        val wakes = listOf(wake, today.plusDays(1).atTime(5, 0), today.plusDays(1).atTime(10, 30))
        for (age in ages) for (c in Chronotype.entries) for (f in FallAsleepTime.entries) for (log in logs) for (w in wakes) {
            // Ao meio-dia, para haver tempo mesmo para 10 h 30 antes das 05:00.
            val p = plan(age, c, f, log, at = today.atTime(12, 0), wakeAt = w)
            val minimum = maxOf(BedtimeEngine.ageMinimumSleepMinutes(age), 450)
            val label = "age=$age $c $f $log $w"
            assertFalse(label, p.shortfall)
            assertTrue(label, Duration.between(p.sleepOnset, p.wake) >= Duration.ofMinutes(minimum.toLong()))
            assertTrue(label, p.sleepOpportunity >= Duration.ofMinutes((minimum + f.planMinutes).toLong()))
            assertTrue(label, p.fallAsleepMinutes >= f.planMinutes)
            assertTrue(label, p.fallAsleepMinutes <= f.planMinutes + BedtimeEngine.MaxExtraLatencyMinutes)
            assertTrue(label, p.windDownMinutes in BedtimeEngine.BaseWindDownMinutes..BedtimeEngine.MaxWindDownMinutes)
            assertTrue(label, p.windDownStart.isBefore(p.bedtime))
            assertEquals(label, 0, p.bedtime.minute % 5)
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
