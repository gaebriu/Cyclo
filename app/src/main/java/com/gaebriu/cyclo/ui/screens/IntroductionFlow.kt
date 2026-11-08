package com.gaebriu.cyclo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.OnboardingReminders
import com.gaebriu.cyclo.data.RoutineDraft
import com.gaebriu.cyclo.reminder.BedtimeReminder
import com.gaebriu.cyclo.reminder.LogSleepReminder
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.notifications.NotificationAccess
import androidx.compose.ui.platform.LocalView
import com.gaebriu.cyclo.ui.components.CycloGlassButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.MaxRoutineNameLength
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.BackGestureVelocity
import com.gaebriu.cyclo.ui.effects.sideBySidePage
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.rememberRepeatSelection
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.smoothStep
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Para que serve o fluxo: o tutorial (Introduction 0 a 7) ou criar/editar uma rotina (2 a 5). */
enum class IntroductionMode { Tutorial, Editor }

/** Passos do tutorial com barra de progresso (Introduction 1 a 7; Beta 2.3: eram 6). */
private const val TutorialStepCount = 7

/** Introduction 6 (Beta 2.3): os lembretes, só no tutorial. O Next dele guarda a rotina. */
private const val NotificationsStep = 6

/** Na troca Next / Done, o "Next" sai até aqui e o "Done" entra a partir de 1 - isto. */
private const val DoneSwapSplit = 0.55f

/** O limiar de visibilidade da posição das páginas (fração de página, ~0,5 px). */
private const val PageThreshold = 0.0005f

/** Primeiro e último passo do editor de rotinas (Introduction 2 a 5). */
private const val EditorFirstStep = 2
private const val LastRoutineStep = 5

/**
 * Fluxo dos ecrãs de introdução, usado de duas formas ([mode]):
 *
 * **Tutorial** (Introduction 0 a 7):
 * - O Introduction 1 sobe de baixo para cima e sobrepõe-se ao 0, com uma mola sem ressalto,
 *   enquanto o 0 recua (zoom out, desfoque, véu escuro, e o brilho roxo some de cima para baixo).
 *   O X e o gesto/botão de voltar do Android fazem-no descer de volta, e o 0 avança com zoom in.
 * - Do Introduction 1 em diante, só as páginas deslizam na horizontal, lado a lado, como o Feed,
 *   as rotinas guardadas e a Tracking ([sideBySidePage], Beta 2.2: antes a nova passava por cima
 *   da anterior): uma só posição contínua ([position]) com a mola das páginas
 *   ([CycloSprings.Page]); a que entra vem encostada à que sai, que escurece um pouco. O botão de
 *   cima, a barra de progresso e o Next são FIXOS: não se mexem; o ícone do botão muda por fade
 *   (X para seta) e a barra cresce.
 * - Introduction 6 (Beta 2.3): os lembretes "Bedtime" e "Log sleep" ([Introduction6Page]), os dois
 *   desligados ao chegar; ligar pede a autorização de notificações ([notifications]) e recusar não
 *   mostra nada. O Next dele guarda a rotina, marca o tutorial como feito e guarda os dois lembretes,
 *   tudo numa só escrita ([onSave]), e passa ao 7. Guardar só aqui (e não no 5) deixa voltar do 6
 *   ao 5 sem guardar a rotina duas vezes; fechar a app no 6 recomeça o tutorial, como no 5.
 * - No Introduction 7 ("You're all set!"), o botão de cima e o Next saem com um fade e deixam de
 *   responder, e os gestos de voltar ficam bloqueados; no fim das animações chama [onFinished].
 *
 * **Editor** (criar ou editar uma rotina, Introduction 2 a 5):
 * - Começa no Introduction 2 com um X (fecha, [onClose]); daí em diante a seta volta atrás.
 * - No Introduction 5, o Next dá lugar ao botão roxo "Done" (fade cruzado), que guarda a rotina.
 * - Voltar no primeiro passo fica para quem mostra o editor (o ecrã desce a seguir ao dedo).
 *
 * Em ambos:
 * - A seta de voltar e o gesto de voltar do Android (com a pré-visualização do sistema, a seguir
 *   o dedo) fazem o caminho inverso. Com um menu aberto, voltar fecha primeiro o menu.
 * - Sem animações no sistema, os movimentos passam a fades.
 * - Nome da rotina vazio (ou só espaços): ao carregar em Next avança logo e guarda "Routine [Nº]" em
 *   segundo plano, sem o mostrar na caixa do Introduction 3. O Introduction 4 já mostra o nome guardado.
 *
 * @param initial o que a rotina já tem, ao editar (null ao criar).
 * @param nextRoutineNumber o número que a próxima rotina vai ter (as já guardadas + 1).
 * @param onSave guarda a rotina (no tutorial, com os lembretes do Introduction 6; no editor, null);
 *        chama onSaved quando a escrita correu bem, ou onFailed se falhou (o fluxo fica onde está,
 *        e quem guarda mostra o erro com "Try again").
 * @param onFinished tutorial: fim do Introduction 7. Editor: a rotina foi guardada.
 * @param notifications a autorização de notificações (tutorial: os interruptores do Introduction 6).
 * @param onClose editor: o X do primeiro passo.
 */
