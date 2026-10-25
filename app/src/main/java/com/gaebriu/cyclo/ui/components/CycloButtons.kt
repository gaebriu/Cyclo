package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredStripGlow
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Faixa de cor que fica atrás do vidro e é desfocada com ele (o "purple fx" do news-b):
 * [insetHorizontal] de margem de cada lado, [height] de altura, colada à base do botão.
 *
 * @param blurFigma desfoque do que está atrás do botão, tal como no Figma.
 */
class GlassGlow(
    val color: Color,
    val insetHorizontal: Dp,
    val height: Dp,
    val blurFigma: Float,
)

/** Botão principal roxo (get-started-b). */
@Composable
fun CycloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CycloButtonSurface(
        text = text,
        modifier = modifier,
        onClick = onClick,
        fill = CycloColors.Accent,
        luminosity = false,
        border = { SolidColor(CycloColors.Accent) },
    )
}

/**
 * Botão de vidro (news-b, next-b): fundo #101011 em mistura Luminosity, borda em gradiente
 * e sombra interior.
 *
 * @param onClick nulo = botão só visual, sem ação.
 * @param glow faixa de cor desfocada que se vê através do vidro (nulo = sem brilho).
 */
@Composable
fun CycloGlassButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glow: GlassGlow? = null,
) {
    CycloButtonSurface(
        text = text,
        modifier = modifier,
        onClick = onClick,
        fill = CycloColors.GlassFill,
        luminosity = true,
        border = { size ->
            val edge = CycloColors.GlassBorder
            Brush.verticalGradient(
                0f to edge.copy(alpha = 0.39f),
                0.495f to edge.copy(alpha = 0.24f),
                1f to edge.copy(alpha = 0.39f),
                startY = 0f,
                endY = size.height,
            )
        },
        glow = glow,
    )
}

@Composable
private fun CycloButtonSurface(
    text: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    fill: Color,
    luminosity: Boolean,
    border: (Size) -> Brush,
    glow: GlassGlow? = null,
) {
    val effects = LocalVisualEffects.current

    // Sem estado de toque: o Figma não define nenhum.
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CycloDimens.ButtonHeight)
            .drawBehind {
                val radius = CycloDimens.CornerRadius.toPx()
                val shape = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }

                // 1. O que está atrás do vidro: a faixa de cor desfocada, dentro da forma do botão.
                if (glow != null) {
                    val inset = glow.insetHorizontal.toPx()
                    clipPath(shape) {
                        drawBlurredStripGlow(
                            color = glow.color,
                            left = inset,
                            right = size.width - inset,
                            top = size.height - glow.height.toPx(),
                            sigma = figmaBlurPx(glow.blurFigma),
                        )
                    }
                }

                // 2. Fundo. Sem efeitos, a mistura Luminosity passa a normal.
                val blend = if (luminosity && effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
                drawPath(shape, color = fill, blendMode = blend)

                // 3. Sombra interior: 0, -6, desfoque 15, preto a 15 %.
                drawInnerShadow(
                    shape = shape,
                    color = Color.Black.copy(alpha = 0.15f),
                    offset = Offset(0f, -6.dp.toPx()),
                    cssBlurPx = 15.dp.toPx(),
                )

                // 4. Borda de 1 dp, por dentro.
                val strokeWidth = 1.dp.toPx()
                val inset = strokeWidth / 2f
                val borderShape = Path().apply {
                    addRoundRect(
                        RoundRect(
                            inset,
                            inset,
                            size.width - inset,
                            size.height - inset,
                            CornerRadius(radius - inset),
                        ),
                    )
                }
                drawPath(
                    borderShape,
                    brush = border(size),
                    style = Stroke(width = strokeWidth),
                    blendMode = blend,
                )
            }
            .then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = CycloType.Button)
    }
}
