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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalContext

/**
 * Estado dos efeitos visuais.
 *
 * - blurEnabled: se o aparelho desenha desfoques (segundo o Android; hoje sempre verdadeiro). Vale
 *   para os desfoques que fazem parte das animações (letras a aparecer, números a rolar, menus a
 *   abrir) e para o loading-data. O desfoque do ecrã que recua quando outro sobe por cima (Feed por
 *   baixo das definições ou do editor, Introduction 0 por baixo do 1) é uma textura avançada desde
 *   a Beta 2.4: usa [textures].
 * - animationsEnabled: falso quando as animações do sistema estão desligadas. Quando é
 *   falso, os movimentos passam a fades com a mesma duração.
 * - textures: "Advanced textures" (definições, Performance). Ligadas, o desenho completo do Figma:
 *   desfoques de fundo das superfícies de vidro, desfoque progressivo das bordas e misturas
 *   Luminosity. Desligadas, o desenho simplificado: sem desfoque de fundo (exceto o loading-data),
 *   o desfoque progressivo dá lugar a um gradiente #010101, e as misturas Luminosity passam a
 *   normais. Os movimentos, as molas e os tempos são os mesmos nos dois casos. Use [textures].
 */
@Immutable
data class VisualEffects(
    val blurEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val advancedTextures: Boolean = true,
) {
    /** Se as texturas avançadas (desfoque de fundo, desfoque progressivo, Luminosity) se desenham. */
    val textures: Boolean get() = blurEnabled && advancedTextures

    /** A mistura de um elemento que no Figma está em Luminosity: normal sem as texturas avançadas. */
    val luminosity: BlendMode get() = if (textures) BlendMode.Luminosity else BlendMode.SrcOver
}

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
