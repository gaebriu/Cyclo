package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Número que rola ao mudar (o contador da cafeína): ao somar, o novo sobe de baixo e o antigo sai
 * por cima; ao subtrair, ao contrário. Os dois passam com fade, um ligeiro zoom e desfoque, como
 * os textos a aparecer no resto da app. Toques seguidos interrompem a animação sem saltos.
 *
 * Os algarismos têm todos a mesma largura ("tnum"), para os botões dos lados não se mexerem.
 * Sem animações, só fade; sem desfoque, sem desfoque.
 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    val tabular = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    var current by remember { mutableIntStateOf(value) }
    var previous by remember { mutableStateOf<Int?>(null) }
    var direction by remember { mutableIntStateOf(1) }
    // 0 = o número antigo ainda no sítio, 1 = o novo assente.
    val progress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value == current) return@LaunchedEffect
        direction = if (value > current) 1 else -1
        previous = current
        current = value
        progress.snapTo(0f)
        progress.animateTo(1f, CycloMotion.CounterSpring)
        previous = null
    }

    Box(
        modifier = modifier.semantics { contentDescription = current.toString() },
        contentAlignment = Alignment.Center,
    ) {
        previous?.let { old ->
            BasicText(
                text = old.toString(),
                style = tabular,
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        rollLayer(this, leaving = true, p = p, direction = direction, effects.animationsEnabled, effects.blurEnabled)
                    },
            )
        }
        BasicText(
            text = current.toString(),
            style = tabular,
            modifier = Modifier
                .clearAndSetSemantics { }
                .graphicsLayer {
                    val p = progress.value
                    rollLayer(this, leaving = false, p = p, direction = direction, effects.animationsEnabled, effects.blurEnabled)
                },
        )
    }
}

/**
 * Posição de um dos números: o que entra vai de [CycloMotion.CounterRollDistance] da altura até 0
 * (pode passar um pouco com a mola); o que sai vai de 0 até à mesma distância do outro lado.
 */
private fun rollLayer(
    scope: androidx.compose.ui.graphics.GraphicsLayerScope,
    leaving: Boolean,
    p: Float,
    direction: Int,
    animationsEnabled: Boolean,
    blurEnabled: Boolean,
) = with(scope) {
    // Quanto falta (o que entra) ou quanto já andou (o que sai), de 0 a 1.
    val away = if (leaving) p else 1f - p
    alpha = (1f - away.coerceIn(0f, 1f) * if (leaving) 1.6f else 1f).coerceIn(0f, 1f)
    if (!animationsEnabled) return@with
    val distance = size.height * CycloMotion.CounterRollDistance
    translationY = if (leaving) -direction * distance * away else direction * distance * away
    val scale = lerp(1f, 0.82f, away.coerceIn(0f, 1f))
    scaleX = scale
    scaleY = scale
    val sigma = figmaBlurPx(FigmaBlur.CounterRoll) * away.coerceIn(0f, 1f)
    renderEffect = if (blurEnabled && sigma > 0.5f) BlurEffect(sigma, sigma, TileMode.Decal) else null
}
