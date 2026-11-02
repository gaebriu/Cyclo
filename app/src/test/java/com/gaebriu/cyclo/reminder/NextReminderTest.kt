package com.gaebriu.cyclo.reminder

import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class NextReminderTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 11, 2)
    private val now = monday.atTime(18, 0)
    private val work = Routine(1, "Work", 7, 0, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 0)
    private val gym = Routine(2, "Gym", 8, 0, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 0)
    private val data = CycloData(onboarded = true, routines = listOf(gym, work), dailyLog = null)

    private fun millis(t: java.time.LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun earliestWakeOfTheNightIsChosen() {
        val (routine, plan) = BedtimeReminder.nextReminder(data, now, 0L, zone)!!
        assertEquals(work, routine)
        // 07:00 − 7 h 30 − 15 min = 23:15; menos 30 min de wind-down = 22:45.
        assertEquals(monday.atTime(22, 45), plan.windDownStart)
    }

    @Test
    fun secondRoutineSameMorningIsNotNotifiedAgain() {
        val notified = millis(monday.plusDays(1).atTime(7, 0))
        val (routine, plan) = BedtimeReminder.nextReminder(data, monday.atTime(22, 50), notified, zone)!!
        // A rotina das 08:00 da mesma manhã fica de fora; a seguinte é a noite de terça.
        assertEquals(monday.plusDays(2).atTime(7, 0), plan.wake)
        assertEquals(work, routine)
    }

    @Test
    fun nothingWhenAllRoutinesAreOff() {
        val off = data.copy(routines = data.routines.map { it.copy(active = false) })
        assertNull(BedtimeReminder.nextReminder(off, now, 0L, zone))
    }

    @Test
    fun windDownAlreadyStartedIsSkipped() {
        // Às 22:50 o wind-down de segunda (22:45) já passou: nada de avisos atrasados.
        val (_, plan) = BedtimeReminder.nextReminder(data.copy(routines = listOf(work)), monday.atTime(22, 50), 0L, zone)!!
        assertEquals(monday.plusDays(2).atTime(7, 0), plan.wake)
    }
}
