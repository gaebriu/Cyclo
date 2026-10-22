package com.gaebriu.cyclo.ui.effects

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
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
import java.util.function.Consumer

/**
 * Estado dos efeitos visuais, segundo as definições do Android.
 *
 * - blurEnabled: se o sistema permite desfoques. Quando é falso, a app tira os desfoques
 *   e as misturas Luminosity passam a normais.
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
    val blurEnabled by rememberCrossWindowBlurEnabled()
    val animationsEnabled by rememberAnimationsEnabled()
    CompositionLocalProvider(
        LocalVisualEffects provides VisualEffects(blurEnabled, animationsEnabled),
        content = content,
    )
}

/** Usa a API oficial do Android para saber se os desfoques estão disponíveis (Android 12+). */
@Composable
private fun rememberCrossWindowBlurEnabled(): State<Boolean> {
    val context = LocalContext.current
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    val state = remember(windowManager) { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled -> state.value = enabled }
        windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return state
}

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
