package com.gaebriu.cyclo.ui.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.gaebriu.cyclo.ui.theme.CycloMotion
import kotlin.math.abs

/**
 * Uma página de um conjunto lado a lado, guiado por UMA posição contínua ([position]: 0 = a
 * primeira, 1 = a segunda...). É a transição do Feed, das rotinas guardadas e da Tracking
 * ([com.gaebriu.cyclo.ui.main.MainScreen]). As Introduction usaram-na da Beta 2.2 à 3.1; desde a
 * beta-3.2 estão empilhadas ([stackedPage]):
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
    // Cyclo 3.2.1: o alpha aplica-se a cada desenho, sem camada. Com a estratégia Auto, uma página
    // a meio do deslize (alpha < 1) era desenhada inteira numa camada fora do ecrã do tamanho do
    // ecrã, a cada fotograma, e eram duas (a que sai e a que entra). A base dos cartões é a cópia
    // do que está por baixo (ver Backdrop.kt), por isso a diferença só se vê no conteúdo sobreposto
    // dentro da mesma página, e só durante o deslize.
    compositingStrategy = CompositingStrategy.ModulateAlpha
    val local = position() - index
    val away = abs(local).coerceIn(0f, 1f)
    if (animationsEnabled) {
        translationX = -local * size.width
        alpha = if (away >= 1f) 0f else 1f - away * (1f - CycloMotion.PageOutgoingAlpha)
    } else {
        alpha = 1f - away
    }
}
