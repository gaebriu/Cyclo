package com.gaebriu.cyclo.reminder

import com.gaebriu.cyclo.data.DailyLog
import com.gaebriu.cyclo.data.Hydration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** As contas do lembrete da água (Cyclo 3.1, [HydrationPlan]). */
class HydrationPlanTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2026, 11, 18)
    private val window = HydrationPlan.Window(day.atTime(7, 0), day.atTime(21, 0))

    private fun at(h: Int, m: Int = 0, s: Int = 0): LocalDateTime = day.atTime(h, m, s)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun log(ml: Int, vararg sips: LocalDateTime) = DailyLog(day, water = ml, waterTimes = sips.map(::ms))

    private fun next(now: LocalDateTime, log: DailyLog, goal: Int = 1800, last: LocalDateTime? = null, sent: Int = 0) =
        HydrationPlan.next(now, window, log, goal, last, sent, zone)

    @Test
    fun nothingLoggedRemindsWhenTheDayFallsOneGlassBehind() {
        // 14 h para 1800 mL: o primeiro copo "devia" estar às 7:00 + 14 h × 250 / 1800 = 8:56:40.
        assertEquals(HydrationPlan.Next(at(8, 56, 40), HydrationPlan.Kind.First), next(at(7, 10), log(0)))
    }

    @Test
    fun drinkingRegularlyPushesTheReminderLater() {
        // 750 mL até às 11:00: o ritmo só fica um copo atrás às 14:46:40 (antes das 4 h sem registos).
        val result = next(at(11, 30), log(750, at(8), at(9, 30), at(11)))
        assertEquals(HydrationPlan.Next(at(14, 46, 40), HydrationPlan.Kind.Pace), result)
    }

    @Test
    fun aLongGapRemindsEvenWhenAheadOfThePace() {
        // À frente do ritmo, mas sem registos desde as 8:00: às 12:00 (4 h depois).
        val result = next(at(9), log(1250, at(7, 30), at(7, 45), at(8)))
        assertEquals(HydrationPlan.Next(at(12), HydrationPlan.Kind.Gap), result)
    }

    @Test
    fun neverRightAfterAGlassNorTooSoonAfterTheLastReminder() {
        // Atrás do ritmo, mas acabou de beber às 13:00: espera 90 min.
        assertEquals(at(14, 30), next(at(13, 5), log(250, at(13)))!!.at)
        // O último lembrete foi às 12:00: o próximo nunca antes das 14:30.
        assertEquals(at(14, 30), next(at(12, 10), log(0), last = at(12))!!.at)
    }

    @Test
    fun stopsAtTheGoalAtTheDailyLimitAndLateInTheDay() {
        assertNull(next(at(15), log(1800, at(14))))
        assertNull(next(at(10), log(0), sent = HydrationPlan.MaxPerDay))
        // Depois das 20:30 (meia hora antes do fim do dia ativo), nada.
        assertNull(next(at(20, 45), log(0)))
    }

    @Test
    fun dueRemindersFireNowAndNotInThePast() {
        assertEquals(at(16), next(at(16), log(0))!!.at)
    }

    @Test
    fun theGoalFollowsTheUsersHabitWithinARange() {
        val target = Hydration.targetMl(30)
        fun history(vararg ml: Int) = ml.mapIndexed { i, v -> DailyLog(day.minusDays(i + 1L), water = v) }
        // Sem dias suficientes: a quantidade adequada para a idade.
        assertEquals(target, HydrationPlan.goal(history(1000, 1200), day, 30))
        // A mediana do hábito.
        assertEquals(1500, HydrationPlan.goal(history(1250, 1500, 2000), day, 30))
        // Dentro da faixa: nunca pede muito menos nem muito mais do que o adequado.
        assertEquals((target * HydrationPlan.HabitLow).toInt(), HydrationPlan.goal(history(250, 250, 500), day, 30))
        assertEquals((target * HydrationPlan.HabitHigh).toInt(), HydrationPlan.goal(history(4000, 4500, 5000), day, 30))
        // Dias sem água registada não contam.
        assertEquals(target, HydrationPlan.goal(history(0, 0, 0, 1500), day, 30))
    }

    @Test
    fun withoutARoutineTheDayRunsFromHalfPastEightToHalfPastNine() {
        val w = HydrationPlan.window(emptyList(), day, zone)
        assertEquals(at(8, 30), w.start)
        assertEquals(at(21, 30), w.end)
    }
}
