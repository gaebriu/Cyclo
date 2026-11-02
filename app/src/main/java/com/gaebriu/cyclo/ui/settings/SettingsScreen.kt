package com.gaebriu.cyclo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.ActionMenuItem
import com.gaebriu.cyclo.ui.components.AnchoredMenuHost
import com.gaebriu.cyclo.ui.components.CycloActionMenu
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.main.MainEdgeFades
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.time.LocalTimeFormat

// Medidas do Figma (Settings, frame de 393 x 852).
/** O título "Settings" começa a 111 dp do topo; a primeira secção a 179 dp. */
private val TitleTop = 111.dp
private val TitleToContent = 39.dp
private val LabelToRow = 15.dp
private val RowGap = 15.dp
private val RowToDescription = 9.dp
private val SectionGap = 15.dp
private val RowInset = 15.dp

/** Onde o valor acaba quando há seta (a seta de 24 dp fica a 15 dp da direita, 9 dp depois do valor). */
private val ValueEndWithArrow = 48.dp

/**
 * Definições: o perfil de sono, os lembretes, o formato das horas e a língua, os dados e "About".
 *
 * Por agora só o formato das horas funciona (12 h com AM/PM ou 24 h; sem escolha, segue o do
 * sistema operativo, e a escolha fica guardada). As outras linhas mostram-se como no Figma mas
 * ainda não fazem nada: a língua aparece sem menu, e os lembretes desligados.
 *
 * - O botão de voltar (back-b) fica fixo no topo; o título e as secções têm scroll, com o
 *   desfoque progressivo no topo e no fundo, como no Feed.
 * - Voltar (botão, gesto) fecha primeiro o menu do formato, se estiver aberto.
 *
 * @param onSetUse24Hour guarda o formato escolhido.
 * @param onClose fecha as definições.
 */
@Composable
fun SettingsScreen(
    onSetUse24Hour: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtra = (statusTop - 24.dp).coerceAtLeast(0.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val use24Hour = LocalTimeFormat.current.use24Hour
    var formatMenuOpen by remember { mutableStateOf(false) }
    var formatAnchor by remember { mutableStateOf(Rect.Zero) }
    val content = rememberBackdropLayer()
    val textShadow = remember(density) { Shadow(Color.Black.copy(alpha = 0.24f), Offset.Zero, with(density) { 15.dp.toPx() }) }
    val labelStyle = remember(textShadow) { CycloType.SectionLabel.copy(shadow = textShadow) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CycloColors.Background)
            // Não deixa os toques passarem para o Feed, por baixo.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(content)
                .background(CycloColors.Background)
                .cycloVerticalScroll(rememberScrollState())
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(top = TitleTop + topExtra, bottom = bottomInset + CycloDimens.ScreenMargin),
        ) {
            BasicText(
                text = stringResource(R.string.settings_title),
                style = CycloType.Greeting.copy(shadow = textShadow),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(TitleToContent))

            // Perfil de sono.
            SectionLabel(stringResource(R.string.settings_sleep_profile), labelStyle)
            SettingsRow(stringResource(R.string.settings_age), value = stringResource(R.string.settings_value_unset))
            Spacer(Modifier.height(RowGap))
            SettingsRow(stringResource(R.string.settings_chronotype), value = stringResource(R.string.settings_value_unset), arrow = true)
            Spacer(Modifier.height(RowGap))
            SettingsRow(stringResource(R.string.settings_cycle_length), value = stringResource(R.string.settings_minutes, 90), arrow = true)
            Description(stringResource(R.string.settings_cycle_length_description), labelStyle)
            Spacer(Modifier.height(RowGap))
            SettingsRow(stringResource(R.string.settings_fall_asleep), value = stringResource(R.string.settings_minutes, 15), arrow = true)
            Spacer(Modifier.height(SectionGap))

            // Lembretes.
            SectionLabel(stringResource(R.string.settings_reminders), labelStyle)
            SettingsRow(stringResource(R.string.settings_bedtime_reminder), switch = false)
            Spacer(Modifier.height(RowGap))
            SettingsRow(stringResource(R.string.settings_morning_check_in), switch = false)
            Description(stringResource(R.string.settings_morning_check_in_description), labelStyle)
            Spacer(Modifier.height(SectionGap))

            // Formato e língua.
            SectionLabel(stringResource(R.string.settings_format_language), labelStyle)
            SettingsRow(
                label = stringResource(R.string.settings_time_format),
                value = stringResource(if (use24Hour) R.string.settings_24_hour else R.string.settings_12_hour),
                arrow = true,
                onClick = { formatMenuOpen = true },
                onBounds = { formatAnchor = it },
            )
            Spacer(Modifier.height(RowGap))
            // Só visual por agora: a escolha da língua ainda não existe.
            SettingsRow(stringResource(R.string.settings_language), value = stringResource(R.string.settings_language_english), arrow = true)
            Spacer(Modifier.height(SectionGap))

            // Dados.
            SectionLabel(stringResource(R.string.settings_data), labelStyle)
            SettingsButton(stringResource(R.string.settings_export_data))
            Spacer(Modifier.height(RowGap))
            SettingsButton(stringResource(R.string.settings_restore_data))
            Description(stringResource(R.string.settings_data_description), labelStyle)
            Spacer(Modifier.height(SectionGap))

            // Sobre.
            SectionLabel(stringResource(R.string.settings_about), labelStyle)
            SettingsButton(stringResource(R.string.settings_github))
            Spacer(Modifier.height(RowGap))
            SettingsButton(stringResource(R.string.settings_feedback))
            Description(stringResource(R.string.settings_version, rememberVersionName()), labelStyle, top = RowGap)
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(content)) {
            MainEdgeFades()

            // back-b: fixo, a 48 dp do topo e 24 dp da esquerda (a zona de toque sai 4,5 dp).
            CycloCircleButton(
                iconPath = CycloIconPaths.Back,
                contentDescription = stringResource(R.string.settings_back),
                onClick = onClose,
                modifier = Modifier
                    .padding(top = statusTop + 24.dp, start = CycloDimens.ScreenMargin)
                    .offset(x = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2, y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2),
            )

            AnchoredMenuHost(
                visible = formatMenuOpen,
                anchor = { formatAnchor },
                onDismiss = { formatMenuOpen = false },
                bottomMargin = bottomInset,
                alignEnd = true,
            ) {
                CycloActionMenu(
                    items = listOf(
                        ActionMenuItem(stringResource(R.string.settings_12_hour)),
                        ActionMenuItem(stringResource(R.string.settings_24_hour)),
                    ),
                    selectedIndex = if (use24Hour) 1 else 0,
                    onItemClick = { index ->
                        formatMenuOpen = false
                        val chosen = index == 1
                        if (chosen != use24Hour) onSetUse24Hour(chosen)
                    },
                )
            }
        }
    }

    // Vem depois do voltar de quem mostra as definições: fecha primeiro o menu.
    BackHandler(enabled = formatMenuOpen) { formatMenuOpen = false }
}

