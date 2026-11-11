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
        age = 29,
        chronotype = Chronotype.NightOwl,
        fallAsleep = FallAsleepTime.ThirtyOrMore,
        bedtimeReminder = false,
        logSleepReminder = true,
        feedOrder = listOf(FeedSection.Feeling, FeedSection.Caffeine, FeedSection.Water, FeedSection.SleepFactors),
    )
    private val exportedAt = Instant.parse("2026-11-02T21:00:00Z")

    private fun valid(text: String) = (parseBackup(text) as BackupParseResult.Valid).contents
    private fun problem(text: String) = (parseBackup(text) as BackupParseResult.Rejected).problem

    @Test
    fun roundTripKeepsEverything() {
        val c = valid(buildBackupJson(data, exportedAt, "alpha-0.8.0"))
        assertEquals(data.routines, c.routines)
        assertEquals(data.dailyLog, c.dailyLog) // o registo de outro dia também volta
        assertEquals(29, c.age)
        assertEquals(Chronotype.NightOwl, c.chronotype)
        assertEquals(FallAsleepTime.ThirtyOrMore, c.fallAsleep)
        assertEquals(false, c.bedtimeReminder)
        assertEquals(true, c.logSleepReminder)
        assertEquals(exportedAt, c.exportedAt)
        assertEquals(data.feedOrder, c.feedOrder)
    }

    @Test
    fun feedOrderIsOptionalAndNormalized() {
        // Sem ordem (cópias antigas): fica a atual.
        val old = buildBackupJson(data, exportedAt, "x").replace(Regex(""",\s*"feedOrder": \[[^\]]*\]"""), "")
        assertNull(valid(old).feedOrder)
        // Nomes desconhecidos saem; os que faltam entram a seguir à que os precede; repetidos contam uma vez.
        assertEquals(
            // beta-3.0: cada uma que falta entra a seguir à que a precede na ordem de omissão.
            listOf(FeedSection.Caffeine, FeedSection.Water, FeedSection.SleepFactors, FeedSection.Feeling),
            normalizeFeedOrder(listOf(FeedSection.Feeling, FeedSection.Feeling)),
        )
    }

    @Test
    fun noAgeRoundTrip() {
        val c = valid(buildBackupJson(data.copy(age = null, dailyLog = null), exportedAt, "x"))
        assertNull(c.age)
        assertNull(c.dailyLog)
    }

    /** Formato das horas, língua e "Advanced textures" são do aparelho: nunca vão na cópia. */
    @Test
    fun devicePreferencesAreNotExported() {
        val json = buildBackupJson(data.copy(use24Hour = true, advancedTextures = false), exportedAt, "x")
        val settings = org.json.JSONObject(json).getJSONObject("data").getJSONObject("settings")
        for (key in listOf("use24Hour", "use_24_hour", "timeFormat", "language", "locale", "advancedTextures", "advanced_textures")) {
            assertTrue("$key não pode ir na cópia", !settings.has(key))
        }
        assertTrue(!json.contains("use24"))
        assertTrue(!json.contains("exture"))
    }

    /** Cópias antigas (beta-1.3 e anteriores) trazem "use24Hour": continuam válidas e o resto entra. */
    @Test
    fun oldBackupWithTimeFormatStillRestoresTheRest() {
        val old = buildBackupJson(data, exportedAt, "beta-1.3")
            .replace("\"settings\": {", "\"settings\": {\n      \"use24Hour\": false,")
        assertTrue(old.contains("\"use24Hour\": false"))
        val c = valid(old)
        assertEquals(29, c.age)
        assertEquals(Chronotype.NightOwl, c.chronotype)
        assertEquals(data.routines, c.routines)
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
        val json = buildBackupJson(data, exportedAt, "x").replace("\"version\": $BackupFormatVersion", "\"version\": 99")
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
        assertNull(c.logSleepReminder)
    }

    @Test
    fun backupWithoutLogSleepKeepsTheCurrentChoice() {
        // Uma cópia da Beta 2.2 (formato 2, sem "logSleepReminder"): o lembrete fica como está.
        val json = """{"format":"cyclo-backup","version":2,"data":{"routines":[],"settings":{"bedtimeReminder":true}}}"""
        val c = valid(json)
        assertEquals(true, c.bedtimeReminder)
        assertNull(c.logSleepReminder)
    }

    @Test
    fun duplicateIdsGetNewOnes() {
        val dup = data.copy(routines = listOf(data.routines[0], data.routines[1].copy(id = 1)))
        val ids = valid(buildBackupJson(dup, exportedAt, "x")).routines.map { it.id }
        assertEquals(2, ids.toSet().size)
    }

    private val withHistory = data.copy(
        trackingOrder = listOf(TrackingSection.Mood, TrackingSection.Restfulness, TrackingSection.Caffeine, TrackingSection.Water),
        history = listOf(
            DailyLog(LocalDate.of(2026, 10, 28), caffeine = 2, opened = true, sleep = SleepQuality.Meh),
            DailyLog(LocalDate.of(2026, 10, 29), mood = 1f, opened = false, sleep = null),
        ),
        dailyLog = data.dailyLog!!.copy(opened = true, sleep = SleepQuality.VeryPoor),
    )

    @Test
    fun historyAndTrackingOrderRoundTrip() {
        val c = valid(buildBackupJson(withHistory, exportedAt, "beta-2.0"))
        assertEquals(withHistory.history, c.history)
        assertEquals(withHistory.trackingOrder, c.trackingOrder)
        assertEquals(withHistory.dailyLog, c.dailyLog)
    }

    @Test
    fun formatOneBackupKeepsCurrentHistory() {
        // Uma cópia da versão 1 (antes da Tracking): sem histórico nem ordem; os registos sem
        // "opened" contam como abertos.
        val v1 = """{"format":"cyclo-backup","version":1,"data":{"routines":[],
            "dailyLog":{"date":"2026-10-30","caffeine":1,"factors":[],"mood":0.5}}}"""
        val c = valid(v1)
        assertNull(c.history)
        assertNull(c.trackingOrder)
        assertEquals(true, c.dailyLog!!.opened)
        assertNull(c.dailyLog!!.sleep)
    }

    @Test
    fun brokenHistoryEntryRejectsTheWholeFile() {
        val json = buildBackupJson(withHistory, exportedAt, "x").replace("\"2026-10-28\"", "\"2026-13-45\"")
        assertEquals(BackupProblem.Invalid, problem(json))
    }

    @Test
    fun unknownSleepNameIsIgnored() {
        val json = buildBackupJson(withHistory, exportedAt, "x").replace("\"Meh\"", "\"Dreamy\"")
        assertNull(valid(json).history!!.first().sleep)
    }
}
