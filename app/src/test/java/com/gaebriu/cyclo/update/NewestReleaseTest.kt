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
