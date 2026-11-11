package com.gaebriu.cyclo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaebriu.cyclo.ui.routine.RepeatOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

private val Context.cycloDataStore: DataStore<Preferences> by preferencesDataStore(name = "cyclo")

private val OnboardedKey = booleanPreferencesKey("onboarded")
private val RoutinesKey = stringPreferencesKey("routines")
private val DailyLogKey = stringPreferencesKey("daily_log")

/** Sem valor guardado, as horas seguem o formato do sistema. */
private val Use24HourKey = booleanPreferencesKey("use_24_hour")

/** "Advanced textures"; sem valor guardado, ligadas. */
private val AdvancedTexturesKey = booleanPreferencesKey("advanced_textures")

// Perfil de sono e lembretes (definições). Sem valor guardado, valem os de omissão do Figma.
private val AgeKey = intPreferencesKey("age")
private val ChronotypeKey = stringPreferencesKey("chronotype")
private val FallAsleepKey = stringPreferencesKey("fall_asleep")
private val BedtimeReminderKey = booleanPreferencesKey("bedtime_reminder")

/** O lembrete "Log sleep" (Beta 2.3); sem valor guardado, desligado. */
private val LogSleepReminderKey = booleanPreferencesKey("log_sleep_reminder")

/** A ordem dos blocos do Feed: os nomes separados por vírgulas (sem valor = a do Figma). */
private val FeedOrderKey = stringPreferencesKey("feed_order")

/** A ordem dos cartões da Tracking: os nomes separados por vírgulas (sem valor = a do Figma). */
private val TrackingOrderKey = stringPreferencesKey("tracking_order")

/** O histórico (Beta 2.0): os registos dos dias que já passaram, em JSON (ver [CycloData.history]). */
private val HistoryKey = stringPreferencesKey("history")

/** O versionCode da última abertura (ver [CycloData.lastRunVersion]). */
private val LastRunVersionKey = intPreferencesKey("last_run_version")

/** O DataStore da app (um só por processo: o lembrete e o widget também o leem). */
internal val Context.cycloStore: DataStore<Preferences> get() = applicationContext.cycloDataStore

/**
 * Onde a app guarda os dados (DataStore). As rotinas e o registo do dia vão em JSON; os enums
 * guardam-se pelo nome, para não se baralharem se a ordem mudar.
 *
 * As escritas lançam exceção quando falham: quem chama mostra o erro (undo-action com "Try again").
 */
class CycloRepository(context: Context) {
    private val store = context.applicationContext.cycloDataStore

