package com.gaebriu.cyclo.ui.effects

import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.roundToInt

/**
 * Um degrau do desfoque progressivo: o fundo desfocado com [sigmaFraction] do desfoque máximo,
 * a aparecer (máscara de 0 a 1) entre [rampStart] e [rampEnd] da faixa (0 = borda de dentro,
 * 1 = borda do ecrã) e inteiro daí até à borda do ecrã.
 */
private class EdgeStep(val sigmaFraction: Float, val rampStart: Float, val rampEnd: Float)

/**
 * Resolução da cópia do fundo usada nas faixas (1/2). O resultado é desfocado (pelo menos meio
 * desfoque do Figma onde se vê), por isso a resolução mais baixa não se nota.
 */
private const val EdgeCopyScale = 0.5f

/** Dois degraus: metade do desfoque na primeira metade da faixa, o desfoque todo na segunda. */
private val EdgeSteps = listOf(
    EdgeStep(sigmaFraction = 0.5f, rampStart = 0f, rampEnd = 0.5f),
    EdgeStep(sigmaFraction = 1f, rampStart = 0.5f, rampEnd = 1f),
)

/**
 * A camada de um degrau. O efeito (desfoque do sistema e máscara em gradiente, num só
 * RenderEffect) só se refaz quando a medida ou o desfoque mudam.
 */
private class EdgeStepLayer(val layer: GraphicsLayer) {
    private var key: List<Any>? = null

    fun update(step: EdgeStep, width: Float, height: Float, sigma: Float, fromTop: Boolean) {
        val next = listOf(width, height, sigma, fromTop)
        if (next == key) return
        val s = (sigma * step.sigmaFraction).coerceAtLeast(0.5f)
        // Posições da rampa em px: a borda do ecrã é o topo (faixa de cima) ou o fundo (faixa de baixo).
        val start = if (fromTop) height * (1f - step.rampStart) else height * step.rampStart
        val end = if (fromTop) height * (1f - step.rampEnd) else height * step.rampEnd
        val mask = LinearGradient(
            0f, start, 0f, end,
            android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK,
            Shader.TileMode.CLAMP,
        )
        layer.renderEffect = RenderEffect.createBlendModeEffect(
            RenderEffect.createShaderEffect(mask),
            RenderEffect.createBlurEffect(s, s, Shader.TileMode.CLAMP),
            BlendMode.SRC_IN,
        ).asComposeRenderEffect()
        key = next
    }
}

/**
 * Faixa do topo ou do fundo do ecrã ("fx progressive-blur-top" / "-bottom"): o que passa por baixo
 * desfoca-se cada vez mais até à borda do ecrã (15 do Figma na borda, 0 na borda de dentro), com o
 * gradiente escuro do Figma por cima.
 *
 * O desfoque progressivo faz-se com cópias do fundo ([LocalBackdrop]) desfocadas pelo desfoque do
 * sistema e reveladas por máscaras em gradiente, em degraus ([EdgeSteps]). Antes era um shader
 * próprio com duas passagens à resolução inteira, refeito a cada frame: pesava tanto que o scroll
 * das definições perdia a maioria dos frames. O desfoque do sistema trabalha a uma resolução
 * reduzida e fica muito mais leve, com o mesmo aspeto.
 *
 * Na borda do ecrã o desfoque estende os píxeis da borda ("clamp"), em vez de ir buscar
 * transparência (o que deixava o topo cortado).
 *
 * Sem desfoque nas definições fica só o gradiente.
 *
 * @param figmaBlur desfoque na borda do ecrã, em valores do Figma (startRadius).
 * @param shade a cor do gradiente na borda do ecrã (transparente na borda de dentro).
 * @param amount de 0 a 1: quanto do efeito se vê (ex.: some no fim do scroll, para não tapar o
 *               último texto). A 0 não desenha nada.
 */
@Composable
fun ProgressiveBlurEdge(
    top: Boolean,
    height: Dp,
    figmaBlur: Float,
    shade: Color,
    modifier: Modifier = Modifier,
    amount: () -> Float = { 1f },
) {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    val first = rememberGraphicsLayer()
    val second = rememberGraphicsLayer()
    val steps = remember(first, second) { listOf(EdgeStepLayer(first), EdgeStepLayer(second)) }
    // O fundo por baixo da faixa, desenhado UMA vez por frame a meia resolução (camada própria,
    // partilhada pelos degraus). Antes cada degrau voltava a desenhar tudo à resolução inteira.
    val copy = rememberGraphicsLayer()
    remember(copy) { copy.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .backdropSampler(sampler)
            .drawBehind {
                val a = amount().coerceIn(0f, 1f)
                if (a <= 0f) return@drawBehind
                val self = sampler.coordinates?.takeIf { it.isAttached }
                sampler.moves
                if (effects.blurEnabled && self != null && backdrop.layers.isNotEmpty()) {
                    // O desfoque diminui com a quantidade (em degraus pequenos, para não refazer o efeito a cada frame).
                    val sigma = figmaBlurPx(figmaBlur) * (a * 20f).roundToInt() / 20f
                    if (sigma >= 0.5f) {
                        val s = EdgeCopyScale
                        val w = size.width * s
                        val h = size.height * s
                        val recordSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
                        copy.record(recordSize) {
                            scale(s, s, pivot = Offset.Zero) { drawLayers(backdrop, self) }
                        }
                        scale(1f / s, 1f / s, pivot = Offset.Zero) {
                            EdgeSteps.forEachIndexed { index, step ->
                                val stepLayer = steps[index]
                                stepLayer.update(step, w, h, sigma * s, top)
                                stepLayer.layer.record(recordSize) { drawLayer(copy) }
                                drawLayer(stepLayer.layer)
                            }
                        }
                    }
                }
                val edge = shade.copy(alpha = shade.alpha * a)
                val inner = shade.copy(alpha = 0f)
                drawRect(Brush.verticalGradient(if (top) listOf(edge, inner) else listOf(inner, edge)))
            },
    )
}
