package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.Bedtime
import com.gaebriu.cyclo.data.DailyLog
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.SleepFactor
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

/** Sombra do cartão da hora de acordar: 0, 4, desfoque 24, #34739A a 24 %. */
private val WakeCardShadow = Color(0xFF34739A).copy(alpha = 0.24f)

/** Quantas posições tem o medidor de emoções (os pontos do Figma). */
private const val MoodSteps = 5

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
 * - As horas para adormecer são a hora de acordar menos 6 a 1 ciclos de 90 min e 15 min para
 *   adormecer (ver [bedtimesFor]); as duas com mais ciclos têm estrela.
 * - O "..." da hora de acordar abre "Edit" / "Remove"; o da cafeína abre "Reset".
 */
@Composable
fun FeedScreen(
    routines: List<Routine>,
    dailyLog: DailyLog?,
    reveal: StaggeredReveal,
    bottomReserve: Dp,
    onEditRoutine: (Routine) -> Unit,
    onRemoveRoutine: (Routine) -> Unit,
    onCaffeineChange: (Int) -> Unit,
    onCaffeineReset: () -> Unit,
    onToggleFactor: (SleepFactor) -> Unit,
    onMoodChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberMinuteClock()
    val today = now.toLocalDate()
    val log = dailyLog?.takeIf { it.date == today } ?: DailyLog(today)
    val nextRoutine = remember(routines, now) {
        routines
            .mapNotNull { routine -> routine.nextWake(now)?.let { routine to it } }
            .filter { (_, wake) -> Duration.between(now, wake) <= Duration.ofHours(24) }
            .minByOrNull { (_, wake) -> wake }
            ?.first
    }

    var wakeMenuOpen by remember { mutableStateOf(false) }
    var wakeMenuAnchor by remember { mutableStateOf(Rect.Zero) }
    var caffeineMenuOpen by remember { mutableStateOf(false) }
    var caffeineMenuAnchor by remember { mutableStateOf(Rect.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .cycloVerticalScroll(rememberScrollState())
                .padding(horizontal = CycloDimens.ScreenMargin)
                .padding(top = FeedContentTop, bottom = bottomReserve),
        ) {
            Greeting(now = now, reveal = reveal)

            if (nextRoutine != null) {
                Spacer(Modifier.height(SectionGap))
                Column(modifier = Modifier.staggeredReveal(reveal, FeedRevealItems.Wake)) {
                    SectionLabel(stringResource(R.string.feed_wake_title))
                    Spacer(Modifier.height(LabelToCard))
                    WakeTimeCard(
                        routine = nextRoutine,
                        onMoreClick = { wakeMenuOpen = true },
                        onMoreBounds = { wakeMenuAnchor = it },
                    )
                }
            }

            Spacer(Modifier.height(SectionGap))
            Column(modifier = Modifier.staggeredReveal(reveal, FeedRevealItems.Caffeine)) {
                SectionLabel(stringResource(R.string.feed_caffeine_title))
                Spacer(Modifier.height(LabelToCard))
                CaffeineCard(
                    count = log.caffeine,
                    onChange = onCaffeineChange,
                    onMoreClick = { caffeineMenuOpen = true },
                    onMoreBounds = { caffeineMenuAnchor = it },
                )
            }

            Spacer(Modifier.height(SectionGap))
            Column(modifier = Modifier.staggeredReveal(reveal, FeedRevealItems.Sleep)) {
                SectionLabel(stringResource(R.string.feed_sleep_title))
                Spacer(Modifier.height(LabelToCard))
                SleepFactorsCard(selected = log.factors, onToggle = onToggleFactor)
            }

            Spacer(Modifier.height(SectionGap))
            Column(modifier = Modifier.staggeredReveal(reveal, FeedRevealItems.Feeling)) {
                SectionLabel(stringResource(R.string.feed_feeling_title))
                Spacer(Modifier.height(LabelToCard))
                FeelingCard(mood = log.mood, onMoodChange = onMoodChange)
            }
        }

        // Menus do "...": por cima de tudo, ancorados ao botão (a direita do menu na direita do cartão).
        AnchoredMenuHost(
            visible = wakeMenuOpen && nextRoutine != null,
            anchor = { wakeMenuAnchor },
            onDismiss = { wakeMenuOpen = false },
            bottomMargin = bottomReserve,
            alignEnd = true,
        ) {
            CycloActionMenu(
                items = listOf(
                    ActionMenuItem(stringResource(R.string.action_edit)),
                    ActionMenuItem(stringResource(R.string.action_remove)),
                ),
                selectedIndex = -1,
                onItemClick = { index ->
                    wakeMenuOpen = false
                    val routine = nextRoutine ?: return@CycloActionMenu
                    if (index == 0) onEditRoutine(routine) else onRemoveRoutine(routine)
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

/** A hora atual, atualizada no início de cada minuto. */
@Composable
private fun rememberMinuteClock() = produceState(LocalDateTime.now()) {
    while (true) {
        val current = LocalDateTime.now()
        value = current
        val untilNextMinute = 60_000L - (current.second * 1000L + current.nano / 1_000_000L)
        delay(untilNextMinute.coerceAtLeast(250L))
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
    val locale = LocalConfiguration.current.locales[0]
    val date = stringResource(
        R.string.feed_date,
        now.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale),
        now.month.getDisplayName(DateTextStyle.FULL, locale),
        now.dayOfMonth,
        now.format(TimeFormatter),
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

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
 */
@Composable
private fun WakeTimeCard(routine: Routine, onMoreClick: () -> Unit, onMoreBounds: (Rect) -> Unit) {
    val bedtimes = remember(routine.wakeHour, routine.wakeMinute) { bedtimesFor(routine.wakeHour, routine.wakeMinute) }
    val wake = LocalTime.of(routine.wakeHour, routine.wakeMinute).format(TimeFormatter)
    val outset = (CycloDimens.TouchTarget - 15.dp) / 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cycloCard(shadowColor = WakeCardShadow, shadowBlur = 24.dp)
            .padding(CardPadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
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
            blendMode = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver,
        )
    }
}

@Composable
private fun BedtimeRow(bedtimes: List<Bedtime>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
    ) {
        bedtimes.forEach { bedtime ->
            val time = bedtime.time.format(TimeFormatter)
            val description = pluralStringResource(R.plurals.feed_bedtime_description, bedtime.cycles, time, bedtime.cycles)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                val (icon, tint) = bedtimeIcon(bedtime.cycles)
                CycloIcon(path = icon, size = 15.dp, tint = tint, luminosity = false)
                Spacer(Modifier.height(6.dp))
                BasicText(text = time, style = CycloType.CardTitle.withFeedShadow())
            }
        }
    }
}

/** Ícone de cada hora: estrela (6 e 5 ciclos) e as caras do medidor, de contente a insatisfeito. */
private fun bedtimeIcon(cycles: Int): Pair<String, Color> = when (cycles) {
    6, 5 -> CycloAppIconPaths.Star to StarYellow
    4 -> CycloAppIconPaths.SentimentSatisfied to MoodViolet
    3 -> CycloAppIconPaths.SentimentNeutral to MoodBlue
    2 -> CycloAppIconPaths.SentimentDissatisfied to MoodSky
    else -> CycloAppIconPaths.SentimentDissatisfied to MoodMint
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
            .height(63.dp)
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
        BasicText(
            text = count.toString(),
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
                        drawCardControl(9.dp.toPx(), if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver)
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
            .height(CycloDimens.InputHeight)
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
                val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
                val shape = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }
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
        BasicText(text = label, style = CycloType.CardTitle)
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

@Composable
private fun EmotionMeter(mood: Float, onMoodChange: (Float) -> Unit) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.feed_feeling_meter)
    val levels = listOf(
        stringResource(R.string.feed_feeling_level_1),
        stringResource(R.string.feed_feeling_level_2),
        stringResource(R.string.feed_feeling_level_3),
        stringResource(R.string.feed_feeling_level_4),
        stringResource(R.string.feed_feeling_level_5),
    )
    val stored = (mood * (MoodSteps - 1)).roundToInt().coerceIn(0, MoodSteps - 1)
    var step by remember { mutableIntStateOf(stored) }
    // A posição da bola (0 a 1), que segue o dedo e assenta no ponto com uma mola.
    val ball = remember { Animatable(stored / (MoodSteps - 1f)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(stored) {
        if (!dragging) {
            step = stored
            ball.animateTo(stored / (MoodSteps - 1f), CycloMotion.CardSpring)
        }
    }

    fun setStep(newStep: Int, haptic: Boolean) {
        if (newStep != step) {
            step = newStep
            if (haptic) CycloHaptics.pickerTick(view, manyChoices = false)
        }
    }

    fun settle() {
        scope.launch { ball.animateTo(step / (MoodSteps - 1f), CycloMotion.CardSpring) }
        onMoodChange(step / (MoodSteps - 1f))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                contentDescription = description
                stateDescription = levels[step]
                progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..(MoodSteps - 1f), MoodSteps - 2)
                setProgress { target ->
                    setStep(target.roundToInt().coerceIn(0, MoodSteps - 1), haptic = false)
                    settle()
                    true
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val ballInset = with(density) { 11.5.dp.toPx() }
        fun fractionAt(x: Float) = ((x - ballInset) / (widthPx - 2f * ballInset)).coerceIn(0f, 1f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        setStep((fractionAt(offset.x) * (MoodSteps - 1)).roundToInt(), haptic = true)
                        settle()
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            settle()
                        },
                        onDragCancel = {
                            dragging = false
                            settle()
                        },
                    ) { change, _ ->
                        val f = fractionAt(change.position.x)
                        scope.launch { ball.snapTo(f) }
                        setStep((f * (MoodSteps - 1)).roundToInt(), haptic = true)
                    }
                },
        ) {
            val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
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
                val x = lerp(ballInset, size.width - ballInset, i / (MoodSteps - 1f))
                drawCircle(
                    color = CycloColors.TextSecondary.copy(alpha = 0.15f),
                    radius = 4.5.dp.toPx(),
                    center = Offset(x, y),
                    blendMode = blend,
                )
            }
            drawCycloBall(Offset(lerp(ballInset, size.width - ballInset, ball.value), y))
        }
    }
}
