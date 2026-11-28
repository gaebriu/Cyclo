package com.gaebriu.cyclo.ui.main

import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.theme.smoothStep
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.cycloGlassPill
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.ProgressiveBlurEdge
import com.gaebriu.cyclo.ui.effects.gaussianGlowShader
import com.gaebriu.cyclo.ui.effects.rememberFigmaNoise
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Medidas do Figma (frame de 393 x 852).
/**
 * Altura da barra de navegação (navigation-bar do Feed, 2066:395: 177 x 72 dp, raio 39, dois botões;
 * a de três botões, com a Tracking, saiu na Cyclo 3.1).
 */
val NavigationBarHeight = 72.dp
private val NavigationBarWidth = 177.dp
private val NavigationBarRadius = 39.dp

/** Pílula do botão escolhido (feed-icon-background): 72 x 54 dp, a 9 dp das pontas. */
private val IndicatorWidth = 72.dp
private val IndicatorHeight = 54.dp
private val IndicatorInset = 9.dp

/** Os dois botões da barra (Feed, rotinas guardadas): as pílulas estão a 9 e 96 dp da esquerda. */
private val IndicatorPitch = 87.dp

/** Mini-menu (mini-menu-group): 39 dp de altura, raio 24; os ícones de 15 dp estão a 39 dp uns dos outros. */
val MiniMenuTop = 48.dp
private val MiniMenuHeight = 39.dp
private val MiniMenuRadius = 24.dp
private val MiniMenuSlot = 39.dp
private val MiniMenuEdge = 3.dp

/** A lupa a entrar no mini-menu: cresce deste tamanho (a partir de 10 % do lugar aberto) e acende a partir de 45 %. */
private const val SlotIconFromScale = 0.55f
private const val SlotIconGrowFrom = 0.1f
private const val SlotIconAlphaFrom = 0.45f

/**
 * Faixas escuras do topo e do fundo (Cyclo 3, beta-3.2): no Feed e nas rotinas guardadas, 87 dp até #010101 a 39 % em cima e 120 dp até #010101 em baixo (antes 130 dp, preto a
 * 15 %, e preto). As definições têm as suas ([MainEdgeFades] com outros valores).
 */
private val TopFadeHeight = 87.dp
private val BottomFadeHeight = 120.dp
private const val TopFadeAlpha = 0.39f

// Brilho do topo ("fx background blur"): elipse de 432 x 267 dp com o topo no topo do ecrã e
// 63 % de grupo. O gradiente vertical vai da cor de cima (até meio da elipse) à da base.
// - Feed: preenchimento a 63 % nas três paragens.
// - Rotinas guardadas: 24 %, 39 % a meio e 24 % (o gradiente complementar, mais leve).
private val GlowRadiusX = 216.dp
private val GlowRadiusY = 133.5.dp
private const val GlowGroupAlpha = 0.63f
private const val FeedFill = 0.63f
private const val SavedFillEdge = 0.24f
private const val SavedFillMiddle = 0.39f

// Cyclo 3.2.5, só no Feed (6095:50, a mesma do Introduction 0): por cima da luz, uma sombra de
// 267 x 165 dp com o topo no topo do ecrã, preto de 39 % (em cima) a 100 % (em baixo), desfoque 165.
// Nas rotinas guardadas não existe: apaga-se com o deslize para lá.
private val ShadeRadiusX = 133.5.dp
private val ShadeRadiusY = 82.5.dp
private const val ShadeFigmaBlur = 165f
private val ShadeTop = Color.Black.copy(alpha = 0.39f).toArgb()
private val ShadeBottom = Color.Black.toArgb()

/** O perfil gaussiano do brilho acaba a 3,2 desvios-padrão do centro (GlowReach de GaussianGlow.kt). */
private const val GlowReachSigmas = 3.2f

/** O brilho ao mudar de página ("Fade" do Introduction 0, em pequeno): de onde volta a crescer e a acender. */
private const val GlowPulseShape = 0.86f
private const val GlowPulseIntensity = 0.55f

/** Os separadores principais, pela ordem das páginas (e da barra de navegação). */
enum class MainTab { Feed, Saved }

