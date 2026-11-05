package com.gaebriu.cyclo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A comparação das versões (tags do GitHub contra o versionName instalado). */
class AppVersionTest {
    private fun v(text: String) = AppVersion.parse(text)!!

    @Test
    fun parsesAlphaTags() {
        assertEquals(AppVersion(listOf(0, 7, 3), AppVersion.Stage.Alpha), v("alpha-0.7.3"))
        assertEquals("Alpha 0.7.3", v("alpha-0.7.3").label())
    }

    @Test
    fun numbersCompareNumerically() {
        assertTrue(v("alpha-0.7.10") > v("alpha-0.7.9"))
        assertTrue(v("alpha-0.8.0") > v("alpha-0.7.3"))
        assertTrue(v("alpha-1.0.0") > v("alpha-0.99.99"))
    }

    @Test
    fun sameNumbersOrderByStage() {
        assertTrue(v("beta-1.0.0") > v("alpha-1.0.0"))
        assertTrue(v("rc-1.0.0") > v("beta-1.0.0"))
        assertTrue(v("1.0.0") > v("rc-1.0.0"))
        assertTrue(v("v1.0.0-beta.2") > v("v1.0.0-beta.1"))
    }

    @Test
    fun labels() {
        assertEquals("Beta 1.0", v("beta-1.0.0").label())
        assertEquals("1.0", v("1.0.0").label())
        assertEquals("Beta 1.0.2", v("beta-1.0.2").label())
        assertTrue(v("beta-1.0.0") > v("alpha-0.7.3"))
    }

    /** beta-1.4.1: patch depois de beta-1.4 (o atualizador tem de a ver como mais recente). */
    @Test
    fun patchAfterTwoPartTag() {
        assertTrue(v("beta-1.4.1") > v("beta-1.4"))
        assertTrue(v("beta-1.4.1") > v("beta-1.3"))
        assertEquals("Beta 1.4.1", v("beta-1.4.1").label())
        assertTrue(v("beta-1.4.2") > v("beta-1.4.1"))
        assertEquals("Beta 1.4.2", v("beta-1.4.2").label())
        assertTrue(v("beta-1.4.4") > v("beta-1.4.3"))
        assertEquals("Beta 1.4.4", v("beta-1.4.4").label())
    }

    @Test
    fun newerAlphaBeatsOlderFinal() = assertTrue(v("alpha-0.8.0") > v("0.7.0"))

    @Test
    fun equalVersionsAreNotNewer() = assertEquals(0, v("alpha-0.7.3").compareTo(v("Alpha-0.7.3")))

    @Test
    fun rejectsNonVersions() = assertNull(AppVersion.parse("docs"))

    /** As tags de duas partes (beta-1.0, beta-1.1): o mesmo que com o zero do fim. */
    @Test
    fun twoPartTags() {
        assertEquals("Beta 1.1", v("beta-1.1").label())
        assertEquals("Beta 1.2", v("beta-1.2").label())
        assertTrue(v("beta-1.2") > v("beta-1.1"))
        assertEquals("Beta 1.0", v("beta-1.0").label())
        assertTrue(v("beta-1.1") > v("beta-1.0.0"))
        // O .apk da beta-1.0 (versionName beta-1.0.0) continua a ser a versão da tag beta-1.0.
        assertEquals(0, v("beta-1.0.0").compareTo(v("beta-1.0")))
        assertEquals(0, v("beta-1.1").compareTo(v("beta-1.1.0")))
    }
}
