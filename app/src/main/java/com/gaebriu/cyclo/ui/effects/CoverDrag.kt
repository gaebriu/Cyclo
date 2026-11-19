package com.gaebriu.cyclo.ui.effects

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * O ecrã de cima (Settings, editor de rotinas) a descer com o dedo (Cyclo 3.1): o mesmo `cover` do
 * CycloApp que sobe ao abrir e desce ao fechar, agora também levado pelo dedo, como o gesto de
 * voltar. Ao largar, a mola dos ecrãs acaba o caminho a partir da velocidade do dedo (fecha, ou volta
 * a subir). Quem estava por baixo (o Feed ou as rotinas guardadas) continua lá: fechar regressa
 * sempre à origem, sem guardar mais nada.
 *
 * Distâncias e velocidades em ecrãs (a altura), positivas para baixo.
 */
@Stable
interface CoverDrag {
    /** O dedo pegou no ecrã de cima. Falso = agora não se pode (nada aberto, ou já a fechar). */
    fun start(): Boolean

    /** O dedo levou o ecrã [screens] para baixo desde onde estava. */
    fun drag(screens: Float)

    /** O dedo largou a [screensPerSecond] (para baixo, positivo): fecha ou volta a subir. */
    fun release(screensPerSecond: Float)
}

/** O [CoverDrag] do ecrã de cima atual (null fora dele). */
val LocalCoverDrag = staticCompositionLocalOf<CoverDrag?> { null }
