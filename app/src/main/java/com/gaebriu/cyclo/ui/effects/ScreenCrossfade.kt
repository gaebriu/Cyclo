package com.gaebriu.cyclo.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.theme.CycloSprings
import androidx.compose.runtime.withFrameNanos

/**
 * Passagem suave entre duas formas de desenhar o MESMO ecrã: "Advanced textures" a ligar ou a
 * desligar, e a língua a mudar. Nenhuma das duas mexe na disposição nem na navegação, mas ambas
 * mudam tudo de uma vez (desfoques e misturas, ou os textos todos), o que num só frame parece um
 * recarregar.
 *
 * Como funciona ([run]): tira-se uma fotografia do ecrã como está, que se põe por cima de tudo
 * ([ScreenCrossfadeOverlay]); muda-se o desenho por baixo; e, quando o desenho novo já está feito,
 * a fotografia desvanece com uma mola sem ressalto. O que se vê é o ecrã antigo a dissolver-se no
 * novo, sem frames intermédios estranhos. O caminho normal de desenho não muda nada: com as
 * texturas ligadas fica exatamente o desenho de sempre.
 *
 * A fotografia não recebe toques nem aparece ao TalkBack (o conteúdo verdadeiro está por baixo).
 */
@Stable
class ScreenCrossfade {
    internal var image by mutableStateOf<ImageBitmap?>(null)
    internal val alpha = Animatable(0f)

    /** Onde está o controlo que fica fora da fotografia (ver [keepLiveDuringCrossfade]). */
    internal var liveArea: Rect? = null

    /** A zona que a fotografia em curso deixa à vista, viva (null = nenhuma). */
    internal var hole by mutableStateOf<Rect?>(null)

    /**
     * Tira a fotografia de [source], corre [change] por baixo dela, espera por [ready] (ex.: os
     * textos já na língua nova) e desvanece-a. Se a fotografia falhar, a mudança faz-se na mesma.
     *
     * @param keepLive deixa fora da fotografia o controlo marcado com [keepLiveDuringCrossfade]
     *                 (o interruptor que acabou de ser tocado continua a deslizar, sem fantasma).
     */
    suspend fun run(source: BackdropLayer, keepLive: Boolean = false, ready: suspend () -> Unit = {}, change: () -> Unit) {
        val shot = runCatching { source.layer.toImageBitmap() }.getOrNull()
        if (shot == null) {
            change()
            return
        }
        try {
            alpha.snapTo(1f)
            hole = if (keepLive) liveArea else null
            image = shot
            // A fotografia já está por cima antes de o desenho mudar.
            withFrameNanos { }
            change()
            ready()
            // Dois frames: o desenho novo já foi composto e desenhado por baixo.
            withFrameNanos { }
            withFrameNanos { }
            alpha.animateTo(0f, CycloSprings.VeilOut.float)
        } finally {
            image = null
            hole = null
            alpha.snapTo(0f)
        }
    }
}

/** A fotografia de [state] por cima do ecrã enquanto desvanece (ver [ScreenCrossfade]). */
@Composable
fun ScreenCrossfadeOverlay(state: ScreenCrossfade, modifier: Modifier = Modifier) {
    val image = state.image ?: return
    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        // A opacidade lê-se aqui, ao desenhar: o desvanecer não recompõe nada.
        val a = state.alpha.value.coerceIn(0f, 1f)
        val hole = state.hole
        if (hole == null) {
            drawImage(image, alpha = a)
        } else {
            clipRect(hole.left, hole.top, hole.right, hole.bottom, clipOp = ClipOp.Difference) {
                drawImage(image, alpha = a)
            }
        }
    }
}

/** O [ScreenCrossfade] da app (dado pela CycloApp). */
val LocalScreenCrossfade = staticCompositionLocalOf<ScreenCrossfade?> { null }

/**
 * Marca o controlo que muda o desenho (o interruptor "Advanced textures"): durante a passagem fica
 * fora da fotografia, vivo, para se ver a deslizar uma só vez em vez de um fantasma dele a
 * desvanecer. A margem cobre o pequeno ressalto do gel ao largar.
 */
fun Modifier.keepLiveDuringCrossfade(): Modifier = composed {
    val state = LocalScreenCrossfade.current
    val margin = with(LocalDensity.current) { LiveAreaMargin.toPx() }
    if (state == null) this else onGloballyPositioned { state.liveArea = it.boundsInRoot().inflate(margin) }
}

private val LiveAreaMargin = 6.dp
