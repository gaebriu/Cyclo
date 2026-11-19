package com.gaebriu.cyclo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.systemGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.effects.BackGestureVelocity
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalCoverDrag
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.chromeHidden
import com.gaebriu.cyclo.ui.effects.keepLiveDuringCrossfade
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.effects.rememberChromeHide
import com.gaebriu.cyclo.ui.effects.trackChromeHide
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.i18n.AppLanguage
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.main.MainEdgeFades
import com.gaebriu.cyclo.ui.screens.IntroSwipe
import com.gaebriu.cyclo.ui.screens.introSwipeDecision
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.time.LocalTimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

// Medidas do Figma (Settings 6012:34 e as subpáginas 6013:224…533, frames de 393 x 852).
/** O título começa a 111 dp do topo; o conteúdo a 179 dp (39 dp depois do título de 29 dp). */
private val TitleTop = 111.dp
private val TitleToContent = 39.dp
private val RowGap = 15.dp

/** Do fim de um grupo ao seguinte (ex.: o new-update-group antes das linhas). */
private val SectionGap = 24.dp

/** Da linha ao texto de ajuda por baixo (15 dp, como todas as distâncias das subpáginas). */
private val RowToDescription = 15.dp
private val RowInset = 15.dp

/** A faixa de baixo das definições (fx progressive-blur-bottom 6012:126). */
private val SettingsBottomFade = 48.dp

/** Scroll em que a faixa de baixo some, ao chegar ao fim da página. */
private val BottomFadeRamp = 48.dp

/** Espaço mínimo entre o nome e o valor de uma linha. */
private val RowLabelGap = 9.dp

/** Margem de cima e de baixo do texto das linhas (só conta com letra grande: a 1x a linha tem 48 dp). */
private val RowTextVertical = 6.dp

/** Onde o valor acaba quando há seta (a seta de 24 dp fica a 15 dp da direita, 9 dp depois do valor). */
private val ValueEndWithArrow = 48.dp

/** A lua antes da versão (6013:574): 15 dp, e 9 dp até ao texto. */
private val VersionIconSize = 15.dp
private val VersionIconGap = 9.dp

/** As linhas da página principal (sleep-profile-group-b…): o círculo de 24 dp a 15 dp da esquerda, o ícone de 16 dp e o texto a 54 dp. */
private val NavCircle = 24.dp
private val NavIcon = 16.dp
private val NavTextStart = 54.dp

/**
 * A página principal, por baixo da subpágina que entra: recua para a esquerda este tanto da largura
 * (como no iOS) e escurece com o véu dos ecrãs que recuam.
 */
private const val MainParallax = 0.3f

/** O swipe para a direita só começa quando o movimento é pelo menos isto mais horizontal do que vertical. */
private const val HorizontalDominance = 1.2f

/** Os menus das definições (só um aberto de cada vez). */
private enum class SettingsMenu { None, Chronotype, FallAsleep, TimeFormat, Language }

/**
 * As subpáginas (Cyclo 3.1, 6013:224…533): o nome (o título da subpágina e o da linha na página
 * principal), a cor do círculo e o ícone (16 dp).
 */
private enum class SettingsPage(
    @StringRes val title: Int,
    val color: Color,
    val icon: String,
) {
    SleepProfile(R.string.settings_sleep_profile, Color(0xFF4A3C99), SettingsIcons.Person),
    Reminders(R.string.settings_reminders, Color(0xFFB5473B), SettingsIcons.Notifications),
    FormatLanguage(R.string.settings_format_language, Color(0xFF3E5FC4), SettingsIcons.Globe),
    Interface(R.string.settings_interface, Color(0xFF1F7A63), SettingsIcons.Mobile),
    Data(R.string.settings_data, Color(0xFF1F6FA3), SettingsIcons.Folder),
    About(R.string.settings_about, Color(0xFF6B6A72), SettingsIcons.Info),
}

