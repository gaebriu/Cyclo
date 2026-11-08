package com.gaebriu.cyclo.ui.settings

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.AgeRange
import com.gaebriu.cyclo.data.Chronotype
import com.gaebriu.cyclo.data.CycloData
import com.gaebriu.cyclo.data.FallAsleepTime
import com.gaebriu.cyclo.ui.components.ActionMenuItem
import com.gaebriu.cyclo.ui.components.AnchoredMenuHost
import com.gaebriu.cyclo.ui.components.BottomSheetHost
import com.gaebriu.cyclo.ui.components.CycloActionMenu
import com.gaebriu.cyclo.ui.components.CycloBigSheet
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.SwitchRow
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.keepLiveDuringCrossfade
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.i18n.AppLanguage
import com.gaebriu.cyclo.ui.theme.CycloSprings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.main.MainEdgeFades
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.time.LocalTimeFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Medidas do Figma (Settings, frame de 393 x 852).
/** O título "Settings" começa a 111 dp do topo; a primeira secção a 179 dp. */
private val TitleTop = 111.dp
private val TitleToContent = 39.dp
private val LabelToRow = 15.dp
private val RowGap = 15.dp

/** Da última linha de uma secção ao título da seguinte (ex.: "Time to fall asleep" a "Reminders"). */
private val SectionGap = 24.dp

/** Da linha ao texto de ajuda por baixo, e do texto de ajuda ao título seguinte. */
private val RowToDescription = 15.dp
private val DescriptionToSection = 15.dp
private val RowInset = 15.dp

/** Scroll em que a faixa de baixo some, ao chegar ao fim da página. */
private val BottomFadeRamp = 48.dp

/** Espaço mínimo entre o nome e o valor de uma linha. */
private val RowLabelGap = 9.dp

/** Margem de cima e de baixo do texto das linhas (só conta com letra grande: a 1x a linha tem 48 dp). */
private val RowTextVertical = 6.dp

/** Onde o valor acaba quando há seta (a seta de 24 dp fica a 15 dp da direita, 9 dp depois do valor). */
private val ValueEndWithArrow = 48.dp

/** A lua antes da versão (Cyclo-2 2039:146): 15 dp, e 9 dp até ao texto (que começa a 63 dp do ecrã). */
private val VersionIconSize = 15.dp
private val VersionIconGap = 9.dp


/** Os menus das definições (só um aberto de cada vez). */
private enum class SettingsMenu { None, Chronotype, FallAsleep, TimeFormat, Language }

/**
 * Definições: o perfil de sono (idade, cronótipo, tempo a adormecer), o lembrete da hora de dormir,
 * o formato das horas e a língua, exportar e repor os dados, e "About".
 *
 * - A idade escreve-se na própria linha (tap-to-write-box-age): o visto guarda; fora de 5–100 a
 *   caixa abana com um háptico de erro e o valor não entra.
 * - Cronótipo, tempo a adormecer e formato das horas abrem os action-menu do Componentes 3.
 * - "Bedtime" e "Log sleep" (Beta 2.3) ligam os lembretes (pedem a autorização de notificações; ver
 *   [onToggleReminder] e [onToggleLogSleep]).
 * - "Export my data" e "Restore my data" abrem o seletor de ficheiros do sistema.
 * - "View on GitHub" mostra primeiro o action-menu-big-open-github.
 * - A língua abre o action-menu-lang (Componentes 4): muda logo a língua da app toda ([onSetLanguage]).
 * - "Advanced textures" (Performance) liga e desliga o desenho completo ([onSetAdvancedTextures]).
 * - Voltar (botão, gesto) fecha primeiro o menu, a folha ou a caixa da idade, se estiverem abertos.
 *
 * @param reminderOn o interruptor "Bedtime" como se vê: ligado nas definições e com autorização.
 * @param logSleepOn o interruptor "Log sleep", da mesma forma.
 * @param reminderAsks ligar um lembrete abre primeiro o pedido de autorização do sistema (o toque
 *        tem então o háptico de botão, e não o de ligar).
 */
