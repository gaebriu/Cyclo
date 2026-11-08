package com.gaebriu.cyclo.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntSize
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.ceil

/*
 * Fundo das superfícies de vidro ("background blur" do Figma e misturas Luminosity).
 *
 * O Android não sabe desfocar "o que está por trás" de um elemento, e uma mistura Luminosity dentro
 * de uma camada à parte (qualquer graphicsLayer com alpha < 1 ou desfoque, ou seja, durante as
 * animações) mistura-se com píxeis transparentes em vez do brilho e dos cartões por baixo: era por
 * isso que o Luminosity só aparecia no fim das animações.
 *
 * A solução é cada superfície levar o seu próprio fundo:
 * - quem está por baixo grava o que desenha numa camada ([backdropSource]);
 * - a superfície desenha essa camada (desfocada ou não), recortada à sua forma, e só depois o seu
 *   preenchimento em Luminosity ([drawBackdrop]). Assim a mistura está sempre certa, mesmo dentro
 *   de camadas a animar, e o desfoque de fundo do Figma sai do mesmo desenho.
 *
 * Uma superfície nunca pode usar uma camada que a contenha (o desenho entrava em ciclo). Por isso
 * cada zona diz, com [LocalBackdrop], que camadas estão por baixo do que tem dentro.
 */

/** Uma camada com o que fica por baixo das superfícies de vidro (ver [backdropSource]). */
@Stable
class BackdropLayer internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null

    /** Muda de cada vez que a camada é gravada de novo: quem a usa volta a desenhar-se. */
    internal var version by mutableIntStateOf(0)
}

/** As camadas por baixo de uma zona, de trás para a frente. */
@Immutable
class Backdrop internal constructor(internal val layers: List<BackdropLayer>) {
    operator fun plus(layer: BackdropLayer): Backdrop = Backdrop(layers + layer)

    companion object {
        val None = Backdrop(emptyList())
        fun of(vararg layers: BackdropLayer) = Backdrop(layers.toList())
    }
}

/** O que está por baixo das superfícies de vidro desta zona. Sem nada, ficam como estavam. */
val LocalBackdrop = compositionLocalOf { Backdrop.None }

@Composable
fun rememberBackdropLayer(): BackdropLayer {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropLayer(layer) }
}

/**
 * Grava o que este elemento desenha em [layer] (e desenha-o normalmente).
 *
 * A fonte tem o seu próprio nó de desenho (a graphicsLayer vazia, sem efeito visível: sem alpha,
 * sem recorte, sem camada fora do ecrã). Sem ele, a fonte desenhava-se no mesmo nó que as
 * superfícies de vidro irmãs (barra de navegação, faixas das bordas): cada vez que a fonte se
 * gravava, a versão mudava, as superfícies voltavam a desenhar-se e, com elas, o nó todo, incluindo
 * a fonte, que se gravava outra vez. Era um ciclo sem fim: com as texturas ligadas, o Feed, as
 * rotinas guardadas e as definições desenhavam frames sem parar, mesmo parados (até beta-1.3).
 * Agora a fonte só se volta a gravar quando o que tem dentro muda.
 */
fun Modifier.backdropSource(layer: BackdropLayer): Modifier = this
    .graphicsLayer { }
    .onPlaced { layer.coordinates = it }
    .drawWithContent {
        layer.layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer.layer)
        // Sem ler o valor: a própria fonte não se volta a desenhar por causa disto.
        Snapshot.withoutReadObservation { layer.version++ }
    }

/** Onde está uma superfície que desenha o fundo, e a camada do desfoque dela. */
@Stable
class BackdropSampler internal constructor(internal val blurLayer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null

    /** Muda quando a superfície se move (ex.: com o scroll), para voltar a recortar o fundo. */
    internal var moves by mutableIntStateOf(0)
    internal var sigma = -1f

    // Onde a superfície estava em relação a cada fonte do fundo (x, y por fonte), no último recorte.
    private var relative = FloatArray(0)

    /**
     * A superfície mudou de lugar EM RELAÇÃO ao que está por baixo dela (Beta 2.4)? Um ecrã inteiro
     * a deslizar, a recuar (zoom) ou a desfocar move a superfície e as fontes juntas: o recorte do
     * fundo fica exatamente igual, e não se volta a gravar nem a desfocar.
     */
    internal fun movedAgainst(backdrop: Backdrop, self: LayoutCoordinates): Boolean {
        val count = backdrop.layers.size * 2
        val changed = relative.size != count
        if (changed) relative = FloatArray(count)
        var moved = changed
        backdrop.layers.forEachIndexed { i, source ->
            val coordinates = source.coordinates?.takeIf { it.isAttached }
            val offset = if (coordinates != null && self.isAttached) coordinates.localPositionOf(self, Offset.Zero) else Offset.Unspecified
            val x = if (offset.isSpecified) offset.x else Float.NaN
            val y = if (offset.isSpecified) offset.y else Float.NaN
            // NaN nunca é igual a si próprio: uma fonte por medir conta sempre como mudança.
            if (relative[i * 2] != x || relative[i * 2 + 1] != y) moved = true
            relative[i * 2] = x
            relative[i * 2 + 1] = y
        }
        return moved
    }
}

