package com.gaebriu.cyclo

import android.app.Application
import android.content.res.Configuration
import com.gaebriu.cyclo.reminder.CycloSync

/** Arranca o que acompanha os dados enquanto a app corre: o lembrete e os widgets (ver [CycloSync]). */
class CycloApplication : Application() {
    private var fontScale = 0f

    override fun onCreate() {
        super.onCreate()
        fontScale = resources.configuration.fontScale
        CycloSync.start(this)
        // O grão dos brilhos (Noise do Figma) calcula-se já, em segundo plano, para o primeiro ecrã.
        com.gaebriu.cyclo.ui.effects.FigmaNoise.prewarm(resources.displayMetrics.density)
        // beta-3.0: o vidro dos widgets usa as cores da wallpaper; quando ela muda (com a app viva),
        // os widgets voltam a ser desenhados. Sem a app viva, apanham as cores novas no desenho seguinte.
        runCatching {
            android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener(
                { _, which -> if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0) CycloSync.refresh(this) },
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
        }
    }

    /**
     * O tamanho de letra do sistema mudou: os textos dos widgets (desenhados com a Inter em
     * bitmaps, ver WidgetText) voltam a ser desenhados no tamanho novo.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.fontScale != fontScale) {
            fontScale = newConfig.fontScale
            CycloSync.refresh(this)
        }
    }
}
