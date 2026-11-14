package com.gaebriu.cyclo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import com.gaebriu.cyclo.ui.theme.CycloSprings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.CycloProgressRing
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloType
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos

/** Do X ao anel de progresso (Cyclo 3: close-b a 48, progress-bar a 102). */
private val CloseToRing = 15.dp

/** Do anel ao topo do ícone com holofote (o icon-header a 150). */
private val RingToContent = 9.dp

/** O topo do X: 24 dp abaixo da barra de estado (48 no Figma, que tem 24 dp de barra). */
@Composable
private fun closeTop(): Dp {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return maxOf(CycloDimens.TopBarTop, statusTop + 24.dp)
}

/** Onde começa o conteúdo das páginas (o topo do icon-header): por baixo do X e do anel. */
@Composable
fun introContentTop(): Dp = closeTop() + CycloDimens.CircleButtonSize + CloseToRing + CycloDimens.CircleButtonSize + RingToContent

/**
 * Parte de cima fixa das Introduction (Cyclo 3, beta-3.2): o X (close-b) e, 15 dp por baixo, o anel
 * de progresso (progress-bar, 39 dp). Não se mexem quando as páginas sobem: só o anel enche.
 *
 * @param closeAlpha opacidade do X (o Introduction 7 fá-lo sair com um fade).
 * @param closeEnabled falso: o X não responde nem aparece ao TalkBack.
 * @param ringAlpha opacidade do anel (sai depois de chegar aos 100 %, no Introduction 7).
 */
@Composable
fun IntroTopBar(
    progress: () -> Float,
    progressDescription: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeAlpha: () -> Float = { 1f },
    closeEnabled: Boolean = true,
    ringAlpha: () -> Float = { 1f },
) {
    // O círculo tem 39 dp mas a zona de toque 48 dp: compensamos os 4,5 dp de cada lado.
    val outset = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2

    Column(modifier = modifier) {
        Spacer(Modifier.height(closeTop() - outset))
        CycloCircleButton(
            iconPath = CycloIconPaths.Close,
            contentDescription = stringResource(R.string.close),
            onClick = onClose,
            enabled = closeEnabled,
            modifier = Modifier
                .offset(x = -outset)
                // ModulateAlpha: a sombra de 39 do close-b sai do botão; uma camada fora do ecrã cortava-a.
                .graphicsLayer {
                    alpha = closeAlpha()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .then(if (closeEnabled) Modifier else Modifier.clearAndSetSemantics { }),
        )
        Spacer(Modifier.height(CloseToRing - outset))
        CycloProgressRing(
            progress = progress,
            description = progressDescription,
            modifier = Modifier.graphicsLayer {
                alpha = ringAlpha()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        )
    }
}

/** O ciclo do indicador (s): sobe, volta e descansa (como o "swipe up" do iOS, sem ser um metrónomo). */
private const val HintCycleS = 2.4f

/** Quanto o texto sobe no ponto mais alto; as setas sobem mais e vão um nadinha à frente (puxam-no). */
private val HintTextRise = 3.dp
private val HintArrowRise = 5.dp
private const val HintArrowLead = 0.035f

/**
 * A curva do indicador num ciclo ([phase] de 0 a 1): sobe depressa e a abrandar (35 %), volta mais
 * devagar (até 80 %) e fica parado no resto. De 0 (em baixo) a 1 (no ponto mais alto).
 */
internal fun hintLift(phase: Float): Float {
    val p = phase - kotlin.math.floor(phase)
    return when {
        p < 0.35f -> {
            val t = p / 0.35f
            1f - (1f - t) * (1f - t) * (1f - t)
        }
        p < 0.8f -> {
            val t = (p - 0.35f) / 0.45f
            (1f + cos(PI.toFloat() * t)) / 2f
        }
        else -> 0f
    }
}

/**
 * O indicador do gesto (swipe-to-get-started / swipe-to-continue, Cyclo 3): o texto com uma seta
 * para cima de cada lado, a 6 dp. Não é um botão desenhado, mas responde como um (TalkBack e quem
 * prefere tocar): tocar avança, como deslizar.
 *
 * O movimento (beta-3.2): sobe e volta, devagar, e descansa; as setas vão um pouco à frente e mais
 * alto, e acendem no topo. Corre num relógio próprio só enquanto se vê e a app está à frente, lido
 * só na camada (nada se recompõe). Sem animações, fica parado.
 *
 * @param alpha a opacidade (quem o mostra liga-a ao assentar das páginas).
 * @param idle o utilizador está parado. Ao interagir, o movimento acalma logo (desce ao repouso numa
 *        mola rápida) e só volta quando [idle] voltar a ser verdade; o texto fica sempre à vista.
 */
@Composable
fun SwipeHint(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    enabled: Boolean = true,
    idle: () -> Boolean = { true },
) {
    val effects = LocalVisualEffects.current
    val phase = remember { mutableFloatStateOf(0f) }
    // Quanto do movimento se vê: 1 parado, 0 enquanto se interage.
    val amplitude = remember { Animatable(1f) }
    val isIdle by remember { derivedStateOf { idle() } }
    LaunchedEffect(isIdle) {
        if (isIdle) amplitude.animateTo(1f, CycloSprings.Gentle.float) else amplitude.animateTo(0f, CycloSprings.Exit.float)
    }
    val latestAlpha by rememberUpdatedState(alpha)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(effects.animationsEnabled, lifecycle) {
        if (!effects.animationsEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var start = -1L
            while (true) {
                // Escondido: o relógio pára (e recomeça do início do ciclo quando volta).
                if (latestAlpha() <= 0.01f) {
                    snapshotFlow { latestAlpha() > 0.01f }.first { it }
                    start = -1L
                }
                withFrameNanos { now ->
                    if (start < 0L) start = now
                    phase.floatValue = ((now - start) / 1e9f) / HintCycleS
                }
            }
        }
    }

    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
            .heightIn(min = CycloDimens.TouchTarget)
            .cycloPressable(onClick = onClick, enabled = enabled)
            .padding(horizontal = CycloDimens.ScreenMargin)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val arrow = Modifier.graphicsLayer {
            val lift = hintLift(phase.floatValue + HintArrowLead) * amplitude.value
            translationY = -lift * HintArrowRise.toPx()
            this.alpha = 0.63f + 0.37f * lift
        }
        CycloIcon(path = CycloAppIconPaths.ArrowUpwardAlt, size = 15.dp, tint = CycloColors.TextPrimary, modifier = arrow)
        // Com letra grande o texto parte em linhas, centradas, e as setas ficam sempre dos dois lados.
        BasicText(
            text = text,
            style = CycloType.Button,
            modifier = Modifier
                .weight(1f, fill = false)
                .graphicsLayer { translationY = -hintLift(phase.floatValue) * amplitude.value * HintTextRise.toPx() },
        )
        CycloIcon(path = CycloAppIconPaths.ArrowUpwardAlt, size = 15.dp, tint = CycloColors.TextPrimary, modifier = arrow)
    }
}
