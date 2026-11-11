package com.gaebriu.cyclo.ui.main

import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.rubberBand
import kotlin.math.exp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.gaebriu.cyclo.ui.components.RollingNumber
import com.gaebriu.cyclo.ui.components.RollingText
import com.gaebriu.cyclo.data.BedtimeEngine
import com.gaebriu.cyclo.data.Chronotype
import com.gaebriu.cyclo.data.FallAsleepTime
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.time.LocalTimeFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import com.gaebriu.cyclo.ui.i18n.rememberAppLocale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.Bedtime
import com.gaebriu.cyclo.data.DailyLog
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.SleepFactor
import com.gaebriu.cyclo.data.FeedSection
import com.gaebriu.cyclo.ui.components.ReorderableColumn
import com.gaebriu.cyclo.ui.components.CycloMeter
import com.gaebriu.cyclo.ui.components.MeterStyle
import com.gaebriu.cyclo.data.bedtimesFor
import com.gaebriu.cyclo.data.CycloViewModel
import com.gaebriu.cyclo.data.Hydration
import com.gaebriu.cyclo.data.TrackingSection
import com.gaebriu.cyclo.data.cycloDay
import com.gaebriu.cyclo.data.resolveDayLogs
import com.gaebriu.cyclo.data.showsSleepPrompt
import com.gaebriu.cyclo.data.trackingSummary
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.gaebriu.cyclo.ui.components.ActionMenuItem
import com.gaebriu.cyclo.ui.components.AnchoredMenuHost
import com.gaebriu.cyclo.ui.components.CycloActionMenu
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.components.drawCardControl
import com.gaebriu.cyclo.ui.components.drawCycloBall
import com.gaebriu.cyclo.ui.components.drawInsideBorder
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.roundToInt

// Medidas do Figma (Feed, frame de 393 x 852).
/** O conteúdo começa a 126 dp do topo ("Good afternoon"; Cyclo-2 24:10, Beta 2.2: era 130). */
val FeedContentTop = 126.dp
private val GreetingToDate = 9.dp
private val SectionGap = 39.dp
private val LabelToCard = 15.dp
private val CardPadding = 15.dp

private val StarYellow = Color(0xFFFFC93C)

/** Sombra do cartão da hora de acordar: 0, 4, desfoque 24, a cor da base do brilho ([skyColorsAt]; #5FA2DB no Figma) a 24 %. */
private const val WakeCardShadowAlpha = 0.24f

/** Quanto as horas para dormir esperam, depois do último toque na cafeína, fatores ou emoções. */
private const val BedtimeSettleMs = 1200L

/** Índices dos blocos da entrada em cascata do Feed (ver [StaggeredReveal]). */
object FeedRevealItems {
    const val MiniMenu = 0
    const val Greeting = 1
    const val Date = 2
    /** O log-sleep-card (sleep-evaluation-prompt) do Unified view. */
    const val Prompt = 3
    const val Wake = 4
    /** O primeiro dos blocos reordenáveis ([FeedSection]); os outros seguem-no. */
    const val FirstSection = 5
    const val NavigationBar = FirstSection + 4
    const val Count = NavigationBar + 1
}

/**
 * Feed: a saudação, as horas recomendadas para adormecer a partir da rotina de amanhã, e o registo
 * do dia (cafeína, o que pode afetar o sono e como se sente).
 *
 * - A rotina mostrada é a primeira que acorda o utilizador nas próximas 24 horas (ligada, e com a
 *   repetição a incluir esse dia, ou "Show just this once" ainda por cumprir). Sem nenhuma, o grupo
 *   "To rise refreshed tomorrow" não aparece.
 * - As horas para adormecer são a hora de acordar menos 6 a 1 ciclos de 90 min e o tempo estimado
 *   para adormecer hoje (o "Time to fall asleep" das definições mais o que a cafeína, os fatores, as
 *   emoções e o cronótipo pedem; ver [BedtimeEngine] e [bedtimesFor]); as duas com mais ciclos têm
 *   estrela. Mudam ~1 s depois do último toque, e rolam para a hora nova.
 * - O "..." da hora de acordar abre "Edit" / "Add as a widget" / "Remove". A cafeína e a água não
 *   têm botão de repor (beta-3.0, Figma 24:10): o "−" volta atrás.
 * - Os blocos da cafeína, da água, do que pode afetar o sono e de como se sente reordenam-se com
 *   long press e arrastar ([feedOrder], [onReorder]; ver [ReorderableColumn]): os outros abrem espaço
 *   com a mesma física das rotinas guardadas. O wake-time-recommendation-group fica de fora e não muda.
 * - Unified view ([unified], beta-3.0, Cyclo-2 2066:259): a Tracking junta-se ao Feed. O
 *   log-sleep-card (o sleep-evaluation-prompt) fica 39 dp abaixo do header-greetings, e cada gráfico
 *   entra no cartão do Feed que lhe corresponde, por baixo de um separador, como o Restfulness no
 *   wake-time-container do Figma: o Restfulness no da hora de acordar, a cafeína e a água nos deles,
 *   o Mood no "How are you feeling tonight?". Sem rotina (sem o wake-time-container), o Restfulness
 *   fica num cartão da Tracking no lugar dele.
 */
