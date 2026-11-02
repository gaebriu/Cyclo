package com.gaebriu.cyclo

import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gaebriu.cyclo.ui.effects.ProvideVisualEffects
import com.gaebriu.cyclo.ui.screens.IntroductionFlow
import com.gaebriu.cyclo.ui.theme.CycloMotion

/** Exemplo mínimo para ver o Introduction 0 e 1. Substitui a MainActivity do template. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false

        // Ecrã de abertura (#010101, ver Theme.Cyclo): sai com um fade subtil para o primeiro ecrã.
        // O ViewPropertyAnimator respeita a escala de animações do sistema (desligadas = sai logo).
        splashScreen.setOnExitAnimationListener { splash ->
            splash.animate()
                .alpha(0f)
                .setDuration(CycloMotion.SplashFadeDurationMs)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { splash.remove() }
                .start()
        }

        setContent {
            ProvideVisualEffects {
                IntroductionFlow()
            }
        }
    }
}
