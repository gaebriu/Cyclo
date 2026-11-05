package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloType

/** Uma opção do action-menu-when-to-show; [hint] é o texto pequeno por baixo (só em "Show in selected days"). */
@Immutable
class WhenToShowItem(val label: String, val hint: String? = null)

/**
 * action-menu-when-to-show (Componentes 2, 159:290): o menu da repetição do Introduction 4 e 5.
 *
 * Medidas do Figma (224 x 254 dp):
 * - 24 dp da borda de cima até ao primeiro texto e 24 dp à esquerda;
 * - um texto a cada 42 dp (Inter Medium 15, #FCFCFE): cada opção é uma faixa de 42 dp, com 12 dp
 *   em cima e em baixo do texto;
 * - a opção escolhida tem o visto (15 x 15, #FCFCFE em Luminosity) 9 dp depois do fim do texto e
 *   1 dp abaixo do topo dele;
 * - a dica (Inter Medium 9, #A3A2A7) fica 9 dp por baixo do texto da sua opção, e 24 dp acima da
 *   borda de baixo do menu.
 *
 * O fundo é o [repeatMenuSurface].
 *
 * @param selectedIndex a opção com o visto.
 * @param onSelect a opção tocada.
 */
@Composable
fun WhenToShowMenu(
    items: List<WhenToShowItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val check = remember { PathParser().parsePathString(CycloIconPaths.Check).toPath() }
    Column(
        modifier = modifier
            .width(MenuWidth)
            .repeatMenuSurface()
            .padding(vertical = MenuPadding),
    ) {
        items.forEachIndexed { index, item ->
            WhenToShowRow(
                item = item,
                selected = index == selectedIndex,
                check = check,
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * Uma opção: faixa de 42 dp (62 com a dica), com o conteúdo ao centro: o texto fica 12 dp abaixo do
 * topo da faixa, como no Figma. A altura é mínima e não a soma "12 + texto + 12": a 2,625x cada
 * parcela arredondava para cima e o menu crescia 0,7 dp por opção. Com a letra do sistema grande,
 * a faixa cresce com o texto e guarda [RowMinPadding] em cima e em baixo.
 */
@Composable
private fun WhenToShowRow(item: WhenToShowItem, selected: Boolean, check: Path, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (item.hint != null) RowWithHintHeight else RowHeight)
            .cycloPressable(onClick = onClick, pressedScale = 0.97f, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .padding(start = TextStart, end = TextEnd, top = RowMinPadding, bottom = RowMinPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Sem limite de linhas: com a letra do sistema grande, o texto passa à linha seguinte.
            BasicText(
                text = item.label,
                style = LabelStyle,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (selected) {
                Spacer(Modifier.width(CheckGap))
                CheckIcon(check, modifier = Modifier.padding(top = CheckTop))
            }
        }
        if (item.hint != null) {
            Spacer(Modifier.height(HintGap))
            // O resumo dos dias pode ser comprido: uma linha, com reticências se não couber.
            BasicText(
                text = item.hint,
                style = HintStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** O visto do Figma (check, 15 x 15), #FCFCFE em Luminosity. */
@Composable
private fun CheckIcon(check: Path, modifier: Modifier = Modifier) {
    val blend = LocalVisualEffects.current.luminosity
    Canvas(modifier = modifier.size(CheckSize)) {
        val scale = size.width / CheckViewBox
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            drawPath(path = check, color = CycloColors.TextPrimary, blendMode = blend)
        }
    }
}

// Medidas do Figma (159:290).
private val MenuWidth = 224.dp
private val MenuPadding = 12.dp
private val RowHeight = 42.dp
/** 12 + 18 (texto) + 9 + 11 (dica) + 12. */
private val RowWithHintHeight = 62.dp
private val RowMinPadding = 6.dp
private val TextStart = 24.dp
private val TextEnd = 15.dp
private val CheckGap = 9.dp
private val CheckTop = 1.dp
private val CheckSize = 15.dp
private val HintGap = 9.dp

/**
 * Os textos com a altura de linha das caixas do Figma (18 e 11; a altura natural da Inter é
 * 1,21 em, que arredondava cada faixa para 43 dp). Com a letra do sistema maior, crescem com ela.
 */
private val FigmaLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val LabelStyle: TextStyle = CycloType.MenuItem.copy(lineHeight = 18.sp, lineHeightStyle = FigmaLine)
private val HintStyle: TextStyle = CycloType.MenuHint.copy(lineHeight = 11.sp, lineHeightStyle = FigmaLine)

/** O viewBox do SVG do visto (0 0 15 15). */
private const val CheckViewBox = 15f