@Composable
fun FeedScreen(
    now: LocalDateTime,
    routines: List<Routine>,
    dailyLog: DailyLog?,
    history: List<DailyLog>,
    age: Int?,
    chronotype: Chronotype,
    fallAsleep: FallAsleepTime,
    feedOrder: List<FeedSection>,
    reveal: StaggeredReveal,
    bottomReserve: Dp,
    wakeShadow: Color,
    onEditRoutine: (Routine) -> Unit,
    onRemoveRoutine: (Routine) -> Unit,
    onAddWakeWidget: () -> Unit,
    onCaffeineChange: (Int) -> Unit,
    onWaterChange: (Int) -> Unit,
    onToggleFactor: (SleepFactor) -> Unit,
    onMoodChange: (Float) -> Unit,
    onReorder: (List<FeedSection>) -> Unit,
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
    unified: Boolean = false,
    promptDismissed: Boolean = false,
    promptGlow: () -> Color = { Color.Transparent },
    onLogSleep: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
) {
    // O dia da Cyclo (beta-3.0): da meia-noite até à hora de acordar ainda é o dia anterior, por
    // isso o registo da noite e o wake-time-container só mudam quando passa a hora de acordar.
    val today = remember(routines, now) { cycloDay(routines, now) }
    // O registo onde o próximo toque cai (resolveDayLogs): o mesmo que o repositório escreve.
    val log = remember(dailyLog, history, today) { resolveDayLogs(dailyLog, history, today).current }
    // A mesma escolha do Wake Time Widget (BedtimeEngine.feedWake): a primeira hora de acordar a 24 h.
    val next = remember(routines, now) { BedtimeEngine.feedWake(routines, now) }
    val nextRoutine = next?.first

    // O tempo estimado para adormecer hoje (BedtimeEngine): o das definições mais o que a cafeína,
    // os fatores, as emoções e o cronótipo pedem. As horas para dormir recuam com ele; o sono
    // planeado nunca encolhe.
    val targetLatency = remember(next, log, age, chronotype, fallAsleep, now, today) {
        next?.let { (_, wake) ->
            BedtimeEngine.feedLatency(wake, now, age, chronotype, fallAsleep, log, today)
        } ?: fallAsleep.planMinutes.toLong()
    }
    // Só muda depois de os toques assentarem (nada de contas nem saltos a cada toque no "+").
    var latency by remember { mutableLongStateOf(targetLatency) }
    // Beta 2.4: quando as horas rolam por causa do que se marcou no Feed (cafeína, fatores,
    // emoções), um tique leve acompanha-as. Não toca quando mudam sozinhas (o relógio, a cafeína a
    // passar), por causa das definições, nem com o Feed fora de vista.
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    // Só o que se marca no Feed conta (o "opened" do dia e o sono registado também mudam o registo).
    val latestInputs by rememberUpdatedState(log.feedInputs())
    val latestCurrent by rememberUpdatedState(isCurrentPage)
    var appliedInputs by remember { mutableStateOf<DailyLog?>(log.feedInputs()) }
    LaunchedEffect(targetLatency) {
        if (targetLatency != latency) {
            delay(BedtimeSettleMs)
            latency = targetLatency
            val before = appliedInputs
            val inputs = latestInputs
            // No mesmo dia (à meia-noite o registo novo começa vazio, sem ninguém ter tocado).
            val fromFeed = inputs != before && inputs?.date == before?.date
            appliedInputs = inputs
            if (fromFeed && latestCurrent && effects.animationsEnabled && next != null) {
                CycloHaptics.pickerTick(view, manyChoices = true)
            }
        } else {
            appliedInputs = latestInputs
        }
    }

    // Unified view: o que a Tracking mostraria, para os gráficos dentro dos cartões (só se calcula
    // com ele ligado, e só quando os registos, o dia ou a idade mudam).
    val summary = if (unified) {
        remember(dailyLog, history, today, age) {
            resolveDayLogs(dailyLog, history, today).let { trackingSummary(it.history, it.current, today, age) }
        }
    } else {
        null
    }
    val showPrompt = unified && showsSleepPrompt(routines, log, now, promptDismissed)
    // Os hápticos dos gráficos só com o Feed escolhido; um só lambda, lembrado.
    val active = remember { { latestCurrent } }

    var wakeMenuOpen by remember { mutableStateOf(false) }
    var wakeMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    // O conteúdo fica por baixo dos menus do "...": é o que eles desfocam.
    val content = rememberBackdropLayer()
    val menuBackdrop = LocalBackdrop.current + content

    val scroll = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(content)
                .cycloVerticalScroll(scroll)
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(top = FeedContentTop, bottom = bottomReserve),
        ) {
            Greeting(now = now, reveal = reveal)

            // Unified view: o log-sleep-card a 39 dp do header-greetings (o espaço abre e fecha com ele).
            if (unified) {
                SleepPrompt(
                    visible = showPrompt,
                    glowColor = promptGlow,
                    onLogSleep = onLogSleep,
                    onDismiss = onDismissPrompt,
                    topGap = SectionGap,
                    modifier = Modifier.staggeredReveal(reveal, FeedRevealItems.Prompt),
                )
            }

            if (nextRoutine != null) {
                Spacer(Modifier.height(SectionGap))
                // A camada da entrada (fade + desfoque) desenha fora do ecrã e corta tudo o que sai
                // dos seus limites: alarga-se [CardShadowBleed] para a sombra do cartão entrar na
                // animação desde o início (antes só aparecia, de repente, no fim da entrada).
                Column(
                    modifier = Modifier
                        .bleed(CardShadowBleed)
                        .staggeredReveal(reveal, FeedRevealItems.Wake)
                        .padding(CardShadowBleed),
                ) {
                    SectionLabel(stringResource(R.string.feed_wake_title))
                    Spacer(Modifier.height(LabelToCard))
                    WakeTimeCard(
                        routine = nextRoutine,
                        fallAsleepMinutes = latency,
                        shadow = wakeShadow,
                        onMoreClick = { wakeMenuOpen = true },
                        onMoreBounds = { wakeMenuAnchor = it },
                        chart = summary?.let { sum ->
                            {
                                val grow = rememberChartGrowth(reveal, FeedRevealItems.Wake)
                                UnifiedChart(restfulnessDescription(sum)) { RestfulnessChart(sum, grow, active, ChartGeometry.Unified) }
                            }
                        },
                    )
                }
            } else if (summary != null) {
                // Unified view sem rotina: o Restfulness não tem o wake-time-container onde entrar, e
                // fica no cartão da Tracking, no mesmo lugar.
                Spacer(Modifier.height(SectionGap))
                Column(
                    modifier = Modifier
                        .bleed(CardShadowBleed)
                        .staggeredReveal(reveal, FeedRevealItems.Wake)
                        .padding(CardShadowBleed),
                ) {
                    val grow = rememberChartGrowth(reveal, FeedRevealItems.Wake)
                    TrackingSectionCard(TrackingSection.Restfulness, summary, grow, active)
                }
            }

            // Os outros blocos: long press e arrastar muda a ordem (o da hora de acordar, em cima,
            // fica sempre no seu lugar). O espaço antes de cada um vai dentro dele, para se
            // deslocar com ele. A entrada em cascata segue a ordem em que aparecem.
            ReorderableColumn(
                items = feedOrder,
                key = { it },
                onReorder = { order -> if (order != feedOrder) onReorder(order) },
                moveUpLabel = stringResource(R.string.reorder_move_up),
                moveDownLabel = stringResource(R.string.reorder_move_down),
                scrollState = scroll,
            ) { section, handle, surface ->
                val revealIndex = FeedRevealItems.FirstSection + feedOrder.indexOf(section).coerceAtLeast(0)
                Column(
                    modifier = Modifier
                        .bleed(CardShadowBleed)
                        .staggeredReveal(reveal, revealIndex)
                        .padding(CardShadowBleed),
                ) {
                    Spacer(Modifier.height(SectionGap))
                    // Agarra-se pelo título ou pelo cartão; só o cartão se levanta (escala e sombra).
                    Column(modifier = handle) {
                        when (section) {
                            FeedSection.Caffeine -> {
                                SectionLabel(stringResource(R.string.feed_caffeine_title))
                                Spacer(Modifier.height(LabelToCard))
                                CaffeineCard(
                                    count = log.caffeine,
                                    onChange = onCaffeineChange,
                                    modifier = surface,
                                    chart = summary?.let { sum ->
                                        {
                                            val grow = rememberChartGrowth(reveal, revealIndex)
                                            UnifiedChart(caffeineDescription(sum)) { CaffeineChart(sum, grow, active, ChartGeometry.Unified) }
                                        }
                                    },
                                )
                            }
                            FeedSection.Water -> {
                                SectionLabel(stringResource(R.string.feed_water_title))
                                Spacer(Modifier.height(LabelToCard))
                                WaterCard(
                                    ml = log.water,
                                    age = age,
                                    onChange = onWaterChange,
                                    modifier = surface,
                                    chart = summary?.let { sum ->
                                        {
                                            val grow = rememberChartGrowth(reveal, revealIndex)
                                            UnifiedChart(waterDescription(sum)) { WaterChart(sum, grow, active, ChartGeometry.Unified) }
                                        }
                                    },
                                )
                            }
                            FeedSection.SleepFactors -> {
                                SectionLabel(stringResource(R.string.feed_sleep_title))
                                Spacer(Modifier.height(LabelToCard))
                                SleepFactorsCard(selected = log.factors, onToggle = onToggleFactor, modifier = surface)
                            }
                            FeedSection.Feeling -> {
                                SectionLabel(stringResource(R.string.feed_feeling_title))
                                Spacer(Modifier.height(LabelToCard))
                                FeelingCard(
                                    mood = log.mood,
                                    onMoodChange = onMoodChange,
                                    modifier = surface,
                                    chart = summary?.let { sum ->
                                        {
                                            val grow = rememberChartGrowth(reveal, revealIndex)
                                            UnifiedChart(moodDescription(sum)) { MoodChart(sum, grow, ChartGeometry.Unified) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Menus do "...": por cima de tudo, ancorados ao botão (a direita do menu na direita do cartão).
        CompositionLocalProvider(LocalBackdrop provides menuBackdrop) {
        AnchoredMenuHost(
            visible = wakeMenuOpen && nextRoutine != null,
            anchor = { wakeMenuAnchor },
            onDismiss = { wakeMenuOpen = false },
            bottomMargin = bottomReserve,
            alignEnd = true,
        ) {
            CycloActionMenu(
                items = listOf(
                    // action-menu (Componentes, 80:727): Edit, Add as a widget, Remove.
                    ActionMenuItem(stringResource(R.string.action_edit)),
                    ActionMenuItem(stringResource(R.string.action_add_widget)),
                    ActionMenuItem(stringResource(R.string.action_remove)),
                ),
                selectedIndex = -1,
                onItemClick = { index ->
                    wakeMenuOpen = false
                    val routine = nextRoutine ?: return@CycloActionMenu
                    when (index) {
                        0 -> onEditRoutine(routine)
                        // O Wake Time Widget: a mesma hora de acordar e as mesmas horas do Feed.
                        1 -> onAddWakeWidget()
                        else -> onRemoveRoutine(routine)
                    }
                },
            )
        }
        }
    }
}

/** Sombra dos textos do Feed: 0, 0, desfoque 15, preto a 24 %. */
@Composable
private fun TextStyle.withFeedShadow(): TextStyle {
    val density = LocalDensity.current
    return remember(this, density) {
        copy(shadow = Shadow(Color.Black.copy(alpha = 0.24f), Offset.Zero, with(density) { 15.dp.toPx() }))
    }
}

@Composable
private fun Greeting(now: LocalDateTime, reveal: StaggeredReveal) {
    val greeting = stringResource(
        when (now.hour) {
            in 5..11 -> R.string.feed_greeting_morning
            in 12..17 -> R.string.feed_greeting_afternoon
            else -> R.string.feed_greeting_evening
        },
    )
    // header-greetings: o dia da semana e o mês na língua da app (não na do sistema), na ordem
    // dessa língua ("Monday, November 2" / "Segunda-feira, 2 de novembro").
    val locale = rememberAppLocale()
    val timeFormatter = LocalTimeFormat.current.formatter
    val date = stringResource(
        R.string.feed_date,
        now.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) },
        now.month.getDisplayName(DateTextStyle.FULL, locale),
        now.dayOfMonth,
        now.format(timeFormatter),
    )
    BasicText(
        text = greeting,
        style = CycloType.Greeting.withFeedShadow(),
        modifier = Modifier
            .staggeredReveal(reveal, FeedRevealItems.Greeting)
            .semantics { heading() },
    )
    Spacer(Modifier.height(GreetingToDate))
    BasicText(
        text = date,
        style = CycloType.GreetingDate.withFeedShadow(),
        modifier = Modifier.staggeredReveal(reveal, FeedRevealItems.Date),
    )
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text = text,
        style = CycloType.SectionLabel.withFeedShadow(),
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * Botão "..." (edit-b) de um cartão: ícone de 15 dp com uma zona de toque de 48 dp.
 * [onBounds] recebe onde o menu se ancora: o topo do ícone e a direita do cartão.
 */
@Composable
private fun MoreButton(description: String, onClick: () -> Unit, onBounds: (Rect) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(CycloDimens.TouchTarget)
            .onGloballyPositioned { coordinates ->
                val b = coordinates.boundsInWindow()
                val outset = with(density) { ((CycloDimens.TouchTarget - 15.dp) / 2).toPx() }
                val cardEdge = with(density) { CardPadding.toPx() }
                onBounds(Rect(b.left + outset, b.top + outset, b.right - outset + cardEdge, b.bottom - outset))
            }
            .cycloPressable(onClick = onClick, pressedScale = CycloMotion.IconButtonPressScale)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CycloIcon(path = CycloAppIconPaths.MoreHorizontal, size = 15.dp, tint = CycloColors.TextSecondary)
    }
}

/**
 * wake-time-container: o sol e a hora de acordar, o "...", um separador, e as horas para
 * adormecer em duas filas (as duas melhores com estrela, as outras com a cara do medidor).
 *
 * @param shadow a cor da base do brilho do topo (muda com a hora); a sombra usa-a a 24 %.
 */
@Composable
private fun WakeTimeCard(
    routine: Routine,
    fallAsleepMinutes: Long,
    shadow: Color,
    onMoreClick: () -> Unit,
    onMoreBounds: (Rect) -> Unit,
    chart: (@Composable () -> Unit)? = null,
) {
    val bedtimes = remember(routine.wakeHour, routine.wakeMinute, fallAsleepMinutes) {
        bedtimesFor(routine.wakeHour, routine.wakeMinute, fallAsleepMinutes)
    }
    val wake = LocalTime.of(routine.wakeHour, routine.wakeMinute).format(LocalTimeFormat.current.formatter)
    val outset = (CycloDimens.TouchTarget - 15.dp) / 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cycloCard(shadowColor = shadow.copy(alpha = WakeCardShadowAlpha), shadowBlur = 24.dp)
            .padding(CardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            CycloIcon(path = CycloAppIconPaths.Sunny, size = 15.dp, tint = CycloColors.TextPrimary)
            Spacer(Modifier.width(6.dp))
            BasicText(
                text = wake,
                style = CycloType.CardValue.withFeedShadow(),
                modifier = Modifier.semantics { contentDescription = "${routine.name}, $wake" },
            )
            Spacer(Modifier.weight(1f))
            // O ícone fica no sítio do Figma; a zona de toque sai para fora dele.
            Box(modifier = Modifier.size(15.dp)) {
                MoreButton(
                    description = stringResource(R.string.feed_wake_more),
                    onClick = onMoreClick,
                    onBounds = onMoreBounds,
                    modifier = Modifier.layoutOutset(outset),
                )
            }
        }
        Spacer(Modifier.height(SeparatorGap))
        Separator()
        Spacer(Modifier.height(SeparatorGap))
        BedtimeRow(bedtimes.take(2))
        Spacer(Modifier.height(15.dp))
        BedtimeRow(bedtimes.drop(2))
        // Unified view (2066:348): o separador (2066:619) a 15 dp das horas e o Restfulness 15 dp abaixo dele.
        if (chart != null) ChartBelow(chart)
    }
}

/** Do conteúdo de um cartão a um separador (2 dp, centrado 15 dp abaixo do conteúdo; Figma 31:19). */
private val SeparatorGap = 14.dp

/** Do separador ao gráfico do Unified view (o topo do gráfico 16 dp abaixo do centro do separador). */
private val SeparatorToChart = 15.dp

/** O separador e um gráfico do Unified view por baixo do conteúdo de um cartão. */
@Composable
private fun ChartBelow(chart: @Composable () -> Unit) {
    Spacer(Modifier.height(SeparatorGap))
    Separator()
    Spacer(Modifier.height(SeparatorToChart))
    chart()
}

/** Um gráfico do Unified view: o TalkBack lê-o pela descrição (o gráfico em si é só desenho). */
@Composable
private fun UnifiedChart(description: String, chart: @Composable () -> Unit) {
    Box(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) { chart() }
}

/** Faz uma caixa maior do que o lugar que ocupa (para zonas de toque à volta de ícones pequenos). */
private fun Modifier.layoutOutset(outset: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val extra = outset.roundToPx()
        val placeable = measurable.measure(
            constraints.copy(
                maxWidth = constraints.maxWidth + extra * 2,
                maxHeight = constraints.maxHeight + extra * 2,
                minWidth = 0,
                minHeight = 0,
            ),
        )
        layout(constraints.minWidth, constraints.minHeight) { placeable.place(-extra, -extra) }
    },
)

/** separator (31:19, 2066:619): linha de 2 dp com pontas redondas, #A3A2A7 a 15 % em Luminosity. */
@Composable
private fun Separator() {
    val effects = LocalVisualEffects.current
    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = CycloColors.TextSecondary.copy(alpha = 0.15f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            blendMode = effects.luminosity,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BedtimeRow(bedtimes: List<Bedtime>) {
    // Uma fila, como no Figma; com letra muito grande, as horas que não cabem passam para uma
    // segunda fila (inteiras) em vez de se partirem.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        val formatter = LocalTimeFormat.current.formatter
        val style = CycloType.CardTitle.withFeedShadow()
        bedtimes.forEach { bedtime ->
            key(bedtime.cycles) {
                val time = bedtime.time.format(formatter)
                val description = pluralStringResource(R.plurals.feed_bedtime_description, bedtime.cycles, time, bedtime.cycles)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                ) {
                    val (icon, tint) = bedtimeIcon(bedtime.cycles)
                    CycloIcon(path = icon, size = 15.dp, tint = tint, luminosity = false)
                    Spacer(Modifier.height(6.dp))
                    // Quando o dia pede mais tempo para adormecer, a hora recua: a nova desce de cima.
                    RollingText(text = time, style = style, direction = -1, description = time)
                }
            }
        }
    }
}

/** Ícone de cada hora: estrela (6 e 5 ciclos) e as caras do medidor, de contente a insatisfeito. */
private fun bedtimeIcon(cycles: Int): Pair<String, Color> = CycloAppIconPaths.bedtime(cycles) to when (cycles) {
    6, 5 -> StarYellow
    4 -> CycloColors.MoodViolet
    3 -> CycloColors.MoodBlue
    2 -> CycloColors.MoodSky
    else -> CycloColors.MoodMint
}

/**
 * caffeine-meter-group (37:125): chávena e cara à esquerda; "−", o número e "+" à direita. Sem o
 * "..." do reset (beta-3.0). No Unified view, o gráfico da cafeína por baixo de um separador.
 */
@Composable
private fun CaffeineCard(
    count: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chart: (@Composable () -> Unit)? = null,
) {
    val (face, faceTint) = caffeineFace(count)
    MeterCard(
        icon = CycloAppIconPaths.LocalCafe,
        face = face,
        faceTint = faceTint,
        lessDescription = stringResource(R.string.feed_caffeine_less),
        moreDescription = stringResource(R.string.feed_caffeine_more),
        canLess = count > 0,
        canMore = count < CycloViewModel.MaxCaffeine,
        onChange = onChange,
        modifier = modifier,
        chart = chart,
    ) {
        RollingNumber(value = count, style = CycloType.CardTitle)
    }
}

/**
 * water-meter-group (Cyclo-2 2066:148, beta-3.0): a gota (humidity_high) e a cara da avaliação da
 * água ([Hydration.level]: melhora até à quantidade adequada para a idade e volta a piorar quando é
 * demasiada); "−", os mL e "+" (passos de 250 mL) à direita. Sem botão de repor.
 */
@Composable
private fun WaterCard(
    ml: Int,
    age: Int?,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chart: (@Composable () -> Unit)? = null,
) {
    val level = Hydration.level(ml, age)
    val (face, faceTint) = moodFace(level)
    val amount = stringResource(R.string.feed_water_amount, ml)
    val state = stringResource(
        if (Hydration.isAbove(ml, age)) R.string.feed_water_state_above else R.string.feed_water_state,
        amount,
        moodLevelLabels()[level],
    )
    MeterCard(
        icon = CycloAppIconPaths.HumidityHigh,
        face = face,
        faceTint = faceTint,
        lessDescription = stringResource(R.string.feed_water_less),
        moreDescription = stringResource(R.string.feed_water_more),
        canLess = ml > 0,
        canMore = ml < Hydration.MaxMl,
        onChange = onChange,
        modifier = modifier,
        chart = chart,
    ) {
        // "250 mL": o número rola, a unidade fica.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics { contentDescription = state },
        ) {
            RollingNumber(value = ml, style = CycloType.CardTitle)
            BasicText(text = " " + stringResource(R.string.feed_water_unit), style = CycloType.CardTitle)
        }
    }
}

/**
 * Um cartão de contador do Feed (cafeína e água): o ícone e a cara à esquerda; "−", o valor e "+"
 * à direita, a 15 dp do valor. Sem gráfico, 63 dp com tudo ao centro; com o gráfico do Unified
 * view, o mesmo topo (15 dp) e o separador por baixo.
 */
@Composable
private fun MeterCard(
    icon: String,
    face: String,
    faceTint: Color,
    lessDescription: String,
    moreDescription: String,
    canLess: Boolean,
    canMore: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier,
    chart: (@Composable () -> Unit)?,
    value: @Composable () -> Unit,
) {
    val row: @Composable (Modifier) -> Unit = { rowModifier ->
        MeterRow(
            modifier = rowModifier.fillMaxWidth(),
            lead = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CycloIcon(path = icon, size = 15.dp, tint = CycloColors.TextPrimary)
                    Spacer(Modifier.width(9.dp))
                    CycloIcon(path = face, size = 15.dp, tint = faceTint, luminosity = false)
                }
            },
            controls = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquareButton(iconPath = CycloAppIconPaths.Remove, description = lessDescription, enabled = canLess, onClick = { onChange(-1) })
                    Box(modifier = Modifier.padding(horizontal = 15.dp)) { value() }
                    SquareButton(iconPath = CycloAppIconPaths.Add, description = moreDescription, enabled = canMore, onClick = { onChange(1) })
                }
            },
        )
    }
    if (chart == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 63.dp)
                .cycloCard()
                // 15 + 33 + 15 = os 63 do Figma numa linha; em duas (letra grande), a mesma margem.
                .padding(CardPadding),
            contentAlignment = Alignment.CenterStart,
        ) { row(Modifier) }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .cycloCard()
                .padding(CardPadding),
        ) {
            row(Modifier.heightIn(min = 33.dp))
            ChartBelow(chart)
        }
    }
}

