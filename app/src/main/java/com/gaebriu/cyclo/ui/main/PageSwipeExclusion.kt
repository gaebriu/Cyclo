package com.gaebriu.cyclo.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * As zonas com um gesto horizontal próprio (ex.: o medidor de emoções), onde o swipe entre o Feed e
 * as rotinas guardadas não começa. Sem isto, um movimento rápido que passe os dois limiares no
 * mesmo evento podia ir para a página em vez de ir para a bola.
 */
class PageSwipeExclusions {
    private val zones = LinkedHashMap<Any, Rect>()

    internal fun set(owner: Any, bounds: Rect) {
        zones[owner] = bounds
    }

    internal fun remove(owner: Any) {
        zones.remove(owner)
    }

    /** Se [windowPosition] (coordenadas da janela) está numa zona excluída. */
    fun contains(windowPosition: Offset): Boolean = zones.values.any { it.contains(windowPosition) }
}

val LocalPageSwipeExclusions = staticCompositionLocalOf<PageSwipeExclusions?> { null }

/** Este elemento tem o seu gesto horizontal: o swipe de páginas não começa nele. */
@Composable
fun Modifier.excludeFromPageSwipe(): Modifier {
    val exclusions = LocalPageSwipeExclusions.current ?: return this
    val owner = remember { Any() }
    DisposableEffect(exclusions) { onDispose { exclusions.remove(owner) } }
    return this.onGloballyPositioned { exclusions.set(owner, it.boundsInWindow()) }
}
