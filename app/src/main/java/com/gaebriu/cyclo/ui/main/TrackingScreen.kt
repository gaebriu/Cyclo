package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.DailyLog

import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.SleepQuality
import com.gaebriu.cyclo.data.TrackedDay
import com.gaebriu.cyclo.data.TrackingSection
import com.gaebriu.cyclo.data.TrackingSummary
import com.gaebriu.cyclo.data.blockAlpha
import com.gaebriu.cyclo.data.showsSleepPrompt
import com.gaebriu.cyclo.data.trackingSummary
import com.gaebriu.cyclo.ui.components.CycloButtonSize
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.ReorderableColumn
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import com.gaebriu.cyclo.ui.effects.InnerShadowSpec
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.innerShadowSprite
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.i18n.rememberAppLocale
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.springProgress
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.roundToInt

// Medidas do Figma (Tracking, Cyclo-2 2001:115 e 2006:449; frame de 393 x 852).
/** O conteúdo começa a 126 dp do topo (o primeiro cartão). */
private val TrackingContentTop = 126.dp
private val CardPadding = 15.dp

/** A largura do conteúdo de um cartão no Figma (345 - 2 x 15): os gráficos são desenhados para ela. */
private const val FigmaContent = 315f

/** Do título (18 dp de altura) à linha de cima dos gráficos. */
private val TitleToChart = 7.dp

/** A cara da média ("average", "average-mood") desce isto do topo do título. */
private val AverageTop = 1.dp

/** Do título à grelha do Mood. */
private val TitleToMoodGrid = 15.dp

// Gráficos (dp do Figma, a partir da linha de cima e da esquerda do conteúdo do cartão).
private const val Cell = 15f
private const val CellPitch = 24f
private const val BarTop = 8f
private const val BarBottom = 151f
private const val BaseTop = 157f
private const val LabelTop = 165f
private const val GridWidth = 87f
private const val GridHeight = 183f
private const val SleepWeekWidth = 183f
private const val CaffeineWeekWidth = 199f
private const val LegendX = 168f
private const val CaffeineFaceX = 184f
private const val MoodColumns = 13
private const val MoodGridWidth = 303f

/** A altura das colunas do Restfulness por nível (Really well 143 ... Very poor 22), alinhadas com as caras da legenda. */
private val SleepBarHeights = floatArrayOf(143f, 112f, 82f, 52f, 22f)

/** O topo das caras da legenda (Really well em cima). */
private val LegendTops = floatArrayOf(9f, 39f, 69f, 99f, 129f)

/** O cinzento de um dia por registar: #A3A2A7 a 15 % em Luminosity. */
private val NotLoggedColor = CycloColors.TextSecondary.copy(alpha = 0.15f)

/** A linha de cima dos gráficos: #A3A2A7 a 15 %. */
private val ChartTopLineColor = CycloColors.TextSecondary.copy(alpha = 0.15f)

/** As iniciais dos dias ocupam no máximo isto da distância entre colunas. */
private const val DayLabelMaxShare = 0.8f

/** O "3" do máximo da semana: #A3A2A7 a 63 % em Luminosity. */
private val MaxLabelColor = CycloColors.TextSecondary.copy(alpha = 0.63f)

/**
 * A opacidade do "fx blur type 01 (base)" do prompt (2006:417, 315 x 33): a cor é a base do brilho
 * da hora atual ([skyColorsAt]; #5FA2DB das 11:30 às 15:30, a do Figma) a 24 %.
 */
private const val PromptGlowAlpha = 0.24f

// A entrada dos gráficos: cada coluna e cada bloco começa um pouco depois do anterior.
private const val BarStaggerMs = 45
private const val BlockStaggerMs = 14
private const val GrowDelayMs = 120
private const val GrowTimelineMs = 2_400

/** Índices da entrada em cascata da Tracking: o prompt e os três cartões (pela ordem em que aparecem). */
object TrackingRevealItems {
    const val Prompt = 0
    const val FirstCard = 1
    const val Count = 4
}

/** A sessão (o processo) já mostrou a entrada da Tracking. */
object TrackingSession {
    @Volatile
    var revealed = false
}

