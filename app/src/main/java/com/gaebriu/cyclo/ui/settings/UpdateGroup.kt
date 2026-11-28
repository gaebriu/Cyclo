package com.gaebriu.cyclo.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.main.PromptCard
import com.gaebriu.cyclo.ui.main.PromptChrome
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

/**
 * new-update-group (Componentes (Prompts) 6031:80, 345 x 147): "Cyclo 2.0 is here", o subtítulo
 * numa linha, 24 dp, o botão roxo e o close-b; o "fx blur type 01" roxo (#4A3C99 a 24 %). Desde a
 * Cyclo 3.2.3 é um [PromptCard] (o mesmo cartão dos outros prompts) e partilha o lugar com o
 * keep-your-progress-safe; o X esconde-o até a app voltar a abrir.
 *
 * Desde o Cyclo 2.0 o botão abre a página da release no GitHub ("Download on GitHub"), onde se
 * descarrega o .apk: a app já não o descarrega nem o instala (ver
 * [com.gaebriu.cyclo.update.CycloUpdates]). Se a página não abrir, a barra de baixo diz.
 */
@Composable
internal fun UpdateGroup(
    update: UpdateUi,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    chrome: PromptChrome,
    modifier: Modifier = Modifier,
) {
    PromptCard(
        title = stringResource(R.string.update_title, update.versionLabel),
        text = stringResource(R.string.update_subtitle),
        buttonText = stringResource(if (update.hasApk) R.string.update_download else R.string.update_view_release),
        onButton = onOpen,
        closeDescription = stringResource(R.string.update_dismiss),
        onClose = onDismiss,
        chrome = chrome,
        textStyle = CycloType.SectionLabel,
        textToButton = 24.dp,
        minHeight = 147.dp,
        textIsLive = true,
        modifier = modifier,
    )
}
