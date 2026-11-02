package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloDropdownField
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

// Medidas do Figma (add-routine-widget-b: 115 x 115 dp, à direita da linha de 345 dp).
private val WidgetButtonSize = 115.dp
private val WidgetIconTop = 23.dp
private val WidgetLabelTop = 56.dp
private val WidgetLabelWidth = 94.dp

/** Distância do microfone à direita da caixa do nome (voice-input-b: 202 + 24 = 226, caixa até 239). */
private val MicEnd = 13.dp

/**
 * Introduction 5: "Want to add it as a widget to your home screen?" (só o conteúdo; o botão de
 * cima, a barra de progresso e o Next são fixos e vivem em [IntroductionFlow]).
 *
 * Por agora é só a ESTRUTURA da página, como no Figma: cabeçalho, título, subtítulo, a caixa do
 * nome (tap-to-write-b, com o nome guardado entre aspas), o campo da repetição (when-to-show-b) e
 * o botão "Add as a widget" (add-routine-widget-b). Ainda não há funcionalidade nem interação:
 * as caixas só mostram o que foi escolhido antes e o botão não faz nada.
 *
 * @param routineName o nome guardado da rotina (o mesmo que o Introduction 4 mostra).
 * @param repeatLabel o texto da repetição escolhida no Introduction 4.
 */
@Composable
fun Introduction5Page(
    routineName: String,
    repeatLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Widgets, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(CycloDimens.Spacing))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(
                text = stringResource(R.string.introduction_5_title),
                style = CycloType.HeaderMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Spacer(Modifier.height(24.dp))
            BasicText(
                text = stringResource(R.string.introduction_5_subtitle),
                style = CycloType.Body,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(63.dp))

            // Linha de 345 dp (grupo "group" do Figma): à esquerda as caixas da rotina
            // (tap-to-write-b e when-to-show-b, 215 x 48 dp, 15 dp entre elas), à direita o botão.
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CycloDimens.Spacing),
                ) {
                    RoutineNameBox(name = routineName)
                    // O mesmo campo do Introduction 4, mas ainda sem abrir o menu.
                    CycloDropdownField(
                        text = repeatLabel,
                        description = stringResource(R.string.introduction_4_repeat_field),
                        onClick = {},
                        enabled = false,
                    )
                }
                Spacer(Modifier.width(CycloDimens.Spacing))
                AddWidgetButton()
            }
        }
    }
}

/**
 * Caixa do nome (tap-to-write-b): #101011, raio 15, o nome entre aspas a 15 dp da esquerda e o
 * microfone (#A3A2A7, Luminosity) a 13 dp da direita, como no Figma. Só visual por agora.
 */
@Composable
private fun RoutineNameBox(name: String) {
    val effects = LocalVisualEffects.current
    val mic = remember { PathParser().parsePathString(CycloIconPaths.Mic).toPath() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CycloDimens.InputHeight)
            .drawBehind {
                drawRoundRect(
                    color = CycloColors.GlassFill,
                    cornerRadius = CornerRadius(CycloDimens.InputRadius.toPx()),
                )
            },
    ) {
        BasicText(
            text = stringResource(R.string.introduction_5_routine_name, name),
            style = CycloType.Input,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 15.dp, end = MicEnd + 24.dp + 15.dp),
        )
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = MicEnd)
                .size(24.dp),
        ) {
            val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
            val pxPerDp = density
            withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                drawPath(path = mic, color = CycloColors.TextSecondary, blendMode = blend)
            }
        }
    }
}

/**
 * Botão "Add as a widget" (add-routine-widget-b): 115 x 115 dp, raio 24, #101011 sem mistura,
 * sombra interior 0, -6, desfoque 15 (preto a 15 %) e sem borda. Ícone de 24 dp a 23 dp do topo
 * e texto a 56 dp do topo, ambos ao centro. Só visual por agora.
 */
@Composable
private fun AddWidgetButton() {
    val effects = LocalVisualEffects.current
    val icon = remember { PathParser().parsePathString(CycloIconPaths.AddWidget).toPath() }

    Box(
        modifier = Modifier
            .size(WidgetButtonSize)
            .drawBehind {
                val radius = CycloDimens.CornerRadius.toPx()
                val shape = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }
                drawPath(shape, color = CycloColors.GlassFill)
                drawInnerShadow(
                    shape = shape,
                    color = Color.Black.copy(alpha = 0.15f),
                    offset = Offset(0f, (-6).dp.toPx()),
                    cssBlurPx = 15.dp.toPx(),
                )
            },
    ) {
        // Ícone #A3A2A7 em mistura Luminosity (normal sem efeitos), como no design.
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WidgetIconTop)
                .size(24.dp),
        ) {
            val blend = if (effects.blurEnabled) BlendMode.Luminosity else BlendMode.SrcOver
            val pxPerDp = density
            withTransform({ scale(pxPerDp, pxPerDp, Offset.Zero) }) {
                drawPath(path = icon, color = CycloColors.TextSecondary, blendMode = blend)
            }
        }
        BasicText(
            text = stringResource(R.string.introduction_5_add_widget),
            style = CycloType.Body,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WidgetLabelTop)
                .width(WidgetLabelWidth),
        )
    }
}