/**
 * Definições (Cyclo 3.1, Settings 6012:34): uma página principal com as seis linhas e, por cima, a
 * subpágina de cada uma.
 *
 * Linhas: Sleep profile, Reminders, Format & Language, Interface, Data, About.
 *
 * **Navegação (como no iOS).** Uma só posição contínua ([slide]: 0 = página principal, 1 = a
 * subpágina): tocar numa linha faz a subpágina entrar da direita, por cima da principal, que recua um
 * pouco para a esquerda e escurece. Arrastar para a direita leva a subpágina a seguir o dedo, e o
 * gesto de voltar do Android também; ao largar, a mola das páginas acaba o caminho a partir da
 * velocidade do dedo ([introSwipeDecision]: um lançamento ou passar de metade). O back-b faz o mesmo
 * que o gesto.
 *
 * **Fechar.** O close-b fecha as definições; arrastar para baixo (em qualquer página) leva o ecrã a
 * descer com o dedo ([LocalCoverDrag]) e, ao largar, desce até ao fim ou volta a subir. Fecha-se
 * sempre para onde se estava (o Feed ou as rotinas guardadas ficam por baixo, tal como estavam).
 * Enquanto o dedo arrasta na vertical, o close-b e o back-b saem de cena ([trackChromeHide]).
 *
 * **Conteúdo.**
 * - Sleep profile: a idade (escreve-se na linha: o visto guarda; fora de 5–100 a caixa abana com um
 *   háptico de erro), o cronótipo e o tempo a adormecer (action-menu do Componentes 3).
 * - Reminders: "Bedtime", "Log sleep" e "Hydration" (pedem a autorização de notificações).
 * - Format & Language: o formato das horas e a língua (muda logo a língua da app toda).
 * - Interface: "Advanced textures".
 * - Data: "Automatic backups", "Export my data" e "Restore my data".
 * - About: "View on GitHub" (mostra primeiro o action-menu-big-open-github) e a versão.
 * - O new-update-group fica no topo da página principal, só com uma versão nova.
 * - Voltar (botão, gesto) fecha primeiro o menu, a folha ou a caixa da idade, se estiverem abertos.
 *
 * @param reminderOn o interruptor "Bedtime" como se vê: ligado nas definições e com autorização.
 * @param logSleepOn o interruptor "Log sleep", da mesma forma; [hydrationOn] o "Hydration".
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
    hydrationOn: Boolean,
    onToggleHydration: () -> Unit,
    autoBackupOn: Boolean,
    onToggleAutoBackup: () -> Unit,
    reminderAsks: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onOpenGitHub: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetAdvancedTextures: (Boolean) -> Unit,
    onClose: () -> Unit,
    update: UpdateUi? = null,
    onUpdateOpen: () -> Unit = {},
    onUpdateDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // A língua em que os textos estão (muda com eles).
    val language = AppLanguage.fromTag(stringResource(R.string.locale_tag))
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val effects = LocalVisualEffects.current
    val coverDrag = LocalCoverDrag.current
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val use24Hour = LocalTimeFormat.current.use24Hour
    var menu by remember { mutableStateOf(SettingsMenu.None) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    var githubOpen by remember { mutableStateOf(false) }
    var ageEditing by remember { mutableStateOf(false) }
    val textShadow = remember(density) { Shadow(Color.Black.copy(alpha = 0.24f), Offset.Zero, with(density) { 15.dp.toPx() }) }
    val labelStyle = remember(textShadow) { CycloType.SectionLabel.copy(shadow = textShadow) }
    val chrome = rememberChromeHide()

    // A subpágina escolhida (-1 = a principal) e a que se mostra (fica enquanto sai); [slide] é a
    // posição contínua das duas. Limiar fino, como as páginas (sem paragens antes do fim).
    var page by rememberSaveable { mutableIntStateOf(-1) }
    var shownPage by remember { mutableIntStateOf(page) }
    val slide = remember { Animatable(if (page >= 0) 1f else 0f, visibilityThreshold = 0.0005f) }
    var dragging by remember { mutableStateOf(false) }
    val dragSlide = remember { mutableFloatStateOf(0f) }
    val slideNow: () -> Float = { if (dragging) dragSlide.floatValue else slide.value }
    val moving by remember { derivedStateOf { dragging || slide.isRunning } }
    val pageSpring = if (effects.animationsEnabled) CycloSprings.Page.float else CycloMotion.FadeSpring

    /** Leva as páginas para a subpágina [target] (-1 = a principal), a partir de onde estão. */
    fun goTo(target: Int, velocity: Float = slide.velocity) {
        page = target
        if (target >= 0) shownPage = target
        // Ao mudar de página, o teclado e os menus fecham.
        menu = SettingsMenu.None
        ageEditing = false
        focusManager.clearFocus()
        scope.launch {
            slide.animateTo(if (target >= 0) 1f else 0f, pageSpring, initialVelocity = velocity)
            if (page < 0) shownPage = -1
        }
    }

    val overlayOpen = menu != SettingsMenu.None || githubOpen || ageEditing

    // Arrastar para baixo: o ecrã desce com o dedo (ver [LocalCoverDrag]). O scroll das páginas anda
    // primeiro; o ecrã só segue o que sobra no topo (o mesmo gesto das Introduction, [IntroSwipe]).
    val latestOverlayOpen by rememberUpdatedState(overlayOpen)
    val dismissSwipe = remember(coverDrag) {
        IntroSwipe(object : IntroSwipe.Target {
            override fun begin(): Boolean =
                coverDrag != null && !latestOverlayOpen && !dragging && coverDrag.start()

            override fun drag(pages: Float) = coverDrag?.drag((-pages).coerceAtLeast(0f)) ?: Unit

            override fun release(pagesPerSecond: Float) = coverDrag?.release(-pagesPerSecond) ?: Unit
        }, downOnly = true)
    }

    // Voltar do Android numa subpágina: ela sai para a direita a seguir ao dedo (o alcance das
    // páginas, [CycloMotion.PageBackReach]) e, ao largar, a mola acaba o caminho ou volta.
    PredictiveBackHandler(enabled = page >= 0 && !overlayOpen) { progress ->
        val gesture = BackGestureVelocity()
        try {
            progress.collect { event ->
                gesture.add(event.progress)
                slide.snapTo(1f - event.progress * CycloMotion.PageBackReach)
            }
            goTo(-1, -gesture.progressPerSecond * CycloMotion.PageBackReach)
        } catch (e: CancellationException) {
            scope.launch { slide.animateTo(1f, pageSpring, initialVelocity = -gesture.progressPerSecond * CycloMotion.PageBackReach) }
        }
    }

    // Arrastar para a direita numa subpágina (como o swipe das páginas principais): só começa com um
    // movimento claramente horizontal, fora das zonas de gesto do sistema, e só se nenhum filho o
    // consumir. A subpágina segue o dedo sem atraso.
    val gestureInsets = WindowInsets.systemGestures
    val layoutDirection = LocalLayoutDirection.current
    val latestPage by rememberUpdatedState(page)
    val backSwipe = Modifier.pointerInput(effects.animationsEnabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val width = size.width.toFloat()
            if (latestPage < 0 || latestOverlayOpen || width <= 0f) return@awaitEachGesture
            val left = gestureInsets.getLeft(this@pointerInput, layoutDirection)
            val right = gestureInsets.getRight(this@pointerInput, layoutDirection)
            if (down.position.x < left || down.position.x > width - right) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var claimed: PointerInputChange? = null
            while (claimed == null) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) return@awaitEachGesture
                val total = change.position - down.position
                if (total.x > slop && total.x > abs(total.y) * HorizontalDominance) {
                    change.consume()
                    claimed = change
                } else if (abs(total.y) > slop || total.x < -slop) {
                    return@awaitEachGesture
                } else {
                    val final = awaitPointerEvent(PointerEventPass.Final)
                    if (final.changes.any { it.isConsumed }) return@awaitEachGesture
                }
            }
            val from = slide.value
            val tracker = VelocityTracker()
            tracker.addPosition(claimed.uptimeMillis, claimed.position)
            scope.launch { slide.stop() }
            dragSlide.floatValue = from
            dragging = true
            fun follow(x: Float) {
                dragSlide.floatValue = (from - (x - down.position.x) / width).coerceIn(0f, 1f)
            }
            follow(claimed.position.x)
            var completed = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    completed = false
                    break
                }
                change.consume()
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                follow(change.position.x)
            }
            // Velocidade em páginas por segundo, positiva para fechar (o dedo para a direita).
            val velocity = if (completed) tracker.calculateVelocity().x / width else 0f
            val shown = dragSlide.floatValue
            val close = completed && introSwipeDecision(1f - shown, velocity) > 0
            scope.launch {
                slide.snapTo(shown)
                dragging = false
                if (close) goTo(-1, -velocity) else slide.animateTo(1f, pageSpring, initialVelocity = -velocity)
            }
        }
    }

    fun openMenu(which: SettingsMenu): (Rect) -> Unit = { menuAnchor = it; menu = which }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CycloColors.Background)
            // Em ecrãs de taxa adaptável, só mexer camadas vota 60 Hz: a deslizar pede-se a taxa alta.
            .preferredFrameRate(if (moving) FrameRateCategory.High else FrameRateCategory.Default)
            // Não deixa os toques passarem para o Feed, por baixo.
            .pointerInput(Unit) { detectTapGestures { } }
            .trackChromeHide(chrome)
            .then(with(dismissSwipe) { Modifier.introSwipe() })
            .then(backSwipe),
    ) {
        // O que os menus e a folha desfocam: as duas páginas.
        val pages = rememberBackdropLayer()
        Box(modifier = Modifier.fillMaxSize().backdropSource(pages)) {
            // A página principal: recua para a esquerda e escurece enquanto a subpágina entra.
            SettingsPageFrame(
                title = stringResource(R.string.settings_title),
                buttonIcon = CycloIconPaths.Close,
                buttonDescription = stringResource(R.string.close),
                onButton = onClose,
                chrome = chrome,
                active = page < 0,
                textShadow = textShadow,
                modifier = Modifier.graphicsLayer {
                    val s = slideNow()
                    // Toda tapada: não se desenha (fica composta, com o scroll).
                    alpha = if (s >= 1f) 0f else 1f
                    if (effects.animationsEnabled) translationX = -MainParallax * s * size.width
                },
                veil = { slideNow() },
            ) {
                UpdateSection(update, onUpdateOpen, onUpdateDismiss)
                SettingsPage.entries.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(Modifier.height(RowGap))
                    SettingsNavRow(
                        label = stringResource(entry.title),
                        color = entry.color,
                        icon = entry.icon,
                        onClick = { goTo(entry.ordinal) },
                    )
                }
            }

            val current = shownPage
            if (current >= 0) {
                val entry = SettingsPage.entries[current]
                SettingsPageFrame(
                    title = stringResource(entry.title),
                    buttonIcon = CycloIconPaths.Back,
                    buttonDescription = stringResource(R.string.back),
                    onButton = { goTo(-1, 0f) },
                    chrome = chrome,
                    active = page == current,
                    textShadow = textShadow,
                    modifier = Modifier.graphicsLayer {
                        val s = slideNow()
                        if (effects.animationsEnabled) translationX = (1f - s) * size.width else alpha = s
                    },
                ) {
                    when (entry) {
                        SettingsPage.SleepProfile -> {
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
                            Description(stringResource(R.string.settings_sleep_profile_description), labelStyle)
                        }
                        SettingsPage.Reminders -> {
                            SwitchRow(
                                label = stringResource(R.string.settings_bedtime_reminder),
                                on = reminderOn,
                                onToggle = onToggleReminder,
                                toggleHaptic = reminderOn || !reminderAsks,
                            )
                            Spacer(Modifier.height(RowGap))
                            SwitchRow(
                                label = stringResource(R.string.settings_log_sleep_reminder),
                                on = logSleepOn,
                                onToggle = onToggleLogSleep,
                                toggleHaptic = logSleepOn || !reminderAsks,
                            )
                            Spacer(Modifier.height(RowGap))
                            // hydration (6031:58, Cyclo 3.1): o lembrete da água.
                            SwitchRow(
                                label = stringResource(R.string.settings_hydration_reminder),
                                on = hydrationOn,
                                onToggle = onToggleHydration,
                                toggleHaptic = hydrationOn || !reminderAsks,
                            )
                        }
                        SettingsPage.FormatLanguage -> {
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
                        }
                        SettingsPage.Interface -> {
                            SwitchRow(
                                label = stringResource(R.string.settings_advanced_textures),
                                on = data.advancedTextures,
                                onToggle = { onSetAdvancedTextures(!data.advancedTextures) },
                                // Durante a passagem do desenho, esta linha fica fora da fotografia (sem fantasma).
                                modifier = Modifier.keepLiveDuringCrossfade(),
                            )
                            Description(stringResource(R.string.settings_advanced_textures_description), labelStyle)
                        }
                        SettingsPage.Data -> {
                            // automatic-backups (6031:72, Cyclo 3.1) e, 15 dp abaixo, exportar e repor.
                            SwitchRow(
                                label = stringResource(R.string.settings_auto_backup),
                                on = autoBackupOn,
                                onToggle = onToggleAutoBackup,
                            )
                            Description(stringResource(R.string.settings_auto_backup_description), labelStyle)
                            Spacer(Modifier.height(RowGap))
                            SettingsButton(stringResource(R.string.settings_export_data), onClick = onExport)
                            Spacer(Modifier.height(RowGap))
                            SettingsButton(stringResource(R.string.settings_restore_data), onClick = onRestore)
                            Description(stringResource(R.string.settings_data_description), labelStyle)
                        }
                        SettingsPage.About -> {
                            SettingsButton(stringResource(R.string.settings_github), onClick = { githubOpen = true })
                            // A versão, com a lua do Cyclo antes (6013:574): #A3A2A7 sem mistura nem
                            // sombra (o texto mantém as dele), centrada na linha do texto.
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
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(pages)) {
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

    // Vem depois do voltar das subpáginas: fecha primeiro o que estiver aberto.
    BackHandler(enabled = overlayOpen) {
        when {
            menu != SettingsMenu.None -> menu = SettingsMenu.None
            githubOpen -> githubOpen = false
            else -> ageEditing = false
        }
    }
}

/**
 * Uma página das definições (a principal e cada subpágina têm o mesmo esqueleto do Figma): fundo
 * #010101 opaco, o título a 111 dp com o botão redondo fixo por cima (close-b na principal, back-b
 * nas subpáginas), o conteúdo com scroll a 179 dp e as faixas do topo (87 dp até #010101 a 100 %) e
 * do fundo (48 dp). [veil] escurece a página quando outra entra por cima dela.
 */
@Composable
private fun SettingsPageFrame(
    title: String,
    buttonIcon: String,
    buttonDescription: String,
    onButton: () -> Unit,
    chrome: com.gaebriu.cyclo.ui.effects.ChromeHide,
    active: Boolean,
    textShadow: Shadow,
    modifier: Modifier = Modifier,
    veil: (() -> Float)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtra = (statusTop - 24.dp).coerceAtLeast(0.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val scroll = rememberScrollState()
    val content0 = rememberBackdropLayer()
    // No fim da página a faixa de baixo some (nos últimos 48 dp de scroll), para não tapar a versão.
    val rampPx = with(density) { BottomFadeRamp.toPx() }
    val bottomAmount by remember(scroll, rampPx) {
        derivedStateOf {
            if (scroll.maxValue == Int.MAX_VALUE) 1f else ((scroll.maxValue - scroll.value) / rampPx).coerceIn(0f, 1f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CycloColors.Background)
            // Só a página à frente chega ao TalkBack.
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(content0)
                .background(CycloColors.Background)
                .cycloVerticalScroll(scroll, alwaysBounce = true)
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(top = TitleTop + topExtra, bottom = bottomInset + CycloDimens.ScreenMargin),
        ) {
            BasicText(
                text = title,
                style = CycloType.Greeting.copy(shadow = textShadow),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(TitleToContent))
            content()
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(content0)) {
            // Cyclo 3: em cima até #010101 a 100 % (87 dp), em baixo 48 dp.
            MainEdgeFades(topExtra = topExtra, bottomAmount = { bottomAmount }, topAlpha = 1f, bottomHeight = SettingsBottomFade)

            // close-b / back-b (6012:130, 6013:316): fixo, a 48 dp do topo e 24 dp da esquerda (a
            // zona de toque sai 4,5 dp). Sai de cena enquanto o dedo arrasta na vertical.
            CycloCircleButton(
                iconPath = buttonIcon,
                contentDescription = buttonDescription,
                onClick = onButton,
                modifier = Modifier
                    .padding(top = statusTop + 24.dp, start = CycloDimens.ScreenMargin)
                    .offset(x = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2, y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2)
                    .chromeHidden(chrome),
            )
        }

        // Véu #010101 a 24 % quando outra página está por cima (como os ecrãs que recuam).
        if (veil != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = CycloMotion.BackgroundOverlayAlpha * veil().coerceIn(0f, 1f) }
                    .background(CycloColors.Background),
            )
        }
    }
}

/**
 * Uma linha da página principal (sleep-profile-group-b…, 345 x 48, raio 15, #101011 em Luminosity):
 * o círculo de 24 dp na cor da página, com o ícone de 16 dp em gradiente vertical #FCFCFE de 100 %
 * a 63 %, e o nome a 54 dp da esquerda. Abre a subpágina.
 */
@Composable
private fun SettingsNavRow(label: String, color: Color, icon: String, onClick: () -> Unit) {
    val path = remember(icon) { PathParser().parsePathString(icon).toPath() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            .cycloPressable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .cycloCard(shadowColor = null),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.padding(start = RowInset).size(NavCircle)) {
            drawCircle(color = color)
            val iconPx = NavIcon.toPx()
            val inset = (size.width - iconPx) / 2f
            val bounds = path.getBounds()
            translate(inset, inset) {
                val scale = density
                // O caminho está em dp (grelha de 16): escala-se para píxeis.
                drawContext.transform.scale(scale, scale, Offset.Zero)
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        listOf(CycloColors.TextPrimary, CycloColors.TextPrimary.copy(alpha = 0.63f)),
                        startY = bounds.top,
                        endY = bounds.bottom,
                    ),
                )
            }
        }
        BasicText(
            text = label,
            style = CycloType.CardTitle,
            modifier = Modifier.padding(start = NavTextStart, end = RowInset, top = RowTextVertical, bottom = RowTextVertical),
        )
    }
}

