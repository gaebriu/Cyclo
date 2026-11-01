package com.gaebriu.cyclo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gaebriu.cyclo.ui.effects.ProvideVisualEffects
import com.gaebriu.cyclo.ui.screens.IntroductionFlow

/** Exemplo mínimo para ver o Introduction 0 e 1. Substitui a MainActivity do template. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            ProvideVisualEffects {
                IntroductionFlow()
            }
        }
    }
}
