package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.LetterRevealText
import com.gaebriu.cyclo.ui.components.rememberLetterReveal
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.delay

// Medida do Figma (Cyclo-2 9:232), a partir do topo da área das páginas (135 dp do topo do frame
// de 852 dp): o texto começa a 265 dp (376 dp do topo do "content").
private val TextTop = 265.dp

/**
 * Espaçamento extra das letras no início, no texto do Introduction 6 e do loading-data (fração do
 * tamanho da letra). Num texto de 15 abre o mesmo, a olho, que os 15 % do "Hello!" de 39.
 */
internal const val AllSetTextSpacingEm = 0.3f

/**
 * Introduction 6: "You’re all set!" (o botão de cima e o Next já saíram).
 *
 * Beta 2.1: a argola de fundo (fx circle-with-blur) saiu do design; fica só o texto.
 *
 * Quando a página assenta ([active]), o texto aparece letra a letra, com o ritmo e o desfoque do
 * "Hello!" do Introduction 0 (à escala do texto), e o espaçamento fecha de [AllSetTextSpacingEm]
 * até ao do Figma, numa mola criticamente amortecida; no fim, espera 1 s e chama [onFinished]
 * (vai para o Feed).
 *
 * Sem animações no sistema, o texto só aparece com fade.
 */
@Composable
fun Introduction6Page(
    active: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(R.string.introduction_6_text)
    val reveal = rememberLetterReveal(text, spacingEm = AllSetTextSpacingEm, settles = true)
    val latestOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(active, reveal) {
        if (!active) return@LaunchedEffect
        reveal.play()
        delay(CycloMotion.AllSetHoldMs)
        latestOnFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LetterRevealText(
            state = reveal,
            // Inter Medium 15, #A3A2A7, centrado (sem sombra, como no Figma).
            style = CycloType.Body,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = TextTop)
                .semantics { heading() },
        )
    }
}
