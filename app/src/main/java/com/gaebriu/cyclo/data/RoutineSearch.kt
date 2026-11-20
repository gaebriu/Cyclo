package com.gaebriu.cyclo.data

import com.gaebriu.cyclo.ui.routine.RepeatOption
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A pesquisa das rotinas guardadas (Cyclo 3.2.2). Tudo no aparelho, sem rede nem bibliotecas: só o
 * [Normalizer] do Java e contas sobre as rotinas.
 *
 * - Texto: sem maiúsculas, acentos, pontuação nem espaços a mais ([foldText]); "burgerking",
 *   "burger-king" e "BURGER KING" são o mesmo, "cafe" encontra "Café".
 * - Nome: igual, sem espaços, começo, começo de palavras, iniciais ("bk" → "Burger King"), parte do
 *   nome e, em palavras de 4+ letras, uma gralha (duas a partir de 8).
 * - Atributos, em português e inglês, com "não"/"not" a negar o seguinte: dias ("segunda", "mon",
 *   "fim de semana", "weekdays", "todos os dias"), quando aparece ("uma vez", "dias escolhidos", "hoje",
 *   "amanhã", "visíveis", "escondidas"), a hora de acordar ("7h", "7:30", "7 pm", "antes das 8",
 *   "after 6"), a parte do dia ("de manhã", "afternoon", "noite": as mesmas faixas da saudação do
 *   Feed) e o estado ("ligadas", "desativadas", "enabled", "off").
 *
 * Ordem: o nome igual primeiro, depois começos, iniciais, partes e gralhas, e por fim as que só
 * batem nos atributos; em empate, a ordem das rotinas guardadas. Um nome igual (ou igual sem espaços)
 * esconde as que só batem nos atributos ("Morning Routine" não traz todas as rotinas da manhã).
 *
 * O trabalho por rotina (o nome normalizado, as palavras, as iniciais) faz-se uma vez por lista de
 * rotinas; cada tecla só normaliza a pesquisa e percorre as rotinas uma vez.
 */
class RoutineSearch(private val routines: List<Routine>) {
    private class Entry(val routine: Routine, val index: Int) {
        val name = foldText(routine.name)
        val words = name.split(' ').filter { it.isNotEmpty() }
        val compact = name.replace(" ", "")
        val initials = words.joinToString("") { it.take(1) }

        /** Onde começa cada palavra em [compact]. */
        private val wordStarts = words.runningFold(0) { at, w -> at + w.length }.dropLast(1)

        /**
         * [part] dentro de uma palavra, ou a atravessar palavras a partir do começo de uma ("rking" não;
         * "gerking" dentro de "burgerking" também não; "kingb" não; "burgerk" sim). Assim "amanha" não
         * encontra "da manhã".
         */
        fun contains(part: String): Boolean =
            words.any { it.contains(part) } || wordStarts.any { compact.startsWith(part, it) }
    }

    private val entries = routines.mapIndexed { i, r -> Entry(r, i) }

