package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.Hydration
import com.gaebriu.cyclo.data.MinBlockAlpha
import com.gaebriu.cyclo.data.SleepQuality
import com.gaebriu.cyclo.data.TrackedDay
import com.gaebriu.cyclo.data.TrackingSummary
import com.gaebriu.cyclo.data.blockAlpha
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import com.gaebriu.cyclo.ui.effects.InnerShadowSpec
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.innerShadowSprite
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.i18n.rememberAppLocale
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloSpring
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.springProgress
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.roundToInt

/*
 * Os gráficos da Tracking (Cyclo-2 2001:115) e as versões deles dentro dos cartões do Feed no
 * Unified view (beta-3.0, Cyclo-2 2066:259): o mesmo desenho e as mesmas contas, com a geometria de
 * cada sítio ([ChartGeometry]).
 */

/** A largura do conteúdo de um cartão no Figma (345 - 2 x 15): os gráficos são desenhados para ela. */
internal const val FigmaContent = 315f

// Gráficos (dp do Figma, a partir do topo e da esquerda da área do gráfico).
private const val Cell = 15f
private const val CellPitch = 24f
private const val GridWidth = 87f
private const val GridHeight = 183f
private const val SleepWeekWidth = 183f
private const val CaffeineWeekWidth = 199f
private const val LegendX = 168f
private const val CaffeineFaceX = 184f
private const val FaceSize = 15f
private const val MoodColumns = 13

/** O passo horizontal dos blocos do Mood (2006:237, release-2.1: 10 dp entre blocos; as linhas mantêm o [CellPitch] 24). */
private const val MoodColumnPitch = 25f
private const val MoodGridWidth = 315f

/**
 * Onde fica cada parte de um gráfico semanal e mensal.
 *
 * @property barBottom o fundo das colunas (o topo da mais alta é [barBottom] - [maxBar]).
 * @property levelHeights a altura das colunas por nível (Restfulness e água: o melhor primeiro).
 * @property legendTops o topo das caras da legenda, por nível.
 * @property topLine a linha de cima por cima do semanal (a Tracking).
 * @property gridTop o topo da grelha do mês.
 * @property inlineAverage a cara da média vai no próprio gráfico, à direita, ligada à legenda por
 *           uma linha tracejada (o Unified view, onde o gráfico não tem título).
 * @property moodGridTop o topo da grelha do Mood.
 */
@androidx.compose.runtime.Immutable
internal class ChartGeometry(
    val barBottom: Float,
    val baseTop: Float,
    val labelTop: Float,
    val maxBar: Float,
    val levelHeights: FloatArray,
    val legendTops: FloatArray,
    val topLine: Boolean,
    val gridTop: Float,
    val inlineAverage: Boolean,
    val moodGridTop: Float,
) {
    val barTop: Float get() = barBottom - maxBar

    companion object {
        /**
         * Os cartões da Tracking (2003:570): colunas de 143 dp (Really well) a 22 (Very poor), com as
         * caras da legenda à altura do topo de cada uma; a linha de cima de 2 dp; a grelha ao lado.
         */
        val Tracking = ChartGeometry(
            barBottom = 151f,
            baseTop = 157f,
            labelTop = 165f,
            maxBar = 143f,
            levelHeights = floatArrayOf(143f, 112f, 82f, 52f, 22f),
            legendTops = floatArrayOf(9f, 39f, 69f, 99f, 129f),
            topLine = true,
            gridTop = 0f,
            inlineAverage = false,
            moodGridTop = 0f,
        )

        /**
         * O Unified view (sleep-quality-tracker 2066:422, dentro do wake-time-container, 315 x 215):
         * colunas até 183 dp (183, 143, 105, 67 e 28, do melhor ao pior nível), a base a 189 e as
         * iniciais a 197; a legenda a 0, 30, 60, 90 e 120; sem a linha de cima; a cara da média à
         * direita, ligada à legenda por uma linha tracejada (2066:622); a grelha a 32, para acabar
         * no fundo das iniciais. O Mood segue o mesmo padrão: a média e a linha em cima, a grelha
         * 15 dp abaixo delas.
         */
        val Unified = ChartGeometry(
            barBottom = 183f,
            baseTop = 189f,
            labelTop = 197f,
            maxBar = 183f,
            levelHeights = floatArrayOf(183f, 143f, 105f, 67f, 28f),
            legendTops = floatArrayOf(0f, 30f, 60f, 90f, 120f),
            topLine = false,
            gridTop = 32f,
            inlineAverage = true,
            moodGridTop = 30f,
        )
    }
}

