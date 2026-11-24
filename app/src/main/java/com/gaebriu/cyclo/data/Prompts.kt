package com.gaebriu.cyclo.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Os prompts (Cyclo 3.2.3): cartões com um convite, um botão e o close-b. Cada um tem um LUGAR; dois
 * no mesmo lugar partilham-no e trocam-se de lado (side swipe, ver ui/components/PromptCarousel.kt).
 * A ordem do enum é a ordem dentro do lugar.
 *
 * - Feed, por cima do wake-time-recommendation-group: [SleepEvaluation], [NeverMissBedtime].
 * - Feed, por cima do water-meter-group: [KeepWaterFlowing].
 * - Settings, no topo da página principal: [NewUpdate], [KeepProgressSafe].
 *
 * Quem aparece decide-se só aqui ([PromptRules], funções puras, PromptsTest); o que fica guardado
 * dos X está em [PromptStore]. A UI só pede a lista de um lugar.
 */
enum class CycloPrompt { SleepEvaluation, NeverMissBedtime, KeepWaterFlowing, NewUpdate, KeepProgressSafe }

/**
 * O que ficou guardado dos X (só no aparelho, como as outras preferências de ecrã; não vai nas
 * cópias). O new-update-group não está aqui: o X dele vale até a app voltar a abrir (ViewModel).
 *
 * @param sleepDismissedDay o dia da Cyclo ([cycloDay]) em que se fechou o sleep-evaluation-prompt.
 * @param neverMissDismissedAt / [waterDismissedAt] quando se fechou (epoch ms; 0 = nunca).
 * @param backupDismissedVersion o versionCode em que se fechou o keep-your-progress-safe (0 = nunca).
 */
@Immutable
data class PromptMemory(
    val sleepDismissedDay: LocalDate? = null,
    val neverMissDismissedAt: Long = 0L,
    val waterDismissedAt: Long = 0L,
    val backupDismissedVersion: Int = 0,
)

/**
 * As regras dos prompts (decididas com o utilizador na Cyclo 3.2.3):
 * - sleep-evaluation-prompt: como antes, do início do dia (a hora de acordar) até o sono de hoje
 *   estar registado ([showsSleepPrompt]); o X esconde-o até ao dia seguinte da Cyclo (a mesma
 *   fronteira dos contadores), mesmo que a app feche.
 * - never-miss-bedtime: dorme mal ([sleepsPoorly]) e o lembrete da hora de dormir está desligado
 *   (o estado efetivo: interruptor e autorização), com pelo menos uma rotina ligada.
 * - keep-the-water-flowing: bebe pouca água ([drinksLittle]) e o lembrete da água está desligado.
 * - Estes dois, fechados, descansam [HabitRestMs]; depois só voltam se os maus hábitos continuarem
 *   ou voltarem (se melhorarem, a condição deixa de valer e não aparecem).
 * - new-update-group: há uma versão nova e não foi fechado nesta abertura da app.
 * - keep-your-progress-safe: uma semana de progresso ([hasWeekOfProgress]) e as cópias automáticas
 *   desligadas. Fechado, não volta nesta versão; volta a poder aparecer numa versão nova (versionCode
 *   maior) ou depois de repor uma cópia que tenha uma semana de progresso ([PromptStore.dataRestored]).
 */
object PromptRules {
    /** As noites e os dias que contam para os hábitos (os últimos 7). */
    const val HabitDays = 7

    /** Noites com o sono registado, no mínimo, para dizer que dorme mal. */
    const val MinSleepNights = 3

    /** A média do sono (ordinal de [SleepQuality]: 0 = Really well … 4 = Very poor) a partir da qual dorme mal: "Meh". */
    const val PoorSleepMean = 2.0

    /** Dias com água registada, no mínimo, para dizer que bebe pouca. */
    const val MinWaterDays = 3

    /** Abaixo desta percentagem do adequado para a idade ([Hydration.targetMl]), a mediana é pouca água. */
    const val LowWaterPercent = 70

    /** Dias diferentes com registos reais para uma semana de progresso. */
    const val ProgressDays = 7

    /** Quanto o never-miss-bedtime e o keep-the-water-flowing descansam depois do X. */
    const val HabitRestMs = 7L * 24 * 60 * 60 * 1000

    /** O humor de um registo em que ninguém mexeu ([DailyLog.mood]). */
    private const val UntouchedMood = 0.5f

    /** Dorme mal: nas últimas [HabitDays] noites até [today], pelo menos [MinSleepNights] registadas e a média "Meh" ou pior. */
    fun sleepsPoorly(logs: Map<LocalDate, DailyLog>, today: LocalDate): Boolean {
        val nights = (0 until HabitDays).mapNotNull { logs[today.minusDays(it.toLong())]?.sleep }
        return nights.size >= MinSleepNights && nights.map { it.ordinal }.average() >= PoorSleepMean
    }

    /**
     * Bebe pouca água: nos [HabitDays] dias antes de [today] (o de hoje ainda vai a meio), pelo menos
     * [MinWaterDays] com água registada, e a mediana desses abaixo de [LowWaterPercent] % do adequado.
     * Quem nunca regista água não recebe o prompt.
     */
    fun drinksLittle(logs: Map<LocalDate, DailyLog>, today: LocalDate, age: Int?): Boolean {
        val days = (1..HabitDays).mapNotNull { logs[today.minusDays(it.toLong())]?.water?.takeIf { ml -> ml > 0 } }.sorted()
        if (days.size < MinWaterDays) return false
        val mid = days.size / 2
        val median = if (days.size % 2 == 1) days[mid].toDouble() else (days[mid - 1] + days[mid]) / 2.0
        return median * 100 < Hydration.targetMl(age).toDouble() * LowWaterPercent
    }

