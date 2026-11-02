package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloProgressBar
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloDimens

/**
 * Parte de cima fixa dos ecrãs de introdução: o botão redondo (X no Introduction 1, seta de
 * voltar daí em diante) e a barra de progresso. Não se mexe quando as páginas deslizam:
 * só o ícone muda (fade cruzado) e a barra cresce.
 *
 * @param leadingIsBack se o botão está (ou vai ficar) em modo "voltar", para o TalkBack.
 * @param backAmount de 0 (X) a 1 (seta), segue a posição das páginas.
 * @param progress de 0 a 1.
 * @param leadingAlpha opacidade do botão (o Introduction 6 fá-lo sair com um fade).
 * @param leadingEnabled falso: o botão não responde nem aparece ao TalkBack.
 */
@Composable
fun IntroTopBar(
    leadingIsBack: Boolean,
    backAmount: () -> Float,
    progress: () -> Float,
    progressDescription: String,
    onLeadingClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingAlpha: () -> Float = { 1f },
    leadingEnabled: Boolean = true,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val closeTop = maxOf(CycloDimens.TopBarTop, statusTop + 24.dp)
    // O círculo tem 39 dp mas a zona de toque 48 dp: compensamos os 4,5 dp de cada lado.
    val outset = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2

    Column(modifier = modifier) {
        Spacer(Modifier.height(closeTop - outset))
        CycloCircleButton(
            iconPath = CycloIconPaths.Close,
            secondaryIconPath = CycloIconPaths.Back,
            secondaryIconAmount = backAmount,
            contentDescription = stringResource(if (leadingIsBack) R.string.back else R.string.close),
            onClick = onLeadingClick,
            enabled = leadingEnabled,
            modifier = Modifier
                .offset(x = -outset)
                .graphicsLayer { alpha = leadingAlpha() }
                .then(if (leadingEnabled) Modifier else Modifier.clearAndSetSemantics { }),
        )

        Spacer(Modifier.height(CycloDimens.ScreenMargin - outset))
        CycloProgressBar(progress = progress, description = progressDescription)
    }
}
