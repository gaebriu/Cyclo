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
