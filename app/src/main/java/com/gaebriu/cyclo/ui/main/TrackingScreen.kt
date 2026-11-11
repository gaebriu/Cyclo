package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.DailyLog
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.data.TrackingSection
import com.gaebriu.cyclo.data.TrackingSummary
import com.gaebriu.cyclo.data.cycloDay
import com.gaebriu.cyclo.data.resolveDayLogs
import com.gaebriu.cyclo.data.showsSleepPrompt
import com.gaebriu.cyclo.data.trackingSummary
import com.gaebriu.cyclo.ui.components.CycloButtonSize
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.ReorderableColumn
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import java.time.LocalDateTime
import kotlin.math.roundToInt

// Medidas do Figma (Tracking, Cyclo-2 2001:115 e 2006:449; frame de 393 x 852).
/** O conteúdo começa a 126 dp do topo (o primeiro cartão). */
private val TrackingContentTop = 126.dp
private val CardPadding = 15.dp

/** Do título (18 dp de altura) à linha de cima dos gráficos. */
private val TitleToChart = 7.dp

/** A cara da média ("average", "average-mood") desce isto do topo do título. */
private val AverageTop = 1.dp

/** Do título à grelha do Mood. */
private val TitleToMoodGrid = 15.dp

/**
 * A opacidade do "fx blur type 01 (base)" do prompt (2006:417, 315 x 33): a cor é a base do brilho
 * da hora atual ([skyColorsAt]; #5FA2DB das 11:30 às 15:30, a do Figma) a 24 %.
 */
private const val PromptGlowAlpha = 0.24f

/** Índices da entrada em cascata da Tracking: o prompt e os quatro cartões (pela ordem em que aparecem). */
object TrackingRevealItems {
    const val Prompt = 0
    const val FirstCard = 1
    const val Count = FirstCard + 4
}

/** A sessão (o processo) já mostrou a entrada da Tracking. */
object TrackingSession {
    @Volatile
    var revealed = false
}

/**
 * Tracking (Cyclo-2 2001:115; com o prompt, 2006:449): o registo do sono, da cafeína, da água e do
 * humor.
 *
 * - O sleep-evaluation-prompt fica no topo desde a hora de acordar de hoje até o sono estar
 *   registado (ver data/Tracking.kt): "Log sleep" abre o action-menu-log-sleep; o X esconde-o até a
 *   app voltar a abrir. Sai a encolher (os cartões sobem com ele, no mesmo movimento).
 * - Os quatro cartões (Restfulness, Caffeine intake, Water intake, Mood) reordenam-se com long
 *   press e arrastar, como no Feed ([ReorderableColumn]); a ordem guarda-se ([onReorder]). Os
 *   gráficos não têm gestos. A água (beta-3.0) vem, de omissão, por baixo da cafeína.
 * - Na primeira vez da sessão, os cartões entram em cascata (como o Feed) e as colunas e os blocos
 *   crescem da base, um a um. Um valor que muda vai para o novo com uma mola.
 * - Com o Unified view ligado esta página não existe: tudo isto vai para o Feed.
 */
@Composable
fun TrackingScreen(
    now: LocalDateTime,
    routines: List<Routine>,
    dailyLog: DailyLog?,
    history: List<DailyLog>,
    age: Int?,
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
    isCurrentPage: Boolean = true,
) {
    // O dia da Cyclo (beta-3.0): até à hora de acordar ainda é o dia anterior.
    val today = remember(routines, now) { cycloDay(routines, now) }
    // Beta 2.4: os hápticos dos gráficos só tocam com a Tracking escolhida (não ao espreitá-la).
    // Um só lambda (lembrado): os gráficos não se recompõem por causa dele.
    val current by rememberUpdatedState(isCurrentPage)
    val active = remember { { current } }
    // O registo da noite de agora já arrumado (resolveDayLogs), como o Feed.
    val logs = remember(dailyLog, history, today) { resolveDayLogs(dailyLog, history, today) }
    val summary = remember(logs, today, age) { trackingSummary(logs.history, logs.current, today, age) }
    val showPrompt = showsSleepPrompt(routines, logs.current, now, promptDismissed)
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
            bottomGap = CycloDimens.Spacing,
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
            // A camada da entrada alarga-se à volta do cartão, para a sombra dele entrar com ele.
            Column(
                modifier = Modifier
                    .bleed(CardShadowBleed)
                    .staggeredReveal(reveal, revealIndex)
                    .padding(CardShadowBleed),
            ) {
                Box(modifier = handle) {
                    val grow = rememberChartGrowth(reveal, revealIndex)
                    TrackingSectionCard(section, summary, grow, active, surface)
                }
                Spacer(Modifier.height(CycloDimens.Spacing))
            }
        }
    }
}

