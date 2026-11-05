package com.gaebriu.cyclo.ui.time

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.staticCompositionLocalOf
import com.gaebriu.cyclo.ui.i18n.rememberAppLocale
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
 * o do sistema operativo, que se segue em tempo real (ver [rememberSystem24Hour]).
 */
@Composable
fun ProvideTimeFormat(use24Hour: Boolean?, content: @Composable () -> Unit) {
    val locale = rememberAppLocale()
    val system = rememberSystem24Hour()
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
 * O formato 12/24 h do sistema, sempre atual. Mudar "Usar formato de 24 horas" nas definições do
 * Android não é uma mudança de configuração (a Activity não sabe de nada): segue-se a definição do
 * sistema ([Settings.System.TIME_12_24]) com um observador e volta a ler-se ao regressar à app e
 * quando a configuração muda (a língua do sistema também decide o formato quando não há escolha).
 * Nada disto se guarda: sem escolha no Cyclo, vale sempre o do sistema.
 */
@Composable
private fun rememberSystem24Hour(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val state = remember(context) { mutableStateOf(DateFormat.is24HourFormat(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = DateFormat.is24HourFormat(context)
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = DateFormat.is24HourFormat(context)
        }
        lifecycle.addObserver(resume)
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(resume)
        }
    }
    LaunchedEffect(configuration) { state.value = DateFormat.is24HourFormat(context) }
    return state.value
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