/**
 * new-update-group: só com uma versão nova no GitHub (e não dispensada há menos de 12 h), no topo da
 * página principal. Entra e sai com zoom, fade e o lugar a abrir e a fechar. O que se vê segue a
 * atualização disponível; um aviso que deixou de ser o disponível nunca responde, mesmo enquanto sai.
 */
@Composable
private fun UpdateSection(update: UpdateUi?, onUpdateOpen: () -> Unit, onUpdateDismiss: () -> Unit) {
    val animationsEnabled = LocalVisualEffects.current.animationsEnabled
    val latestUpdate by rememberUpdatedState(update)
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
        enter = if (animationsEnabled) {
            fadeIn(CycloSprings.Enter.float) + scaleIn(CycloSprings.Enter.float, initialScale = CycloMotion.MenuStartScale) +
                expandVertically(CycloSprings.Enter.intSize, expandFrom = Alignment.Top)
        } else {
            fadeIn(CycloMotion.FadeSpring)
        },
        exit = if (animationsEnabled) {
            fadeOut(CycloSprings.Exit.float) + scaleOut(CycloSprings.Exit.float, targetScale = CycloMotion.MenuStartScale) +
                shrinkVertically(CycloSprings.Exit.intSize, shrinkTowards = Alignment.Top)
        } else {
            fadeOut(CycloMotion.FadeSpring)
        },
    ) {
        shownUpdate?.let { first ->
            // Outra versão no lugar da que se via: o conteúdo funde-se e a altura acompanha. O mesmo
            // aviso não anima: a chave é a tag.
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
                        onOpen = forShown(shown.tag, onUpdateOpen),
                        onDismiss = forShown(shown.tag, onUpdateDismiss),
                    )
                    Spacer(Modifier.height(SectionGap))
                }
            }
        }
    }
}

