package com.gaebriu.cyclo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Layout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.FigmaBlur
import com.gaebriu.cyclo.ui.theme.figmaBlurPx

/**
 * Número que rola ao mudar (os contadores da cafeína e da água). Beta 3.1: cada algarismo é
 * independente, guardado pela sua casa (unidades, dezenas...), e só os que mudam rolam, da direita
 * para a esquerda ([CycloMotion.CounterDigitStaggerMs] entre um e o seguinte): de 9 para 10, o "0"
 * entra primeiro e o "1" logo a seguir. Ao somar, o novo sobe de baixo e o antigo sai por cima; ao
 * subtrair, ao contrário, com fade, um ligeiro zoom e desfoque.
 *
 * Cada algarismo tem uma só posição contínua ([Glyph.offset]): um toque a meio inverte-o a partir
 * de onde está, com a velocidade que tem, sem saltos. A largura de cada casa também é animada
 * ([CycloMotion.CounterWidthSpring]): um algarismo novo abre o seu espaço e o "−" ao lado vai atrás
 * da largura real, em vez de saltar.
 *
 * Os algarismos têm todos a mesma largura ("tnum"). Sem animações, só fade e larguras de imediato;
 * sem desfoque, sem desfoque.
 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val effects = LocalVisualEffects.current
    val tabular = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val scope = rememberCoroutineScope()
    val counter = remember { DigitCounter(value) }

    LaunchedEffect(value) { counter.show(value, scope, effects.animationsEnabled) }

    // O que se desenha, lido na composição e usado igual na medição (a mesma ordem dos filhos).
    val items = counter.places.flatMapIndexed { place, slot -> slot.glyphs.map { place to it } }
    Layout(
        modifier = modifier.semantics { contentDescription = value.toString() },
        content = {
            items.forEach { (_, glyph) ->
                key(glyph) {
                    BasicText(
                        text = glyph.char.toString(),
                        style = tabular,
                        modifier = Modifier
                            .clearAndSetSemantics { }
                            .graphicsLayer { glyphLayer(this, glyph.offset.value, effects.animationsEnabled, effects.blurEnabled) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        placeables.firstOrNull()?.let { counter.digitWidth = it.width; counter.digitHeight = it.height }
        val digit = counter.digitWidth.toFloat()
        // Largura de cada casa, da direita para a esquerda (a presença anima de 0 a 1; lida aqui,
        // na medição, para a fila e o "−" seguirem a largura real a cada fotograma).
        val widths = counter.places.map { it.presence.value.coerceAtLeast(0f) * digit }
        val width = widths.sum()
        layout(width.roundToInt(), placeables.maxOfOrNull { it.height } ?: counter.digitHeight) {
            placeables.forEachIndexed { i, p ->
                val place = items[i].first
                // Encostado à direita da sua casa: ao abrir espaço, a casa cresce para a esquerda.
                var right = width
                for (k in 0 until place) right -= widths[k]
                p.place((right - digit).roundToInt(), 0)
            }
        }
    }
}

/** Um algarismo desenhado: 0 = no lugar, ±1 = uma distância de rolagem abaixo (+) ou acima (−). */
private class Glyph(val char: Char, start: Float) {
    val offset = Animatable(start)
}

/** Uma casa (unidades, dezenas...): o algarismo que fica ([active]) e os que ainda estão a sair. */
private class DigitPlace(initial: Char?) {
    val glyphs = mutableStateListOf<Glyph>()
    var active: Glyph? = initial?.let { Glyph(it, 0f) }?.also { glyphs += it }
    val presence = Animatable(if (initial != null) 1f else 0f)
    var pending: Job? = null
}

private class DigitCounter(initial: Int) {
    val places = mutableStateListOf<DigitPlace>()
    private var value = initial
    var digitWidth = 0
    var digitHeight = 0

    init {
        initial.toString().reversed().forEach { places += DigitPlace(it) }
    }

    /**
     * Passa a mostrar [target]: cada casa que muda espera a sua vez (da direita para a esquerda) e
     * rola. Um toque novo cancela só as esperas; o que já está a rolar continua de onde está.
     */
    fun show(target: Int, scope: CoroutineScope, animate: Boolean) {
        if (target == value) return
        val direction = if (target > value) 1f else -1f
        value = target
        val digits = target.toString().reversed()
        while (places.size < digits.length) places += DigitPlace(null)
        var order = 0
        places.forEachIndexed { index, place ->
            val want = digits.getOrNull(index)
            place.pending?.cancel()
            if (place.active?.char == want) return@forEachIndexed
            val wait = if (animate) order++ * CycloMotion.CounterDigitStaggerMs else 0L
            place.pending = scope.launch {
                if (wait > 0) delay(wait)
                place.change(want, direction, scope, animate)
            }
        }
    }

