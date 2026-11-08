package com.gaebriu.cyclo.ui.main

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.SleepQuality
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors

/*
 * As caras do Cyclo (ícones de 15 dp e as cores do medidor), partilhadas pelo Feed e pela Tracking.
 */

/** Cara ao lado da chávena: muda com a cafeína (0 a 2 contente ... 6 ou mais insatisfeito). */
fun caffeineFace(count: Int): Pair<String, Color> = when {
    count <= 2 -> CycloAppIconPaths.SentimentSatisfied to CycloColors.MoodViolet
    count <= 4 -> CycloAppIconPaths.SentimentNeutral to CycloColors.MoodBlue
    count == 5 -> CycloAppIconPaths.SentimentDissatisfied to CycloColors.MoodSky
    else -> CycloAppIconPaths.SentimentDissatisfied to CycloColors.MoodMint
}

/**
 * A cara de cada nível do sono (a legenda do Restfulness, Cyclo-2 2013:101 a 2004:657): really-well
 * (mood) e well (sentiment_satisfied) a violeta, meh (sentiment_neutral) a azul, poor
 * (sentiment_dissatisfied) a céu e very-poor (sentiment_dissatisfied) a menta.
 */
fun SleepQuality.face(): Pair<String, Color> = when (this) {
    SleepQuality.ReallyWell -> CycloAppIconPaths.Mood to CycloColors.MoodViolet
    SleepQuality.Well -> CycloAppIconPaths.SentimentSatisfied to CycloColors.MoodViolet
    SleepQuality.Meh -> CycloAppIconPaths.SentimentNeutral to CycloColors.MoodBlue
    SleepQuality.Poor -> CycloAppIconPaths.SentimentDissatisfied to CycloColors.MoodSky
    SleepQuality.VeryPoor -> CycloAppIconPaths.SentimentDissatisfied to CycloColors.MoodMint
}

/** O nome de cada nível do sono ("Really well" ... "Very poor"). */
@get:StringRes
val SleepQuality.label: Int
    get() = when (this) {
        SleepQuality.ReallyWell -> R.string.sleep_quality_really_well
        SleepQuality.Well -> R.string.sleep_quality_well
        SleepQuality.Meh -> R.string.sleep_quality_meh
        SleepQuality.Poor -> R.string.sleep_quality_poor
        SleepQuality.VeryPoor -> R.string.sleep_quality_very_poor
    }

/**
 * A cor de cada nível do medidor de emoções na grelha do Mood (Tracking, 2006:237): Great violeta,
 * Good azul, Okay o meio do gradiente ([CycloColors.MoodOkay]), Not great céu, Bad menta.
 */
fun moodColor(level: Int): Color = when (level.coerceIn(0, 4)) {
    0 -> CycloColors.MoodViolet
    1 -> CycloColors.MoodBlue
    2 -> CycloColors.MoodOkay
    3 -> CycloColors.MoodSky
    else -> CycloColors.MoodMint
}

/**
 * A cara de cada nível do medidor de emoções (0 Great ... 4 Bad), para a média do Mood (average-mood,
 * Cyclo-2 2032:48; o Figma mostra a de Great: mood a violeta). As mesmas caras e cores, por nível,
 * da legenda do Restfulness ([face]): Great mood e Good satisfied a violeta, Okay neutral a azul,
 * Not great dissatisfied a céu e Bad dissatisfied a menta (as duas pontas são as do medidor do Feed).
 */
fun moodFace(level: Int): Pair<String, Color> = SleepQuality.entries[level.coerceIn(0, 4)].face()
