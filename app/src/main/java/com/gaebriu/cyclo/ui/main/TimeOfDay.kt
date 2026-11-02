package com.gaebriu.cyclo.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * Cores do brilho do topo do Feed e das rotinas guardadas, conforme a hora do aparelho:
 * [top] na metade de cima da elipse e [base] em baixo. A sombra do wake-time-container usa
 * sempre a [base] (no Figma, das 11 h às 15 h, é #34739A).
 */
@Immutable
data class SkyColors(val top: Color, val base: Color)

/**
 * As faixas horárias, cada uma de [início, fim[:
 * 04–07, 07–11, 11–15 (a do Figma), 15–19, 19–23 e 23–04.
 */
fun skyColorsAt(hour: Int): SkyColors = when (hour) {
    in 4 until 7 -> SkyColors(Color(0xFF16123A), Color(0xFF7A5190))
    in 7 until 11 -> SkyColors(Color(0xFF1E2060), Color(0xFF3F6FA6))
    in 11 until 15 -> SkyColors(Color(0xFF252C80), Color(0xFF34739A))
    in 15 until 19 -> SkyColors(Color(0xFF2E2670), Color(0xFFA0546C))
    in 19 until 23 -> SkyColors(Color(0xFF1A1540), Color(0xFF4A3C99))
    else -> SkyColors(Color(0xFF08070F), Color(0xFF221B57))
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
