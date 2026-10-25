package com.gaebriu.cyclo.ui.effects

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Estado dos efeitos visuais, segundo as definições do Android.
 *
 * - blurEnabled: se os desfoques e as misturas Luminosity se desenham. Quando é falso, a app
 *   tira os desfoques e as misturas passam a normais.
 * - animationsEnabled: falso quando as animações do sistema estão desligadas. Quando é
 *   falso, os movimentos passam a fades com a mesma duração.
 */
@Immutable
data class VisualEffects(
    val blurEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
)

val LocalVisualEffects = staticCompositionLocalOf { VisualEffects() }

@Composable
fun ProvideVisualEffects(content: @Composable () -> Unit) {
    val blurEnabled by rememberBlurEnabled()
    val animationsEnabled by rememberAnimationsEnabled()
    CompositionLocalProvider(
        LocalVisualEffects provides VisualEffects(blurEnabled, animationsEnabled),
        content = content,
    )
}

/**
 * Os desfoques da app (RenderEffect) funcionam em qualquer aparelho com Android 12 ou superior.
 *
 * NÃO usar WindowManager.isCrossWindowBlurEnabled para isto: essa API só diz se o sistema
 * desfoca JANELAS (diálogos, fundos de janela) e devolve falso em muitos aparelhos,
 * incluindo Samsung. Foi o que escondia os brilhos roxos e os desfoques no S24.
 *
 * A definição "Reduzir transparência e desfocagem" da Samsung (Definições > Acessibilidade >
 * Melhorias de visão) ainda NÃO é lida, porque falta descobrir a chave do sistema onde é guardada.
 * Quando a tivermos, é só ligá-la aqui.
 */
@Composable
private fun rememberBlurEnabled(): State<Boolean> = remember { mutableStateOf(true) }

/** Lê a escala de animações do sistema (0 = animações desligadas). */
@Composable
private fun rememberAnimationsEnabled(): State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    val state = remember(resolver) { mutableStateOf(readAnimationsEnabled(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = readAnimationsEnabled(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}

private fun readAnimationsEnabled(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
