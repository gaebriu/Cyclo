package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import com.gaebriu.cyclo.ui.effects.drawGaussianGlow
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlin.math.sqrt

// Medidas do Figma (frame de 393 x 852).
/** Altura da barra de navegação (navigation-bar: 177 x 72 dp, raio 39). */
val NavigationBarHeight = 72.dp
private val NavigationBarWidth = 177.dp
private val NavigationBarRadius = 39.dp

/** Pílula do botão escolhido (feed-icon-background): 72 x 54 dp, a 9 dp das pontas. */
private val IndicatorWidth = 72.dp
private val IndicatorHeight = 54.dp
private val IndicatorInset = 9.dp

/** Os dois botões da barra: as pílulas estão a 9 e a 96 dp da esquerda. */
private val SecondIndicatorStart = 96.dp

/** Mini-menu (mini-menu-group): 39 dp de altura, raio 24; os ícones de 15 dp estão a 39 dp uns dos outros. */
val MiniMenuTop = 48.dp
private val MiniMenuHeight = 39.dp
private val MiniMenuRadius = 24.dp
private val MiniMenuSlot = 39.dp
private val MiniMenuEdge = 3.dp

/** Faixas escuras do topo (130 dp, preto a 15 %) e do fundo (120 dp, até preto). */
private val TopFadeHeight = 130.dp
private val BottomFadeHeight = 120.dp

// Brilho do topo ("fx background blur"): elipse de 432 x 267 dp com o topo no topo do ecrã,
// gradiente vertical #252C80 (metade de cima) a #34739A, 63 % de preenchimento e 63 % de grupo.
private val GlowRadiusX = 216.dp
private val GlowRadiusY = 133.5.dp
private const val GlowAlpha = 0.63f * 0.63f

/** Média da cor do gradiente da elipse (3/4 de #252C80 e 1/4 de #34739A). */
private val GlowColor = Color(0xFF293E86)

/** Os dois separadores principais. */
enum class MainTab { Feed, Saved }

/**
 * Brilho azul do topo do Feed e das rotinas guardadas, desenhado só com gradientes (ver
 * [drawGaussianGlow]), como o do Introduction 0.
 *
 * @param growth de 0 a 1: nasce do topo e cresce até à forma do design.
 */
@Composable
fun FeedTopGlow(growth: () -> Float, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val g = growth().coerceIn(0f, 1f)
        if (g <= 0f) return@Canvas
        val shape = if (effects.animationsEnabled) g else 1f
        val intensity = if (effects.animationsEnabled) 1f else g
        val sigma = figmaBlurPx(FigmaBlur.FeedGlow)
        val a = GlowRadiusX.toPx() * shape
        val b = GlowRadiusY.toPx() * shape
        val sigmaX = sqrt(sigma * sigma + a * a / 4f)
        val sigmaY = sqrt(sigma * sigma + b * b / 4f)
        drawGaussianGlow(
            color = GlowColor,
            center = Offset(size.width / 2f, b),
            sigmaX = sigmaX,
            sigmaY = sigmaY,
            peakAlpha = GlowAlpha * a * b / (2f * sigmaX * sigmaY) * intensity,
        )
    }
}

/**
 * "fx progressive-blur-top" e "fx progressive-blur-bottom": o conteúdo escurece ao passar por baixo
 * do mini-menu e da barra de navegação. O desfoque progressivo do Figma não se exporta; fica o gradiente.
 */
@Composable
fun MainEdgeFades(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopFadeHeight)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent)))
                },
        )
        Spacer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BottomFadeHeight)
                .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))) },
        )
    }
}

/** Um botão do mini-menu. */
@Immutable
class MiniMenuAction(val iconPath: String, val description: String, val onClick: () -> Unit)

/**
 * Mini-menu do canto de cima à direita (mini-menu-group): ícones de 15 dp a 39 dp uns dos outros.
 * Cada ícone tem uma zona de toque de 48 x 48 dp, centrada nele; como os ícones estão a 39 dp, as
 * zonas das vizinhas sobrepõem-se 9 dp (aí ganha a da direita).
 *
 * @param firstSlot de 0 a 1: quanto do primeiro botão existe. A 0 fica escondido e o menu mais
 *                  curto (a lupa só existe nas rotinas guardadas); pelo meio, alarga e aparece.
 */
@Composable
fun MiniMenu(actions: List<MiniMenuAction>, modifier: Modifier = Modifier, firstSlot: () -> Float = { 1f }) {
    val first = firstSlot().coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(MiniMenuHeight)
            .width(MiniMenuEdge * 2 + MiniMenuSlot * (actions.size - 1) + MiniMenuSlot * first)
            .cycloGlassPill(MiniMenuRadius),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = MiniMenuEdge)
                .offset(y = (MiniMenuHeight - CycloDimens.TouchTarget) / 2),
        ) {
            actions.forEachIndexed { index, action ->
                val amount = if (index == 0) first else 1f
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
 * Barra de navegação (navigation-bar): Feed e rotinas guardadas. A pílula do botão escolhido
 * desliza de um para o outro com uma mola; o ícone escolhido é branco e o outro cinzento.
 */
@Composable
fun MainNavigationBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    feedDescription: String,
    savedDescription: String,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val position by animateFloatAsState(
        targetValue = if (selected == MainTab.Saved) 1f else 0f,
        animationSpec = CycloMotion.NavIndicatorSpring,
        label = "navIndicator",
    )
    Box(
        modifier = modifier
            .size(NavigationBarWidth, NavigationBarHeight)
            .cycloGlassPill(NavigationBarRadius)
            .drawBehind {
                // feed-icon-background: #313233 a 39 % em Luminosity.
                val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
                val x = lerp(IndicatorInset.toPx(), SecondIndicatorStart.toPx(), position)
                drawRoundRect(
                    color = CycloColors.GlassBorder,
                    alpha = 0.39f,
                    topLeft = Offset(x, IndicatorInset.toPx()),
                    size = Size(IndicatorWidth.toPx(), IndicatorHeight.toPx()),
                    cornerRadius = CornerRadius(NavigationBarRadius.toPx()),
                    blendMode = blend,
                )
            },
    ) {
        Row(modifier = Modifier.padding(start = IndicatorInset, top = IndicatorInset)) {
            NavigationItem(
                iconPath = CycloAppIconPaths.Home,
                description = feedDescription,
                amount = { 1f - position },
                isSelected = selected == MainTab.Feed,
                onClick = { onSelect(MainTab.Feed) },
            )
            Spacer(Modifier.width(SecondIndicatorStart - IndicatorInset - IndicatorWidth))
            NavigationItem(
                iconPath = CycloAppIconPaths.Bookmark,
                description = savedDescription,
                amount = { position },
                isSelected = selected == MainTab.Saved,
                onClick = { onSelect(MainTab.Saved) },
            )
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
