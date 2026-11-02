package com.gaebriu.cyclo.ui.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class TimeFormatTest {
    private val evening = LocalTime.of(18, 5)
    private val morning = LocalTime.of(6, 5)

    /** Em português o padrão "a" dava "a.m."/"p.m.": tem de ser sempre "AM"/"PM". */
    @Test
    fun twelveHourUsesUppercaseAmPmInAnyLanguage() {
        for (locale in listOf(Locale.forLanguageTag("pt-PT"), Locale.forLanguageTag("pt-BR"), Locale.US, Locale.UK)) {
            val f = cycloTimeFormatter(use24Hour = false, locale = locale)
            assertEquals(locale.toString(), "6:05 PM", evening.format(f))
            assertEquals(locale.toString(), "6:05 AM", morning.format(f))
        }
    }

    @Test
    fun twentyFourHour() {
        assertEquals("18:05", evening.format(cycloTimeFormatter(use24Hour = true, locale = Locale.forLanguageTag("pt-PT"))))
        assertEquals("06:05", morning.format(cycloTimeFormatter(use24Hour = true, locale = Locale.US)))
    }
}