/** O cinzento de um dia por registar: #A3A2A7 a 15 % em Luminosity. */
private val NotLoggedColor = CycloColors.TextSecondary.copy(alpha = 0.15f)

/** A linha de cima dos gráficos (e a tracejada do Unified view): #A3A2A7 a 15 %. */
private val ChartLineColor = CycloColors.TextSecondary.copy(alpha = 0.15f)

/** As iniciais dos dias ocupam no máximo isto da distância entre colunas. */
private const val DayLabelMaxShare = 0.8f

/** O "3" do máximo da semana: #A3A2A7 a 63 % em Luminosity. */
private val MaxLabelColor = CycloColors.TextSecondary.copy(alpha = 0.63f)

// A entrada dos gráficos: cada coluna e cada bloco começa um pouco depois do anterior.
private const val BarStaggerMs = 45
private const val BlockStaggerMs = 14
private const val GrowDelayMs = 120
private const val GrowTimelineMs = 2_400

/**
 * O relógio da entrada dos gráficos de um bloco (ms desde que ele começa a aparecer). Na primeira
 * vez da sessão corre com a cascata do bloco ([reveal], [index]); depois já começa no fim.
 */
@Composable
internal fun rememberChartGrowth(reveal: StaggeredReveal, index: Int): State<Float> {
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

/**
 * Os hápticos de um gráfico semanal (Beta 2.4), suaves como o movimento:
 * - na entrada, um tique muito leve por coluna com valor, no momento em que ela chega ao topo (ao
 *   ritmo da cascata, mais forte nas colunas mais altas), numa só composição; as colunas sem valor
 *   (só a base) não tocam. Só com a página escolhida ([active]): se ela só estava a ser espreitada,
 *   tocam apenas as colunas que ainda não chegaram quando passa a ser a escolhida;
 * - um valor que muda depois (ex.: o sono de hoje registado): um tique quando a coluna chega.
 * Sem animações (os gráficos aparecem logo), nada.
 */
@Composable
private fun ChartHaptics(heights: List<Float?>, grow: State<Float>, active: () -> Boolean) {
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    val latestActive by rememberUpdatedState(active)
    LaunchedEffect(Unit) {
        if (!effects.animationsEnabled || grow.value >= GrowTimelineMs) return@LaunchedEffect
        snapshotFlow { grow.value > 0f && latestActive() }.first { it }
        val elapsed = grow.value
        val tallest = heights.maxOfOrNull { it ?: 0f } ?: 0f
        if (tallest <= 0f) return@LaunchedEffect
        val ticks = heights.mapIndexedNotNull { i, h ->
            if (h == null || h <= 0f) return@mapIndexedNotNull null
            val at = (GrowDelayMs + i * BarStaggerMs + GrowArrivalMs - elapsed).roundToInt()
            if (at < 0) null else at to (0.5f + 0.5f * h / tallest)
        }
        CycloHaptics.cascade(view, ticks.map { it.first }, ticks.map { it.second })
    }
    var previous by remember { mutableStateOf(heights) }
    LaunchedEffect(heights) {
        val before = previous
        previous = heights
        if (before == heights || !effects.animationsEnabled || grow.value < GrowTimelineMs || !latestActive()) return@LaunchedEffect
        val grew = heights.indices.any { i -> (heights[i] ?: 0f) > (before.getOrNull(i) ?: 0f) }
        if (grew) CycloHaptics.cascade(view, listOf(ChangeArrivalMs), listOf(0.8f))
    }
}

/** Quanto tempo (ms) uma mola leva a chegar (98 % do caminho) ao destino, a partir do repouso. */
private fun arrivalMs(spring: CycloSpring): Int {
    var t = 0
    while (t < 2_000 && springProgress(t / 1000f, spring) < 0.98f) t++
    return t
}

private val GrowArrivalMs: Int by lazy { arrivalMs(CycloSprings.ChartGrow) }
private val ChangeArrivalMs: Int by lazy { arrivalMs(CycloSprings.ChartChange) }

/** Quanto do elemento [order] já cresceu (0 a 1, com o ressalto da mola) ao fim de [clockMs]. */
private fun grown(clockMs: Float, order: Int, staggerMs: Int): Float {
    val t = (clockMs - GrowDelayMs - order * staggerMs) / 1000f
    return if (t <= 0f) 0f else springProgress(t, CycloSprings.ChartGrow)
}

/** O preenchimento de um bloco: a cor e a opacidade. */
private class BlockFill(val color: Color, val alpha: Float)

/** Opacidade de um bloco da água no mês: o melhor nível a 100 %, o pior a 9 %, como o Restfulness. */
private fun waterBlockAlpha(level: Int): Float = 1f - level.coerceIn(0, Hydration.Levels - 1) * (1f - MinBlockAlpha) / (Hydration.Levels - 1)

// --- Os quatro gráficos ------------------------------------------------------------------------

/** O Restfulness: como dormiu em cada um dos últimos 7 dias, e o mês. */
@Composable
internal fun RestfulnessChart(summary: TrackingSummary, grow: State<Float>, active: () -> Boolean, geometry: ChartGeometry) {
    WeekAndMonthChart(
        geometry = geometry,
        weekWidth = SleepWeekWidth,
        week = summary.sleepWeek.map { day -> day.date to day.value?.let { geometry.levelHeights[it.ordinal] / geometry.maxBar } },
        month = summary.sleepMonth.map { day -> day.value?.let { BlockFill(CycloColors.Accent, it.blockAlpha()) } },
        grow = grow,
        active = active,
        legend = SleepQuality.entries.map { it.face() },
        average = summary.sleepMonthAverage?.face(),
    )
}

/** A cafeína: os cafés de cada um dos últimos 7 dias (proporcionais ao máximo da semana), e o mês. */
@Composable
internal fun CaffeineChart(summary: TrackingSummary, grow: State<Float>, active: () -> Boolean, geometry: ChartGeometry) {
    val max = summary.caffeineWeekMax
    WeekAndMonthChart(
        geometry = geometry,
        weekWidth = CaffeineWeekWidth,
        // As colunas são proporcionais ao máximo da semana (a linha de cima); 0 cafés = só a base.
        week = summary.caffeineWeek.map { day -> day.date to day.value?.takeIf { it > 0 && max > 0 }?.let { it.toFloat() / max } },
        month = summary.caffeineMonth.map { day -> day.value?.let { BlockFill(CycloColors.Accent, summary.caffeineBlockAlpha(it)) } },
        grow = grow,
        active = active,
        // O máximo da semana e a cara da média da semana; sem cafés (ou sem dados), não aparecem.
        maxLabel = max.takeIf { it > 0 }?.toString(),
        maxFace = summary.caffeineWeekAverage?.takeIf { max > 0 }?.let { caffeineFace(it) },
        average = summary.caffeineMonthAverage?.let { caffeineFace(it) },
    )
}

/**
 * A água (beta-3.0), com o comportamento do Restfulness: cada coluna e cada bloco mostram o nível da
 * avaliação do dia ([Hydration.level], que piora com pouca E com demasiada água), não os mL; a
 * legenda tem as cinco caras, a melhor em cima.
 */
@Composable
internal fun WaterChart(summary: TrackingSummary, grow: State<Float>, active: () -> Boolean, geometry: ChartGeometry) {
    WeekAndMonthChart(
        geometry = geometry,
        weekWidth = SleepWeekWidth,
        week = summary.waterWeek.map { day -> day.date to day.value?.let { geometry.levelHeights[it] / geometry.maxBar } },
        month = summary.waterMonth.map { day -> day.value?.let { BlockFill(CycloColors.Accent, waterBlockAlpha(it)) } },
        grow = grow,
        active = active,
        legend = (0 until Hydration.Levels).map(::moodFace),
        average = summary.waterMonthAverage?.let(::moodFace),
    )
}

/** O Mood: a grelha do mês com a cor do humor de cada dia. */
@Composable
internal fun MoodChart(summary: TrackingSummary, grow: State<Float>, geometry: ChartGeometry) {
    MoodGrid(
        days = summary.moodMonth,
        grow = grow,
        geometry = geometry,
        average = summary.moodMonthAverage?.let(::moodFace),
    )
}

// --- O que o TalkBack lê ------------------------------------------------------------------------

@Composable
internal fun restfulnessDescription(summary: TrackingSummary): String {
    val locale = rememberAppLocale()
    val labels = SleepQuality.entries.map { stringResource(it.label) }
    val notLogged = stringResource(R.string.tracking_not_logged)
    val week = stringResource(R.string.tracking_week_chart)
    val month = stringResource(R.string.tracking_month_chart)
    val title = stringResource(R.string.tracking_restfulness)
    return buildString {
        append(title).append(". ").append(week).append(": ")
        append(summary.sleepWeek.joinToString(", ") { day ->
            day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale) + " " + (day.value?.let { labels[it.ordinal] } ?: notLogged)
        })
        summary.sleepMonthAverage?.let { append(". ").append(month).append(", ").append(labels[it.ordinal]) }
    }
}