/**
 * Tracking (Cyclo-2 2001:115; com o prompt, 2006:449): o registo do sono, da cafeína e do humor.
 *
 * - O sleep-evaluation-prompt fica no topo desde a hora de acordar de hoje até o sono estar
 *   registado (ver data/Tracking.kt): "Log sleep" abre o action-menu-log-sleep; o X esconde-o até a
 *   app voltar a abrir. Sai a encolher (os cartões sobem com ele, no mesmo movimento).
 * - Os três cartões (Restfulness, Caffeine intake, Mood) reordenam-se com long press e arrastar,
 *   como no Feed ([ReorderableColumn]); a ordem guarda-se ([onReorder]). Os gráficos não têm gestos.
 * - Na primeira vez da sessão, os cartões entram em cascata (como o Feed) e as colunas e os blocos
 *   crescem da base, um a um. Um valor que muda vai para o novo com uma mola.
 */
@Composable
fun TrackingScreen(
    now: LocalDateTime,
    routines: List<Routine>,
    dailyLog: DailyLog?,
    history: List<DailyLog>,
    order: List<TrackingSection>,
    promptDismissed: Boolean,
    promptGlow: () -> Color,
    reveal: StaggeredReveal,
    onShown: () -> Unit,
    bottomReserve: Dp,
    onLogSleep: () -> Unit,
    onDismissPrompt: () -> Unit,
    onReorder: (List<TrackingSection>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
    val summary = remember(history, dailyLog, today) { trackingSummary(history, dailyLog, today) }
    val showPrompt = showsSleepPrompt(routines, dailyLog, now, promptDismissed)
    val latestOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(Unit) {
        if (!TrackingSession.revealed) {
            latestOnShown()
            reveal.play()
        }
    }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .cycloVerticalScroll(scroll)
            // O espaço entre cartões vai dentro de cada um: tira-se o do último.
            .padding(top = TrackingContentTop, bottom = (bottomReserve - CycloDimens.Spacing).coerceAtLeast(0.dp))
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        SleepPrompt(
            visible = showPrompt,
            glowColor = promptGlow,
            onLogSleep = onLogSleep,
            onDismiss = onDismissPrompt,
            modifier = Modifier.staggeredReveal(reveal, TrackingRevealItems.Prompt),
        )
        ReorderableColumn(
            items = order,
            key = { it },
            onReorder = { reordered -> if (reordered != order) onReorder(reordered) },
            moveUpLabel = stringResource(R.string.reorder_move_up),
            moveDownLabel = stringResource(R.string.reorder_move_down),
            scrollState = scroll,
        ) { section, handle, surface ->
            val revealIndex = TrackingRevealItems.FirstCard + order.indexOf(section).coerceAtLeast(0)
            Column(modifier = Modifier.staggeredReveal(reveal, revealIndex)) {
                Box(modifier = handle) {
                    val grow = rememberChartGrowth(reveal, revealIndex)
                    when (section) {
                        TrackingSection.Restfulness -> RestfulnessCard(summary, grow, surface)
                        TrackingSection.Caffeine -> CaffeineCard(summary, grow, surface)
                        TrackingSection.Mood -> MoodCard(summary, grow, surface)
                    }
                }
                Spacer(Modifier.height(CycloDimens.Spacing))
            }
        }
    }
}

/**
 * O relógio da entrada dos gráficos de um cartão (ms desde que ele começa a aparecer). Na primeira
 * vez da sessão corre com a cascata do cartão; depois já começa no fim.
 */
@Composable
private fun rememberChartGrowth(reveal: StaggeredReveal, index: Int): State<Float> {
    val effects = LocalVisualEffects.current
    val clock = remember { Animatable(if (reveal.progress(index) >= 1f) GrowTimelineMs.toFloat() else 0f) }
    LaunchedEffect(Unit) {
        if (clock.value >= GrowTimelineMs) return@LaunchedEffect
        if (!effects.animationsEnabled) {
            clock.snapTo(GrowTimelineMs.toFloat())
            return@LaunchedEffect
        }
        snapshotFlow { reveal.progress(index) > 0f }.first { it }
        clock.animateTo(GrowTimelineMs.toFloat(), tween(GrowTimelineMs, easing = LinearEasing))
    }
    return clock.asState()
}


/** Quanto do elemento [order] já cresceu (0 a 1, com o ressalto da mola) ao fim de [clockMs]. */
private fun grown(clockMs: Float, order: Int, staggerMs: Int): Float {
    val t = (clockMs - GrowDelayMs - order * staggerMs) / 1000f
    return if (t <= 0f) 0f else springProgress(t, CycloSprings.ChartGrow)
}

