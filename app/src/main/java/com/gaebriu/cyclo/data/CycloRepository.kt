package com.gaebriu.cyclo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
            )
        }

    /** Guarda uma rotina nova e, se for a do tutorial, marca-o como feito na MESMA escrita. */
    suspend fun addRoutine(draft: RoutineDraft, completeOnboarding: Boolean, nowMillis: Long) {
        store.edit { prefs ->
            val routines = prefs.routines()
            val id = (routines.maxOfOrNull { it.id } ?: 0L) + 1L
            prefs.setRoutines(routines + draft.toRoutine(id, nowMillis))
            if (completeOnboarding) prefs[OnboardedKey] = true
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
            val routines = prefs.routines().filterNot { it.id == routine.id }.toMutableList()
            routines.add(index.coerceIn(0, routines.size), routine)
            prefs.setRoutines(routines)
        }
    }

    /** Muda o registo de hoje (se o guardado for de outro dia, começa um novo). */
    suspend fun updateDailyLog(today: LocalDate, change: (DailyLog) -> DailyLog) {
        store.edit { prefs ->
            val current = prefs[DailyLogKey]?.let(::parseDailyLog)?.takeIf { it.date == today }
                ?: DailyLog(today)
            prefs[DailyLogKey] = change(current).toJson()
        }
    }
}

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

private fun MutablePreferences.setRoutines(routines: List<Routine>) {
    val array = JSONArray()
    routines.forEach { r ->
        array.put(
            JSONObject()
                .put("id", r.id)
                .put("name", r.name)
                .put("wakeHour", r.wakeHour)
                .put("wakeMinute", r.wakeMinute)
                .put("repeat", r.repeat.name)
                .put("daysMask", r.daysMask)
                .put("active", r.active)
                .put("anchor", r.anchorMillis),
        )
    }
    this[RoutinesKey] = array.toString()
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

private fun DailyLog.toJson(): String = JSONObject()
    .put("date", date.toString())
    .put("caffeine", caffeine)
    .put("factors", JSONArray(factors.map { it.name }))
    .put("mood", mood.toDouble())
    .toString()

private fun parseDailyLog(json: String): DailyLog? = runCatching {
    val o = JSONObject(json)
    val factors = o.optJSONArray("factors")
    DailyLog(
        date = LocalDate.parse(o.getString("date")),
        caffeine = o.optInt("caffeine", 0),
        factors = buildSet {
            if (factors != null) {
                for (i in 0 until factors.length()) {
                    runCatching { SleepFactor.valueOf(factors.getString(i)) }.getOrNull()?.let(::add)
                }
            }
        },
        mood = o.optDouble("mood", 0.5).toFloat(),
    )
}.getOrNull()
