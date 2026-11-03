package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupTest {
    private val data = CycloData(
        onboarded = true,
        routines = listOf(
            Routine(1, "Work", 6, 30, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 123L),
            Routine(2, "Gym \"days\"", 8, 0, RepeatOption.SelectedDays, 0b0101010, active = false, anchorMillis = 456L),
        ),
        dailyLog = DailyLog(
            LocalDate.of(2026, 10, 30),
            caffeine = 3,
            factors = setOf(SleepFactor.LongLateNap),
            mood = 0.25f,
            // Um café sem hora (registo antigo) e dois com hora.
            caffeineTimes = listOf(1_793_300_000_000L, 1_793_310_000_000L),
            factorTimes = mapOf(SleepFactor.LongLateNap to 1_793_320_000_000L),
        ),
        use24Hour = false,
        age = 29,
        chronotype = Chronotype.NightOwl,
        fallAsleep = FallAsleepTime.ThirtyOrMore,
        bedtimeReminder = false,
        feedOrder = listOf(FeedSection.Feeling, FeedSection.Caffeine, FeedSection.SleepFactors),
    )
    private val exportedAt = Instant.parse("2026-11-02T21:00:00Z")

    private fun valid(text: String) = (parseBackup(text) as BackupParseResult.Valid).contents
    private fun problem(text: String) = (parseBackup(text) as BackupParseResult.Rejected).problem

    @Test
    fun roundTripKeepsEverything() {
        val c = valid(buildBackupJson(data, exportedAt, "alpha-0.8.0"))
        assertEquals(data.routines, c.routines)
        assertEquals(data.dailyLog, c.dailyLog) // o registo de outro dia também volta
        assertEquals(false, c.use24Hour)
        assertEquals(29, c.age)
        assertEquals(Chronotype.NightOwl, c.chronotype)
        assertEquals(FallAsleepTime.ThirtyOrMore, c.fallAsleep)
        assertEquals(false, c.bedtimeReminder)
        assertEquals(exportedAt, c.exportedAt)
        assertEquals(data.feedOrder, c.feedOrder)
    }

    @Test
    fun feedOrderIsOptionalAndNormalized() {
        // Sem ordem (cópias antigas): fica a atual.
        val old = buildBackupJson(data, exportedAt, "x").replace(Regex(""",\s*"feedOrder": \[[^\]]*\]"""), "")
        assertNull(valid(old).feedOrder)
        // Nomes desconhecidos saem; os que faltam vão para o fim; repetidos contam uma vez.
        assertEquals(
            listOf(FeedSection.Feeling, FeedSection.Caffeine, FeedSection.SleepFactors),
            normalizeFeedOrder(listOf(FeedSection.Feeling, FeedSection.Feeling)),
        )
    }

    @Test
    fun systemTimeFormatAndNoAgeRoundTrip() {
        val c = valid(buildBackupJson(data.copy(use24Hour = null, age = null, dailyLog = null), exportedAt, "x"))
        assertNull(c.use24Hour)
        assertNull(c.age)
        assertNull(c.dailyLog)
    }

    @Test
    fun rejectsGarbageAndOtherJson() {
        assertEquals(BackupProblem.Invalid, problem("not json"))
        assertEquals(BackupProblem.Invalid, problem("{}"))
        assertEquals(BackupProblem.Invalid, problem("""{"format":"cyclo-backup","version":1}"""))
        assertEquals(BackupProblem.Invalid, problem("[1,2,3]"))
    }

    @Test
    fun rejectsNewerVersion() {
        val json = buildBackupJson(data, exportedAt, "x").replace("\"version\": 1", "\"version\": 99")
        assertEquals(BackupProblem.TooNew, problem(json))
    }

    @Test
    fun rejectsImpossibleValuesWholeFile() {
        val json = buildBackupJson(data, exportedAt, "x").replace("\"wakeHour\": 8", "\"wakeHour\": 25")
        assertEquals(BackupProblem.Invalid, problem(json))
        val badAge = buildBackupJson(data, exportedAt, "x").replace("\"age\": 29", "\"age\": 300")
        assertEquals(BackupProblem.Invalid, problem(badAge))
    }

    @Test
    fun unknownValuesAreIgnoredNotFatal() {
        val json = buildBackupJson(data, exportedAt, "x")
            .replace("\"NightOwl\"", "\"Lark\"")
            .replace("\"LongLateNap\"", "\"Something\"")
        val c = valid(json)
        assertNull(c.chronotype)
        assertTrue(c.dailyLog!!.factors.isEmpty())
    }

    @Test
    fun olderBackupWithoutSettingsKeepsCurrentOnes() {
        val json = """{"format":"cyclo-backup","version":1,"data":{"routines":[]}}"""
        val c = valid(json)
        assertTrue(c.routines.isEmpty())
        assertNull(c.age)
        assertNull(c.chronotype)
        assertNull(c.bedtimeReminder)
    }

    @Test
    fun duplicateIdsGetNewOnes() {
        val dup = data.copy(routines = listOf(data.routines[0], data.routines[1].copy(id = 1)))
        val ids = valid(buildBackupJson(dup, exportedAt, "x")).routines.map { it.id }
        assertEquals(2, ids.toSet().size)
    }
}