// --- O prompt do sono ---------------------------------------------------------------------------

/**
 * sleep-evaluation-prompt (2006:416): 345 x 156, o vidro do Figma (#101011 em Luminosity, desfoque
 * de fundo 39) com o "fx blur type 01 (base)" por trás do topo; título, texto, "Log sleep" (o botão
 * roxo compacto) e o close-b. Entra a abrir e sai a encolher (a altura, a opacidade e um pouco a
 * escala), e os cartões de baixo acompanham, sem saltos.
 *
 * @param glowColor a base do brilho da hora atual (já animada quando a faixa horária muda); lê-se
 *                  só ao desenhar, por isso uma mudança de cor não recompõe o cartão.
 */
@Composable
private fun SleepPrompt(
    visible: Boolean,
    glowColor: () -> Color,
    onLogSleep: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val promptGlow = remember { PromptGlow() }
    val presence = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        when {
            !effects.animationsEnabled -> presence.animateTo(target, tween(150))
            visible -> presence.animateTo(1f, CycloSprings.Enter.float)
            else -> presence.animateTo(0f, CycloSprings.CardCollapse.float)
        }
    }
    if (!visible && presence.value <= 0f) return
    Box(
        modifier = modifier
            // Ocupa só a fração da altura que já abriu (os cartões de baixo sobem com ele).
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val p = presence.value.coerceIn(0f, 1f)
                val height = (placeable.height * if (effects.animationsEnabled) p else 1f).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val p = presence.value.coerceIn(0f, 1f)
                alpha = p
                if (effects.animationsEnabled) {
                    val s = lerp(0.96f, 1f, p)
                    scaleX = s
                    scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                }
            },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 156.dp)
                    .cycloCard(
                        shadowColor = null,
                        // O retângulo azul do Figma fica por baixo do vidro, desfocado por ele. O
                        // desfoque (no CPU) faz-se uma vez por tamanho, numa imagem: o cartão volta a
                        // desenhar-se a cada fotograma do scroll (lê o que tem por trás).
                        underFill = { promptGlow.draw(this, glowColor()) },
                    )
                    .padding(CardPadding),
            ) {
                Column {
                    BasicText(
                        text = stringResource(R.string.sleep_prompt_title),
                        style = CycloType.CardTitle,
                        modifier = Modifier
                            .padding(end = 39.dp + 15.dp)
                            .semantics { heading() },
                    )
                    Spacer(Modifier.height(9.dp))
                    BasicText(
                        text = stringResource(R.string.sleep_prompt_text),
                        style = CycloType.Body.copy(textAlign = TextAlign.Start),
                        modifier = Modifier.padding(end = 39.dp + 15.dp),
                    )
                    Spacer(Modifier.height(15.dp))
                    CycloPrimaryButton(
                        text = stringResource(R.string.sleep_prompt_log),
                        onClick = onLogSleep,
                        size = CycloButtonSize.Compact,
                    )
                }
                // close-b: o círculo de 39 dp no canto (a zona de toque de 48 dp sai 4,5 dp para fora).
                CycloCircleButton(
                    iconPath = CycloIconPaths.Close,
                    contentDescription = stringResource(R.string.sleep_prompt_close),
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.5.dp, y = (-4.5).dp),
                )
            }
            Spacer(Modifier.height(CycloDimens.Spacing))
        }
    }
}

/**
 * O "fx blur type 01 (base)" do prompt (315 x 33, a base do brilho a 24 %, desfocado pelo vidro do
 * cartão, 39). A forma desfocada (no CPU) desenha-se uma vez por tamanho do cartão numa imagem
 * branca e opaca, que serve de máscara; a cor entra só ao copiar a imagem (tint SrcIn), por isso
 * pode mudar a cada frame (a transição entre faixas horárias) sem voltar a desfocar nada.
 */
private class PromptGlow {
    private var image: ImageBitmap? = null
    private var forSize = androidx.compose.ui.geometry.Size.Zero
    private var tintColor = Color.Unspecified
    private var tint: ColorFilter? = null

