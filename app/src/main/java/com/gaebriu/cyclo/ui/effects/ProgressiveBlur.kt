package com.gaebriu.cyclo.ui.effects

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Desfoque gaussiano numa só direção, com o desvio-padrão a variar em linha reta de [maxSigma] na
 * borda do ecrã até 0 na borda de dentro (o "progressive blur" do Figma: startRadius na borda,
 * 0 no fim). Corre duas vezes (vertical e horizontal) para dar o desfoque completo.
 */
private const val EdgeBlurShader = """
uniform shader content;
uniform float height;
uniform float2 direction;
uniform float maxSigma;
uniform float fromTop;

half4 main(float2 p) {
    float t = fromTop > 0.5 ? 1.0 - p.y / height : p.y / height;
    float sigma = maxSigma * clamp(t, 0.0, 1.0);
    if (sigma < 0.5) {
        return content.eval(p);
    }
    float4 sum = float4(0.0);
    float weights = 0.0;
    for (int i = -12; i <= 12; i++) {
        float w = exp(-float(i * i) / 32.0);
        sum += float4(content.eval(p + direction * (float(i) * sigma / 4.0))) * w;
        weights += w;
    }
    return half4(sum / weights);
}
"""

/** Os dois efeitos (vertical e horizontal) de uma faixa; só se refazem quando a medida muda. */
private class EdgeBlur(private val vertical: RuntimeShader, private val horizontal: RuntimeShader, val layer: GraphicsLayer) {
    private var key: Triple<Float, Float, Boolean>? = null

    fun update(height: Float, sigma: Float, fromTop: Boolean) {
        val next = Triple(height, sigma, fromTop)
        if (next == key) return
        listOf(vertical to (0f to 1f), horizontal to (1f to 0f)).forEach { (shader, dir) ->
            shader.setFloatUniform("height", height)
            shader.setFloatUniform("direction", dir.first, dir.second)
            shader.setFloatUniform("maxSigma", sigma)
            shader.setFloatUniform("fromTop", if (fromTop) 1f else 0f)
        }
        layer.renderEffect = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(horizontal, "content"),
            RenderEffect.createRuntimeShaderEffect(vertical, "content"),
        ).asComposeRenderEffect()
        key = next
    }
}

/**
 * Faixa do topo ou do fundo do ecrã ("fx progressive-blur-top" / "-bottom"): o que passa por baixo
 * desfoca-se cada vez mais até à borda do ecrã, com o gradiente escuro do Figma por cima.
 *
 * O desfoque usa as camadas de [LocalBackdrop] (o que está por baixo). Sem desfoque nas definições
 * (ou se o shader não compilar) fica só o gradiente.
 *
 * @param figmaBlur desfoque na borda do ecrã, em valores do Figma (startRadius).
 * @param shade a cor do gradiente na borda do ecrã (transparente na borda de dentro).
 */
@Composable
fun ProgressiveBlurEdge(
    top: Boolean,
    height: Dp,
    figmaBlur: Float,
    shade: Color,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val backdrop = LocalBackdrop.current
    val sampler = rememberBackdropSampler()
    val layer = rememberGraphicsLayer()
    val blur = remember(layer) {
        runCatching { EdgeBlur(RuntimeShader(EdgeBlurShader), RuntimeShader(EdgeBlurShader), layer) }.getOrNull()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .backdropSampler(sampler)
            .drawBehind {
                val self = sampler.coordinates?.takeIf { it.isAttached }
                sampler.moves
                if (effects.blurEnabled && blur != null && self != null && backdrop.layers.isNotEmpty()) {
                    blur.update(size.height, figmaBlurPx(figmaBlur), top)
                    blur.layer.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                        drawLayers(backdrop, self)
                    }
                    drawLayer(blur.layer)
                }
                val edge = shade
                val inner = shade.copy(alpha = 0f)
                drawRect(Brush.verticalGradient(if (top) listOf(edge, inner) else listOf(inner, edge)))
            },
    )
}
