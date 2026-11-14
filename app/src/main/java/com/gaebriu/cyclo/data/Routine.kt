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
 * @property opened a app foi aberta neste dia depois da hora de acordar desse dia (ver
 *                  [trackingDayStart]): só então a cafeína e o humor deste dia contam na Tracking.
 *                  Registos antigos (antes da Beta 2.0) não têm este campo e contam como abertos:
 *                  só existiam porque o utilizador mexeu na app nesse dia.
 * @property sleep como dormiu na noite que acabou na manhã deste dia ("Log sleep"); null = por registar.
 * @property water a água bebida neste dia, em mL (water-meter-group, beta-3.0; passos de
 *                 [Hydration.StepMl], no máximo [Hydration.MaxMl]). Registos antigos não têm o campo: 0.
 */
@Immutable
data class DailyLog(
    val date: LocalDate,
    val caffeine: Int = 0,
    val factors: Set<SleepFactor> = emptySet(),
    val mood: Float = 0.5f,
    val caffeineTimes: List<Long> = emptyList(),
    val factorTimes: Map<SleepFactor, Long> = emptyMap(),
    val opened: Boolean = false,
    val sleep: SleepQuality? = null,
    val water: Int = 0,
)

/**
 * O nível do medidor de emoções do Feed ("How are you feeling tonight?"), de 0 (Great) a 4 (Bad):
 * o ponto mais perto de [DailyLog.mood].
 */
val DailyLog.moodLevel: Int get() = (mood.coerceIn(0f, 1f) * (MoodLevels - 1)).let { kotlin.math.round(it).toInt() }

/** Quantos níveis tem o medidor de emoções (Great, Good, Okay, Not great, Bad). */
const val MoodLevels = 5

/**
 * Como dormiu (o sleep-quality-meter do action-menu-log-sleep, Componentes 5), pela ordem do medidor,
 * da esquerda para a direita. Guarda-se pelo nome.
 */
enum class SleepQuality { ReallyWell, Well, Meh, Poor, VeryPoor }

/**
 * Os cartões da Tracking que se podem reordenar (long press e arrastar). O sleep-evaluation-prompt
 * não está aqui: quando aparece, fica sempre no topo. Water (beta-3.0) vem por baixo da cafeína.
 */
enum class TrackingSection { Restfulness, Caffeine, Water, Mood }

/** A ordem do Figma (Tracking, 2001:115), com a água por baixo da cafeína. */
val DefaultTrackingOrder: List<TrackingSection> = TrackingSection.entries

/** Como [normalizeFeedOrder], para os cartões da Tracking. */
fun normalizeTrackingOrder(order: List<TrackingSection>): List<TrackingSection> =
    completeOrder(order, DefaultTrackingOrder)

/**
 * Uma ordem guardada, completa: as repetidas contam uma vez, e cada uma que falta (uma secção nova
 * de uma versão mais recente, ex.: a água na beta-3.0) entra logo a seguir à que a precede na ordem
 * de omissão, onde quer que o utilizador a tenha posto (sem nenhuma antes, vai para o início). Assim
 * a água aparece por baixo da cafeína também em quem já tinha mudado a ordem.
 */
internal fun <T> completeOrder(order: List<T>, default: List<T>): List<T> {
    val result = order.distinct().filter { it in default }.toMutableList()
    default.forEachIndexed { i, section ->
        if (section in result) return@forEachIndexed
        val before = default.subList(0, i).lastOrNull { it in result }
        result.add(if (before == null) 0 else result.indexOf(before) + 1, section)
    }
    return result
}

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
enum class FeedSection { Caffeine, Water, SleepFactors, Feeling }

/** A ordem do Figma (Feed 24:10): cafeína, água, o que pode afetar o sono, como se sente. */
val DefaultFeedOrder: List<FeedSection> = FeedSection.entries

/**
 * Uma ordem guardada, completa: as secções que esta versão não conhece saem, as repetidas contam uma
 * vez e as que faltam entram a seguir à que as precede na ordem de omissão ([completeOrder]).
 */
fun normalizeFeedOrder(order: List<FeedSection>): List<FeedSection> =
    completeOrder(order, DefaultFeedOrder)

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
    /**
     * O interruptor "Log sleep" (Beta 2.3, reminders-group): o lembrete para registar o sono, depois
     * da hora de acordar. DESLIGADO de omissão (também para quem já usava a app); só vale de facto
     * com a autorização de notificações (ver LogSleepReminder).
     */
    val logSleepReminder: Boolean = false,
    /** A ordem dos blocos do Feed (reordenáveis), sem o grupo da hora de acordar. */
    val feedOrder: List<FeedSection> = DefaultFeedOrder,
    /** A ordem dos cartões da Tracking (reordenáveis), sem o prompt do sono. */
    val trackingOrder: List<TrackingSection> = DefaultTrackingOrder,
    /**
     * O histórico: o registo de cada dia que já passou (o de hoje está em [dailyLog]), do mais
     * antigo ao mais recente, um por data. Guarda-se tudo, para sempre (ver data/Tracking.kt).
     */
    val history: List<DailyLog> = emptyList(),
    /**
     * O versionCode da última vez que a app abriu (null = nunca guardado). Serve para saber, na
     * primeira abertura depois de uma atualização, que deve mostrar o Introduction 0 e ir logo
     * para o Feed (sem o tutorial). Fica no aparelho: não vai nas cópias.
     */
    val lastRunVersion: Int? = null,
    /**
     * "Unified view" (definições, Interface, beta-3.0): a Tracking junta-se ao Feed (cada gráfico no
     * cartão do Feed que lhe corresponde) e a barra de navegação fica só com o Feed e as rotinas
     * guardadas. Desligado de omissão. Preferência do aparelho: não vai nas cópias.
     */
    val unifiedView: Boolean = false,
)

/** Idades aceites na caixa da idade. */
val AgeRange = 5..100
