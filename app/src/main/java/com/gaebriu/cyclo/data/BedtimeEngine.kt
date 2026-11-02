package com.gaebriu.cyclo.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.ceil

/**
 * Hora recomendada para ir dormir e quando começar a abrandar ("wind-down").
 *
 * Usa SÓ o que o Cyclo já recolhe: idade, cronótipo, "Time to fall asleep", a hora de acordar das
 * rotinas e o registo do dia (número de cafés, os três fatores e o medidor de emoções). Não há
 * horas da cafeína, intensidade do exercício, nível de stress nem histórico de sono, por isso não
 * se assumem.
 *
 * Ordem de prioridades (a primeira manda sempre):
 * 1. Duração de sono recomendada para a idade (consenso da National Sleep Foundation, Hirshkowitz
 *    et al. 2015, PMC4442216; CDC "About sleep"): 3–5 anos 10–13 h, 6–12 anos 9–12 h, 13–17 anos
 *    8–10 h, adultos 7 h ou mais (65+: 7–8 h). Usa-se o mínimo de cada faixa.
 * 2. Pelo menos 5 ciclos estimados (5 × ~90 min = 7 h 30). Os 90 min são uma estimativa para
 *    planear: os ciclos reais variam (~70–120 min) de pessoa para pessoa e ao longo da noite. Conta-se
 *    um número inteiro de ciclos que cubra o ponto 1 (nunca menos de 5).
 * 3. A hora de acordar da rotina é fixa: a hora de deitar é acordar − ciclos × 90 − tempo a adormecer.
 * 4–5. Cronótipo, cafeína, exercício, refeição, sesta e emoções mexem SÓ no início do wind-down
 *    (e no texto da notificação), NUNCA na hora de deitar nem no tempo de sono:
 *    - Cafeína (Gardiner et al. 2023, Sleep Med Rev 101764, meta-análise): reduz o sono total
 *      (~45 min) e atrasa o adormecer (~9 min); a meia-vida é longa (~5 h). Sem a hora do café,
 *      só se pode dar mais tempo para abrandar.
 *    - Exercício (Stutz et al. 2019, Sports Med, meta-análise): exercício à noite não piora o sono
 *      em geral, mas exercício intenso a acabar menos de ~1 h antes de deitar pode atrasar o
 *      adormecer. "Intense exercise" dá mais tempo para arrefecer.
 *    - Emoções e stress (ativação antes de dormir, ruminação): adormecer custa mais; uma rotina
 *      calma mais longa ajuda (revisões sobre higiene do sono e ativação pré-sono). Não se traduz
 *      em mais horas de sono, só em mais tempo para abrandar.
 *    - Luz e ritmo circadiano (NCBI NBK620865): luz forte à noite atrasa o relógio, sobretudo nos
 *      cronótipos tardios (Night owl): começa-se a baixar as luzes mais cedo.
 *    - Refeição pesada tarde: dá-se tempo para a digestão. Sesta longa ou tardia: menos pressão de
 *      sono; o deitar não muda (deitar mais tarde encurtaria o sono), só se avisa no texto.
 * 6. O wind-down tem um teto ([MaxWindDownMinutes]) e muda em passos de 5 min, para a hora do
 *    lembrete não saltar muito de dia para dia.
 *
 * Se já não houver tempo para os ciclos todos até acordar, recomenda-se ir dormir já (a hora mais
 * cedo possível), sem nunca baixar o número de ciclos pedido. Nada disto é conselho médico.
 */
object BedtimeEngine {
    const val CycleMinutes = 90
    const val MinCycles = 5
    const val BaseWindDownMinutes = 30
    const val MaxWindDownMinutes = 90

    /** Mínimo recomendado de sono para a idade, em minutos (idade por escrever: adulto). */
    fun ageMinimumSleepMinutes(age: Int?): Int = when {
        age == null -> 7 * 60
        age <= 5 -> 10 * 60
        age <= 12 -> 9 * 60
        age <= 17 -> 8 * 60
        else -> 7 * 60
    }

    /** Ciclos a planear: os que cobrem o mínimo da idade, e nunca menos de [MinCycles]. */
    fun targetCycles(age: Int?): Int =
        maxOf(MinCycles, ceil(ageMinimumSleepMinutes(age) / CycleMinutes.toDouble()).toInt())