    val data: Flow<CycloData> = store.data
        // Ficheiro ilegível: começa do zero em vez de rebentar.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            CycloData(
                onboarded = prefs[OnboardedKey] ?: false,
                routines = prefs[RoutinesKey]?.let(::parseRoutines).orEmpty(),
                dailyLog = prefs[DailyLogKey]?.let(::parseDailyLog),
                use24Hour = prefs[Use24HourKey],
                advancedTextures = prefs[AdvancedTexturesKey] ?: true,
                age = prefs[AgeKey]?.takeIf { it in AgeRange },
                chronotype = prefs[ChronotypeKey]
                    ?.let { name -> Chronotype.entries.firstOrNull { it.name == name } }
                    ?: Chronotype.Hummingbird,
                fallAsleep = prefs[FallAsleepKey]
                    ?.let { name -> FallAsleepTime.entries.firstOrNull { it.name == name } }
                    ?: FallAsleepTime.Fifteen,
                bedtimeReminder = prefs[BedtimeReminderKey] ?: true,
                logSleepReminder = prefs[LogSleepReminderKey] ?: false,
                feedOrder = prefs[FeedOrderKey]?.let(::parseFeedOrder) ?: DefaultFeedOrder,
                trackingOrder = prefs[TrackingOrderKey]?.let(::parseTrackingOrder) ?: DefaultTrackingOrder,
                history = prefs.history(),
                lastRunVersion = prefs[LastRunVersionKey],
            )
        }

    /** Guarda o formato das horas escolhido nas definições (fica até ser mudado outra vez). */
    suspend fun setUse24Hour(use24Hour: Boolean) {
        store.edit { prefs -> prefs[Use24HourKey] = use24Hour }
    }

    suspend fun setAdvancedTextures(on: Boolean) {
        store.edit { prefs -> prefs[AdvancedTexturesKey] = on }
    }

    suspend fun setAge(age: Int) {
        require(age in AgeRange)
        store.edit { prefs -> prefs[AgeKey] = age }
    }

    suspend fun setChronotype(chronotype: Chronotype) {
        store.edit { prefs -> prefs[ChronotypeKey] = chronotype.name }
    }

    suspend fun setFallAsleep(time: FallAsleepTime) {
        store.edit { prefs -> prefs[FallAsleepKey] = time.name }
    }

    suspend fun setBedtimeReminder(on: Boolean) {
        store.edit { prefs -> prefs[BedtimeReminderKey] = on }
    }

    suspend fun setLogSleepReminder(on: Boolean) {
        store.edit { prefs -> prefs[LogSleepReminderKey] = on }
    }

    /** Guarda a nova ordem das rotinas (arrastadas nas rotinas guardadas), pelos ids. */
    suspend fun reorderRoutines(ids: List<Long>) {
        store.edit { prefs ->
            val routines = prefs.routines()
            val position = ids.withIndex().associate { (i, id) -> id to i }
            // As que não vieram na lista (ex.: criadas entretanto) ficam no fim, pela ordem que tinham.
            prefs.setRoutines(routines.sortedBy { position[it.id] ?: (ids.size + routines.indexOf(it)) })
        }
    }

    /** Guarda a nova ordem dos blocos do Feed. */
    suspend fun setFeedOrder(order: List<FeedSection>) {
        store.edit { prefs -> prefs[FeedOrderKey] = normalizeFeedOrder(order).joinToString(",") { it.name } }
    }

    /** Guarda a nova ordem dos cartões da Tracking. */
    suspend fun setTrackingOrder(order: List<TrackingSection>) {
        store.edit { prefs -> prefs[TrackingOrderKey] = normalizeTrackingOrder(order).joinToString(",") { it.name } }
    }

    /** Guarda o versionCode desta abertura (ver [CycloData.lastRunVersion]). */
    suspend fun setLastRunVersion(versionCode: Int) {
        store.edit { prefs -> prefs[LastRunVersionKey] = versionCode }
    }

    /**
     * Repõe uma cópia já validada ([parseBackup]), numa só escrita: ou entra tudo, ou nada.
     * Substitui as rotinas e o registo do dia; as definições que a cópia não traz ficam como estão.
     * O tutorial fica dado como feito (quem repõe já está dentro da app).
     * As preferências do aparelho (formato das horas, língua, "Advanced textures") nunca mudam aqui.
     */
    suspend fun restore(contents: BackupContents) {
        store.edit { prefs ->
            prefs[OnboardedKey] = true
            prefs.setRoutines(contents.routines)
            val log = contents.dailyLog
            if (log != null) prefs[DailyLogKey] = log.toJson() else prefs.remove(DailyLogKey)
            contents.age?.let { prefs[AgeKey] = it }
            contents.chronotype?.let { prefs[ChronotypeKey] = it.name }
            contents.fallAsleep?.let { prefs[FallAsleepKey] = it.name }
            contents.bedtimeReminder?.let { prefs[BedtimeReminderKey] = it }
            contents.logSleepReminder?.let { prefs[LogSleepReminderKey] = it }
            contents.feedOrder?.let { order -> prefs[FeedOrderKey] = normalizeFeedOrder(order).joinToString(",") { it.name } }
            contents.trackingOrder?.let { order -> prefs[TrackingOrderKey] = normalizeTrackingOrder(order).joinToString(",") { it.name } }
            // Cópias sem histórico (formato 1, antes da Beta 2.0) não apagam o que já há.
            contents.history?.let { prefs.setHistory(it) }
        }
    }

    /**
     * Guarda uma rotina nova. A do tutorial ([onboarding] não null) marca-o como feito e guarda os
     * dois lembretes escolhidos no Introduction 6, tudo na MESMA escrita (ou entra tudo, ou nada).
     */
    suspend fun addRoutine(draft: RoutineDraft, onboarding: OnboardingReminders?, nowMillis: Long) {
        store.edit { prefs ->
            val routines = prefs.routines()
            val id = (routines.maxOfOrNull { it.id } ?: 0L) + 1L
            prefs.setRoutines(routines + draft.toRoutine(id, nowMillis))
            if (onboarding != null) {
                prefs[OnboardedKey] = true
                prefs[BedtimeReminderKey] = onboarding.bedtime
                prefs[LogSleepReminderKey] = onboarding.logSleep
            }
        }
    }

    /** Muda o que se escolheu numa rotina já guardada (ligada ou desligada, fica como estava). */
    suspend fun updateRoutine(id: Long, draft: RoutineDraft, nowMillis: Long) {
        store.edit { prefs ->
            prefs.setRoutines(
                prefs.routines().map { if (it.id == id) draft.toRoutine(id, nowMillis).copy(active = it.active) else it },
            )
        }
    }

    suspend fun setRoutineActive(id: Long, active: Boolean, nowMillis: Long) {
        store.edit { prefs ->
            prefs.setRoutines(
                prefs.routines().map {
                    // Ao ligar, uma "Show just this once" volta a valer para a próxima hora de acordar.
                    if (it.id == id) it.copy(active = active, anchorMillis = if (active) nowMillis else it.anchorMillis) else it
                },
            )
        }
    }

    suspend fun deleteRoutine(id: Long) {
        store.edit { prefs -> prefs.setRoutines(prefs.routines().filterNot { it.id == id }) }
    }

    /** Volta a pôr uma rotina apagada no mesmo lugar (o "Undo"). */
    suspend fun restoreRoutine(routine: Routine, index: Int) {
        store.edit { prefs ->
            val current = prefs.routines()
            val sameId = current.firstOrNull { it.id == routine.id }
            // Já lá está (dois "Undo" seguidos): nada a fazer.
            if (sameId == routine) return@edit
            // Entretanto criou-se outra rotina com o mesmo id (os ids novos são o maior + 1): a
            // apagada volta com um id novo, em vez de substituir a nova.
            val restored = if (sameId != null) routine.copy(id = (current.maxOf { it.id }) + 1L) else routine
            val routines = current.toMutableList()
            routines.add(index.coerceIn(0, routines.size), restored)
            prefs.setRoutines(routines)
        }
    }

    /**
     * Muda o registo de hoje. Se o guardado for de outro dia, passa para o histórico na MESMA escrita
     * (nunca se perde um dia) e começa um novo.
     */
    suspend fun updateDailyLog(today: LocalDate, change: (DailyLog) -> DailyLog) {
        store.edit { prefs ->
            // O mesmo arrumo que quem lê usa (resolveDayLogs): o toque cai sempre no registo que o
            // Feed mostra. Um registo guardado à frente do dia da Cyclo (as rotinas mudaram depois
            // da meia-noite) junta-se à noite certa, em vez de continuar escondido até acordar.
            val history = prefs.history()
            val logs = resolveDayLogs(prefs[DailyLogKey]?.let(::parseDailyLog), history, today)
            if (logs.history != history) prefs.setHistory(logs.history)
            prefs[DailyLogKey] = change(logs.current).toJson()
        }
    }
}

