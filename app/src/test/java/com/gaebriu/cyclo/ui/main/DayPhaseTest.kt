package com.gaebriu.cyclo.ui.main

import com.gaebriu.cyclo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Cyclo 3.2.2: a fase do dia muda só nas fronteiras, e a próxima calcula-se de uma vez. */
class DayPhaseTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private fun at(h: Int, m: Int, day: Int = 20, month: Int = 11) = ZonedDateTime.of(2026, month, day, h, m, 0, 0, lisbon)

    @Test
    fun nextChangeIsTheNextBoundaryOfTheSkyOrTheGreeting() {
        assertEquals(at(5, 0), nextDayPhaseChange(at(0, 10)))
        assertEquals(at(8, 0), nextDayPhaseChange(at(5, 0)))
        assertEquals(at(11, 30), nextDayPhaseChange(at(8, 0)))
        assertEquals(at(12, 0), nextDayPhaseChange(at(11, 45))) // a saudação passa a "afternoon"
        assertEquals(at(15, 30), nextDayPhaseChange(at(12, 0)))
        assertEquals(at(18, 0), nextDayPhaseChange(at(17, 59))) // "evening"
        assertEquals(at(18, 30), nextDayPhaseChange(at(18, 0)))
        assertEquals(at(20, 30), nextDayPhaseChange(at(19, 0)))
        assertEquals(at(23, 30), nextDayPhaseChange(at(21, 0)))
        // Depois das 23:30: as 05:00 do dia seguinte (passa a meia-noite).
        assertEquals(at(5, 0, day = 21), nextDayPhaseChange(at(23, 45)))
    }

    @Test
    fun thePhaseChangesExactlyAtEachBoundaryAndNeverInBetween() {
        var t = at(0, 0)
        val end = t.plusDays(1)
        var changes = 0
        while (true) {
            val next = nextDayPhaseChange(t)
            if (!next.isBefore(end)) break
            // Nada muda até à fronteira; na fronteira muda.
            assertEquals(dayPhaseAt(t.toLocalDateTime()), dayPhaseAt(next.minusMinutes(1).toLocalDateTime()))
            assertNotEquals(dayPhaseAt(next.minusMinutes(1).toLocalDateTime()), dayPhaseAt(next.toLocalDateTime()))
            t = next
            changes++
        }
        assertEquals(9, changes)
    }

    @Test
    fun greetingBandsAreTheFeedOnes() {
        assertEquals(R.string.feed_greeting_evening, dayPhaseAt(LocalDateTime.of(2026, 11, 20, 4, 59)).greeting)
        assertEquals(R.string.feed_greeting_morning, dayPhaseAt(LocalDateTime.of(2026, 11, 20, 5, 0)).greeting)
        assertEquals(R.string.feed_greeting_afternoon, dayPhaseAt(LocalDateTime.of(2026, 11, 20, 12, 0)).greeting)
        assertEquals(R.string.feed_greeting_evening, dayPhaseAt(LocalDateTime.of(2026, 11, 20, 18, 0)).greeting)
    }

    @Test
    fun daylightSavingDaysStillLandOnTheWallClockBoundary() {
        // 29 de março de 2026: em Lisboa a 01:00 passa a 02:00. A próxima fronteira continua às 05:00.
        assertEquals(at(5, 0, day = 29, month = 3), nextDayPhaseChange(at(0, 30, day = 29, month = 3)))
    }
}