@Composable
fun SettingsScreen(
    data: CycloData,
    reminderOn: Boolean,
    onSetUse24Hour: (Boolean) -> Unit,
    onSetAge: (Int) -> Unit,
    onSetChronotype: (Chronotype) -> Unit,
    onSetFallAsleep: (FallAsleepTime) -> Unit,
    onToggleReminder: () -> Unit,
    logSleepOn: Boolean,
    onToggleLogSleep: () -> Unit,
    reminderAsks: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onOpenGitHub: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetAdvancedTextures: (Boolean) -> Unit,
    onClose: () -> Unit,
    update: UpdateUi? = null,
    onUpdateDownload: () -> Unit = {},
    onUpdateCancel: () -> Unit = {},
    onUpdateInstall: () -> Unit = {},
    onUpdateDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // A língua em que os textos estão (muda com eles).
    val language = AppLanguage.fromTag(stringResource(R.string.locale_tag))
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtra = (statusTop - 24.dp).coerceAtLeast(0.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val use24Hour = LocalTimeFormat.current.use24Hour
    var menu by remember { mutableStateOf(SettingsMenu.None) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    var githubOpen by remember { mutableStateOf(false) }
    var ageEditing by remember { mutableStateOf(false) }
    val content = rememberBackdropLayer()
    val scroll = rememberScrollState()
    val animationsEnabled = LocalVisualEffects.current.animationsEnabled
    val latestUpdate by rememberUpdatedState(update)
    // No fim da página a faixa de baixo some (nos últimos 48 dp de scroll), para não tapar a versão.
    val rampPx = with(density) { BottomFadeRamp.toPx() }
    val bottomAmount by remember(scroll, rampPx) {
        derivedStateOf {
            if (scroll.maxValue == Int.MAX_VALUE) 1f else ((scroll.maxValue - scroll.value) / rampPx).coerceIn(0f, 1f)
        }
    }
    val textShadow = remember(density) { Shadow(Color.Black.copy(alpha = 0.24f), Offset.Zero, with(density) { 15.dp.toPx() }) }
    val labelStyle = remember(textShadow) { CycloType.SectionLabel.copy(shadow = textShadow) }

    val chronotypeLabels = listOf(
        stringResource(R.string.settings_chronotype_hummingbird),
        stringResource(R.string.settings_chronotype_early_bird),
        stringResource(R.string.settings_chronotype_night_owl),
    )
    val fallAsleepShort = listOf(
        stringResource(R.string.settings_fall_asleep_under_5_short),
        stringResource(R.string.settings_fall_asleep_15_short),
        stringResource(R.string.settings_fall_asleep_30_short),
    )

    fun openMenu(which: SettingsMenu): (Rect) -> Unit = { menuAnchor = it; menu = which }

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
                .cycloVerticalScroll(scroll)
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(top = TitleTop + topExtra, bottom = bottomInset + CycloDimens.ScreenMargin),
        ) {
            BasicText(
                text = stringResource(R.string.settings_title),
                style = CycloType.Greeting.copy(shadow = textShadow),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(TitleToContent))

            // new-update-group: só com uma versão nova no GitHub (e não dispensada há menos de
            // 12 h). Entra e sai com zoom, fade e o lugar a abrir e a fechar. O que se vê ([shownTag])
            // segue a atualização disponível; outra versão no lugar da que se via funde-se com ela.
            // Um aviso que deixou de ser o disponível nunca responde, mesmo enquanto sai.
            var shownTag by remember { mutableStateOf(update?.tag) }
            var shownUpdate by remember { mutableStateOf(update) }
            if (update != null && update.tag == shownTag) shownUpdate = update
            LaunchedEffect(update?.tag) {
                val target = latestUpdate?.tag
                if (target == shownTag) return@LaunchedEffect
                shownTag = target
                latestUpdate?.let { shownUpdate = it }
            }
            fun forShown(tag: String, action: () -> Unit): () -> Unit = { if (latestUpdate?.tag == tag) action() }
            AnimatedVisibility(
                visible = shownTag != null,
                enter = if (LocalVisualEffects.current.animationsEnabled) {
                    fadeIn(CycloSprings.Enter.float) + scaleIn(CycloSprings.Enter.float, initialScale = CycloMotion.MenuStartScale) +
                        expandVertically(CycloSprings.Enter.intSize, expandFrom = Alignment.Top)
                } else {
                    fadeIn(CycloMotion.FadeSpring)
                },
                exit = if (LocalVisualEffects.current.animationsEnabled) {
                    fadeOut(CycloSprings.Exit.float) + scaleOut(CycloSprings.Exit.float, targetScale = CycloMotion.MenuStartScale) +
                        shrinkVertically(CycloSprings.Exit.intSize, shrinkTowards = Alignment.Top)
                } else {
                    fadeOut(CycloMotion.FadeSpring)
                },
            ) {
                shownUpdate?.let { first ->
                    // Outra versão no lugar da que se via (ex.: a beta dá lugar à estável): o
                    // conteúdo funde-se e a altura acompanha. O mesmo aviso (progresso do download)
                    // não anima: a chave é a tag.
                    AnimatedContent(
                        targetState = first,
                        contentKey = { it.tag },
                        transitionSpec = {
                            if (animationsEnabled) {
                                (fadeIn(CycloSprings.Enter.float) togetherWith fadeOut(CycloSprings.Exit.float))
                                    .using(SizeTransform(clip = false) { _, _ -> CycloSprings.Enter.intSize })
                            } else {
                                fadeIn(CycloMotion.FadeSpring) togetherWith fadeOut(CycloMotion.FadeSpring)
                            }
                        },
                        label = "update",
                    ) { state ->
                        val shown = shownUpdate?.takeIf { it.tag == state.tag } ?: state
                        Column {
                            UpdateGroup(
                                update = shown,
                                onDownload = forShown(shown.tag, onUpdateDownload),
                                onCancel = forShown(shown.tag, onUpdateCancel),
                                onInstall = forShown(shown.tag, onUpdateInstall),
                                onDismiss = forShown(shown.tag, onUpdateDismiss),
                            )
                            Spacer(Modifier.height(SectionGap))
                        }
                    }
                }
            }

            // Perfil de sono.
            SectionLabel(stringResource(R.string.settings_sleep_profile), labelStyle)
            AgeRow(
                age = data.age,
                editing = ageEditing,
                onEdit = { ageEditing = true },
                onDone = { value ->
                    onSetAge(value)
                    ageEditing = false
                },
                onCancel = { ageEditing = false },
            )
            Spacer(Modifier.height(RowGap))
            SettingsRow(
                label = stringResource(R.string.settings_chronotype),
                value = chronotypeLabels[data.chronotype.ordinal],
                arrow = true,
                onClickAt = openMenu(SettingsMenu.Chronotype),
            )
            Spacer(Modifier.height(RowGap))
            SettingsRow(
                label = stringResource(R.string.settings_fall_asleep),
                value = fallAsleepShort[data.fallAsleep.ordinal],
                arrow = true,
                onClickAt = openMenu(SettingsMenu.FallAsleep),
            )
            Spacer(Modifier.height(SectionGap))

            // Lembretes.
            SectionLabel(stringResource(R.string.settings_reminders), labelStyle)
            SwitchRow(
                label = stringResource(R.string.settings_bedtime_reminder),
                on = reminderOn,
                onToggle = onToggleReminder,
                toggleHaptic = reminderOn || !reminderAsks,
            )
            Spacer(Modifier.height(RowGap))
            // log-sleep (Cyclo-2 2039:134, Beta 2.3): o lembrete para registar o sono.
            SwitchRow(
                label = stringResource(R.string.settings_log_sleep_reminder),
                on = logSleepOn,
                onToggle = onToggleLogSleep,
                toggleHaptic = logSleepOn || !reminderAsks,
            )
            Spacer(Modifier.height(SectionGap))

            // Formato e língua.
            SectionLabel(stringResource(R.string.settings_format_language), labelStyle)
            SettingsRow(
                label = stringResource(R.string.settings_time_format),
                value = stringResource(if (use24Hour) R.string.settings_24_hour else R.string.settings_12_hour),
                arrow = true,
                onClickAt = openMenu(SettingsMenu.TimeFormat),
            )
            Spacer(Modifier.height(RowGap))
            SettingsRow(
                label = stringResource(R.string.settings_language),
                value = stringResource(language.label),
                arrow = true,
                onClickAt = openMenu(SettingsMenu.Language),
            )
            Spacer(Modifier.height(SectionGap))

            // Desempenho (performance-group, 256:114): "Advanced textures" e o texto de ajuda.
            SectionLabel(stringResource(R.string.settings_performance), labelStyle)
            SwitchRow(
                label = stringResource(R.string.settings_advanced_textures),
                on = data.advancedTextures,
                onToggle = { onSetAdvancedTextures(!data.advancedTextures) },
                // Durante a passagem do desenho, esta linha fica fora da fotografia (sem fantasma).
                modifier = Modifier.keepLiveDuringCrossfade(),
            )
            Description(stringResource(R.string.settings_advanced_textures_description), labelStyle)
            // No Figma, deste texto ao título "Data" são 24 dp (e não os 15 dos outros textos).
            Spacer(Modifier.height(SectionGap))

            // Dados.
            SectionLabel(stringResource(R.string.settings_data), labelStyle)
            SettingsButton(stringResource(R.string.settings_export_data), onClick = onExport)
            Spacer(Modifier.height(RowGap))
            SettingsButton(stringResource(R.string.settings_restore_data), onClick = onRestore)
            Description(stringResource(R.string.settings_data_description), labelStyle)
            Spacer(Modifier.height(DescriptionToSection))

            // Sobre (Cyclo-2 54:36): "Beta updates" e o texto dele, "View on GitHub" e a versão,
            // tudo a 15 dp.
            SectionLabel(stringResource(R.string.settings_about), labelStyle)
            BetaUpdatesRow()
            Description(stringResource(R.string.settings_beta_updates_description), labelStyle)
            Spacer(Modifier.height(DescriptionToSection))
            SettingsButton(stringResource(R.string.settings_github), onClick = { githubOpen = true })
            // A versão é o fim da página, com a lua do Cyclo antes (Beta 2.3, 2039:146): #A3A2A7 sem
            // mistura nem sombra (o texto mantém as dele), centrada na linha do texto.
            Spacer(Modifier.height(RowToDescription))
            Row(
                modifier = Modifier.padding(horizontal = RowInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CycloIcon(path = CycloAppIconPaths.Bedtime, size = VersionIconSize, tint = CycloColors.TextSecondary, luminosity = false)
                Spacer(Modifier.width(VersionIconGap))
                BasicText(text = stringResource(R.string.settings_version, rememberVersionLabel()), style = labelStyle)
            }
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(content)) {
            MainEdgeFades(topExtra = topExtra, bottomAmount = { bottomAmount })

            // back-b: fixo, a 48 dp do topo e 24 dp da esquerda (a zona de toque sai 4,5 dp).
            CycloCircleButton(
                iconPath = CycloIconPaths.Back,
                contentDescription = stringResource(R.string.settings_back),
                onClick = onClose,
                modifier = Modifier
                    .padding(top = statusTop + 24.dp, start = CycloDimens.ScreenMargin)
                    .offset(x = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2, y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2),
            )

            // Os menus nascem do canto de cima à direita da linha.
            AnchoredMenuHost(
                visible = menu != SettingsMenu.None,
                anchor = { menuAnchor },
                onDismiss = { menu = SettingsMenu.None },
                bottomMargin = bottomInset,
                alignEnd = true,
            ) {
                // Durante a saída o menu continua a mostrar o que tinha.
                var shown by remember { mutableStateOf(menu) }
                if (menu != SettingsMenu.None) shown = menu
                when (shown) {
                    SettingsMenu.Chronotype -> CycloActionMenu(
                        items = chronotypeLabels.map(::ActionMenuItem),
                        selectedIndex = data.chronotype.ordinal,
                        compact = true,
                        onItemClick = { index ->
                            menu = SettingsMenu.None
                            val chosen = Chronotype.entries[index]
                            if (chosen != data.chronotype) onSetChronotype(chosen)
                        },
                    )
                    SettingsMenu.FallAsleep -> CycloActionMenu(
                        items = listOf(
                            ActionMenuItem(stringResource(R.string.settings_fall_asleep_under_5)),
                            ActionMenuItem(stringResource(R.string.settings_fall_asleep_15)),
                            ActionMenuItem(stringResource(R.string.settings_fall_asleep_30)),
                        ),
                        selectedIndex = data.fallAsleep.ordinal,
                        compact = true,
                        onItemClick = { index ->
                            menu = SettingsMenu.None
                            val chosen = FallAsleepTime.entries[index]
                            if (chosen != data.fallAsleep) onSetFallAsleep(chosen)
                        },
                    )
                    // action-menu-lang (Componentes 4, 238:73): "English" e "Portuguese", com o
                    // visto na atual. A escolha muda a língua da app toda, sem reiniciar.
                    SettingsMenu.Language -> CycloActionMenu(
                        items = AppLanguage.entries.map { ActionMenuItem(stringResource(it.label)) },
                        selectedIndex = language.ordinal,
                        compact = true,
                        onItemClick = { index ->
                            menu = SettingsMenu.None
                            val chosen = AppLanguage.entries[index]
                            if (chosen != language) onSetLanguage(chosen)
                        },
                    )
                    // Como no Componentes 3: "24-hour" primeiro.
                    SettingsMenu.TimeFormat, SettingsMenu.None -> CycloActionMenu(
                        items = listOf(
                            ActionMenuItem(stringResource(R.string.settings_24_hour)),
                            ActionMenuItem(stringResource(R.string.settings_12_hour)),
                        ),
                        selectedIndex = if (use24Hour) 0 else 1,
                        compact = true,
                        onItemClick = { index ->
                            menu = SettingsMenu.None
                            // Escolher no menu é sempre uma escolha explícita, também quando é igual
                            // ao formato do sistema: daí em diante vale esta, mesmo que o sistema mude.
                            val chosen = index == 0
                            if (chosen != data.use24Hour) onSetUse24Hour(chosen)
                        },
                    )
                }
            }

            BottomSheetHost(
                visible = githubOpen,
                onDismiss = { githubOpen = false },
                bottomMargin = bottomInset,
            ) {
                CycloBigSheet(
                    title = stringResource(R.string.github_sheet_title),
                    texts = listOf(
                        stringResource(R.string.github_sheet_text_1),
                        stringResource(R.string.github_sheet_text_2),
                    ),
                    buttonText = stringResource(R.string.github_sheet_open),
                    onButtonClick = {
                        githubOpen = false
                        onOpenGitHub()
                    },
                )
            }
        }
    }

    // Vem depois do voltar de quem mostra as definições: fecha primeiro o que estiver aberto.
    BackHandler(enabled = menu != SettingsMenu.None || githubOpen || ageEditing) {
        when {
            menu != SettingsMenu.None -> menu = SettingsMenu.None
            githubOpen -> githubOpen = false
            else -> ageEditing = false
        }
    }
}

/**
 * A versão instalada como se mostra ao utilizador (lida do versionName do pacote, não escrita à
 * mão): "beta-1.0.0" fica "Beta 1.0", "alpha-0.7.2" fica "Alpha 0.7.2" e uma versão final
 * ("1.0.0") fica só o número ("1.0"). A mesma regra de [AppVersion.label].
 */
@Composable
private fun rememberVersionLabel(): String {
    val context = LocalContext.current
    return remember(context) {
        val name = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
        versionLabel(name)
    }
}

internal fun versionLabel(versionName: String): String =
    com.gaebriu.cyclo.update.AppVersion.parse(versionName)?.label() ?: versionName

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
 * Luminosity. O nome à esquerda; à direita o valor (#A3A2A7) e, nas que abrem um menu, a seta.
 * Sem [onClickAt], não responde ao toque. [onClickAt] recebe onde a linha está (para o menu).
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    arrow: Boolean = false,
    onClickAt: ((Rect) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val click: (() -> Unit)? = when {
        onClickAt != null -> {
            {
                // O menu nasce do canto de cima à direita da linha.
                coordinates[0]?.takeIf { it.isAttached }?.let { onClickAt(it.boundsInWindow()) }
            }
        }
        else -> onClick
    }
    // O nome à esquerda e o valor (e a seta) à direita, lado a lado: com letra grande, cada um
    // passa para a linha seguinte e a linha cresce, em vez de se sobreporem.
    LabelValueRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            // A posição mede-se antes do zoom do toque, para o menu não "tremer".
            .onGloballyPositioned { coordinates[0] = it }
            .then(if (click != null) Modifier.cycloPressable(onClick = click) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (value != null) stateDescription = value
            }
            .cycloCard(shadowColor = null)
            .padding(horizontal = RowInset, vertical = RowTextVertical),
        label = {
            BasicText(text = label, style = CycloType.CardTitle, modifier = Modifier.padding(end = RowLabelGap))
        },
        value = {
            // O valor e a seta juntos, encostados à direita.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    BasicText(
                        text = value,
                        style = CycloType.InputHint.copy(textAlign = TextAlign.End),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (arrow) {
                    Spacer(Modifier.width(ValueEndWithArrow - RowInset - 24.dp))
                    CycloIcon(path = CycloIconPaths.DropDown, size = 24.dp, tint = CycloColors.TextPrimary, luminosity = false)
                }
            }
        },
    )
}

