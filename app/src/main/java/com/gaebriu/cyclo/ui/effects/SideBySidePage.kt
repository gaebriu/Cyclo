package com.gaebriu.cyclo.ui.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.gaebriu.cyclo.ui.theme.CycloMotion
import kotlin.math.abs

/**
 * Uma página de um conjunto lado a lado, guiado por UMA posição contínua ([position]: 0 = a
 * primeira, 1 = a segunda...). É a transição do Feed, das rotinas guardadas e da Tracking
 * ([com.gaebriu.cyclo.ui.main.MainScreen]) e, desde a Beta 2.2, a das páginas do Introduction
 * (antes, a página nova passava POR CIMA da anterior, que só se deslocava 30 %):
 *
 * - a página desliza a largura inteira com a posição: a que entra vem encostada à que sai, e as
 *   duas veem-se sempre (nunca há um fotograma sem nenhuma);
 * - a que sai escurece até [CycloMotion.PageOutgoingAlpha] (e a que entra acende a partir daí);
 * - fora de vista (uma página inteira ou mais de distância) não se desenha (alpha 0);
 * - sem animações no sistema, só o fade (a que sai apaga-se, a que entra acende).
 *
 * Só lê a posição na camada: arrastar ou animar a posição não recompõe nada.
 */
fun Modifier.sideBySidePage(index: Int, position: () -> Float, animationsEnabled: Boolean): Modifier = graphicsLayer {
    val local = position() - index
    val away = abs(local).coerceIn(0f, 1f)
    if (animationsEnabled) {
        translationX = -local * size.width
        alpha = if (away >= 1f) 0f else 1f - away * (1f - CycloMotion.PageOutgoingAlpha)
    } else {
        alpha = 1f - away
    }
}