    fun draw(scope: DrawScope, color: Color) = with(scope) {
        if (image == null || forSize != size) {
            forSize = size
            val w = size.width.roundToInt().coerceAtLeast(1)
            val h = size.height.roundToInt().coerceAtLeast(1)
            val bitmap = ImageBitmap(w, h)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                this, layoutDirection, androidx.compose.ui.graphics.Canvas(bitmap), androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
            ) {
                val glow = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(15.dp.toPx(), 0f, w - 15.dp.toPx(), 33.dp.toPx()))
                }
                drawBlurredPath(glow, Color.White, 39.dp.toPx())
            }
            image = bitmap
        }
        val glowColor = color.copy(alpha = color.alpha * PromptGlowAlpha)
        if (tint == null || glowColor != tintColor) {
            tintColor = glowColor
            tint = ColorFilter.tint(glowColor, BlendMode.SrcIn)
        }
        image?.let { drawImage(it, colorFilter = tint) }
    }
}

// --- Cartões ------------------------------------------------------------------------------------

/** O cartão da Tracking: #101011 em Luminosity, raio 15, sem sombra (Figma), título e o resto. */
@Composable
private fun TrackingCard(
    title: String,
    description: String,
    average: Pair<String, Color>?,
    titleToContent: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cycloCard(shadowColor = null)
            .padding(CardPadding)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp)) {
            BasicText(text = title, style = CycloType.CardTitle, modifier = Modifier.weight(1f))
            // "average": a cara da média do mês, a 1 dp do topo do título (Figma, Beta 2.2: 142,
            // 395 e 648 com os títulos a 141, 394 e 647).
            val (path, tint) = average ?: (null to Color.Transparent)
            val avgAlpha by animateFloatAsState(
                targetValue = if (average != null) 1f else 0f,
                animationSpec = CycloSprings.ChartChange.float,
                label = "average",
            )
            val avgColor by animateColorAsState(tint.takeIf { average != null } ?: CycloColors.MoodViolet, CycloSprings.ChartChange.spec(), label = "averageColor")
            var shownPath by remember { mutableStateOf(path) }
            if (path != null) shownPath = path
            Box(modifier = Modifier.padding(top = AverageTop).size(15.dp)) {
                shownPath?.let { p ->
                    com.gaebriu.cyclo.ui.components.CycloIcon(
                        path = p,
                        size = 15.dp,
                        tint = avgColor,
                        luminosity = false,
                        alpha = { avgAlpha },
                    )
                }
            }
        }
        Spacer(Modifier.height(titleToContent))
        Box(modifier = Modifier.clearAndSetSemantics { }) { content() }
    }
}

@Composable
private fun RestfulnessCard(summary: TrackingSummary, grow: State<Float>, modifier: Modifier) {
    val locale = rememberAppLocale()
    val labels = SleepQuality.entries.map { stringResource(it.label) }
    val notLogged = stringResource(R.string.tracking_not_logged)
    val description = buildString {
        append(stringResource(R.string.tracking_restfulness)).append(". ")
        append(stringResource(R.string.tracking_week_chart)).append(": ")
        append(summary.sleepWeek.joinToString(", ") { day ->
            day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale) + " " + (day.value?.let { labels[it.ordinal] } ?: notLogged)
        })
        summary.sleepMonthAverage?.let { append(". ").append(stringResource(R.string.tracking_month_chart)).append(", ").append(labels[it.ordinal]) }
    }
    TrackingCard(
        title = stringResource(R.string.tracking_restfulness),
        description = description,
        average = summary.sleepMonthAverage?.face(),
        titleToContent = TitleToChart,
        modifier = modifier,
    ) {
        WeekAndMonthChart(
            weekWidth = SleepWeekWidth,
            week = summary.sleepWeek.map { day -> day.date to day.value?.let { SleepBarHeights[it.ordinal] } },
            month = summary.sleepMonth.map { day -> day.value?.let { BlockFill(CycloColors.Accent, it.blockAlpha()) } },
            caffeineBlocks = false,
            grow = grow,
            legend = SleepQuality.entries.map { it.face() },
        )
    }
}

