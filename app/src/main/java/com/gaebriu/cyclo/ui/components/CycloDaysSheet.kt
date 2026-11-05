package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.routine.Weekday
import com.gaebriu.cyclo.ui.routine.hasDay
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlin.math.roundToInt

/**
 * action-menu-big-choose-days (Componentes 2, 178:97): a folha para escolher os dias da semana.
 *
 * Medidas do Figma (345 x 240 dp, conteúdo com 15 dp de cada lado, 315 dp de largura):
 * - pega swipe-to-close-b: 39 x 2, #FCFCFE a 15 %, pontas redondas, 22 dp abaixo do topo;
 * - 15 dp, título "Choose days" (Inter Semi Bold 15, #FCFCFE, ao centro);
 * - 15 dp, descrição (Inter Medium 15, #A3A2A7, ao centro);
 * - 15 dp, os sete dias (domingo primeiro), círculos de 39 dp com 7 dp entre eles (ver [DaysRow]);
 * - 15 dp, done-b (o botão roxo compacto, 315 x 48) e 15 dp até à borda de baixo.
 *
 * O fundo é o [repeatMenuSurface].
 *
 * @param selectedMask os dias ligados (ver [hasDay]).
 */
@Composable
fun CycloDaysSheet(
    selectedMask: Int,
    onToggleDay: (Weekday) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .repeatMenuSurface()
            .padding(horizontal = SheetPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(HandleTop))
        SwipeHandle()

        Spacer(Modifier.height(Gap))
        BasicText(text = stringResource(R.string.days_sheet_title), style = TitleStyle)

        Spacer(Modifier.height(Gap))
        BasicText(
            text = stringResource(R.string.days_sheet_description),
            style = DescriptionStyle,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Gap))
        DaysRow(modifier = Modifier.fillMaxWidth()) {
            Weekday.values().forEach { day ->
                DayChip(day = day, selected = selectedMask.hasDay(day), onToggle = { onToggleDay(day) })
            }
        }

        Spacer(Modifier.height(Gap))
        CycloPrimaryButton(
            text = stringResource(R.string.days_sheet_done),
            onClick = onDone,
            size = CycloButtonSize.Compact,
        )
        Spacer(Modifier.height(Gap))
    }
}

/**
 * A fila "days" (178:157): sete círculos iguais com espaços iguais.
 *
 * No Figma (ecrã de 393 dp) a fila tem 315 dp, exatamente a largura do conteúdo da folha:
 * 7 x 39 + 6 x 7. Em ecrãs mais estreitos sobra menos do que isso, e um `spacedBy(7.dp)` dava os
 * 39 dp aos primeiros dias e o resto ao sábado, que ficava espremido com o "S" fora do círculo.
 * Aqui a fila mede a largura que tem: com 315 dp ou mais é a do Figma (39 / 7, ao centro); com
 * menos, círculos e espaços encolhem juntos na proporção 39 : 7 e a fila ocupa a largura toda.
 */
@Composable
private fun DaysRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val count = measurables.size
        val figmaDay = DaySize.toPx()
        val figmaGap = DayGap.toPx()
        val figmaRow = figmaDay * count + figmaGap * (count - 1)
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else figmaRow
        val scale = (available / figmaRow).coerceAtMost(1f)

        val day = (figmaDay * scale).toInt().coerceAtLeast(1)
        // O que sobra dos círculos (já arredondados) é repartido por igual entre eles.
        val rowWidth = (figmaRow * scale).coerceAtMost(available)
        val gap = if (count > 1) (rowWidth - day * count) / (count - 1) else 0f
        val placeables = measurables.map { it.measure(Constraints.fixed(day, day)) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else rowWidth.roundToInt()
        val start = (width - rowWidth) / 2f

        layout(width, day) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place((start + index * (day + gap)).roundToInt(), 0)
            }
        }
    }
}