/**
 * O nome à esquerda e o valor à direita, centrados na vertical.
 *
 * Cada um fica com a largura de que precisa; quando não há espaço para os dois numa linha (ecrã
 * estreito ou letra grande), o valor (curto: "Hummingbird", "15 min") fica inteiro e é o nome que
 * passa para a linha seguinte, pelas palavras. Só se nem assim couber é que o valor também quebra,
 * sempre entre palavras enquanto a palavra mais longa couber. Antes, cada lado tinha metade da
 * linha: a 320 dp "Hummingbird" partia-se a meio ("Hummingbir / d").
 */
@Composable
private fun LabelValueRow(
    label: @Composable () -> Unit,
    value: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(label, value), modifier = modifier) { (labels, values), constraints ->
        val labelMeasurable = labels.first()
        val valueMeasurable = values.first()
        val available = constraints.maxWidth
        val valueMax = valueMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val valueMin = valueMeasurable.minIntrinsicWidth(Constraints.Infinity)
        val labelMin = labelMeasurable.minIntrinsicWidth(Constraints.Infinity)
        // O valor inteiro, se o nome ainda couber pela palavra mais longa; senão, o que sobrar
        // (mas nunca menos do que a palavra mais longa do valor).
        val valueWidth = minOf(valueMax, maxOf(valueMin, available - labelMin), available).coerceAtLeast(0)
        val valuePlaceable = valueMeasurable.measure(Constraints(maxWidth = valueWidth))
        val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = (available - valuePlaceable.width).coerceAtLeast(0)))
        val height = maxOf(labelPlaceable.height, valuePlaceable.height, constraints.minHeight)
        // O mesmo centro que uma Row com CenterVertically (arredondado da mesma maneira).
        layout(available, height) {
            labelPlaceable.place(0, Alignment.CenterVertically.align(labelPlaceable.height, height))
            valuePlaceable.place(available - valuePlaceable.width, Alignment.CenterVertically.align(valuePlaceable.height, height))
        }
    }
}

