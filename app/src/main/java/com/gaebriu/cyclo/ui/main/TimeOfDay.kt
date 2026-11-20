package com.gaebriu.cyclo.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gaebriu.cyclo.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Cores do brilho do topo do Feed, das rotinas guardadas e da Tracking, conforme a hora do
 * aparelho: [top] na metade de cima da elipse e [base] em baixo. A [base] também pinta a sombra do
 * wake-time-container e o "fx blur type 01 (base)" do sleep-evaluation-prompt (no Figma, das
 * 11:30 às 15:30, é #5FA2DB).
 */
@Immutable
data class SkyColors(val top: Color, val base: Color)

/** Onde começa cada faixa do brilho, em minutos do dia (ver [skyColorsAt]). */
private val SkyStarts = intArrayOf(5 * 60, 8 * 60, 11 * 60 + 30, 15 * 60 + 30, 18 * 60 + 30, 20 * 60 + 30, 23 * 60 + 30)

/** Onde começa cada saudação do Feed, em minutos do dia (ver [greetingAt]). */
private val GreetingStarts = intArrayOf(5 * 60, 12 * 60, 18 * 60)

/**
 * As faixas horárias (Beta 2.1, escolhidas pelo utilizador), cada uma de [início, fim[:
 * 23:30–05:00, 05:00–08:00, 08:00–11:30, 11:30–15:30, 15:30–18:30, 18:30–20:30 e 20:30–23:30.
 */
fun skyColorsAt(hour: Int, minute: Int): SkyColors = when (hour * 60 + minute) {
    in 5 * 60 until 8 * 60 -> SkyColors(Color(0xFF1A1342), Color(0xFFB67FA6))
    in 8 * 60 until 11 * 60 + 30 -> SkyColors(Color(0xFF1E2468), Color(0xFF7C9AE6))
    in 11 * 60 + 30 until 15 * 60 + 30 -> SkyColors(Color(0xFF2A3290), Color(0xFF5FA2DB))
    in 15 * 60 + 30 until 18 * 60 + 30 -> SkyColors(Color(0xFF2C2470), Color(0xFFDE8A7E))
    in 18 * 60 + 30 until 20 * 60 + 30 -> SkyColors(Color(0xFF251A62), Color(0xFFA24FA0))
    in 20 * 60 + 30 until 23 * 60 + 30 -> SkyColors(Color(0xFF160F3E), Color(0xFF4A3C99))
    // 23:30 até às 05:00 (passa a meia-noite).
    else -> SkyColors(Color(0xFF07060F), Color(0xFF1D1A4F))
}

/** A saudação do header-greetings do Feed: manhã 5–11 h, tarde 12–17 h, noite 18–4 h. */
@StringRes
fun greetingAt(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.feed_greeting_morning
    in 12..17 -> R.string.feed_greeting_afternoon
    else -> R.string.feed_greeting_evening
}

/** A fase do dia que o Feed e as rotinas guardadas mostram: as cores do brilho e a saudação. */
@Immutable
data class DayPhase(val sky: SkyColors, @StringRes val greeting: Int)

fun dayPhaseAt(time: LocalDateTime): DayPhase = DayPhase(skyColorsAt(time.hour, time.minute), greetingAt(time.hour))

/**
 * O próximo momento em que a fase do dia muda depois de [now]: a próxima fronteira das faixas do
 * brilho ou da saudação (05:00, 08:00, 11:30, 12:00, 15:30, 18:00, 18:30, 20:30, 23:30).
 */
fun nextDayPhaseChange(now: ZonedDateTime): ZonedDateTime {
    val minute = now.hour * 60 + now.minute
    val next = (SkyStarts + GreetingStarts).filter { it > minute }.minOrNull()
    // Na hora do relógio (LocalDateTime): num dia de mudança de hora, somar minutos a um ZonedDateTime
    // contava a hora que saltou e chegava uma hora depois.
    val day = now.toLocalDate().atStartOfDay()
    val at = if (next != null) day.plusMinutes(next.toLong()) else day.plusDays(1).plusMinutes((SkyStarts + GreetingStarts).min().toLong())
    return at.atZone(now.zone)
}

/**
 * A fase do dia (Cyclo 3.2.2), sem perguntar as horas a cada instante: calcula-se a fase de agora e
 * espera-se UMA vez pela próxima fronteira ([nextDayPhaseChange]); lá, calcula-se a seguinte. Só com a
 * app à vista; ao voltar (desbloquear, regressar de outra app) e quando o relógio, o fuso ou a data
 * mudam no sistema, recalcula-se logo.
 */
@Composable
fun rememberDayPhase(): State<DayPhase> = rememberTimeDriven { now -> dayPhaseAt(now.toLocalDateTime()) to nextDayPhaseChange(now) }

/**
 * A hora atual, ao minuto: o HH:mm do header-small e as contas do Feed (horas para dormir, o dia da
 * Cyclo) precisam dela minuto a minuto. Como a fase do dia, só corre com a app à vista e acerta-se logo
 * ao voltar e quando o relógio ou o fuso mudam (antes contava em segundo plano e, depois de o telemóvel
 * dormir, podia mostrar a hora antiga até um minuto depois de desbloquear).
 */
@Composable
fun rememberMinuteClock(): State<LocalDateTime> = rememberTimeDriven { now ->
    val minute = now.truncatedTo(ChronoUnit.MINUTES)
    minute.toLocalDateTime() to minute.plusMinutes(1)
}

/** Mudanças de hora vindas do sistema: o utilizador acertou o relógio, mudou o fuso ou a data mudou. */
private fun timeChanges() = IntentFilter().apply {
    addAction(Intent.ACTION_TIME_CHANGED)
    addAction(Intent.ACTION_TIMEZONE_CHANGED)
    addAction(Intent.ACTION_DATE_CHANGED)
}

/**
 * Um valor que depende da hora: [compute] dá o valor de agora e quando volta a mudar. Enquanto a app
 * está à vista (STARTED), espera só por esse momento ou por uma mudança de hora no sistema; fora de
 * vista não corre nada. A espera conta em tempo de funcionamento (pára quando o telemóvel dorme): o
 * recomeço ao voltar à vista corrige isso.
 */
@Composable
private fun <T> rememberTimeDriven(compute: (ZonedDateTime) -> Pair<T, ZonedDateTime>): State<T> {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val initial = remember { compute(ZonedDateTime.now()).first }
    return produceState(initial, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val changed = Channel<Unit>(Channel.CONFLATED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    changed.trySend(Unit)
                }
            }
            context.registerReceiver(receiver, timeChanges(), Context.RECEIVER_NOT_EXPORTED)
            try {
                while (true) {
                    val now = ZonedDateTime.now()
                    val (current, next) = compute(now)
                    value = current
                    val wait = Duration.between(now, next).toMillis().coerceAtLeast(1L)
                    withTimeoutOrNull(wait) { changed.receive() }
                }
            } finally {
                context.unregisterReceiver(receiver)
            }
        }
    }
}
