package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.routine.Weekday
import com.gaebriu.cyclo.ui.routine.hasDay
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Folha para escolher os dias da semana (action-menu-big-choose-days, 345 x 240 dp): pega,
 * título, descrição, os sete dias (domingo primeiro) e o botão "Done". Todos os espaços são de 15 dp.
 *
 * @param selectedMask os dias ligados (ver [hasDay]).
 */
@Composable
fun CycloDaysSheet(
    selectedMask: Int,
    onToggleDay: (Weekday) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cycloOverlaySurface()
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        // Pega para arrastar a folha para baixo (swipe-to-close-b).
        Box(
            modifier = Modifier
                .size(width = 39.dp, height = 2.dp)
                .background(CycloColors.TextPrimary.copy(alpha = 0.15f), RoundedCornerShape(1.dp)),
        )

        Spacer(Modifier.height(15.dp))
        BasicText(text = stringResource(R.string.days_sheet_title), style = CycloType.SheetTitle)

        Spacer(Modifier.height(15.dp))
        BasicText(
            text = stringResource(R.string.days_sheet_description),
            style = CycloType.Body,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(15.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Weekday.values().forEach { day ->
                DayChip(day = day, selected = selectedMask.hasDay(day), onToggle = { onToggleDay(day) })
            }
        }

        Spacer(Modifier.height(15.dp))
        CycloPrimaryButton(
            text = stringResource(R.string.days_sheet_done),
            onClick = onDone,
            size = CycloButtonSize.Compact,
        )
        Spacer(Modifier.height(15.dp))
    }
}

/** Um dia da semana: círculo de 39 dp que passa de cinzento a roxo, com uma mola. */
@Composable
private fun DayChip(day: Weekday, selected: Boolean, onToggle: () -> Unit) {
    val view = LocalView.current
    val letter = stringResource(day.shortLabel)
    val name = stringResource(day.fullName)
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "day",
    )

    Box(
        modifier = Modifier
            .size(CycloDimens.CircleButtonSize)
            .cycloPressable(
                onClick = {
                    // Ligar / desligar tem o seu próprio háptico.
                    CycloHaptics.toggle(view, !selected)
                    onToggle()
                },
                haptic = false,
                pressedScale = CycloMotion.IconButtonPressScale,
                role = Role.Checkbox,
            )
            .semantics {
                this.selected = selected
                contentDescription = name
            }
            .drawBehind { drawDayChip(progress) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = letter,
            style = CycloType.Button.copy(
                color = lerp(CycloColors.TextPrimary.copy(alpha = 0.24f), CycloColors.TextPrimary, progress),
            ),
        )
    }
}

/** Cinzento (#A3A2A7 a 24 %) quando desligado; roxo (#4A3C99) quando ligado. Sombra interior 0, 6, 15. */
private fun DrawScope.drawDayChip(progress: Float) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val offFill = CycloColors.TextSecondary.copy(alpha = 0.24f)

    drawCircle(color = lerp(offFill, CycloColors.Accent, progress), radius = radius, center = center)
    val shape = Path().apply { addOval(Rect(center, radius)) }
    drawInnerShadow(shape, Color.Black.copy(alpha = 0.15f), Offset(0f, 6.dp.toPx()), 15.dp.toPx())
    drawCircle(
        color = lerp(offFill, CycloColors.Accent.copy(alpha = 0.24f), progress),
        radius = radius - 0.5.dp.toPx(),
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )
}
