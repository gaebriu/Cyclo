package com.gaebriu.cyclo.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.data.CycloPrompt
import com.gaebriu.cyclo.ui.components.CycloButtonSize
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloPrimaryButton
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawBlurredPath
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.screens.introSwipeDecision
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.rubberBand
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// Medidas do Figma (Componentes (Prompts) 2006:449 e (Prompts 2) 6063:84).
private val PromptPadding = 15.dp
private val TitleToText = 9.dp

/** O título e o texto param antes do close-b (39 dp e 15 dp de folga). */
private val TextEnd = 39.dp + 15.dp

/** O "fx blur type 01" (315 x 33, a 15 dp dos lados, no topo) e o desfoque dele pelo vidro (39). */
private val GlowInset = 15.dp
private val GlowHeight = 33.dp
private val GlowBlur = 39.dp

/** A cor do "fx blur type 01" entra a 24 % (#5FA2DB a 24 % nos do céu, #4A3C99 a 24 % nos roxos). */
private const val GlowAlpha = 0.24f

/**
 * card-indicator (sleep-evaluation-prompt (w/side swipe), 6062:69): dois pontos de 6 dp, 4 dp entre
 * eles, 15 dp acima da base do cartão; o do cartão à vista #A3A2A7, o outro a 15 %. O cartão cresce
 * [IndicatorSpace] (os 6 dp mais os 15 dp até ao botão): 156 + 21 = os 177 do Figma.
 */
private val IndicatorDot = 6.dp
private val IndicatorGap = 4.dp
private val IndicatorBottom = 15.dp
private val IndicatorSpace = IndicatorDot + 15.dp
private const val IndicatorOffAlpha = 0.15f

/** Entre dois cartões do mesmo lugar, lado a lado (o vizinho fica fora do ecrã, para lá da margem). */
private val CardGap = 24.dp

/** O vizinho a meio caminho fica um nadinha mais pequeno (profundidade), e o que sai recua até aqui. */
private const val SideScale = 0.94f

/** Na entrada e na saída do lugar, o cartão cresce de / encolhe até esta escala (como o de 3.2). */
private const val PresenceScale = 0.96f

/** Um movimento claramente horizontal: x tem de passar y por isto. */
private const val HorizontalDominance = 1.2f

/**
 * O que o lugar dá a cada cartão (Cyclo 3.2.3): o close-b ([closeAlpha], [closeEnabled]) some
 * enquanto os cartões trocam e só volta quando a troca acaba; [extraBottom] (px) é o espaço do
 * card-indicator quando há dois. Os dois lambdas leem-se só no desenho/na medida.
 */
@Stable
internal class PromptChrome(
    val closeAlpha: () -> Float,
    val closeEnabled: Boolean,
    val extraBottom: () -> Float,
)

/** Um cartão sozinho, sem troca (fora de um [PromptSlot]). */
private val StillChrome = PromptChrome(closeAlpha = { 1f }, closeEnabled = true, extraBottom = { 0f })

/** O texto dos prompts (Inter Medium 15, #A3A2A7), alinhado à esquerda. */
internal val PromptTextStyle: TextStyle = CycloType.Body.copy(textAlign = TextAlign.Start)

/**
 * Um prompt (sleep-evaluation-prompt 2006:416, never-miss-bedtime 6062:70, keep-the-water-flowing
 * 6063:111, keep-your-progress-safe 6063:141, new-update-group 6031:80): 345 dp de largura, o vidro
 * dos cartões com a sombra (0, 4, desfoque 39, preto a 24 %) e o "fx blur type 01" por trás do topo;
 * o título (Inter Semi Bold 15), [TitleToText], o texto, [textToButton], o botão roxo compacto e o
 * close-b no canto (39 dp a 15 dp do topo e da direita; a zona de toque de 48 dp sai 4,5 dp).
 *
 * @param glowColor a cor do "fx blur type 01" (a base do brilho da hora, ou o roxo); lê-se só ao
 *                  desenhar, por isso uma mudança de cor não recompõe o cartão.
 * @param minHeight a altura do Figma (156; o new-update-group, com uma linha de texto, 147).
 */
