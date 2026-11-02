package com.gaebriu.cyclo.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.gaebriu.cyclo.R

val Inter = FontFamily(
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

object CycloType {
    /** "header-big": Inter Bold 39. */
    val HeaderBig = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 39.sp,
        color = CycloColors.TextPrimary,
    )

    /** "header-medium": Inter Bold 24, centrado. */
    val HeaderMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = CycloColors.TextPrimary,
        textAlign = TextAlign.Center,
    )

    /** Texto corrido: Inter Medium 15, cinzento, centrado. */
    val Body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = CycloColors.TextSecondary,
        textAlign = TextAlign.Center,
    )

    /** Números das caixas de hora e minuto: Inter SemiBold 24. */
    val Picker = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        color = CycloColors.TextPrimary,
        textAlign = TextAlign.Center,
    )

    /** Os dois pontos entre a hora e os minutos: Inter Medium 24. */
    val PickerColon = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        color = CycloColors.TextPrimary,
        textAlign = TextAlign.Center,
    )

    /** Texto escrito na caixa do nome: Inter SemiBold 15, branco. */
    val Input = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CycloColors.TextPrimary,
    )

    /** "Tap to write" e "Listening...": Inter SemiBold 15, cinzento. */
    val InputHint = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CycloColors.TextSecondary,
    )

    /** Opção de um menu: Inter Medium 15, branco, alinhado à esquerda. */
    val MenuItem = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = CycloColors.TextPrimary,
    )

    /** Texto pequeno por baixo de uma opção de menu: Inter Medium 9, cinzento. */
    val MenuHint = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        color = CycloColors.TextSecondary,
    )

    /** Título de uma folha ("Choose days"): Inter SemiBold 15, branco, centrado. */
    val SheetTitle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CycloColors.TextPrimary,
        textAlign = TextAlign.Center,
    )

    /** Botão de texto à direita da barra de baixo ("Undo", undo-b): Inter Medium 15, cinzento. */
    val MenuAction = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = CycloColors.TextSecondary,
    )

    /** Saudação do Feed ("Good afternoon", header-greetings): Inter Bold 24, branco, à esquerda. */
    val Greeting = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = CycloColors.TextPrimary,
    )

    /** Data por baixo da saudação: Inter SemiBold 15, cinzento. */
    val GreetingDate = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CycloColors.TextSecondary,
    )

    /** Título de cada grupo do Feed ("To rise refreshed tomorrow:"): Inter Medium 15, cinzento, à esquerda. */
    val SectionLabel = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = CycloColors.TextSecondary,
    )

    /** Valores dentro dos cartões ("06:00", "Show just this once"): Inter Medium 15, branco. */
    val CardValue = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = CycloColors.TextPrimary,
    )

    /** Nomes e números dos cartões ("Routine 1", "08:45", "Intense exercise"): Inter SemiBold 15, branco. */
    val CardTitle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CycloColors.TextPrimary,
    )

    /** Texto dos botões: Inter SemiBold 15. */
    val Button = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = CycloColors.TextPrimary,
        textAlign = TextAlign.Center,
    )
}
