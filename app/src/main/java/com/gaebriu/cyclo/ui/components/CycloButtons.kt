package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Botão principal roxo: o done-b do Figma (Cyclo 3.2.5; Introduction 6 6092:50, menus e prompts):
 * #4A3C99 liso, 48 dp de altura, raio 15 com o corner smoothing de 60 %, texto Inter Semi Bold 15
 * ao centro. Sem contorno nem sombra interior (saíram do Figma na 3.2.5). A largura é a de quem o
 * usa: 315 dp nos menus e prompts (margens de 15 num cartão de 345) e no Introduction 6 / editor
 * (margens de 39 num ecrã de 393).
 */
@Composable
fun CycloPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Os 48 dp do Figma; com letra grande o texto pode passar a duas linhas e o botão cresce.
            .heightIn(min = CycloDimens.InputHeight)
            // O toque (zoom out + háptico) vem ANTES de desenhar, para o desenho também encolher.
            .cycloPressable(onClick = onClick, enabled = enabled)
            .drawWithCache {
                val shape = smoothRoundRectPath(size.width, size.height, CycloDimens.InputRadius.toPx())
                onDrawBehind { drawPath(shape, CycloColors.Accent) }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = CycloType.Button)
    }
}
