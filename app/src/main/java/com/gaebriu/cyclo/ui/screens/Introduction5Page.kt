package com.gaebriu.cyclo.ui.screens

import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloDropdownField
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.components.RoutineNameInput
import com.gaebriu.cyclo.ui.components.rememberRoutineNameInput
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.keyboard.rememberFadeWithKeyboard
import com.gaebriu.cyclo.ui.keyboard.rememberIsKeyboardOpen
import com.gaebriu.cyclo.ui.routine.RepeatSelection
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType

// Medidas do Figma (add-routine-widget-b: 115 x 115 dp, à direita da linha de 345 dp).
private val WidgetButtonSize = 115.dp
private val WidgetIconTop = 23.dp
private val WidgetLabelTop = 56.dp
private val WidgetLabelWidth = 94.dp

/**
 * Introduction 5: "Want to add it as a widget to your home screen?" (só o conteúdo; o botão de
 * cima, a barra de progresso e o Next são fixos, e os menus da repetição vivem em [RepeatOverlays]).
 *
 * - A caixa do nome (tap-to-write-b) e o campo da repetição (when-to-show-b) são os mesmos do
 *   Introduction 4 e funcionam da mesma forma: o nome edita-se a escrever ou a ditar, e o campo
 *   abre o menu da repetição. O nome aparece sem aspas (as do Figma eram só uma indicação).
 * - Ao tocar na caixa do nome (teclado aberto), ela alarga com uma mola até à largura toda da
 *   linha, e o subtítulo (o "text" do text-group), o campo da repetição e o botão "Add as a widget"
 *   desvanecem-se com o mesmo fade do Introduction 3 e 4; tudo volta quando o teclado fecha.
 *   Durante o ditado nada se esconde e a caixa não alarga.
 * - O botão "Add as a widget" pede ao ecrã principal para fixar o widget desta rotina ([onAddWidget]).
 */
@Composable
fun Introduction5Page(
    name: String,
    onNameChange: (String) -> Unit,
    repeat: RepeatSelection,
    modifier: Modifier = Modifier,
    iconMotion: com.gaebriu.cyclo.ui.components.IconMotion? = null,
    onAddWidget: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val nameInput = rememberRoutineNameInput(onNameChange)
    val fade = rememberFadeWithKeyboard(keepVisible = nameInput.isDictating)
    // Enquanto está (quase) escondido, o campo da repetição não aceita toques.
    val dropdownInteractive by remember { derivedStateOf { fade.value >= 0.99f } }

    // Alargamento da caixa do nome: 0 = ao lado do botão (215 dp no Figma), 1 = linha inteira.
    val expand = animateFloatAsState(
        targetValue = if (rememberIsKeyboardOpen() && !nameInput.isDictating) 1f else 0f,
        animationSpec = CycloMotion.NameFieldExpandSpring,
        label = "nameFieldExpand",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            // Tocar fora da caixa fecha o teclado.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Widgets, motion = iconMotion, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(CycloDimens.Spacing))
        // O padding horizontal vai por dentro, para a área com scroll ocupar o ecrã todo
        // e o brilho da caixa a ouvir não ser cortado nas laterais (como no Introduction 4).
        Column(
            modifier = Modifier
                .weight(1f)
                .cycloVerticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin)) {
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
                    // Mantém o espaço: só muda a opacidade, por isso nada salta.
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = fade.value },
                )
                Spacer(Modifier.height(63.dp))
            }

            // Grupo de 345 dp ("group" do Figma): à esquerda a caixa do nome e o campo da
            // repetição (215 x 48 dp, 15 dp entre eles), à direita o botão de 115 dp.
            Box(
                modifier = Modifier
                    .padding(horizontal = CycloDimens.ScreenMargin)
                    .fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Lugar da caixa do nome, que vai por cima (ver em baixo).
                        Spacer(Modifier.height(CycloDimens.InputHeight + CycloDimens.Spacing))
                        Box(modifier = Modifier.graphicsLayer { alpha = fade.value }) {
                            CycloDropdownField(
                                text = stringResource(repeat.option.label),
                                description = stringResource(R.string.introduction_4_repeat_field),
                                onClick = { repeat.isMenuOpen = true },
                                enabled = dropdownInteractive,
                                // Beta 2.4: numa linha, com reticências se não couber (ex.: "Mostrar só desta…").
                                singleLine = true,
                                onBoundsInRoot = { repeat.anchor = it },
                            )
                        }
                    }
                    Spacer(Modifier.width(CycloDimens.Spacing))
                    AddWidgetButton(
                        onClick = onAddWidget,
                        enabled = dropdownInteractive,
                        modifier = Modifier.graphicsLayer { alpha = fade.value },
                    )
                }

                // A caixa do nome por cima de tudo: alarga do lugar dela até à linha inteira.
                // A largura lê-se só na medição, por isso a animação não recompõe a página.
                RoutineNameInput(
                    state = nameInput,
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.layout { measurable, constraints ->
                        val full = constraints.maxWidth
                        val collapsed = (full - (CycloDimens.Spacing + WidgetButtonSize).roundToPx())
                            .coerceAtLeast(0)
                        val width = lerp(collapsed, full, expand.value)
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
                )
            }
        }
    }
}

/**
 * Botão "Add as a widget" (add-routine-widget-b): 115 x 115 dp, raio 24, #101011 sem mistura,
 * sombra interior 0, -6, desfoque 15 (preto a 15 %) e sem borda. Ícone de 24 dp a 23 dp do topo
 * e texto a 56 dp do topo, ambos ao centro.
 */
@Composable
private fun AddWidgetButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    val icon = remember { PathParser().parsePathString(CycloIconPaths.AddWidget).toPath() }

    Box(
        modifier = modifier
            .size(WidgetButtonSize)
            .cycloPressable(onClick = onClick, enabled = enabled)
            .drawBehind {
                val radius = CycloDimens.CornerRadius.toPx()
                val shape = smoothRoundRectPath(size.width, size.height, radius)
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
            val blend = effects.luminosity
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
