package com.gaebriu.cyclo.data

import androidx.compose.runtime.Immutable
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.Weekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Uma rotina guardada: a hora de acordar, o nome e quando aparece no Feed.
 *
 * @property daysMask os dias escolhidos em "Show in selected days" (ver [com.gaebriu.cyclo.ui.routine.hasDay]).
 *                    Guarda-se mesmo com outra opção em vigor, para não se perder ao trocar de opção.
 * @property active se está ligada (o botão do visto das rotinas guardadas). Desligada, não aparece no Feed.
 * @property anchorMillis quando foi criada, editada ou ligada pela última vez. Uma rotina "Show just
 *                        this once" vale para a primeira hora de acordar depois deste momento.
 */
@Immutable
data class Routine(
    val id: Long,
    val name: String,
    val wakeHour: Int,
    val wakeMinute: Int,
    val repeat: RepeatOption,
    val daysMask: Int,
    val active: Boolean,
    val anchorMillis: Long,
) {
    /** Os dias em que a rotina se repete (máscara de bits; 0 em "Show just this once"). */
    val repeatMask: Int
        get() = when (repeat) {
            RepeatOption.JustOnce -> 0
            RepeatOption.Everyday -> AllDaysMask
            RepeatOption.DuringWeek -> WeekDaysMask
            RepeatOption.DuringWeekend -> WeekendMask
            RepeatOption.SelectedDays -> daysMask
        }

    /**
     * A próxima vez que esta rotina acorda o utilizador, depois de [now]; null se já não houver
     * (uma rotina "Show just this once" já passada, ou desligada).
     */
    fun nextWake(now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        if (!active) return null
        if (repeat == RepeatOption.JustOnce) {
            val anchor = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(anchorMillis), zone)
            var wake = anchor.toLocalDate().atTime(wakeHour, wakeMinute)
            if (!wake.isAfter(anchor)) wake = wake.plusDays(1)
            return wake.takeIf { it.isAfter(now) }
        }
        val mask = repeatMask
        if (mask == 0) return null
        // Hoje (se a hora ainda não passou) ou um dos próximos 7 dias.
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val wake = date.atTime(wakeHour, wakeMinute)
            if (wake.isAfter(now) && mask and date.weekdayBit() != 0) return wake
        }
        return null
    }

    companion object {
        const val AllDaysMask = 0b1111111

        /** Segunda a sexta. */
        const val WeekDaysMask = 0b0111110

        /** Domingo e sábado. */
        const val WeekendMask = 0b1000001
    }
}

/** O bit deste dia na máscara dos dias (bit 0 = domingo ... bit 6 = sábado). */
fun LocalDate.weekdayBit(): Int = 1 shl dayOfWeek.toWeekday().ordinal

fun DayOfWeek.toWeekday(): Weekday = when (this) {
    DayOfWeek.SUNDAY -> Weekday.Sunday
    DayOfWeek.MONDAY -> Weekday.Monday
    DayOfWeek.TUESDAY -> Weekday.Tuesday
    DayOfWeek.WEDNESDAY -> Weekday.Wednesday
    DayOfWeek.THURSDAY -> Weekday.Thursday
    DayOfWeek.FRIDAY -> Weekday.Friday
    DayOfWeek.SATURDAY -> Weekday.Saturday
}

/** O que o utilizador escolheu ao criar ou editar uma rotina (Introduction 2 a 5). */
@Immutable
data class RoutineDraft(
    val name: String,
    val wakeHour: Int,
    val wakeMinute: Int,
    val repeat: RepeatOption,
    val daysMask: Int,
)

/** Coisas que podem afetar o sono ("Anything today that might affect your sleep?"). */
enum class SleepFactor { IntenseExercise, HeavyLateMeal, LongLateNap }

/**
 * O que o utilizador registou hoje no Feed: cafeína, o que pode afetar o sono e como se sente.
 * Recomeça a cada dia.
 *
 * @property mood de 0 (contente, à esquerda) a 1 (insatisfeito, à direita).
 * @property caffeineTimes quando foi registado cada café (epoch ms, do mais antigo ao mais recente).
 *                         Pode ter menos entradas do que [caffeine] (registos de versões antigas,
 *                         sem hora); nunca mais.
 * @property factorTimes quando cada fator foi marcado (epoch ms). Só os fatores em [factors].
 */