/** O cartão da Tracking de [section] (o título, a média e o gráfico da geometria da Tracking). */
@Composable
internal fun TrackingSectionCard(
    section: TrackingSection,
    summary: TrackingSummary,
    grow: androidx.compose.runtime.State<Float>,
    active: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val geometry = ChartGeometry.Tracking
    when (section) {
        TrackingSection.Restfulness -> TrackingCard(
            title = stringResource(R.string.tracking_restfulness),
            description = restfulnessDescription(summary),
            average = summary.sleepMonthAverage?.face(),
            titleToContent = TitleToChart,
            modifier = modifier,
        ) { RestfulnessChart(summary, grow, active, geometry) }
        TrackingSection.Caffeine -> TrackingCard(
            title = stringResource(R.string.tracking_caffeine),
            description = caffeineDescription(summary),
            average = summary.caffeineMonthAverage?.let { caffeineFace(it) },
            titleToContent = TitleToChart,
            modifier = modifier,
        ) { CaffeineChart(summary, grow, active, geometry) }
        TrackingSection.Water -> TrackingCard(
            title = stringResource(R.string.tracking_water),
            description = waterDescription(summary),
            average = summary.waterMonthAverage?.let(::moodFace),
            titleToContent = TitleToChart,
            modifier = modifier,
        ) { WaterChart(summary, grow, active, geometry) }
        // average-mood (Cyclo-2 2032:48): a cara da média do mês, como a do Restfulness.
        TrackingSection.Mood -> TrackingCard(
            title = stringResource(R.string.tracking_mood),
            description = moodDescription(summary),
            average = summary.moodMonthAverage?.let(::moodFace),
            titleToContent = TitleToMoodGrid,
            modifier = modifier,
        ) { MoodChart(summary, grow, geometry) }
    }
}

// --- O prompt do sono ---------------------------------------------------------------------------

/**
 * sleep-evaluation-prompt (2006:416): 345 x 156, o vidro do Figma (#101011 em Luminosity) com a
 * sombra dos cartões (0, 4, desfoque 39, preto a 24 %; beta-3.0) e o "fx blur type 01 (base)" por
 * trás do topo; título, texto, "Log sleep" (o botão roxo compacto) e o close-b. Entra a abrir e sai
 * a encolher (a altura, a opacidade e um pouco a escala), e o que está por baixo acompanha, sem
 * saltos. Também é o log-sleep-card do Feed no Unified view.
 *
 * @param glowColor a base do brilho da hora atual (já animada quando a faixa horária muda); lê-se
 *                  só ao desenhar, por isso uma mudança de cor não recompõe o cartão.
 * @param topGap, bottomGap os espaços antes e depois do cartão: abrem e fecham com ele.
 */
@Composable
internal fun SleepPrompt(
    visible: Boolean,
    glowColor: () -> Color,
    onLogSleep: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    topGap: Dp = 0.dp,
    bottomGap: Dp = 0.dp,
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
            // Ocupa só a fração da altura que já abriu (o que está por baixo sobe com ele).
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val p = presence.value.coerceIn(0f, 1f)
                val height = (placeable.height * if (effects.animationsEnabled) p else 1f).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            // A camada (opacidade a meio) desenha fora do ecrã: alarga-se para a sombra caber.
            .bleed(CardShadowBleed)
            .graphicsLayer {
                val p = presence.value.coerceIn(0f, 1f)
                alpha = p
                if (effects.animationsEnabled) {
                    val s = lerp(0.96f, 1f, p)
                    scaleX = s
                    scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                }
            }
            .padding(CardShadowBleed),
    ) {
        Column {
            Spacer(Modifier.height(topGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 156.dp)
                    .cycloCard(
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
            Spacer(Modifier.height(bottomGap))
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

/**
 * O cartão da Tracking: #101011 em Luminosity, raio 15, a sombra dos cartões (0, 4, desfoque 39,
 * preto a 24 %; Cyclo-2 2003:571, beta-3.0), o título, a cara da média e o resto. Também é o
 * Restfulness do Feed no Unified view quando não há rotina para o wake-time-container.
 */
@Composable
internal fun TrackingCard(
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
            .cycloCard()
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
            if (path != null && path != shownPath) shownPath = path
            Box(modifier = Modifier.padding(top = AverageTop).size(15.dp)) {
                shownPath?.let { p ->
                    CycloIcon(
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
