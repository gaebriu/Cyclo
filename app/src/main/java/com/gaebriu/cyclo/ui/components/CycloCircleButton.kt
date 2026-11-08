package com.gaebriu.cyclo.ui.components

import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.ui.theme.smoothStep
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion

/**
 * Botão redondo de vidro (close-b, e a seta de voltar nos ecrãs seguintes).
 *
 * O círculo tem 39 dp, como no Figma, mas a zona de toque tem 48 dp (acessibilidade);
 * quem o usa compensa os 4,5 dp de diferença no posicionamento.
 *
 * @param iconPath vetor do ícone numa grelha de 39 x 39 (ver [com.gaebriu.cyclo.ui.icons.CycloIconPaths]).
 * @param secondaryIconPath segundo ícone, que substitui o primeiro por fade cruzado;
 *                          [secondaryIconAmount] vai de 0 (só o primeiro) a 1 (só o segundo).
 */
@Composable
fun CycloCircleButton(
    iconPath: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryIconPath: String? = null,
    secondaryIconAmount: () -> Float = { 0f },
    enabled: Boolean = true,
) {
    val effects = LocalVisualEffects.current
    val icon = remember(iconPath) { PathParser().parsePathString(iconPath).toPath() }
    val secondaryIcon = remember(secondaryIconPath) {
        secondaryIconPath?.let { PathParser().parsePathString(it).toPath() }
    }

    Box(
        modifier = modifier
            .size(CycloDimens.TouchTarget)
            .cycloPressable(onClick = onClick, enabled = enabled, pressedScale = CycloMotion.IconButtonPressScale)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(CycloDimens.CircleButtonSize)) {
            val blend = effects.luminosity
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = 19.dp.toPx()
            val edge = CycloColors.GlassBorder

            drawCircle(color = CycloColors.GlassFill, radius = radius, center = center, blendMode = blend)
            drawCircle(
                brush = Brush.verticalGradient(
                    0f to edge.copy(alpha = 0.63f),
                    0.5f to edge.copy(alpha = 0.39f),
                    1f to edge.copy(alpha = 0.63f),
                    startY = 0f,
                    endY = size.height,
                ),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
                blendMode = blend,
            )

            // O vetor vem em unidades do Figma (1 unidade = 1 dp).
            val pxPerDp = density
            withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                // O ícone muda no sítio, o círculo fica sempre parado. Beta 2.2: o que sai apaga-se
                // na primeira parte e encolhe até [IconSwapScale]; o que entra cresce de lá e acende
                // na segunda (antes cruzavam-se do mesmo tamanho, os dois a meio, sobrepostos).
                // Sem animações, só o fade cruzado.
                val amount = secondaryIconAmount().coerceIn(0f, 1f)
                val pivot = Offset(IconGridCenter, IconGridCenter)
                val animated = effects.animationsEnabled && secondaryIcon != null
                val outAlpha = if (animated) 1f - smoothStep(amount, 0f, IconSwapSplit) else 1f - amount
                val inAlpha = if (animated) smoothStep(amount, 1f - IconSwapSplit, 1f) else amount
                val outScale = if (animated) lerp(1f, IconSwapScale, amount) else 1f
                val inScale = if (animated) lerp(IconSwapScale, 1f, amount) else 1f
                if (outAlpha > 0f) {
                    withTransform({ scale(outScale, outScale, pivot) }) {
                        drawPath(path = icon, color = CycloColors.TextPrimary, alpha = outAlpha)
                    }
                }
                if (secondaryIcon != null && inAlpha > 0f) {
                    withTransform({ scale(inScale, inScale, pivot) }) {
                        drawPath(path = secondaryIcon, color = CycloColors.TextPrimary, alpha = inAlpha)
                    }
                }
            }
        }
    }
}

/** O centro da grelha de 39 x 39 dos ícones (o centro do círculo). */
private const val IconGridCenter = 19.5f

/** Na troca de ícone, o tamanho a que o que sai chega e de onde o que entra parte. */
private const val IconSwapScale = 0.85f

/** O ícone que sai apaga-se até aqui; o que entra acende a partir de 1 - isto. */
private const val IconSwapSplit = 0.6f