@Composable
private fun CaffeineCard(summary: TrackingSummary, grow: State<Float>, modifier: Modifier) {
    val locale = rememberAppLocale()
    val notLogged = stringResource(R.string.tracking_not_logged)
    val description = buildString {
        append(stringResource(R.string.tracking_caffeine)).append(". ")
        append(stringResource(R.string.tracking_week_chart)).append(": ")
        append(summary.caffeineWeek.joinToString(", ") { day ->
            day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale) + " " + (day.value?.toString() ?: notLogged)
        })
    }
    val max = summary.caffeineWeekMax
    TrackingCard(
        title = stringResource(R.string.tracking_caffeine),
        description = description,
        average = summary.caffeineMonthAverage?.let { caffeineFace(it) },
        titleToContent = TitleToChart,
        modifier = modifier,
    ) {
        WeekAndMonthChart(
            weekWidth = CaffeineWeekWidth,
            // As colunas são proporcionais ao máximo da semana (a linha de cima); 0 cafés = só a base.
            week = summary.caffeineWeek.map { day ->
                day.date to day.value?.takeIf { it > 0 && max > 0 }?.let { 143f * it / max }
            },
            month = summary.caffeineMonth.map { day -> day.value?.let { BlockFill(CycloColors.Accent, summary.caffeineBlockAlpha(it)) } },
            caffeineBlocks = true,
            grow = grow,
            // O máximo da semana e a cara da média da semana; sem cafés (ou sem dados), não aparecem.
            maxLabel = max.takeIf { it > 0 }?.toString(),
            maxFace = summary.caffeineWeekAverage?.takeIf { max > 0 }?.let { caffeineFace(it) },
        )
    }
}

@Composable
private fun MoodCard(summary: TrackingSummary, grow: State<Float>, modifier: Modifier) {
    val labels = listOf(
        stringResource(R.string.feed_feeling_level_1),
        stringResource(R.string.feed_feeling_level_2),
        stringResource(R.string.feed_feeling_level_3),
        stringResource(R.string.feed_feeling_level_4),
        stringResource(R.string.feed_feeling_level_5),
    )
    val notLogged = stringResource(R.string.tracking_not_logged)
    val description = buildString {
        append(stringResource(R.string.tracking_mood)).append(". ")
        append(stringResource(R.string.tracking_month_chart)).append(": ")
        append(summary.moodMonth.filter { it.value != null }.joinToString(", ") { "${it.date.dayOfMonth} ${labels[it.value!!]}" }.ifEmpty { notLogged })
        summary.moodMonthAverage?.let { append(". ").append(stringResource(R.string.tracking_average)).append(", ").append(labels[it]) }
    }
    TrackingCard(
        title = stringResource(R.string.tracking_mood),
        description = description,
        // average-mood (Cyclo-2 2032:48): a cara da média do mês, como a do Restfulness.
        average = summary.moodMonthAverage?.let(::moodFace),
        titleToContent = TitleToMoodGrid,
        modifier = modifier,
    ) {
        MoodGrid(days = summary.moodMonth, grow = grow)
    }
}

/** O preenchimento de um bloco: a cor e a opacidade. */
private data class BlockFill(val color: Color, val alpha: Float)

// --- Gráficos -----------------------------------------------------------------------------------

/**
 * Os sprites das sombras interiores (feitos uma vez por tamanho, ver [innerShadowSprite]):
 * - bloco: 0, 2, desfoque 9, preto a 15 % (e, na cafeína, 0, -4, desfoque 4, #4A3C99 a 63 %);
 * - coluna: 0, -6, desfoque 15, preto a 15 %, numa coluna alta que se corta em três (topo, meio
 *   esticado e fundo) para qualquer altura.
 */
private class ChartSprites(val block: ImageBitmap, val bar: ImageBitmap, val barSlice: Int)

private fun androidx.compose.ui.unit.Density.chartSprites(scale: Float, caffeine: Boolean): ChartSprites {
    val cell = (Cell * scale).dp.toPx().roundToInt().coerceAtLeast(1)
    val radius = (6f * scale).dp.toPx()
    val blockShadows = buildList {
        add(InnerShadowSpec(Color.Black.copy(alpha = 0.15f), Offset(0f, 2.dp.toPx() * scale), 9.dp.toPx() * scale))
        if (caffeine) add(InnerShadowSpec(CycloColors.Accent.copy(alpha = 0.63f), Offset(0f, -4.dp.toPx() * scale), 4.dp.toPx() * scale))
    }
    val block = innerShadowSprite(cell, cell, radius, blockShadows, this)
    val barHeight = (143f * scale).dp.toPx().roundToInt().coerceAtLeast(1)
    val bar = innerShadowSprite(
        cell,
        barHeight,
        radius,
        listOf(InnerShadowSpec(Color.Black.copy(alpha = 0.15f), Offset(0f, -6.dp.toPx() * scale), 15.dp.toPx() * scale)),
        this,
    )
    return ChartSprites(block, bar, barSlice = (24f * scale).dp.toPx().roundToInt())
}

