package com.gaebriu.cyclo.ui.settings

import com.gaebriu.cyclo.ui.components.addSmoothRoundRect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloButtonSize
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType

/** O que o new-update-group mostra (vem de [com.gaebriu.cyclo.update.CycloUpdates]). */
class UpdateUi(
    /** A tag da release (identifica o aviso: outra tag é outro aviso). */
    val tag: String,
    /** A versão nova como se mostra ("Beta 1.0"). */
    val versionLabel: String,
    /** A release tem um .apk ("Download on GitHub"); senão o botão diz "See what's new". */
    val hasApk: Boolean,
)

// Medidas do Figma: new-update-group 345 x 147 (nó 56:252).
private val GroupPadding = 15.dp
private val TitleToSubtitle = 9.dp

/** Do subtítulo ao botão roxo (56:252). */
private val SubtitleToButton = 24.dp
private val CloseGap = 9.dp

/** "fx blur type 01": a mancha roxa (#4A3C99 a 24 %) por baixo do vidro, no topo do cartão. */
private val HazeColor = Color(0x3D4A3C99)

/**
 * new-update-group: "Cyclo 2.0 is here", o subtítulo e o botão roxo, com o close-b em cima à direita
 * (dispensa o aviso durante 12 horas).
 *
 * Desde o Cyclo 2.0 o botão abre a página da release no GitHub ("Download on GitHub"), onde se
 * descarrega o .apk: a app já não o descarrega nem o instala (ver
 * [com.gaebriu.cyclo.update.CycloUpdates]). O estado "a descarregar" (Figma 244:209) saiu com isso.
 * Se a página não abrir, o [com.gaebriu.cyclo.ui.CycloApp] diz na barra de baixo (undo-action).
 */
@Composable
fun UpdateGroup(
    update: UpdateUi,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.update_title, update.versionLabel)
    val subtitle = stringResource(R.string.update_subtitle)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // A sombra dos cartões (0, 4, desfoque 39, preto a 24 %; Cyclo-2 55:203, beta-3.0).
            .cycloCard(
                underFill = {
                    // fx blur type 01: 315 x 33 a 15 dp da esquerda, no topo, muito desfocado.
                    val haze = Path().apply {
                        addSmoothRoundRect(GroupPadding.toPx(), 0f, size.width - GroupPadding.toPx(), 33.dp.toPx(), 16.dp.toPx())
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
            Spacer(Modifier.height(SubtitleToButton))
            Box(modifier = Modifier.fillMaxWidth().height(CycloDimens.InputHeight)) {
                CycloPrimaryButton(
                    text = stringResource(if (update.hasApk) R.string.update_download else R.string.update_view_release),
                    onClick = onOpen,
                    size = CycloButtonSize.Compact,
                )
            }
        }
        // close-b: 39 dp a 15 dp do topo e da direita.
        CycloCircleButton(
            iconPath = CycloIconPaths.Close,
            contentDescription = stringResource(R.string.update_dismiss),
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = GroupPadding, end = GroupPadding)
                .offset(
                    x = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2,
                    y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2,
                ),
        )
    }
}
