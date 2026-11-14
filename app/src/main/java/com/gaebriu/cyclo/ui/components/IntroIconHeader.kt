package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.icons.HeaderIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gaebriu.cyclo.ui.theme.smoothStep
import kotlin.math.PI
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Onde fica a imagem do fx spotlight em relação ao canto de cima à esquerda do círculo de 63 dp.
// Têm de coincidir com LEFT, TOP, WIDTH e HEIGHT de tools/bake_spotlight.py.
private val SpotlightLeft = (-152).dp
private val SpotlightTop = (-20).dp
private val SpotlightWidth = 367.dp
private val SpotlightHeight = 235.dp

/** Centro da caixa de 63 x 63 do ícone, nas unidades do SVG (o sol fica centrado aqui). */
private const val IconCenter = 31.5f

/** A ponta por onde a etiqueta fica pendurada (perto da borda esquerda da caixa de 63). */
private const val LeftPivotX = 8f

/** O holofote entra a abrir daqui (Beta 3.1, igual em todas as páginas). */
private const val SpotlightFromScale = 0.9f

/** Um ciclo da respiração do holofote (s): mais lento do que respirar, para quase não se notar. */
private const val BreathPeriodS = 6.5f

/** Este ponto rodado [degrees] graus (sentido horário no ecrã) à volta de [pivot]. */
private fun Offset.rotateAround(pivot: Offset, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val dx = x - pivot.x
    val dy = y - pivot.y
    return Offset(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
}

/**
 * Ícone com o holofote roxo por trás (sleep-icon, sun-icon...), 63 x 95 dp.
 *
 * O holofote ("fx spotlight") é, no Figma, um círculo de 63 dp (#4A3C99, 63 %) com desfoque
 * PROGRESSIVO, de 9 em cima a 100 em baixo: quase nítido no topo e a espalhar-se para baixo.
 * Como o desfoque progressivo não existe no Android (nem no SVG do Figma), vem pré-calculado na
 * imagem `fx_spotlight` (ver tools/bake_spotlight.py). Desenhar uma imagem funciona em qualquer
 * aparelho, sem RenderEffect.
 * O ícone leva gradiente vertical #FCFCFE a #9F9FA0 e mistura Luminosity, como no design.
 *
 * @param rotation rotação do ícone em graus (sentido horário), à volta do centro da caixa de 63 dp.
 *        É lida só ao desenhar, por isso animá-la não recompõe nada. O holofote não roda.
 * @param scale escala do ícone à volta do mesmo centro (lida só ao desenhar; o holofote fica).
 * @param motion a entrada e as reações do ícone e do holofote ([IconMotion]), somadas a
 *        [rotation] e [scale]. O holofote abre de 90 % à volta do círculo e acende com a luz.
 *        Beta-3.2: com a página à vista ([IconMotion.breathing]), o holofote respira muito devagar
 *        (luz 92–100 %, ±1,5 % de tamanho, 6,5 s): só propriedades da camada, nada se redesenha.
 *        O Figma tirou o grão (Noise) dos holofotes no Cyclo 3.
 */
@Composable
fun IntroIconHeader(
    icon: HeaderIcon,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
    scale: () -> Float = { 1f },
    motion: IconMotion? = null,
) {
    val effects = LocalVisualEffects.current
    val shape = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    val spotlight: ImageBitmap = ImageBitmap.imageResource(R.drawable.fx_spotlight)
    // O relógio da respiração (s), só enquanto a página está à vista e a app à frente.
    val breath = remember { mutableFloatStateOf(0f) }
    val breathing = motion?.breathing?.value == true && effects.animationsEnabled
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(breathing, lifecycle) {
        if (!breathing) {
            breath.floatValue = 0f
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var start = -1L
            while (true) {
                withFrameNanos { now ->
                    if (start < 0L) start = now - (breath.floatValue * 1e9f).toLong()
                    breath.floatValue = (now - start) / 1e9f
                }
            }
        }
    }

    Box(modifier = modifier.size(63.dp, 95.dp)) {
        Spacer(
            modifier = Modifier
                .size(63.dp)
                .then(
                    if (motion == null) {
                        Modifier
                    } else {
                        // ModulateAlpha: sem camada fora do ecrã, que cortaria o holofote nos 63 dp.
                        Modifier.graphicsLayer {
                            val l = motion.light.value.coerceIn(0f, 1f)
                            val t = breath.floatValue
                            // Entra devagar (1,5 s) para não saltar quando a página assenta.
                            val b = smoothStep(t, 0f, 1.5f) * sin(2f * PI.toFloat() * t / BreathPeriodS)
                            alpha = l * (0.96f + 0.04f * b)
                            val s = lerp(SpotlightFromScale, 1f, l) * (1f + 0.015f * b)
                            scaleX = s
                            scaleY = s
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                    },
                )
                .drawBehind {
                    val offset = IntOffset(SpotlightLeft.toPx().roundToInt(), SpotlightTop.toPx().roundToInt())
                    val dstSize = IntSize(SpotlightWidth.toPx().roundToInt(), SpotlightHeight.toPx().roundToInt())
                    drawImage(image = spotlight, dstOffset = offset, dstSize = dstSize, filterQuality = FilterQuality.High)
                },
        )

        Spacer(
            modifier = Modifier
                .offset(y = 32.dp)
                .size(63.dp)
                .drawBehind {
                    val blend = effects.luminosity
                    val pxPerDp = density
                    val degrees = rotation()
                    val iconScaleX = scale() * (motion?.scaleX?.value ?: 1f)
                    val iconScaleY = scale() * (motion?.scaleY?.value ?: 1f)
                    val lift = motion?.lift?.value ?: 0f
                    val swing = motion?.rotation?.value ?: 0f
                    val pivot = Offset(IconCenter, IconCenter) +
                        Offset(CycloIconPaths.BedOriginX, CycloIconPaths.BedOriginY)
                    val swingPivot = when (motion?.style?.pivot) {
                        IconPivot.Top -> Offset(pivot.x, icon.gradientTop)
                        IconPivot.Left -> Offset(CycloIconPaths.BedOriginX + LeftPivotX, pivot.y)
                        else -> pivot
                    }
                    // Pousar (a cama, a folha): deforma a partir da base, como algo que assenta.
                    val scalePivot = when (motion?.style) {
                        IconMotionStyle.Rest, IconMotionStyle.Page -> Offset(pivot.x, icon.gradientBottom)
                        else -> pivot
                    }
                    withTransform({
                        scale(pxPerDp, pxPerDp, Offset.Zero)
                        translate(-CycloIconPaths.BedOriginX, -CycloIconPaths.BedOriginY)
                        if (lift != 0f) translate(0f, lift)
                        if (iconScaleX != 1f || iconScaleY != 1f) scale(iconScaleX, iconScaleY, scalePivot)
                        if (degrees != 0f) rotate(degrees, pivot)
                        if (swing != 0f) rotate(swing, swingPivot)
                    }) {
                        // O gradiente roda ao contrário, para a luz continuar a vir de cima.
                        val start = Offset(pivot.x, icon.gradientTop).rotateAround(pivot, -degrees).rotateAround(swingPivot, -swing)
                        val end = Offset(pivot.x, icon.gradientBottom).rotateAround(pivot, -degrees).rotateAround(swingPivot, -swing)
                        drawPath(
                            path = shape,
                            brush = Brush.linearGradient(
                                0f to Color(0xFFFCFCFE),
                                1f to Color(0xFF9F9FA0),
                                start = start,
                                end = end,
                            ),
                            blendMode = blend,
                        )
                    }
                },
        )
    }
}
