package com.gaebriu.cyclo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloType

/**
 * Folha de confirmação (action-menu-big, ex.: action-menu-big-open-github do Componentes 3,
 * 345 x 219 dp): pega, título, um ou dois textos centrados e o botão roxo de 48 dp. Todos os
 * espaços são de 15 dp, como na folha dos dias.
 */
@Composable
fun CycloBigSheet(
    title: String,
    texts: List<String>,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cycloOverlaySurface()
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        // Pega para arrastar a folha para baixo (swipe-to-close-b).
        Box(
            modifier = Modifier
                .size(width = 39.dp, height = 2.dp)
                .background(CycloColors.TextPrimary.copy(alpha = 0.15f), RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.height(15.dp))
        BasicText(text = title, style = CycloType.SheetTitle, modifier = Modifier.semantics { heading() })
        texts.forEach { text ->
            Spacer(Modifier.height(15.dp))
            BasicText(
                text = text,
                style = CycloType.Body.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(15.dp))
        CycloPrimaryButton(text = buttonText, onClick = onButtonClick, size = CycloButtonSize.Compact)
        Spacer(Modifier.height(15.dp))
    }
}