@Composable
internal fun caffeineDescription(summary: TrackingSummary): String {
    val locale = rememberAppLocale()
    val notLogged = stringResource(R.string.tracking_not_logged)
    val title = stringResource(R.string.tracking_caffeine)
    val week = stringResource(R.string.tracking_week_chart)
    return buildString {
        append(title).append(". ").append(week).append(": ")
        append(summary.caffeineWeek.joinToString(", ") { day ->
            day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale) + " " + (day.value?.toString() ?: notLogged)
        })
    }
}

@Composable
internal fun waterDescription(summary: TrackingSummary): String {
    val locale = rememberAppLocale()
    val labels = moodLevelLabels()
    val notLogged = stringResource(R.string.tracking_not_logged)
    val title = stringResource(R.string.tracking_water)
    val week = stringResource(R.string.tracking_week_chart)
    val month = stringResource(R.string.tracking_month_chart)
    // Os mL de cada dia já escritos ("1500 mL"); map é inline, por isso stringResource pode estar lá dentro.
    val amounts = summary.waterWeekMl.map { ml -> ml?.let { stringResource(R.string.feed_water_amount, it) } }
    return buildString {
        append(title).append(". ").append(week).append(": ")
        append(summary.waterWeek.mapIndexed { i, day ->
            val amount = amounts.getOrNull(i)
            day.date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale) + " " +
                (if (day.value != null && amount != null) amount + ", " + labels[day.value] else notLogged)
        }.joinToString(", "))
        summary.waterMonthAverage?.let { append(". ").append(month).append(", ").append(labels[it]) }
    }
}

