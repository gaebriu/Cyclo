package com.gaebriu.cyclo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import com.gaebriu.cyclo.ui.keyboard.rememberIsKeyboardOpen
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.OnboardingReminders
import com.gaebriu.cyclo.data.RoutineDraft
import com.gaebriu.cyclo.reminder.BedtimeReminder
import com.gaebriu.cyclo.reminder.LogSleepReminder
import com.gaebriu.cyclo.reminder.HydrationReminder
import com.gaebriu.cyclo.ui.effects.LocalCoverDrag
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.IconMotion
import com.gaebriu.cyclo.ui.components.IconMotionStyle
import com.gaebriu.cyclo.ui.components.MaxRoutineNameLength
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.BackGestureVelocity
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.VisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.effects.stackedPage
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.notifications.NotificationAccess
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.rememberRepeatSelection
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx
import com.gaebriu.cyclo.ui.theme.rubberBand
import com.gaebriu.cyclo.ui.theme.smoothStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Para que serve o fluxo: o tutorial (Introduction 0 a 7) ou criar/editar uma rotina (2 a 5). */
enum class IntroductionMode { Tutorial, Editor }

/** Passos do tutorial com anel de progresso (Introduction 1 a 7). */
private const val TutorialStepCount = 7

/** Introduction 6 (Beta 2.3): os lembretes, só no tutorial. Passar dele guarda a rotina. */
private const val NotificationsStep = 6

/** O limiar de visibilidade da posição das páginas (ver [CycloMotion.PageThreshold]). */
private const val PageThreshold = CycloMotion.PageThreshold

/** Primeiro e último passo do editor de rotinas (Introduction 2 a 5). */
private const val EditorFirstStep = 2
private const val LastRoutineStep = 5

/** O indicador "swipe up to continue" apaga-se nesta fração de página e acende ao assentar. */
private const val HintFadeReach = 0.12f

/** Depois de uma interação, o indicador volta a oscilar ao fim disto (ms, com o dedo já levantado). */
private const val GuideResumeMs = 1200L

/** A demonstração: quanto a página sobe (fração do ecrã, ~38 dp) e quanto tempo fica lá (ms). */
private const val GuidePeek = 0.045f
private const val GuidePeekHoldMs = 120L

/** De quanto em quanto tempo se volta a ver se já se pode mostrar a demonstração (ms). */
private const val GuideCheckMs = 400L

/**
 * Os tempos parados (ms) antes de cada demonstração numa página: a primeira (o Introduction 0 conta
 * a entrada do "Hello!"; o 1 dá tempo para ler), e depois mais espaçadas. Nunca mais de três.
 */
private fun guideDelays(step: Int): List<Long> = when (step) {
    0 -> listOf(6_500L, 16_000L, 30_000L)
    1 -> listOf(14_000L, 20_000L, 34_000L)
    else -> listOf(8_000L, 18_000L, 32_000L)
}