/** A sombra de uma coluna de [height] px a partir do sprite alto: topo e fundo inteiros, o meio esticado. */
private fun DrawScope.drawBarShadow(sprite: ImageBitmap, slice: Int, left: Int, top: Int, height: Int) {
    if (height <= 0) return
    val w = sprite.width
    val h = sprite.height
    val s = minOf(slice, h / 2)
    if (height <= 2 * s) {
        val upper = height / 2
        val lower = height - upper
        drawImage(sprite, IntOffset.Zero, IntSize(w, upper), IntOffset(left, top), IntSize(w, upper))
        drawImage(sprite, IntOffset(0, h - lower), IntSize(w, lower), IntOffset(left, top + upper), IntSize(w, lower))
    } else {
        drawImage(sprite, IntOffset.Zero, IntSize(w, s), IntOffset(left, top), IntSize(w, s))
        drawImage(sprite, IntOffset(0, s), IntSize(w, h - 2 * s), IntOffset(left, top + s), IntSize(w, height - 2 * s))
        drawImage(sprite, IntOffset(0, h - s), IntSize(w, s), IntOffset(left, top + height - s), IntSize(w, s))
    }
}

/**
 * O gráfico semanal (colunas sobre os últimos 7 dias, a linha de cima, as bases e as iniciais dos
 * dias) e a grelha mensal (blocos de 15 dp, 4 por linha, do dia 1 ao último), lado a lado.
 *
 * Desenhado para os 315 dp do Figma: num ecrã mais estreito, tudo encolhe junto (colunas, blocos e
 * espaços, ver [FigmaContent]); num mais largo, o semanal fica à esquerda e a grelha à direita. O
 * texto segue o tamanho de letra do sistema (a área cresce com ele).
 *
 * @param week a altura de cada coluna em dp do Figma (null = só a base).
 * @param month o preenchimento de cada dia do mês (null = por registar, cinzento).
 * @param legend as caras da legenda (Restfulness), uma por nível.
 * @param maxLabel o número do máximo da semana (cafeína) e [maxFace] a cara ao lado.
 */
