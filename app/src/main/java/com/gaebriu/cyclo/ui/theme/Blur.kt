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
    /** Brilho do topo do Introduction 0 (elipse "gradient with blur"). */
    const val Glow = 267f

    /** Letras do "Hello!" a aparecer: desfoque 9 a 0. */
    const val HelloReveal = 9f

    /** Títulos a aparecer (Introduction 1): desfoque 6 a 0. */
    const val TextReveal = 6f

    /** Ecrã que recua por baixo de outro (Introduction 0 enquanto o 1 sobe). */
    const val BackgroundRecede = 9f

    /**
     * Desfoque máximo nas extremidades do texto com scroll da introdução. O desfoque progressivo
     * do Figma é 15 na borda ([EdgeProgressive]); aqui fica mais leve (PROPOSTA), porque o texto
     * já está quase transparente pelo fade.
     */
    const val ScrollEdge = 9f

    /** Anel do Introduction 6 ("fx circle-with-blur"): desfoque 39. */
    const val Circle = 39f

    /**
     * Desfoque do anel do Introduction 6 no início da animação (muito desfocado). PROPOSTA:
     * o triplo do final, para a revelação da esquerda para a direita se notar bem.
     */
    const val CircleRevealStart = 117f

    /** Brilho do topo do Feed e das rotinas guardadas ("fx background blur"): desfoque 267. */
    const val FeedGlow = 267f

    /** Barra de navegação (navigation-bar): "background blur" 39 sobre o conteúdo que passa por trás. */
    const val NavigationBar = 39f

    /** Mini-menu (mini-menu-group): "background blur" 63. */
    const val MiniMenu = 63f

    /** Menus, folhas, barra de baixo e caixa de pesquisa (undo-action, action-menu, search-box): 39. */
    const val Overlay = 39f

    /**
     * "fx progressive-blur-top" e "-bottom" do Feed, das rotinas guardadas e das definições:
     * desfoque progressivo de 15 na borda do ecrã até 0 na borda de dentro (startRadius 15).
     */
    const val EdgeProgressive = 15f

    /** Menus (action-menu) a abrir e a fechar: desfoque 6 a 0, como os títulos a aparecer. */
    const val MenuReveal = 6f

    /** Números do contador da cafeína a rolar: desfoque 6 no início e no fim do movimento. */
    const val CounterRoll = 6f
}