@Immutable
data class DailyLog(
    val date: LocalDate,
    val caffeine: Int = 0,
    val factors: Set<SleepFactor> = emptySet(),
    val mood: Float = 0.5f,
    val caffeineTimes: List<Long> = emptyList(),
    val factorTimes: Map<SleepFactor, Long> = emptyMap(),
)

/**
 * Cronótipo escolhido nas definições (action-menu-chronotype, Componentes 3), pela ordem do menu.
 * Hummingbird é o tipo intermédio e o valor de omissão do Figma.
 */
enum class Chronotype { Hummingbird, EarlyBird, NightOwl }

/**
 * Quanto tempo se demora a adormecer (action-menu-time-to-fall-asleep, Componentes 3).
 *
 * @property planMinutes os minutos usados nas contas: "Under 5 minutes" conta como 5 e
 *                       "30 minutes or more" como 30 (o valor mais baixo da opção, para nunca
 *                       encurtar o tempo na cama por causa disto; ver BedtimeEngine).
 */
enum class FallAsleepTime(val planMinutes: Int) {
    UnderFive(5),
    Fifteen(15),
    ThirtyOrMore(30),
}

/**
 * Os blocos do Feed que se podem reordenar (long press e arrastar). O wake-time-recommendation-group
 * ("To rise refreshed tomorrow") não está aqui: fica sempre no seu lugar, no topo.
 */
enum class FeedSection { Caffeine, SleepFactors, Feeling }

/** A ordem do Figma: cafeína, o que pode afetar o sono, como se sente. */
val DefaultFeedOrder: List<FeedSection> = FeedSection.entries

/**
 * Uma ordem guardada, completa: as secções que esta versão não conhece saem, as que faltam vão
 * para o fim (pela ordem de omissão) e as repetidas contam uma vez.
 */
fun normalizeFeedOrder(order: List<FeedSection>): List<FeedSection> =
    (order + DefaultFeedOrder).distinct()

/** Tudo o que a app guarda. */
@Immutable
data class CycloData(
    val onboarded: Boolean,
    val routines: List<Routine>,
    val dailyLog: DailyLog?,
    /**
     * Formato das horas escolhido nas definições: true = 24 h, false = 12 h (AM/PM), null = o do
     * sistema (e segue-o quando muda). Só se guarda quando o utilizador escolhe no menu. É uma
     * preferência do aparelho: não vai nas cópias.
     */
    val use24Hour: Boolean? = null,
    /**
     * "Advanced textures" (definições, Performance): ligadas = o desenho completo (desfoques de fundo,
     * desfoque progressivo, misturas Luminosity); desligadas = o desenho simplificado. Ligadas de
     * omissão. Preferência do aparelho: não vai nas cópias.
     */
    val advancedTextures: Boolean = true,
    /** Idade (5 a 100); null = ainda por escrever ("Tap to write"). */
    val age: Int? = null,
    val chronotype: Chronotype = Chronotype.Hummingbird,
    val fallAsleep: FallAsleepTime = FallAsleepTime.Fifteen,
    /**
     * O interruptor "Bedtime" (lembrete) como o utilizador o deixou. Ligado de omissão, como no
     * Figma; só vale de facto com a autorização de notificações (ver BedtimeReminder).
     */
    val bedtimeReminder: Boolean = true,
    /** A ordem dos blocos do Feed (reordenáveis), sem o grupo da hora de acordar. */
    val feedOrder: List<FeedSection> = DefaultFeedOrder,
    /**
     * O versionCode da última vez que a app abriu (null = nunca guardado). Serve para saber, na
     * primeira abertura depois de uma atualização, que deve mostrar o Introduction 0 e ir logo
     * para o Feed (sem o tutorial). Fica no aparelho: não vai nas cópias.
     */
    val lastRunVersion: Int? = null,
)

/** Idades aceites na caixa da idade. */
val AgeRange = 5..100