@Composable
private fun WeekAndMonthChart(
    weekWidth: Float,
    week: List<Pair<java.time.LocalDate, Float?>>,
    month: List<BlockFill?>,
    caffeineBlocks: Boolean,
    grow: State<Float>,
    legend: List<Pair<String, Color>> = emptyList(),
    maxLabel: String? = null,
    maxFace: Pair<String, Color>? = null,
) {
    val effects = LocalVisualEffects.current
    val locale = rememberAppLocale()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val dayStyle = CycloType.CardTitle.copy(textAlign = TextAlign.Center)
    val maxText: TextLayoutResult? = remember(maxLabel, density, measurer) {
        maxLabel?.let { measurer.measure(it, CycloType.CardTitle.copy(color = MaxLabelColor)) }
    }
    val legendPaths = remember(legend.map { it.first }) { legend.map { PathParser().parsePathString(it.first).toPath() } }
    val maxFacePath = remember(maxFace?.first) { maxFace?.first?.let { PathParser().parsePathString(it).toPath() } }

    // As colunas e os blocos vão para os valores novos com uma mola (sem animações: logo).
    fun <T> spec() = if (effects.animationsEnabled) CycloSprings.ChartChange.spec<T>() else snap()
    val barHeights = week.mapIndexed { i, (_, h) ->
        animateFloatAsState(h ?: 0f, spec(), label = "bar$i")
    }
    val blockAlphas = month.mapIndexed { i, fill ->
        animateFloatAsState(fill?.alpha ?: 0f, spec(), label = "block$i")
    }
    val blockLogged = month.mapIndexed { i, fill ->
        animateFloatAsState(if (fill != null) 1f else 0f, spec(), label = "logged$i")
    }
    val maxAlpha by animateFloatAsState(if (maxText != null) 1f else 0f, spec(), label = "max")

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val available = maxWidth.value
        val scale = (available / FigmaContent).coerceAtMost(1f)
        // As iniciais dos dias seguem o tamanho de letra do sistema, mas nunca ficam mais largas do que
        // a distância entre colunas (com letra grande num ecrã estreito colavam-se: "DSTQQSS"). São
        // um eixo: o TalkBack lê os valores do cartão inteiro.
        val pitchPx = with(density) { (CellPitch * scale).dp.toPx() }
        val dayLabels: List<TextLayoutResult> = remember(week.map { it.first.dayOfWeek }, locale, density, measurer, pitchPx) {
            val names = week.map { (date, _) -> date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, locale) }
            val natural = names.map { measurer.measure(it, dayStyle) }
            val widest = natural.maxOf { it.size.width }.toFloat()
            val room = pitchPx * DayLabelMaxShare
            if (widest <= room) {
                natural
            } else {
                val fitted = dayStyle.copy(fontSize = dayStyle.fontSize * (room / widest))
                names.map { measurer.measure(it, fitted) }
            }
        }
        val labelHeight = with(density) { (dayLabels.maxOfOrNull { it.size.height } ?: 0).toDp() }
        val chartHeight = maxOf((LabelTop * scale).dp + labelHeight, (GridHeight * scale).dp)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                // Camada própria: o cartão redesenha-se no scroll (o vidro lê o que tem por trás), o gráfico não.
                .graphicsLayer()
                .drawWithCache {
                    val sprites = chartSprites(scale, caffeineBlocks)
                    val cellPx = (Cell * scale).dp.toPx()
                    val blockPath = smoothRoundRectPath(cellPx, cellPx, (6f * scale).dp.toPx())
                    onDrawBehind {
                        val u = scale.dp.toPx() // px por dp do Figma
                        val blend = effects.luminosity
                        val clock = grow.value
                        // A linha de cima (2 dp) por cima do semanal.
                        drawRoundRect(
                            color = ChartTopLineColor,
                            size = androidx.compose.ui.geometry.Size(weekWidth * u, 2f * u),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u),
                        )
                        // As colunas, as bases e as iniciais dos dias.
                        week.forEachIndexed { i, _ ->
                            val x = i * CellPitch * u
                            val left = x.roundToInt()
                            val g = grown(clock, i, BarStaggerMs)
                            drawRoundRect(
                                color = CycloColors.Accent,
                                topLeft = Offset(x, BaseTop * u),
                                size = androidx.compose.ui.geometry.Size(cellPx, 2f * u),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * u),
                                alpha = g.coerceIn(0f, 1f),
                            )
                            val h = barHeights[i].value * u * g
                            if (h > 0.5f) {
                                val top = BarBottom * u - h
                                drawPath(
                                    path = smoothRoundRectPath(cellPx, h, minOf(6f * u, h / 2f)).apply { translate(Offset(x, top)) },
                                    color = CycloColors.Accent,
                                )
                                drawBarShadow(sprites.bar, sprites.barSlice, left, top.roundToInt(), h.roundToInt())
                            }
                            val label = dayLabels[i]
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(x + (cellPx - label.size.width) / 2f, LabelTop * u),
                                blendMode = blend,
                            )
                        }
                        // A legenda (Restfulness): uma cara por nível, à altura do topo da coluna dele.
                        legendPaths.forEachIndexed { i, path ->
                            drawFace(path, legend[i].second, LegendX * u, LegendTops[i] * u, u, alpha = grown(clock, i, BarStaggerMs).coerceIn(0f, 1f))
                        }
                        // O máximo da semana e a cara da média (cafeína), na linha das colunas mais altas.
                        if (maxText != null && maxAlpha > 0f) {
                            drawText(maxText, topLeft = Offset(LegendX * u, BarTop * u), alpha = maxAlpha, blendMode = blend)
                        }
                        if (maxFacePath != null && maxFace != null) {
                            // A cara fica a 6 dp do número (Figma: "3" em 207, cara em 223), mesmo com letra grande.
                            val faceX = maxOf(CaffeineFaceX * u, LegendX * u + (maxText?.size?.width ?: 0) + 6f * u)
                            drawFace(maxFacePath, maxFace.second, faceX, (BarTop + 1f) * u, u, alpha = maxAlpha)
                        }
                        // A grelha do mês: encostada à direita.
                        val gridLeft = size.width - GridWidth * u
                        month.forEachIndexed { day, _ ->
                            val col = day % 4
                            val row = day / 4
                            val left = gridLeft + col * CellPitch * u
                            val top = row * CellPitch * u
                            val g = grown(clock, day, BlockStaggerMs).coerceIn(0f, 1.2f)
                            drawBlock(
                                path = blockPath,
                                sprite = sprites.block,
                                left = left,
                                top = top,
                                grow = g,
                                logged = blockLogged[day].value,
                                alpha = blockAlphas[day].value,
                                color = CycloColors.Accent,
                                blend = blend,
                            )
                        }
                    }
                },
        )
    }
}

