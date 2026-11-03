package com.gaebriu.cyclo

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import com.gaebriu.cyclo.widget.BedtimeWidgets
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gaebriu.cyclo.data.CycloViewModel
import com.gaebriu.cyclo.ui.CycloApp
import com.gaebriu.cyclo.ui.effects.ProvideVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloMotion

/**
 * A única Activity: mostra a [CycloApp]. Enquanto os dados se leem (milissegundos), só se vê o fundo
 * #010101, igual ao do ecrã de abertura: a app abre logo no sítio certo (o tutorial ou o Feed),
 * sem nada a carregar.
 *
 * NÃO segurar o primeiro desenho com um OnPreDrawListener: com o fade de saída do ecrã de abertura,
 * a app ficava preta até ao primeiro toque.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: CycloViewModel by viewModels()

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

        // Aberta pelo lápis do widget: vai direta ao editor da rotina (só na primeira criação, não
        // ao recriar depois de rodar o ecrã).
        if (savedInstanceState == null) handleWidgetIntent(intent)
        lastLocales = resources.configuration.locales

        setContent {
            ProvideVisualEffects {
                CycloApp(viewModel)
            }
        }
    }

    /**
     * A língua mudou (escolhida nas definições do Cyclo ou do Android): a Activity não é recriada
     * (configChanges no manifesto), o Compose volta a ler os textos sozinho. Aqui redesenham-se os
     * widgets e atualiza-se o nome do canal das notificações, que vivem fora da Activity.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        val before = lastLocales
        super.onConfigurationChanged(newConfig)
        lastLocales = newConfig.locales
        if (before != null && before != newConfig.locales) {
            viewModel.data.value?.let { com.gaebriu.cyclo.reminder.CycloSync.languageChanged(applicationContext, it) }
        }
    }

    private var lastLocales: android.os.LocaleList? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action != BedtimeWidgets.ActionEdit) return
        val routineId = intent.getLongExtra(BedtimeWidgets.ExtraRoutineId, -1L)
        if (routineId >= 0) viewModel.requestEdit(routineId)
    }
}
