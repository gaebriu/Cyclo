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
            // Cyclo 3.2.2: todos os campos dos registos na ida e volta (sono, água, aberto).
            opened = true,
            sleep = SleepQuality.Poor,
            water = 750,
            waterTimes = listOf(1_793_300_100_000L, 1_793_300_200_000L, 1_793_300_300_000L),
        ),
        age = 29,
        chronotype = Chronotype.NightOwl,
        fallAsleep = FallAsleepTime.ThirtyOrMore,
        history = listOf(
            DailyLog(
                LocalDate.of(2026, 10, 28), caffeine = 2, caffeineTimes = listOf(1_793_100_000_000L, 1_793_110_000_000L),
                factors = setOf(SleepFactor.IntenseExercise, SleepFactor.HeavyLateMeal),
                factorTimes = mapOf(SleepFactor.IntenseExercise to 1_793_120_000_000L, SleepFactor.HeavyLateMeal to 1_793_130_000_000L),
                mood = 1f, opened = true, sleep = SleepQuality.ReallyWell, water = 2000, waterTimes = listOf(1_793_140_000_000L),
            ),
            DailyLog(LocalDate.of(2026, 10, 29), opened = false),
        ),
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
        assertEquals(data.history, c.history) // cada dia na sua data, com todos os campos
        assertEquals(29, c.age)
        assertEquals(Chronotype.NightOwl, c.chronotype)
        assertEquals(FallAsleepTime.ThirtyOrMore, c.fallAsleep)
        assertEquals(exportedAt, c.exportedAt)
        assertEquals(data.feedOrder, c.feedOrder)
        assertEquals(data.trackingOrder, c.trackingOrder)
    }

    /** Exportar o que se repôs dá a mesma cópia (fora a hora e a versão): nada se perde em vários ciclos. */
    @Test
    fun repeatedCyclesAreStable() {
        fun restored(c: BackupContents) = data.copy(
            routines = c.routines, dailyLog = c.dailyLog, history = c.history!!, age = c.age,
            chronotype = c.chronotype!!, fallAsleep = c.fallAsleep!!, feedOrder = c.feedOrder!!, trackingOrder = c.trackingOrder!!,
        )
        val first = buildBackupJson(data, exportedAt, "x")
        var json = first
        repeat(3) { json = buildBackupJson(restored(valid(json)), exportedAt, "x") }
        assertEquals(first, json)
    }

    @Test
    fun reminderSwitchesAreNotExportedAndOldOnesAreIgnored() {
        // Cyclo 3.2.2: os interruptores dos lembretes não vão na cópia.
        val json = buildBackupJson(data.copy(bedtimeReminder = false, logSleepReminder = true, hydrationReminder = true), exportedAt, "x")
        val settings = org.json.JSONObject(json).getJSONObject("data").getJSONObject("settings")
        for (key in listOf("bedtimeReminder", "logSleepReminder", "hydrationReminder")) {
            assertTrue("$key não pode ir na cópia", !settings.has(key))
        }
        // Uma cópia antiga que os traz continua válida; o resto entra.
        val old = """{"format":"cyclo-backup","version":2,"data":{"routines":[],
            "settings":{"age":40,"bedtimeReminder":true,"logSleepReminder":false,"hydrationReminder":true}}}"""
        assertEquals(40, valid(old).age)
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
