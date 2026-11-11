package com.gaebriu.cyclo.ui.main

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * Até onde se vê a sombra dos cartões (0, 4, desfoque 39 a 24 %, ou 24 no wake-time-container): a
 * camada da entrada em cascata (fade + desfoque) desenha fora do ecrã e corta tudo o que sai dos seus
 * limites, por isso alarga-se este tanto à volta de um bloco com cartões, e a sombra entra com ele
 * desde o início (antes aparecia de repente no fim da entrada). Para lá disto a sombra fica abaixo de
 * 1 % (sigma 19,5 e 4 de deslocamento).
 */
internal val CardShadowBleed = 44.dp

/**
 * Dá [amount] a mais de cada lado aos modificadores que vêm a seguir (ex.: a camada da entrada),
 * sem mudar o lugar que o bloco ocupa. Quem vem depois devolve o espaço com `padding(amount)`.
 */
internal fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val px = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(px * 2, px * 2))
    layout(placeable.width - px * 2, placeable.height - px * 2) { placeable.place(-px, -px) }
}
