package com.gaebriu.cyclo.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloButtonSize
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.update.DownloadState
import kotlin.math.roundToInt

/** O que o new-update-group mostra (vem de [com.gaebriu.cyclo.update.CycloUpdates]). */
class UpdateUi(
    /** A versão nova como se mostra ("Beta 1.0"). */
    val versionLabel: String,
    val download: DownloadState,
    /** A release tem um .apk (senão, "Download now" abre a página no GitHub). */
    val canDownload: Boolean,
)

// Medidas do Figma: new-update-group 345 x 147 (nó 56:252) e, a descarregar, 345 x 126 (nó 244:209).
private val GroupPadding = 15.dp
private val TitleToSubtitle = 9.dp

/** Do subtítulo ao botão roxo (56:252) e ao download-bar / cancel-download-b (244:209). */
private val SubtitleToButton = 24.dp
private val SubtitleToBar = 12.dp

/** download-bar: 15 dp de altura; o primeiro plano e o fundo separados por 6 dp (244:290 / 244:215). */
private val BarHeight = 15.dp
private val BarGap = 6.dp

/** Da barra ao cancel-download-b (300 → 315 no Figma). */
private val BarToCancel = 15.dp

/** download-bar > background: #A3A2A7 a 15 %. */
private val BarTrack = CycloColors.TextSecondary.copy(alpha = 0.15f)
private val CloseGap = 9.dp

/** "fx blur type 01": a mancha roxa (#4A3C99 a 24 %) por baixo do vidro, no topo do cartão. */
private val HazeColor = Color(0x3D4A3C99)

/**
 * new-update-group: "Cyclo Beta 1.0 is here", o subtítulo e o botão roxo "Download now", com o
 * close-b em cima à direita (dispensa o aviso durante 24 horas).
 *
 * A descarregar (Figma 244:209): "Downloading...", a download-bar (o primeiro plano roxo com a
 * sombra interior vai até ao progresso REAL; o fundo cinzento ocupa o resto, a 6 dp) e o
 * cancel-download-b. Sem close-b, como no Figma. O botão e a barra trocam com uma mola
 * ([CycloSprings.Enter]) que também ajusta a altura do grupo (147 → 126 dp).
 *
 * Os erros não ficam aqui: o [com.gaebriu.cyclo.ui.CycloApp] mostra-os na barra de baixo
 * (undo-action) com "Try again". Descarregado, o instalador do Android abre logo; se o utilizador
 * sair dele sem instalar, fica o botão "Install update".
 */
