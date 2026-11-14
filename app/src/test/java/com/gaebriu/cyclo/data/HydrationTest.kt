package com.gaebriu.cyclo.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A avaliação da água (beta-3.0): por idade, com défice E excesso. */
class HydrationTest {
    private val adult = 30

    @Test
    fun targetFollowsAge() {
        assertEquals(Hydration.ChildMl, Hydration.targetMl(5))
        assertEquals(Hydration.ChildMl, Hydration.targetMl(8))
        assertEquals(Hydration.PreteenMl, Hydration.targetMl(9))
        assertEquals(Hydration.PreteenMl, Hydration.targetMl(13))
        assertEquals(Hydration.AdultMl, Hydration.targetMl(14))
        assertEquals(Hydration.AdultMl, Hydration.targetMl(100))
        // Sem idade escrita: a de um adulto.
        assertEquals(Hydration.AdultMl, Hydration.targetMl(null))
    }

    @Test
    fun littleIsWorstAdequateIsBest() {
        assertEquals(4, Hydration.level(0, adult))
        assertEquals(4, Hydration.level(250, adult))
        assertEquals(3, Hydration.level(500, adult))
        assertEquals(2, Hydration.level(1000, adult))
        assertEquals(1, Hydration.level(1500, adult))
        assertEquals(0, Hydration.level(1750, adult))
        assertEquals(0, Hydration.level(2000, adult))
        assertEquals(0, Hydration.level(2500, adult))
    }

    @Test
    fun tooMuchGetsWorseAgain() {
        assertEquals(1, Hydration.level(2750, adult))
        assertEquals(1, Hydration.level(3000, adult))
        assertEquals(2, Hydration.level(3500, adult))
        assertEquals(3, Hydration.level(4500, adult))
        assertEquals(4, Hydration.level(5500, adult))
        assertEquals(4, Hydration.level(Hydration.MaxMl, adult))
    }

    @Test
    fun moreWaterNeverKeepsImprovingForever() {
        // A cada idade: o nível desce (melhora) até ao adequado e depois nunca volta a melhorar.
        for (age in listOf(5, 8, 9, 13, 14, 30, 70, 100, null)) {
            val levels = (0..Hydration.MaxMl step Hydration.StepMl).map { Hydration.level(it, age) }
            val best = levels.indexOf(0)
            assertTrue("$age reaches the best level", best >= 0)
            for (i in 1..best) assertTrue("$age improves up to the target", levels[i] <= levels[i - 1])
            for (i in best + 1 until levels.size) assertTrue("$age never improves past the target", levels[i] >= levels[i - 1])
            assertEquals("$age too much ends at the worst level", 4, levels.last())
        }
    }

    @Test
    fun ageChangesTheEvaluation() {
        // 1,25 L: bom para uma criança de 6 anos, pouco para um adulto.
        assertEquals(0, Hydration.level(1250, 6))
        assertEquals(2, Hydration.level(1250, adult))
        // 3 L: já demasiado para a criança, ainda aceitável para o adulto.
        assertTrue(Hydration.level(3000, 6) > Hydration.level(3000, adult))
        assertTrue(Hydration.isAbove(3000, 6))
        assertFalse(Hydration.isAbove(2000, adult))
    }

    @Test
    fun boundariesAreExact() {
        // 90 % e 140 % de 1800 mL: 1620 e 2520 ainda no melhor nível.
        assertEquals(0, Hydration.level(1620, adult))
        assertEquals(1, Hydration.level(1619, adult))
        assertEquals(0, Hydration.level(2520, adult))
        assertEquals(1, Hydration.level(2521, adult))
        // Valores impossíveis não rebentam.
        assertEquals(4, Hydration.level(-500, adult))
        assertEquals(4, Hydration.level(Int.MAX_VALUE, adult))
    }

    @Test
    fun summaryShowsLevelsOfOpenedDaysWithWater() {
        val today = LocalDate.of(2026, 11, 11)
        val history = listOf(
            DailyLog(today.minusDays(3), opened = true, water = 2000),
            // Aberto sem água: por registar (não fica no pior nível).
            DailyLog(today.minusDays(2), opened = true, water = 0),
            // Não aberto: por registar, mesmo com água.
            DailyLog(today.minusDays(1), opened = false, water = 2000),
        )
        val current = DailyLog(today, opened = true, water = 6000)
        val summary = trackingSummary(history, current, today, adult)
        assertEquals(listOf(null, null, null, 0, null, null, 4), summary.waterWeek.map { it.value })
        assertEquals(listOf(null, null, null, 2000, null, null, 6000), summary.waterWeekMl)
        // A média dos níveis: (0 + 4) / 2 = 2 (pouco e demasiado não se anulam num "adequado").
        assertEquals(2, summary.waterMonthAverage)
        // A idade muda a avaliação dos dias passados também.
        assertEquals(0, trackingSummary(history, DailyLog(today, opened = true, water = 1250), today, 6).waterWeek.last().value)
    }

    @Test
    fun summaryWithoutWaterHasNoAverage() {
        val today = LocalDate.of(2026, 11, 11)
        val summary = trackingSummary(emptyList(), DailyLog(today, opened = true), today, adult)
        assertNull(summary.waterMonthAverage)
        assertTrue(summary.waterWeek.all { it.value == null })
    }

    @Test
    fun nightMergeAddsWaterUpToTheMaximum() {
        val day = LocalDate.of(2026, 11, 10)
        val merged = mergeNightLogs(DailyLog(day, water = 1500), DailyLog(day.plusDays(1), water = 750))
        assertEquals(2250, merged.water)
        val capped = mergeNightLogs(DailyLog(day, water = 9000), DailyLog(day.plusDays(1), water = 9000))
        assertEquals(Hydration.MaxMl, capped.water)
    }

    @Test
    fun waterIsReadAndWrittenWithTheLog() {
        val log = DailyLog(LocalDate.of(2026, 11, 11), water = 1750)
        val json = dailyLogToJson(log)
        assertEquals(1750, json.getInt("water"))
        assertEquals(1750, parseWater(json))
        // Registos antigos, sem o campo: 0.
        assertEquals(0, parseWater(JSONObject().put("date", "2026-11-11")))
    }

    @Test
    fun backupKeepsWaterAndRejectsImpossibleAmounts() {
        val day = LocalDate.of(2026, 11, 11)
        val data = CycloData(
            onboarded = true,
            routines = emptyList(),
            dailyLog = DailyLog(day, opened = true, water = 1750),
            history = listOf(DailyLog(day.minusDays(1), opened = true, water = 500)),
            unifiedView = true,
        )
        val json = buildBackupJson(data, java.time.Instant.parse("2026-11-11T20:00:00Z"), "beta-3.0")
        val contents = (parseBackup(json) as BackupParseResult.Valid).contents
        assertEquals(1750, contents.dailyLog!!.water)
        assertEquals(500, contents.history!!.single().water)
        // O Unified view é do aparelho: não vai na cópia.
        assertFalse(json.contains("unified", ignoreCase = true))
        val tooMuch = json.replace("\"water\": 1750", "\"water\": ${Hydration.MaxMl + 1}")
        assertTrue(tooMuch != json)
        assertEquals(BackupProblem.Invalid, (parseBackup(tooMuch) as BackupParseResult.Rejected).problem)
        // Uma cópia antiga, sem "water": vale, com 0.
        val old = JSONObject(json).apply { getJSONObject("data").getJSONObject("dailyLog").remove("water") }.toString()
        assertEquals(0, (parseBackup(old) as BackupParseResult.Valid).contents.dailyLog!!.water)
    }
}
