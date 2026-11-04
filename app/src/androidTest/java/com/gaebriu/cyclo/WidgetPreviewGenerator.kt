package com.gaebriu.cyclo

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaebriu.cyclo.widget.BedtimeWidgets
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Gera as pré-visualizações dos widgets para a lista de widgets do lançador, com o MESMO código de
 * desenho dos widgets reais (BedtimeWidgets.previewBitmaps) e os valores de exemplo do Figma.
 *
 * Não é um teste de comportamento: corre-se à mão quando o desenho dos widgets muda, e os PNG vão
 * para res/drawable-nodpi (inglês) e res/drawable-pt-nodpi (português):
 *
 *   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gaebriu.cyclo.WidgetPreviewGenerator
 *   adb pull /sdcard/Android/data/<applicationId>/files/widget-previews
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewGenerator {
    @Test
    fun generate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.getExternalFilesDir(null), "widget-previews").apply { mkdirs() }
        for ((folder, locale) in listOf("drawable-nodpi" to Locale.ENGLISH, "drawable-pt-nodpi" to Locale.forLanguageTag("pt-PT"))) {
            val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val localized = context.createConfigurationContext(config)
            // 4 píxeis por unidade do Figma (xxxhdpi): nítido em qualquer ecrã.
            val (routine, wake) = BedtimeWidgets.previewBitmaps(localized, pxPerUnit = 4f)
            val dir = File(out, folder).apply { mkdirs() }
            File(dir, "widget_routine_preview_image.png").outputStream().use { routine.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "widget_wake_time_preview_image.png").outputStream().use { wake.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