/** Os ícones das linhas da página principal (Settings 6012:34), numa grelha de 16 x 16. */
private object SettingsIcons {
    /** person (6013:86). */
    const val Person = "M5.8858 6.967C5.3039 6.3851 5.013 5.6804 5.013 4.8528C5.013 4.0253 5.3039 3.3222 5.8858 2.7435C6.4677 2.1647 7.1724 1.8753 8 1.8753C8.8276 1.8753 9.5323 2.1647 10.1142 2.7435C10.6961 3.3222 10.987 4.0253 10.987 4.8528C10.987 5.6804 10.6961 6.3851 10.1142 6.967C9.5323 7.5489 8.8276 7.8398 8 7.8398C7.1724 7.8398 6.4677 7.5489 5.8858 6.967ZM2.3463 12.0472L2.3463 11.608C2.3463 11.1737 2.4593 10.7731 2.6852 10.4062C2.9109 10.0393 3.2132 9.7599 3.592 9.5682C4.2998 9.2174 5.0218 8.9528 5.758 8.7743C6.4942 8.5958 7.2416 8.5065 8 8.5065C8.771 8.5065 9.5246 8.5942 10.2608 8.7695C10.9971 8.9449 11.7128 9.208 12.408 9.5587C12.7868 9.7504 13.0891 10.0282 13.3148 10.392C13.5407 10.7558 13.6537 11.1611 13.6537 11.608L13.6537 12.0472C13.6537 12.5394 13.4823 12.9569 13.1395 13.2997C12.7967 13.6424 12.3792 13.8138 11.887 13.8138L4.113 13.8138C3.6208 13.8138 3.2033 13.6424 2.8605 13.2997C2.5177 12.9569 2.3463 12.5394 2.3463 12.0472Z"

