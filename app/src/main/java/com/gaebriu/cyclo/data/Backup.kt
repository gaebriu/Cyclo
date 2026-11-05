package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Cópia de segurança em JSON ("Export my data" / "Restore my data").
 *
 * Formato (versão [BackupFormatVersion]):
 * ```
 * {
 *   "format": "cyclo-backup",
 *   "version": 1,
 *   "exportedAt": "2026-11-02T21:00:00Z",
 *   "appVersion": "alpha-0.8.0",
 *   "data": {
 *     "onboarded": true,
 *     "routines": [ { "id", "name", "wakeHour", "wakeMinute", "repeat", "daysMask", "active", "anchor" } ],
 *     "dailyLog": { "date", "caffeine", "factors": [], "mood", "caffeineTimes": [ms], "factorTimes": { nome: ms } } | null,
 *     "settings": { "age": 30 | null, "chronotype", "fallAsleep", "bedtimeReminder",
 *                   "feedOrder": ["Caffeine", "SleepFactors", "Feeling"] }
 *   }
 * }
 * ```
 * "caffeineTimes" e "factorTimes" (as horas dos registos, para o [BedtimeEngine]) vieram depois e
 * são opcionais: as versões anteriores ignoram-nos e as cópias sem eles continuam válidas, por isso
 * a versão do formato não mudou. O mesmo para "feedOrder" (a ordem dos blocos do Feed, alpha-0.7.3).
 * Os enums vão pelo nome, como no DataStore. Ao mudar o formato, sobe-se [BackupFormatVersion] e
 * [parseBackup] passa a saber ler as versões antigas.
 *
 * As preferências do aparelho ficam de fora: o formato das horas, a língua (guardada pelo Android)
 * e "Advanced textures" (beta-1.4). As cópias antigas trazem "use24Hour": continuam válidas e esse
 * valor é ignorado ao repor.
 */
const val BackupFormatVersion = 1
private const val FormatName = "cyclo-backup"

/** Ficheiros maiores do que isto não são uma cópia do Cyclo (as cópias reais têm poucos KB). */
const val MaxBackupBytes = 2 * 1024 * 1024

/** O que uma cópia válida traz. Os campos null não vieram na cópia e ficam como estão. */
data class BackupContents(
    val routines: List<Routine>,
    val dailyLog: DailyLog?,
    val age: Int?,
    val chronotype: Chronotype?,
    val fallAsleep: FallAsleepTime?,
    val bedtimeReminder: Boolean?,
    val exportedAt: Instant?,
    /** A ordem dos blocos do Feed (null = a cópia não a traz, fica a atual). */
    val feedOrder: List<FeedSection>? = null,
)

/** Porque é que uma cópia não pode ser reposta. */
enum class BackupProblem {
    /** Não é JSON, não é uma cópia do Cyclo, ou tem valores impossíveis. */
    Invalid,

    /** Foi feita por uma versão do Cyclo mais recente do que esta. */
    TooNew,
}

sealed interface BackupParseResult {
    data class Valid(val contents: BackupContents) : BackupParseResult
    data class Rejected(val problem: BackupProblem) : BackupParseResult
}

/** Transforma os dados num JSON de cópia, legível e com a versão do formato. */
fun buildBackupJson(data: CycloData, exportedAt: Instant, appVersion: String): String {
    val settings = JSONObject()
        .put("age", data.age ?: JSONObject.NULL)
        .put("chronotype", data.chronotype.name)
        .put("fallAsleep", data.fallAsleep.name)
        .put("bedtimeReminder", data.bedtimeReminder)
        .put("feedOrder", JSONArray(data.feedOrder.map { it.name }))
    val content = JSONObject()
        .put("onboarded", data.onboarded)
        .put("routines", routinesToJson(data.routines))
        .put("dailyLog", data.dailyLog?.let(::dailyLogToJson) ?: JSONObject.NULL)
        .put("settings", settings)
    return JSONObject()
        .put("format", FormatName)
        .put("version", BackupFormatVersion)
        .put("exportedAt", exportedAt.toString())
        .put("appVersion", appVersion)
        .put("data", content)
        .toString(2)
}

/**
 * Lê e valida a cópia INTEIRA antes de qualquer mudança nos dados.
 *
 * - Estrutura errada, valores impossíveis (ex.: hora 25, dias fora da máscara) ou uma rotina
 *   estragada: a cópia é recusada inteira, para nunca se repor só uma parte.
 * - Uma versão mais recente do formato: recusada (não se sabe ler).
 * - Valores que esta versão já não conhece (ex.: um cronótipo que deixou de existir) são
 *   ignorados e ficam como estão; o resto entra.
 * - Ids repetidos recebem um id novo, para não haver duas rotinas com o mesmo.
 */
