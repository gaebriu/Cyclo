package com.gaebriu.cyclo.ui.main

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.cycloGlassPill
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.ProgressiveBlurEdge
import com.gaebriu.cyclo.ui.effects.drawGaussianGlow
import com.gaebriu.cyclo.ui.effects.drawFigmaNoise
import com.gaebriu.cyclo.ui.effects.rememberFigmaNoise
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.sqrt

// Medidas do Figma (frame de 393 x 852).
/** Altura da barra de navegação (navigation-bar, Cyclo-2 2001:124: 264 x 72 dp, raio 39). */
val NavigationBarHeight = 72.dp
private val NavigationBarWidth = 264.dp
private val NavigationBarRadius = 39.dp

/** Pílula do botão escolhido (feed-icon-background): 72 x 54 dp, a 9 dp das pontas. */
private val IndicatorWidth = 72.dp
private val IndicatorHeight = 54.dp
private val IndicatorInset = 9.dp

/** Os três botões da barra (Feed, rotinas guardadas, Tracking): as pílulas estão a 9, 96 e 183 dp da esquerda. */
private val IndicatorPitch = 87.dp

/** Mini-menu (mini-menu-group): 39 dp de altura, raio 24; os ícones de 15 dp estão a 39 dp uns dos outros. */
val MiniMenuTop = 48.dp
private val MiniMenuHeight = 39.dp
private val MiniMenuRadius = 24.dp
private val MiniMenuSlot = 39.dp
private val MiniMenuEdge = 3.dp

/** Faixas escuras do topo (130 dp, preto a 15 %) e do fundo (120 dp, até preto). */
private val TopFadeHeight = 130.dp
private val BottomFadeHeight = 120.dp

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

/** O brilho ao mudar de página ("Fade" do Introduction 0, em pequeno): de onde volta a crescer e a acender. */
private const val GlowPulseShape = 0.86f
private const val GlowPulseIntensity = 0.55f

/** Os três separadores principais, pela ordem das páginas (e da barra de navegação). */
enum class MainTab { Feed, Saved, Tracking }

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
    val noise = rememberFigmaNoise()
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val g = growth().coerceIn(0f, 1f)
                if (g <= 0f) return@drawBehind
                val p = pulse().coerceIn(0f, 1f)
                val shape = if (effects.animationsEnabled) g * lerp(GlowPulseShape, 1f, p) else 1f
                val intensity = (if (effects.animationsEnabled) 1f else g) * lerp(GlowPulseIntensity, 1f, p)
                val sigma = figmaBlurPx(FigmaBlur.FeedGlow)
                val a = GlowRadiusX.toPx() * shape
                val b = GlowRadiusY.toPx() * shape
                val sigmaX = sqrt(sigma * sigma + a * a / 4f)
                val sigmaY = sqrt(sigma * sigma + b * b / 4f)
                // Máscara: a mancha desfocada, com a opacidade do grupo.
                drawGaussianGlow(
                    color = Color.White,
                    center = Offset(size.width / 2f, b),
                    sigmaX = sigmaX,
                    sigmaY = sigmaY,
                    peakAlpha = GlowGroupAlpha * a * b / (2f * sigmaX * sigmaY) * intensity,
                )
                // Cor: o gradiente vertical da elipse (topo da elipse ao fundo dela).
                val s = saved().coerceIn(0f, 1f)
                val c = sky()
                val edgeFill = lerp(FeedFill, SavedFillEdge, s)
                val middleFill = lerp(FeedFill, SavedFillMiddle, s)
                val fill = Brush.verticalGradient(
                    0f to c.top.copy(alpha = edgeFill),
                    0.5f to c.top.copy(alpha = middleFill),
                    1f to c.base.copy(alpha = edgeFill),
                    startY = 0f,
                    endY = b * 2f,
                )
                drawRect(brush = fill, blendMode = BlendMode.SrcIn)
                // Noise do Figma (Cyclo-2: MONOTONE 2, #FCFCFE a 39 %), só dentro do brilho: a opacidade
                // da elipse desfocada (a mancha vezes o preenchimento) recorta o grão. É textura:
                // desligada com as "Advanced textures".
                if (effects.textures) {
                    drawFigmaNoise(noise.value) {
                        drawGaussianGlow(
                            color = Color.White,
                            center = Offset(size.width / 2f, b),
                            sigmaX = sigmaX,
                            sigmaY = sigmaY,
                            peakAlpha = GlowGroupAlpha * a * b / (2f * sigmaX * sigmaY) * intensity,
                        )
                        drawRect(brush = fill, blendMode = BlendMode.DstIn)
                    }
                }
            },
    )
}

