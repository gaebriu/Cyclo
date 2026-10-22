package com.gaebriu.cyclo.ui.theme

import androidx.compose.ui.unit.Density

/**
 * O Figma mede o desfoque com o dobro da escala do Android (e do CSS):
 * um desfoque de 63 no Figma equivale a um raio de 31,5 no Android.
 * Os valores do design entram sempre por aqui, tal como estão no Figma.
 */
fun Density.figmaBlurPx(figmaRadius: Float): Float = figmaRadius / 2f * density

/** Valores de desfoque tal como estão no Figma. */
object FigmaBlur {
    /** Brilho do topo (elipse "gradient with blur"). */
    const val Glow = 267f

    /** Letras do "Hello!" a aparecer. */
    const val Letter = 6f

    /** Botão "What's new?" (news-b): exceção deliberada ao valor unificado de 39. */
    const val NewsButton = 63f
}
