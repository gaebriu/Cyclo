package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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
 * @param singleLine (Beta 2.4, Introduction 5) o texto fica sempre numa linha, como no Figma (13:163,
 *        whitespace-nowrap) e, se não couber, é cortado com reticências antes da seta ("Mostrar só
 *        desta…"), em vez de passar à linha seguinte. O espaço do texto é o campo menos 15 dp à
 *        esquerda, 8 dp até à seta, a seta (24 dp) e 15 dp à direita: no campo de 215 dp do Figma
 *        (ecrã de 393 dp), 153 dp. O TalkBack lê sempre a opção inteira (stateDescription).
 * @param onBoundsInRoot avisa onde o campo está, para o menu aparecer ancorado a ele. É chamado
 *        no momento do toque, logo antes de [onClick], com a posição atual no ecrã. Assim só o
 *        campo tocado define a âncora, mesmo havendo outro campo igual numa página por baixo
 *        (o do Introduction 4 continua composto, deslocado, enquanto se vê o Introduction 5).
 */
@Composable
fun CycloDropdownField(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    onBoundsInRoot: (Rect) -> Unit = {},
) {
    val arrow = remember { PathParser().parsePathString(CycloIconPaths.DropDown).toPath() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            // A posição mede-se ANTES do zoom do toque, para o menu não "tremer" ao carregar.
            .onGloballyPositioned { coordinates[0] = it }
            .cycloPressable(
                onClick = {
                    coordinates[0]?.takeIf { it.isAttached }?.let { onBoundsInRoot(it.boundsInRoot()) }
                    onClick()
                },
                enabled = enabled,
            )
            .semantics {
                contentDescription = description
                stateDescription = text
            }
            .drawBehind {
                drawPath(smoothRoundRectPath(size.width, size.height, CycloDimens.InputRadius.toPx()), CycloColors.GlassFill)
            },
    ) {
        BasicText(
            text = text,
            style = CycloType.Input,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.CenterStart)
                // Numa linha fica com os 48 dp do Figma; com o texto em duas linhas (ecrã estreito ou letra
                // grande) o campo cresce, em vez de o texto sair dele.
                // À direita: a seta (24 dp a 15 dp da borda) e pelo menos 8 dp entre o texto e ela. No
                // Figma o texto abraça o conteúdo e a seta fica encostada à direita (campo de 215 dp num
                // ecrã de 393 dp); com 15 dp de folga, "Show just this once" já quebrava a 384 dp.
                .padding(start = 15.dp, end = 15.dp + 24.dp + 8.dp, top = 12.dp, bottom = 12.dp),
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