    /** notifications (6013:74). */
    const val Notifications = "M3.2297 12.987C2.9843 12.987 2.7758 12.9011 2.604 12.7293C2.4322 12.5576 2.3463 12.349 2.3463 12.1037C2.3463 11.8582 2.4322 11.6496 2.604 11.4778C2.7758 11.3062 2.9843 11.2203 3.2297 11.2203L3.5667 11.2203L3.5667 6.874C3.5667 5.8701 3.8617 4.9706 4.4518 4.1753C5.0419 3.3802 5.8191 2.8621 6.7833 2.621L6.7833 2.2297C6.7833 1.8891 6.9009 1.6012 7.1362 1.366C7.3715 1.1307 7.6594 1.013 8 1.013C8.3406 1.013 8.6285 1.1307 8.8638 1.366C9.0991 1.6012 9.2167 1.8891 9.2167 2.2297L9.2167 2.621C10.1872 2.8621 10.966 3.3787 11.553 4.1707C12.1399 4.9627 12.4333 5.8638 12.4333 6.874L12.4333 11.2203L12.7703 11.2203C13.0157 11.2203 13.2242 11.3062 13.396 11.4778C13.5678 11.6496 13.6537 11.8582 13.6537 12.1037C13.6537 12.349 13.5678 12.5576 13.396 12.7293C13.2242 12.9011 13.0157 12.987 12.7703 12.987L3.2297 12.987ZM8.0095 15.0907C7.6114 15.0907 7.2708 14.9507 6.9877 14.6707C6.7046 14.3907 6.563 14.0517 6.563 13.6537L9.4463 13.6537C9.4463 14.0517 9.3064 14.3907 9.0265 14.6707C8.7465 14.9507 8.4075 15.0907 8.0095 15.0907Z"

