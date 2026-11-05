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
import com.gaebriu.cyclo.data.bedtimesFor
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
/** O conteúdo começa a 130 dp do topo ("Good afternoon"). */
val FeedContentTop = 130.dp
private val GreetingToDate = 9.dp
private val SectionGap = 39.dp
private val LabelToCard = 15.dp
private val CardPadding = 15.dp

/** Cores das caras e do medidor de emoções (os pontos do gradiente "emotion-meter-background"). */
private val MoodViolet = Color(0xFFB3A3E8)
private val MoodBlue = Color(0xFF8FA8F0)
private val MoodSky = Color(0xFF7FC1E8)
private val MoodMint = Color(0xFF8FD9C4)
private val StarYellow = Color(0xFFFFC93C)

/** Sombra do cartão da hora de acordar: 0, 4, desfoque 24, a cor da base do brilho (#34739A no Figma) a 24 %. */
private const val WakeCardShadowAlpha = 0.24f

/** Quantas posições tem o medidor de emoções (os pontos do Figma). */
private const val MoodSteps = 5

/** Quanto as horas para dormir esperam, depois do último toque na cafeína, fatores ou emoções. */
private const val BedtimeSettleMs = 1200L

/** Índices dos blocos da entrada em cascata do Feed (ver [StaggeredReveal]). */
object FeedRevealItems {
    const val MiniMenu = 0
    const val Greeting = 1
    const val Date = 2
    const val Wake = 3
    const val Caffeine = 4
    const val Sleep = 5
    const val Feeling = 6
    const val NavigationBar = 7
    const val Count = 8
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
 * - O "..." da hora de acordar abre "Edit" / "Remove"; o da cafeína abre "Reset".
 * - Os blocos da cafeína, do que pode afetar o sono e de como se sente reordenam-se com long press e
 *   arrastar ([feedOrder], [onReorder]; ver [ReorderableColumn]): os outros abrem espaço com a mesma
 *   física das rotinas guardadas. O wake-time-recommendation-group fica de fora e não muda.
 */
@Composable
fun FeedScreen(
    now: LocalDateTime,
    routines: List<Routine>,
    dailyLog: DailyLog?,
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
    onCaffeineReset: () -> Unit,
    onToggleFactor: (SleepFactor) -> Unit,
    onMoodChange: (Float) -> Unit,
    onReorder: (List<FeedSection>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
    val log = dailyLog?.takeIf { it.date == today } ?: DailyLog(today)
    // A mesma escolha do Wake Time Widget (BedtimeEngine.feedWake): a primeira hora de acordar a 24 h.
    val next = remember(routines, now) { BedtimeEngine.feedWake(routines, now) }
    val nextRoutine = next?.first

    // O tempo estimado para adormecer hoje (BedtimeEngine): o das definições mais o que a cafeína,
    // os fatores, as emoções e o cronótipo pedem. As horas para dormir recuam com ele; o sono
    // planeado nunca encolhe.
    val targetLatency = remember(next, dailyLog, age, chronotype, fallAsleep, now) {
        next?.let { (_, wake) ->
            BedtimeEngine.feedLatency(wake, now, age, chronotype, fallAsleep, dailyLog)
        } ?: fallAsleep.planMinutes.toLong()
    }
    // Só muda depois de os toques assentarem (nada de contas nem saltos a cada toque no "+").
    var latency by remember { mutableLongStateOf(targetLatency) }
    LaunchedEffect(targetLatency) {
        if (targetLatency != latency) {
            delay(BedtimeSettleMs)
            latency = targetLatency
        }
    }

    var wakeMenuOpen by remember { mutableStateOf(false) }
    var wakeMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var caffeineMenuOpen by remember { mutableStateOf(false) }
    var caffeineMenuAnchor by remember { mutableStateOf(Rect.Zero) }
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

            if (nextRoutine != null) {
                Spacer(Modifier.height(SectionGap))
                // A camada da entrada (fade + desfoque) desenha fora do ecrã e corta tudo o que sai
                // dos seus limites: alarga-se [WakeShadowBleed] para a sombra do cartão entrar na
                // animação desde o início (antes só aparecia, de repente, no fim da entrada).
                Column(
                    modifier = Modifier
                        .bleed(WakeShadowBleed)
                        .staggeredReveal(reveal, FeedRevealItems.Wake)
                        .padding(WakeShadowBleed),
                ) {
                    SectionLabel(stringResource(R.string.feed_wake_title))
                    Spacer(Modifier.height(LabelToCard))
                    WakeTimeCard(
                        routine = nextRoutine,
                        fallAsleepMinutes = latency,
                        shadow = wakeShadow,
                        onMoreClick = { wakeMenuOpen = true },
                        onMoreBounds = { wakeMenuAnchor = it },
                    )
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
            ) { section, handle ->
                val revealIndex = FeedRevealItems.Caffeine + feedOrder.indexOf(section).coerceAtLeast(0)
                Column(modifier = Modifier.staggeredReveal(reveal, revealIndex)) {
                    Spacer(Modifier.height(SectionGap))
                    Column(modifier = handle) {
                        when (section) {
                            FeedSection.Caffeine -> {
                                SectionLabel(stringResource(R.string.feed_caffeine_title))
                                Spacer(Modifier.height(LabelToCard))
                                CaffeineCard(
                                    count = log.caffeine,
                                    onChange = onCaffeineChange,
                                    onMoreClick = { caffeineMenuOpen = true },
                                    onMoreBounds = { caffeineMenuAnchor = it },
                                )
                            }
                            FeedSection.SleepFactors -> {
                                SectionLabel(stringResource(R.string.feed_sleep_title))
                                Spacer(Modifier.height(LabelToCard))
                                SleepFactorsCard(selected = log.factors, onToggle = onToggleFactor)
                            }
                            FeedSection.Feeling -> {
                                SectionLabel(stringResource(R.string.feed_feeling_title))
                                Spacer(Modifier.height(LabelToCard))
                                FeelingCard(mood = log.mood, onMoodChange = onMoodChange)
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
        AnchoredMenuHost(
            visible = caffeineMenuOpen,
            anchor = { caffeineMenuAnchor },
            onDismiss = { caffeineMenuOpen = false },
            bottomMargin = bottomReserve,
            alignEnd = true,
        ) {
            CycloActionMenu(
                items = listOf(ActionMenuItem(stringResource(R.string.feed_caffeine_reset))),
                selectedIndex = -1,
                onItemClick = {
                    caffeineMenuOpen = false
                    onCaffeineReset()
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
        Spacer(Modifier.height(15.dp))
        Separator()
        Spacer(Modifier.height(15.dp))
        BedtimeRow(bedtimes.take(2))
        Spacer(Modifier.height(15.dp))
        BedtimeRow(bedtimes.drop(2))
    }
}

/**
 * Dá [amount] a mais de cada lado aos modificadores que vêm a seguir (ex.: a camada da entrada),
 * sem mudar o lugar que o bloco ocupa. Quem vem depois devolve o espaço com `padding(amount)`.
 */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(px * 2, px * 2))
    layout(placeable.width - px * 2, placeable.height - px * 2) { placeable.place(-px, -px) }
}

/**
 * Até onde chega a sombra do wake-time-container (0, 4, desfoque 24 → sigma 12, ~3 sigma + 4) e,
 * por isso, quanto a camada da entrada se alarga à volta dele.
 */
private val WakeShadowBleed = 44.dp

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

/** separator: linha de 0,5 dp, #A3A2A7 a 15 % em Luminosity. */
@Composable
private fun Separator() {
    val effects = LocalVisualEffects.current
    Canvas(modifier = Modifier.fillMaxWidth().height(0.5.dp)) {
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
    4 -> MoodViolet
    3 -> MoodBlue
    2 -> MoodSky
    else -> MoodMint
}

/** Cara ao lado da chávena: muda com a cafeína (0 a 2 contente ... 6 ou mais insatisfeito). */
private fun caffeineFace(count: Int): Pair<String, Color> = when {
    count <= 2 -> CycloAppIconPaths.SentimentSatisfied to MoodViolet
    count <= 4 -> CycloAppIconPaths.SentimentNeutral to MoodBlue
    count == 5 -> CycloAppIconPaths.SentimentDissatisfied to MoodSky
    else -> CycloAppIconPaths.SentimentDissatisfied to MoodMint
}

/** caffeine-meter-group: chávena e cara à esquerda; "−", o número e "+" à direita; e o "...". */
@Composable
private fun CaffeineCard(
    count: Int,
    onChange: (Int) -> Unit,
    onMoreClick: () -> Unit,
    onMoreBounds: (Rect) -> Unit,
) {
    val outset = (CycloDimens.TouchTarget - 15.dp) / 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 63.dp)
            .cycloCard()
            .padding(horizontal = CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (face, faceTint) = caffeineFace(count)
        CycloIcon(path = CycloAppIconPaths.LocalCafe, size = 15.dp, tint = CycloColors.TextPrimary)
        Spacer(Modifier.width(9.dp))
        CycloIcon(path = face, size = 15.dp, tint = faceTint, luminosity = false)
        Spacer(Modifier.weight(1f))
        SquareButton(
            iconPath = CycloAppIconPaths.Remove,
            description = stringResource(R.string.feed_caffeine_less),
            enabled = count > 0,
            onClick = { onChange(-1) },
        )
        RollingNumber(
            value = count,
            style = CycloType.CardTitle,
            modifier = Modifier.padding(horizontal = 15.dp),
        )
        SquareButton(
            iconPath = CycloAppIconPaths.Add,
            description = stringResource(R.string.feed_caffeine_more),
            enabled = true,
            onClick = { onChange(1) },
        )
        Spacer(Modifier.width(15.dp))
        Box(modifier = Modifier.size(15.dp)) {
            MoreButton(
                description = stringResource(R.string.feed_caffeine_more_actions),
                onClick = onMoreClick,
                onBounds = onMoreBounds,
                modifier = Modifier.layoutOutset(outset),
            )
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
private fun SleepFactorsCard(selected: Set<SleepFactor>, onToggle: (SleepFactor) -> Unit) {
    Column(
        modifier = Modifier
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
private fun FeelingCard(mood: Float, onMoodChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cycloCard()
            .padding(CardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            CycloIcon(path = CycloAppIconPaths.Mood, size = 15.dp, tint = MoodViolet, luminosity = false)
            Spacer(Modifier.weight(1f))
            CycloIcon(path = CycloAppIconPaths.SentimentDissatisfied, size = 15.dp, tint = MoodMint, luminosity = false)
        }
        Spacer(Modifier.height(12.dp))
        EmotionMeter(mood = mood, onMoodChange = onMoodChange)
    }
}

/**
 * O medidor de emoções: a bola segue o dedo (com resistência para lá das pontas) e, ao largar,
 * escolhe o ponto pela posição e pelo lançamento, e assenta lá com uma mola que parte com a
 * velocidade do dedo. Cresce um pouco enquanto se segura. Um tique em cada ponto que passa.
 * Tocar num sítio leva a bola para o ponto mais perto.
 */
@Composable
private fun EmotionMeter(mood: Float, onMoodChange: (Float) -> Unit) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val description = stringResource(R.string.feed_feeling_meter)
    val levels = listOf(
        stringResource(R.string.feed_feeling_level_1),
        stringResource(R.string.feed_feeling_level_2),
        stringResource(R.string.feed_feeling_level_3),
        stringResource(R.string.feed_feeling_level_4),
        stringResource(R.string.feed_feeling_level_5),
    )
    val last = MoodSteps - 1
    val stored = (mood * last).roundToInt().coerceIn(0, last)
    var step by remember { mutableIntStateOf(stored) }
    // A posição da bola (0 a 1; passa um pouco das pontas com a resistência e a mola).
    val ball = remember { Animatable(stored / last.toFloat()) }
    // Enquanto o dedo arrasta, a bola está no dedo ([dragFinger]) mais a distância a que estava
    // dele quando o arrasto começou ([grabOffset], que se fecha sozinha em poucos fotogramas): sem
    // corrotinas a cada movimento e sem saltar para o dedo.
    var dragging by remember { mutableStateOf(false) }
    var dragFinger by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var grabJob by remember { mutableStateOf<Job?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var held by remember { mutableStateOf(false) }
    // State (sem "by"): o tamanho da bola só se lê ao desenhar, por isso a mola dela redesenha o
    // medidor sem o recompor (antes recompunha-o a cada fotograma enquanto crescia).
    val ballScale = animateFloatAsState(
        targetValue = if ((held || dragging) && effects.animationsEnabled) CycloMotion.MoodBallHeldScale else 1f,
        animationSpec = CycloMotion.MoodBallScaleSpring,
        label = "moodBall",
    )
    // A última escolha feita aqui (e quando): um valor guardado mais antigo que chegue depois (duas
    // escolhas seguidas) não leva a bola para trás.
    var localStep by remember { mutableIntStateOf(-1) }
    var localAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(stored) {
        if (localStep >= 0) {
            if (stored == localStep) {
                localStep = -1
                return@LaunchedEffect
            }
            if (System.currentTimeMillis() - localAt < PendingMoodMs) return@LaunchedEffect
            localStep = -1
        }
        // Mudou fora daqui (outro dia, restauro): a bola vai para lá.
        if (!dragging && stored != step) {
            step = stored
            if (effects.animationsEnabled) ball.animateTo(stored / last.toFloat(), CycloMotion.MoodSettleSpring) else ball.snapTo(stored / last.toFloat())
        }
    }

    fun setStep(newStep: Int, haptic: Boolean) {
        if (newStep != step) {
            step = newStep
            if (haptic) CycloHaptics.pickerTick(view, manyChoices = false)
        }
    }

    /**
     * Leva a bola para o ponto escolhido, a partir de [from] e com [velocity] (fração/s). A bola
     * passa do dedo para a mola no mesmo fotograma ([dragging] só desliga depois de a mola estar na
     * posição do dedo) e a escolha só se grava quando a bola assenta, para a recomposição do Feed
     * (as horas a dormir mudam) não cair a meio da mola.
     */
    fun settle(from: Float, velocity: Float = 0f) {
        val chosen = step
        val target = chosen / last.toFloat()
        localStep = chosen
        localAt = System.currentTimeMillis()
        settleJob = scope.launch {
            try {
                ball.snapTo(from)
                dragging = false
                if (effects.animationsEnabled) {
                    ball.animateTo(target, CycloMotion.MoodSettleSpring, initialVelocity = velocity)
                } else {
                    ball.snapTo(target)
                }
            } finally {
                // Também se um arrasto novo interromper a mola (grava-se depois a escolha dele).
                onMoodChange(target)
            }
        }
    }

    /**
     * A posição da bola enquanto o dedo arrasta: o dedo com o encaixe suave dos pontos
     * ([moodDetent]) mais o que falta fechar da distância inicial, com resistência para lá das pontas.
     */
    fun dragPosition(): Float = rubberBandFraction(moodDetent(dragFinger, last) + grabOffset)

    val ballInset = with(density) { 11.5.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            // O medidor arrasta na horizontal: o swipe entre páginas não começa aqui.
            .excludeFromPageSwipe()
            .semantics {
                contentDescription = description
                stateDescription = levels[step]
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..last.toFloat(), MoodSteps - 2)
                setProgress { target ->
                    setStep(target.roundToInt().coerceIn(0, last), haptic = false)
                    settle(ball.value)
                    true
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                    ) { offset ->
                        val trackPx = (size.width - 2f * ballInset).coerceAtLeast(1f)
                        setStep((((offset.x - ballInset) / trackPx).coerceIn(0f, 1f) * last).roundToInt(), haptic = true)
                        settle(if (dragging) dragPosition() else ball.value)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val trackPx = (size.width - 2f * ballInset).coerceAtLeast(1f)
                        fun rawFractionAt(x: Float) = (x - ballInset) / trackPx
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        } ?: return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(start.uptimeMillis, start.position)
                        // A bola começa onde está (a meio de uma mola, se for o caso) e não salta
                        // para o dedo: a distância entre os dois fecha-se sozinha (mola crítica, a
                        // partir deste mesmo fotograma), e daí em diante a bola fica debaixo do dedo.
                        // A mola anterior pára já (antes, ao acabar, devolvia a bola a meio do arrasto).
                        settleJob?.cancel()
                        val shown = if (dragging) dragPosition() else ball.value
                        val finger = rawFractionAt(start.position.x)
                        dragFinger = finger
                        // Medida a partir do dedo já com o encaixe: a bola não salta no primeiro frame.
                        grabOffset = shown - moodDetent(finger, last)
                        dragging = true
                        grabJob?.cancel()
                        grabJob = if (effects.animationsEnabled) {
                            scope.launch { closeGrab(grabOffset) { grabOffset = it } }
                        } else {
                            grabOffset = 0f
                            null
                        }
                        setStep((dragPosition().coerceIn(0f, 1f) * last).roundToInt(), haptic = true)
                        horizontalDrag(start.id) { change ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragFinger = rawFractionAt(change.position.x)
                            setStep((dragPosition().coerceIn(0f, 1f) * last).roundToInt(), haptic = true)
                        }
                        // Larga-se onde a bola está desenhada; a mola parte daí, com a velocidade do dedo.
                        grabJob?.cancel()
                        val released = dragPosition()
                        val velocity = tracker.calculateVelocity().x / trackPx
                        val projected = (released + velocity * CycloMotion.MoodFlingProjectionS).coerceIn(0f, 1f)
                        setStep((projected * last).roundToInt(), haptic = true)
                        settle(released, velocity)
                    }
                },
        ) {
            val blend = effects.luminosity
            // emotion-meter-background: gradiente a 39 %, de violeta a menta.
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to MoodViolet.copy(alpha = 0.39f),
                    0.25f to MoodBlue.copy(alpha = 0.39f),
                    0.75f to MoodSky.copy(alpha = 0.39f),
                    1f to MoodMint.copy(alpha = 0.39f),
                ),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            // Os pontos (9 dp, #A3A2A7 a 15 % em Luminosity), nas cinco posições da bola.
            val y = size.height / 2f
            for (i in 0 until MoodSteps) {
                val x = lerp(ballInset, size.width - ballInset, i / last.toFloat())
                drawCircle(
                    color = CycloColors.TextSecondary.copy(alpha = 0.15f),
                    radius = 4.5.dp.toPx(),
                    center = Offset(x, y),
                    blendMode = blend,
                )
            }
            // Enquanto arrasta: o dedo mais o que falta fechar da distância inicial (lido aqui, a cada fotograma).
            val position = if (dragging) dragPosition() else ball.value
            drawCycloBall(Offset(lerp(ballInset, size.width - ballInset, position), y), ballScale.value)
        }
    }
}

/** Quanto tempo uma escolha feita no medidor manda sobre valores guardados diferentes (ms). */
private const val PendingMoodMs = 2_000L

/**
 * Para lá das pontas, a bola anda cada vez menos do que o dedo (a resistência do iOS, [rubberBand]):
 * começa com [CycloMotion.MoodRubberBand] do movimento e aproxima-se de [CycloMotion.MoodRubberBandMax]
 * sem nunca bater num limite seco (antes parava de repente no máximo, como contra uma parede).
 */
private fun rubberBandFraction(f: Float): Float = when {
    f < 0f -> rubberBand(f, CycloMotion.MoodRubberBandMax, CycloMotion.MoodRubberBand)
    f > 1f -> 1f + rubberBand(f - 1f, CycloMotion.MoodRubberBandMax, CycloMotion.MoodRubberBand)
    else -> f
}

/**
 * Encaixe suave nos cinco pontos enquanto se arrasta: perto de um ponto a bola anda um pouco menos
 * do que o dedo (até [MoodDetentPull] da distância, a [MoodDetentReach] de um passo), e longe deles
 * acompanha-o por inteiro. A curva é contínua e sempre a subir (a bola nunca anda para trás nem
 * salta): o dedo sente os pontos sem a bola deixar de o seguir. [f] é a posição do dedo (0 a 1,
 * pode passar das pontas) e [last] o índice do último ponto.
 */
private fun moodDetent(f: Float, last: Int): Float {
    val s = f * last
    val nearest = s.roundToInt().coerceIn(0, last)
    val d = s - nearest
    val u = (kotlin.math.abs(d) / MoodDetentReach).coerceIn(0f, 1f)
    val smooth = u * u * (3f - 2f * u)
    // Junto ao ponto anda (1 - pull) do dedo; ao afastar-se volta, suavemente, a andar com ele.
    val eased = d * (1f - MoodDetentPull * (1f - smooth))
    return (nearest + eased) / last
}

/** Quanto do movimento o encaixe segura junto a um ponto (0 = nada). */
private const val MoodDetentPull = 0.35f

/** Até onde chega o encaixe, em frações de um passo entre pontos. */
private const val MoodDetentReach = 0.28f

/**
 * Fecha a distância entre a bola e o dedo no início de um arrasto: uma mola crítica (sem passar
 * do dedo) com a resposta de [CycloSprings.Snappy], fotograma a fotograma, a partir de [from].
 */
private suspend fun closeGrab(from: Float, set: (Float) -> Unit) {
    val omega = (2.0 * Math.PI / CycloSprings.Snappy.response).toFloat()
    var startNanos = -1L
    while (true) {
        val done = withFrameNanos { now ->
            if (startNanos < 0) startNanos = now
            val t = (now - startNanos) / 1_000_000_000f
            val k = (1f + omega * t) * exp(-omega * t)
            set(from * k)
            k < 0.001f
        }
        if (done) {
            set(0f)
            return
        }
    }
}