/**
 * A idade. Sem estar a escrever, é uma linha como as outras ("Your age", e o valor ou
 * "Tap to write"). Ao tocar, passa à caixa de escrever da idade (tap-to-write-box-age, Componentes 3):
 * "Tap to write" à esquerda, o visto de 24 dp à direita e o teclado numérico.
 */
@Composable
private fun AgeRow(
    age: Int?,
    editing: Boolean,
    onEdit: () -> Unit,
    onDone: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val effects = LocalVisualEffects.current
    Crossfade(
        targetState = editing,
        animationSpec = tween(if (effects.animationsEnabled) 220 else 0),
        label = "age",
    ) { isEditing ->
        if (isEditing) {
            AgeInputBox(initial = age, onDone = onDone, onCancel = onCancel)
        } else {
            SettingsRow(
                label = stringResource(R.string.settings_age),
                value = if (age != null) pluralStringResource(R.plurals.settings_age_value, age, age) else stringResource(R.string.settings_age_unset),
                onClick = onEdit,
            )
        }
    }
}

/**
 * tap-to-write-box-age: 345 x 48, raio 15, #101011 com desfoque de fundo; o texto a 15 dp da
 * esquerda e o visto (24 dp, #A3A2A7) a 15 dp da direita, com zona de toque de 48 dp.
 * Um valor fora de 5–100 faz a caixa abanar com o háptico de erro, e fica por guardar.
 */
