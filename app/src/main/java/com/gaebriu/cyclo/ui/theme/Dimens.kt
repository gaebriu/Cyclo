package com.gaebriu.cyclo.ui.theme

import androidx.compose.ui.unit.dp

object CycloDimens {
    val ScreenMargin = 24.dp
    val CornerRadius = 24.dp
    val Spacing = 15.dp
    val ButtonHeight = 66.dp

    /** Altura da barra de navegação do frame do Figma; é o mínimo que reservamos em baixo. */
    val MinBottomInset = 48.dp

    /** Botão redondo (X / seta): o círculo tem 39 dp, mas a zona de toque tem 48 dp. */
    val CircleButtonSize = 39.dp
    val TouchTarget = 48.dp

    val ProgressBarHeight = 24.dp

    /** Caixas de texto (tap-to-write-box). */
    val InputHeight = 48.dp
    val InputRadius = 15.dp

    /** Distância do topo do ecrã ao botão X / seta no frame do Figma. */
    val TopBarTop = 48.dp

    /** Altura do fade nas extremidades do texto com scroll ("fx progressive-blur-bottom", 120 dp). */
    val ScrollEdgeFade = 120.dp
}
