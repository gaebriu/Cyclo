package com.gaebriu.cyclo.update

/**
 * Uma versão do Cyclo, como as tags do GitHub e o versionName: "alpha-0.7.3", "beta-1.0.0",
 * "rc-1.0.0" ou "1.0.0" (também aceita um "v" no início e "-alpha.2" à moda do semver).
 *
 * A ordem: primeiro a série, depois os números (0.7.3 < 0.7.10 < 1.0.0) e, com os mesmos números,
 * a fase (alpha < beta < rc < final). Uma alpha nova (alpha-0.8.0) é mais recente do que uma final
 * antiga (0.7.0), como no histórico de tags do projeto.
 *
 * Séries: as versões estáveis chamam-se "release-1.0", "release-1.1"… (série 1) e são sempre mais
 * recentes do que as da série 0 (tudo o que veio antes: alpha-0.x, beta-1.0 … beta-1.4.5), cujos
 * números já passavam de 1.0. Sem isto a release-1.0 seria "mais antiga" do que a beta-1.4.4.
 * Uma prerelease depois da 1.0 também leva o prefixo ("release-1.1-beta.1").
 */
data class AppVersion(
    val numbers: List<Int>,
    val stage: Stage,
    val stageNumber: Int = 0,
    val series: Int = 0,
) : Comparable<AppVersion> {
    enum class Stage { Alpha, Beta, ReleaseCandidate, Final }

    val isPrerelease: Boolean get() = stage != Stage.Final

    override fun compareTo(other: AppVersion): Int {
        if (series != other.series) return series.compareTo(other.series)
        val size = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until size) {
            val a = numbers.getOrElse(i) { 0 }
            val b = other.numbers.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        if (stage != other.stage) return stage.compareTo(other.stage)
        return stageNumber.compareTo(other.stageNumber)
    }

    /**
     * Como se mostra ao utilizador: "Alpha 0.7.3", "Beta 1.0" (beta-1.0.0), "1.0" (1.0.0),
     * "Beta 1.0.1". Os zeros do fim caem, mas fica sempre "maior.menor".
     */
    fun label(): String {
        var shown = numbers
        while (shown.size > 2 && shown.last() == 0) shown = shown.dropLast(1)
        val number = shown.joinToString(".")
        return when (stage) {
            Stage.Alpha -> "Alpha $number"
            Stage.Beta -> "Beta $number"
            Stage.ReleaseCandidate -> "RC $number"
            Stage.Final -> number
        }
    }

    companion object {
        private val Numbers = Regex("""(\d+(?:\.\d+)*)""")

        /** null se [text] não for uma versão (ex.: uma tag "docs"). */
        fun parse(text: String): AppVersion? {
            val trimmed = text.trim().removePrefix("v").removePrefix("V").lowercase()
            val series = if (trimmed.startsWith(StablePrefix)) 1 else 0
            val clean = trimmed.removePrefix(StablePrefix)
            val match = Numbers.find(clean) ?: return null
            val numbers = match.value.split('.').map { it.toIntOrNull() ?: return null }
            val rest = (clean.substring(0, match.range.first) + " " + clean.substring(match.range.last + 1))
            val stage = when {
                "alpha" in rest -> Stage.Alpha
                "beta" in rest -> Stage.Beta
                "rc" in rest -> Stage.ReleaseCandidate
                else -> Stage.Final
            }
            // "1.0.0-beta.2": o número da fase.
            val stageNumber = Regex("""(?:alpha|beta|rc)[.\-]?(\d+)""").find(clean.substring(match.range.last + 1))
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return AppVersion(numbers, stage, stageNumber, series)
        }

        /** O prefixo das versões estáveis ("release-1.0"), a série 1. */
        private const val StablePrefix = "release-"
    }
}
