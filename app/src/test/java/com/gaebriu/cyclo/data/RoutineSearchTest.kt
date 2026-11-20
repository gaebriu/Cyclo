package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RoutineSearchTest {
    private val zone = ZoneOffset.UTC

    // Sexta-feira, 20 de novembro de 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 20, 10, 0)
    private val anchor = now.minusHours(1).toInstant(zone).toEpochMilli()

    private val burger = Routine(1, "Burger King", 7, 30, RepeatOption.Everyday, 0, active = true, anchorMillis = 0)
    private val morning = Routine(2, "Morning Routine", 6, 0, RepeatOption.DuringWeek, 0, active = true, anchorMillis = 0)
    private val cafe = Routine(3, "Café da manhã", 8, 15, RepeatOption.SelectedDays, 0b0001010, active = false, anchorMillis = 0)
    private val gym = Routine(4, "Gym", 19, 0, RepeatOption.DuringWeekend, 0, active = true, anchorMillis = 0)
    private val night = Routine(5, "Night shift", 22, 0, RepeatOption.SelectedDays, 0b0000001, active = true, anchorMillis = 0)
    private val once = Routine(6, "Routine 1", 14, 0, RepeatOption.JustOnce, 0, active = true, anchorMillis = anchor)
    private val all = listOf(burger, morning, cafe, gym, night, once)
    private val search = RoutineSearch(all)

    private fun find(q: String) = search.find(q, now, zone)
    private fun ids(q: String) = find(q).map { it.id }
    private fun same(q: String, vararg expected: Routine) = assertEquals(q, expected.map { it.id }.toSet(), ids(q).toSet())

    @Test
    fun foldIgnoresCaseAccentsPunctuationAndSpaces() {
        assertEquals("burger king", foldText("  BURGER-King!! "))
        assertEquals("cafe da manha", foldText("Café   da manhã"))
        assertEquals("7 30", foldText("7:30"))
    }

    @Test
    fun namesMatchWhateverTheFormatting() {
        for (q in listOf("burger king", "BURGER KING", "burgerking", "burger-king", "Burger  King!!", "bk", "BK", "burg", "king", "burgr king")) {
            assertEquals(q, burger.id, ids(q).first())
        }
        assertEquals(cafe.id, ids("cafe").first())
        assertEquals(cafe.id, ids("CAFÉ DA MANHÃ").first())
        assertEquals(morning.id, ids("Morning Routine").first())
    }

    @Test
    fun anExactNameHidesAttributeOnlyResults() {
        // "morning" também é a parte do dia, mas o nome igual ganha e chega.
        assertEquals(listOf(morning.id), ids("morning routine"))
        // Só "night": o nome primeiro, depois as rotinas da noite.
        assertEquals(listOf(night.id, gym.id), ids("night"))
    }

    @Test
    fun days() {
        for (q in listOf("monday", "segunda", "Segunda-feira", "rotinas de segunda", "routines on Monday", "seg", "mon")) {
            same(q, burger, morning, cafe)
        }
        for (q in listOf("weekend", "fim de semana", "rotinas ao fim de semana", "fds")) same(q, burger, gym, night)
        same("weekdays", burger, morning, cafe, once)
        same("todos os dias", burger)
        same("every day", burger)
        same("sexta", burger, morning, once)
    }

    @Test
    fun negation() {
        for (q in listOf("rotinas que não aparecem ao domingo", "routines that don't appear on Sunday", "not sunday")) {
            same(q, morning, cafe, once)
        }
        same("not enabled", cafe)
    }

    @Test
    fun activation() {
        for (q in listOf("disabled", "disabled routines", "rotinas desativadas", "desligadas", "off", "inativas")) same(q, cafe)
        same("enabled", burger, morning, gym, night, once)
        same("rotinas ligadas", burger, morning, gym, night, once)
    }

    @Test
    fun partsOfTheDayFollowTheGreetingBands() {
        for (q in listOf("rotinas que aparecem de manhã", "morning routines", "manha")) same(q, burger, morning, cafe)
        same("routines that appear in the evening", gym, night)
        same("à tarde", once)
    }

    @Test
    fun times() {
        for (q in listOf("7:30", "7h30", "07h30", "às 7", "at 7", "7am", "730")) same(q, burger)
        same("antes das 7", morning)
        same("before 7am", morning)
        same("after 6pm", gym, night)
        same("depois das 18h", gym, night)
        same("19h", gym)
        same("7 pm", gym)
    }

    @Test
    fun visibility() {
        same("uma vez", once)
        same("just once", once)
        same("dias escolhidos", cafe, night)
        // Hoje (sexta): as ligadas que acordam hoje.
        same("hoje", burger, morning, once)
        same("today", burger, morning, once)
        same("amanhã", burger, gym)
        same("hidden", cafe)
        same("visíveis", burger, morning, gym, night, once)
    }

    @Test
    fun attributesAndNameTogether() {
        same("gym weekend", gym)
        assertTrue(ids("gym monday").isEmpty())
    }

    @Test
    fun noiseDoesNotMatchEverything() {
        assertTrue(ids("a").isEmpty())
        assertTrue(ids("xyz").isEmpty())
        assertTrue(ids("de").isEmpty())
        assertEquals(all.map { it.id }, ids("rotinas"))
        assertEquals(all, find(""))
        assertEquals(all, find("   "))
    }

    @Test
    fun manyRoutinesStayFast() {
        val many = List(1000) { i ->
            Routine(i.toLong(), "Rotina número $i ${listOf("Ginásio", "Café", "Trabalho", "Escola")[i % 4]}", i % 24, i % 60,
                RepeatOption.entries[i % 5], i % 128, active = i % 3 != 0, anchorMillis = 0)
        }
        val big = RoutineSearch(many)
        val queries = listOf("gin", "cafe", "rotinas de segunda", "desativadas", "7:30", "trbalho", "rotina numero 999", "fim de semana")
        val start = System.nanoTime()
        repeat(20) { for (q in queries) big.find(q, now, zone) }
        val perQueryMs = (System.nanoTime() - start) / 1e6 / (20 * queries.size)
        println("RoutineSearch: 1000 rotinas, ${"%.2f".format(perQueryMs)} ms por pesquisa")
        assertTrue("$perQueryMs ms por pesquisa", perQueryMs < 16)
        assertEquals(999L, big.find("rotina numero 999", now, zone).first().id)
    }
}