    /**
     * O plano para uma noite.
     *
     * @param wake a hora a que a rotina acorda.
     * @param now agora (para saber se ainda dá tempo).
     * @param log o registo do dia; só conta se for de hoje.
     */
    fun plan(
        wake: LocalDateTime,
        now: LocalDateTime,
        age: Int?,
        chronotype: Chronotype,
        fallAsleep: FallAsleepTime,
        log: DailyLog?,
    ): BedtimePlan {
        val cycles = targetCycles(age)
        val sleepMinutes = cycles * CycleMinutes
        val latency = fallAsleep.planMinutes
        val idealBedtime = wake.minusMinutes((sleepMinutes + latency).toLong())
        val shortfall = idealBedtime.isBefore(now)
        // Sem tempo: o mais cedo possível é já (arredondado aos 5 min seguintes).
        val bedtime = if (shortfall) roundUpTo5(now) else idealBedtime

        // O registo é o de hoje e só vale para a noite que vem (não para as noites seguintes).
        val today = log?.takeIf {
            it.date == now.toLocalDate() && Duration.between(now, bedtime) < Duration.ofHours(20)
        }
        val reasons = reasonsFor(today, chronotype)
        val windDown = windDownMinutes(reasons, today)
        val windDownStart = bedtime.minusMinutes(windDown.toLong())

        return BedtimePlan(
            wake = wake,
            bedtime = bedtime,
            idealBedtime = idealBedtime,
            cycles = cycles,
            fallAsleepMinutes = latency,
            windDownStart = windDownStart,
            windDownMinutes = windDown,
            reasons = reasons,
            shortfall = shortfall,
        )
    }

    /** O que pesa hoje, do mais para o menos importante (o primeiro dá o texto da notificação). */
    fun reasonsFor(log: DailyLog?, chronotype: Chronotype): List<WindDownReason> = buildList {
        if (log != null) {
            if (log.mood >= StressedMood) add(WindDownReason.Stress)
            if (log.caffeine >= 1) add(WindDownReason.Caffeine)
            if (SleepFactor.IntenseExercise in log.factors) add(WindDownReason.Exercise)
            if (SleepFactor.HeavyLateMeal in log.factors) add(WindDownReason.Meal)
            if (SleepFactor.LongLateNap in log.factors) add(WindDownReason.Nap)
        }
        if (chronotype == Chronotype.NightOwl) add(WindDownReason.NightOwl)
    }

    /** Minutos de wind-down: 30 de base, mais o que cada fator pede, até [MaxWindDownMinutes]. */
    fun windDownMinutes(reasons: List<WindDownReason>, log: DailyLog?): Int {
        var minutes = BaseWindDownMinutes
        for (reason in reasons) {
            minutes += when (reason) {
                // 5 min por café, até 20: mais cafés, mais tempo para o corpo se acalmar.
                WindDownReason.Caffeine -> (5 * (log?.caffeine ?: 0)).coerceAtMost(20)
                WindDownReason.Stress -> if ((log?.mood ?: 0f) >= VeryStressedMood) 20 else 15
                WindDownReason.Exercise -> 15
                WindDownReason.Meal -> 15
                WindDownReason.NightOwl -> 15
                WindDownReason.Nap -> 0
            }
        }
        return (minutes.coerceAtMost(MaxWindDownMinutes) / 5) * 5
    }

    /** "Not great" ou pior no medidor de emoções (0 = Great ... 1 = Bad). */
    const val StressedMood = 0.625f
    const val VeryStressedMood = 0.875f

    private fun roundUpTo5(time: LocalDateTime): LocalDateTime {
        val base = time.withSecond(0).withNano(0)
        val extra = (5 - base.minute % 5) % 5
        val rounded = base.plusMinutes(extra.toLong())
        return if (rounded.isBefore(time)) rounded.plusMinutes(5) else rounded
    }

    /**
     * A próxima noite a planear: a primeira hora de acordar das rotinas ligadas (respeitando a
     * repetição e os dias), como no Feed.
     */
    fun nextWake(routines: List<Routine>, now: LocalDateTime): Pair<Routine, LocalDateTime>? =
        routines
            .mapNotNull { r -> r.nextWake(now)?.let { r to it } }
            .minByOrNull { it.second }

    /** As próximas horas de acordar de uma rotina, a partir de [now] (no máximo [count]). */
    fun upcomingWakes(routine: Routine, now: LocalDateTime, count: Int): List<LocalDateTime> {
        val result = ArrayList<LocalDateTime>(count)
        var from = now
        repeat(count) {
            val wake = routine.nextWake(from) ?: return result
            result += wake
            from = wake
        }
        return result
    }
}

/** Porque é que hoje vale a pena começar a abrandar mais cedo (por ordem de importância). */
enum class WindDownReason { Stress, Caffeine, Exercise, Meal, Nap, NightOwl }

data class BedtimePlan(
    val wake: LocalDateTime,
    /** Hora a que deve ir para a cama (já conta com o tempo a adormecer). */
    val bedtime: LocalDateTime,
    /** A hora que daria os ciclos todos (igual a [bedtime], a não ser que já tenha passado). */
    val idealBedtime: LocalDateTime,
    val cycles: Int,
    val fallAsleepMinutes: Int,
    val windDownStart: LocalDateTime,
    val windDownMinutes: Int,
    val reasons: List<WindDownReason>,
    /** Já não dá tempo para os ciclos todos: a recomendação é ir dormir quanto antes. */
    val shortfall: Boolean,
) {
    /** Tempo na cama até acordar (oportunidade de sono, já com o tempo a adormecer). */
    val sleepOpportunity: Duration get() = Duration.between(bedtime, wake)

    /** A noite a que o plano pertence (o dia em que se vai para a cama). */
    val night: LocalDate get() = bedtime.toLocalDate()
}
