package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloType

/** Uma opção do menu. */
@Immutable
class ActionMenuItem(val label: String)

// Medidas do Figma (action-menu, 80:727, e os compactos de Componentes 3).
private val RowHeight = 42.dp
private val TextStart = 24.dp
private val CheckGap = 9.dp

/**
 * Menu de ações, em duas formas do Figma:
 * - action-menu ("Edit" / "Remove"): sem escolha ([selectedIndex] = -1), com a largura do texto
 *   mais 24 dp de cada lado.
 * - Com [compact] (action-menu-chronotype, -time-format, -time-to-fall-asleep, Componentes 3):
 *   escolha com a largura da opção mais comprida, 24 dp à esquerda, o lugar do visto (9 + 15 dp)
 *   em todas as linhas e 24 dp à direita; 12 dp em cima e em baixo. Ex.: o do formato tem 132 x 108.
 *
 * O menu da repetição (action-menu-when-to-show) é outro componente: [WhenToShowMenu].
 *
 * @param selectedIndex a opção com o visto ([compact]); -1 para um menu de ações simples.
 */
@Composable
fun CycloActionMenu(
    items: List<ActionMenuItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        val check = remember { PathParser().parsePathString(CycloIconPaths.Check).toPath() }
        Column(
            modifier = modifier
                .width(IntrinsicSize.Max)
                .cycloOverlaySurface()
                .padding(vertical = 12.dp),
        ) {
            items.forEachIndexed { index, item ->
                CompactChoiceRow(
                    label = item.label,
                    isSelected = index == selectedIndex,
                    check = check,
                    onClick = { onItemClick(index) },
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .cycloOverlaySurface()
            .padding(vertical = 12.dp),
    ) {
        items.forEachIndexed { index, item ->
            ActionMenuRow(label = item.label, onClick = { onItemClick(index) })
        }
    }
}

/** Uma opção do menu compacto: texto, 9 dp, o visto de 15 dp (ou o lugar dele) e 24 dp até à borda. */
@Composable
private fun CompactChoiceRow(label: String, isSelected: Boolean, check: Path, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .cycloPressable(onClick = onClick, pressedScale = 0.97f, role = Role.RadioButton)
            .semantics { selected = isSelected }
            .padding(start = TextStart, end = TextStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = label, style = CycloType.MenuItem, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(CheckGap))
        Canvas(modifier = Modifier.size(15.dp)) {
            if (!isSelected) return@Canvas
            val pxPerDp = density
            withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                drawPath(path = check, color = CycloColors.TextPrimary)
            }
        }
    }
}

/** Uma ação: o texto com 24 dp de cada lado, numa faixa de 42 dp. */
@Composable
private fun ActionMenuRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .cycloPressable(onClick = onClick, pressedScale = 0.97f, role = Role.Button)
            .padding(horizontal = TextStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = CycloType.MenuItem,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