fun parseBackup(text: String): BackupParseResult {
    val root = try {
        JSONObject(text)
    } catch (e: JSONException) {
        return BackupParseResult.Rejected(BackupProblem.Invalid)
    }
    return try {
        if (root.optString("format") != FormatName) return BackupParseResult.Rejected(BackupProblem.Invalid)
        val version = root.optInt("version", -1)
        if (version < 1) return BackupParseResult.Rejected(BackupProblem.Invalid)
        if (version > BackupFormatVersion) return BackupParseResult.Rejected(BackupProblem.TooNew)
        val data = root.getJSONObject("data")

        val routines = parseBackupRoutines(data.getJSONArray("routines"))
            ?: return BackupParseResult.Rejected(BackupProblem.Invalid)
        val dailyLog = if (data.isNull("dailyLog") || !data.has("dailyLog")) {
            null
        } else {
            parseBackupDailyLog(data.getJSONObject("dailyLog")) ?: return BackupParseResult.Rejected(BackupProblem.Invalid)
        }
        val settings = data.optJSONObject("settings") ?: JSONObject()
        val age = if (settings.isNull("age")) null else settings.optInt("age", -1).takeIf { it in AgeRange }
        if (!settings.isNull("age") && settings.has("age") && age == null) {
            return BackupParseResult.Rejected(BackupProblem.Invalid)
        }
        BackupParseResult.Valid(
            BackupContents(
                routines = routines,
                dailyLog = dailyLog,
                age = age,
                chronotype = enumOrNull<Chronotype>(settings.optString("chronotype")),
                fallAsleep = enumOrNull<FallAsleepTime>(settings.optString("fallAsleep")),
                bedtimeReminder = if (settings.has("bedtimeReminder")) settings.getBoolean("bedtimeReminder") else null,
                exportedAt = runCatching { Instant.parse(root.getString("exportedAt")) }.getOrNull(),
                // Nomes que esta versão não conhece são ignorados; os que faltam vão para o fim.
                feedOrder = settings.optJSONArray("feedOrder")?.let { array ->
                    normalizeFeedOrder(List(array.length()) { array.optString(it) }.mapNotNull { enumOrNull<FeedSection>(it) })
                },
            ),
        )
    } catch (e: JSONException) {
        BackupParseResult.Rejected(BackupProblem.Invalid)
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

/** null se alguma rotina estiver estragada. */
private fun parseBackupRoutines(array: JSONArray): List<Routine>? {
    val usedIds = HashSet<Long>()
    val parsed = ArrayList<Routine>(array.length())
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: return null
        val hour = o.optInt("wakeHour", -1)
        val minute = o.optInt("wakeMinute", -1)
        val mask = o.optInt("daysMask", 0)
        val name = o.optString("name", "")
        if (hour !in 0..23 || minute !in 0..59 || mask !in 0..Routine.AllDaysMask || name.isBlank()) return null
        if (!o.has("id")) return null
        parsed += Routine(
            id = o.getLong("id"),
            name = name.take(MaxBackupNameLength),
            wakeHour = hour,
            wakeMinute = minute,
            // Uma repetição que esta versão não conhece fica "Show just this once", como no DataStore.
            repeat = enumOrNull<RepeatOption>(o.optString("repeat")) ?: RepeatOption.JustOnce,
            daysMask = mask,
            active = o.optBoolean("active", true),
            anchorMillis = o.optLong("anchor", 0L),
        )
    }
    // Ids repetidos: os seguintes recebem um id novo, depois do maior.
    var nextId = (parsed.maxOfOrNull { it.id } ?: 0L) + 1L
    return parsed.map { r ->
        if (usedIds.add(r.id)) r else r.copy(id = nextId++).also { usedIds.add(it.id) }
    }
}

private const val MaxBackupNameLength = 200

private fun parseBackupDailyLog(o: JSONObject): DailyLog? {
    val date = try {
        LocalDate.parse(o.getString("date"))
    } catch (e: DateTimeParseException) {
        return null
    }
    val caffeine = o.optInt("caffeine", 0)
    val mood = o.optDouble("mood", 0.5)
    if (caffeine !in 0..CycloViewModel.MaxCaffeine || mood.isNaN() || mood !in 0.0..1.0) return null
    val factors = o.optJSONArray("factors")
    val factorSet = buildSet {
        if (factors != null) for (i in 0 until factors.length()) enumOrNull<SleepFactor>(factors.optString(i))?.let(::add)
    }
    return DailyLog(
        date = date,
        caffeine = caffeine,
        factors = factorSet,
        mood = mood.toFloat(),
        caffeineTimes = parseCaffeineTimes(o, caffeine),
        factorTimes = parseFactorTimes(o, factorSet),
    )
}

internal fun routinesToJson(routines: List<Routine>): JSONArray {
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
    return array
}

internal fun dailyLogToJson(log: DailyLog): JSONObject = JSONObject()
    .put("date", log.date.toString())
    .put("caffeine", log.caffeine)
    .put("factors", JSONArray(log.factors.map { it.name }))
    .put("mood", log.mood.toDouble())
    .put("caffeineTimes", JSONArray(log.caffeineTimes))
    .put("factorTimes", JSONObject().apply { log.factorTimes.forEach { (f, t) -> put(f.name, t) } })

/**
 * As horas dos cafés (opcionais: registos antigos não as têm). Só as positivas, por ordem, e no
 * máximo [caffeine] (as mais recentes).
 */
internal fun parseCaffeineTimes(o: JSONObject, caffeine: Int): List<Long> {
    val array = o.optJSONArray("caffeineTimes") ?: return emptyList()
    return List(array.length()) { array.optLong(it, 0L) }.filter { it > 0L }.sorted().takeLast(caffeine)
}

/** As horas dos fatores (opcionais). Só as dos fatores marcados e que esta versão conhece. */
internal fun parseFactorTimes(o: JSONObject, factors: Set<SleepFactor>): Map<SleepFactor, Long> {
    val times = o.optJSONObject("factorTimes") ?: return emptyMap()
    return buildMap {
        for (factor in factors) {
            val t = times.optLong(factor.name, 0L)
            if (t > 0L) put(factor, t)
        }
    }
}
