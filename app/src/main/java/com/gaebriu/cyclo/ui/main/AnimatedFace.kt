package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.smoothStep
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A cara de um contador do Feed (cafeína, água) a mudar de expressão (beta-3.2). A troca é um
 * movimento, não um corte:
 *
 * - a cara que sai encolhe e apaga-se na primeira metade; a nova cresce do mesmo sítio, um pouco
 *   depois (nunca se veem as duas inteiras, nem nenhuma desaparece a meio);
 * - ao mesmo tempo, o ícone reage com o sentido da mudança ([level], menor = melhor): a melhorar
 *   ergue-se (sobe 2 dp, inclina 9° para trás e estica um nadinha na altura); a piorar descai (desce,
 *   inclina para a frente e achata). Largura e altura em molas desfasadas, como o gel dos botões;
 * - a cor passa de uma para a outra na mesma mola.
 *
 * A mesma cara com outra cor (cafeína 5 → 6) só muda a cor e descai, sem trocar o desenho.
 * Sem animações, troca e muda de cor de imediato.
 */
@Composable
fun AnimatedFace(path: String, tint: Color, level: Int, modifier: Modifier = Modifier, size: Dp = 15.dp) {
    val effects = LocalVisualEffects.current
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    var shown by remember { mutableStateOf(parsed) }
    var previous by remember { mutableStateOf<Path?>(null) }
    var lastLevel by remember { mutableIntStateOf(level) }
    val swap = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val stretchX = remember { Animatable(1f) }
    val stretchY = remember { Animatable(1f) }
    val color by animateColorAsState(
        targetValue = tint,
        animationSpec = if (effects.animationsEnabled) CycloSprings.FaceChange.spec() else androidx.compose.animation.core.snap(),
        label = "faceTint",
    )

    LaunchedEffect(parsed, level) {
        val direction = (lastLevel - level).coerceIn(-1, 1) // 1 = melhorou
        lastLevel = level
        if (parsed !== shown) {
            previous = shown
            shown = parsed
            if (effects.animationsEnabled) {
                swap.snapTo(0f)
            } else {
                previous = null
                return@LaunchedEffect
            }
        }
        if (!effects.animationsEnabled || direction == 0 && swap.value >= 1f) return@LaunchedEffect
        coroutineScope {
            launch {
                swap.animateTo(1f, CycloSprings.FaceChange.float)
                previous = null
            }
            // Impulsos: cada canal parte do repouso com uma velocidade e volta a ele (um só gesto).
            launch { tilt.animateTo(0f, CycloSprings.FaceTilt.float, initialVelocity = tilt.velocity - direction * FaceTiltVelocity) }
            launch { lift.animateTo(0f, CycloSprings.FaceTilt.float, initialVelocity = lift.velocity - direction * FaceLiftVelocity) }
            launch { stretchY.animateTo(1f, CycloSprings.IconDeformY.float, initialVelocity = stretchY.velocity + direction * FaceStretchVelocity) }
            launch { stretchX.animateTo(1f, CycloSprings.IconDeformX.float, initialVelocity = stretchX.velocity - direction * FaceStretchVelocity * 0.6f) }
        }
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val t = swap.value
        val liftPx = lift.value.dp.toPx()
        withTransform({
            translate(top = liftPx)
            rotate(tilt.value, center)
            scale(stretchX.value, stretchY.value, center)
        }) {
            previous?.let { old ->
                val a = 1f - smoothStep(t, 0.05f, 0.5f)
                if (a > 0f) drawFace(old, color, a, lerp(1f, FaceSwapScale, smoothStep(t, 0f, 0.6f)), center)
            }
            // Sobrepostas o bastante para a cara nunca desaparecer a meio (a soma fica acima de ~2/3).
            val a = if (previous == null) 1f else smoothStep(t, 0.15f, 0.6f)
            drawFace(shown, color, a, if (previous == null) 1f else lerp(FaceSwapScale, 1f, t), center)
        }
    }
}

private fun DrawScope.drawFace(path: Path, color: Color, alpha: Float, scale: Float, center: Offset) {
    val pxPerDp = density
    withTransform({
        scale(scale, scale, center)
        scale(pxPerDp, pxPerDp, Offset.Zero)
    }) {
        drawPath(path, color = color, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** A cara que sai encolhe até aqui; a nova cresce daqui (o movimento passa por ela). */
private const val FaceSwapScale = 0.72f

/** Os impulsos da reação: graus/s, dp/s e fração/s (a melhorar; a piorar, ao contrário). */
private const val FaceTiltVelocity = 120f
private const val FaceLiftVelocity = 22f
private const val FaceStretchVelocity = 0.9f
