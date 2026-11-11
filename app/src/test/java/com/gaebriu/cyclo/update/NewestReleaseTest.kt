package com.gaebriu.cyclo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A escolha da release a partir da resposta da API do GitHub (canais, datas e prioridade). */
class NewestReleaseTest {
    /**
     * Uma release como a API a devolve. [day] é o dia de publicação (a ordem real das publicações);
     * [beta] é a marca `prerelease` do GitHub.
     */
    private fun release(
        tag: String,
        day: Int,
        beta: Boolean,
        draft: Boolean = false,
        apk: Boolean = true,
    ) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": $beta,
         "published_at": ${if (draft) "null" else "\"2026-%02d-%02dT10:00:00Z\"".format(1 + day / 28, 1 + day % 28)},
         "html_url": "https://github.com/gaebriu/Cyclo/releases/tag/$tag",
         "assets": [${if (apk) """{"name": "com.gaebriu.cyclo.apk", "size": 1234, "browser_download_url": "https://github.com/gaebriu/Cyclo/releases/download/$tag/com.gaebriu.cyclo.apk"}""" else ""}]}
    """.trimIndent()

    private fun list(vararg items: String) = items.joinToString(prefix = "[", postfix = "]")

    private fun pick(json: String, installed: String, betaEnabled: Boolean) =
        ReleaseSelection.select(json, installed).pick(betaEnabled)

    /** O histórico real até à release-1.1 (as betas antigas são prereleases anteriores à 1.0). */
    private val history = arrayOf(
        release("beta-1.4.4", day = 1, beta = true),
        release("beta-1.4.5", day = 2, beta = true),
        release("release-1.0", day = 3, beta = false),
        release("release-1.1", day = 4, beta = false),
    )

    @Test
    fun noUpdateRightAfterReleaseOneOne() {
        val json = list(*history)
        assertNull(pick(json, "release-1.1", betaEnabled = false))
        // A beta-1.4.5 tem número maior do que 1.1, mas é anterior: nunca aparece.
        assertNull(pick(json, "release-1.1", betaEnabled = true))
    }

    @Test
    fun betaOffNeverShowsABeta() {
        val json = list(*history, release("beta-1.5", day = 5, beta = true))
        assertNull(pick(json, "release-1.1", betaEnabled = false))
        assertEquals("beta-1.5", pick(json, "release-1.1", betaEnabled = true)!!.tag)
        assertTrue(pick(json, "release-1.1", betaEnabled = true)!!.beta)
    }

    @Test
    fun newestBetaFirstInListStillFallsBackToStable() {
        // A release mais recente da lista é uma beta: sem beta, procura-se a estável.
        val json = list(release("beta-1.6", day = 7, beta = true), release("release-1.2", day = 6, beta = false), *history)
        assertEquals("release-1.2", pick(json, "release-1.1", betaEnabled = false)!!.tag)
    }

    @Test
    fun stableAlwaysBeatsBeta() {
        // Com a beta ligada, a estável elegível ganha à beta, mesmo com número menor e mais recente.
        val json = list(*history, release("release-1.2", day = 5, beta = false), release("beta-1.6", day = 6, beta = true))
        assertEquals("release-1.2", pick(json, "release-1.1", betaEnabled = true)!!.tag)
        // Instalada a release-1.2, a beta-1.6 (posterior) aparece.
        assertEquals("beta-1.6", pick(json, "release-1.2", betaEnabled = true)!!.tag)
        assertNull(pick(json, "release-1.2", betaEnabled = false))
    }

    /** A Beta 2.5 atualiza diretamente para a Estável 2.0 (publicada depois), com a beta ligada ou não. */
    @Test
    fun betaTwoFiveGetsStableTwoZero() {
        val json = list(
            release("beta-2.4", day = 10, beta = true),
            release("beta-2.5", day = 11, beta = true),
            release("release-2.0", day = 12, beta = false),
        )
        assertEquals("release-2.0", pick(json, "beta-2.5", betaEnabled = true)!!.tag)
        assertEquals("release-2.0", pick(json, "beta-2.5", betaEnabled = false)!!.tag)
    }

    @Test
    fun betaOnlyMovesUp() {
        val base = arrayOf(
            release("release-1.1", day = 4, beta = false),
            release("beta-2.5", day = 11, beta = true),
        )
        // Nenhuma estável publicada depois: nada (a release-1.1 é anterior).
        assertNull(pick(list(*base), "beta-2.5", betaEnabled = true))
        // Uma correção de uma linha anterior (major 1 < 2) não serve a quem tem a beta-2.5.
        assertNull(pick(list(*base, release("release-1.1.1", day = 12, beta = false)), "beta-2.5", betaEnabled = true))
        // A beta seguinte e a estável de uma linha seguinte, sim.
        assertEquals("beta-2.6", pick(list(*base, release("beta-2.6", day = 12, beta = true)), "beta-2.5", betaEnabled = true)!!.tag)
        assertEquals("release-3.0", pick(list(*base, release("release-3.0", day = 12, beta = false)), "beta-2.5", betaEnabled = true)!!.tag)
        // Uma beta com número menor, mesmo publicada depois, não.
        assertNull(pick(list(*base, release("beta-2.4.1", day = 12, beta = true)), "beta-2.5", betaEnabled = true))
    }

    /** Da Beta 1.5 direto para a mais recente (1.7), saltando a 1.6, seja qual for a ordem da lista. */
    @Test
    fun skipsIntermediateVersions() {
        val a = release("beta-1.5", day = 5, beta = true)
        val b = release("beta-1.6", day = 6, beta = true)
        val c = release("beta-1.7", day = 7, beta = true)
        for (json in listOf(list(c, b, a), list(b, a, c), list(a, c, b))) {
            val update = pick(json, "beta-1.5", betaEnabled = true)!!
            assertEquals("beta-1.7", update.tag)
            assertEquals("Beta 1.7", update.version.label())
            assertEquals("https://github.com/gaebriu/Cyclo/releases/download/beta-1.7/com.gaebriu.cyclo.apk", update.apkUrl)
        }
        val stables = list(
            release("release-1.3", day = 7, beta = false),
            release("release-1.1", day = 4, beta = false),
            release("release-1.2", day = 5, beta = false),
        )
        assertEquals("release-1.3", pick(stables, "release-1.1", betaEnabled = false)!!.tag)
    }

    /** O GitHub decide o canal (`prerelease`), não a tag. */
    @Test
    fun prereleaseFlagIsAuthoritative() {
        val json = list(*history, release("release-1.2", day = 5, beta = true))
        assertNull(pick(json, "release-1.1", betaEnabled = false))
        assertEquals("release-1.2", pick(json, "release-1.1", betaEnabled = true)!!.tag)
        val flaggedStable = list(*history, release("beta-1.5", day = 5, beta = false))
        assertEquals("beta-1.5", pick(flaggedStable, "release-1.1", betaEnabled = false)!!.tag)
        assertFalse(pick(flaggedStable, "release-1.1", betaEnabled = false)!!.beta)
    }

    @Test
    fun ignoresDraftsAndOlderOrEqual() {
        val json = list(*history, release("release-1.2", day = 5, beta = false, draft = true))
        assertNull(pick(json, "release-1.1", betaEnabled = true))
        // Publicada depois, mas com número menor (uma correção da 1.0): não é mais recente do que a 1.1.
        assertNull(pick(list(*history, release("release-1.0.1", day = 5, beta = false)), "release-1.1", betaEnabled = false))
    }

    /** Sem a tag instalada na lista, a âncora é a última do mesmo canal com número ≤ ao instalado. */
    @Test
    fun anchorFallbackWithoutTheInstalledTag() {
        val json = list(
            release("beta-1.4.5", day = 2, beta = true),
            release("release-1.0", day = 3, beta = false),
            release("beta-1.5", day = 5, beta = true),
        )
        // release-1.1 não está na lista: âncora = release-1.0 (dia 3); a beta-1.4.5 fica de fora.
        assertEquals("beta-1.5", pick(json, "release-1.1", betaEnabled = true)!!.tag)
    }

    @Test
    fun releaseOneZeroGetsReleaseOneOne() {
        assertEquals("release-1.1", pick(list(*history), "release-1.0", betaEnabled = false)!!.tag)
    }

    @Test
    fun releaseWithoutApkStillShowsThePage() {
        val update = pick(list(*history, release("release-1.2", day = 5, beta = false, apk = false)), "release-1.1", false)!!
        assertNull(update.apkUrl)
        assertEquals("https://github.com/gaebriu/Cyclo/releases/tag/release-1.2", update.pageUrl)
    }

    @Test
    fun prefersTheApkNamedAfterTheTag() {
        val json = """[{"tag_name": "release-1.2", "draft": false, "prerelease": false, "published_at": "2026-02-01T00:00:00Z", "html_url": "https://x",
            "assets": [{"name": "other.apk", "size": 1, "browser_download_url": "https://x/other.apk"},
                       {"name": "cyclo-release-1.2.apk", "size": 2, "browser_download_url": "https://x/cyclo-release-1.2.apk"}]},
            {"tag_name": "release-1.1", "draft": false, "prerelease": false, "published_at": "2026-01-01T00:00:00Z", "html_url": "https://y", "assets": []}]"""
        assertEquals(2L, pick(json, "release-1.1", betaEnabled = false)!!.apkSize)
    }

    /** A partir de uma beta 2.x, a release-2.0 (estável, major 2) é elegível. */
    @Test
    fun betaTwoPointFourGetsReleaseTwoPointZero() {
        val json = list(
            release("beta-2.4", day = 10, beta = true),
            release("release-2.0", day = 11, beta = false),
        )
        assertEquals("release-2.0", pick(json, "beta-2.4", betaEnabled = false)!!.tag)
        assertEquals("release-2.0", pick(json, "beta-2.4", betaEnabled = true)!!.tag)
    }

    /** O histórico real até à Cyclo 2.2 (a release-2.0 saiu depois da beta-2.4). */
    private val twoPointX = arrayOf(
        *history,
        release("beta-2.0", day = 10, beta = true),
        release("beta-2.4", day = 11, beta = true),
        release("release-2.0", day = 12, beta = false),
        release("release-2.1", day = 13, beta = false),
        release("release-2.2", day = 14, beta = false),
    )

    /**
     * Cyclo 2.2: a app pede sempre sem as betas ([CycloUpdates] usa pick(betaEnabled = false)). Uma
     * beta nova publicada depois da 2.2 não aparece; uma estável nova aparece.
     */
    @Test
    fun releaseTwoPointTwoOnlyGetsStables() {
        val withBeta = list(*twoPointX, release("beta-2.5", day = 15, beta = true))
        assertNull(pick(withBeta, "release-2.2", betaEnabled = false))
        // A seleção continua a reconhecer a beta (a lógica dos canais fica): só não se mostra.
        assertEquals("beta-2.5", ReleaseSelection.select(withBeta, "release-2.2").beta!!.tag)
        val withStable = list(*twoPointX, release("beta-2.5", day = 15, beta = true), release("release-2.3", day = 16, beta = false))
        assertEquals("release-2.3", pick(withStable, "release-2.2", betaEnabled = false)!!.tag)
        assertFalse(pick(withStable, "release-2.2", betaEnabled = false)!!.beta)
    }

    /** Antes de atualizar (release-2.1, com ou sem as betas), a release-2.2 aparece como estável. */
    @Test
    fun releaseTwoPointOneGetsReleaseTwoPointTwo() {
        val json = list(*twoPointX)
        assertEquals("release-2.2", pick(json, "release-2.1", betaEnabled = false)!!.tag)
        assertEquals("release-2.2", pick(json, "release-2.1", betaEnabled = true)!!.tag)
        assertEquals("release-2.2", pick(json, "beta-2.4", betaEnabled = true)!!.tag)
    }

    /** Uma beta instalada à mão pelo GitHub (depois da 2.2) só recebe estáveis, e nunca betas. */
    @Test
    fun manuallyInstalledBetaOnlyGetsStables() {
        val json = list(*twoPointX, release("beta-2.5", day = 15, beta = true), release("beta-2.6", day = 16, beta = true))
        assertNull(pick(json, "beta-2.5", betaEnabled = false))
        val withStable = list(*twoPointX, release("beta-2.5", day = 15, beta = true), release("release-2.3", day = 16, beta = false))
        assertEquals("release-2.3", pick(withStable, "beta-2.5", betaEnabled = false)!!.tag)
    }
}
