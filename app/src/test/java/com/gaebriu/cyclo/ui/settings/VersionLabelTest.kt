package com.gaebriu.cyclo.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** O texto da versão nas definições: "Cyclo " + isto. */
class VersionLabelTest {
    @Test
    fun alpha() = assertEquals("Alpha 0.7.3", versionLabel("alpha-0.7.3"))

    @Test
    fun beta() = assertEquals("Beta 1.0", versionLabel("beta-1.0.0"))

    @Test
    fun betaPatch() = assertEquals("Beta 1.0.1", versionLabel("beta-1.0.1"))

    @Test
    fun release() = assertEquals("1.0", versionLabel("1.0.0"))
}