/** O nome da versão instalada (ex.: "alpha-0.7.0"). */
@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
private fun SectionLabel(text: String, style: TextStyle) {
    BasicText(text = text, style = style, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(LabelToRow))
}

/** Texto de ajuda por baixo de uma linha: a 15 dp da esquerda, 315 dp de largura. */
@Composable
private fun Description(text: String, style: TextStyle, top: Dp = RowToDescription) {
    Spacer(Modifier.height(top))
    BasicText(text = text, style = style, modifier = Modifier.padding(horizontal = RowInset))
}

/**
 * Uma linha das definições (age, chronotype, time-format...): 345 x 48 dp, raio 15, #101011 em
 * Luminosity. O nome à esquerda; à direita o valor (#A3A2A7), a seta, ou o interruptor (y/n).
 * Sem [onClick], não responde ao toque (ainda não tem função).
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    arrow: Boolean = false,
    switch: Boolean? = null,
    onClick: (() -> Unit)? = null,
    onBounds: (Rect) -> Unit = {},
) {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CycloDimens.InputHeight)
            // A posição mede-se antes do zoom do toque, para o menu não "tremer".
            .onGloballyPositioned { coordinates[0] = it }
            .then(
                if (onClick != null) {
                    Modifier.cycloPressable(onClick = {
                        // O menu nasce do canto de cima à direita da linha.
                        coordinates[0]?.takeIf { it.isAttached }?.let { onBounds(it.boundsInWindow()) }
                        onClick()
                    })
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (value != null) stateDescription = value
            }
            .cycloCard(shadowColor = null),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text = label, style = CycloType.CardTitle, modifier = Modifier.padding(start = RowInset))
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = RowInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value != null) {
                BasicText(text = value, style = CycloType.InputHint)
                if (arrow) Spacer(Modifier.width(ValueEndWithArrow - RowInset - 24.dp))
            }
            if (arrow) CycloIcon(path = CycloIconPaths.DropDown, size = 24.dp, tint = CycloColors.TextPrimary, luminosity = false)
            if (switch != null) SettingsSwitch(on = switch)
        }
    }
}

/**
 * Interruptor (y/n): 42 x 23 dp, raio 15. Ligado: #4A3C99 com a bola à direita; desligado:
 * #A3A2A7 a 15 % com a bola à esquerda a 63 %. A bola tem 15 dp, a 4 dp das pontas, com
 * gradiente #FCFCFE a #9F9FA0 em Luminosity.
 */
@Composable
private fun SettingsSwitch(on: Boolean) {
    val effects = LocalVisualEffects.current
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 23.dp)
            .drawBehind {
                val radius = CornerRadius(15.dp.toPx())
                drawRoundRect(
                    color = if (on) CycloColors.Accent else CycloColors.TextSecondary.copy(alpha = 0.15f),
                    cornerRadius = radius,
                )
                val knob = 15.dp.toPx()
                val inset = 4.dp.toPx()
                val x = if (on) size.width - inset - knob else inset
                val alpha = if (on) 1f else 0.63f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(CycloColors.TextPrimary.copy(alpha = alpha), Color(0xFF9F9FA0).copy(alpha = alpha)),
                        startY = inset,
                        endY = inset + knob,
                    ),
                    topLeft = Offset(x, inset),
                    size = androidx.compose.ui.geometry.Size(knob, knob),
                    cornerRadius = radius,
                    blendMode = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver,
                )
            },
    )
}

/** Botão de largura inteira com o texto ao centro (export-data-b, git...): 345 x 48 dp, raio 15. Ainda sem função. */
@Composable
private fun SettingsButton(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CycloDimens.InputHeight)
            .cycloCard(shadowColor = null),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = CycloType.CardTitle.copy(textAlign = TextAlign.Center))
    }
}