/**
 * Brilho do topo do Feed e das rotinas guardadas, desenhado só com gradientes (ver
 * [drawGaussianGlow]), como o do Introduction 0. A mancha desfocada serve de máscara e o gradiente
 * de cor do Figma pinta-a (camada própria, para a máscara só afetar o brilho).
 *
 * @param growth de 0 a 1: nasce do topo e cresce até à forma do design (entrada do Feed).
 * @param pulse de 0 a 1: ao mudar de página, o brilho volta a crescer e a acender um pouco.
 * @param sky as cores da hora atual (ver [skyColorsAt]).
 * @param saved de 0 (Feed) a 1 (rotinas guardadas): passa de um gradiente para o complementar.
 */
@Composable
fun FeedTopGlow(
    growth: () -> Float,
    pulse: () -> Float,
    sky: () -> SkyColors,
    saved: () -> Float,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    // O grão do Figma: tamanho 1 no Feed, 2 nas rotinas guardadas (Cyclo 3.2.5).
    val fineTile = rememberFigmaNoise(grainSize = 1).value
    val coarseTile = rememberFigmaNoise(grainSize = 2).value
    // As telhas como shaders, por cima do pixel 0,0 do brilho (como o drawRect de drawFigmaNoise).
    val fineNoise = remember(fineTile) { fineTile?.let { BitmapShader(it.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) } }
    val coarseNoise = remember(coarseTile) { coarseTile?.let { BitmapShader(it.asAndroidBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) } }
    val paint = remember { Paint().apply { filterQuality = FilterQuality.Low } }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            // O grupo a 63 % (o Figma junta a luz e a sombra antes da opacidade).
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = GlowGroupAlpha
            }
            .drawBehind {
                val g = growth().coerceIn(0f, 1f)
                if (g <= 0f) return@drawBehind
                val p = pulse().coerceIn(0f, 1f)
                val shape = if (effects.animationsEnabled) g * lerp(GlowPulseShape, 1f, p) else 1f
                val intensity = (if (effects.animationsEnabled) 1f else g) * lerp(GlowPulseIntensity, 1f, p)
                val s = saved().coerceIn(0f, 1f)
                val framework = paint.asFrameworkPaint()
                // Até onde o brilho chega (px): a área desenhada acaba aí, não no fundo do ecrã.
                var reach = 0f
                // Uma elipse: máscara (a mancha desfocada) × cor (o gradiente vertical dela). No
                // ComposeShader do Android, o primeiro é o destino e o segundo a fonte.
                fun ellipse(halfW: Float, halfH: Float, figmaBlur: Float, peak: Float, fill: (Float) -> Shader): Shader? {
                    if (peak <= 0f) return null
                    val sigma = figmaBlurPx(figmaBlur)
                    val a = halfW * shape
                    val b = halfH * shape
                    val sigmaX = sqrt(sigma * sigma + a * a / 4f)
                    val sigmaY = sqrt(sigma * sigma + b * b / 4f)
                    val mask = gaussianGlowShader(
                        center = Offset(size.width / 2f, b),
                        sigmaX = sigmaX,
                        sigmaY = sigmaY,
                        peakAlpha = a * b / (2f * sigmaX * sigmaY) * intensity * peak,
                    ) ?: return null
                    reach = maxOf(reach, b + GlowReachSigmas * sigmaY)
                    return ComposeShader(mask, fill(b), android.graphics.BlendMode.SRC_IN)
                }
                // A luz: o gradiente vertical da elipse (topo da elipse ao fundo dela), da hora atual.
                val c = sky()
                val edgeFill = lerp(FeedFill, SavedFillEdge, s)
                val middleFill = lerp(FeedFill, SavedFillMiddle, s)
                val light = ellipse(GlowRadiusX.toPx(), GlowRadiusY.toPx(), FigmaBlur.FeedGlow, 1f) { b ->
                    LinearGradient(
                        0f, 0f, 0f, b * 2f,
                        intArrayOf(c.top.copy(alpha = edgeFill).toArgb(), c.top.copy(alpha = middleFill).toArgb(), c.base.copy(alpha = edgeFill).toArgb()),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                // A sombra (só no Feed): preto de 39 % a 100 %, por cima da luz.
                val shade = ellipse(ShadeRadiusX.toPx(), ShadeRadiusY.toPx(), ShadeFigmaBlur, 1f - s) { b ->
                    LinearGradient(0f, 0f, 0f, b * 2f, ShadeTop, ShadeBottom, Shader.TileMode.CLAMP)
                }
                val color: Shader = when {
                    light != null && shade != null -> ComposeShader(light, shade, android.graphics.BlendMode.SRC_OVER)
                    else -> light ?: shade ?: return@drawBehind
                }
                // O grão do Figma, recortado pelo brilho e por cima dele (textura: sem as "Advanced
                // textures", sem grão). Parado (só um grão), tudo num só desenho, como desde a 3.2.1;
                // só no deslize Feed ⇄ Saved o grão de cada página entra num desenho à parte.
                val rest = if (s <= 0f) fineNoise else if (s >= 1f) coarseNoise else null
                framework.shader = if (effects.textures && rest != null) {
                    ComposeShader(color, ComposeShader(color, rest, android.graphics.BlendMode.SRC_IN), android.graphics.BlendMode.SRC_OVER)
                } else {
                    color
                }
                val bottom = minOf(reach, size.height)
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, bottom, framework) }
                if (effects.textures && rest == null) {
                    if (fineNoise != null) {
                        framework.shader = ComposeShader(color, fineNoise, android.graphics.BlendMode.SRC_IN)
                        framework.alpha = ((1f - s) * 255f).roundToInt()
                        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, bottom, framework) }
                    }
                    if (coarseNoise != null) {
                        framework.shader = ComposeShader(color, coarseNoise, android.graphics.BlendMode.SRC_IN)
                        framework.alpha = (s * 255f).roundToInt()
                        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, bottom, framework) }
                    }
                    framework.alpha = 255
                }
                framework.shader = null
            },
    )
}

