package com.gaebriu.cyclo.ui.effects

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Uma página de um conjunto EMPILHADO, guiado por uma posição contínua ([position]: 1 = a página 1,
 * 2 = a 2...). Beta-3.2: é a transição das Introduction e do editor de rotinas, a mesma linguagem
 * do ecrã que sobe por cima de outro ([com.gaebriu.cyclo.ui.CycloApp], `cover`):
 *
 * - a que entra sobe de baixo, opaca, e tapa a anterior (translação da altura inteira);
 * - a anterior recua por baixo dela: zoom out até [CycloMotion.BackgroundScale], véu #010101 até
 *   [CycloMotion.BackgroundOverlayAlpha] e, com as texturas avançadas, o desfoque do recuo;
 * - uma página inteira ou mais longe não se desenha (alpha 0, sem RenderEffect: nenhuma camada
 *   desfocada fica viva por baixo de uma página assente);
 * - a última página ([hasNext] falso) não recua: passar dela (a resistência do gesto) só a sobe;
 * - sem animações no sistema, só o fade da que entra.
 *
 * Só lê a posição na camada e no desenho: arrastar ou animar a posição não recompõe nada.
 */
fun Modifier.stackedPage(
    index: Int,
    position: () -> Float,
    hasNext: Boolean,
    effects: VisualEffects,
): Modifier = graphicsLayer {
    val local = position() - index
    renderEffect = null
    when {
        local <= -1f || (hasNext && local >= 1f) -> alpha = 0f
        local <= 0f -> {
            // A entrar (ou a resistência para trás na primeira página): vem de baixo.
            if (effects.animationsEnabled) translationY = -local * size.height else alpha = 1f + local
        }
        !hasNext -> {
            // Para lá da última: só a resistência, a página sobe com o dedo.
            if (effects.animationsEnabled) translationY = -local * size.height
        }
        effects.animationsEnabled -> {
            val s = 1f - (1f - CycloMotion.BackgroundScale) * local
            scaleX = s
            scaleY = s
            if (effects.textures) {
                val sigma = figmaBlurPx(FigmaBlur.BackgroundRecede) * local
                if (sigma >= 0.5f) renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp)
            }
        }
    }
}.drawWithContent {
    drawContent()
    // O véu da página que recua (dentro da camada: encolhe com ela).
    val local = position() - index
    if (hasNext && local > 0f && local < 1f) {
        drawRect(CycloColors.Background, alpha = CycloMotion.BackgroundOverlayAlpha * local)
    }
}
