package com.gaebriu.cyclo.ui.effects

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Desfoque gaussiano numa só direção, com o raio a crescer para as extremidades (desfoque
 * progressivo). Corre duas vezes (vertical e depois horizontal) para dar o desfoque completo.
 * O raio cresce AO QUADRADO de 0 no início de cada faixa até [maxSigma] na borda (vezes a
 * quantidade dessa borda, 0 a 1): perto do texto legível o desfoque é quase nulo, e só fica forte
 * onde o fade já tornou o texto quase transparente. Assim nunca aparece um borrão cinzento visível.
 *
 * Na última passagem ([fade] = 1) aplica também o fade, com a mesma curva de [fadeStops]: assim o
 * desfoque e o fade saem do MESMO desenho, com os mesmos valores, e nunca ficam desfasados do scroll.
 */
private const val ProgressiveBlurShader = """
uniform shader content;
uniform float height;
uniform float2 direction;
uniform float band;
uniform float topAmount;
uniform float bottomAmount;
uniform float maxSigma;
uniform float fade;

half4 main(float2 p) {
    float inTop = clamp(1.0 - p.y / band, 0.0, 1.0);
    float inBottom = clamp(1.0 - (height - p.y) / band, 0.0, 1.0);
    float t = max(topAmount * inTop, bottomAmount * inBottom);
    float sigma = maxSigma * t * t;
    float4 color;
    if (sigma < 0.5) {
        color = float4(content.eval(p));
    } else {
        float4 sum = float4(0.0);
        float weights = 0.0;
        for (int i = -12; i <= 12; i++) {
            float w = exp(-float(i * i) / 32.0);
            sum += float4(content.eval(p + direction * (float(i) * sigma / 4.0))) * w;
            weights += w;
        }
        color = sum / weights;
    }
    if (fade > 0.5) {
        float alpha = (1.0 - topAmount * smoothstep(0.0, 1.0, inTop))
            * (1.0 - bottomAmount * smoothstep(0.0, 1.0, inBottom));
        color *= alpha;
    }
    return half4(color);
}
"""

/**
 * Esbate as extremidades de uma área com scroll, em vez de cortar o conteúdo a direito
 * ("fx progressive-blur-bottom" do Figma: gradiente linear de transparente a preto em 120 dp,
 * com desfoque progressivo).
 *
 * - Cada borda só se esbate quando há conteúdo escondido desse lado; o efeito entra por completo
 *   nos primeiros [EdgeStrengthRamp] de scroll. Parado no topo, o título fica nítido e sem fade.
 * - O fade deixa o conteúdo transparente, por isso o fundo #010101 mantém-se igual.
 * - Com [VisualEffects.blurEnabled], o desfoque progressivo e o fade saem de um só efeito (o
 *   shader), na mesma camada que o conteúdo com scroll: acompanham o movimento no mesmo frame.
 *   Sem desfoque (ou se o shader não compilar) fica só o fade, como máscara.
 * - As quantidades de cada borda calculam-se num só sítio e só mudam perto das pontas; a meio do
 *   scroll nada é recalculado nem recriado a cada frame, e o efeito só se refaz quando muda.
 *
 * Vem ANTES de `verticalScroll` na cadeia de modificadores, para medir a área visível.
 *
 * @param length altura de cada faixa (120 dp no Figma).
 * @param figmaBlur desfoque máximo na borda, em valores do Figma (ver [figmaBlurPx]).
 */
@Composable
fun Modifier.scrollEdgeFade(state: ScrollState, length: Dp, figmaBlur: Float): Modifier {
    val effects = LocalVisualEffects.current
    val rampPx = with(LocalDensity.current) { EdgeStrengthRamp.toPx() }
    // Só muda quando as quantidades mudam (perto das pontas), não a cada pixel de scroll.
    val edges by remember(state, rampPx) { derivedStateOf { state.edgeAmounts(rampPx) } }

    // Se o shader não compilar neste aparelho, fica só o fade (nunca rebenta a app).
    val vertical = remember { runCatching { RuntimeShader(ProgressiveBlurShader) }.getOrNull() }
    val horizontal = remember { runCatching { RuntimeShader(ProgressiveBlurShader) }.getOrNull() }

    if (effects.blurEnabled && vertical != null && horizontal != null) {
        val cache = remember { EdgeEffectCache(vertical, horizontal) }
        return graphicsLayer {
            val e = edges
            renderEffect = if (e.top > 0f || e.bottom > 0f) {
                cache.effect(
                    EdgeEffectParams(
                        height = size.height,
                        band = length.toPx().coerceAtMost(size.height / 2f),
                        top = e.top,
                        bottom = e.bottom,
                        sigma = figmaBlurPx(figmaBlur),
                    ),
                )
            } else {
                null
            }
        }
    }

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val e = edges
            if (e.top <= 0f && e.bottom <= 0f) return@drawWithContent
            // A faixa nunca passa de metade da altura, para as paragens do gradiente ficarem por ordem.
            val band = (length.toPx() / size.height).coerceAtMost(0.5f)
            drawRect(
                brush = Brush.verticalGradient(*fadeStops(band, e.top, e.bottom)),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** Tudo o que define o efeito; se não mudar, o efeito anterior serve. */
private data class EdgeEffectParams(
    val height: Float,
    val band: Float,
    val top: Float,
    val bottom: Float,
    val sigma: Float,
)

/** Guarda o último efeito e só o refaz quando os parâmetros mudam. */
private class EdgeEffectCache(
    private val vertical: RuntimeShader,
    private val horizontal: RuntimeShader,
) {
    private var params: EdgeEffectParams? = null
    private var effect: ComposeRenderEffect? = null

    fun effect(next: EdgeEffectParams): ComposeRenderEffect {
        effect?.let { if (next == params) return it }
        // Primeiro o desfoque vertical; depois o horizontal, que aplica também o fade.
        listOf(vertical to (0f to 1f), horizontal to (1f to 0f)).forEach { (shader, dir) ->
            shader.setFloatUniform("height", next.height)
            shader.setFloatUniform("direction", dir.first, dir.second)
            shader.setFloatUniform("band", next.band)
            shader.setFloatUniform("topAmount", next.top)
            shader.setFloatUniform("bottomAmount", next.bottom)
            shader.setFloatUniform("maxSigma", next.sigma)
            shader.setFloatUniform("fade", if (shader === horizontal) 1f else 0f)
        }
        val created = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(horizontal, "content"),
            RenderEffect.createRuntimeShaderEffect(vertical, "content"),
        ).asComposeRenderEffect()
        params = next
        effect = created
        return created
    }
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

/** Data class: duas quantidades iguais contam como "sem mudança" (ver o derivedStateOf). */
private data class EdgeAmounts(val top: Float, val bottom: Float)

/** Quanto se esbate cada borda (0 a 1): o que já passou para cima e o que ainda falta em baixo. */
private fun ScrollState.edgeAmounts(rampPx: Float): EdgeAmounts {
    if (rampPx <= 0f || maxValue == Int.MAX_VALUE) return EdgeAmounts(0f, 0f)
    return EdgeAmounts(
        top = (value / rampPx).coerceIn(0f, 1f),
        bottom = ((maxValue - value) / rampPx).coerceIn(0f, 1f),
    )
}