    /** As rotinas que respondem a [query], da mais à menos relevante (vazia = todas, pela ordem). */
    fun find(query: String, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): List<Routine> {
        val q = foldText(query)
        if (q.isEmpty()) return routines
        val tokens = q.split(' ')
        val compact = q.replace(" ", "")
        val filters = parseFilters(tokens)
        val scored = entries.mapNotNull { e ->
            val byName = nameScore(e, q, compact, tokens)
            val byAttributes = filters?.let { f ->
                if (!f.conditions.all { it.test(e.routine, now, zone) }) 0
                else if (f.rest.isEmpty()) AttributeScore
                else if (f.rest.all { wordMatches(e, it) }) AttributeAndNameScore
                else 0
            } ?: 0
            maxOf(byName, byAttributes).takeIf { it > 0 }?.let { e to it }
        }
        val exact = scored.any { it.second >= CompactScore }
        return scored
            .filter { !exact || it.second > AttributeAndNameScore }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.index })
            .map { it.first.routine }
    }

    private fun nameScore(e: Entry, q: String, compact: String, tokens: List<String>): Int = when {
        e.name == q -> 100
        e.compact == compact -> CompactScore
        e.name.startsWith(q) || e.compact.startsWith(compact) -> 85
        tokens.all { t -> e.words.any { it.startsWith(t) } } -> 75
        compact.length >= 2 && e.words.size >= 2 && e.initials.startsWith(compact) -> 70
        compact.length >= 2 && e.contains(compact) -> 60
        tokens.all { wordMatches(e, it) } -> 40
        else -> 0
    }

    /**
     * Uma palavra da pesquisa no nome: começo de palavra, parte do nome ou uma gralha. As palavras dos
     * atributos ("amanhã", "segunda") não contam como gralhas de um nome ("manhã").
     */
    private fun wordMatches(e: Entry, token: String): Boolean =
        e.words.any { it.startsWith(token) } ||
            (token.length >= 2 && e.contains(token)) ||
            (token.length >= 4 && token !in Keywords && e.words.any { closeEnough(it, token) })

    /** Os filtros da pesquisa e as palavras que sobram (para o nome); null = nenhum atributo nem palavra comum. */
    private class Filters(val conditions: List<Condition>, val rest: List<String>)

    private fun interface Condition {
        fun test(r: Routine, now: LocalDateTime, zone: ZoneId): Boolean
    }

    private fun parseFilters(tokens: List<String>): Filters? {
        val conditions = ArrayList<Condition>()
        val rest = ArrayList<String>()
        var negate = false
        var compare = 0 // -1 antes de, 1 depois de, 0 a essa hora
        var recognized = false
        var i = 0
        fun add(c: Condition) {
            val n = negate
            conditions += if (n) Condition { r, now, z -> !c.test(r, now, z) } else c
            negate = false
            recognized = true
        }
        while (i < tokens.size) {
            // As expressões de várias palavras primeiro ("fim de semana", "todos os dias"...).
            val phrase = Phrases.firstOrNull { (words, _) -> tokens.startsWith(words, i) }
            if (phrase != null) {
                add(phrase.second)
                i += phrase.first.size
                continue
            }
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            val time = parseTime(t, next)
            when {
                t in Negations -> { negate = true; recognized = true }
                t in Before -> { compare = -1; recognized = true }
                t in After -> { compare = 1; recognized = true }
                // "on" só é o estado no fim ("routines on Monday", "on the weekend": é a preposição).
                t == "on" && next != null -> recognized = true
                time != null -> {
                    val (minutes, used, exactMinute) = time
                    val c = compare
                    add { r, _, _ ->
                        val wake = r.wakeHour * 60 + r.wakeMinute
                        when {
                            c < 0 -> wake < minutes
                            c > 0 -> wake > minutes
                            exactMinute -> wake == minutes
                            else -> r.wakeHour == minutes / 60
                        }
                    }
                    compare = 0
                    i += used
                    continue
                }
                Keywords[t] != null -> add(Keywords.getValue(t))
                t in StopWords -> recognized = true
                else -> rest += t
            }
            i++
        }
        if (!recognized) return null
        // Sem nenhum atributo, só "rotinas"/"routines" (e ligações) quer dizer todas; "a" ou "de" sozinhos não.
        if (conditions.isEmpty()) return if (rest.isEmpty() && tokens.any { it in RoutineWords }) Filters(conditions, rest) else null
        return Filters(conditions, rest)
    }

    private companion object {
        const val CompactScore = 95
        const val AttributeAndNameScore = 50
        const val AttributeScore = 30

        fun days(mask: Int) = Condition { r, now, z -> r.dayMask(now, z) and mask != 0 }
        fun hours(range: IntRange) = Condition { r, _, _ -> r.wakeHour in range }
        fun repeat(option: RepeatOption) = Condition { r, _, _ -> r.repeat == option }
        val Enabled = Condition { r, _, _ -> r.active }
        val Disabled = Condition { r, _, _ -> !r.active }
        val Visible = Condition { r, now, z -> r.nextWake(now, z) != null }
        val Today = Condition { r, now, z -> r.wakeOn(now.toLocalDate(), z) != null }
        val Tomorrow = Condition { r, now, z -> r.wakeOn(now.toLocalDate().plusDays(1), z) != null }

        // As faixas da saudação do Feed (FeedScreen.Greeting): manhã 5–11, tarde 12–17, noite 18–4.
        val Morning = hours(5..11)
        val Afternoon = hours(12..17)
        val Evening = Condition { r, _, _ -> r.wakeHour >= 18 || r.wakeHour < 5 }
        val Dawn = hours(0..4)

        val Keywords: Map<String, Condition> = buildMap {
            fun put(c: Condition, vararg words: String) = words.forEach { put(it, c) }
            put(days(1 shl 0), "sunday", "sundays", "sun", "domingo", "domingos", "dom")
            put(days(1 shl 1), "monday", "mondays", "mon", "segunda", "segundas", "seg")
            put(days(1 shl 2), "tuesday", "tuesdays", "tue", "tues", "terca", "tercas", "ter")
            put(days(1 shl 3), "wednesday", "wednesdays", "wed", "quarta", "quartas", "qua")
            put(days(1 shl 4), "thursday", "thursdays", "thu", "thur", "thurs", "quinta", "quintas", "qui")
            put(days(1 shl 5), "friday", "fridays", "fri", "sexta", "sextas", "sex")
            put(days(1 shl 6), "saturday", "saturdays", "sat", "sabado", "sabados", "sab")
            put(days(Routine.WeekDaysMask), "weekday", "weekdays", "workday", "workdays", "uteis", "util", "semana")
            put(days(Routine.WeekendMask), "weekend", "weekends", "fds")
            put(repeat(RepeatOption.Everyday), "everyday", "daily", "diariamente", "diaria", "diarias", "diario", "diarios")
            put(repeat(RepeatOption.JustOnce), "once")
            put(repeat(RepeatOption.SelectedDays), "custom", "personalizada", "personalizadas")
            put(Enabled, "enabled", "active", "on", "ativa", "ativas", "ativo", "ativos", "ativada", "ativadas", "ligada", "ligadas", "ligado", "ligados")
            put(Disabled, "disabled", "inactive", "off", "paused", "desativada", "desativadas", "desativado", "desativados", "desligada", "desligadas", "desligado", "desligados", "inativa", "inativas", "pausada", "pausadas")
            put(Visible, "visible", "visivel", "visiveis", "showing")
            put(Condition { r, now, z -> r.nextWake(now, z) == null }, "hidden", "escondida", "escondidas", "oculta", "ocultas", "invisivel", "invisiveis")
            put(Today, "today", "hoje", "now", "agora")
            put(Tomorrow, "tomorrow", "amanha")
            put(Morning, "morning", "mornings", "manha", "manhas")
            put(Afternoon, "afternoon", "afternoons", "tarde", "tardes")
            put(Evening, "evening", "evenings", "night", "nights", "tonight", "noite", "noites")
            put(Dawn, "dawn", "madrugada", "madrugadas")
        }

        val Phrases: List<Pair<List<String>, Condition>> = listOf(
            "fim de semana" to days(Routine.WeekendMask),
            "fins de semana" to days(Routine.WeekendMask),
            "dias de semana" to days(Routine.WeekDaysMask),
            "dias uteis" to days(Routine.WeekDaysMask),
            "durante a semana" to days(Routine.WeekDaysMask),
            "during the week" to days(Routine.WeekDaysMask),
            "every day" to repeat(RepeatOption.Everyday),
            "all days" to repeat(RepeatOption.Everyday),
            "todos os dias" to repeat(RepeatOption.Everyday),
            "todo o dia" to repeat(RepeatOption.Everyday),
            "todos dias" to repeat(RepeatOption.Everyday),
            "just once" to repeat(RepeatOption.JustOnce),
            "only once" to repeat(RepeatOption.JustOnce),
            "one time" to repeat(RepeatOption.JustOnce),
            "so uma vez" to repeat(RepeatOption.JustOnce),
            "uma vez" to repeat(RepeatOption.JustOnce),
            "selected days" to repeat(RepeatOption.SelectedDays),
            "dias escolhidos" to repeat(RepeatOption.SelectedDays),
            "dias selecionados" to repeat(RepeatOption.SelectedDays),
            "early morning" to Dawn,
        ).map { (text, c) -> text.split(' ') to c }

        // Sem o "no" (em português é "no domingo"); "don't" chega aqui como "don t".
        val Negations = setOf("not", "dont", "don", "doesnt", "doesn", "isn", "aren", "never", "except", "without", "excluding", "nao", "nunca", "sem", "exceto", "menos")
        val RoutineWords = setOf("routine", "routines", "rotina", "rotinas", "alarm", "alarms", "alarme", "alarmes")
        val Before = setOf("before", "until", "antes", "ate")
        val After = setOf("after", "from", "since", "depois", "desde", "apos")

        /** Palavras que só ligam a frase ("rotinas que aparecem de manhã", "routines that show on Monday"). */
        val StopWords = setOf(
            "routine", "routines", "rotina", "rotinas", "alarm", "alarms", "alarme", "alarmes",
            "that", "which", "what", "the", "my", "a", "an", "in", "at", "by", "of", "for", "with", "and", "or", "to", "is", "are",
            "on", "during", "every", "each", "day", "days", "time", "clock", "oclock", "wake", "wakes", "up",
            "show", "shows", "shown", "appear", "appears", "t", "do", "does",
            "de", "da", "do", "das", "dos", "que", "quais", "qual", "o", "os", "as", "ao", "aos", "e", "em", "no", "na", "nos", "nas",
            "minha", "minhas", "meu", "meus", "com", "para", "por", "pela", "pelas", "cada", "todas", "todos", "toda", "todo",
            "dia", "dias", "feira", "feiras", "hora", "horas", "aparece", "aparecem", "aparecer", "mostra", "mostram",
            "acorda", "acordam", "acordar", "esta", "estao", "sao", "e",
        )

        /**
         * Uma hora na pesquisa: "7", "07", "7h", "7h30", "19h", "7am", "7 pm", "7 30" (o "7:30" fica assim
         * depois de [foldText]). Devolve os minutos do dia, quantas palavras gastou e se trazia minutos.
         */
        fun parseTime(t: String, next: String?): Triple<Int, Int, Boolean>? {
            val m = TimeToken.matchEntire(t) ?: return null
            var hour = m.groupValues[1].toInt()
            var minute = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            var suffix = m.groupValues[4]
            var used = 1
            if (minute == null && suffix.isEmpty() && next != null) {
                if (next.length == 2 && next.all { it.isDigit() } && m.groupValues[2].isEmpty()) {
                    minute = next.toInt()
                    used = 2
                } else if (next == "am" || next == "pm") {
                    suffix = next
                    used = 2
                }
            }
            if (suffix == "pm" && hour in 1..11) hour += 12
            if (suffix == "am" && hour == 12) hour = 0
            if (hour !in 0..23 || (minute != null && minute !in 0..59)) return null
            return Triple(hour * 60 + (minute ?: 0), used, minute != null)
        }

        val TimeToken = Regex("""(\d{1,2})(h)?(\d{2})?(am|pm|hrs|hr)?""")

        fun List<String>.startsWith(words: List<String>, at: Int): Boolean =
            at + words.size <= size && words.indices.all { this[at + it] == words[it] }
    }
}

