package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.TrackingSection
import com.gaebriu.cyclo.data.TrackingSummary
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.ReorderableColumn
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType

// Medidas do Figma (Tracking, Cyclo-2 2001:115 e 2006:449; frame de 393 x 852).
/** O conteúdo começa a 126 dp do topo (o primeiro cartão). */
private val CardPadding = 15.dp

/** Do título (18 dp de altura) à linha de cima dos gráficos. */
private val TitleToChart = 7.dp

/** A cara da média ("average", "average-mood") desce isto do topo do título. */
private val AverageTop = 1.dp

/** Do título à grelha do Mood. */
private val TitleToMoodGrid = 15.dp

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