/** swipe-to-close-b: uma linha de 39 dp com 2 dp de espessura e pontas redondas. */
@Composable
private fun SwipeHandle() {
    Canvas(modifier = Modifier.size(width = HandleWidth, height = HandleThickness)) {
        val stroke = size.height
        val y = size.height / 2f
        drawLine(
            color = CycloColors.TextPrimary.copy(alpha = 0.15f),
            start = Offset(stroke / 2f, y),
            end = Offset(size.width - stroke / 2f, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Um dia da semana (sunday … saturday): círculo de 39 dp que passa de background-off a background-on com uma mola. */
@Composable
private fun DayChip(day: Weekday, selected: Boolean, onToggle: () -> Unit) {
    val view = LocalView.current
    val blend = LocalVisualEffects.current.luminosity
    val letter = stringResource(day.shortLabel)
    val name = stringResource(day.fullName)
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = CycloSprings.fromStiffness(Spring.StiffnessMediumLow, 0.8f).float,
        label = "day",
    )

    Box(
        // O tamanho vem da [DaysRow] (39 dp, ou menos em ecrãs estreitos).
        modifier = Modifier
            .cycloPressable(
                onClick = {
                    // Ligar / desligar tem o seu próprio háptico.
                    CycloHaptics.toggle(view, !selected)
                    onToggle()
                },
                haptic = false,
                pressedScale = CycloMotion.IconButtonPressScale,
                role = Role.Checkbox,
            )
            .semantics {
                this.selected = selected
                contentDescription = name
            }
            .drawBehind { drawDay(progress, blend) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = letter,
            style = CycloType.Button.copy(color = lerp(DayLetterOff, CycloColors.TextPrimary, progress)),
        )
    }
}

/**
 * O círculo de um dia, como nos SVG do Figma (background-off / background-on):
 * - preenchimento: #A3A2A7 a 24 % (desligado) ou #4A3C99 (ligado);
 * - sombra interior: preto a 15 %, (0, 6), desfoque 15;
 * - contorno de 1 dp por dentro (círculo de raio 19 com traço 1): a cor do preenchimento a 24 %,
 *   em Luminosity.
 */
private fun DrawScope.drawDay(progress: Float, blend: BlendMode) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(color = lerp(DayFillOff, DayFillOn, progress), radius = radius, center = center)

    val circle = Path().apply { addOval(Rect(center, radius)) }
    drawInnerShadow(circle, DayShadow, Offset(0f, 6.dp.toPx()), 15.dp.toPx())

    val strokeWidth = 1.dp.toPx()
    drawCircle(
        color = lerp(DayStrokeOff, DayStrokeOn, progress),
        radius = radius - strokeWidth / 2f,
        center = center,
        style = Stroke(width = strokeWidth),
        blendMode = blend,
    )
}

// Medidas do Figma (178:97).
private val SheetPadding = 15.dp
private val Gap = 15.dp
private val HandleTop = 22.dp
private val HandleWidth = 39.dp
private val HandleThickness = 2.dp
private val DaySize = 39.dp
private val DayGap = 7.dp

/**
 * Título (178:158) e descrição (178:160) com a altura de linha das caixas do Figma (18 por linha).
 * Com a letra do sistema maior, crescem com ela.
 */
private val FigmaLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val TitleStyle: TextStyle = CycloType.SheetTitle.copy(lineHeight = 18.sp, lineHeightStyle = FigmaLine)
private val DescriptionStyle: TextStyle = CycloType.Body.copy(lineHeight = 18.sp, lineHeightStyle = FigmaLine)

// Cores do Figma (178:92 background-off, 178:117 background-on, letras 178:114 / 178:118).
private val DayFillOff = CycloColors.TextSecondary.copy(alpha = 0.24f)
private val DayFillOn = CycloColors.Accent
private val DayStrokeOff = CycloColors.TextSecondary.copy(alpha = 0.24f)
private val DayStrokeOn = CycloColors.Accent.copy(alpha = 0.24f)
private val DayLetterOff = CycloColors.TextPrimary.copy(alpha = 0.24f)
private val DayShadow = Color.Black.copy(alpha = 0.15f)
