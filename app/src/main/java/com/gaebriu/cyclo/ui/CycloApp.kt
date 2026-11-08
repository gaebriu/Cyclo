package com.gaebriu.cyclo.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gaebriu.cyclo.data.sleepMeterStart
import com.gaebriu.cyclo.ui.main.SleepLogSheet
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.FilePicker
import com.gaebriu.cyclo.reminder.BedtimeReminder
import com.gaebriu.cyclo.reminder.CycloSync
import com.gaebriu.cyclo.ui.components.BottomSheetHost
import com.gaebriu.cyclo.ui.components.CycloBigSheet
import com.gaebriu.cyclo.ui.settings.LoadingDataOverlay
import com.gaebriu.cyclo.ui.settings.loadingDataBlur
import com.gaebriu.cyclo.ui.settings.rememberLoadingDataState
import com.gaebriu.cyclo.widget.BedtimeWidgets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gaebriu.cyclo.data.CycloViewModel
import com.gaebriu.cyclo.data.RoutineDraft
import com.gaebriu.cyclo.ui.components.CycloMessageBar
import androidx.compose.runtime.CompositionLocalProvider
import com.gaebriu.cyclo.ui.effects.BackGestureVelocity
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.ScreenCrossfade
import com.gaebriu.cyclo.ui.effects.ScreenCrossfadeOverlay
import com.gaebriu.cyclo.ui.effects.LocalScreenCrossfade
import com.gaebriu.cyclo.ui.effects.backdropSource
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.settings.SettingsScreen
import com.gaebriu.cyclo.ui.settings.UpdateUi
import com.gaebriu.cyclo.ui.screens.Introduction0Screen
import com.gaebriu.cyclo.update.CycloUpdates
import com.gaebriu.cyclo.update.DownloadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.gaebriu.cyclo.ui.time.ProvideTimeFormat
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.main.FeedRevealItems
import com.gaebriu.cyclo.ui.main.MainActions
import com.gaebriu.cyclo.ui.main.MainScreen
import com.gaebriu.cyclo.ui.main.NavigationBarHeight
import com.gaebriu.cyclo.ui.screens.IntroductionFlow
import com.gaebriu.cyclo.ui.screens.IntroductionMode
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** O que está por cima do Feed: nada, o tutorial ou o editor de rotinas. */
private const val OverlayNone = 0
private const val OverlayTutorial = 1
private const val OverlayEditor = 2
private const val OverlaySettings = 3

/** A primeira abertura depois de uma atualização: o Introduction 0 e, sozinho, o Feed. */
private const val OverlayUpdated = 4

/**
 * A app: o Feed e as rotinas guardadas por baixo e, por cima, o tutorial (só até ser feito), o
 * editor de rotinas (Introduction 2 a 5, ao criar ou editar) ou as definições.
 *
 * - O que está por cima sobe de baixo e desce para sair, como o Introduction 1 sobre o 0: o Feed
 *   recua por baixo (zoom out, desfoque e véu escuro) e volta a avançar quando ele desce.
 * - No fim do tutorial, o Introduction 6 desce e o Feed aparece com a sua entrada em cascata
 *   (o mesmo acontece ao abrir a app depois do tutorial). Só acontece uma vez por sessão.
 * - No primeiro passo do editor, e nas definições, o gesto de voltar faz o ecrã descer a seguir ao dedo.
 * - As horas seguem o formato escolhido nas definições ou, sem escolha, o do sistema.
 * - Sem animações, os movimentos passam a fades.
 *
 * Só se mostra depois de os dados estarem lidos (o ecrã de abertura espera, ver MainActivity).
 */