/**
 * "fx progressive-blur-top" e "fx progressive-blur-bottom": o conteúdo desfoca-se (24 na borda do
 * ecrã até 0) e escurece ao passar por baixo do topo (até #010101 a [topAlpha]) e do fundo (até #010101).
 * Usa o que está por baixo ([com.gaebriu.cyclo.ui.effects.LocalBackdrop]).
 *
 * @param topExtra quanto a barra de estado do aparelho é mais alta do que os 24 dp do Figma: a
 *                 faixa de cima cresce o mesmo, para acabar no mesmo sítio em relação ao conteúdo.
 * @param bottomAmount de 0 a 1: quanto se vê da faixa de baixo (ex.: some no fim do scroll).
 * @param topAlpha a opacidade do #010101 na borda de cima (as definições: 100 %).
 * @param bottomHeight a altura da faixa de baixo (as definições: 48 dp).
 */
@Composable
fun MainEdgeFades(
    modifier: Modifier = Modifier,
    topExtra: Dp = 0.dp,
    bottomAmount: () -> Float = { 1f },
    topAlpha: Float = TopFadeAlpha,
    bottomHeight: Dp = BottomFadeHeight,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ProgressiveBlurEdge(
            top = true,
            height = TopFadeHeight + topExtra,
            figmaBlur = FigmaBlur.EdgeProgressive,
            shade = CycloColors.Background.copy(alpha = topAlpha),
        )
        ProgressiveBlurEdge(
            top = false,
            height = bottomHeight,
            figmaBlur = FigmaBlur.EdgeProgressive,
            shade = CycloColors.Background,
            modifier = Modifier.align(Alignment.BottomCenter),
            amount = bottomAmount,
        )
    }
}

/** Um botão do mini-menu. */
@Immutable
class MiniMenuAction(val iconPath: String, val description: String, val onClick: () -> Unit)

