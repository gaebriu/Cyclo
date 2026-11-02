package com.gaebriu.cyclo.ui.time

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Como se escrevem as horas na app (Feed, rotinas guardadas): "06:00" em 24 h ou "6:00 AM" em 12 h.
 * O seletor do Introduction 2 continua sempre em 24 h.
 */
class CycloTimeFormat(val use24Hour: Boolean, val formatter: DateTimeFormatter)

val LocalTimeFormat = staticCompositionLocalOf {
    CycloTimeFormat(true, DateTimeFormatter.ofPattern("HH:mm"))
}

/**
 * Dá o formato das horas a [content]: o escolhido nas definições ([use24Hour]) ou, sem escolha,
 * o do sistema operativo.
 */
@Composable
fun ProvideTimeFormat(use24Hour: Boolean?, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    // O do sistema lê-se de cada vez que a configuração muda (ex.: ao voltar das definições do Android).
    val system = DateFormat.is24HourFormat(context)
    val is24 = use24Hour ?: system
    val format = remember(is24, locale) {
        CycloTimeFormat(
            use24Hour = is24,
            formatter = cycloTimeFormatter(is24, locale),
        )
    }
    CompositionLocalProvider(LocalTimeFormat provides format, content = content)
}

/**
 * O formatador das horas do Cyclo: "06:00" em 24 h ou "6:00 AM" em 12 h. O "AM"/"PM" vai sempre
 * assim, em maiúsculas e sem pontos (o padrão "a" depende da língua e em português dá "a.m."/"p.m.").
 * Usado pela app, pelo widget e pela notificação.
 */
fun cycloTimeFormatter(use24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter =
    if (use24Hour) {
        DateTimeFormatter.ofPattern("HH:mm", locale)
    } else {
        DateTimeFormatterBuilder()
            .appendPattern("h:mm")
            .appendLiteral(' ')
            .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
            .toFormatter(locale)
    }
