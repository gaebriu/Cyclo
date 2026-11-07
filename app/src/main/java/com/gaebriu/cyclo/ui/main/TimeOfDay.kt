package com.gaebriu.cyclo.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * Cores do brilho do topo do Feed, das rotinas guardadas e da Tracking, conforme a hora do
 * aparelho: [top] na metade de cima da elipse e [base] em baixo. A [base] também pinta a sombra do
 * wake-time-container e o "fx blur type 01 (base)" do sleep-evaluation-prompt (no Figma, das
 * 11:30 às 15:30, é #5FA2DB).
 */
@Immutable
data class SkyColors(val top: Color, val base: Color)

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

/** A hora atual, atualizada no início de cada minuto. */
@Composable
fun rememberMinuteClock(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val current = LocalDateTime.now()
        value = current
        val untilNextMinute = 60_000L - (current.second * 1000L + current.nano / 1_000_000L)
        delay(untilNextMinute.coerceAtLeast(250L))
    }
}
