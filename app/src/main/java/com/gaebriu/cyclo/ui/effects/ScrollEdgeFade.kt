package com.gaebriu.cyclo.ui.effects

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Desfoque gaussiano numa só direção, com o raio a crescer para as extremidades (desfoque
 * progressivo). Corre duas vezes (vertical e depois horizontal) para dar o desfoque completo.
 * O raio cresce AO QUADRADO de 0 no início de cada faixa até [maxSigma] na borda (vezes a
 * quantidade dessa borda, 0 a 1): perto do texto legível o desfoque é quase nulo, e só fica forte
 * onde o fade já tornou o texto quase transparente. Assim nunca aparece um borrão cinzento visível.
 */
private const val ProgressiveBlurShader = """
uniform shader content;
uniform float height;
uniform float2 direction;
uniform float band;
uniform float topAmount;
uniform float bottomAmount;
uniform float maxSigma;

half4 main(float2 p) {
    float t = topAmount * clamp(1.0 - p.y / band, 0.0, 1.0);
    t = max(t, bottomAmount * clamp(1.0 - (height - p.y) / band, 0.0, 1.0));
    float sigma = maxSigma * t * t;
    if (sigma < 0.5) return content.eval(p);
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

/**
 * Esbate as extremidades de uma área com scroll, em vez de cortar o conteúdo a direito
 * ("fx progressive-blur-bottom" do Figma: gradiente linear de transparente a preto em 120 dp,
 * com desfoque progressivo).
 *
 * - Cada borda só se esbate quando há conteúdo escondido desse lado; o efeito entra por completo
 *   nos primeiros [EdgeStrengthRamp] de scroll. Parado no topo, o título fica nítido e sem fade.
 * - O fade é uma máscara (o conteúdo fica transparente), por isso o fundo #010101 mantém-se igual.
 * - O desfoque progressivo só se aplica com [VisualEffects.blurEnabled]; sem ele fica só o fade.
 *
 * Vem ANTES de `verticalScroll` na cadeia de modificadores, para medir a área visível.
 *
 * @param length altura de cada faixa (120 dp no Figma).
 * @param figmaBlur desfoque máximo na borda, em valores do Figma (ver [figmaBlurPx]).
 */
@Composable
fun Modifier.scrollEdgeFade(state: ScrollState, length: Dp, figmaBlur: Float): Modifier {
    val effects = LocalVisualEffects.current
    // Se o shader não compilar neste aparelho, fica só o fade (nunca rebenta a app).
    val vertical = remember { runCatching { RuntimeShader(ProgressiveBlurShader) }.getOrNull() }
    val horizontal = remember { runCatching { RuntimeShader(ProgressiveBlurShader) }.getOrNull() }
    val strength = EdgeStrengthRamp

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edges = state.edgeAmounts(strength.toPx())
            if (edges.top <= 0f && edges.bottom <= 0f) return@drawWithContent
            // A faixa nunca passa de metade da altura, para as paragens do gradiente ficarem por ordem.
            val band = (length.toPx() / size.height).coerceAtMost(0.5f)
            drawRect(
                brush = Brush.verticalGradient(*fadeStops(band, edges.top, edges.bottom)),
                blendMode = BlendMode.DstIn,
            )
        }
        .then(
            if (effects.blurEnabled && vertical != null && horizontal != null) {
                Modifier.graphicsLayer {
                    val edges = state.edgeAmounts(strength.toPx())
                    renderEffect = if (edges.top > 0f || edges.bottom > 0f) {
                        val band = length.toPx().coerceAtMost(size.height / 2f)
                        val sigma = figmaBlurPx(figmaBlur)
                        listOf(vertical to (0f to 1f), horizontal to (1f to 0f)).forEach { (shader, dir) ->
                            shader.setFloatUniform("height", size.height)
                            shader.setFloatUniform("direction", dir.first, dir.second)
                            shader.setFloatUniform("band", band)
                            shader.setFloatUniform("topAmount", edges.top)
                            shader.setFloatUniform("bottomAmount", edges.bottom)
                            shader.setFloatUniform("maxSigma", sigma)
                        }
                        RenderEffect.createChainEffect(
                            RenderEffect.createRuntimeShaderEffect(horizontal, "content"),
                            RenderEffect.createRuntimeShaderEffect(vertical, "content"),
                        ).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
            } else {
                Modifier
            },
        )
}

/**
 * Quanto scroll é preciso para uma borda chegar ao fade completo. Curto, para que mesmo pouco
 * texto escondido já tenha a borda bem esbatida (a faixa continua a ter [length]). PROPOSTA.
 */
private val EdgeStrengthRamp = 12.dp

/** Paragens de cada faixa do fade: chega para a curva suave parecer contínua. */
private const val FadeSteps = 12

/**
 * Máscara do fade (opacidade do conteúdo, de cima para baixo). Em cada faixa a opacidade desce
 * de 1 até (1 - quantidade) na borda, com uma curva suave (smoothstep): começa devagar junto ao
 * texto legível e chega a zero na borda sem um degrau.
 *
 * @param band a altura de cada faixa, em fração da altura total (no máximo 0,5).
 */
private fun fadeStops(band: Float, top: Float, bottom: Float): Array<Pair<Float, Color>> {
    fun smooth(x: Float) = x * x * (3f - 2f * x)
    val stops = ArrayList<Pair<Float, Color>>(FadeSteps * 2 + 2)
    // Faixa de cima: posição 0 (borda) até band (texto inteiro).
    for (i in 0..FadeSteps) {
        val u = i / FadeSteps.toFloat()
        stops += band * u to Color.Black.copy(alpha = 1f - top * smooth(1f - u))
    }
    // Faixa de baixo: posição 1 - band (texto inteiro) até 1 (borda).
    for (i in 0..FadeSteps) {
        val u = i / FadeSteps.toFloat()
        stops += (1f - band) + band * u to Color.Black.copy(alpha = 1f - bottom * smooth(u))
    }
    return stops.toTypedArray()
}

private class EdgeAmounts(val top: Float, val bottom: Float)

/** Quanto se esbate cada borda (0 a 1): o que já passou para cima e o que ainda falta em baixo. */
private fun ScrollState.edgeAmounts(rampPx: Float): EdgeAmounts {
    if (rampPx <= 0f || maxValue == Int.MAX_VALUE) return EdgeAmounts(0f, 0f)
    return EdgeAmounts(
        top = (value / rampPx).coerceIn(0f, 1f),
        bottom = ((maxValue - value) / rampPx).coerceIn(0f, 1f),
    )
}
