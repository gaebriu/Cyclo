package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.components.IntroIconHeader
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.scrollEdgeFade
import com.gaebriu.cyclo.ui.icons.CycloHeaderIcons
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Introduction 1: "Sleep cycles, what are they?" (só o conteúdo; o botão de cima, a barra de
 * progresso e o Next são fixos e vivem em [IntroductionFlow]).
 *
 * O título aparece com desfoque 6 a 0 e fade, o texto com um simples fade. Seguem [enter]
 * (o progresso da subida do ecrã), por isso duram o mesmo que a subida.
 */
@Composable
fun Introduction1Page(
    enter: () -> Float,
    modifier: Modifier = Modifier,
    iconMotion: com.gaebriu.cyclo.ui.components.IconMotion? = null,
) {
    val effects = LocalVisualEffects.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CycloDimens.ScreenMargin),
    ) {
        Spacer(Modifier.height(46.dp))
        IntroIconHeader(icon = CycloHeaderIcons.Bed, motion = iconMotion, modifier = Modifier.align(Alignment.CenterHorizontally))

        // 7 dp de espaço + 8 dp de folga dentro da área com scroll = 15 dp até ao título.
        // A folga deixa o desfoque do título sair do texto sem ser cortado.
        Spacer(Modifier.height(7.dp))
        // O texto continua com scroll, mas sem corte a direito: as extremidades esbatem-se
        // (fade e desfoque progressivo) quando há texto escondido desse lado.
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .scrollEdgeFade(scroll, CycloDimens.ScrollEdgeFade, FigmaBlur.ScrollEdge)
                .cycloVerticalScroll(scroll),
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
    }
}