    /** globe (6013:106). */
    const val Globe = "M8 14.987C7.0276 14.987 6.1169 14.8039 5.2682 14.4377C4.4195 14.0714 3.6813 13.5744 3.0535 12.9465C2.4256 12.3187 1.9286 11.5805 1.5623 10.7318C1.1961 9.8831 1.013 8.9724 1.013 8C1.013 7.0276 1.1961 6.1169 1.5623 5.2682C1.9286 4.4195 2.4256 3.6813 3.0535 3.0535C3.6813 2.4256 4.4195 1.9286 5.2682 1.5623C6.1169 1.1961 7.0276 1.013 8 1.013C8.9724 1.013 9.8831 1.1961 10.7318 1.5623C11.5805 1.9286 12.3187 2.4256 12.9465 3.0535C13.5744 3.6813 14.0714 4.4195 14.4377 5.2682C14.8039 6.1169 14.987 7.0276 14.987 8C14.987 8.9724 14.8039 9.8831 14.4377 10.7318C14.0714 11.5805 13.5744 12.3187 12.9465 12.9465C12.3187 13.5744 11.5805 14.0714 10.7318 14.4377C9.8831 14.8039 8.9724 14.987 8 14.987ZM8 13.2203C9.3507 13.2203 10.5097 12.7853 11.4772 11.9152C12.4445 11.0452 13.0004 9.9631 13.145 8.6688C13.145 8.6351 13.1438 8.6001 13.1413 8.5638C13.1389 8.5276 13.1377 8.4974 13.1377 8.4732C13.0381 8.6886 12.885 8.8627 12.6783 8.9953C12.4716 9.1279 12.236 9.1942 11.9717 9.1942L10.7043 9.1942C10.3066 9.1942 9.9661 9.0526 9.6828 8.7693C9.3996 8.4861 9.258 8.1456 9.258 7.7478L9.258 7.0247L6.3652 7.0247L6.3652 5.6537C6.3652 5.2533 6.5068 4.9106 6.79 4.6255C7.0733 4.3404 7.4139 4.1978 7.8117 4.1978L8.5348 4.1978C8.5348 3.9024 8.6121 3.6423 8.7667 3.4177C8.9212 3.1929 9.1111 3.0382 9.3362 2.9537C9.1202 2.9009 8.9014 2.8587 8.68 2.8272C8.4586 2.7955 8.2319 2.7797 8 2.7797C6.5362 2.7797 5.3002 3.2838 4.292 4.292C3.2838 5.3002 2.7797 6.5362 2.7797 8C2.7797 8.0314 2.7813 8.0628 2.7845 8.0942C2.7876 8.1256 2.7892 8.157 2.7892 8.1883L5.5478 8.1883C6.3503 8.1883 7.033 8.4699 7.596 9.033C8.159 9.596 8.4405 10.2774 8.4405 11.0773L8.4405 11.8043L6.271 11.8043L6.271 12.9123C6.5434 13.0119 6.8227 13.0881 7.1088 13.141C7.3948 13.1939 7.6919 13.2203 8 13.2203Z"