/**
 * A linha de um contador: o ícone e a cara à esquerda, os controlos ("−", o valor, "+") à direita,
 * centrados na altura. Quando não cabem lado a lado (letra grande num ecrã estreito com "10000 mL"),
 * os controlos passam para a linha de baixo, encostados à direita, 15 dp abaixo: nada encolhe nem
 * sai do cartão (como as horas do wake-time-container, que passam para outra fila).
 */
@Composable
private fun MeterRow(modifier: Modifier, lead: @Composable () -> Unit, controls: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(contents = listOf(lead, controls), modifier = modifier) { (leadM, controlsM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val l = leadM.first().measure(loose)
        val c = controlsM.first().measure(loose)
        val width = constraints.maxWidth
        if (l.width + c.width <= width) {
            val height = maxOf(l.height, c.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                l.place(0, (height - l.height) / 2)
                c.place(width - c.width, (height - c.height) / 2)
            }
        } else {
            val gap = 15.dp.roundToPx()
            val height = (l.height + gap + c.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                l.place(0, 0)
                c.place((width - c.width).coerceAtLeast(0), l.height + gap)
            }
        }
    }
}

/** add-b / remove-b: 33 dp, raio 9 (a zona de toque tem 48 dp). */
@Composable
private fun SquareButton(iconPath: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val effects = LocalVisualEffects.current
    val outset = (CycloDimens.TouchTarget - 33.dp) / 2
    Box(modifier = Modifier.size(33.dp)) {
        Box(
            modifier = Modifier
                .layoutOutset(outset)
                .size(CycloDimens.TouchTarget)
                .cycloPressable(onClick = onClick, enabled = enabled, pressedScale = CycloMotion.IconButtonPressScale)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(33.dp)
                    .drawBehind {
                        drawCardControl(9.dp.toPx(), effects.luminosity)
                    },
                contentAlignment = Alignment.Center,
            ) {
                CycloIcon(path = iconPath, size = 15.dp, tint = CycloColors.TextPrimary, alpha = { if (enabled) 1f else 0.39f })
            }
        }
    }
}