/**
 * Fluxo dos ecrãs de introdução, usado de duas formas ([mode]). Beta-3.2: sem Next, por gestos.
 *
 * **Navegação (tutorial e editor).** As páginas estão EMPILHADAS ([stackedPage]), numa só posição
 * contínua ([position]): arrastar para cima faz a seguinte subir por cima da atual, que recua (zoom
 * out, véu e desfoque, como os ecrãs que sobem por cima do Feed); para baixo, o inverso. O dedo
 * leva a página e, ao largar, a mola dos ecrãs ([CycloMotion.ScreenRiseSpring]) acaba o caminho a
 * partir da velocidade do dedo ([introSwipeDecision]: um lançamento ou passar de meia página); um
 * gesto incompleto volta. Nunca ficam duas páginas a meio. Uma página por gesto; agarrar a meio
 * continua de onde está. Para lá das pontas, resistência ([rubberBand]). Quem tem o gesto (scroll,
 * rodas, menus) está em [IntroSwipe]. TODAS as mudanças de passo passam por [navigate] (gesto,
 * toque no indicador, TalkBack, voltar do sistema, X): o nome por omissão e a gravação ficam lá.
 *
 * **Tutorial** (Introduction 0 a 7):
 * - O Introduction 0 tem o "swipe up to get started"; deslizar faz subir o grupo do Introduction 1
 *   em diante ([enter]), com o X e o anel de progresso, por cima do 0, que recua.
 * - O X volta SEMPRE ao Introduction 0 (o grupo desce) e guarda a página onde estava ([resumeStep]):
 *   deslizar de novo no 0 sobe direto para ela. Voltar do sistema anda uma página para trás (no 1,
 *   desce para o 0).
 * - Do 1 ao 6, o "swipe up to continue" em baixo: some ao começar a mudar e acende ao assentar.
 * - Passar do Introduction 6 guarda a rotina, o tutorial e os lembretes numa só escrita ([onSave])
 *   enquanto o 7 sobe; se falhar, o 7 volta a descer (o erro aparece com "Try again").
 * - No Introduction 7 ("You're all set!"), o X sai com um fade, o anel chega aos 100 % e só então
 *   sai; os gestos ficam bloqueados; no fim das animações chama [onFinished].
 *
 * **Editor** (criar ou editar uma rotina, Introduction 2 a 5):
 * - O X fecha o editor em qualquer passo ([onClose]); o gesto para baixo volta um passo. No primeiro
 *   (a hora de acordar, Cyclo 3.1), não há passo antes: o gesto para baixo leva o editor inteiro a
 *   descer com o dedo e fecha-o, de volta a onde foi aberto (o Feed ou as rotinas guardadas), como o
 *   grupo do Introduction 1 desce para o 0 no tutorial ([LocalCoverDrag]).
 * - No passo 5, o indicador dá lugar ao botão roxo "Done", que guarda a rotina (subir não faz nada).
 *
 * Em ambos:
 * - Com um menu aberto (repetição), voltar fecha o menu e as páginas não se mexem.
 * - Sem animações no sistema, os movimentos passam a fades.
 * - Nome da rotina vazio (ou só espaços): ao passar do Introduction 3 guarda "Routine [Nº]" em
 *   segundo plano, sem o mostrar na caixa. O Introduction 4 já mostra o nome guardado.
 *
 * @param initial o que a rotina já tem, ao editar (null ao criar).
 * @param nextRoutineNumber o número que a próxima rotina vai ter (as já guardadas + 1).
 * @param onSave guarda a rotina (no tutorial, com os lembretes do Introduction 6; no editor, null);
 *        chama onSaved quando a escrita correu bem, ou onFailed se falhou.
 * @param onFinished tutorial: fim do Introduction 7. Editor: a rotina foi guardada.
 * @param notifications a autorização de notificações (tutorial: os interruptores do Introduction 6).
 * @param onClose editor: o X.
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

    // Primeira página empilhada: a 1 no tutorial, a 2 no editor.
    val firstPage = if (isEditor) EditorFirstStep else 1
    val lastStep = if (isEditor) LastRoutineStep else TutorialStepCount
    var step by rememberSaveable { mutableIntStateOf(if (isEditor) EditorFirstStep else 0) }
    // Tutorial: a página a que o Introduction 0 volta a subir (o X guarda aqui onde estava).
    var resumeStep by rememberSaveable { mutableIntStateOf(1) }

    // Beta 3.1: os ícones com holofote entram só ao AVANÇAR (pela direção do passo, não por a página
    // aparecer): voltar atrás, o gesto de voltar e recriar a atividade não repetem nada. No editor,
    // abrir conta como avançar para o primeiro passo (o sol entra com o editor a subir). Voltar do
    // Introduction 0 para a página guardada conta como avançar.
    var previousStep by rememberSaveable { mutableIntStateOf(if (isEditor) EditorFirstStep - 1 else step) }
    val iconMotions = remember {
        mapOf(
            1 to IconMotionStyle.Rest,
            2 to IconMotionStyle.Dawn,
            3 to IconMotionStyle.Tag,
            4 to IconMotionStyle.Page,
            5 to IconMotionStyle.Assemble,
            6 to IconMotionStyle.Chime,
        ).mapValues { (_, style) -> IconMotion(style, scope, view) }
    }
    LaunchedEffect(step) {
        val from = previousStep
        previousStep = step
        if (from == step) return@LaunchedEffect
        // A página que fica para trás acaba depressa o que ainda estiver a fazer, sem cortes.
        iconMotions[from]?.settle()
        if (step > from && effects.animationsEnabled) iconMotions[step]?.enter()
    }
    // O holofote da página à vista respira devagar (só o dela).
    LaunchedEffect(step) {
        iconMotions.forEach { (page, motion) -> motion.breathing.value = page == step }
    }
    fun nudgeIcon(page: Int) {
        if (effects.animationsEnabled && step == page) iconMotions[page]?.nudge()
    }

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

    // O calendário (Introduction 4) mexe-se ao abrir o "When to show" ou a escolha dos dias.
    LaunchedEffect(repeat) {
        snapshotFlow { repeat.isMenuOpen || repeat.isSheetOpen }.collect { open -> if (open) nudgeIcon(4) }
    }

    // Enquanto a rotina está a ser guardada, nada volta a guardar nem as páginas se mexem.
    var saving by remember { mutableStateOf(false) }

    // Introduction 6: os lembretes que o utilizador ligou (começam desligados). Mostram-se ligados
    // só com as notificações autorizadas; é isso que se guarda ao passar dele.
    var bedtimeWanted by rememberSaveable { mutableStateOf(false) }
    var logSleepWanted by rememberSaveable { mutableStateOf(false) }
    var hydrationWanted by rememberSaveable { mutableStateOf(false) }
    val bedtimeShown = bedtimeWanted && notifications?.isAllowed(BedtimeReminder.ChannelId) == true
    val logSleepShown = logSleepWanted && notifications?.isAllowed(LogSleepReminder.ChannelId) == true
    val hydrationShown = hydrationWanted && notifications?.isAllowed(HydrationReminder.ChannelId) == true

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
            nudgeIcon(NotificationsStep)
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

    // enter: o grupo do Introduction 1 em diante sobe por cima do 0 (0 a 1, só no tutorial).
    // position: a página empilhada, de [firstPage] em diante (limiar fino: com o de omissão, 0,01
    // de página, a mola parava ~10 px antes e saltava no fim).
    val enter = remember { Animatable(if (step >= 1) 1f else 0f, visibilityThreshold = PageThreshold) }
    val position = remember {
        Animatable((if (step == 0) resumeStep else step).coerceAtLeast(firstPage).toFloat(), visibilityThreshold = PageThreshold)
    }
    // Com o dedo, as páginas leem estes valores (como o swipe do Feed); ao largar passam para as molas.
    var dragging by remember { mutableStateOf(false) }
    val dragPosition = remember { mutableFloatStateOf(0f) }
    val dragEnter = remember { mutableFloatStateOf(0f) }
    // A demonstração do gesto (só no tutorial, parado): a página à vista sobe um pouco e volta, no
    // sentido do gesto (fração da altura; no Introduction 0, o ecrã dele).
    val peek = remember { Animatable(0f, visibilityThreshold = PageThreshold) }
    val shownPosition: () -> Float = { if (dragging) dragPosition.floatValue else position.value }
    val shownEnter: () -> Float = { if (dragging) dragEnter.floatValue else enter.value }

    // O anel (0 a 1) sai da posição das páginas: anda com elas, segue o dedo e o voltar do sistema.
    fun progressAt(p: Float): Float = if (isEditor) {
        (p - EditorFirstStep + 1f) / (LastRoutineStep - EditorFirstStep + 1).toFloat()
    } else {
        p / TutorialStepCount.toFloat()
    }
    val progress: () -> Float = { progressAt(shownPosition()) }
    val pageSpring = if (effects.animationsEnabled) CycloMotion.ScreenRiseSpring else CycloMotion.FadeSpring

    // A velocidade com que o gesto largou cada valor (páginas/s; 0 = nenhuma): a mola parte dela.
    val releaseVelocity = remember { floatArrayOf(0f, 0f) }
    LaunchedEffect(step) {
        val v = releaseVelocity[0]
        val ve = releaseVelocity[1]
        releaseVelocity[0] = 0f
        releaseVelocity[1] = 0f
        if (step >= 1) {
            launch { enter.animateTo(1f, pageSpring, initialVelocity = if (ve != 0f) ve else enter.velocity) }
            launch { position.animateTo(step.toFloat(), pageSpring, initialVelocity = if (v != 0f) v else position.velocity) }
        } else {
            enter.animateTo(0f, pageSpring, initialVelocity = if (ve != 0f) ve else enter.velocity)
        }
    }

    // Ao mudar de passo, o teclado e os menus fecham.
    LaunchedEffect(step) {
        focusManager.clearFocus()
        repeat.closeOverlays()
    }

    /** Introduction 6 → 7: o 7 sobe já, enquanto se guarda; se falhar, volta a descer. */
    fun saveTutorial(velocity: Float) {
        if (saving) return
        commitRoutineName(savedRoutineName)
        saving = true
        scope.launch { position.animateTo(TutorialStepCount.toFloat(), pageSpring, initialVelocity = velocity) }
        onSave(
            RoutineDraft(savedRoutineName, wakeHour, wakeMinute, repeat.option, repeat.daysMask),
            OnboardingReminders(bedtime = bedtimeShown, logSleep = logSleepShown, hydration = hydrationShown),
            {
                saving = false
                step = TutorialStepCount
            },
            {
                saving = false
                scope.launch { position.animateTo(NotificationsStep.toFloat(), pageSpring) }
            },
        )
    }

    /** Editor: o "Done" do passo 5. */
    fun saveEditor() {
        if (saving) return
        // Nome apagado no Introduction 5: volta ao que estava guardado.
        commitRoutineName(savedRoutineName)
        saving = true
        onSave(
            RoutineDraft(savedRoutineName, wakeHour, wakeMinute, repeat.option, repeat.daysMask),
            null,
            {
                saving = false
                // A rotina ficou guardada: a confirmação do Android, com o editor a descer.
                CycloHaptics.confirm(view)
                onFinished()
            },
            { saving = false },
        )
    }

    // A página onde as páginas assentam (no Introduction 0, a guardada, escondida por baixo).
    val restStep: () -> Int = { (if (!isEditor && step == 0) resumeStep else step).coerceAtLeast(firstPage) }

    val canForward: () -> Boolean = {
        if (isEditor) step < LastRoutineStep else step < TutorialStepCount
    }
    val canBack: () -> Boolean = {
        if (isEditor) step > EditorFirstStep else step in 1 until TutorialStepCount
    }
    val canNavigate: () -> Boolean = {
        !saving && !repeat.isOverlayOpen && !(!isEditor && step == TutorialStepCount)
    }

    /**
     * A ÚNICA forma de mudar de passo (gesto, indicador, TalkBack, voltar do sistema). [direction]
     * 1 = seguinte, -1 = anterior; [velocity] a do gesto (páginas/s), para a mola continuar dela.
     */
    fun navigate(direction: Int, velocity: Float = 0f) {
        if (!canNavigate()) return
        if (direction > 0) {
            if (!canForward()) return
            when {
                // Introduction 0: sobe para a página guardada.
                !isEditor && step == 0 -> {
                    releaseVelocity[1] = velocity
                    step = resumeStep.coerceIn(1, NotificationsStep)
                    return
                }
                !isEditor && step == NotificationsStep -> {
                    saveTutorial(velocity)
                    return
                }
                // Nome vazio: fica "Routine [Nº]" guardado em segundo plano, sem aparecer na caixa.
                step == 3 -> commitRoutineName(routineName)
                step == 4 -> commitRoutineName(savedRoutineName)
            }
            releaseVelocity[0] = velocity
            step += 1
        } else {
            if (!canBack()) return
            if (!isEditor && step == 1) {
                releaseVelocity[1] = velocity
                resumeStep = 1
                step = 0
                return
            }
            releaseVelocity[0] = velocity
            step -= 1
        }
    }

    /** O X: no tutorial volta ao Introduction 0 e guarda a página; no editor fecha. */
    fun close() {
        if (isEditor) {
            onClose()
            return
        }
        if (saving || step == 0 || step == TutorialStepCount) return
        resumeStep = step
        step = 0
    }

    // A guia do gesto (beta-3.2). Qualquer toque ou letra conta como interação: o indicador pára de
    // oscilar logo e só volta [GuideResumeMs] depois; a demonstração conta o tempo parado.
    var touching by remember { mutableStateOf(false) }
    var guideIdle by remember { mutableStateOf(true) }
    val lastInteraction = remember { longArrayOf(SystemClock.uptimeMillis()) }
    val resumeGuide = remember { arrayOfNulls<Job>(1) }
    fun interacted() {
        lastInteraction[0] = SystemClock.uptimeMillis()
        guideIdle = false
        resumeGuide[0]?.cancel()
        resumeGuide[0] = scope.launch {
            snapshotFlow { touching }.first { !it }
            delay(GuideResumeMs)
            guideIdle = true
        }
        // A demonstração a meio desce já (sem se sobrepor ao que o utilizador está a fazer).
        if (peek.value != 0f && !dragging) scope.launch { peek.animateTo(0f, CycloSprings.Exit.precise) }
    }
    val keyboardOpen = rememberIsKeyboardOpen()
    val latestKeyboardOpen by rememberUpdatedState(keyboardOpen)
    val context = LocalContext.current
    val touchExploration = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    // Parado tempo suficiente (e nada aberto): a página sobe ~4,5 % e volta, devagar. A
    // primeira vez ao fim de [guideDelays] (mais tempo no Introduction 1, que é para ler), depois com
    // intervalos maiores; no máximo três por página. Sem animações (ou com o TalkBack) não há.
    LaunchedEffect(step, effects.animationsEnabled) {
        if (isEditor || !effects.animationsEnabled || touchExploration || step == TutorialStepCount) return@LaunchedEffect
        lastInteraction[0] = maxOf(lastInteraction[0], SystemClock.uptimeMillis())
        val settled = {
            !dragging && !touching && !saving && !repeat.isOverlayOpen && !latestKeyboardOpen &&
                !position.isRunning && !enter.isRunning && abs(position.value - restStep()) < 0.001f
        }
        for (gap in guideDelays(step)) {
            while (true) {
                val idleFor = SystemClock.uptimeMillis() - lastInteraction[0]
                if (idleFor >= gap && settled()) break
                delay((gap - idleFor).coerceAtLeast(GuideCheckMs))
            }
            peek.animateTo(GuidePeek, CycloSprings.GuidePeekRise.precise)
            delay(GuidePeekHoldMs)
            peek.animateTo(0f, CycloSprings.GuidePeekFall.precise)
            // O próximo intervalo conta a partir do fim desta.
            lastInteraction[0] = SystemClock.uptimeMillis()
        }
    }

    // O gesto: começa onde as páginas estão (a meio de uma mola também) e leva no máximo uma página.
    val dragFrom = remember { intArrayOf(0) }
    val dragStart = remember { floatArrayOf(0f) }
    val dragBeyond = remember { booleanArrayOf(false) }
    // Editor, primeiro passo: o que o dedo leva para baixo, para lá da primeira página, é o editor
    // inteiro (o cover do CycloApp). [coverTaken]: já se pegou nele neste gesto.
    val coverDrag = LocalCoverDrag.current
    val coverTaken = remember { booleanArrayOf(false) }
    val swipe = remember {
        IntroSwipe(object : IntroSwipe.Target {
            override fun begin(): Boolean {
                if (!canNavigate()) return false
                dragFrom[0] = step
                // Começa onde as páginas se veem (a meio de uma mola também).
                val p = shownPosition()
                val e = shownEnter()
                dragPosition.floatValue = p
                dragEnter.floatValue = e
                // Um só valor contínuo: no Introduction 1 do tutorial, abaixo de 1 é o grupo a descer.
                dragStart[0] = if (!isEditor && step <= 1) p - 1f + e else p
                dragBeyond[0] = false
                coverTaken[0] = false
                dragging = true
                scope.launch {
                    position.stop()
                    enter.stop()
                }
                // A demonstração a meio desce depressa (o dedo manda a partir daqui).
                if (peek.value != 0f) scope.launch { peek.animateTo(0f, CycloSprings.Exit.precise) }
                return true
            }

            override fun drag(pages: Float) {
                val from = dragFrom[0]
                val raw = dragStart[0] + pages
                val enterMode = !isEditor && from == 0
                val lo: Float
                val hi: Float
                if (enterMode) {
                    // No 0, o valor é o grupo a subir: de 0 (escondido) a 1 (a página guardada).
                    lo = 0f
                    hi = 1f
                } else {
                    lo = (if (canBack()) from - 1 else from).toFloat()
                    hi = (if (canForward()) from + 1 else from).toFloat()
                }
                val base = if (enterMode) raw - dragPosition.floatValue + 1f else raw
                // Editor no primeiro passo, a puxar para baixo: em vez da resistência, desce o editor.
                if (isEditor && from == firstPage && base < lo && coverDrag != null && (coverTaken[0] || coverDrag.start())) {
                    coverTaken[0] = true
                    coverDrag.drag(lo - base)
                    dragEnter.floatValue = 1f
                    dragPosition.floatValue = lo
                    return
                }
                if (coverTaken[0]) coverDrag?.drag(0f)
                // Para lá do que se pode, a resistência do iOS (em páginas, como o swipe do Feed).
                val shown = when {
                    base < lo -> lo - rubberBand(lo - base, 1f)
                    base > hi -> hi + rubberBand(base - hi, 1f)
                    else -> base
                }
                if (enterMode) {
                    dragEnter.floatValue = shown
                } else if (!isEditor && from == 1 && shown < 1f) {
                    dragEnter.floatValue = shown
                    dragPosition.floatValue = 1f
                } else {
                    dragEnter.floatValue = 1f
                    dragPosition.floatValue = shown
                }
                val offset = if (enterMode) shown else shown - from
                val past = abs(offset) > 0.5f && offset in -1f..1f && (if (offset > 0f) canForward() || enterMode else canBack())
                if (past != dragBeyond[0]) {
                    dragBeyond[0] = past
                    CycloHaptics.thresholdCrossed(view, past)
                }
            }

            override fun release(pagesPerSecond: Float) {
                val from = dragFrom[0]
                if (coverTaken[0]) {
                    // O editor desce até ao fim ou volta a subir; as páginas ficam onde estão.
                    coverTaken[0] = false
                    coverDrag?.release(-pagesPerSecond)
                    scope.launch {
                        position.snapTo(dragPosition.floatValue)
                        enter.snapTo(1f)
                        dragging = false
                    }
                    return
                }
                val enterMode = !isEditor && from == 0
                val offset = if (enterMode) {
                    dragEnter.floatValue
                } else if (!isEditor && from == 1 && dragEnter.floatValue < 1f) {
                    dragEnter.floatValue - 1f
                } else {
                    dragPosition.floatValue - from
                }
                var direction = introSwipeDecision(offset, pagesPerSecond)
                if (direction > 0 && !(canForward() || enterMode)) direction = 0
                if (direction < 0 && (enterMode || !canBack())) direction = 0
                // Um lançamento curto não passou o limite: o tique de "vai mudar" chega aqui.
                if (direction != 0 && !dragBeyond[0]) CycloHaptics.thresholdCrossed(view, true)
                if (direction == 0 && dragBeyond[0]) CycloHaptics.thresholdCrossed(view, false)
                val p = dragPosition.floatValue
                val e = dragEnter.floatValue
                // Quem o dedo moveu: o grupo (no 0, ou a descer do 1) ou as páginas. Só esse leva a velocidade.
                val movedGroup = enterMode || (!isEditor && from == 1 && e < 1f)
                scope.launch {
                    position.snapTo(p)
                    enter.snapTo(e)
                    dragging = false
                    if (direction != 0 && step == from && canNavigate()) {
                        navigate(direction, pagesPerSecond)
                    } else {
                        // Volta ao sítio, com a velocidade do dedo.
                        launch { position.animateTo(restStep().toFloat(), pageSpring, initialVelocity = if (movedGroup) 0f else pagesPerSecond) }
                        launch { enter.animateTo(if (!isEditor && step == 0) 0f else 1f, pageSpring, initialVelocity = if (movedGroup) pagesPerSecond else 0f) }
                    }
                }
            }
        })
    }

    // Gesto de voltar do Android: a página atual desce a seguir ao dedo (até [CycloMotion.PageBackReach])
    // e, ao largar, a mola acaba o caminho (ou volta atrás). No Introduction 1 do tutorial é o grupo
    // que desce para o 0 (e a página fica guardada: 1).
    PredictiveBackHandler(enabled = canBack() && !saving && !repeat.isOverlayOpen) { backProgress ->
        val from = step
        val group = !isEditor && from == 1
        val gesture = BackGestureVelocity()
        try {
            backProgress.collect { event ->
                gesture.add(event.progress)
                if (group) enter.snapTo(1f - event.progress * CycloMotion.PageBackReach) else position.snapTo(from - event.progress * CycloMotion.PageBackReach)
            }
            navigate(-1, -gesture.progressPerSecond * CycloMotion.PageBackReach)
        } catch (e: CancellationException) {
            scope.launch {
                val v = -gesture.progressPerSecond * CycloMotion.PageBackReach
                if (group) enter.animateTo(1f, pageSpring, initialVelocity = v) else position.animateTo(from.toFloat(), pageSpring, initialVelocity = v)
            }
        }
    }

    // Introduction 7: os gestos de voltar ficam bloqueados.
    BackHandler(enabled = !isEditor && step == TutorialStepCount) { }

    // Com um menu aberto, voltar fecha o menu. Vem DEPOIS dos outros, por isso tem prioridade.
    BackHandler(enabled = repeat.isOverlayOpen) { repeat.closeOverlays() }

    // Cada página só existe enquanto se vê (ou é a do passo); o grupo, enquanto não está escondido.
    // Lidos em estados derivados: a composição só muda quando muda o conjunto (não a cada fotograma).
    val showGroup by remember { derivedStateOf { isEditor || step >= 1 || shownEnter() > 0f } }
    val zeroCovered by remember { derivedStateOf { showGroup && shownEnter() >= 1f } }
    val shownPages by remember {
        derivedStateOf(structuralEqualityPolicy()) {
            (firstPage..lastStep).filter { abs(shownPosition() - it) < 1f || it == restStep() }
        }
    }
    // O Introduction 7 só começa as animações depois de assentar.
    val introduction7Settled by remember { derivedStateOf { step == TutorialStepCount && position.value > 6.98f } }
    // Tudo pronto: a confirmação do Android quando o "You're all set!" assenta (uma vez).
    LaunchedEffect(introduction7Settled) { if (introduction7Settled) CycloHaptics.confirm(view) }

    // Introduction 7: o X sai com um fade à medida que a página entra; o anel chega aos 100 % e só
    // depois sai.
    val toSeven: () -> Float = { if (isEditor) 0f else (shownPosition() - NotificationsStep).coerceIn(0f, 1f) }
    val closeEnabled = isEditor || step < TutorialStepCount
    val ringAlpha by animateFloatAsState(
        targetValue = if (introduction7Settled) 0f else 1f,
        animationSpec = CycloMotion.FadeSpring,
        label = "ringAlpha",
    )

    // Editor: no último passo o indicador dá lugar ao "Done" roxo.
    val done by animateFloatAsState(
        targetValue = if (isEditor && step == LastRoutineStep) 1f else 0f,
        animationSpec = CycloMotion.ButtonSwapSpring,
        label = "doneButton",
    )
    // O indicador: só na primeira página (Introduction 1; no editor, a hora de acordar, beta-3.2.1).
    // Apaga-se assim que a página começa a mudar e acende quando assenta.
    val hintAlpha: () -> Float = {
        if (restStep() != firstPage) {
            0f
        } else {
            // Sem a demonstração: o indicador fica à vista enquanto a página espreita.
            val p = if (dragging) dragPosition.floatValue else position.value
            1f - smoothStep(abs(p - restStep()), 0f, HintFadeReach)
        }
    }

    // O frame do Figma tem 48 dp de barra de navegação; reservamos pelo menos isso.
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val contentTop = introContentTop()
    // O conteúdo das páginas acaba acima do indicador / do "Done" (66 dp e 15 dp de folga).
    val contentBottom = bottomInset + CycloDimens.ButtonHeight + CycloDimens.Spacing

    // Em ecrãs de taxa adaptável, mexer só a translação de camadas vota 60 Hz: enquanto as páginas
    // se mexem pede-se a taxa alta (como o `cover` do CycloApp); paradas, nada.
    val moving by remember { derivedStateOf { dragging || position.isRunning || enter.isRunning } }

    val nextLabel = stringResource(R.string.next)
    val backLabel = stringResource(R.string.back)

    Box(
        modifier = modifier
            .fillMaxSize()
            .preferredFrameRate(if (moving) FrameRateCategory.High else FrameRateCategory.Default)
            .background(CycloColors.Background)
            // Vê cada toque antes de todos (passagem Initial) sem o consumir: é a interação da guia.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    interacted()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    touching = false
                }
            }
            .then(with(swipe) { Modifier.introSwipe() }),
    ) {
        if (!isEditor) {
            IntroductionZero(
                enter = shownEnter,
                lift = { if (step == 0) peek.value else 0f },
                covered = zeroCovered,
                effects = effects,
                hintIdle = { guideIdle },
                onGetStarted = { navigate(1) },
            )
        }

        if (showGroup) {
            // Tudo o que é do Introduction 1 em diante sobe junto (no editor, quem sobe é o fluxo
            // inteiro, por fora).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isEditor) return@graphicsLayer
                        val e = shownEnter()
                        if (effects.animationsEnabled) {
                            translationY = (1f - e.coerceAtMost(1f)) * size.height
                        } else {
                            alpha = e.coerceIn(0f, 1f)
                        }
                    }
                    .background(CycloColors.Background)
                    // Impede que os toques passem para o ecrã que está por baixo.
                    .pointerInput(Unit) { detectTapGestures { } }
                    // TalkBack: sem gestos, as páginas mudam pelas ações do contentor e pelo indicador.
                    .semantics {
                        customActions = buildList {
                            if (canForward() && !(isEditor && step == LastRoutineStep)) add(CustomAccessibilityAction(nextLabel) { navigate(1); true })
                            if (canBack()) add(CustomAccessibilityAction(backLabel) { navigate(-1); true })
                        }
                    },
            ) {
                // O que fica por baixo dos menus da repetição (action-menu-when-to-show e
                // action-menu-big-choose-days), que o desfocam: as páginas, o X com o anel roxo, o
                // indicador e o "Done" roxo (a cor deles passa desfocada, como no Figma). Os menus
                // ficam fora desta Box: não podem usar uma camada que os contenha. Tutorial e editor
                // são este mesmo código: o desfoque é igual.
                val pagesBackdrop = rememberBackdropLayer()
                Box(modifier = Modifier.fillMaxSize().backdropSource(pagesBackdrop)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val page: @Composable (Int, @Composable () -> Unit) -> Unit = { index, content ->
                            if (index in shownPages) {
                                IntroPage(
                                    index = index,
                                    position = shownPosition,
                                    lift = { if (step == index) peek.value else 0f },
                                    hasNext = index < lastStep,
                                    effects = effects,
                                    active = step == index,
                                    top = contentTop,
                                    bottom = contentBottom,
                                    content = content,
                                )
                            }
                        }
                        if (!isEditor) {
                            page(1) { Introduction1Page(enter = { shownEnter() }, iconMotion = iconMotions[1]) }
                        }
                        page(2) {
                            Introduction2Page(
                                iconMotion = iconMotions[2],
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
                        page(3) {
                            Introduction3Page(
                                iconMotion = iconMotions[3],
                                name = routineName,
                                onNameChange = {
                                    // A primeira letra escrita dá um toque à etiqueta.
                                    if (routineName.isEmpty() && it.isNotEmpty()) nudgeIcon(3)
                                    interacted()
                                    routineName = it
                                },
                            )
                        }
                        page(4) {
                            Introduction4Page(
                                iconMotion = iconMotions[4],
                                name = savedRoutineName,
                                // O que se muda aqui passa também para a caixa do Introduction 3.
                                onNameChange = {
                                    savedRoutineName = it
                                    routineName = it
                                },
                                repeat = repeat,
                            )
                        }
                        page(5) {
                            Introduction5Page(
                                iconMotion = iconMotions[5],
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
                        if (!isEditor) {
                            page(6) {
                                Introduction6Page(
                                    iconMotion = iconMotions[6],
                                    bedtimeOn = bedtimeShown,
                                    onToggleBedtime = {
                                        toggleReminder(BedtimeReminder.ChannelId, bedtimeShown) { bedtimeWanted = it }
                                    },
                                    logSleepOn = logSleepShown,
                                    onToggleLogSleep = {
                                        toggleReminder(LogSleepReminder.ChannelId, logSleepShown) { logSleepWanted = it }
                                    },
                                    hydrationOn = hydrationShown,
                                    onToggleHydration = {
                                        toggleReminder(HydrationReminder.ChannelId, hydrationShown) { hydrationWanted = it }
                                    },
                                    asks = notifications?.isPermissionGranted() != true,
                                )
                            }
                            page(7) {
                                Introduction7Page(
                                    active = introduction7Settled,
                                    onFinished = onFinished,
                                )
                            }
                        }
                    }

                    // FIXO: o X e o anel de progresso.
                    IntroTopBar(
                        progress = progress,
                        progressDescription = stringResource(
                            R.string.introduction_progress,
                            if (isEditor) step - EditorFirstStep + 1 else step.coerceAtLeast(1),
                            if (isEditor) LastRoutineStep - EditorFirstStep + 1 else TutorialStepCount,
                        ),
                        onClose = ::close,
                        closeAlpha = { 1f - toSeven() },
                        closeEnabled = closeEnabled,
                        ringAlpha = { ringAlpha },
                        modifier = Modifier
                            .padding(horizontal = CycloDimens.ScreenMargin)
                            .zIndex(1f),
                    )

                    // FIXO: o indicador e, no último passo do editor, o "Done" roxo.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .zIndex(1f),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // Só a página do passo já tem a ação "Next" do TalkBack: o indicador fica só a ler-se.
                        SwipeHint(
                            text = stringResource(R.string.introduction_swipe_continue),
                            alpha = hintAlpha,
                            idle = { guideIdle },
                            modifier = Modifier
                                .padding(bottom = bottomInset + HintBottomGap)
                                .then(if (restStep() != firstPage) Modifier.clearAndSetSemantics { } else Modifier),
                        )
                        if (done > 0f) {
                            CycloPrimaryButton(
                                text = stringResource(R.string.editor_done),
                                enabled = step == LastRoutineStep,
                                onClick = ::saveEditor,
                                textAlpha = { smoothStep(done, 0.45f, 1f) },
                                modifier = Modifier
                                    .padding(horizontal = CycloDimens.ScreenMargin)
                                    .padding(bottom = bottomInset)
                                    .graphicsLayer {
                                        alpha = done
                                        if (effects.animationsEnabled) translationY = (1f - done) * CycloMotion.ButtonsRise.toPx() * 0.25f
                                    },
                            )
                        }
                    }
                }

                // Menus por cima de tudo, incluindo o "Done" (zIndex 1).
                Box(Modifier.fillMaxSize().zIndex(2f)) {
                    CompositionLocalProvider(LocalBackdrop provides Backdrop.of(pagesBackdrop)) {
                        RepeatOverlays(selection = repeat, bottomMargin = bottomInset)
                    }
                }
            }
        }
    }
}

