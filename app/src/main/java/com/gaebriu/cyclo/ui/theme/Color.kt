package com.gaebriu.cyclo.ui.theme

import androidx.compose.ui.graphics.Color

object CycloColors {
    val Background = Color(0xFF010101)
    val Accent = Color(0xFF4A3C99)
    val TextPrimary = Color(0xFFFCFCFE)
    val TextSecondary = Color(0xFFA3A2A7)
    val GlassFill = Color(0xFF101011)
    val GlassBorder = Color(0xFF313233)

    // As cores das caras e do medidor de emoções (os pontos do gradiente "emotion-meter-background"),
    // da esquerda (contente) para a direita (insatisfeito). Também pintam a Tracking.
    val MoodViolet = Color(0xFFB3A3E8)
    val MoodBlue = Color(0xFF8FA8F0)

    /**
     * "Okay" na grelha do Mood (Tracking): o meio do gradiente do medidor (entre o azul, a 25 %, e o
     * céu, a 75 %). Não está no Figma: pedida pelo utilizador ("entre o azul e o céu", Beta 2.0).
     */
    val MoodOkay = Color(0xFF87B5EC)
    val MoodSky = Color(0xFF7FC1E8)
    val MoodMint = Color(0xFF8FD9C4)
}
