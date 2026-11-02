package com.gaebriu.cyclo.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.pow

/**
 * O plano de uma noite: quando começar a abrandar (wind-down), quando estar na cama e quando se
 * estima adormecer.
 *
 * Usa SÓ o que o Cyclo já recolhe: idade, cronótipo, "Time to fall asleep", a hora de acordar das
 * rotinas e o registo do dia (cafés e quando foram registados, os três fatores e quando foram
 * marcados, e o medidor de emoções). Não há dados de luz, de ecrãs nem histórico de sono, por isso
 * não se assumem: a luz e os ecrãs só entram como conselho do wind-down.
 *
 * Regras (a primeira manda sempre):
 * 1. A hora de acordar da rotina é a referência fixa.
 * 2. Duração de sono para a idade (consenso da National Sleep Foundation, Hirshkowitz et al. 2015,
 *    PMC4442216; CDC "About sleep"): 3–5 anos 10–13 h, 6–12 anos 9–12 h, 13–17 anos 8–10 h, adultos
 *    7 h ou mais. Usa-se o mínimo de cada faixa.
 * 3. Pelo menos 5 ciclos estimados (5 × ~90 min = 7 h 30). Os 90 min são uma estimativa para
 *    planear (os ciclos reais variam ~70–120 min); conta-se um número inteiro de ciclos que cubra o
 *    ponto 2, nunca menos de 5.
 * 4. Hora de adormecer estimada = acordar − sono planeado. Hora de deitar = adormecer − latência
 *    estimada. Os fatores que dificultam o sono AUMENTAM a latência e por isso ANTECIPAM a hora de
 *    deitar: o sono planeado nunca encolhe. Nada empurra a hora de deitar para mais tarde.
 * 5. Wind-down = hora de deitar − minutos de preparação; os mesmos fatores dão mais preparação.
 *
 * Os fatores (todos em passos de 5 min, para a recomendação não oscilar):
 * - Cafeína (Gardiner et al. 2023, Sleep Med Rev 101764, meta-análise): reduz o sono total e atrasa
 *   o adormecer (~9 min em média). Para não afetar o sono, 107 mg (um café) devem ser tomados pelo
 *   menos 8,8 h antes de deitar e 217,5 mg pelo menos 13,2 h antes; doses maiores, mais horas
 *   (interpolação linear entre os dois pontos). Cada toque no "+" conta como um café de 107 mg à hora
 *   em que foi registado; várias doses somam-se com uma meia-vida de ~5 h. Se a hora de deitar cai
 *   antes de a cafeína "limpar", a latência e a preparação sobem com a cafeína que ainda resta.
 *   Cafés sem hora (registos antigos) só dão mais preparação: sem a hora não se sabe se ainda atuam.
 * - Emoções (ativação pré-sono, stress e ruminação; revisões PMC7853203 e PubMed 40840150): "Not
 *   great" e "Bad" demoram mais a adormecer e pedem uma rotina calma mais longa. Não dão mais horas
 *   de sono, só latência e preparação.
 * - Exercício intenso (Stutz et al. 2019, Sports Med, meta-análise): à noite não piora o sono, a
 *   não ser que seja intenso e acabe a menos de ~1 h de deitar. A hora em que foi marcado é o limite
 *   mais tarde em que acabou: marcado a menos de 1 h de deitar, conta como perto.
 * - Refeição pesada tarde: a menos de ~3 h de deitar atrasa um pouco o adormecer; dá-se tempo para a
 *   digestão.
 * - Sesta longa ou tardia: baixa a pressão de sono, adormece-se mais devagar (mais latência).
 * - Cronótipo (PubMed 33545116; Clin Transl Med 2023, ctm2.1131; luz e ritmo circadiano, NCBI
 *   NBK620865): cada tipo tem uma hora preferida para adormecer (Early bird ~22:00, Hummingbird
 *   ~23:00, Night owl ~00:30). Deitar bem antes dela custa mais (latência maior). O Night owl começa
 *   a baixar as luzes mais cedo (a luz da noite atrasa-lhe mais o relógio); o Early bird, se deitar
 *   depois da sua hora, começa a abrandar mais cedo, quando o corpo já o pede.
 *
 * Se já não houver tempo para os ciclos todos até acordar, recomenda-se ir dormir já, sem nunca
 * baixar o número de ciclos pedido. Nada disto é conselho médico nem garante um sono reparador.
 */
