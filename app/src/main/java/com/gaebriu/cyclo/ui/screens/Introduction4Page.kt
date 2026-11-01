package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloDropdownField
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.RoutineNameInput
import com.gaebriu.cyclo.ui.components.rememberRoutineNameInput
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.keyboard.rememberFadeWithKeyboard
import com.gaebriu.cyclo.ui.routine.RepeatSelection
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Introduction 4: "When should it appear on your feed?" (só o conteúdo; o botão de cima, a barra
 * de progresso e o Next são fixos, e os menus da repetição vivem em [RepeatOverlays]).
 *
 * - A caixa do nome mostra o nome que ficou do Introduction 3 (incluindo o "Routine [Nº]"
 *   automático) e pode editar-se, escrevendo ou ditando, como no Introduction 3.
 * - Ao usar a caixa do nome (teclado aberto) escondem-se, com um fade, o campo que abre o menu da
 *   repetição e o texto "If it's just for one time use, skip this part"; reaparecem quando o
 *   teclado fecha e nunca se escondem durante o ditado.
 * - O campo abre o menu da repetição; "Show in selected days" abre a folha dos dias.
 */
@Composable
fun Introduction4Page(
    name: String,
    onNameChange: (String) -> Unit,
    repeat: RepeatSelection,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val nameInput = rememberRoutineNameInput(onNameChange)
    val fade = rememberFadeWithKeyboard(keepVisible = nameInput.isDictating)
    // Enquanto está (quase) escondido, o campo da repetição não aceita toques.
    val dropdownInteractive by remember { derivedStateOf { fade.value >= 0.99f } }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Tocar fora da caixa fecha o teclado.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Calendar, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(CycloDimens.Spacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin)) {
                BasicText(
                    text = stringResource(R.string.introduction_4_title),
                    style = CycloType.HeaderMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                )
                Spacer(Modifier.height(24.dp))
                BasicText(
                    text = stringResource(R.string.introduction_4_subtitle),
                    style = CycloType.Body,
                    // Mantém o espaço: só muda a opacidade, por isso nada salta.
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = fade.value },
                )
                Spacer(Modifier.height(63.dp))
            }

            RoutineNameInput(
                state = nameInput,
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin),
            )

            Spacer(Modifier.height(CycloDimens.Spacing))
            Box(
                modifier = Modifier
                    .padding(horizontal = CycloDimens.ScreenMargin)
                    .graphicsLayer { alpha = fade.value },
            ) {
                CycloDropdownField(
                    text = stringResource(repeat.option.label),
                    description = stringResource(R.string.introduction_4_repeat_field),
                    onClick = { repeat.isMenuOpen = true },
                    enabled = dropdownInteractive,
                    onBoundsInRoot = { repeat.anchor = it },
                )
            }
        }
    }
}
