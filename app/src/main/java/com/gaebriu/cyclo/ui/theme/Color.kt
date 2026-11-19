package com.gaebriu.cyclo.ui.theme

import androidx.compose.ui.graphics.Color

object CycloColors {
    val Background = Color(0xFF010101)
    val Accent = Color(0xFF4A3C99)
    val TextPrimary = Color(0xFFFCFCFE)
    val TextSecondary = Color(0xFFA3A2A7)
    val GlassFill = Color(0xFF101011)
    val GlassBorder = Color(0xFF313233)

    // As cinco cores das caras e do medidor (os pontos do gradiente "emotion-meter-background", Cyclo
    // 3.2: 0, 25, 50, 75 e 100 %), do melhor nível (mood) ao pior (dissatisfied). Também pintam os
    // gráficos e as horas recomendadas.
    val MoodViolet = Color(0xFFC4A8FF)
    val MoodBlue = Color(0xFF8FA6FF)

    /** O meio do gradiente: "Okay"/meh, a cara sentiment_neutral (no Figma desde o Cyclo 3.2). */
    val MoodOkay = Color(0xFF7FB4FB)
    val MoodSky = Color(0xFF6FC2F7)
    val MoodMint = Color(0xFF5ED6D0)

    /** O branco dos medidores (ball-b, os pontos e as luzes do fundo, Cyclo 3.2). */
    val MeterWhite = Color(0xFFF6F4FF)
}