@Composable
internal fun PromptCard(
    title: String,
    text: String,
    buttonText: String,
    onButton: () -> Unit,
    closeDescription: String,
    onClose: () -> Unit,
    glowColor: () -> Color,
    modifier: Modifier = Modifier,
    chrome: PromptChrome = StillChrome,
    textStyle: TextStyle = PromptTextStyle,
    textToButton: Dp = 15.dp,
    minHeight: Dp = 156.dp,
    textIsLive: Boolean = false,
) {
    val glow = remember { PromptGlow() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            // O retângulo do "fx blur type 01" fica por baixo do vidro, desfocado por ele (uma vez
            // por largura, ver PromptGlow: o cartão redesenha-se a cada fotograma do scroll).
            .cycloCard(underFill = { glow.draw(this, glowColor()) })
            .padding(PromptPadding),
    ) {
        Column {
            BasicText(
                text = title,
                style = CycloType.CardTitle,
                modifier = Modifier
                    .padding(end = TextEnd)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(TitleToText))
            BasicText(
                text = text,
                style = textStyle,
                modifier = Modifier
                    .padding(end = TextEnd)
                    .then(if (textIsLive) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            )
            Spacer(Modifier.height(textToButton))
            CycloPrimaryButton(text = buttonText, onClick = onButton, size = CycloButtonSize.Compact)
            // O lugar do card-indicator (só com dois prompts no mesmo sítio); abre e fecha na medida.
            Box(Modifier.layout { _, constraints -> layout(constraints.minWidth, chrome.extraBottom().roundToInt()) {} })
        }
        CycloCircleButton(
            iconPath = CycloIconPaths.Close,
            contentDescription = closeDescription,
            onClick = onClose,
            enabled = chrome.closeEnabled,
            inCard = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2, y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2)
                .graphicsLayer { alpha = chrome.closeAlpha() },
        )
    }
}

/**
 * O "fx blur type 01" de um prompt (315 x 33 a 15 dp dos lados, no topo, a cor a 24 %, desfocado
 * pelo vidro, 39). A forma desfocada (no CPU) desenha-se numa imagem branca e opaca, que serve de
 * máscara; a cor entra só ao copiar a imagem (tint SrcIn), por isso pode mudar a cada fotograma (a
 * transição entre faixas horárias) sem voltar a desfocar nada.
 *
 * Cyclo 3.2.3: a imagem depende só da LARGURA (o brilho está preso ao topo; a altura dela é a do
 * brilho desfocado, não a do cartão): o cartão pode crescer (o card-indicator a entrar) sem desfocar
 * de novo em cada fotograma.
 */
internal class PromptGlow {
    private var image: ImageBitmap? = null
    private var forWidth = -1f
    private var tintColor = Color.Unspecified
    private var tint: ColorFilter? = null

    fun draw(scope: DrawScope, color: Color) = with(scope) {
        if (image == null || forWidth != size.width) {
            forWidth = size.width
            val blur = GlowBlur.toPx()
            val w = size.width.roundToInt().coerceAtLeast(1)
            // Três desvios-padrão (o desfoque do Figma é 2 sigma) abaixo do retângulo: o resto é < 1 %.
            val h = (GlowHeight.toPx() + blur * 1.5f).roundToInt().coerceAtLeast(1)
            val bitmap = ImageBitmap(w, h)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                this, layoutDirection, androidx.compose.ui.graphics.Canvas(bitmap), androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat()),
            ) {
                val glow = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(GlowInset.toPx(), 0f, w - GlowInset.toPx(), GlowHeight.toPx()))
                }
                drawBlurredPath(glow, Color.White, blur)
            }
            image = bitmap
        }
        val glowColor = color.copy(alpha = color.alpha * GlowAlpha)
        if (tint == null || glowColor != tintColor) {
            tintColor = glowColor
            tint = ColorFilter.tint(glowColor, BlendMode.SrcIn)
        }
        image?.let { drawImage(it, colorFilter = tint) }
    }
}

/**
 * O lugar de um ou mais prompts (Cyclo 3.2.3). Sem nenhum, não ocupa nada; o primeiro entra a abrir
 * (a altura, a opacidade e um pouco a escala) e o último sai a encolher, e o que está por baixo
 * acompanha, sem saltos (o comportamento do sleep-evaluation-prompt desde a beta-3.0).
 *
 * Dois no mesmo lugar não se empilham: partilham-no, lado a lado, e trocam-se com o dedo
 * ([PromptCarousel]).
 *
 * @param topGap, bottomGap os espaços antes e depois: abrem e fecham com o lugar.
 */
