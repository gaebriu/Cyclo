package com.gaebriu.cyclo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A escolha da release a partir da resposta da API do GitHub. */
class NewestReleaseTest {
    private val installed = AppVersion.parse("alpha-0.7.3")

    private fun release(tag: String, draft: Boolean = false, apk: Boolean = true) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": true,
         "html_url": "https://github.com/gaebriu/Cyclo/releases/tag/$tag",
         "assets": [${if (apk) """{"name": "cyclo-$tag.apk", "size": 1234, "browser_download_url": "https://github.com/gaebriu/Cyclo/releases/download/$tag/cyclo-$tag.apk"}""" else ""}]}
    """.trimIndent()

    /** A beta-1.1 publicada como no GitHub: tag de duas partes e o .apk com.gaebriu.cyclo.apk. */
    @Test
    fun betaOneZeroSeesBetaOneOne() {
        fun real(tag: String) = """
            {"tag_name": "$tag", "draft": false, "prerelease": true, "html_url": "https://github.com/gaebriu/Cyclo/releases/tag/$tag",
             "assets": [{"name": "com.gaebriu.cyclo.apk", "size": 99, "browser_download_url": "https://github.com/gaebriu/Cyclo/releases/download/$tag/com.gaebriu.cyclo.apk"}]}
        """.trimIndent()
        val update = CycloUpdates.newestRelease("[${real("beta-1.1")}, ${real("beta-1.0")}]", AppVersion.parse("beta-1.0.0"))!!
        assertEquals("beta-1.1", update.tag)
        assertEquals("https://github.com/gaebriu/Cyclo/releases/download/beta-1.1/com.gaebriu.cyclo.apk", update.apkUrl)
        // Já com a beta-1.1 instalada, não há nada mais recente.
        assertNull(CycloUpdates.newestRelease("[${real("beta-1.1")}, ${real("beta-1.0")}]", AppVersion.parse("beta-1.1")))
    }

    /** Da Beta 1.3 direto para a mais recente (1.5), saltando a 1.4, seja qual for a ordem da lista. */
    @Test
    fun skipsIntermediateVersions() {
        fun real(tag: String) = """
            {"tag_name": "$tag", "draft": false, "prerelease": true, "html_url": "https://github.com/gaebriu/Cyclo/releases/tag/$tag",
             "assets": [{"name": "com.gaebriu.cyclo.apk", "size": 99, "browser_download_url": "https://github.com/gaebriu/Cyclo/releases/download/$tag/com.gaebriu.cyclo.apk"}]}
        """.trimIndent()
        val beta13 = AppVersion.parse("beta-1.3")
        for (json in listOf(
            "[${real("beta-1.5")}, ${real("beta-1.4")}, ${real("beta-1.3")}]",
            "[${real("beta-1.4")}, ${real("beta-1.3")}, ${real("beta-1.5")}]",
        )) {
            val update = CycloUpdates.newestRelease(json, beta13)!!
            assertEquals("beta-1.5", update.tag)
            assertEquals("Beta 1.5", update.version.label())
            assertEquals("https://github.com/gaebriu/Cyclo/releases/download/beta-1.5/com.gaebriu.cyclo.apk", update.apkUrl)
        }
        assertNull(CycloUpdates.newestRelease("[${real("beta-1.5")}, ${real("beta-1.4")}]", AppVersion.parse("beta-1.5")))
    }

    @Test
    fun picksTheNewestNewerRelease() {
        val json = "[${release("alpha-0.7.4")}, ${release("alpha-0.8.0")}, ${release("alpha-0.7.2")}]"
        val update = CycloUpdates.newestRelease(json, installed)!!
        assertEquals("alpha-0.8.0", update.tag)
        assertEquals(1234L, update.apkSize)
    }

    @Test
    fun ignoresDraftsAndOlderOrEqual() {
        val json = "[${release("alpha-0.9.0", draft = true)}, ${release("alpha-0.7.3")}, ${release("alpha-0.7.1")}]"
        assertNull(CycloUpdates.newestRelease(json, installed))
    }

    /**
     * A ponte para a 1.0: a beta-1.4.5 vê a release-1.0 como mais recente, apesar de 1.0 < 1.4.5;
     * a release-1.0 instalada ignora as betas e recebe a release-1.1.
     */
    @Test
    fun stableSeriesIsNewerThanEveryBeta() {
        val all = "[${release("release-1.0")}, ${release("beta-1.4.5")}, ${release("beta-1.4.4")}]"
        assertEquals("release-1.0", CycloUpdates.newestRelease(all, AppVersion.parse("beta-1.4.5"))!!.tag)
        assertEquals("release-1.0", CycloUpdates.newestRelease(all, AppVersion.parse("beta-1.4.4"))!!.tag)
        assertNull(CycloUpdates.newestRelease(all, AppVersion.parse("release-1.0")))
        val later = "[${release("release-1.1-beta.1")}, ${release("beta-1.4.6")}, ${release("release-1.0")}]"
        assertNull(CycloUpdates.newestRelease(later, AppVersion.parse("release-1.0")))
        assertEquals("release-1.1", CycloUpdates.newestRelease("[${release("release-1.1")}, ${release("release-1.0")}]", AppVersion.parse("release-1.0"))!!.tag)
    }

    @Test
    fun betaGetsTheNextBetaAndTheFinal() {
        val beta = AppVersion.parse("beta-1.0.0")
        assertEquals("beta-1.0.1", CycloUpdates.newestRelease("[${release("beta-1.0.1")}, ${release("alpha-0.7.3")}]", beta)!!.tag)
        // Os números primeiro: beta-1.0.1 é mais recente do que 1.0.0; a final 1.0.1 ganha à beta-1.0.1.
        assertEquals("1.0.0", CycloUpdates.newestRelease("[${release("1.0.0")}]", beta)!!.tag)
        assertEquals("1.0.1", CycloUpdates.newestRelease("[${release("beta-1.0.1")}, ${release("1.0.1")}]", beta)!!.tag)
    }

    @Test
    fun finalIgnoresPrereleases() {
        val final = AppVersion.parse("1.0.0")
        assertNull(CycloUpdates.newestRelease("[${release("beta-1.1.0")}]", final))
        assertEquals("1.0.1", CycloUpdates.newestRelease("[${release("beta-1.1.0")}, ${release("1.0.1")}]", final)!!.tag)
    }

    @Test
    fun prefersTheApkNamedAfterTheTag() {
        val json = """[{"tag_name": "beta-1.0.1", "draft": false, "html_url": "https://x",
            "assets": [{"name": "other.apk", "size": 1, "browser_download_url": "https://x/other.apk"},
                       {"name": "cyclo-beta-1.0.1.apk", "size": 2, "browser_download_url": "https://x/cyclo-beta-1.0.1.apk"}]}]"""
        assertEquals(2L, CycloUpdates.newestRelease(json, AppVersion.parse("beta-1.0.0"))!!.apkSize)
    }

    @Test
    fun releaseWithoutApkStillShowsThePage() {
        val update = CycloUpdates.newestRelease("[${release("alpha-0.7.4", apk = false)}]", installed)!!
        assertNull(update.apkUrl)
        assertEquals("https://github.com/gaebriu/Cyclo/releases/tag/alpha-0.7.4", update.pageUrl)
    }
}
