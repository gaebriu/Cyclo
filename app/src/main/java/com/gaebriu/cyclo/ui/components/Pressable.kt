package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.launch

/**
 * O toque de todos os botões da Cyclo, num só sítio:
 * - gel: enquanto está carregado, o botão comprime-se (mais na altura do que na largura, como
 *   gelatina carregada por cima) e, ao largar, recupera com um ressalto controlado. Cada eixo tem a
 *   sua mola ([CycloSprings.GelPressX] / [CycloSprings.GelPressY] e [CycloSprings.GelReleaseX] /
 *   [CycloSprings.GelReleaseY]), ligeiramente desfasadas: é isso que dá a sensação de material.
 *   A compressão tem um máximo em dp ([MaxShrinkX], [MaxShrinkY]): um botão da largura do ecrã não
 *   abana 14 dp. O centro da deformação puxa um pouco para o sítio do dedo (resposta espacial).
 *   Um toque rápido (carregar e largar no mesmo frame) parte com um impulso, para se ver na mesma;
 * - háptico suave ao clicar ([CycloHaptics.buttonTap]), a menos que [haptic] seja falso
 *   (ex.: botões que mudam o modo de ditado, que por decisão não têm háptico).
 *
 * Sem animações no sistema, não há deformação nenhuma.
 *
 * A área de toque não encolhe (só o que está dentro): por isso o dedo não "cai" do botão.
 * Tudo o que o botão desenha tem de vir DEPOIS deste modificador.
 *
 * @param pressedScale a escala carregada (o quanto comprime), antes do máximo em dp.
 */
@Composable
fun Modifier.cycloPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressedScale: Float = CycloMotion.ButtonPressScale,
    role: Role? = Role.Button,
): Modifier {
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    val interactionSource = remember { MutableInteractionSource() }
    // 0 = em repouso, 1 = carregado; abaixo de 0 = o ressalto ao largar (passa da forma original).
    val pressX = remember { Animatable(0f) }
    val pressY = remember { Animatable(0f) }
    // Onde o dedo tocou (fração do tamanho), guardado até ao fim do ressalto.
    var origin by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    // O tamanho do botão (do último desenho), sem ser estado: escrevê-lo não volta a desenhar nada.
    val lastSize = remember { SizeHolder() }

    LaunchedEffect(interactionSource, effects.animationsEnabled, enabled) {
        if (!effects.animationsEnabled || !enabled) {
            pressX.snapTo(0f)
            pressY.snapTo(0f)
            return@LaunchedEffect
        }
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    val size = lastSize.value
                    if (size.width > 0f && size.height > 0f) {
                        val fx = (interaction.pressPosition.x / size.width).coerceIn(0f, 1f)
                        val fy = (interaction.pressPosition.y / size.height).coerceIn(0f, 1f)
                        origin = Offset(0.5f + (fx - 0.5f) * OriginPull, 0.5f + (fy - 0.5f) * OriginPull)
                    }
                    launch { pressX.animateTo(1f, CycloSprings.GelPressX.float) }
                    launch { pressY.animateTo(1f, CycloSprings.GelPressY.float) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    // Se ainda não comprimiu (toque rápido), um impulso leva-o a comprimir antes de voltar.
                    val impulseX = (1f - pressX.value).coerceAtLeast(0f) * QuickTapImpulse
                    val impulseY = (1f - pressY.value).coerceAtLeast(0f) * QuickTapImpulse
                    launch { pressX.animateTo(0f, CycloSprings.GelReleaseX.float, initialVelocity = impulseX) }
                    launch { pressY.animateTo(0f, CycloSprings.GelReleaseY.float, initialVelocity = impulseY) }
                }
            }
        }
    }

    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                if (haptic) CycloHaptics.buttonTap(view)
                onClick()
            },
        )
        .graphicsLayer {
            lastSize.value = size
            val px = pressX.value
            val py = pressY.value
            if (px == 0f && py == 0f) return@graphicsLayer
            if (size.width <= 0f || size.height <= 0f) return@graphicsLayer
            // Quanto encolhe cada eixo (em píxeis), com o máximo em dp.
            val amount = 1f - pressedScale
            val shrinkX = (amount * size.width).coerceAtMost(MaxShrinkX.toPx())
            val shrinkY = (amount * size.height * HeightBias).coerceAtMost(MaxShrinkY.toPx())
            scaleX = 1f - px * shrinkX / size.width
            scaleY = 1f - py * shrinkY / size.height
            transformOrigin = TransformOrigin(origin.x, origin.y)
        }
}

/** Máximo que um botão encolhe na largura (o Next tem 345 dp: sem isto, 14 dp). */
private val MaxShrinkX = 8.dp

/** Máximo que um botão encolhe na altura. */
private val MaxShrinkY = 5.dp

/** A altura comprime um pouco mais do que a largura: carregado por cima, o gel achata. */
private const val HeightBias = 1.25f

/** Quanto o centro da deformação se aproxima do dedo (0 = centro, 1 = o próprio dedo). */
private const val OriginPull = 0.35f

/** Velocidade (fração por segundo) que um toque rápido dá ao gel, para comprimir antes de voltar. */
private const val QuickTapImpulse = 9f

/** Guarda o tamanho medido no desenho, fora do sistema de estado. */
private class SizeHolder {
    var value = androidx.compose.ui.geometry.Size.Zero
}
