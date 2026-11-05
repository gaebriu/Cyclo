package com.gaebriu.cyclo.update

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * A escolha da atualização a partir da lista de releases do GitHub, com os dois canais (release-1.1).
 *
 * - **Canal:** uma release é beta se o GitHub a marcar como prerelease (`prerelease`), seja qual for
 *   a tag. As tags seguem `beta-x.x` (beta) e `release-x.x` (estável), mas é a marca que decide.
 * - **Âncora (o "mais recente"):** só conta uma release publicada DEPOIS da versão instalada
 *   (`published_at` comparado com o da tag instalada na mesma lista). O versionCode sobe a cada
 *   publicação e o GitHub não o mostra; a data é o que o representa. Assim a `beta-1.4.5` antiga
 *   (número maior do que 1.1, mas anterior) nunca aparece a quem tem a `release-1.1`. Sem a tag
 *   instalada na lista, a âncora é a release mais recente do mesmo canal com número ≤ ao instalado.
 * - **Números (sempre, além da data):** de uma estável instalada, qualquer candidata tem de ter um
 *   número maior. De uma beta instalada: uma beta tem de ter número maior (beta-2.5 → beta-2.6); uma
 *   estável tem de ter o primeiro número (major) igual ou maior (beta-2.5 → release-2.0 e release-3.0,
 *   mas não release-1.1.1).
 * - **Prioridade:** entre as elegíveis, a estável mais recente ganha SEMPRE a qualquer beta, seja
 *   qual for o número; só sem estável elegível é que conta a beta mais recente. Dentro de cada canal
 *   compara-se o número normalmente, sem depender da ordem da lista (salta versões intermédias).
 */
internal object ReleaseSelection {
    /** Uma release publicada (sem rascunhos), como vem da API. */
    data class Release(
        val tag: String,
        val version: AppVersion,
        val beta: Boolean,
        val published: Instant?,
        val pageUrl: String,
        val apkUrl: String?,
        val apkSize: Long,
    )

    /** A melhor estável e a melhor beta elegíveis (cada uma pode faltar). */
    data class Candidates(val stable: AvailableUpdate?, val beta: AvailableUpdate?) {
        /** A que se mostra: a estável primeiro; a beta só com as atualizações beta ligadas. */
        fun pick(betaEnabled: Boolean): AvailableUpdate? = stable ?: beta.takeIf { betaEnabled }
    }

    fun parse(json: String): List<Release> {
        val array = JSONArray(json)
        val out = ArrayList<Release>(array.length())
        for (i in 0 until array.length()) {
            val release = array.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            val version = AppVersion.parse(tag) ?: continue
            val assets = release.optJSONArray("assets") ?: JSONArray()
            // O .apk da release (com.gaebriu.cyclo.apk; havendo mais do que um, o que tem a tag no nome).
            var apk: JSONObject? = null
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (apk == null || name.contains(tag, ignoreCase = true)) apk = asset
            }
            out += Release(
                tag = tag,
                version = version,
                beta = release.optBoolean("prerelease"),
                published = release.optString("published_at").takeIf { it.isNotEmpty() && it != "null" }
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                pageUrl = release.optString("html_url"),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") },
                apkSize = apk?.optLong("size", 0L) ?: 0L,
            )
        }
        return out
    }

    /** As candidatas para a versão instalada [installedName] (o versionName = a tag). */
    fun select(releases: List<Release>, installedName: String?): Candidates {
        val installed = installedName?.let(AppVersion::parse) ?: return Candidates(null, null)
        val own = releases.firstOrNull { it.tag.equals(installedName.trim(), ignoreCase = true) }
        val installedBeta = own?.beta ?: installed.isPrerelease
        val anchor = own?.published
            ?: releases
                .filter { it.beta == installedBeta && compareNumbers(it.version.numbers, installed.numbers) <= 0 }
                .mapNotNull { it.published }
                .maxOrNull()

        fun eligible(r: Release): Boolean {
            if (anchor != null) {
                val published = r.published ?: return false
                if (!published.isAfter(anchor)) return false
            }
            val byNumber = compareNumbers(r.version.numbers, installed.numbers)
            return when {
                !installedBeta -> byNumber > 0
                r.beta -> byNumber > 0
                else -> r.version.numbers.firstOrNull().orZero() >= installed.numbers.firstOrNull().orZero()
            }
        }

        val newestFirst = compareBy<Release>({ it.version.numbers.paddedKey() }, { it.published ?: Instant.EPOCH })
        val pool = releases.filter(::eligible)
        return Candidates(
            stable = pool.filter { !it.beta }.maxWithOrNull(newestFirst)?.toUpdate(),
            beta = pool.filter { it.beta }.maxWithOrNull(newestFirst)?.toUpdate(),
        )
    }

    fun select(json: String, installedName: String?): Candidates = select(parse(json), installedName)

    /** Só os números (1.10 > 1.9; zeros no fim não contam: 1.0 = 1.0.0). */
    fun compareNumbers(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun Int?.orZero() = this ?: 0

    /** Chave comparável de uma lista de números (para o maxWith). */
    private fun List<Int>.paddedKey(): NumbersKey = NumbersKey(this)

    private class NumbersKey(val numbers: List<Int>) : Comparable<NumbersKey> {
        override fun compareTo(other: NumbersKey) = compareNumbers(numbers, other.numbers)
    }

    private fun Release.toUpdate() = AvailableUpdate(
        tag = tag,
        version = version,
        pageUrl = pageUrl,
        apkUrl = apkUrl,
        apkSize = apkSize,
        beta = beta,
    )
}
