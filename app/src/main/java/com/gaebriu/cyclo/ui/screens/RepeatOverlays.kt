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
import com.gaebriu.cyclo.ui.routine.withDayToggled

/**
 * O menu da repetição e a folha de escolher os dias, por cima do ecrã todo (também por cima do
 * botão Next). Só existem enquanto estão abertos (ou a fechar).
 *
 * - Escolher uma opção fecha o menu e passa a valer; "Show in selected days" abre a folha em vez disso.
 * - Na folha, os dias mexem num rascunho: "Done" confirma (com pelo menos um dia); fechar de
 *   outra forma (arrastar, tocar fora, voltar) deita o rascunho fora.
 */
@Composable
fun RepeatOverlays(
    selection: RepeatSelection,
    bottomMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.repeat_choose_days_hint)
    val items = RepeatOption.values().map { option ->
        ActionMenuItem(
            label = stringResource(option.label),
            hint = if (option == RepeatOption.SelectedDays) hint else null,
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
