package com.gaebriu.cyclo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gaebriu.cyclo.ui.effects.ProvideVisualEffects
import com.gaebriu.cyclo.ui.studio.WidgetStudio

/**
 * O Widget Studio (beta-3.0): a página que o lançador abre nas definições de um widget da Cyclo
 * (manter o widget carregado → "Definições" / "Personalizar"; `android:configure` com
 * `reconfigurable|configuration_optional`, por isso não abre ao pôr um widget novo).
 *
 * A janela é opaca e mostra a wallpaper por baixo (`windowShowWallpaper`, tema Theme.Cyclo.Studio):
 * a página é #010101 menos a "janela" do preview, onde se vê a wallpaper verdadeira (decisão do
 * utilizador, opção A). A página sobe e desce como as definições na app ([WidgetStudio]), por isso as
 * animações da janela estão desligadas.
 *
 * O resultado é sempre OK (fechar nunca tira o widget do ecrã): as mudanças guardam-se à medida.
 */
class WidgetStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        // A página faz a sua própria entrada (sobe como as definições): nada de animação da janela
        // nem do ecrã de abertura por cima dela.
        splashScreen.setOnExitAnimationListener { it.remove() }
        noWindowAnimation(open = true)

        setContent {
            ProvideVisualEffects {
                WidgetStudio(appWidgetId = appWidgetId, onFinished = { finish() })
            }
        }
    }

    override fun finish() {
        super.finish()
        noWindowAnimation(open = false)
    }

    @Suppress("DEPRECATION")
    private fun noWindowAnimation(open: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(if (open) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }
}
