package com.gaebriu.cyclo.ui.routine

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.gaebriu.cyclo.R

/** Quando a rotina aparece no Feed (opções do menu do Introduction 4). */
enum class RepeatOption(@StringRes val label: Int) {
    JustOnce(R.string.repeat_just_once),
    Everyday(R.string.repeat_everyday),
    DuringWeek(R.string.repeat_during_week),
    DuringWeekend(R.string.repeat_during_weekend),
    SelectedDays(R.string.repeat_selected_days),
}

/** Dias da semana pela ordem do design (domingo primeiro). */
enum class Weekday(
    @StringRes val shortLabel: Int,
    @StringRes val fullName: Int,
    @StringRes val pluralName: Int,
) {
    Sunday(R.string.day_short_sunday, R.string.day_sunday, R.string.day_sunday_plural),
    Monday(R.string.day_short_monday, R.string.day_monday, R.string.day_monday_plural),
    Tuesday(R.string.day_short_tuesday, R.string.day_tuesday, R.string.day_tuesday_plural),
    Wednesday(R.string.day_short_wednesday, R.string.day_wednesday, R.string.day_wednesday_plural),
    Thursday(R.string.day_short_thursday, R.string.day_thursday, R.string.day_thursday_plural),
    Friday(R.string.day_short_friday, R.string.day_friday, R.string.day_friday_plural),
    Saturday(R.string.day_short_saturday, R.string.day_saturday, R.string.day_saturday_plural),
}

/** Os dias escolhidos guardam-se numa máscara de bits: bit 0 = domingo ... bit 6 = sábado. */
fun Int.hasDay(day: Weekday): Boolean = this and (1 shl day.ordinal) != 0

fun Int.withDayToggled(day: Weekday): Int = this xor (1 shl day.ordinal)

/**
 * O que o utilizador escolheu para a repetição, mais o estado dos menus que a escolhem.
 *
 * @property option a opção em vigor. "Show in selected days" só passa a valer depois de "Done"
 *                  com pelo menos um dia escolhido.
 * @property daysMask os dias escolhidos (ver [hasDay]).
 * @property anchor onde está o campo que abre o menu, em coordenadas da janela.
 */
@Stable
class RepeatSelection(option: RepeatOption = RepeatOption.JustOnce, daysMask: Int = 0) {
    var option by mutableStateOf(option)
    var daysMask by mutableIntStateOf(daysMask)
    var isMenuOpen by mutableStateOf(false)
    var isSheetOpen by mutableStateOf(false)
    var anchor by mutableStateOf(Rect.Zero)

    val isOverlayOpen: Boolean get() = isMenuOpen || isSheetOpen

    fun closeOverlays() {
        isMenuOpen = false
        isSheetOpen = false
    }

    companion object {
        /** Só se guarda a escolha (os menus abertos não sobrevivem a rodar o ecrã). */
        val Saver: Saver<RepeatSelection, Any> = listSaver<RepeatSelection, Int>(
            save = { listOf(it.option.ordinal, it.daysMask) },
            restore = { RepeatSelection(RepeatOption.values()[it[0]], it[1]) },
        )
    }
}

/** @param option e [daysMask]: a escolha com que começa (ex.: a de uma rotina a editar). */
@Composable
fun rememberRepeatSelection(
    option: RepeatOption = RepeatOption.JustOnce,
    daysMask: Int = 0,
): RepeatSelection =
    rememberSaveable(saver = RepeatSelection.Saver) { RepeatSelection(option, daysMask) }
