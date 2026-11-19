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

/**
 * O nível da cafeína como o das caras do medidor (menor = melhor): 0 a 2 = 1, 3 e 4 = 2, 5 = 3,
 * 6 ou mais = 4. Diz à cara animada se a mudança melhorou ou piorou.
 */
fun caffeineLevel(count: Int): Int = when {
    count <= 2 -> 1
    count <= 4 -> 2
    count == 5 -> 3
    else -> 4
}

/** Cara ao lado da chávena: muda com a cafeína (0 a 2 contente ... 6 ou mais insatisfeito). */
fun caffeineFace(count: Int): Pair<String, Color> = when {
    count <= 2 -> CycloAppIconPaths.SentimentSatisfied to CycloColors.MoodBlue
    count <= 4 -> CycloAppIconPaths.SentimentNeutral to CycloColors.MoodOkay
    count == 5 -> CycloAppIconPaths.SentimentDissatisfied to CycloColors.MoodSky
    else -> CycloAppIconPaths.SentimentDissatisfied to CycloColors.MoodMint
}

/**
 * A cara de cada nível do sono (a legenda do Restfulness, Cyclo 3.2 2066:487 a 2066:502), cada uma
 * com a sua cor: really-well (mood) violeta, well (sentiment_satisfied) azul, meh
 * (sentiment_neutral) o meio, poor (sentiment_dissatisfied) céu e very-poor menta.
 */
fun SleepQuality.face(): Pair<String, Color> = when (this) {
    SleepQuality.ReallyWell -> CycloAppIconPaths.Mood to CycloColors.MoodViolet
    SleepQuality.Well -> CycloAppIconPaths.SentimentSatisfied to CycloColors.MoodBlue
    SleepQuality.Meh -> CycloAppIconPaths.SentimentNeutral to CycloColors.MoodOkay
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
 * A cor de cada nível (0 o melhor ... 4 o pior) nos blocos do mês (mood-graph-montly 6020:289 e
 * sleep-graph-montly 2066:455, Cyclo 3.2): as cinco cores do medidor, da violeta à menta.
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
 * da legenda do Restfulness ([face]).
 */
fun moodFace(level: Int): Pair<String, Color> = SleepQuality.entries[level.coerceIn(0, 4)].face()
