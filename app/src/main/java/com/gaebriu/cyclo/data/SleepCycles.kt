package com.gaebriu.cyclo.data

import java.time.LocalTime

/** Um ciclo de sono dura cerca de 90 minutos (Introduction 1). */
const val SleepCycleMinutes = 90L

/** Tempo médio que se demora a adormecer, somado antes do primeiro ciclo. */
const val FallAsleepMinutes = 15L

/** Uma hora recomendada para ir dormir, e quantos ciclos completos dá até à hora de acordar. */
data class Bedtime(val time: LocalTime, val cycles: Int)

/**
 * As horas a que se deve adormecer para acordar no fim de um ciclo, da mais cedo (6 ciclos) à mais
 * tarde (1 ciclo): hora de acordar − n × 90 min − 15 min. Para as 06:00 dá 20:45, 22:15, 23:45,
 * 01:15, 02:45 e 04:15, como no Figma.
 *
 * @param fallAsleepMinutes o "Time to fall asleep" das definições (15 de omissão).
 */
fun bedtimesFor(wakeHour: Int, wakeMinute: Int, fallAsleepMinutes: Long = FallAsleepMinutes): List<Bedtime> {
    val wake = LocalTime.of(wakeHour, wakeMinute)
    return (6 downTo 1).map { cycles ->
        Bedtime(wake.minusMinutes(cycles * SleepCycleMinutes + fallAsleepMinutes), cycles)
    }
}
