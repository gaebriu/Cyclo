package com.gaebriu.cyclo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloGlassButton
import com.gaebriu.cyclo.ui.components.CycloProgressBar
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

private const val StepNumber = 1
private const val StepCount = 6

/**
 * Introduction 1: "Sleep cycles, what are they?".
 *
 * A subida do ecrã é feita por quem o usa (ver [IntroductionFlow]); aqui só se complementa:
 * o título aparece com desfoque 6 a 0 e fade, o texto com um simples fade, e a barra de progresso
 * cresce até 1/6. O desfoque e o fade seguem [enter], por isso duram o mesmo que a subida.
 *
 * @param enter progresso da subida do ecrã, de 0 a 1.
 * @param onClose X: volta ao Introduction 0.
 * @param onNext ação do botão Next (a ligar ao Introduction 2, que ainda não existe).
 */
@Composable
fun Introduction1Screen(
    enter: () -> Float,
    onClose: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(StepNumber / StepCount.toFloat(), CycloMotion.ProgressSpring)
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val closeTop = maxOf(CycloDimens.TopBarTop, statusTop + 24.dp)
    val bottomInset = maxOf(
        CycloDimens.MinBottomInset,
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    // O círculo tem 39 dp mas a zona de toque 48 dp: compensamos os 4,5 dp de cada lado.
    val outset = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(closeTop - outset))
        CycloCircleButton(
            iconPath = CycloIconPaths.Close,
            contentDescription = stringResource(R.string.close),
            onClick = onClose,
            modifier = Modifier.offset(x = -outset),
        )

        Spacer(Modifier.height(CycloDimens.ScreenMargin - outset))
        CycloProgressBar(
            progress = { progress.value },
            description = stringResource(R.string.introduction_progress, StepNumber, StepCount),
        )

        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Bed, modifier = Modifier.align(Alignment.CenterHorizontally))

        // 7 dp de espaço + 8 dp de folga dentro da área com scroll = 15 dp até ao título.
        // A folga deixa o desfoque do título sair do texto sem ser cortado.
        Spacer(Modifier.height(7.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            BasicText(
                text = stringResource(R.string.introduction_1_title),
                style = CycloType.HeaderMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
                    .graphicsLayer {
                        if (effects.animationsEnabled) {
                            val e = enter().coerceIn(0f, 1f)
                            alpha = e
                            renderEffect = if (effects.blurEnabled && e < 1f) {
                                val sigma = figmaBlurPx(FigmaBlur.TextReveal) * (1f - e)
                                BlurEffect(sigma, sigma, TileMode.Decal)
                            } else {
                                null
                            }
                            clip = false
                        }
                    },
            )

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (effects.animationsEnabled) alpha = enter().coerceIn(0f, 1f)
                    },
                verticalArrangement = Arrangement.spacedBy(CycloDimens.Spacing),
            ) {
                listOf(
                    R.string.introduction_1_body_1,
                    R.string.introduction_1_body_2,
                    R.string.introduction_1_body_3,
                    R.string.introduction_1_body_4,
                ).forEach { paragraph ->
                    BasicText(
                        text = stringResource(paragraph),
                        style = CycloType.Body,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(CycloDimens.Spacing))
        CycloGlassButton(
            text = stringResource(R.string.next),
            onClick = onNext,
        )
        Spacer(Modifier.height(bottomInset))
    }
}