@Composable
internal fun PromptSlot(
    prompts: List<CycloPrompt>,
    modifier: Modifier = Modifier,
    topGap: Dp = 0.dp,
    bottomGap: Dp = 0.dp,
    card: @Composable (prompt: CycloPrompt, chrome: PromptChrome) -> Unit,
) {
    val effects = LocalVisualEffects.current
    val visible = prompts.isNotEmpty()
    // A sair, continuam à vista os últimos que havia.
    val last = remember { arrayOf(prompts) }
    if (visible) last[0] = prompts
    val shown = if (visible) prompts else last[0]
    val presence = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        when {
            !effects.animationsEnabled -> presence.animateTo(target, tween(150))
            visible -> presence.animateTo(1f, CycloSprings.Enter.precise)
            else -> presence.animateTo(0f, CycloSprings.CardCollapse.precise)
        }
    }
    // Lê-se a presença só quando passa por zero (não recompõe a cada fotograma da entrada).
    val present by remember { derivedStateOf { presence.value > 0f } }
    if ((!visible && !present) || shown.isEmpty()) return
    Box(
        modifier = modifier
            // Ocupa só a fração da altura que já abriu (o que está por baixo sobe com ele).
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val p = presence.value.coerceIn(0f, 1f)
                val height = (placeable.height * if (effects.animationsEnabled) p else 1f).roundToInt()
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            // A camada (opacidade a meio) desenha fora do ecrã: alarga-se para a sombra caber.
            .bleed(CardShadowBleed)
            .graphicsLayer {
                val p = presence.value.coerceIn(0f, 1f)
                alpha = p
                if (effects.animationsEnabled) {
                    val s = lerp(PresenceScale, 1f, p)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
            .padding(CardShadowBleed),
    ) {
        Column {
            Spacer(Modifier.height(topGap))
            PromptCarousel(prompts = shown, card = card)
            Spacer(Modifier.height(bottomGap))
        }
    }
}

/**
 * Os prompts de um lugar, lado a lado, um de cada vez à vista (o side swipe da Cyclo 3.2.3):
 * - Uma só posição contínua ([position], em cartões, a partir do que está à frente): o dedo move-a
 *   1:1, sem atraso; para lá do primeiro e do último, resiste (rubber band). Ao largar, a decisão é
 *   a das páginas ([introSwipeDecision]: passar de metade ou lançar) e a mola [CycloSprings.Page]
 *   parte da velocidade do dedo. O vizinho a meio caminho fica um pouco mais pequeno (profundidade).
 * - A altura do lugar vai de uma à outra com a posição (o new-update-group tem 147, os outros 156).
 * - O close-b some logo que os cartões começam a mexer e só volta quando a troca acaba de todo.
 * - O card-indicator (dois pontos) segue a posição; entra e sai com o segundo prompt.
 * - Um prompt que sai (fechado, ou a condição deixou de valer) enquanto o outro fica recua no lugar
 *   (encolhe e apaga-se, como o ecrã por baixo de uma sobreposição) e o vizinho entra por cima
 *   dele, do lado de onde estava.
 * - Com dois, o swipe das páginas (Feed ⇄ Saved) não começa em cima deles ([excludeFromPageSwipe]).
 *
 * Desempenho: a posição, a altura, o close-b e os pontos leem-se só na medida e no desenho; o
 * cartão fora de vista não se desenha.
 */
@Composable
private fun PromptCarousel(
    prompts: List<CycloPrompt>,
    card: @Composable (prompt: CycloPrompt, chrome: PromptChrome) -> Unit,
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val latestPrompts by rememberUpdatedState(prompts)

    // O cartão à frente e onde o lugar está em relação a ele (em cartões; + = a caminho do seguinte).
    var current by remember { mutableStateOf(prompts.first()) }
    val position = remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }

    // O close-b: 1 = à vista. Some enquanto os cartões mexem; o toque só vale com ele todo à vista.
    val closeAlpha = remember { mutableFloatStateOf(1f) }
    var closeJob by remember { mutableStateOf<Job?>(null) }
    val closeEnabled by remember { derivedStateOf { closeAlpha.floatValue >= 0.99f } }
    fun showClose(show: Boolean) {
        closeJob?.cancel()
        if (!effects.animationsEnabled) {
            closeAlpha.floatValue = if (show) 1f else 0f
            return
        }
        closeJob = scope.launch {
            val spec = if (show) CycloSprings.Gentle.float else CycloSprings.Exit.float
            animate(closeAlpha.floatValue, if (show) 1f else 0f, animationSpec = spec) { v, _ -> closeAlpha.floatValue = v }
        }
    }

    /** Leva a posição a 0 (o cartão [current] à frente), com a velocidade [velocity] (cartões/s). */
    fun settleHome(velocity: Float) {
        settle?.cancel()
        if (!effects.animationsEnabled) {
            position.floatValue = 0f
            showClose(true)
            return
        }
        settle = scope.launch {
            animate(position.floatValue, 0f, velocity, CycloSprings.Page.precise) { v, _ -> position.floatValue = v }
            showClose(true)
        }
    }

    // Um prompt que sai com o outro à vista: recua no lugar e o vizinho entra por cima dele.
    var leaving by remember { mutableStateOf<CycloPrompt?>(null) }
    val leave = remember { mutableFloatStateOf(0f) }
    val before = remember { arrayOf(prompts) }
    if (prompts != before[0]) {
        val old = before[0]
        before[0] = prompts
        if (current !in prompts) {
            val from = old.indexOf(current)
            val next = old.drop(from + 1).firstOrNull { it in prompts }
                ?: old.take(from.coerceAtLeast(0)).lastOrNull { it in prompts }
                ?: prompts.first()
            leaving = current.takeIf { effects.animationsEnabled }
            leave.floatValue = 1f
            current = next
            // Começa onde estava: à direita (o seguinte) ou à esquerda (o anterior).
            position.floatValue = if (effects.animationsEnabled) (if (old.indexOf(next) > from) -1f else 1f) else 0f
        }
    }
    LaunchedEffect(leaving) {
        if (leaving == null) return@LaunchedEffect
        showClose(false)
        settle?.cancel()
        coroutineScope {
            launch { animate(1f, 0f, animationSpec = CycloSprings.CardCollapse.float) { v, _ -> leave.floatValue = v } }
            animate(position.floatValue, 0f, 0f, CycloSprings.Page.precise) { v, _ -> position.floatValue = v }
        }
        leaving = null
        showClose(true)
    }

    // O card-indicator e o espaço dele: só com dois ou mais.
    val many = prompts.size >= 2
    val dots = remember { mutableFloatStateOf(if (many) 1f else 0f) }
    LaunchedEffect(many) {
        val target = if (many) 1f else 0f
        if (!effects.animationsEnabled) {
            dots.floatValue = target
        } else {
            val spec = if (many) CycloSprings.Enter.float else CycloSprings.CardCollapse.float
            animate(dots.floatValue, target, animationSpec = spec) { v, _ -> dots.floatValue = v }
        }
    }

    val indicatorSpacePx = with(LocalDensity.current) { IndicatorSpace.toPx() }
    val chrome = remember(closeEnabled, indicatorSpacePx) {
        PromptChrome(
            closeAlpha = { closeAlpha.floatValue },
            closeEnabled = closeEnabled,
            extraBottom = { indicatorSpacePx * dots.floatValue.coerceAtLeast(0f) },
        )
    }

    /** Onde [prompt] está em relação à frente (em cartões): 0 = à vista. */
    fun relative(prompt: CycloPrompt): Float {
        val list = latestPrompts
        val index = list.indexOf(prompt)
        if (index < 0) return 0f
        return index - list.indexOf(current).coerceAtLeast(0) - position.floatValue
    }

    val rendered = if (leaving != null && leaving !in prompts) listOf(leaving!!) + prompts else prompts
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (many) Modifier.excludeFromPageSwipe() else Modifier)
            .pointerInput(effects.animationsEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Com um prompt a sair, a posição é da saída dele (não do dedo).
                    if (latestPrompts.size < 2 || size.width <= 0 || leaving != null) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    // 1. Esperar por um movimento claramente horizontal (o vertical é do scroll).
                    var claimed: PointerInputChange? = null
                    while (claimed == null) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        val total = change.position - down.position
                        if (abs(total.x) > slop && abs(total.x) > abs(total.y) * HorizontalDominance) {
                            change.consume()
                            claimed = change
                        } else if (abs(total.y) > slop) {
                            return@awaitEachGesture
                        }
                    }
                    // 2. O lugar segue o dedo (a partir de onde estava, a meio de uma mola se for o caso).
                    settle?.cancel()
                    showClose(false)
                    val pitch = size.width + CardGap.toPx()
                    val list = latestPrompts
                    val index = list.indexOf(current).coerceAtLeast(0)
                    val start = position.floatValue
                    val origin = claimed.position.x
                    val tracker = VelocityTracker()
                    tracker.addPosition(claimed.uptimeMillis, claimed.position)
                    var beyond = false
                    var completed = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            completed = false
                            break
                        }
                        change.consume()
                        if (!change.pressed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val raw = start - (change.position.x - origin) / pitch
                        // Para lá do primeiro e do último cartão, resistência do iOS.
                        val low = -index.toFloat()
                        val high = (list.size - 1 - index).toFloat()
                        position.floatValue = when {
                            raw < low -> low - rubberBand((low - raw) * pitch, pitch) / pitch
                            raw > high -> high + rubberBand((raw - high) * pitch, pitch) / pitch
                            else -> raw
                        }
                        val past = abs(position.floatValue) > 0.5f && position.floatValue in low..high
                        if (past != beyond) {
                            beyond = past
                            CycloHaptics.thresholdCrossed(view, past)
                        }
                    }
                    // 3. Ao largar: o vizinho, se passou de metade ou foi lançado; senão volta.
                    val velocity = if (completed) -tracker.calculateVelocity().x / pitch else 0f
                    val decision = if (completed) introSwipeDecision(position.floatValue, velocity) else 0
                    val target = (index + decision).coerceIn(0, list.size - 1)
                    if (target != index) {
                        current = list[target]
                        position.floatValue -= (target - index)
                    }
                    settleHome(velocity)
                }
            }
            .drawWithContent {
                drawContent()
                // card-indicator: por cima dos cartões, ao centro, 15 dp acima da base do lugar.
                val presence = dots.floatValue.coerceIn(0f, 1f)
                val list = latestPrompts
                if (presence <= 0f || list.size < 2) return@drawWithContent
                val dot = IndicatorDot.toPx()
                val gap = IndicatorGap.toPx()
                val width = dot * list.size + gap * (list.size - 1)
                val y = size.height - IndicatorBottom.toPx() - dot / 2f
                val at = list.indexOf(current).coerceAtLeast(0) + position.floatValue
                list.indices.forEach { i ->
                    val on = (1f - abs(i - at)).coerceIn(0f, 1f)
                    val x = (size.width - width) / 2f + i * (dot + gap) + dot / 2f
                    drawCircle(
                        color = CycloColors.TextSecondary,
                        radius = dot / 2f,
                        center = Offset(x, y),
                        alpha = lerp(IndicatorOffAlpha, 1f, on) * presence,
                    )
                }
            },
        content = {
            rendered.forEach { prompt ->
                key(prompt) {
                    val isLeaving = prompt == leaving
                    Box(
                        modifier = Modifier
                            .bleed(CardShadowBleed)
                            .graphicsLayer {
                                if (isLeaving) {
                                    // Recua no lugar (como o ecrã por baixo de uma sobreposição).
                                    val l = leave.floatValue.coerceIn(0f, 1f)
                                    alpha = l
                                    val s = lerp(SideScale, 1f, l)
                                    scaleX = s
                                    scaleY = s
                                } else {
                                    val rel = relative(prompt)
                                    val away = abs(rel)
                                    // Fora de vista: não se desenha (nem lê o fundo).
                                    alpha = if (away >= 1f) 0f else 1f
                                    translationX = rel * (size.width - CardShadowBleed.toPx() * 2 + CardGap.toPx())
                                    val s = lerp(1f, SideScale, away.coerceAtMost(1f))
                                    scaleX = s
                                    scaleY = s
                                }
                            }
                            .padding(CardShadowBleed),
                    ) { card(prompt, chrome) }
                }
            }
        },
    ) { measurables, constraints ->
        val loose = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(loose) }
        fun heightOf(prompt: CycloPrompt?): Int? =
            prompt?.let { p -> rendered.indexOf(p).takeIf { it >= 0 }?.let { placeables[it].height } }
        // A altura vai da do cartão à frente à do vizinho para onde o dedo o leva.
        val list = latestPrompts
        val index = list.indexOf(current).coerceAtLeast(0)
        val pos = position.floatValue
        val toward = list.getOrNull(index + sign(pos).toInt())
        val front = heightOf(current) ?: 0
        var height = lerp(front.toFloat(), (heightOf(toward) ?: front).toFloat(), abs(pos).coerceAtMost(1f))
        // Um prompt a sair: a altura dele dá lugar à do que fica.
        leaving?.let { gone -> heightOf(gone)?.let { height = lerp(height, it.toFloat(), leave.floatValue.coerceIn(0f, 1f)) } }
        layout(constraints.maxWidth, height.roundToInt()) {
            placeables.forEachIndexed { i, placeable ->
                // O que sai fica por baixo; os outros por cima dele.
                placeable.place(0, 0, zIndex = if (rendered[i] == leaving) 0f else 1f)
            }
        }
    }
}