object BedtimeEngine {
    const val CycleMinutes = 90
    const val MinCycles = 5
    const val BaseWindDownMinutes = 30
    const val MaxWindDownMinutes = 120

    /** Teto da latência a mais por causa do dia (para o plano não fugir por somas de fatores). */
    const val MaxExtraLatencyMinutes = 45

    /** Um toque no "+" da cafeína: um café, 107 mg (a dose de referência de Gardiner et al. 2023). */
    const val CaffeineServingMg = 107.0

    /** Meia-vida da cafeína usada para somar doses (estimativa; varia muito de pessoa para pessoa). */
    const val CaffeineHalfLifeHours = 5.0

    private const val RefDoseMg = 107.0
    private const val RefHours = 8.8
    private const val HighDoseMg = 217.5
    private const val HighHours = 13.2

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
     * Quantas horas antes de deitar se deve parar a cafeína, para a dose [mg]: 8,8 h para 107 mg e
     * 13,2 h para 217,5 mg (Gardiner et al. 2023), em linha reta entre (e para lá de) os dois.
     */
    fun caffeineCutoffHours(mg: Double): Double =
        (RefHours + (mg - RefDoseMg) * (HighHours - RefHours) / (HighDoseMg - RefDoseMg)).coerceIn(0.0, 24.0)

    /** Hora preferida para adormecer de cada cronótipo (estimativa para planear). */
    fun preferredSleepTime(chronotype: Chronotype): LocalTime = when (chronotype) {
        Chronotype.EarlyBird -> LocalTime.of(22, 0)
        Chronotype.Hummingbird -> LocalTime.of(23, 0)
        Chronotype.NightOwl -> LocalTime.of(0, 30)
    }

