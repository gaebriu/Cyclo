package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloType

/** Uma opção do menu; [hint] é o texto pequeno por baixo (ex.: "Choose days"). */
@Immutable
class ActionMenuItem(val label: String, val hint: String? = null)

// Medidas do Figma (action-menu-when-to-show: 224 x 254 dp).
private val MenuWidth = 224.dp
private val RowHeight = 42.dp
private val RowWithHintHeight = 56.dp
private val TextStart = 24.dp
private val TextEnd = 15.dp
private val CheckGap = 9.dp

/**
 * Menu de ações, em duas formas do Figma:
 * - action-menu-when-to-show (224 dp de largura): as opções com um visto na escolhida. É o menu da
 *   repetição do Introduction 4.
 * - action-menu ("Edit" / "Remove"): sem escolha ([selectedIndex] = -1), com a largura do texto
 *   mais 24 dp de cada lado.
 *
 * @param selectedIndex a opção com o visto; -1 para um menu de ações simples.
 */
@Composable
fun CycloActionMenu(
    items: List<ActionMenuItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val check = remember { PathParser().parsePathString(CycloIconPaths.Check).toPath() }
    val isChoice = selectedIndex >= 0

    Column(
        modifier = modifier
            .then(if (isChoice) Modifier.width(MenuWidth) else Modifier.width(IntrinsicSize.Max))
            .cycloOverlaySurface()
            .padding(top = 12.dp, bottom = if (isChoice) 18.dp else 12.dp),
    ) {
        items.forEachIndexed { index, item ->
            ActionMenuRow(
                item = item,
                isSelected = index == selectedIndex,
                check = check,
                textEnd = if (isChoice) TextEnd else TextStart,
                role = if (isChoice) Role.RadioButton else Role.Button,
                onClick = { onItemClick(index) },
            )
        }
    }
}

@Composable
private fun ActionMenuRow(
    item: ActionMenuItem,
    isSelected: Boolean,
    check: Path,
    textEnd: Dp,
    role: Role,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (item.hint != null) RowWithHintHeight else RowHeight)
            .cycloPressable(onClick = onClick, pressedScale = 0.97f, role = role)
            .then(if (role == Role.RadioButton) Modifier.semantics { selected = isSelected } else Modifier),
    ) {
        // O texto e, na opção escolhida, o visto 9 dp a seguir ao fim do texto (como no Figma).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .padding(start = TextStart, end = textEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = item.label,
                style = CycloType.MenuItem,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isSelected) {
                Spacer(Modifier.width(CheckGap))
                Canvas(modifier = Modifier.size(15.dp)) {
                    val pxPerDp = density
                    withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                        drawPath(path = check, color = CycloColors.TextPrimary)
                    }
                }
            }
        }
        if (item.hint != null) {
            // Nunca passa da margem de 15 dp da caixa: o que não couber acaba em reticências.
            BasicText(
                text = item.hint,
                style = CycloType.MenuHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = TextStart, end = TextEnd, top = 39.dp),
            )
        }
    }
}