    /** mobile (6013:126). */
    const val Mobile = "M4.7892 15.6537C4.2968 15.6537 3.8793 15.4823 3.5367 15.1395C3.1939 14.7967 3.0225 14.3792 3.0225 13.887L3.0225 2.113C3.0225 1.6208 3.1939 1.2033 3.5367 0.8605C3.8793 0.5177 4.2968 0.3463 4.7892 0.3463L11.2108 0.3463C11.7032 0.3463 12.1207 0.5177 12.4633 0.8605C12.8061 1.2033 12.9775 1.6208 12.9775 2.113L12.9775 3.9065C13.1838 4.0094 13.3481 4.1567 13.4703 4.3485C13.5926 4.5403 13.6537 4.7512 13.6537 4.9812L13.6537 6.2673C13.6537 6.4973 13.5926 6.7098 13.4703 6.9047C13.3481 7.0997 13.1838 7.2486 12.9775 7.3515L12.9775 13.887C12.9775 14.3792 12.8061 14.7967 12.4633 15.1395C12.1207 15.4823 11.7032 15.6537 11.2108 15.6537L4.7892 15.6537ZM8.5268 4.0392C8.6703 3.8957 8.742 3.7201 8.742 3.5123C8.742 3.3046 8.6703 3.1289 8.5268 2.9855C8.3834 2.8421 8.2078 2.7703 8 2.7703C7.7922 2.7703 7.6166 2.8421 7.4732 2.9855C7.3297 3.1289 7.258 3.3046 7.258 3.5123C7.258 3.7201 7.3297 3.8957 7.4732 4.0392C7.6166 4.1826 7.7922 4.2543 8 4.2543C8.2078 4.2543 8.3834 4.1826 8.5268 4.0392Z"