/**
 * Um bloco de um dia: o cinzento "por registar" (#A3A2A7 a 15 % em Luminosity) e, por cima, a cor
 * do registo com a sua opacidade e as sombras interiores; [logged] passa de um para o outro.
 * A crescer ([grow] de 0 a 1), sai do centro e aparece.
 */
private fun DrawScope.drawBlock(
    path: Path,
    sprite: ImageBitmap,
    left: Float,
    top: Float,
    grow: Float,
    logged: Float,
    alpha: Float,
    color: Color,
    blend: BlendMode,
) {
    if (grow <= 0f) return
    val cell = sprite.width.toFloat()
    val s = lerp(0.6f, 1f, grow.coerceAtMost(1.2f))
    val fade = grow.coerceIn(0f, 1f)
    withTransform({
        translate(left, top)
        scale(s, s, Offset(cell / 2f, cell / 2f))
    }) {
        if (logged < 1f) drawPath(path, NotLoggedColor, alpha = fade * (1f - logged), blendMode = blend)
        if (logged > 0f) {
            drawPath(path, color, alpha = fade * logged * alpha)
            drawImage(sprite, alpha = fade * logged)
        }
    }
}

/** Uma cara de 15 dp (ícone do Figma) em ([x], [y]) dp do Figma × [u]. */
private fun DrawScope.drawFace(path: Path, color: Color, x: Float, y: Float, u: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(u, u, Offset.Zero)
    }) {
        drawPath(path, color, alpha = alpha)
    }
}

/**
 * mood-graph-montly (2006:237): 13 blocos por linha, do dia 1 ao último, centrados; cada um com a
 * cor do nível do humor desse dia ([moodColor]) e a sombra interior do Figma; por registar, cinzento.
 */
@Composable
private fun MoodGrid(days: List<TrackedDay<Int>>, grow: State<Float>) {
    val effects = LocalVisualEffects.current
    fun <T> spec() = if (effects.animationsEnabled) CycloSprings.ChartChange.spec<T>() else snap()
    val colors = days.mapIndexed { i, day ->
        animateColorAsState(day.value?.let(::moodColor) ?: CycloColors.MoodOkay, spec(), label = "mood$i")
    }
    val logged = days.mapIndexed { i, day ->
        animateFloatAsState(if (day.value != null) 1f else 0f, spec(), label = "moodLogged$i")
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val scale = (maxWidth.value / FigmaContent).coerceAtMost(1f)
        val rows = (days.size + MoodColumns - 1) / MoodColumns
        val height = ((rows - 1) * CellPitch + Cell) * scale
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .graphicsLayer()
                .drawWithCache {
                    val cellPx = (Cell * scale).dp.toPx()
                    val sprite = innerShadowSprite(
                        cellPx.roundToInt(),
                        cellPx.roundToInt(),
                        (6f * scale).dp.toPx(),
                        // Os blocos do Mood (2006:238, Beta 2.2): 0, 2, 9 preto a 15 % e 0, -4, 4 #FCFCFE a 63 %.
                        listOf(
                            InnerShadowSpec(Color.Black.copy(alpha = 0.15f), Offset(0f, 2.dp.toPx() * scale), 9.dp.toPx() * scale),
                            InnerShadowSpec(CycloColors.TextPrimary.copy(alpha = 0.63f), Offset(0f, -4.dp.toPx() * scale), 4.dp.toPx() * scale),
                        ),
                        this,
                    )
                    val path = smoothRoundRectPath(cellPx, cellPx, (6f * scale).dp.toPx())
                    onDrawBehind {
                        val u = scale.dp.toPx()
                        val gridLeft = (size.width - MoodGridWidth * u) / 2f
                        val clock = grow.value
                        days.forEachIndexed { i, _ ->
                            drawBlock(
                                path = path,
                                sprite = sprite,
                                left = gridLeft + (i % MoodColumns) * CellPitch * u,
                                top = (i / MoodColumns) * CellPitch * u,
                                grow = grown(clock, i, BlockStaggerMs).coerceIn(0f, 1.2f),
                                logged = logged[i].value,
                                alpha = 1f,
                                color = colors[i].value,
                                blend = effects.luminosity,
                            )
                        }
                    }
                },
        )
    }
}

