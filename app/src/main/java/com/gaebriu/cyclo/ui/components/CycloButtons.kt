package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/** Tamanhos do botão principal: o grande (66 dp) e o compacto das folhas (48 dp). */
enum class CycloButtonSize { Large, Compact }

/** Botão principal roxo (get-started-b; "Done" na folha, em tamanho compacto). */
@Composable
fun CycloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: CycloButtonSize = CycloButtonSize.Large,
    enabled: Boolean = true,
) {
    val style = when (size) {
        CycloButtonSize.Large -> ButtonStyle(
            height = CycloDimens.ButtonHeight,
            cornerRadius = CycloDimens.CornerRadius,
            fill = CycloColors.Accent,
            luminosity = false,
            border = { SolidColor(CycloColors.Accent) },
            innerShadowOffsetY = (-6).dp,
        )
        CycloButtonSize.Compact -> ButtonStyle(
            height = CycloDimens.InputHeight,
            cornerRadius = CycloDimens.InputRadius,
            fill = CycloColors.Accent,
            luminosity = false,
            border = { SolidColor(CycloColors.Accent.copy(alpha = 0.24f)) },
            innerShadowOffsetY = 6.dp,
        )
    }
    CycloButtonSurface(text = text, onClick = onClick, style = style, enabled = enabled, modifier = modifier)
}

/**
 * Botão de vidro (next-b): fundo #101011 em mistura Luminosity, borda em gradiente
 * e sombra interior.
 */
@Composable
fun CycloGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val style = ButtonStyle(
        height = CycloDimens.ButtonHeight,
        cornerRadius = CycloDimens.CornerRadius,
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
        innerShadowOffsetY = (-6).dp,
    )
    CycloButtonSurface(text = text, onClick = onClick, style = style, enabled = enabled, modifier = modifier)
}

/** Tudo o que muda de um botão para outro. */
private class ButtonStyle(
    val height: Dp,
    val cornerRadius: Dp,
    val fill: Color,
    val luminosity: Boolean,
    val border: (Size) -> Brush,
    val innerShadowOffsetY: Dp,
)

@Composable
private fun CycloButtonSurface(
    text: String,
    onClick: () -> Unit,
    style: ButtonStyle,
    enabled: Boolean,
    modifier: Modifier,
) {
    val effects = LocalVisualEffects.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(style.height)
            // O toque (zoom out + háptico) vem ANTES de desenhar, para o desenho também encolher.
            .cycloPressable(onClick = onClick, enabled = enabled)
            .drawBehind {
                val radius = style.cornerRadius.toPx()
                val shape = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }

                // Fundo. Sem efeitos, a mistura Luminosity passa a normal.
                val blend = if (style.luminosity && effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
                drawPath(shape, color = style.fill, blendMode = blend)

                // Sombra interior: 0, ±6, desfoque 15, preto a 15 %.
                drawInnerShadow(
                    shape = shape,
                    color = Color.Black.copy(alpha = 0.15f),
                    offset = Offset(0f, style.innerShadowOffsetY.toPx()),
                    cssBlurPx = 15.dp.toPx(),
                )

                // Borda de 1 dp, por dentro.
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
                    brush = style.border(size),
                    style = Stroke(width = strokeWidth),
                    blendMode = blend,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = CycloType.Button)
    }
}