    /** folder (6013:146). */
    const val Folder = "M2.7797 13.6537C2.2874 13.6537 1.8699 13.4823 1.5272 13.1395C1.1844 12.7967 1.013 12.3792 1.013 11.887L1.013 4.113C1.013 3.6208 1.1844 3.2033 1.5272 2.8605C1.8699 2.5177 2.2874 2.3463 2.7797 2.3463L5.7963 2.3463C6.0307 2.3463 6.2551 2.3907 6.4695 2.4793C6.6841 2.568 6.8744 2.6954 7.0405 2.8617L8 3.821L13.2203 3.821C13.7126 3.821 14.1301 3.9924 14.4728 4.3352C14.8156 4.6779 14.987 5.0954 14.987 5.5877L14.987 11.887C14.987 12.3792 14.8156 12.7967 14.4728 13.1395C14.1301 13.4823 13.7126 13.6537 13.2203 13.6537L2.7797 13.6537Z"

    /** info (6013:178). */
    const val Info = "M8.574 11.2423C8.73 11.0874 8.808 10.8956 8.808 10.6667L8.808 8.1413C8.808 7.9124 8.73 7.7206 8.574 7.5657C8.4179 7.4108 8.2266 7.3333 8 7.3333C7.7734 7.3333 7.5821 7.4108 7.426 7.5657C7.27 7.7206 7.192 7.9124 7.192 8.1413L7.192 10.6667C7.192 10.8956 7.27 11.0874 7.426 11.2423C7.5821 11.3972 7.7734 11.4747 8 11.4747C8.2266 11.4747 8.4179 11.3972 8.574 11.2423ZM8.6068 5.9425C8.7723 5.7786 8.855 5.5756 8.855 5.3333C8.855 5.0911 8.7731 4.8881 8.6092 4.7242C8.4453 4.5603 8.2422 4.4783 8 4.4783C7.7578 4.4783 7.5547 4.5603 7.3908 4.7242C7.2269 4.8881 7.145 5.0911 7.145 5.3333C7.145 5.5756 7.2277 5.7786 7.3932 5.9425C7.5586 6.1064 7.7609 6.1883 8 6.1883C8.2391 6.1883 8.4414 6.1064 8.6068 5.9425ZM8 14.987C7.0276 14.987 6.1169 14.8039 5.2682 14.4377C4.4195 14.0714 3.6813 13.5744 3.0535 12.9465C2.4256 12.3187 1.9286 11.5805 1.5623 10.7318C1.1961 9.8831 1.013 8.9724 1.013 8C1.013 7.0276 1.1961 6.1169 1.5623 5.2682C1.9286 4.4195 2.4256 3.6813 3.0535 3.0535C3.6813 2.4256 4.4195 1.9286 5.2682 1.5623C6.1169 1.1961 7.0276 1.013 8 1.013C8.9724 1.013 9.8831 1.1961 10.7318 1.5623C11.5805 1.9286 12.3187 2.4256 12.9465 3.0535C13.5744 3.6813 14.0714 4.4195 14.4377 5.2682C14.8039 6.1169 14.987 7.0276 14.987 8C14.987 8.9724 14.8039 9.8831 14.4377 10.7318C14.0714 11.5805 13.5744 12.3187 12.9465 12.9465C12.3187 13.5744 11.5805 14.0714 10.7318 14.4377C9.8831 14.8039 8.9724 14.987 8 14.987Z"
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
 * curto que vai morrendo (dp). O háptico ([CycloHaptics.reject]) toca quem chama.
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