@Composable
fun rememberBackdropSampler(): BackdropSampler {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropSampler(layer) }
}

/**
 * Mede onde a superfície está. Vem logo antes do desenho que usa [drawBackdrop].
 *
 * Com [backdrop] (Beta 2.4), o fundo só se volta a recortar quando a superfície se move em relação
 * às fontes dele (o scroll, uma página que desliza por cima do brilho). Antes recortava-se a cada
 * mudança de posição no ecrã: com as definições ou o editor a subir, e o Feed a recuar por baixo
 * (zoom e desfoque), todos os cartões, pílulas e faixas de desfoque gravavam e desfocavam de novo o
 * seu fundo em CADA frame, para chegar à mesma imagem, e a animação perdia frames. O tamanho não
 * conta aqui: quando muda, o desenho refaz-se sozinho.
 */
fun Modifier.backdropSampler(sampler: BackdropSampler, backdrop: Backdrop? = null): Modifier = onGloballyPositioned {
    sampler.coordinates = it
    if (backdrop == null || sampler.movedAgainst(backdrop, it)) sampler.moves++
}

/**
 * Desenha o fundo por baixo desta superfície, recortado a [clip].
 *
 * @param figmaBlur desfoque de fundo do Figma (0 = sem desfoque, só para a mistura Luminosity).
 */
fun DrawScope.drawBackdrop(sampler: BackdropSampler, backdrop: Backdrop, clip: Path, figmaBlur: Float = 0f) {
    if (backdrop.layers.isEmpty()) return
    sampler.moves // relê a posição quando a superfície se move
    val self = sampler.coordinates?.takeIf { it.isAttached } ?: return
    val sigma = if (figmaBlur <= 0f) 0f else figmaBlurPx(figmaBlur)
    clipPath(clip) {
        if (sigma <= 0f) {
            drawLayers(backdrop, self)
        } else {
            val margin = ceil(sigma * 3f)
            val layer = sampler.blurLayer
            if (sampler.sigma != sigma) {
                layer.renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp)
                sampler.sigma = sigma
            }
            val recordSize = IntSize(
                (size.width + margin * 2f).toInt().coerceAtLeast(1),
                (size.height + margin * 2f).toInt().coerceAtLeast(1),
            )
            layer.record(recordSize) {
                translate(margin, margin) { drawLayers(backdrop, self) }
            }
            translate(-margin, -margin) { drawLayer(layer) }
        }
    }
}

/**
 * O fundo desfocado de uma superfície que tem de ser OPACA: [base] por baixo de tudo, as camadas
 * de [backdrop] por cima, e só depois o desfoque, recortado a [clip].
 *
 * As fontes do fundo ([backdropSource]) não têm fundo próprio: gravadas sozinhas, são
 * transparentes à volta do conteúdo, e desfocadas ficam meio transparentes (o texto e os botões
 * espalham-se para o transparente). Desenhadas assim, deixavam ver por baixo o ecrã verdadeiro,
 * nítido: o vidro mostrava duas camadas, a desfocada e a nítida (o Done roxo por trás da folha dos
 * dias, até beta-1.4.2). Com [base] primeiro, o que se desfoca é uma imagem opaca, e o resultado
 * tapa por completo o que está por baixo.
 *
 * @param base a cor que está por baixo das fontes no ecrã (o fundo #010101 da app).
 * @param blurRadius o raio a dar ao [BlurEffect], em píxeis (ver figmaBlurRadiusExact).
 */
fun DrawScope.drawOpaqueBackdrop(
    sampler: BackdropSampler,
    backdrop: Backdrop,
    clip: Path,
    base: Color,
    blurRadius: Float,
) {
    sampler.moves // relê a posição quando a superfície se move
    val self = sampler.coordinates?.takeIf { it.isAttached }
    clipPath(clip) {
        if (self == null || backdrop.layers.isEmpty() || blurRadius <= 0f) {
            drawRect(base)
            if (self != null) drawLayers(backdrop, self)
            return@clipPath
        }
        val layer = sampler.blurLayer
        if (sampler.sigma != blurRadius) {
            layer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
            sampler.sigma = blurRadius
        }
        // A margem guarda o que fica à volta da forma, para a borda desfocar com o que lá está.
        val margin = ceil(blurRadius * 3f)
        val recordSize = IntSize(
            (size.width + margin * 2f).toInt().coerceAtLeast(1),
            (size.height + margin * 2f).toInt().coerceAtLeast(1),
        )
        layer.record(recordSize) {
            drawRect(base)
            translate(margin, margin) { drawLayers(backdrop, self) }
        }
        translate(-margin, -margin) { drawLayer(layer) }
    }
}

/** As camadas de [backdrop] na posição certa em relação a [self]. */
internal fun DrawScope.drawLayers(backdrop: Backdrop, self: LayoutCoordinates) {
    for (source in backdrop.layers) {
        source.version // volta a desenhar quando a camada muda
        val coordinates = source.coordinates?.takeIf { it.isAttached } ?: continue
        val offset = coordinates.localPositionOf(self, Offset.Zero)
        translate(-offset.x, -offset.y) { drawLayer(source.layer) }
    }
}