@Composable
private fun AgeInputBox(initial: Int?, onDone: (Int) -> Unit, onCancel: () -> Unit) {
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    var hadFocus by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val ageLabel = stringResource(R.string.settings_age)
    val saveLabel = stringResource(R.string.settings_age_done)
    val invalidText = stringResource(R.string.settings_age_invalid)

    fun submit() {
        val value = text.toIntOrNull()
        if (value != null && value in AgeRange) {
            CycloHaptics.confirm(view)
            keyboard?.hide()
            onDone(value)
        } else {
            CycloHaptics.reject(view)
            // Para o TalkBack, o porquê (a caixa só abana).
            view.announceForAccessibility(invalidText)
            if (effects.animationsEnabled) scope.launch { shake.playRejectShake() }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            .graphicsLayer { translationX = shake.value.dp.toPx() }
            .cycloCard(shadowColor = null),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { new -> text = new.filter(Char::isDigit).take(3) },
            singleLine = true,
            textStyle = CycloType.Input,
            cursorBrush = SolidColor(CycloColors.TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = RowInset, end = RowInset + CycloDimens.TouchTarget)
                .focusRequester(focus)
                .onFocusChanged { state ->
                    // Sair da caixa sem guardar (tocar fora, fechar o teclado com voltar) cancela.
                    if (state.isFocused) hadFocus = true else if (hadFocus) onCancel()
                }
                .semantics { contentDescription = ageLabel },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) BasicText(stringResource(R.string.settings_age_unset), style = CycloType.InputHint)
                    inner()
                }
            },
        )
        // O visto (no Figma a camada chama-se voice-input-b): 24 dp a 15 dp da direita.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = RowInset - (CycloDimens.TouchTarget - 24.dp) / 2)
                .size(CycloDimens.TouchTarget)
                .cycloPressable(onClick = ::submit, haptic = false, pressedScale = CycloMotion.IconButtonPressScale)
                .semantics { contentDescription = saveLabel },
            contentAlignment = Alignment.Center,
        ) {
            CycloIcon(path = AgeCheckPath, size = 24.dp, tint = CycloColors.TextSecondary)
        }
    }
}