@Composable
internal fun moodDescription(summary: TrackingSummary): String {
    val labels = moodLevelLabels()
    val notLogged = stringResource(R.string.tracking_not_logged)
    val title = stringResource(R.string.tracking_mood)
    val month = stringResource(R.string.tracking_month_chart)
    val average = stringResource(R.string.tracking_average)
    return buildString {
        append(title).append(". ").append(month).append(": ")
        append(summary.moodMonth.filter { it.value != null }.joinToString(", ") { "${it.date.dayOfMonth} ${labels[it.value!!]}" }.ifEmpty { notLogged })
        summary.moodMonthAverage?.let { append(". ").append(average).append(", ").append(labels[it]) }
    }
}

/** Os nomes dos cinco níveis do medidor de emoções (Great ... Bad), também os da água. */
@Composable
internal fun moodLevelLabels(): List<String> = listOf(
    stringResource(R.string.feed_feeling_level_1),
    stringResource(R.string.feed_feeling_level_2),
    stringResource(R.string.feed_feeling_level_3),
    stringResource(R.string.feed_feeling_level_4),
    stringResource(R.string.feed_feeling_level_5),
)

// --- Desenho ------------------------------------------------------------------------------------

/**
 * Os sprites das sombras interiores (feitos uma vez por tamanho, ver [innerShadowSprite]):
 * - bloco: 0, 2, desfoque 9, preto a 15 %, e 0, -4, desfoque 4, #4A3C99 a 63 % (beta-3.0: também no
 *   Restfulness e na água, como na cafeína; Cyclo-2 2003:608);
 * - coluna: 0, -6, desfoque 15, preto a 15 %, numa coluna alta que se corta em três (topo, meio
 *   esticado e fundo) para qualquer altura.
 */