    /** Um registo com algo marcado (abrir a app, só por si, não conta). */
    fun DailyLog.hasProgress(): Boolean =
        sleep != null || caffeine > 0 || water > 0 || factors.isNotEmpty() || mood != UntouchedMood

    /** Uma semana de progresso: [ProgressDays] dias diferentes com registos reais. */
    fun hasWeekOfProgress(history: List<DailyLog>, current: DailyLog?): Boolean =
        dayLogs(history, current).values.count { it.hasProgress() } >= ProgressDays

    private fun rested(dismissedAt: Long, nowMs: Long): Boolean =
        dismissedAt == 0L || nowMs - dismissedAt !in 0 until HabitRestMs

    /** O lugar por cima do wake-time-recommendation-group. [logs] são os do [cycloDay] de [now]. */
    fun feedTop(
        routines: List<Routine>,
        logs: DayLogs,
        now: LocalDateTime,
        nowMs: Long,
        memory: PromptMemory,
        bedtimeReminderOn: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CycloPrompt> = buildList {
        val today = cycloDay(routines, now, zone)
        if (showsSleepPrompt(routines, logs.current, now, dismissed = memory.sleepDismissedDay == today, zone = zone)) {
            add(CycloPrompt.SleepEvaluation)
        }
        if (!bedtimeReminderOn &&
            routines.any { it.active } &&
            rested(memory.neverMissDismissedAt, nowMs) &&
            sleepsPoorly(dayLogs(logs.history, logs.current), today)
        ) {
            add(CycloPrompt.NeverMissBedtime)
        }
    }

    /** O lugar por cima do water-meter-group. */
    fun feedWater(
        logs: DayLogs,
        age: Int?,
        nowMs: Long,
        memory: PromptMemory,
        hydrationReminderOn: Boolean,
    ): List<CycloPrompt> =
        if (!hydrationReminderOn &&
            rested(memory.waterDismissedAt, nowMs) &&
            drinksLittle(dayLogs(logs.history, logs.current), logs.current.date, age)
        ) {
            listOf(CycloPrompt.KeepWaterFlowing)
        } else {
            emptyList()
        }

    /** O lugar no topo das definições. [updateShown]: há uma versão nova e não foi fechada nesta abertura. */
    fun settings(
        updateShown: Boolean,
        data: CycloData,
        memory: PromptMemory,
        versionCode: Int,
    ): List<CycloPrompt> = buildList {
        if (updateShown) add(CycloPrompt.NewUpdate)
        if (!data.autoBackup &&
            memory.backupDismissedVersion < versionCode &&
            hasWeekOfProgress(data.history, data.dailyLog)
        ) {
            add(CycloPrompt.KeepProgressSafe)
        }
    }
}

/**
 * O que fica guardado dos X dos prompts ([PromptMemory]), em SharedPreferences `prompts` (como as
 * atualizações). Lê-se uma vez; cada X grava e atualiza [memory].
 */
class PromptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE)

    private val _memory = MutableStateFlow(read())
    val memory: StateFlow<PromptMemory> = _memory.asStateFlow()

    private fun read() = PromptMemory(
        sleepDismissedDay = prefs.getString(KeySleepDay, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        neverMissDismissedAt = prefs.getLong(KeyNeverMissAt, 0L),
        waterDismissedAt = prefs.getLong(KeyWaterAt, 0L),
        backupDismissedVersion = prefs.getInt(KeyBackupVersion, 0),
    )

    /** O X de um prompt: [day] é o dia da Cyclo de agora (o sleep-evaluation-prompt volta no seguinte). */
    fun dismiss(prompt: CycloPrompt, day: LocalDate, nowMs: Long, versionCode: Int) {
        val edit = prefs.edit()
        when (prompt) {
            CycloPrompt.SleepEvaluation -> edit.putString(KeySleepDay, day.toString())
            CycloPrompt.NeverMissBedtime -> edit.putLong(KeyNeverMissAt, nowMs)
            CycloPrompt.KeepWaterFlowing -> edit.putLong(KeyWaterAt, nowMs)
            CycloPrompt.KeepProgressSafe -> edit.putInt(KeyBackupVersion, versionCode)
            // O X do new-update-group vale até a app voltar a abrir: não se guarda (ver CycloViewModel).
            CycloPrompt.NewUpdate -> return
        }
        edit.apply()
        _memory.value = read()
    }

    /**
     * Repôs-se uma cópia (com sucesso): se ela traz uma semana de progresso, o keep-your-progress-safe
     * fechado nesta versão volta a poder aparecer (se as cópias automáticas estiverem desligadas).
     */
    fun dataRestored(contents: BackupContents) {
        if (!PromptRules.hasWeekOfProgress(contents.history.orEmpty(), contents.dailyLog)) return
        prefs.edit().remove(KeyBackupVersion).apply()
        _memory.value = read()
    }

    private companion object {
        const val Prefs = "prompts"
        const val KeySleepDay = "sleep_dismissed_day"
        const val KeyNeverMissAt = "never_miss_dismissed_at"
        const val KeyWaterAt = "water_dismissed_at"
        const val KeyBackupVersion = "backup_dismissed_version"
    }
}