private const val ShakeDurationMs = 360

/**
 * A recusa da caixa da idade (tap-to-write-box-age: um valor fora de 5–100): um abanão horizontal
 * curto que vai morrendo (dp). Partilhado com a linha "Beta updates", que não se pode desligar numa
 * versão beta. O háptico ([CycloHaptics.reject]) toca quem chama.
 */
private suspend fun Animatable<Float, AnimationVector1D>.playRejectShake() {
    snapTo(0f)
    animateTo(0f, keyframes {
        durationMillis = ShakeDurationMs
        -8f at 50
        7f at 110
        -5f at 170
        4f at 230
        -2f at 290
        0f at ShakeDurationMs
    })
}

/**
 * beta-updates (Cyclo-2 2006:691): o interruptor das atualizações beta. Esta app é uma beta e
 * recebe sempre as duas (ver [com.gaebriu.cyclo.update.CycloUpdates]), por isso fica sempre ligado
 * e não se pode desligar: está cá pela paridade com as versões estáveis. Tocar não muda nada e
 * responde como a caixa da idade quando recusa um valor: abana, com o háptico de erro (e, para o
 * TalkBack, diz porquê). Numa versão estável, esta linha precisa da lógica dos canais
 * (release-1.1: `setBetaEnabled`), que hoje não existe.
 */