/** might-affect-sleep-group: três opções que se ligam e desligam. */
@Composable
private fun SleepFactorsCard(selected: Set<SleepFactor>, onToggle: (SleepFactor) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cycloCard()
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        SleepFactor.entries.forEach { factor ->
            SleepFactorOption(
                label = stringResource(
                    when (factor) {
                        SleepFactor.IntenseExercise -> R.string.feed_factor_exercise
                        SleepFactor.HeavyLateMeal -> R.string.feed_factor_meal
                        SleepFactor.LongLateNap -> R.string.feed_factor_nap
                    },
                ),
                isSelected = factor in selected,
                onClick = { onToggle(factor) },
            )
        }
    }
}

/**
 * Uma opção (intense-exercise...): 48 dp, raio 15, #A3A2A7 a 15 % em Luminosity. Escolhida, ganha
 * a borda (#A3A2A7 a 24 %), o brilho de cima (0, -6, desfoque 9), a sombra interior e o visto;
 * a mudança faz-se com uma mola.
 */
@Composable
private fun SleepFactorOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val on by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = CycloMotion.CardSpring,
        label = "sleepFactor",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CycloDimens.InputHeight)
            .cycloPressable(
                onClick = {
                    CycloHaptics.toggle(view, !isSelected)
                    onClick()
                },
                haptic = false,
                role = Role.Checkbox,
            )
            .semantics { toggleableState = ToggleableState(isSelected) }
            .drawBehind {
                val radius = CycloDimens.InputRadius.toPx()
                val blend = effects.luminosity
                val shape = smoothRoundRectPath(size.width, size.height, radius)
                if (on > 0f) {
                    // Brilho de cima, só fora da forma.
                    clipPath(shape, clipOp = ClipOp.Difference) {
                        translate(top = -6.dp.toPx()) {
                            drawBlurredPath(shape, CycloColors.TextSecondary.copy(alpha = 0.15f * on), 9.dp.toPx())
                        }
                    }
                }
                drawPath(shape, CycloColors.TextSecondary.copy(alpha = 0.15f), blendMode = blend)
                if (on > 0f) {
                    drawInnerShadow(shape, Color.Black.copy(alpha = 0.15f * on), Offset(0f, 6.dp.toPx()), 15.dp.toPx())
                    drawInsideBorder(radius, Brush.linearGradient(List(2) { CycloColors.TextSecondary.copy(alpha = 0.24f * on) }))
                }
            }
            .padding(horizontal = CardPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text = label, style = CycloType.CardTitle, modifier = Modifier.padding(end = 24.dp))
        CycloIcon(
            path = CycloAppIconPaths.SmallCheck,
            size = 15.dp,
            tint = CycloColors.TextPrimary,
            alpha = { on },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * feeling-tonight-group: as caras dos dois extremos e o medidor de emoções (gradiente a 39 %), com
 * a bola sobre cinco pontos. Arrastar ou tocar muda a posição (com um tique em cada ponto); a bola
 * assenta no ponto mais perto com uma mola.
 */
@Composable
private fun FeelingCard(
    mood: Float,
    onMoodChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    chart: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cycloCard()
            .padding(CardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            CycloIcon(path = CycloAppIconPaths.Mood, size = 15.dp, tint = CycloColors.MoodViolet, luminosity = false)
            Spacer(Modifier.weight(1f))
            CycloIcon(path = CycloAppIconPaths.SentimentDissatisfied, size = 15.dp, tint = CycloColors.MoodMint, luminosity = false)
        }
        Spacer(Modifier.height(12.dp))
        CycloMeter(
            value = mood,
            levels = listOf(
                stringResource(R.string.feed_feeling_level_1),
                stringResource(R.string.feed_feeling_level_2),
                stringResource(R.string.feed_feeling_level_3),
                stringResource(R.string.feed_feeling_level_4),
                stringResource(R.string.feed_feeling_level_5),
            ),
            description = stringResource(R.string.feed_feeling_meter),
            onValueChange = onMoodChange,
            style = MeterStyle.Emotion,
        )
        // Unified view: o Mood por baixo do medidor.
        if (chart != null) ChartBelow(chart)
    }
}

/** O que se marca no Feed e muda as horas para dormir (Beta 2.4, ver o háptico das horas a rolar). */
private fun DailyLog.feedInputs(): DailyLog = DailyLog(
    date = date,
    caffeine = caffeine,
    factors = factors,
    mood = mood,
    caffeineTimes = caffeineTimes,
    factorTimes = factorTimes,
)