/**
 * Mini-menu do canto de cima à direita (mini-menu-group): 39 dp de altura, ícones de 15 dp a 15 dp
 * das pontas e a 39 dp uns dos outros, centrados na altura (a 12 dp do topo).
 * Cada ícone tem uma zona de toque de 48 x 48 dp, centrada nele; como os ícones estão a 39 dp, as
 * zonas das vizinhas sobrepõem-se 9 dp (aí ganha a da direita). As zonas saem 4,5 dp por cima e
 * por baixo da pílula, sem a empurrar.
 *
 * Só com o último botão (a roda dentada) é um círculo de 39 dp: as pontas de 3 dp somem com os
 * outros botões.
 *
 * Desempenho (Cyclo 3.1): [slot] lê-se só na medida e no desenho. Antes lia-se na composição, e o
 * mini-menu inteiro voltava a compor-se em cada fotograma do deslize Feed ↔ Saved; agora só se
 * recompõe quando um botão aparece, desaparece ou passa a responder ao toque.
 *
 * @param slot de 0 a 1, para cada botão: quanto dele existe. A 0 fica escondido e o menu mais curto
 *             (a lupa só existe nas rotinas guardadas); pelo meio, alarga e aparece. Segue a posição
 *             contínua das páginas.
 */
@Composable
fun MiniMenu(actions: List<MiniMenuAction>, modifier: Modifier = Modifier, slot: (Int) -> Float = { 1f }) {
    val latestSlot by rememberUpdatedState(slot)
    val amount: (Int) -> Float = remember { { index -> latestSlot(index).coerceIn(0f, 1f) } }
    // Cyclo 3.2.3: o lugar abre com uma curva suave (devagar a começar e a acabar), sempre preso à
    // posição das páginas (o dedo continua a mandar). Antes abria em linha reta e a lupa, do tamanho
    // inteiro e meio apagada, ficava por cima do "+" enquanto o lugar ainda abria.
    val open: (Int) -> Float = remember { { index -> smoothStep(amount(index)) } }
    // As pontas de 3 dp só existem com mais de um botão (sozinha, a roda dentada é um círculo).
    val edge: () -> Float = remember(actions.size) {
        {
            var max = 0f
            for (i in 0 until actions.size - 1) max = maxOf(max, open(i))
            max
        }
    }
    Box(
        modifier = modifier
            .layout { measurable, _ ->
                var slots = 0f
                for (i in actions.indices) slots += open(i)
                val width = (MiniMenuEdge * 2 * edge() + MiniMenuSlot * slots).roundToPx()
                val height = MiniMenuHeight.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .cycloGlassPill(MiniMenuRadius, FigmaBlur.MiniMenu),
        contentAlignment = Alignment.CenterStart,
    ) {
        // A fila tem a altura das zonas de toque (48 dp) e fica centrada na pílula de 39 dp. Antes
        // ficava cortada a 39 dp e deslocada, e os ícones apareciam 4,5 dp acima do centro.
        Row(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val inset = (MiniMenuEdge * edge()).roundToPx()
                    val placeable = measurable.measure(constraints.offset(horizontal = -inset * 2))
                    layout(placeable.width + inset * 2, placeable.height) { placeable.place(inset, 0) }
                }
                .requiredHeight(CycloDimens.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                // Um grupo por botão (sem "return" a meio: os remember de cada um ficam no sítio).
                key(index) {
                    val shown by remember { derivedStateOf { amount(index) > 0f } }
                    val responds by remember { derivedStateOf { amount(index) > 0.5f } }
                    if (shown) {
                        Box(
                            modifier = Modifier.layout { measurable, _ ->
                                val width = (MiniMenuSlot * open(index)).roundToPx()
                                val height = CycloDimens.TouchTarget.roundToPx()
                                val placeable = measurable.measure(Constraints.fixed(width, height))
                                layout(width, height) { placeable.place(0, 0) }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth(unbounded = true)
                                    .size(CycloDimens.TouchTarget)
                                    .then(
                                        if (responds) {
                                            Modifier
                                                .cycloPressable(onClick = action.onClick, pressedScale = CycloMotion.IconButtonPressScale)
                                                .semantics { contentDescription = action.description }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                // O ícone cresce com o lugar e só acende quando já tem espaço (lido
                                // no desenho: o deslize não recompõe o mini-menu).
                                CycloIcon(
                                    path = action.iconPath,
                                    size = 15.dp,
                                    tint = CycloColors.TextPrimary,
                                    alpha = { smoothStep(amount(index), SlotIconAlphaFrom, 1f) },
                                    modifier = Modifier.graphicsLayer {
                                        val s = lerp(SlotIconFromScale, 1f, smoothStep(amount(index), SlotIconGrowFrom, 1f))
                                        scaleX = s
                                        scaleY = s
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Barra de navegação (navigation-bar do Feed, 2066:395): Feed e rotinas guardadas. A pílula do botão
 * escolhido acompanha a página: segue o dedo durante o swipe e a mola da página ao tocar; o ícone
 * escolhido é branco e o outro cinzento.
 *
 * @param pagePosition de 0 (Feed) a 1 (rotinas guardadas), a posição contínua das páginas.
 * @param descriptions o nome de cada separador (TalkBack), pela ordem de [MainTab].
 */
@Composable
fun MainNavigationBar(
    selected: MainTab,
    pagePosition: () -> Float,
    onSelect: (MainTab) -> Unit,
    descriptions: List<String>,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val stretch: () -> Float = { pageStretch(pagePosition()) }
    val iconAmount: (Int) -> Float = { index -> (1f - kotlin.math.abs(pagePosition() - index)).coerceIn(0f, 1f) }
    Box(
        modifier = modifier
            .size(NavigationBarWidth, NavigationBarHeight)
            .cycloGlassPill(NavigationBarRadius)
            .drawBehind {
                // feed-icon-background: #313233 a 39 % em Luminosity.
                val blend = effects.luminosity
                val p = pagePosition().coerceIn(0f, (MainTab.entries.size - 1).toFloat())
                val x = IndicatorInset.toPx() + IndicatorPitch.toPx() * p
                // A meio caminho entre dois separadores, a pílula estica-se na largura e afina na
                // altura (uma gota a passar de um lado para o outro); nos separadores tem a forma do
                // Figma. Segue a mesma posição contínua (dedo, toque, voltar), separador a separador.
                val drop = if (effects.animationsEnabled) stretch() else 0f
                val width = IndicatorWidth.toPx() * (1f + IndicatorStretchX * drop)
                val height = IndicatorHeight.toPx() * (1f - IndicatorSquashY * drop)
                val left = x + (IndicatorWidth.toPx() - width) / 2f
                val top = IndicatorInset.toPx() + (IndicatorHeight.toPx() - height) / 2f
                drawRoundRect(
                    color = CycloColors.GlassBorder,
                    alpha = 0.39f,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(NavigationBarRadius.toPx()),
                    blendMode = blend,
                )
            },
    ) {
        Row(modifier = Modifier.padding(start = IndicatorInset, top = IndicatorInset)) {
            MainTab.entries.forEach { tab ->
                if (tab.ordinal > 0) Spacer(Modifier.width(IndicatorPitch - IndicatorWidth))
                NavigationItem(
                    iconPath = when (tab) {
                        MainTab.Feed -> CycloAppIconPaths.Home
                        MainTab.Saved -> CycloAppIconPaths.Bookmark
                    },
                    description = descriptions[tab.ordinal],
                    // O ícone acende à medida que a pílula chega a ele.
                    amount = { iconAmount(tab.ordinal) },
                    isSelected = selected == tab,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    iconPath: String,
    description: String,
    amount: () -> Float,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(IndicatorWidth, IndicatorHeight)
            .cycloPressable(onClick = onClick, pressedScale = CycloMotion.IconButtonPressScale, role = Role.Tab)
            .semantics {
                contentDescription = description
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        // Cinzento por baixo e branco por cima, com a opacidade a seguir a pílula.
        CycloIcon(path = iconPath, size = 24.dp, tint = CycloColors.TextSecondary)
        CycloIcon(path = iconPath, size = 24.dp, tint = CycloColors.TextPrimary, alpha = amount)
    }
}

/** Espaço a reservar por baixo do conteúdo para a barra de navegação não o tapar no fim do scroll. */
fun navigationBarReserve(bottomInset: Dp): Dp = bottomInset + NavigationBarHeight + CycloDimens.ScreenMargin

/** Quanto a pílula da barra de navegação estica na largura a meio da passagem entre separadores. */
private const val IndicatorStretchX = 0.22f

/** Quanto a mesma pílula afina na altura a meio da passagem (o volume parece manter-se). */
private const val IndicatorSquashY = 0.08f