/**
 * Os lembretes escolhidos no Introduction 6 do tutorial (Beta 2.3). Guardam-se os dois, mesmo
 * desligados: quem salta a página fica com as notificações desligadas.
 */
data class OnboardingReminders(val bedtime: Boolean, val logSleep: Boolean)

private fun RoutineDraft.toRoutine(id: Long, nowMillis: Long) = Routine(
    id = id,
    name = name,
    wakeHour = wakeHour,
    wakeMinute = wakeMinute,
    repeat = repeat,
    daysMask = daysMask,
    active = true,
    anchorMillis = nowMillis,
)

private fun Preferences.routines(): List<Routine> = this[RoutinesKey]?.let(::parseRoutines).orEmpty()

private fun Preferences.history(): List<DailyLog> = this[HistoryKey]?.let(::parseHistory).orEmpty()

/** Guarda o histórico: um registo por data (o mais recente ganha), do mais antigo ao mais recente. */
private fun MutablePreferences.setHistory(logs: List<DailyLog>) {
    val byDate = LinkedHashMap<LocalDate, DailyLog>()
    logs.forEach { byDate[it.date] = it }
    this[HistoryKey] = historyToJson(byDate.values.sortedBy { it.date }).toString()
}

private fun parseHistory(json: String): List<DailyLog> = runCatching {
    val array = JSONArray(json)
    List(array.length()) { i -> array.optJSONObject(i)?.let(::parseDailyLogObject) }.filterNotNull()
}.getOrDefault(emptyList())