@Composable
fun CycloApp(viewModel: CycloViewModel) {
    val data = viewModel.data.collectAsState().value ?: return
    val message by viewModel.message.collectAsState()
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()

    // O tutorial decide-se uma vez, ao abrir: a marca "feito" é escrita no Introduction 5, mas o
    // tutorial continua até ao fim do 6.
    // Depois de uma atualização (o versionCode guardado é outro), a primeira abertura mostra o
    // Introduction 0 e passa sozinha para o Feed, sem o tutorial. Só com o tutorial já feito: numa
    // instalação nova vale o tutorial. Os dados não mudam.
    val context = LocalContext.current
    val versionCode = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt() }.getOrDefault(0)
    }
    val justUpdated = data.onboarded && data.lastRunVersion != versionCode
    var overlay by rememberSaveable {
        mutableIntStateOf(
            when {
                !data.onboarded -> OverlayTutorial
                justUpdated -> OverlayUpdated
                else -> OverlayNone
            },
        )
    }
    var overlayOpen by rememberSaveable { mutableStateOf(!data.onboarded || justUpdated) }
    // action-menu-log-sleep aberto (pedido pelo "Log sleep" da Tracking).
    var sleepSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Esta versão já abriu: guarda-se logo (um arranque normal a seguir não volta a mostrar nada).
    LaunchedEffect(versionCode) {
        if (data.lastRunVersion != versionCode) viewModel.markVersionSeen(versionCode)
    }
    // Cada abertura do editor tem uma chave nova, para começar sempre do zero.
    var editorKey by rememberSaveable { mutableLongStateOf(0L) }
    var editorRoutineId by rememberSaveable { mutableLongStateOf(-1L) }

    // cover: 0 = nada por cima, 1 = o que está por cima tapa tudo.
    // Limiar fino (Beta 2.2): com o de omissão (0,01) a mola parava ~24 px antes e saltava no fim.
    val cover = remember { Animatable(if (overlayOpen) 1f else 0f, visibilityThreshold = 0.0005f) }
    // A velocidade com que o gesto de voltar largou o ecrã de cima (Beta 2.2): a mola continua dela.
    val coverRelease = remember { floatArrayOf(0f) }
    LaunchedEffect(overlayOpen) {
        val v = coverRelease[0]
        coverRelease[0] = 0f
        val target = if (overlayOpen) 1f else 0f
        if (v != 0f) cover.animateTo(target, CycloMotion.ScreenRiseSpring, initialVelocity = v) else cover.animateTo(target, CycloMotion.ScreenRiseSpring)
        if (!overlayOpen) overlay = OverlayNone
    }

    // Entrada do Feed: uma vez por sessão, quando ele fica à vista pela primeira vez.
    var revealPlayed by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { StaggeredReveal(FeedRevealItems.Count, startRevealed = revealPlayed) }
    val glow = remember { Animatable(if (revealPlayed) 1f else 0f) }
    val feedVisible = !((overlay == OverlayTutorial || overlay == OverlayUpdated) && overlayOpen)
    LaunchedEffect(feedVisible) {
        if (feedVisible && !revealPlayed) {
            revealPlayed = true
            // Uma só composição (Beta 2.2, como o "Hello!" e o brilho do Introduction 0): o brilho
            // nasce numa mola e os blocos só começam quando ele já vai a [FeedRevealGlowStart],
            // entrando sobre a luz a crescer. Antes eram dois relógios lançados juntos.
            launch { glow.animateTo(1f, CycloSprings.FeedGlow.float) }
            snapshotFlow { glow.value }.first { it >= CycloMotion.FeedRevealGlowStart }
            reveal.play()
        }
    }

    fun openEditor(routineId: Long) {
        editorRoutineId = routineId
        editorKey += 1
        overlay = OverlayEditor
        overlayOpen = true
    }

    // Seletores de ficheiros do sistema e a autorização de notificações. Ficam aqui (e não nas
    // definições) porque o "Try again" da barra de baixo pode abri-los depois de as fechar.
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        viewModel::exportTo,
    )
    // Muitos gestores de ficheiros marcam o .json como octet-stream: aceitam-se os dois.
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::readBackup)
    fun openPicker(picker: FilePicker) {
        try {
            when (picker) {
                FilePicker.Export -> exportPicker.launch(viewModel.backupFileName())
                FilePicker.Restore -> restorePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
        } catch (e: ActivityNotFoundException) {
            if (picker == FilePicker.Export) viewModel.exportTo(null) else viewModel.readBackup(null)
        }
    }
    LaunchedEffect(viewModel) { viewModel.pickerRequests.collect { openPicker(it) } }

    // O interruptor "Bedtime" mostra-se ligado só com a autorização dada; volta a ver-se ao regressar
    // à app (pode ter sido tirada nas definições do sistema).
    var notificationsAllowed by remember { mutableStateOf(BedtimeReminder.isPermitted(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestData by rememberUpdatedState(data)

    // O lápis do widget: abre o editor dessa rotina (com o tutorial feito e se ela ainda existir).
    val editRequest by viewModel.editRequest.collectAsState()
    LaunchedEffect(editRequest) {
        val id = editRequest ?: return@LaunchedEffect
        viewModel.consumeEditRequest()
        if (latestData.onboarded && overlay != OverlayTutorial && latestData.routines.any { it.id == id }) openEditor(id)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Atualizações no GitHub: uma vez por arranque a frio (voltar à app não volta a verificar).
                CycloUpdates.checkIfDue(context)
                val allowed = BedtimeReminder.isPermitted(context)
                if (allowed != notificationsAllowed) {
                    notificationsAllowed = allowed
                    CycloSync.apply(context, latestData, animateWidgets = false)
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = BedtimeReminder.isPermitted(context)
        if (granted && notificationsAllowed) {
            viewModel.setBedtimeReminder(true)
            // A preferência costuma já estar ligada (é o valor por omissão): gravar o mesmo valor não
            // muda o DataStore e o CycloSync não reagendava. E este resultado chega antes do ON_RESUME,
            // que por isso também já não via diferença. Agenda-se aqui o lembrete desta noite (se a
            // preferência estava desligada, a escrita acima também reagenda, pelo CycloSync).
            CycloSync.refresh(context)
        } else {
            viewModel.notificationsDenied()
        }
    }
    val reminderOn = data.bedtimeReminder && notificationsAllowed
    fun toggleReminder() {
        when {
            reminderOn -> viewModel.setBedtimeReminder(false)
            notificationsAllowed -> viewModel.setBedtimeReminder(true)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            // Autorizado, mas as notificações da app estão desligadas no sistema.
            else -> viewModel.notificationsDenied()
        }
    }

    /** "Add as a widget": pede ao lançador para fixar o Routine Widget da rotina [routineId]. */
    fun pinWidget(routineId: Long) {
        if (!BedtimeWidgets.requestPin(context, routineId)) viewModel.widgetPinUnsupported()
    }

    /** "Add as a widget" do menu do Wake Time do Feed: o Wake Time Widget. */
    fun pinWakeWidget() {
        if (!BedtimeWidgets.requestPinWakeTime(context)) viewModel.widgetPinUnsupported()
    }

    val gitHubUrl = stringResource(R.string.github_url)
    val pendingRestore by viewModel.pendingRestore.collectAsState()
    val restoring by viewModel.restoring.collectAsState()

    val actions = remember(viewModel) {
        MainActions(
            onCreateRoutine = { openEditor(-1L) },
            onEditRoutine = { openEditor(it.id) },
            onDeleteRoutine = viewModel::deleteRoutine,
            onToggleRoutine = { viewModel.setRoutineActive(it.id, !it.active) },
            onAddWidget = { routine -> pinWidget(routine.id) },
            onAddWakeWidget = { pinWakeWidget() },
            onCaffeineChange = viewModel::changeCaffeine,
            onCaffeineReset = viewModel::resetCaffeine,
            onToggleFactor = viewModel::toggleFactor,
            onMoodChange = viewModel::setMood,
            onOpenSettings = {
                overlay = OverlaySettings
                overlayOpen = true
            },
            onReorderRoutines = { ordered -> viewModel.reorderRoutines(ordered.map { it.id }) },
            onReorderFeed = viewModel::setFeedOrder,
            onLogSleep = { sleepSheetOpen = true },
            onDismissSleepPrompt = viewModel::dismissSleepPrompt,
            onReorderTracking = viewModel::setTrackingOrder,
        )
    }
    val sleepPromptDismissed by viewModel.sleepPromptDismissed.collectAsState()

    // Tracking: o dia conta como "aberto" quando a app está à frente depois da hora de acordar de
    // hoje. Vê-se ao voltar à frente e a cada minuto enquanto se vê (a hora pode passar com a app
    // aberta). Só a atividade marca: widgets e alarmes não abrem a app.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.markOpened()
                delay(OpenedCheckMs)
            }
        }
    }

    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    // O Feed só precisa de existir quando se vê (durante o tutorial está todo tapado).
    val showMain by remember { derivedStateOf { (overlay != OverlayTutorial && overlay != OverlayUpdated) || cover.value < 1f } }

    // O new-update-group das definições: a versão nova (se houver e não tiver sido dispensada há
    // menos de 12 horas, ou se já estiver a descarregar) e o estado do download.
    val availableUpdate by CycloUpdates.available.collectAsState()
    val dismissedUntil by CycloUpdates.dismissedUntil.collectAsState()
    val updateDownload by CycloUpdates.download.collectAsState()
    val updateUi = availableUpdate
        ?.takeIf { updateDownload != DownloadState.Idle || System.currentTimeMillis() >= dismissedUntil }
        ?.let { UpdateUi(it.tag, it.version.label(), updateDownload, canDownload = it.apkUrl != null) }

    // Instalar: o instalador nativo do Android (o utilizador confirma lá). Se não abrir, a barra de
    // baixo diz e "Try again" tenta outra vez.
    fun installUpdate() {
        val file = (CycloUpdates.download.value as? DownloadState.Done)?.file
        if (file == null || !CycloUpdates.install(context, file)) viewModel.updateInstallFailed { installUpdate() }
    }
    // O download acabou: o instalador abre logo (uma vez). Se o utilizador sair dele sem instalar,
    // o grupo fica com "Install update". Um erro vai para a barra de baixo (undo-action) com
    // "Try again", que descarrega de novo, e o grupo volta ao "Download now".
    LaunchedEffect(updateDownload) {
        when (val state = updateDownload) {
            is DownloadState.Done -> {
                // Com a app em segundo plano, o Android bloqueia abrir o instalador: espera-se que o
                // utilizador volte (o pedido só se gasta quando o instalador pode mesmo abrir).
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                if (CycloUpdates.takeAutoInstall()) installUpdate()
            }
            is DownloadState.Failed -> {
                CycloUpdates.resetDownload()
                viewModel.updateDownloadFailed(state.reason) {
                    CycloUpdates.available.value?.let { CycloUpdates.startDownload(context, it) }
                }
            }
            else -> Unit
        }
    }
    // Tudo o que fica por baixo da barra de baixo (undo-action), que o desfoca.
    val screens = rememberBackdropLayer()
    // loading-data: o ecrã por baixo dele desfoca-se a si próprio, em tempo real, com o véu.
    val loadingData = rememberLoadingDataState(restoring)

    // "Advanced textures" e língua: o ecrã antigo dissolve-se no novo (ver ScreenCrossfade).
    val crossfade = remember { ScreenCrossfade() }
    // O desenho que está a valer. O interruptor segue a preferência guardada; o desenho muda por
    // baixo da fotografia do ecrã, e o interruptor fica fora dela (keepLive): continua a deslizar, vivo.
    var appliedTextures by remember { mutableStateOf(data.advancedTextures) }
    LaunchedEffect(data.advancedTextures) {
        val target = data.advancedTextures
        if (target == appliedTextures) return@LaunchedEffect
        crossfade.run(screens, keepLive = true) { appliedTextures = target }
    }
    val appEffects = remember(effects, appliedTextures) { effects.copy(advancedTextures = appliedTextures) }

    // A língua dos textos que se veem: a mudança de língua espera por ela antes de desvanecer.
    val localeTag by rememberUpdatedState(stringResource(R.string.locale_tag))
    fun changeLanguage(language: com.gaebriu.cyclo.ui.i18n.AppLanguage) {
        scope.launch {
            // Primeiro o menu acaba de fechar; a fotografia é do ecrã das definições parado.
            if (effects.animationsEnabled) delay((MenuCloseSettleMs * animatorScale(context)).toLong())
            val before = localeTag
            crossfade.run(
                screens,
                ready = { withTimeoutOrNull(LanguageChangeTimeoutMs) { snapshotFlow { localeTag }.first { it != before } } },
            ) { com.gaebriu.cyclo.ui.i18n.AppLanguage.set(context, language) }
        }
    }

    CompositionLocalProvider(LocalVisualEffects provides appEffects, LocalScreenCrossfade provides crossfade) {
    ProvideTimeFormat(data.use24Hour) {
    Box(modifier = Modifier.fillMaxSize().background(CycloColors.Background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(screens)
                .loadingDataBlur(loadingData, effects),
        ) {
        if (showMain) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Todo tapado (editor ou definições abertos): não se desenha, mas fica composto
                        // (mantém a página e o scroll). Antes continuava a desenhar-se por baixo, com o
                        // desfoque do ecrã inteiro, e pesava em cada frame das definições.
                        alpha = if (cover.value >= 1f) 0f else 1f
                        if (effects.animationsEnabled) {
                            val c = cover.value
                            val s = 1f - (1f - CycloMotion.BackgroundScale) * c
                            scaleX = s
                            scaleY = s
                            renderEffect = if (effects.blurEnabled && c > 0f) {
                                val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * c
                                BlurEffect(sigma, sigma, TileMode.Clamp)
                            } else {
                                null
                            }
                        }
                    }
                    // Tapado pelo tutorial ou pelo editor: fica fora do alcance do TalkBack.
                    .then(if (overlayOpen) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                MainScreen(
                    data = data,
                    actions = actions,
                    reveal = reveal,
                    glow = { glow.value },
                    sleepPromptDismissed = sleepPromptDismissed,
                )
                // Véu #010101 a 24 % quando o Feed está recuado.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = CycloMotion.BackgroundOverlayAlpha * cover.value }
                        .background(CycloColors.Background),
                )
            }
        }

        // Vem DEPOIS do Feed (para ter prioridade sobre o voltar das rotinas guardadas) e ANTES do
        // editor: no primeiro passo, o gesto de voltar faz o editor descer a seguir ao dedo. Os passos
        // seguintes e os menus abertos têm os seus próprios gestos, declarados depois (com prioridade).
        PredictiveBackHandler(enabled = (overlay == OverlayEditor || overlay == OverlaySettings) && overlayOpen) { progress ->
            // Beta 2.2: o mesmo alcance das páginas e, ao largar, a velocidade do dedo.
            val gesture = BackGestureVelocity()
            try {
                progress.collect { event ->
                    gesture.add(event.progress)
                    cover.snapTo(1f - event.progress * CycloMotion.PageBackReach)
                }
                coverRelease[0] = -gesture.progressPerSecond * CycloMotion.PageBackReach
                overlayOpen = false
            } catch (e: CancellationException) {
                scope.launch {
                    cover.animateTo(1f, CycloMotion.ScreenRiseSpring, initialVelocity = -gesture.progressPerSecond * CycloMotion.PageBackReach)
                }
            }
        }

        if (overlay != OverlayNone) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val c = cover.value
                        if (effects.animationsEnabled) {
                            translationY = (1f - c) * size.height
                        } else {
                            alpha = c
                        }
                    },
            ) {
                if (overlay == OverlayUpdated) {
                    // Depois de uma atualização: o "Hello!" do Introduction 0 e, no fim dele, o
                    // ecrã desce e o Feed aparece com a sua entrada. Voltar salta a espera.
                    Introduction0Screen(onGetStarted = {}, showButtons = false)
                    LaunchedEffect(Unit) {
                        delay(CycloMotion.LettersStartDelayMs + CycloMotion.HelloDurationMs + CycloMotion.AllSetHoldMs)
                        overlayOpen = false
                    }
                    BackHandler(enabled = overlayOpen) { overlayOpen = false }
                } else if (overlay == OverlayTutorial) {
                    IntroductionFlow(
                        mode = IntroductionMode.Tutorial,
                        nextRoutineNumber = data.routines.size + 1,
                        onSave = { draft, onSaved, onFailed ->
                            viewModel.addRoutine(draft, completeOnboarding = true, onSaved = onSaved, onFailed = onFailed)
                        },
                        onFinished = { overlayOpen = false },
                        onAddWidget = { pinWidget(nextRoutineId(data)) },
                    )
                } else if (overlay == OverlaySettings) {
                    SettingsScreen(
                        data = data,
                        reminderOn = reminderOn,
                        onSetUse24Hour = viewModel::setUse24Hour,
                        onSetAge = viewModel::setAge,
                        onSetChronotype = viewModel::setChronotype,
                        onSetFallAsleep = viewModel::setFallAsleep,
                        onToggleReminder = ::toggleReminder,
                        onExport = { openPicker(FilePicker.Export) },
                        onRestore = { openPicker(FilePicker.Restore) },
                        onOpenGitHub = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(gitHubUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (e: ActivityNotFoundException) {
                                viewModel.gitHubOpenFailed()
                            }
                        },
                        onSetLanguage = ::changeLanguage,
                        onSetAdvancedTextures = viewModel::setAdvancedTextures,
                        onClose = { overlayOpen = false },
                        update = updateUi,
                        onUpdateDownload = {
                            availableUpdate?.let { update ->
                                if (update.apkUrl != null) {
                                    CycloUpdates.startDownload(context, update)
                                } else if (!CycloUpdates.openReleasePage(context, update)) {
                                    viewModel.gitHubOpenFailed()
                                }
                            }
                        },
                        onUpdateCancel = CycloUpdates::cancelDownload,
                        onUpdateInstall = ::installUpdate,
                        onUpdateDismiss = {
                            CycloUpdates.cancelDownload()
                            CycloUpdates.dismiss(context)
                            viewModel.updateDismissed { CycloUpdates.undoDismiss(context) }
                        },
                    )
                } else {
                    key(editorKey) {
                        val editing = data.routines.firstOrNull { it.id == editorRoutineId }
                        IntroductionFlow(
                            mode = IntroductionMode.Editor,
                            initial = editing?.let {
                                RoutineDraft(it.name, it.wakeHour, it.wakeMinute, it.repeat, it.daysMask)
                            },
                            nextRoutineNumber = data.routines.size + 1,
                            onSave = { draft, onSaved, onFailed ->
                                if (editing != null) {
                                    viewModel.updateRoutine(editing.id, draft, onSaved, onFailed)
                                } else {
                                    viewModel.addRoutine(draft, completeOnboarding = false, onSaved = onSaved, onFailed = onFailed)
                                }
                            },
                            onFinished = { overlayOpen = false },
                            onClose = { overlayOpen = false },
                            onAddWidget = { pinWidget(editing?.id ?: nextRoutineId(data)) },
                        )
                    }
                }
            }
        }

        }

        // A fotografia do ecrã durante a passagem das texturas ou da língua (por baixo da barra de
        // baixo, das folhas e do loading-data, que continuam vivos).
        ScreenCrossfadeOverlay(crossfade)

        // Barra de baixo (undo-action): por cima da barra de navegação, ou do Next/Done quando o
        // tutorial ou o editor estão por cima. Desfoca o que está por trás.
        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(screens)) {
            // "Restore my data": a cópia é válida; só se repõe depois de confirmar (action-menu-big).
            // Fechar a folha (arrastar, tocar fora, voltar) desiste sem mudar nada.
            var shownRestore by remember { mutableStateOf(pendingRestore) }
            if (pendingRestore != null) shownRestore = pendingRestore
            BottomSheetHost(
                visible = pendingRestore != null,
                onDismiss = viewModel::cancelRestore,
                bottomMargin = bottomInset,
            ) {
                val locale = com.gaebriu.cyclo.ui.i18n.rememberAppLocale()
                val savedOn = shownRestore?.exportedAt?.let { instant ->
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                        .withLocale(locale)
                        .format(instant.atZone(ZoneId.systemDefault()).toLocalDate())
                }
                CycloBigSheet(
                    title = stringResource(R.string.restore_sheet_title),
                    texts = listOf(
                        stringResource(R.string.restore_sheet_text_1),
                        if (savedOn != null) stringResource(R.string.restore_sheet_text_date, savedOn) else stringResource(R.string.restore_sheet_text_no_date),
                    ),
                    buttonText = stringResource(R.string.restore_sheet_confirm),
                    onButtonClick = {
                        viewModel.confirmRestore(com.gaebriu.cyclo.ui.settings.loadingDataMinMs(context.getString(R.string.restore_loading)))
                    },
                )
            }
            BackHandler(enabled = pendingRestore != null) { viewModel.cancelRestore() }

            // action-menu-log-sleep (Componentes 5, 2013:40): "Log sleep" na Tracking. A bola começa
            // na média dos últimos 30 dias (com 7 noites ou mais) ou em "Really well"; "Done" regista o
            // sono de hoje (uma vez por dia) e o prompt sai. Fechar a folha não regista nada.
            SleepLogSheet(
                visible = sleepSheetOpen,
                start = remember(sleepSheetOpen) {
                    sleepMeterStart(data.history, data.dailyLog, java.time.LocalDate.now())
                },
                bottomMargin = bottomInset,
                onDismiss = { sleepSheetOpen = false },
                onDone = { quality ->
                    sleepSheetOpen = false
                    viewModel.logSleep(quality)
                },
            )
            BackHandler(enabled = sleepSheetOpen) { sleepSheetOpen = false }

            LoadingDataOverlay(loadingData)

            CycloMessageBar(
                // Durante o loading-data, a mensagem espera que ele comece a sair.
                message = if (loadingData.holdsMessages) null else message,
                onDismiss = viewModel::dismissMessage,
                bottomMargin = bottomInset + CycloDimens.Spacing +
                    if (overlayOpen) CycloDimens.ButtonHeight else NavigationBarHeight,
            )
        }
    }
    }
    }
}

/** De quanto em quanto tempo, com a app à frente, se vê se o dia de hoje já conta como aberto. */
private const val OpenedCheckMs = 60_000L

/** Quanto um menu das definições leva a fechar antes da fotografia da mudança de língua. */
private const val MenuCloseSettleMs = 320L

/** A escala de animações do sistema: as esperas acompanham as molas, que também escalam. */
private fun animatorScale(context: android.content.Context): Float =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** Se a língua nova não chegar a tempo (não devia), a fotografia desvanece na mesma. */
private const val LanguageChangeTimeoutMs = 1_500L

/**
 * O id que a próxima rotina nova vai ter (o repositório dá o maior + 1). O "Add as a widget" do
 * Introduction 5 fixa o widget antes de a rotina ser guardada; se ela não chegar a ser guardada,
 * o widget mostra a próxima rotina a acordar.
 */
private fun nextRoutineId(data: com.gaebriu.cyclo.data.CycloData): Long = (data.routines.maxOfOrNull { it.id } ?: 0L) + 1L

/** Quanto o editor desce com o gesto de voltar completo (o resto faz a mola ao largar). */

