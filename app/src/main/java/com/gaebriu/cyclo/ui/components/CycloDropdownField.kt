package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Campo que mostra a opção escolhida e abre o menu (when-to-show-b): 345 x 48 dp, raio 15,
 * texto a 15 dp da esquerda e seta de 24 dp a 15 dp da direita.
 *
 * @param description o que o campo faz, para o TalkBack (a opção escolhida vai como estado).
 * @param onBoundsInRoot avisa onde o campo está, para o menu aparecer ancorado a ele.
 */
@Composable
fun CycloDropdownField(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBoundsInRoot: (Rect) -> Unit = {},
) {
    val arrow = remember { PathParser().parsePathString(CycloIconPaths.DropDown).toPath() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CycloDimens.InputHeight)
            // A posição mede-se ANTES do zoom do toque, para o menu não "tremer" ao carregar.
            .onGloballyPositioned { onBoundsInRoot(it.boundsInRoot()) }
            .cycloPressable(onClick = onClick, enabled = enabled)
            .semantics {
                contentDescription = description
                stateDescription = text
            }
            .drawBehind {
                drawRoundRect(
                    color = CycloColors.GlassFill,
                    cornerRadius = CornerRadius(CycloDimens.InputRadius.toPx()),
                )
            },
    ) {
        BasicText(
            text = text,
            style = CycloType.Input,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 15.dp, end = 54.dp),
        )
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 15.dp)
                .size(24.dp),
        ) {
            val pxPerDp = density
            withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                drawPath(path = arrow, color = CycloColors.TextPrimary)
            }
        }
    }
}