private fun parseTrackingOrder(text: String): List<TrackingSection> =
    normalizeTrackingOrder(text.split(',').mapNotNull { name -> TrackingSection.entries.firstOrNull { it.name == name.trim() } })

private fun MutablePreferences.setRoutines(routines: List<Routine>) {
    this[RoutinesKey] = routinesToJson(routines).toString()
}

private fun parseRoutines(json: String): List<Routine> = runCatching {
    val array = JSONArray(json)
    List(array.length()) { i ->
        val o = array.getJSONObject(i)
        Routine(
            id = o.getLong("id"),
            name = o.getString("name"),
            wakeHour = o.getInt("wakeHour"),
            wakeMinute = o.getInt("wakeMinute"),
            repeat = runCatching { RepeatOption.valueOf(o.getString("repeat")) }.getOrDefault(RepeatOption.JustOnce),
            daysMask = o.optInt("daysMask", 0),
            active = o.optBoolean("active", true),
            anchorMillis = o.optLong("anchor", 0L),
        )
    }
}.getOrDefault(emptyList())

/** Os nomes que esta versão conhece, pela ordem guardada, completados com os que faltam. */
private fun parseFeedOrder(text: String): List<FeedSection> =
    normalizeFeedOrder(text.split(',').mapNotNull { name -> FeedSection.entries.firstOrNull { it.name == name.trim() } })

private fun DailyLog.toJson(): String = dailyLogToJson(this).toString()

private fun parseDailyLog(json: String): DailyLog? = runCatching { JSONObject(json) }.getOrNull()?.let(::parseDailyLogObject)

private fun parseDailyLogObject(o: JSONObject): DailyLog? = runCatching {
    val factors = o.optJSONArray("factors")
    val caffeine = o.optInt("caffeine", 0)
    val factorSet = buildSet {
        if (factors != null) {
            for (i in 0 until factors.length()) {
                runCatching { SleepFactor.valueOf(factors.getString(i)) }.getOrNull()?.let(::add)
            }
        }
    }
    DailyLog(
        date = LocalDate.parse(o.getString("date")),
        caffeine = caffeine,
        factors = factorSet,
        mood = o.optDouble("mood", 0.5).toFloat(),
        caffeineTimes = parseCaffeineTimes(o, caffeine),
        factorTimes = parseFactorTimes(o, factorSet),
        // Registos de antes da Beta 2.0 não têm o campo: contam como abertos (ver DailyLog.opened).
        opened = o.optBoolean("opened", true),
        sleep = parseSleepQuality(o),
    )
}.getOrNull()
