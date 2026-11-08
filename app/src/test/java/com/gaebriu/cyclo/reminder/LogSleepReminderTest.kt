package com.gaebriu.cyclo.reminder

import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.DailyLog
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.SleepQuality
import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LogSleepReminderTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 11, 2)
    private val work = Routine(1, "Work", 7, 0, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 0)
    private val gym = Routine(2, "Gym", 6, 30, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 0)
    private val data = CycloData(onboarded = true, routines = listOf(work, gym), dailyLog = null, logSleepReminder = true)
    private val none = Long.MIN_VALUE

    @Test
    fun comesAfterTheEarliestWakeOfTheDay() {
        val (day, at) = LogSleepReminder.nextReminder(data, monday.atTime(5, 0), none, zone)!!
        assertEquals(monday, day)
        // A rotina das 06:30 acorda primeiro: 30 min depois.
        assertEquals(monday.atTime(7, 0), at)
    }

    @Test
    fun longPassedTimeWaitsForTheNextDay() {
        // Sem avisos atrasados: às 08:30 de segunda (1 h 30 depois das 07:00), o próximo é terça.
        val (day, at) = LogSleepReminder.nextReminder(data, monday.atTime(8, 30), none, zone)!!
        assertEquals(monday.plusDays(1), day)
        assertEquals(monday.plusDays(1).atTime(7, 0), at)
    }

    @Test
    fun dueButUndeliveredReminderIsKept() {
        // Às 07:20 o alarme das 07:00 pode ainda estar por entregar (inexato): reagendar não o perde.
        val (day, at) = LogSleepReminder.nextReminder(data, monday.atTime(7, 20), none, zone)!!
        assertEquals(monday, day)
        assertEquals(monday.atTime(7, 0), at)
    }

    @Test
    fun reminderPastMidnightIsSkipped() {
        // Acordar às 23:45 daria o lembrete às 00:15 do dia seguinte: o registo já seria de outro dia.
        val late = Routine(4, "Night shift", 23, 45, RepeatOption.Everyday, 0, active = true, anchorMillis = 0)
        assertNull(LogSleepReminder.nextReminder(data.copy(routines = listOf(late)), monday.atTime(12, 0), none, zone))
    }

    @Test
    fun daysWithoutAWakeAreSkipped() {
        // Sexta às 08:00: sábado e domingo não têm rotina ("During the week"), o próximo é segunda.
        val friday = monday.plusDays(4)
        val (day, _) = LogSleepReminder.nextReminder(data, friday.atTime(8, 0), none, zone)!!
        assertEquals(monday.plusDays(7), day)
    }

    @Test
    fun alreadyNotifiedDayIsNotRepeated() {
        val (day, _) = LogSleepReminder.nextReminder(data, monday.atTime(5, 0), monday.toEpochDay(), zone)!!
        assertEquals(monday.plusDays(1), day)
    }

    @Test
    fun sleepAlreadyLoggedTodaySkipsToday() {
        val logged = data.copy(dailyLog = DailyLog(monday, sleep = SleepQuality.Well))
        val (day, _) = LogSleepReminder.nextReminder(logged, monday.atTime(5, 0), none, zone)!!
        assertEquals(monday.plusDays(1), day)
    }

    @Test
    fun routinesOffMeanNoReminder() {
        val off = data.copy(routines = data.routines.map { it.copy(active = false) })
        assertNull(LogSleepReminder.nextReminder(off, monday.atTime(5, 0), none, zone))
    }

    @Test
    fun justOnceRoutineOnlyCountsItsOwnDay() {
        // "Show just this once" criada segunda às 20:00 para as 07:00: só terça.
        val anchor = monday.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val once = Routine(3, "Trip", 7, 0, RepeatOption.JustOnce, 0, active = true, anchorMillis = anchor)
        val onlyOnce = data.copy(routines = listOf(once))
        val (day, at) = LogSleepReminder.nextReminder(onlyOnce, monday.atTime(21, 0), none, zone)!!
        assertEquals(monday.plusDays(1), day)
        assertEquals(monday.plusDays(1).atTime(7, 30), at)
        // Passada essa vez (e a margem de entrega), nada.
        assertNull(LogSleepReminder.nextReminder(onlyOnce, monday.plusDays(1).atTime(9, 0), none, zone))
    }
}
