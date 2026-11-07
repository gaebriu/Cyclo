package com.gaebriu.cyclo.ui.main

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** As faixas horárias do brilho do topo (Beta 2.1): cada uma de [início, fim[. */
class SkyColorsTest {
    private val night = SkyColors(Color(0xFF07060F), Color(0xFF1D1A4F))
    private val dawn = SkyColors(Color(0xFF1A1342), Color(0xFFB67FA6))
    private val morning = SkyColors(Color(0xFF1E2468), Color(0xFF7C9AE6))
    private val midday = SkyColors(Color(0xFF2A3290), Color(0xFF5FA2DB))
    private val afternoon = SkyColors(Color(0xFF2C2470), Color(0xFFDE8A7E))
    private val dusk = SkyColors(Color(0xFF251A62), Color(0xFFA24FA0))
    private val evening = SkyColors(Color(0xFF160F3E), Color(0xFF4A3C99))

    @Test
    fun bandEdges() {
        assertEquals(night, skyColorsAt(0, 0))
        assertEquals(night, skyColorsAt(4, 59))
        assertEquals(dawn, skyColorsAt(5, 0))
        assertEquals(dawn, skyColorsAt(7, 59))
        assertEquals(morning, skyColorsAt(8, 0))
        assertEquals(morning, skyColorsAt(11, 29))
        assertEquals(midday, skyColorsAt(11, 30))
        assertEquals(midday, skyColorsAt(15, 29))
        assertEquals(afternoon, skyColorsAt(15, 30))
        assertEquals(afternoon, skyColorsAt(18, 29))
        assertEquals(dusk, skyColorsAt(18, 30))
        assertEquals(dusk, skyColorsAt(20, 29))
        assertEquals(evening, skyColorsAt(20, 30))
        assertEquals(evening, skyColorsAt(23, 29))
        assertEquals(night, skyColorsAt(23, 30))
        assertEquals(night, skyColorsAt(23, 59))
    }
}