private class ChartSprites(val block: ImageBitmap, val bar: ImageBitmap, val barSlice: Int)

private fun Density.chartSprites(scale: Float): ChartSprites {
    val cell = (Cell * scale).dp.toPx().roundToInt().coerceAtLeast(1)
    val radius = (6f * scale).dp.toPx()
    val block = innerShadowSprite(
        cell,
        cell,
        radius,
        listOf(
            InnerShadowSpec(Color.Black.copy(alpha = 0.15f), Offset(0f, 2.dp.toPx() * scale), 9.dp.toPx() * scale),
            InnerShadowSpec(CycloColors.Accent.copy(alpha = 0.63f), Offset(0f, -4.dp.toPx() * scale), 4.dp.toPx() * scale),
        ),
        this,
    )
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

/** Uma cara de 15 dp e o seu caminho (lido do SVG uma vez). */
private class Face(val path: Path, val color: Color)

@Composable
private fun rememberFaces(faces: List<Pair<String, Color>>): List<Face> =
    remember(faces) { faces.map { (d, color) -> Face(PathParser().parsePathString(d).toPath(), color) } }

/**
 * A cara da média dentro do gráfico (Unified view): aparece e desaparece com uma mola e muda de cor
 * suavemente, como a do título dos cartões da Tracking.
 */
private class AnimatedAverage(val path: State<Path?>, val color: State<Color>, val alpha: State<Float>)

@Composable
private fun rememberAnimatedAverage(average: Pair<String, Color>?, enabled: Boolean): AnimatedAverage? {
    if (!enabled) return null
    val effects = LocalVisualEffects.current
    val alpha = animateFloatAsState(
        targetValue = if (average != null) 1f else 0f,
        animationSpec = if (effects.animationsEnabled) CycloSprings.ChartChange.float else snap(),
        label = "inlineAverage",
    )
    val color = animateColorAsState(
        targetValue = average?.second ?: CycloColors.MoodViolet,
        animationSpec = if (effects.animationsEnabled) CycloSprings.ChartChange.spec() else snap(),
        label = "inlineAverageColor",
    )
    // Ao desaparecer, a cara que estava fica até acabar de se apagar.
    val shown = remember { mutableStateOf<Path?>(null) }
    val pathData = average?.first
    val parsed = remember(pathData) { pathData?.let { PathParser().parsePathString(it).toPath() } }
    if (shown.value == null) shown.value = parsed
    androidx.compose.runtime.SideEffect { if (parsed != null) shown.value = parsed }
    return remember(alpha, color) { AnimatedAverage(shown, color, alpha) }
}

/**
 * A cara da média (encostada a [right]) e a linha tracejada que a liga (de [lineStart] à cara), no
 * Unified view.
 */
private fun DrawScope.drawInlineAverage(average: AnimatedAverage, lineStart: Float, right: Float, u: Float, lineAlpha: Float, blend: BlendMode) {
    val faceLeft = right - FaceSize * u
    val lineEnd = faceLeft - 9f * u
    if (lineEnd > lineStart && lineAlpha > 0f) {
        // 2066:622: 2 dp, pontas redondas, traços de 4 com 4 de intervalo, a meio da altura da cara.
        drawLine(
            color = ChartLineColor,
            start = Offset(lineStart, 8f * u),
            end = Offset(lineEnd, 8f * u),
            strokeWidth = 2f * u,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 4f * u)),
            alpha = lineAlpha,
            blendMode = blend,
        )
    }
    val path = average.path.value ?: return
    drawFace(path, average.color.value, faceLeft, 0f, u, alpha = average.alpha.value)
}