/** Os dias em que a rotina aparece (bit 0 = domingo); "Show just this once" é só o dia da hora dela. */
private fun Routine.dayMask(now: LocalDateTime, zone: ZoneId): Int {
    if (repeat != RepeatOption.JustOnce) return repeatMask
    val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchorMillis), zone)
    var wake = anchor.toLocalDate().atTime(wakeHour, wakeMinute)
    if (!wake.isAfter(anchor)) wake = wake.plusDays(1)
    return wake.toLocalDate().weekdayBit()
}

/**
 * O texto como a pesquisa o compara: minúsculas, sem acentos (NFD sem as marcas), e tudo o que não é
 * letra nem algarismo vira um só espaço ("Burger-King!" → "burger king"; "Café" → "cafe").
 */
fun foldText(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    var pendingSpace = false
    for (c in decomposed) {
        when {
            Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
            c.isLetterOrDigit() -> {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(c.lowercaseChar())
            }
            else -> pendingSpace = true
        }
    }
    return out.toString()
}

/**
 * Até [max] diferenças (trocar, juntar, tirar uma letra ou trocar duas vizinhas) entre [a] e [b];
 * para as gralhas da pesquisa. Compara com o começo da palavra do nome do mesmo tamanho ou maior.
 */
private fun closeEnough(word: String, token: String): Boolean {
    val max = if (token.length >= 8) 2 else 1
    // A palavra pode continuar ("burgr" ~ "burger", "burgr" ~ "burgers").
    for (len in maxOf(1, token.length - max)..minOf(word.length, token.length + max)) {
        if (distance(word.substring(0, len), token, max) <= max) return true
    }
    return false
}

/** Distância de Damerau (alinhamento ótimo), parando cedo quando passa de [max]. */
private fun distance(a: String, b: String, max: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    var prev2 = IntArray(b.length + 1)
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        var rowMin = cur[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
            cur[j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > max) return max + 1
        val t = prev2; prev2 = prev; prev = cur; cur = t
    }
    return prev[b.length]
}