/** Do indicador à barra de navegação: 24 dp do texto (18 dp) num alvo de 48 dp, centrado. */
private val HintBottomGap = CycloDimens.ScreenMargin - (CycloDimens.TouchTarget - 18.dp) / 2

/**
 * O Introduction 0 por baixo do grupo: enquanto o grupo sobe, recua com zoom out, desfoque e um
 * véu escuro (seguem [enter]). Abaixo de 0 (a resistência do gesto para baixo), desce com o dedo.
 */
@Composable
private fun IntroductionZero(
    enter: () -> Float,
    lift: () -> Float,
    covered: Boolean,
    effects: VisualEffects,
    hintIdle: () -> Boolean,
    onGetStarted: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val e = enter()
                if (!effects.animationsEnabled) return@graphicsLayer
                if (e < 0f) {
                    translationY = -e * size.height
                    return@graphicsLayer
                }
                translationY = -lift() * size.height
                val s = 1f - (1f - CycloMotion.BackgroundScale) * e
                scaleX = s
                scaleY = s
                // Beta 2.4: o desfoque do recuo é uma textura avançada (desligadas, só o zoom e o véu).
                renderEffect = if (effects.textures && e > 0f && e < 1f) {
                    val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * e
                    BlurEffect(sigma, sigma, TileMode.Clamp)
                } else {
                    null
                }
                // Todo tapado: não se desenha (fica composto, com o relógio parado).
                alpha = if (e >= 1f) 0f else 1f
            }
            // Tapado pelo grupo: fica fora do alcance do TalkBack.
            .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        Introduction0Screen(
            onGetStarted = onGetStarted,
            recede = { enter().coerceIn(0f, 1f) },
            hintIdle = hintIdle,
        )
        // Véu #010101 a 24 % quando o ecrã está recuado.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = CycloMotion.BackgroundOverlayAlpha * enter().coerceIn(0f, 1f) }
                .background(CycloColors.Background),
        )
    }
}

/**
 * Uma página do fluxo, empilhada ([stackedPage]) e opaca (tapa a que recua). O conteúdo fica entre
 * o X e o anel ([top]) e o indicador ([bottom]). Só a do passo atual chega ao TalkBack.
 */
@Composable
private fun IntroPage(
    index: Int,
    position: () -> Float,
    lift: () -> Float,
    hasNext: Boolean,
    effects: VisualEffects,
    active: Boolean,
    top: Dp,
    bottom: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // A demonstração do gesto: só a página à vista, por cima do resto da transformação.
            .graphicsLayer { translationY = -lift() * size.height }
            .stackedPage(index, position, hasNext, effects)
            .background(CycloColors.Background)
            .padding(top = top, bottom = bottom)
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        content()
    }
}