/**
 * O gráfico semanal (colunas sobre os últimos 7 dias, as bases e as iniciais dos dias) e a grelha
 * mensal (blocos de 15 dp, 4 por linha, do dia 1 ao último), lado a lado, com a geometria de
 * [geometry] (a da Tracking ou a do Unified view).
 *
 * Desenhado para os 315 dp do Figma: num ecrã mais estreito, tudo encolhe junto (colunas, blocos e
 * espaços, ver [FigmaContent]); num mais largo, o semanal fica à esquerda e a grelha à direita. O
 * texto segue o tamanho de letra do sistema (a área cresce com ele).
 *
 * @param week a altura de cada coluna, em fração da coluna mais alta da geometria (null = só a base).
 * @param month o preenchimento de cada dia do mês (null = por registar, cinzento).
 * @param legend as caras da legenda (Restfulness e água), uma por nível.
 * @param maxLabel o número do máximo da semana (cafeína) e [maxFace] a cara ao lado.
 * @param average a cara da média do mês, desenhada aqui só no Unified view ([ChartGeometry.inlineAverage]).
 */
@Composable
private fun WeekAndMonthChart(
    geometry: ChartGeometry,
    weekWidth: Float,
    week: List<Pair<LocalDate, Float?>>,
    month: List<BlockFill?>,
    grow: State<Float>,
    active: () -> Boolean,
    legend: List<Pair<String, Color>> = emptyList(),
    maxLabel: String? = null,
    maxFace: Pair<String, Color>? = null,
    average: Pair<String, Color>? = null,
) {
    val effects = LocalVisualEffects.current
    val locale = rememberAppLocale()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val dayStyle = CycloType.CardTitle.copy(textAlign = TextAlign.Center)
    val maxText: TextLayoutResult? = remember(maxLabel, density, measurer) {
        maxLabel?.let { measurer.measure(it, CycloType.CardTitle.copy(color = MaxLabelColor)) }
    }
    val legendFaces = rememberFaces(legend)
    val maxFacePath = remember(maxFace?.first) { maxFace?.first?.let { PathParser().parsePathString(it).toPath() } }
    val inlineAverage = rememberAnimatedAverage(average, geometry.inlineAverage)

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
    ChartHaptics(week.map { it.second }, grow, active)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val available = maxWidth.value
        val scale = (available / FigmaContent).coerceAtMost(1f)
        // As iniciais dos dias seguem o tamanho de letra do sistema, mas nunca ficam mais largas do que
        // a distância entre colunas (com letra grande num ecrã estreito colavam-se: "DSTQQSS"). São
        // um eixo: o TalkBack lê os valores do cartão inteiro.
        val pitchPx = with(density) { (CellPitch * scale).dp.toPx() }
        val days = week.map { it.first.dayOfWeek }
        val dayLabels: List<TextLayoutResult> = remember(days, locale, density, measurer, pitchPx) {
            val names = days.map { it.getDisplayName(DateTextStyle.NARROW, locale) }
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
        val chartHeight = maxOf((geometry.labelTop * scale).dp + labelHeight, ((geometry.gridTop + GridHeight) * scale).dp)
        // As sombras interiores (desfocadas no CPU) e a forma dos blocos fazem-se uma vez por escala,
        // e não a cada recomposição (o desenho muda a cada toque no Feed e a cada minuto).
        val sprites = remember(scale, density) { density.chartSprites(scale) }
        val blockPath = remember(scale, density) { with(density) { (Cell * scale).dp.toPx() }.let { smoothRoundRectPath(it, it, with(density) { (6f * scale).dp.toPx() }) } }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                // Camada própria: o cartão redesenha-se no scroll (o vidro lê o que tem por trás), o gráfico não.
                .graphicsLayer()
                .drawWithCache {
                    val cellPx = (Cell * scale).dp.toPx()
                    val radiusPx = (6f * scale).dp.toPx()
                    // As colunas mudam de altura a cada fotograma da mola: o caminho reaproveita-se.
                    val barPath = Path()
                    onDrawBehind {
                        val u = scale.dp.toPx() // px por dp do Figma
                        val blend = effects.luminosity
                        val clock = grow.value
                        // A linha de cima (2 dp) por cima do semanal (só a Tracking).
                        if (geometry.topLine) {
                            drawRoundRect(
                                color = ChartLineColor,
                                size = Size(weekWidth * u, 2f * u),
                                cornerRadius = CornerRadius(1f * u),
                            )
                        }
                        // As colunas, as bases e as iniciais dos dias.
                        val maxBar = geometry.maxBar * u
                        week.forEachIndexed { i, _ ->
                            val x = i * CellPitch * u
                            val left = x.roundToInt()
                            val g = grown(clock, i, BarStaggerMs)
                            drawRoundRect(
                                color = CycloColors.Accent,
                                topLeft = Offset(x, geometry.baseTop * u),
                                size = Size(cellPx, 2f * u),
                                cornerRadius = CornerRadius(1f * u),
                                alpha = g.coerceIn(0f, 1f),
                            )
                            val h = barHeights[i].value * maxBar * g
                            if (h > 0.5f) {
                                val top = geometry.barBottom * u - h
                                barPath.rewind()
                                barPath.addSmoothRoundRect(x, top, cellPx, h, minOf(radiusPx, h / 2f))
                                drawPath(barPath, CycloColors.Accent)
                                drawBarShadow(sprites.bar, sprites.barSlice, left, top.roundToInt(), h.roundToInt())
                            }
                            val label = dayLabels[i]
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(x + (cellPx - label.size.width) / 2f, geometry.labelTop * u),
                                blendMode = blend,
                            )
                        }
                        // A legenda: uma cara por nível, à altura do topo da coluna dele (na Tracking).
                        legendFaces.forEachIndexed { i, face ->
                            drawFace(face.path, face.color, LegendX * u, geometry.legendTops[i] * u, u, alpha = grown(clock, i, BarStaggerMs).coerceIn(0f, 1f))
                        }
                        // O máximo da semana e a cara da média (cafeína), na linha das colunas mais altas.
                        var lineStart = (LegendX + FaceSize + 9f) * u
                        if (maxText != null && maxAlpha > 0f) {
                            drawText(maxText, topLeft = Offset(LegendX * u, geometry.barTop * u), alpha = maxAlpha, blendMode = blend)
                        }
                        if (maxFacePath != null && maxFace != null) {
                            // A cara fica a 6 dp do número (Figma: "3" em 207, cara em 223), mesmo com letra grande.
                            val faceX = maxOf(CaffeineFaceX * u, LegendX * u + (maxText?.size?.width ?: 0) + 6f * u)
                            drawFace(maxFacePath, maxFace.second, faceX, (geometry.barTop + 1f) * u, u, alpha = maxAlpha)
                            lineStart = faceX + (FaceSize + 9f) * u
                        }
                        // Unified view: a cara da média à direita, ligada à legenda (ou ao máximo).
                        if (inlineAverage != null) {
                            val lineAlpha = if (legendFaces.isEmpty()) maxAlpha else grown(clock, 0, BarStaggerMs).coerceIn(0f, 1f)
                            drawInlineAverage(inlineAverage, lineStart, size.width, u, lineAlpha, blend)
                        }
                        // A grelha do mês: encostada à direita.
                        val gridLeft = size.width - GridWidth * u
                        val gridTop = geometry.gridTop * u
                        month.forEachIndexed { day, _ ->
                            drawBlock(
                                path = blockPath,
                                sprite = sprites.block,
                                left = gridLeft + (day % 4) * CellPitch * u,
                                top = gridTop + (day / 4) * CellPitch * u,
                                grow = grown(clock, day, BlockStaggerMs).coerceIn(0f, 1.2f),
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

/** Um retângulo de cantos suaves em ([x], [y]), [w] x [h], acrescentado a este caminho. */
private fun Path.addSmoothRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float) {
    addPath(smoothRoundRectPath(w, h, radius), Offset(x, y))
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

/** Uma cara de 15 dp (ícone do Figma) em ([x], [y]) px, com [u] px por dp do Figma. */
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
 * No Unified view, a cara da média e a linha tracejada vão por cima da grelha.
 */
@Composable
private fun MoodGrid(days: List<TrackedDay<Int>>, grow: State<Float>, geometry: ChartGeometry, average: Pair<String, Color>?) {
    val effects = LocalVisualEffects.current
    fun <T> spec() = if (effects.animationsEnabled) CycloSprings.ChartChange.spec<T>() else snap()
    val colors = days.mapIndexed { i, day ->
        animateColorAsState(day.value?.let(::moodColor) ?: CycloColors.MoodOkay, spec(), label = "mood$i")
    }
    val logged = days.mapIndexed { i, day ->
        animateFloatAsState(if (day.value != null) 1f else 0f, spec(), label = "moodLogged$i")
    }
    val inlineAverage = rememberAnimatedAverage(average, geometry.inlineAverage)
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val scale = (maxWidth.value / FigmaContent).coerceAtMost(1f)
        val rows = (days.size + MoodColumns - 1) / MoodColumns
        val sprite = remember(scale, density) {
            with(density) {
                val cellPx = (Cell * scale).dp.toPx()
                innerShadowSprite(
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
            }
        }
        val height = (geometry.moodGridTop + (rows - 1) * CellPitch + Cell) * scale
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .graphicsLayer()
                .drawWithCache {
                    val cellPx = (Cell * scale).dp.toPx()
                    val path = smoothRoundRectPath(cellPx, cellPx, (6f * scale).dp.toPx())
                    onDrawBehind {
                        val u = scale.dp.toPx()
                        val gridLeft = (size.width - MoodGridWidth * u) / 2f
                        val gridTop = geometry.moodGridTop * u
                        val clock = grow.value
                        if (inlineAverage != null) {
                            drawInlineAverage(inlineAverage, gridLeft, gridLeft + MoodGridWidth * u, u, grown(clock, 0, BlockStaggerMs).coerceIn(0f, 1f), effects.luminosity)
                        }
                        days.forEachIndexed { i, _ ->
                            drawBlock(
                                path = path,
                                sprite = sprite,
                                left = gridLeft + (i % MoodColumns) * MoodColumnPitch * u,
                                top = gridTop + (i / MoodColumns) * CellPitch * u,
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