@Composable
private fun BetaUpdatesRow() {
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val lockedText = stringResource(R.string.settings_beta_updates_locked)
    SwitchRow(
        label = stringResource(R.string.settings_beta_updates),
        on = true,
        onToggle = {
            CycloHaptics.reject(view)
            view.announceForAccessibility(lockedText)
            if (effects.animationsEnabled) scope.launch { shake.playRejectShake() }
        },
        toggleHaptic = false,
        pressHaptic = false,
        shake = { shake.value },
    )
}

/** O visto da caixa da idade (check, 24 x 24), do SVG do Componentes 3. */
private const val AgeCheckPath =
    "M9.55 14.6695L17.7708 6.449C18.0366 6.183 18.3499 6.05 18.7108 6.05C19.0718 6.05 19.3852 6.183 19.651 6.449C19.917 6.71483 20.05 7.03 20.05 7.3945C20.05 7.759 19.917 8.07425 19.651 8.34025L10.4902 17.512C10.2242 17.778 9.91083 17.911 9.55 17.911C9.18917 17.911 8.87575 17.778 8.60975 17.512L4.338 13.2402C4.072 12.9742 3.94083 12.659 3.9445 12.2945C3.94817 11.93 4.083 11.6148 4.349 11.349C4.61483 11.083 4.93 10.95 5.2945 10.95C5.659 10.95 5.97425 11.083 6.24025 11.349L9.55 14.6695Z"

/** Botão de largura inteira com o texto ao centro (export-data-b, git...): 345 x 48 dp, raio 15. */
@Composable
private fun SettingsButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            .cycloPressable(onClick = onClick)
            .cycloCard(shadowColor = null),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = CycloType.CardTitle.copy(textAlign = TextAlign.Center),
            modifier = Modifier.padding(horizontal = RowInset, vertical = RowTextVertical),
        )
    }
}
