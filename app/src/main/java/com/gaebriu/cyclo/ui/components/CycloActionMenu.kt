package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
private val CheckEnd = 36.dp

/**
 * Menu de ações (action-menu-when-to-show): as opções em linhas de 42 dp, com um visto na
 * escolhida. É o menu da repetição do Introduction 4.
 *
 * @param selectedIndex a opção com o visto.
 */
@Composable
fun CycloActionMenu(
    items: List<ActionMenuItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val check = remember { PathParser().parsePathString(CycloIconPaths.Check).toPath() }

    Column(
        modifier = modifier
            .width(MenuWidth)
            .cycloOverlaySurface()
            .padding(top = 12.dp, bottom = 18.dp),
    ) {
        items.forEachIndexed { index, item ->
            ActionMenuRow(
                item = item,
                isSelected = index == selectedIndex,
                check = check,
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (item.hint != null) RowWithHintHeight else RowHeight)
            .cycloPressable(onClick = onClick, pressedScale = 0.97f, role = Role.RadioButton)
            .semantics { selected = isSelected },
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(RowHeight)) {
            BasicText(
                text = item.label,
                style = CycloType.MenuItem,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = TextStart),
            )
            if (isSelected) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = CheckEnd)
                        .size(15.dp),
                ) {
                    val pxPerDp = density
                    withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                        drawPath(path = check, color = CycloColors.TextPrimary)
                    }
                }
            }
        }
        if (item.hint != null) {
            BasicText(
                text = item.hint,
                style = CycloType.MenuHint,
                modifier = Modifier.padding(start = TextStart, top = 39.dp),
            )
        }
    }
}