@Composable
fun IntroductionFlow(
    onSave: (draft: RoutineDraft, reminders: OnboardingReminders?, onSaved: () -> Unit, onFailed: () -> Unit) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    mode: IntroductionMode = IntroductionMode.Tutorial,
    initial: RoutineDraft? = null,
    nextRoutineNumber: Int = 1,
    onClose: () -> Unit = {},
    onAddWidget: () -> Unit = {},
    notifications: NotificationAccess? = null,
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val isEditor = mode == IntroductionMode.Editor

    // Primeiro passo com páginas a deslizar: o 1 no tutorial, o 2 no editor.
    val firstPage = if (isEditor) EditorFirstStep else 1
    val lastStep = if (isEditor) LastRoutineStep else TutorialStepCount
    var step by rememberSaveable { mutableIntStateOf(if (isEditor) EditorFirstStep else 0) }

    // Hora de acordar escolhida no Introduction 2 (06:00 é o que está no design).
    var wakeHour by rememberSaveable { mutableIntStateOf(initial?.wakeHour ?: 6) }
    var wakeMinute by rememberSaveable { mutableIntStateOf(initial?.wakeMinute ?: 0) }
    // Ao editar, a hora já foi escolhida: o "Scroll or tap to interact" não aparece.
    var timeChanged by rememberSaveable { mutableStateOf(initial != null) }

    // routineName: o que está na caixa do Introduction 3 (o que o utilizador escreveu ou ditou ali, ou
    // mudou no Introduction 4; o "Routine [Nº]" automático nunca entra aqui).
    // savedRoutineName: o nome final, guardado em segundo plano; é o que o Introduction 4 mostra e edita.
    var routineName by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var savedRoutineName by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    val defaultRoutineName = stringResource(R.string.routine_default_name, nextRoutineNumber)

    // Quando a repetição fica pelo menu do Introduction 4.
    val repeat = rememberRepeatSelection(
        option = initial?.repeat ?: RepeatOption.JustOnce,
        daysMask = initial?.daysMask ?: 0,
    )

    // Enquanto a rotina está a ser guardada, o botão não volta a guardar.
    var saving by remember { mutableStateOf(false) }

    // Introduction 6: os lembretes que o utilizador ligou (começam desligados). Mostram-se ligados
    // só com as notificações autorizadas; é isso que se guarda no Next.
    var bedtimeWanted by rememberSaveable { mutableStateOf(false) }
    var logSleepWanted by rememberSaveable { mutableStateOf(false) }
    val bedtimeShown = bedtimeWanted && notifications?.isAllowed(BedtimeReminder.ChannelId) == true
    val logSleepShown = logSleepWanted && notifications?.isAllowed(LogSleepReminder.ChannelId) == true

    /**
     * Um interruptor do Introduction 6. Desligar é logo. Ligar: já autorizado, liga; sem
     * autorização, abre o pedido nativo do Android (sempre que o sistema o deixa mostrar) e só liga
     * se for aceite, com o háptico de ligar nesse momento. Recusado: fica desligado, sem mensagens.
     */
    fun toggleReminder(channelId: String, shown: Boolean, setWanted: (Boolean) -> Unit) {
        if (shown) {
            setWanted(false)
            return
        }
        val access = notifications ?: return
        val askedSystem = !access.isAllowed(channelId)
        access.request(channelId) { allowed ->
            if (!allowed) return@request
            setWanted(true)
            if (askedSystem) CycloHaptics.toggle(view, true)
        }
    }

    // Guarda o nome: o que foi escrito; se estiver vazio, o que já estava guardado; se também, "Routine [Nº]".
    fun commitRoutineName(typed: String) {
        savedRoutineName = typed.trim()
            .ifEmpty { savedRoutineName.trim() }
            .ifEmpty { defaultRoutineName }
            .take(MaxRoutineNameLength)
    }

    fun save() {
        if (saving) return
        // Nome apagado no Introduction 5: volta ao que estava guardado.
        commitRoutineName(savedRoutineName)
        saving = true
        val draft = RoutineDraft(savedRoutineName, wakeHour, wakeMinute, repeat.option, repeat.daysMask)
        onSave(
            draft,
            if (isEditor) null else OnboardingReminders(bedtime = bedtimeShown, logSleep = logSleepShown),
            {
                saving = false
                if (isEditor) onFinished() else step = TutorialStepCount
            },
            // Se a escrita falhar, o botão volta a funcionar (o erro aparece com "Try again").
            { saving = false },
        )
    }

    // enter: o Introduction 1 sobe (0 a 1). position: página horizontal, de [firstPage] em diante
    // (limiar fino: com o de omissão, 0,01 de página, a mola parava ~10 px antes e saltava no fim).
    val enter = remember { Animatable(if (step >= 1) 1f else 0f, visibilityThreshold = PageThreshold) }
    val position = remember { Animatable(maxOf(step, firstPage).toFloat(), visibilityThreshold = PageThreshold) }
    // A barra de progresso (0 a 1) sai da posição das páginas (Beta 2.2): anda com elas, segue o
    // gesto de voltar e chega quando a página chega. Antes tinha uma mola própria (~0,55 s), que
    // chegava ~0,2 s depois da página e não acompanhava o dedo. No tutorial, sobe com o Introduction 1.
    fun progressAt(p: Float): Float = if (isEditor) {
        (p - EditorFirstStep + 1f) / (LastRoutineStep - EditorFirstStep + 1).toFloat()
    } else {
        p / TutorialStepCount.toFloat()
    }
    val progress: () -> Float = { progressAt(position.value) * (if (isEditor) 1f else enter.value) }
    // As páginas mudam com a mola das páginas do Feed (sem animações, com o fade dela).
    val pageSpring = if (effects.animationsEnabled) CycloSprings.Page.float else CycloMotion.FadeSpring

    // A velocidade com que o gesto de voltar largou a posição (páginas/s; 0 = nenhuma).
    val releaseVelocity = remember { floatArrayOf(0f) }
    LaunchedEffect(step) {
        if (step >= 1) {
            launch { enter.animateTo(1f, CycloMotion.ScreenRiseSpring) }
            launch {
                val v = releaseVelocity[0]
                releaseVelocity[0] = 0f
                // Sem gesto, continua com a velocidade que já tiver (ex.: Next carregado a meio).
                if (v != 0f) position.animateTo(step.toFloat(), pageSpring, initialVelocity = v) else position.animateTo(step.toFloat(), pageSpring)
            }
        } else {
            enter.animateTo(0f, CycloMotion.ScreenRiseSpring)
        }
    }

    // Ao mudar de ecrã, o teclado e os menus fecham.
    LaunchedEffect(step) {
        focusManager.clearFocus()
        repeat.closeOverlays()
    }

    BackHandler(enabled = !isEditor && step == 1) { step = 0 }

    // Gesto de voltar do Android: como nas páginas do Feed, a anterior entra a seguir ao dedo (até
    // [CycloMotion.PageBackReach] da largura) e, ao largar, a mola acaba o caminho (ou volta atrás).
    // No tutorial vai até ao Introduction 6 (o 7 bloqueia o voltar).
    PredictiveBackHandler(enabled = step in (firstPage + 1)..(if (isEditor) LastRoutineStep else NotificationsStep)) { backProgress ->
        val from = step
        val gesture = BackGestureVelocity()
        try {
            backProgress.collect { event ->
                gesture.add(event.progress)
                position.snapTo(from - event.progress * CycloMotion.PageBackReach)
            }
            releaseVelocity[0] = -gesture.progressPerSecond * CycloMotion.PageBackReach
            step = from - 1
        } catch (e: CancellationException) {
            scope.launch {
                position.animateTo(from.toFloat(), pageSpring, initialVelocity = -gesture.progressPerSecond * CycloMotion.PageBackReach)
            }
        }
    }

    // Introduction 7: os gestos de voltar ficam bloqueados.
    BackHandler(enabled = !isEditor && step == TutorialStepCount) { }

    // Com um menu aberto, voltar fecha o menu. Vem DEPOIS dos outros, por isso tem prioridade.
    BackHandler(enabled = repeat.isOverlayOpen) { repeat.closeOverlays() }

    // Cada ecrã só existe enquanto estiver (ou a entrar/sair) em cima do anterior.
    val showIntroduction1 by remember { derivedStateOf { !isEditor && (step >= 1 || enter.value > 0f) } }
    val showIntroduction2 by remember { derivedStateOf { step >= 2 || position.value > 1.001f } }
    val showIntroduction3 by remember { derivedStateOf { step >= 3 || position.value > 2.001f } }
    val showIntroduction4 by remember { derivedStateOf { step >= 4 || position.value > 3.001f } }
    val showIntroduction5 by remember { derivedStateOf { step >= 5 || position.value > 4.001f } }
    val showIntroduction6 by remember { derivedStateOf { !isEditor && (step >= 6 || position.value > 5.001f) } }
    val showIntroduction7 by remember { derivedStateOf { !isEditor && (step >= 7 || position.value > 6.001f) } }
    // O Introduction 7 só começa as animações depois de assentar.
    val introduction7Settled by remember { derivedStateOf { step == TutorialStepCount && position.value > 6.98f } }

    // Introduction 7: o botão de cima e o Next saem com um fade, à medida que a página entra.
    val chromeAlpha = { if (isEditor) 1f else 1f - (position.value - NotificationsStep).coerceIn(0f, 1f) }
    val chromeEnabled = isEditor || step < TutorialStepCount

    // Editor: no último passo o Next dá lugar ao "Done" roxo.
    val done by animateFloatAsState(
        targetValue = if (isEditor && step == LastRoutineStep) 1f else 0f,
        animationSpec = CycloMotion.ButtonSwapSpring,
        label = "doneButton",
    )

    // O frame do Figma tem 48 dp de barra de navegação; reservamos pelo menos isso.
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )

    Box(modifier = modifier.fillMaxSize().background(CycloColors.Background)) {
        if (!isEditor) {
            // Introduction 0: enquanto o 1 sobe, recua com zoom out, desfoque e um véu escuro.
            // Ao voltar atrás, tudo se desfaz ao mesmo tempo que o 1 desce: seguem o mesmo progresso.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (effects.animationsEnabled) {
                            val e = enter.value
                            val s = 1f - (1f - CycloMotion.BackgroundScale) * e
                            scaleX = s
                            scaleY = s
                            renderEffect = if (effects.blurEnabled && e > 0f) {
                                val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * e
                                BlurEffect(sigma, sigma, TileMode.Clamp)
                            } else {
                                null
                            }
                        }
                    }
                    // Tapado pelo Introduction 1: fica fora do alcance do TalkBack.
                    .then(if (showIntroduction1) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                Introduction0Screen(
                    onGetStarted = { step = 1 },
                    recede = { enter.value },
                )

                // Véu #010101 a 24 % de opacidade quando o ecrã está recuado.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = CycloMotion.BackgroundOverlayAlpha * enter.value }
                        .background(CycloColors.Background),
                )
            }
        }

        if (isEditor || showIntroduction1) {
            // Tudo o que é do Introduction 1 em diante sobe junto, uma só vez
            // (no editor, quem sobe é o fluxo inteiro, por fora).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isEditor) return@graphicsLayer
                        val e = enter.value
                        if (effects.animationsEnabled) {
                            translationY = (1f - e) * size.height
                        } else {
                            alpha = e
                        }
                    }
                    .background(CycloColors.Background)
                    // Impede que os toques passem para o ecrã que está por baixo.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                // O que fica por baixo dos menus da repetição (action-menu-when-to-show e
                // action-menu-big-choose-days), que o desfocam como os menus das definições. É a
                // Column e não esta Box: os menus estão dentro da Box e não podem usar uma camada
                // que os contenha. Tutorial e editor são este mesmo código: o desfoque é igual.
                val pagesBackdrop = rememberBackdropLayer()
                Column(modifier = Modifier.fillMaxSize().backdropSource(pagesBackdrop)) {
                    // FIXO: botão de cima e barra de progresso.
                    IntroTopBar(
                        leadingIsBack = step > firstPage,
                        backAmount = { (position.value - firstPage).coerceIn(0f, 1f) },
                        progress = progress,
                        progressDescription = stringResource(
                            R.string.introduction_progress,
                            if (isEditor) step - EditorFirstStep + 1 else step.coerceAtLeast(1),
                            if (isEditor) LastRoutineStep - EditorFirstStep + 1 else TutorialStepCount,
                        ),
                        onLeadingClick = {
                            when {
                                step > firstPage -> step -= 1
                                isEditor -> onClose()
                                else -> step = 0
                            }
                        },
                        leadingAlpha = chromeAlpha,
                        leadingEnabled = chromeEnabled,
                        modifier = Modifier
                            .padding(horizontal = CycloDimens.ScreenMargin)
                            .zIndex(1f),
                    )

                    // As páginas deslizam aqui, entre a parte de cima e o Next.
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (!isEditor) {
                            IntroPage(1, { position.value }, effects.animationsEnabled, active = step == 1) {
                                Introduction1Page(enter = { enter.value })
                            }
                        }

                        if (showIntroduction2) {
                            IntroPage(2, { position.value }, effects.animationsEnabled, active = step == 2) {
                                Introduction2Page(
                                    wakeHour = wakeHour,
                                    wakeMinute = wakeMinute,
                                    hintVisible = !timeChanged,
                                    onHourChange = {
                                        if (it != wakeHour) {
                                            wakeHour = it
                                            timeChanged = true
                                        }
                                    },
                                    onMinuteChange = {
                                        if (it != wakeMinute) {
                                            wakeMinute = it
                                            timeChanged = true
                                        }
                                    },
                                )
                            }
                        }

                        if (showIntroduction3) {
                            IntroPage(3, { position.value }, effects.animationsEnabled, active = step == 3) {
                                Introduction3Page(
                                    name = routineName,
                                    onNameChange = { routineName = it },
                                )
                            }
                        }

                        if (showIntroduction4) {
                            IntroPage(4, { position.value }, effects.animationsEnabled, active = step == 4) {
                                Introduction4Page(
                                    name = savedRoutineName,
                                    // O que se muda aqui passa também para a caixa do Introduction 3.
                                    onNameChange = {
                                        savedRoutineName = it
                                        routineName = it
                                    },
                                    repeat = repeat,
                                )
                            }
                        }

                        if (showIntroduction5) {
                            IntroPage(5, { position.value }, effects.animationsEnabled, active = step == 5) {
                                Introduction5Page(
                                    name = savedRoutineName,
                                    // Como no Introduction 4: o que se muda aqui passa também para a
                                    // caixa do Introduction 3 (e para a do 4).
                                    onNameChange = {
                                        savedRoutineName = it
                                        routineName = it
                                    },
                                    repeat = repeat,
                                    onAddWidget = onAddWidget,
                                )
                            }
                        }

                        if (showIntroduction6) {
                            IntroPage(6, { position.value }, effects.animationsEnabled, active = step == 6) {
                                Introduction6Page(
                                    bedtimeOn = bedtimeShown,
                                    onToggleBedtime = {
                                        toggleReminder(BedtimeReminder.ChannelId, bedtimeShown) { bedtimeWanted = it }
                                    },
                                    logSleepOn = logSleepShown,
                                    onToggleLogSleep = {
                                        toggleReminder(LogSleepReminder.ChannelId, logSleepShown) { logSleepWanted = it }
                                    },
                                    asks = notifications?.isPermissionGranted() != true,
                                )
                            }
                        }

                        if (showIntroduction7) {
                            IntroPage(7, { position.value }, effects.animationsEnabled, active = step == 7) {
                                Introduction7Page(
                                    active = introduction7Settled,
                                    onFinished = onFinished,
                                )
                            }
                        }
                    }

                    // FIXO: botão Next (e, no último passo do editor, o "Done" roxo por cima).
                    Spacer(Modifier.height(CycloDimens.Spacing))
                    Box(
                        modifier = Modifier
                            .padding(horizontal = CycloDimens.ScreenMargin)
                            .zIndex(1f)
                            .graphicsLayer { alpha = chromeAlpha() }
                            .then(if (chromeEnabled) Modifier else Modifier.clearAndSetSemantics { }),
                    ) {
                        CycloGlassButton(
                            text = stringResource(R.string.next),
                            enabled = chromeEnabled && !(isEditor && step == LastRoutineStep),
                            onClick = {
                                when (step) {
                                    1 -> step = 2
                                    2 -> step = 3
                                    3 -> {
                                        // Nome vazio: fica "Routine [Nº]" guardado em segundo plano, sem aparecer
                                        // na caixa, e avança logo.
                                        commitRoutineName(routineName)
                                        step = 4
                                    }
                                    4 -> {
                                        commitRoutineName(savedRoutineName)
                                        step = 5
                                    }
                                    // Editor: o "Done" (por cima) é que guarda. Tutorial: segue para os lembretes.
                                    LastRoutineStep -> if (isEditor) save() else step = NotificationsStep
                                    NotificationsStep -> save()
                                }
                            },
                            // A troca é UM botão a mudar (Beta 2.2): a forma fica, o vidro fica opaco por
                            // baixo enquanto o roxo enche por cima, e os textos não se cruzam ("Next"
                            // sai na primeira parte, "Done" entra na segunda). Antes os dois botões
                            // cruzavam-se meio transparentes. Com o "Done" já inteiro, o vidro sai
                            // (o gel do "Done" a encolher não o deixa ver por baixo).
                            textAlpha = { 1f - smoothStep(done, 0f, DoneSwapSplit) },
                            modifier = Modifier
                                .graphicsLayer { alpha = if (done >= 0.999f) 0f else 1f }
                                .then(if (done > 0.5f) Modifier.clearAndSetSemantics { } else Modifier),
                        )
                        if (done > 0f) {
                            CycloPrimaryButton(
                                text = stringResource(R.string.editor_done),
                                enabled = step == LastRoutineStep,
                                onClick = { save() },
                                textAlpha = { smoothStep(done, 1f - DoneSwapSplit, 1f) },
                                modifier = Modifier.graphicsLayer { alpha = done },
                            )
                        }
                    }
                    Spacer(Modifier.height(bottomInset))
                }

                // Menus por cima de tudo (também do Next).
                CompositionLocalProvider(LocalBackdrop provides Backdrop.of(pagesBackdrop)) {
                    RepeatOverlays(selection = repeat, bottomMargin = bottomInset)
                }
            }
        }
    }
}

/**
 * Uma página do fluxo: lado a lado com as outras ([sideBySidePage]). Só a do passo atual chega ao
 * TalkBack (as outras estão fora do ecrã ou a sair).
 */
@Composable
private fun IntroPage(
    index: Int,
    position: () -> Float,
    animationsEnabled: Boolean,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .sideBySidePage(index, position, animationsEnabled)
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        content()
    }
}