    private fun DigitPlace.change(want: Char?, direction: Float, scope: CoroutineScope, animate: Boolean) {
        // O que estava (mesmo a meio de entrar) sai daqui, com a velocidade que tem.
        active?.let { old ->
            scope.launch {
                old.offset.animateTo(-direction, CycloMotion.CounterSpring)
                glyphs.remove(old)
            }
        }
        active = want?.let { Glyph(it, direction) }?.also { glyph ->
            glyphs += glyph
            scope.launch { glyph.offset.animateTo(0f, CycloMotion.CounterSpring) }
        }
        scope.launch {
            val to = if (want != null) 1f else 0f
            if (animate) presence.animateTo(to, CycloMotion.CounterWidthSpring) else presence.snapTo(to)
        }
    }
}

/**
 * Um algarismo à distância [offset] do seu lugar: desloca-se [CycloMotion.CounterRollDistance] da
 * altura por unidade, com fade, zoom até 82 % e desfoque. A opacidade depende só da distância (igual
 * para quem entra e quem sai), para um algarismo que muda de sentido a meio não piscar.
 */
private fun glyphLayer(scope: GraphicsLayerScope, offset: Float, animationsEnabled: Boolean, blurEnabled: Boolean) = with(scope) {
    val away = abs(offset).coerceIn(0f, 1f)
    alpha = (1f - away * GlyphFade).coerceIn(0f, 1f)
    if (!animationsEnabled) return@with
    translationY = offset * size.height * CycloMotion.CounterRollDistance
    val scale = lerp(1f, 0.82f, away)
    scaleX = scale
    scaleY = scale
    val sigma = figmaBlurPx(FigmaBlur.CounterRoll) * away
    renderEffect = if (blurEnabled && sigma > 0.5f) BlurEffect(sigma, sigma, TileMode.Decal) else null
}

/** Quão depressa um algarismo desaparece com a distância (some a 77 % do caminho). */
private const val GlyphFade = 1.3f

/**
 * Texto que rola ao mudar, com o mesmo movimento do [RollingNumber] (as horas para dormir do Feed,
 * quando a cafeína, os fatores ou as emoções as mudam). [direction] 1: o novo sobe de baixo; -1: desce
 * de cima. [description] é o que o TalkBack lê.
 *
 * Algarismos proporcionais e texto centrado, como no Figma (as horas têm larguras diferentes): com
 * "tnum" os "1" ficavam com espaço à volta e o texto visível saía do centro, debaixo do ícone. O
 * texto que sai não conta para a largura (sai centrado por cima do lugar), para a fila não saltar
 * quando ele desaparece no fim da animação.
 */
@Composable
fun RollingText(text: String, style: TextStyle, direction: Int, modifier: Modifier = Modifier, description: String = text) {
    val effects = LocalVisualEffects.current
    val centered = remember(style) { style.copy(textAlign = TextAlign.Center) }
    var current by remember { mutableStateOf(text) }
    var previous by remember { mutableStateOf<String?>(null) }
    var dir by remember { mutableIntStateOf(1) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(text) {
        if (text == current) return@LaunchedEffect
        dir = direction
        previous = current
        current = text
        progress.snapTo(0f)
        progress.animateTo(1f, CycloMotion.CounterSpring)
        previous = null
    }

    Box(modifier = modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        previous?.let { old ->
            BasicText(
                text = old,
                style = centered,
                softWrap = false,
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentWidth(unbounded = true)
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        rollLayer(this, leaving = true, p = progress.value.coerceIn(0f, 1f), direction = dir, effects.animationsEnabled, effects.blurEnabled)
                    },
            )
        }
        BasicText(
            text = current,
            style = centered,
            // Uma hora nunca se parte em duas linhas (com letra grande, a linha de horas é que
            // passa para baixo; ver BedtimeRow).
            softWrap = false,
            modifier = Modifier
                .clearAndSetSemantics { }
                .graphicsLayer {
                    rollLayer(this, leaving = false, p = progress.value, direction = dir, effects.animationsEnabled, effects.blurEnabled)
                },
        )
    }
}

/**
 * Posição de um dos números: o que entra vai de [CycloMotion.CounterRollDistance] da altura até 0
 * (pode passar um pouco com a mola); o que sai vai de 0 até à mesma distância do outro lado.
 */
private fun rollLayer(
    scope: androidx.compose.ui.graphics.GraphicsLayerScope,
    leaving: Boolean,
    p: Float,
    direction: Int,
    animationsEnabled: Boolean,
    blurEnabled: Boolean,
) = with(scope) {
    // Quanto falta (o que entra) ou quanto já andou (o que sai), de 0 a 1.
    val away = if (leaving) p else 1f - p
    alpha = (1f - away.coerceIn(0f, 1f) * if (leaving) 1.6f else 1f).coerceIn(0f, 1f)
    if (!animationsEnabled) return@with
    val distance = size.height * CycloMotion.CounterRollDistance
    translationY = if (leaving) -direction * distance * away else direction * distance * away
    val scale = lerp(1f, 0.82f, away.coerceIn(0f, 1f))
    scaleX = scale
    scaleY = scale
    val sigma = figmaBlurPx(FigmaBlur.CounterRoll) * away.coerceIn(0f, 1f)
    renderEffect = if (blurEnabled && sigma > 0.5f) BlurEffect(sigma, sigma, TileMode.Decal) else null
}
