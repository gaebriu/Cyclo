package com.gaebriu.cyclo.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.ActionMenuItem
import com.gaebriu.cyclo.ui.components.AnchoredMenuHost
import com.gaebriu.cyclo.ui.components.BottomSheetHost
import com.gaebriu.cyclo.ui.components.CycloActionMenu
import com.gaebriu.cyclo.ui.components.CycloDaysSheet
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.RepeatSelection
import com.gaebriu.cyclo.ui.routine.Weekday
import com.gaebriu.cyclo.ui.routine.hasDay
import com.gaebriu.cyclo.ui.routine.withDayToggled

/**
 * O menu da repetição e a folha de escolher os dias, por cima do ecrã todo (também por cima do
 * botão Next). Só existem enquanto estão abertos (ou a fechar).
 *
 * - Escolher uma opção fecha o menu e passa a valer; "Show in selected days" abre a folha em vez disso.
 * - Por baixo de "Show in selected days" aparece o resumo dos dias escolhidos (ver [selectedDaysHint]).
 * - Na folha, os dias mexem num rascunho: "Done" confirma (com pelo menos um dia); fechar de
 *   outra forma (arrastar, tocar fora, voltar) deita o rascunho fora.
 */
@Composable
fun RepeatOverlays(
    selection: RepeatSelection,
    bottomMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val items = RepeatOption.values().map { option ->
        ActionMenuItem(
            label = stringResource(option.label),
            hint = if (option == RepeatOption.SelectedDays) selectedDaysHint(selection) else null,
        )
    }

    AnchoredMenuHost(
        visible = selection.isMenuOpen,
        anchor = { selection.anchor },
        onDismiss = { selection.isMenuOpen = false },
        bottomMargin = bottomMargin,
        modifier = modifier,
    ) {
        CycloActionMenu(
            items = items,
            selectedIndex = selection.option.ordinal,
            onItemClick = { index ->
                val option = RepeatOption.values()[index]
                selection.isMenuOpen = false
                if (option == RepeatOption.SelectedDays) {
                    selection.isSheetOpen = true
                } else {
                    selection.option = option
                }
            },
        )
    }

    // Rascunho dos dias: começa nos dias já escolhidos de cada vez que a folha abre.
    var draft by remember { mutableIntStateOf(selection.daysMask) }
    LaunchedEffect(selection.isSheetOpen) {
        if (selection.isSheetOpen) draft = selection.daysMask
    }

    BottomSheetHost(
        visible = selection.isSheetOpen,
        onDismiss = { selection.isSheetOpen = false },
        bottomMargin = bottomMargin,
        modifier = modifier,
    ) {
        CycloDaysSheet(
            selectedMask = draft,
            onToggleDay = { day -> draft = draft.withDayToggled(day) },
            onDone = {
                if (draft != 0) {
                    selection.daysMask = draft
                    selection.option = RepeatOption.SelectedDays
                }
                selection.isSheetOpen = false
            },
        )
    }
}

/**
 * Texto por baixo de "Show in selected days": com dias escolhidos (e essa opção em vigor), o resumo
 * deles no plural, pela ordem do design (ex.: domingo + segunda dá "Sundays, Mondays");
 * caso contrário, "Choose days".
 */
@Composable
private fun selectedDaysHint(selection: RepeatSelection): String {
    val chooseDays = stringResource(R.string.repeat_choose_days_hint)
    val separator = stringResource(R.string.days_summary_separator)
    val pluralNames = Weekday.values().map { stringResource(it.pluralName) }
    if (selection.option != RepeatOption.SelectedDays || selection.daysMask == 0) return chooseDays
    return Weekday.values()
        .filter { selection.daysMask.hasDay(it) }
        .joinToString(separator) { pluralNames[it.ordinal] }
}