/**
 * "fx progressive-blur-top" e "fx progressive-blur-bottom": o conteúdo desfoca-se (15 na borda do
 * ecrã até 0) e escurece ao passar por baixo do topo (preto a 15 %) e do fundo (até preto).
 * Usa o que está por baixo ([com.gaebriu.cyclo.ui.effects.LocalBackdrop]).
 *
 * @param topExtra quanto a barra de estado do aparelho é mais alta do que os 24 dp do Figma: a
 *                 faixa de cima cresce o mesmo, para acabar no mesmo sítio em relação ao conteúdo.
 * @param bottomAmount de 0 a 1: quanto se vê da faixa de baixo (ex.: some no fim do scroll).
 */
@Composable
fun MainEdgeFades(
    modifier: Modifier = Modifier,
    topExtra: Dp = 0.dp,
    bottomAmount: () -> Float = { 1f },
) {
    Box(modifier = modifier.fillMaxSize()) {
        ProgressiveBlurEdge(
            top = true,
            height = TopFadeHeight + topExtra,
            figmaBlur = FigmaBlur.EdgeProgressive,
            shade = Color.Black.copy(alpha = 0.15f),
        )
        ProgressiveBlurEdge(
            top = false,
            height = BottomFadeHeight,
            figmaBlur = FigmaBlur.EdgeProgressive,
            shade = Color.Black,
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
 * Só com o último botão (a roda dentada, na Tracking, 2001:255) é um círculo de 39 dp: as pontas de
 * 3 dp somem com os outros botões.
 *
 * @param slot de 0 a 1, para cada botão: quanto dele existe. A 0 fica escondido e o menu mais curto
 *             (a lupa só existe nas rotinas guardadas, o "+" não existe na Tracking); pelo meio,
 *             alarga e aparece. Segue a posição contínua das páginas.
 */
@Composable
fun MiniMenu(actions: List<MiniMenuAction>, modifier: Modifier = Modifier, slot: (Int) -> Float = { 1f }) {
    val amounts = actions.indices.map { slot(it).coerceIn(0f, 1f) }
    // As pontas de 3 dp só existem com mais de um botão (sozinha, a roda dentada é um círculo).
    val edge = amounts.dropLast(1).maxOrNull() ?: 0f
    Box(
        modifier = modifier
            .height(MiniMenuHeight)
            .width(MiniMenuEdge * 2 * edge + MiniMenuSlot * amounts.sum())
            .cycloGlassPill(MiniMenuRadius, FigmaBlur.MiniMenu),
        contentAlignment = Alignment.CenterStart,
    ) {
        // A fila tem a altura das zonas de toque (48 dp) e fica centrada na pílula de 39 dp. Antes
        // ficava cortada a 39 dp e deslocada, e os ícones apareciam 4,5 dp acima do centro.
        Row(
            modifier = Modifier
                .padding(horizontal = MiniMenuEdge * edge)
                .requiredHeight(CycloDimens.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                val amount = amounts[index]
                if (amount <= 0f) return@forEachIndexed
                Box(
                    modifier = Modifier.size(width = MiniMenuSlot * amount, height = CycloDimens.TouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true)
                            .size(CycloDimens.TouchTarget)
                            .then(
                                if (amount > 0.5f) {
                                    Modifier
                                        .cycloPressable(onClick = action.onClick, pressedScale = CycloMotion.IconButtonPressScale)
                                        .semantics { contentDescription = action.description }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CycloIcon(path = action.iconPath, size = 15.dp, tint = CycloColors.TextPrimary, alpha = { amount })
                    }
                }
            }
        }
    }
}

/**
 * Barra de navegação (navigation-bar, Cyclo-2 2001:124): Feed, rotinas guardadas e Tracking. A
 * pílula do botão escolhido acompanha a página: segue o dedo durante o swipe e a mola da página ao
 * tocar; o ícone escolhido é branco e os outros cinzentos.
 *
 * @param pagePosition de 0 (Feed) a 2 (Tracking), a posição contínua das páginas.
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
                val between = p - kotlin.math.floor(p)
                val stretch = if (effects.animationsEnabled) kotlin.math.sin(between * kotlin.math.PI.toFloat()) else 0f
                val width = IndicatorWidth.toPx() * (1f + IndicatorStretchX * stretch)
                val height = IndicatorHeight.toPx() * (1f - IndicatorSquashY * stretch)
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
                        MainTab.Tracking -> CycloAppIconPaths.BarChart
                    },
                    description = descriptions[tab.ordinal],
                    // O ícone acende à medida que a pílula chega a ele.
                    amount = { (1f - kotlin.math.abs(pagePosition() - tab.ordinal)).coerceIn(0f, 1f) },
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