    /**
     * O plano para uma noite.
     *
     * @param wake a hora a que a rotina acorda.
     * @param now agora (para saber se ainda dá tempo).
     * @param log o registo do dia; só conta se for de hoje.
     * @param zone o fuso das horas registadas (cafés e fatores).
     */
    fun plan(
        wake: LocalDateTime,
        now: LocalDateTime,
        age: Int?,
        chronotype: Chronotype,
        fallAsleep: FallAsleepTime,
        log: DailyLog?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BedtimePlan {
        val cycles = targetCycles(age)
        val sleepMinutes = cycles * CycleMinutes
        val baseLatency = fallAsleep.planMinutes
        // A hora estimada de adormecer: a hora de acordar menos o sono planeado. Não se mexe.
        val idealOnset = wake.minusMinutes(sleepMinutes.toLong())

        // O registo é o de hoje e só vale para a noite que vem (não para as noites seguintes).
        val today = log?.takeIf {
            it.date == now.toLocalDate() && Duration.between(now, idealOnset) < Duration.ofHours(20)
        }

        // A latência depende da hora de deitar (a cafeína e o cronótipo pesam mais quanto mais
        // cedo), e a hora de deitar da latência: duas passagens chegam (a latência só sobe ao
        // deitar mais cedo, por isso a segunda nunca desfaz a primeira).
        val first = assess(today, chronotype, idealOnset.minusMinutes(baseLatency.toLong()), zone)
        var latency = baseLatency + first.extraLatency
        val second = assess(today, chronotype, idealOnset.minusMinutes(latency.toLong()), zone)
        latency = baseLatency + maxOf(first.extraLatency, second.extraLatency)

        val idealBedtime = idealOnset.minusMinutes(latency.toLong())
        val shortfall = idealBedtime.isBefore(now)
        // Sem tempo: o mais cedo possível é já (arredondado aos 5 min seguintes).
        val bedtime = if (shortfall) roundUpTo5(now) else idealBedtime
        val final = assess(today, chronotype, bedtime, zone)
        val windDown = (BaseWindDownMinutes + final.extraWindDown).coerceAtMost(MaxWindDownMinutes)

        return BedtimePlan(
            wake = wake,
            bedtime = bedtime,
            idealBedtime = idealBedtime,
            sleepOnset = bedtime.plusMinutes(latency.toLong()),
            cycles = cycles,
            baseFallAsleepMinutes = baseLatency,
            fallAsleepMinutes = latency,
            windDownStart = bedtime.minusMinutes(windDown.toLong()),
            windDownMinutes = windDown,
            reasons = final.reasons,
            shortfall = shortfall,
        )
    }

    /** O que o dia e o cronótipo pedem, para quem se deita em [bedtime]. */
    internal class Assessment(val extraLatency: Int, val extraWindDown: Int, val reasons: List<WindDownReason>)

    /** Uma parcela: latência a mais, preparação a mais e o motivo (para a notificação). */
    private class Effect(val reason: WindDownReason, val latency: Int, val windDown: Int)

    internal fun assess(log: DailyLog?, chronotype: Chronotype, bedtime: LocalDateTime, zone: ZoneId): Assessment {
        val effects = buildList {
            if (log != null) {
                moodEffect(log)?.let(::add)
                caffeineEffect(log, bedtime, zone)?.let(::add)
                factorEffect(log, SleepFactor.IntenseExercise, bedtime, zone)?.let(::add)
                factorEffect(log, SleepFactor.HeavyLateMeal, bedtime, zone)?.let(::add)
                factorEffect(log, SleepFactor.LongLateNap, bedtime, zone)?.let(::add)
            }
            chronotypeEffect(chronotype, bedtime)?.let(::add)
        }.filter { it.latency > 0 || it.windDown > 0 }
        return Assessment(
            extraLatency = effects.sumOf { it.latency }.coerceAtMost(MaxExtraLatencyMinutes),
            extraWindDown = effects.sumOf { it.windDown },
            reasons = effects.map { it.reason },
        )
    }

    /** "Not great" (4.º ponto do medidor) e "Bad" (5.º). Os outros três não mudam nada. */
    private fun moodEffect(log: DailyLog): Effect? = when {
        log.mood >= VeryStressedMood -> Effect(WindDownReason.Stress, latency = 10, windDown = 25)
        log.mood >= StressedMood -> Effect(WindDownReason.Stress, latency = 5, windDown = 15)
        else -> null
    }

    /**
     * A cafeína que ainda atua em [bedtime]. As doses com hora juntam-se numa dose equivalente à
     * hora da última (as anteriores descontadas pela meia-vida); essa dose dá as horas até
     * "limpar" ([caffeineCutoffHours]). Se a hora de deitar vem antes, a latência sobe ~10 min por
     * cada 107 mg que restam à hora de deitar (Gardiner: ~9 min em média), entre 5 e 30.
     */
    private fun caffeineEffect(log: DailyLog, bedtime: LocalDateTime, zone: ZoneId): Effect? {
        if (log.caffeine <= 0) return null
        val timed = log.caffeineTimes.takeLast(log.caffeine).map { toLocal(it, zone) }
        val untimed = log.caffeine - timed.size
        var latency = 0
        var windDown = 0
        if (timed.isNotEmpty()) {
            val last = timed.max()
            val equivalent = timed.sumOf { CaffeineServingMg * decay(hoursBetween(it, last)) }
            val clearsAt = last.plusMinutes((caffeineCutoffHours(equivalent) * 60).toLong())
            if (bedtime.isBefore(clearsAt)) {
                val residual = equivalent * decay(hoursBetween(last, bedtime).coerceAtLeast(0.0))
                val servings = residual / CaffeineServingMg
                latency = ceilTo5(servings * 10).coerceIn(5, 30)
                windDown = ceilTo5(10 + servings * 10).coerceAtMost(40)
            }
        }
        // Sem hora não se sabe se ainda atua: só mais tempo para abrandar (5 min por café, até 20).
        windDown += (5 * untimed).coerceAtMost(20)
        return Effect(WindDownReason.Caffeine, latency, windDown)
    }

    /**
     * Os três fatores do Feed. A hora em que foi marcado é o mais tarde em que pode ter acontecido;
     * sem ela (registos antigos) conta só a preparação.
     */
    private fun factorEffect(log: DailyLog, factor: SleepFactor, bedtime: LocalDateTime, zone: ZoneId): Effect? {
        if (factor !in log.factors) return null
        val markedAt = log.factorTimes[factor]?.let { toLocal(it, zone) }
        val gap = markedAt?.let { Duration.between(it, bedtime).toMinutes() }
        return when (factor) {
            SleepFactor.IntenseExercise -> when {
                gap == null -> Effect(WindDownReason.Exercise, latency = 0, windDown = 15)
                gap < 60 -> Effect(WindDownReason.Exercise, latency = 10, windDown = 20)
                gap < 4 * 60 -> Effect(WindDownReason.Exercise, latency = 0, windDown = 10)
                else -> null
            }
            SleepFactor.HeavyLateMeal -> when {
                gap == null -> Effect(WindDownReason.Meal, latency = 0, windDown = 15)
                gap < 3 * 60 -> Effect(WindDownReason.Meal, latency = 5, windDown = 15)
                gap < 5 * 60 -> Effect(WindDownReason.Meal, latency = 0, windDown = 10)
                else -> null
            }
            SleepFactor.LongLateNap -> when {
                gap == null -> Effect(WindDownReason.Nap, latency = 5, windDown = 10)
                gap < 8 * 60 -> Effect(WindDownReason.Nap, latency = 10, windDown = 10)
                else -> Effect(WindDownReason.Nap, latency = 5, windDown = 5)
            }
        }
    }

    /**
     * Deitar antes da hora preferida do cronótipo: +1 min de latência por cada 6 min de
     * antecedência (até 20). Night owl: mais 15 min para baixar as luzes. Early bird a deitar depois
     * da sua hora: +1 min de preparação por cada 3 min de atraso (até 30).
     */
    private fun chronotypeEffect(chronotype: Chronotype, bedtime: LocalDateTime): Effect? {
        val early = minutesBeforePreferred(chronotype, bedtime)
        val latency = if (early > 0) ceilTo5(minOf(20.0, early / 6.0)) else 0
        return when (chronotype) {
            Chronotype.NightOwl -> Effect(WindDownReason.NightOwl, latency, windDown = 15)
            Chronotype.EarlyBird -> Effect(
                WindDownReason.EarlyBird,
                latency,
                windDown = if (early < 0) ceilTo5(minOf(30.0, -early / 3.0)) else 0,
            )
            Chronotype.Hummingbird -> if (latency > 0) Effect(WindDownReason.BodyClock, latency, windDown = 0) else null
        }
    }

    /** Minutos entre [bedtime] e a hora preferida mais próxima (positivo = deita-se antes dela). */
    internal fun minutesBeforePreferred(chronotype: Chronotype, bedtime: LocalDateTime): Long {
        val time = preferredSleepTime(chronotype)
        val candidates = (-1L..1L).map { bedtime.toLocalDate().plusDays(it).atTime(time) }
        val nearest = candidates.minBy { Duration.between(bedtime, it).abs() }
        return Duration.between(bedtime, nearest).toMinutes()
    }

    /** "Not great" ou pior no medidor de emoções (0 = Great ... 1 = Bad). */
    const val StressedMood = 0.625f
    const val VeryStressedMood = 0.875f

    private fun decay(hours: Double): Double = 0.5.pow(hours / CaffeineHalfLifeHours)

    private fun hoursBetween(from: LocalDateTime, to: LocalDateTime): Double =
        Duration.between(from, to).toMinutes() / 60.0

    private fun toLocal(millis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    private fun ceilTo5(minutes: Double): Int = (ceil(minutes / 5.0) * 5).toInt()

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
enum class WindDownReason { Stress, Caffeine, Exercise, Meal, Nap, NightOwl, EarlyBird, BodyClock }

data class BedtimePlan(
    val wake: LocalDateTime,
    /** Hora a que deve ir para a cama (já conta com o tempo estimado a adormecer). */
    val bedtime: LocalDateTime,
    /** A hora que daria os ciclos todos (igual a [bedtime], a não ser que já tenha passado). */
    val idealBedtime: LocalDateTime,
    /** Hora estimada para adormecer: [bedtime] mais [fallAsleepMinutes]. */
    val sleepOnset: LocalDateTime,
    val cycles: Int,
    /** O "Time to fall asleep" das definições. */
    val baseFallAsleepMinutes: Int,
    /** A latência estimada para hoje: a das definições mais o que o dia e o cronótipo pedem. */
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
