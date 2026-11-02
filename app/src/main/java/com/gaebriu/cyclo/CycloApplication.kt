package com.gaebriu.cyclo

import android.app.Application
import com.gaebriu.cyclo.reminder.CycloSync

/** Arranca o que acompanha os dados enquanto a app corre: o lembrete e os widgets (ver [CycloSync]). */
class CycloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CycloSync.start(this)
    }
}