@Composable
fun UpdateGroup(
    update: UpdateUi,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val download = update.download
    val running = download is DownloadState.Running

    // 0 = o botão, 1 = a barra com o X ao lado.
    val barMode = remember { Animatable(if (running) 1f else 0f) }
    LaunchedEffect(running) {
        val target = if (running) 1f else 0f
        if (effects.animationsEnabled) barMode.animateTo(target, CycloSprings.Enter.float) else barMode.snapTo(target)
    }
    // Só mudam no início e no fim da troca (não recompõe a cada frame da mola).
    val showButton by remember { derivedStateOf { barMode.value < 0.999f } }
    val showBar by remember { derivedStateOf { barMode.value > 0.001f } }
    // O progresso mostrado segue o real com uma mola sem ressalto (nunca o ultrapassa).
    val fraction = (download as? DownloadState.Running)?.fraction
    val shown = remember { Animatable(0f) }
    LaunchedEffect(fraction, running) {
        val target = fraction ?: 0f
        when {
            !running -> if (download is DownloadState.Done) shown.snapTo(1f) else shown.snapTo(0f)
            effects.animationsEnabled -> shown.animateTo(target, CycloSprings.Gentle.float)
            else -> shown.snapTo(target)
        }
    }
    val latestDownload by rememberUpdatedState(download)
    LaunchedEffect(download::class) {
        if (latestDownload is DownloadState.Done) CycloHaptics.confirm(view)
    }

    val title = stringResource(R.string.update_title, update.versionLabel)
    val subtitle = when (download) {
        is DownloadState.Running -> stringResource(R.string.update_downloading)
        is DownloadState.Done -> stringResource(R.string.update_ready)
        else -> stringResource(R.string.update_subtitle)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .cycloCard(
                shadowColor = null,
                underFill = {
                    // fx blur type 01: 315 x 33 a 15 dp da esquerda, no topo, muito desfocado.
                    val haze = Path().apply {
                        addRoundRect(RoundRect(GroupPadding.toPx(), 0f, size.width - GroupPadding.toPx(), 33.dp.toPx(), CornerRadius(16.dp.toPx())))
                    }
                    drawBlurredPath(haze, HazeColor, 39.dp.toPx())
                },
            ),
    ) {
        Column(modifier = Modifier.padding(GroupPadding)) {
            // O título e o subtítulo param antes do close-b.
            Column(modifier = Modifier.padding(end = CycloDimens.CircleButtonSize + CloseGap).semantics(mergeDescendants = true) {}) {
                BasicText(text = title, style = CycloType.CardTitle)
                Spacer(Modifier.height(TitleToSubtitle))
                BasicText(
                    text = subtitle,
                    style = CycloType.SectionLabel,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            // A distância e a altura da ação acompanham a troca botão ↔ barra (147 → 126 dp).
            Spacer(Modifier.lerpHeight(SubtitleToButton, SubtitleToBar) { barMode.value })
            Box(modifier = Modifier.fillMaxWidth().lerpHeight(CycloDimens.InputHeight, CycloDimens.CircleButtonSize) { barMode.value }) {
                if (showButton) {
                    val primary = when (download) {
                        is DownloadState.Done -> stringResource(R.string.update_install) to onInstall
                        else -> stringResource(if (update.canDownload) R.string.update_download else R.string.update_view_release) to onDownload
                    }
                    CycloPrimaryButton(
                        text = primary.first,
                        onClick = primary.second,
                        enabled = !running,
                        size = CycloButtonSize.Compact,
                        modifier = Modifier.graphicsLayer {
                            val m = barMode.value.coerceIn(0f, 1f)
                            alpha = 1f - m
                            val s = lerp(1f, 0.96f, m)
                            scaleX = s
                            scaleY = s
                        },
                    )
                }
                if (showBar || running) {
                    val percent = fraction?.let { (it * 100).roundToInt() }
                    DownloadBar(
                        label = if (percent != null) stringResource(R.string.update_downloading_percent, percent) else subtitle,
                        fraction = fraction,
                        progress = { shown.value },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = CycloDimens.CircleButtonSize + BarToCancel)
                            .graphicsLayer { alpha = barMode.value.coerceIn(0f, 1f) },
                    )
                    // cancel-download-b: 39 dp, alinhado ao centro da barra.
                    CycloCircleButton(
                        iconPath = CycloIconPaths.Close,
                        contentDescription = stringResource(R.string.update_cancel),
                        onClick = onCancel,
                        enabled = running,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2)
                            .graphicsLayer {
                                val m = barMode.value.coerceIn(0f, 1f)
                                alpha = m
                                val s = lerp(0.6f, 1f, barMode.value)
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            }
        }
        // close-b: 39 dp a 15 dp do topo e da direita. Sai durante o download (244:209 não o tem).
        if (showButton) {
            CycloCircleButton(
                iconPath = CycloIconPaths.Close,
                contentDescription = stringResource(R.string.update_dismiss),
                onClick = onDismiss,
                enabled = !running,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = GroupPadding, end = GroupPadding)
                    .offset(
                        x = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2,
                        y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2,
                    )
                    .graphicsLayer { alpha = 1f - barMode.value.coerceIn(0f, 1f) },
            )
        }
    }
}

/** Uma altura entre [from] (t = 0) e [to] (t = 1), lida na medição (sem recompor a cada frame). */
private fun Modifier.lerpHeight(from: Dp, to: Dp, t: () -> Float): Modifier = layout { measurable, constraints ->
    val h = lerp(from.toPx(), to.toPx(), t().coerceIn(0f, 1.1f)).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}

/**
 * download-bar (Figma 244:214): 15 dp de altura, cantos de 15 dp. O primeiro plano (#4A3C99 com a
 * sombra interior 0/6/15 a 15 %) vai até ao progresso; o fundo (#A3A2A7 a 15 %) começa 6 dp depois
 * e ocupa o resto. No início (sem primeiro plano) o fundo ocupa a barra toda.
 */
@Composable
private fun DownloadBar(label: String, fraction: Float?, progress: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = if (fraction != null) ProgressBarRangeInfo(fraction, 0f..1f) else ProgressBarRangeInfo.Indeterminate
            }
            .drawBehind {
                val radius = CornerRadius(size.height)
                val p = progress().coerceIn(0f, 1f)
                // O espaço entre os dois só aparece quando o primeiro plano já tem a largura de um círculo.
                val gap = BarGap.toPx() * (p * size.width / size.height).coerceIn(0f, 1f) * (1f - ((p - 0.98f) / 0.02f).coerceIn(0f, 1f))
                val usable = size.width - gap
                val fg = usable * p
                val trackStart = fg + gap
                if (size.width - trackStart > 0.5f) {
                    drawRoundRect(BarTrack, topLeft = Offset(trackStart, 0f), size = androidx.compose.ui.geometry.Size(size.width - trackStart, size.height), cornerRadius = radius)
                }
                if (fg > 0.5f) {
                    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, fg, size.height, radius)) }
                    drawPath(shape, CycloColors.Accent)
                    drawInnerShadow(shape, Color.Black.copy(alpha = 0.15f), Offset(0f, 6.dp.toPx()), 15.dp.toPx())
                }
            },
    )
}
