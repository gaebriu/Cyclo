package com.gaebriu.cyclo.ui.main

import androidx.compose.foundation.layout.offset
import com.gaebriu.cyclo.ui.effects.staggeredReveal
import com.gaebriu.cyclo.ui.effects.StaggeredReveal
import com.gaebriu.cyclo.ui.components.smoothRoundRectPath
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.Routine
import com.gaebriu.cyclo.ui.components.CycloIcon
import com.gaebriu.cyclo.ui.components.RoutineNameInput
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.components.cycloPressable
import com.gaebriu.cyclo.ui.components.rememberRoutineNameInput
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.drawInnerShadow
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.icons.CycloAppIconPaths
import com.gaebriu.cyclo.ui.routine.RepeatOption
import com.gaebriu.cyclo.ui.routine.Weekday
import com.gaebriu.cyclo.ui.routine.hasDay
import com.gaebriu.cyclo.ui.scroll.ClimbNearTop
import com.gaebriu.cyclo.ui.scroll.climbToTop
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.components.ReorderableColumn
import com.gaebriu.cyclo.ui.effects.ShatterOverlay
import com.gaebriu.cyclo.ui.effects.ShatterState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import com.gaebriu.cyclo.ui.time.LocalTimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalTime

// Medidas do Figma (Saved routines, frame de 393 x 852).
/** Os cartões começam a 126 dp do topo (39 dp abaixo do mini-menu). */
val SavedContentTop = 126.dp
private val CardHeight = 164.dp
private val CardPadding = 15.dp
private val ActionButtonSize = 39.dp

/** Dias da semana desligados: #A3A2A7 a 39 %. */
private val DayOff = CycloColors.TextSecondary.copy(alpha = 0.39f)

/** "fx upper-shadow" das rotinas desligadas: #101011 a 63 % em Luminosity, por cima do texto. */
private val DeactivatedVeil = CycloColors.GlassFill.copy(alpha = 0.63f)

/** Quanto tempo a caixa de pesquisa espera, depois de o teclado e o ditado pararem, para dar a escrita por acabada. */
private const val SearchFinishDelayMs = 350L

/**
 * Rotinas guardadas: um cartão por rotina com o nome, a hora de acordar e quando aparece, e os
 * botões apagar, widget, editar e ligar/desligar. As rotinas desligadas ficam veladas (o véu
 * aparece e desaparece com uma mola), menos os botões.
 *
 * - Long press num cartão e arrastar muda a ordem (ver [ReorderableColumn]); a nova ordem guarda-se
 *   ao largar ([onReorder]). Com uma pesquisa escrita não se reordena (a lista está filtrada).
 * - Apagar: o cartão parte-se em partículas a partir do botão de apagar ([ShatterOverlay]) e os de
 *   baixo sobem para o lugar dele, em cascata. Repor (o "Undo"): volta com zoom, opacidade e um
 *   ligeiro overshoot, e os de baixo descem para lhe dar lugar.
 *
 * Com a pesquisa aberta ([searchOpen]), a caixa de pesquisa (search-box) aparece no topo da lista
 * e só ficam os cartões com o texto no nome. [onSearchFinished] avisa quando se acaba de escrever
 * (quem mostra decide se a caixa fecha); [onSearchEnter] avisa do Enter e diz se há resultados.
 *
 * Sem rotinas nenhumas, mostra o estado vazio do Figma (Saved routines (no routines), 255:40), que
 * entra de baixo para cima com a cascata do Feed na primeira vez que se vê nesta sessão ([emptyReveal]).
 */
@Composable
fun SavedRoutinesScreen(
    routines: List<Routine>,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchFinished: () -> Unit,
    onSearchEnter: (hasResults: Boolean) -> Unit,
    bottomReserve: Dp,
    emptyReveal: StaggeredReveal,
    onEmptyShown: () -> Unit,
    onDelete: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onToggleActive: (Routine) -> Unit,
    onAddWidget: (Routine) -> Unit,
    onReorder: (List<Routine>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    // A lupa com a página lá em baixo: primeiro a página sobe até ao topo (a mola das páginas, o
    // movimento do aviso de atualização da release-1.1) e só então a caixa abre, já à vista e sem
    // empurrar a lista a meio da subida. Um dedo que interrompa a subida abre-a logo.
    var searchShown by remember { mutableStateOf(searchOpen) }
    val climbNear = with(density) { ClimbNearTop.roundToPx() }
    LaunchedEffect(searchOpen) {
        if (searchOpen && !searchShown) scroll.climbToTop(scope, effects.animationsEnabled, climbNear)
        searchShown = searchOpen
    }
    val search = animateFloatAsState(
        targetValue = if (searchShown) 1f else 0f,
        animationSpec = CycloMotion.SearchSpring,
        label = "search",
    )
    val returning = remember { ReturningCards() }
    val entering = remember(routines) { returning.update(routines) }
    val filtering = searchOpen && query.isNotBlank()
    val visible = remember(routines, query, searchOpen) {
        val q = query.trim()
        if (!filtering) routines else routines.filter { it.name.contains(q, ignoreCase = true) }
    }
    val shatter = remember { ShatterState() }
    val overlayCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { overlayCoordinates[0] = it }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .cycloVerticalScroll(scroll)
                // O espaço entre cartões vai dentro de cada um (encolhe com ele ao sair): tira-se o do último.
                .padding(top = SavedContentTop, bottom = (bottomReserve - CycloDimens.Spacing).coerceAtLeast(0.dp)),
        ) {
            if (searchShown || search.value > 0f) {
                SearchBox(
                    query = query,
                    onQueryChange = onQueryChange,
                    onFinished = onSearchFinished,
                    onEnter = { onSearchEnter(visible.isNotEmpty()) },
                    open = searchShown,
                    progress = { search.value },
                )
            }
            ReorderableColumn(
                items = visible,
                key = { it.id },
                onReorder = { ordered -> if (ordered.map { it.id } != routines.map { it.id }) onReorder(ordered) },
                moveUpLabel = stringResource(R.string.reorder_move_up),
                moveDownLabel = stringResource(R.string.reorder_move_down),
                enabled = !filtering,
                scrollState = scroll,
                modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin),
            ) { routine, handle, surface ->
                val snapshot = rememberGraphicsLayer()
                val cardCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
                Column {
                    PoppingIn(entering = routine.id in entering, onStarted = { entering -= routine.id }) {
                        RoutineCard(
                            routine = routine,
                            modifier = Modifier
                                .then(handle)
                                .then(surface)
                                .onGloballyPositioned { cardCoordinates[0] = it }
                                // O cartão desenha-se numa camada que também serve de fotografia
                                // para as partículas, ao apagar.
                                .drawWithContent {
                                    snapshot.record { this@drawWithContent.drawContent() }
                                    drawLayer(snapshot)
                                },
                            onDelete = {
                                val card = cardCoordinates[0]?.takeIf { it.isAttached }
                                val overlay = overlayCoordinates[0]?.takeIf { it.isAttached }
                                if (!effects.animationsEnabled || card == null || overlay == null) {
                                    onDelete(routine)
                                } else {
                                    scope.launch {
                                        val image = snapshot.toImageBitmap()
                                        val topLeft = overlay.localPositionOf(card, Offset.Zero)
                                        // As partículas partem do botão de apagar (o primeiro dos quatro).
                                        val origin = with(density) {
                                            Offset(
                                                card.size.width - (CardPadding + ActionButtonSize * 4 + 15.dp * 3 - ActionButtonSize / 2).toPx(),
                                                card.size.height - (CardPadding + ActionButtonSize / 2).toPx(),
                                            )
                                        }
                                        shatter.burst(image, topLeft, origin, density)
                                        CycloHaptics.shatter(view)
                                        returning.removed += routine.id
                                        onDelete(routine)
                                    }
                                }
                            },
                            onEdit = { onEdit(routine) },
                            onToggleActive = { onToggleActive(routine) },
                            onAddWidget = { onAddWidget(routine) },
                        )
                    }
                    Spacer(Modifier.height(CycloDimens.Spacing))
                }
            }
        }
        ShatterOverlay(shatter, Modifier.fillMaxSize())
        if (routines.isEmpty()) {
            SavedEmptyState(reveal = emptyReveal, onShown = onEmptyShown)
        }
    }
}

/**
 * Saved routines (no routines), Figma 255:40: "Nothing here just yet" e "Tap the + to create your
 * first routine", Inter Medium 15, #A3A2A7, centrados, a 27 um do outro (topos a 391 e 418 num
 * ecrã de 852: o meio do grupo fica 12,5 acima do meio do ecrã). O glow, o mini-menu e a barra de
 * navegação são os de sempre (fixos). Entra como os blocos do Feed ([StaggeredReveal]), uma só vez
 * por sessão: [onShown] marca-a como vista quando a cascata começa.
 */
@Composable
private fun SavedEmptyState(reveal: StaggeredReveal, onShown: () -> Unit) {
    val latestOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(Unit) {
        latestOnShown()
        reveal.play()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .offset(y = (-12.5).dp)
                .semantics(mergeDescendants = true) { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = stringResource(R.string.saved_empty_title),
                style = CycloType.Body,
                modifier = Modifier.staggeredReveal(reveal, 0),
            )
            Spacer(Modifier.height(9.dp))
            BasicText(
                text = stringResource(R.string.saved_empty_text),
                style = CycloType.Body,
                modifier = Modifier.staggeredReveal(reveal, 1),
            )
        }
    }
}


/**
 * Memória das rotinas apagadas neste ecrã, para que uma que volte (o "Undo") entre com zoom. Não é
 * estado do Compose: só muda quando a lista de rotinas muda, dentro do `remember` que a lê.
 */
private class ReturningCards {
    private var previous: Set<Long>? = null

    /** Ids apagados neste ecrã. */
    val removed = HashSet<Long>()

    /** As rotinas que acabaram de voltar (entram com zoom uma vez). */
    fun update(routines: List<Routine>): MutableSet<Long> {
        val ids = routines.mapTo(HashSet()) { it.id }
        val before = previous
        previous = ids
        if (before == null) return HashSet()
        val back = ids.filterTo(HashSet()) { it !in before && it in removed }
        removed -= back
        return back
    }
}

/**
 * Um cartão que volta (o "Undo"): zoom de 90 % com opacidade, e a mola [CycloSprings.PopIn] passa
 * um pouco do tamanho final e volta (overshoot controlado), com o háptico a acompanhar. Sem
 * animações, aparece logo.
 */
@Composable
private fun PoppingIn(entering: Boolean, onStarted: () -> Unit, content: @Composable () -> Unit) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    val pop = remember { Animatable(if (entering && effects.animationsEnabled) 0f else 1f) }
    val latestOnStarted by rememberUpdatedState(onStarted)
    LaunchedEffect(Unit) {
        if (pop.value < 1f) {
            latestOnStarted()
            CycloHaptics.popIn(view)
            pop.animateTo(1f, CycloSprings.PopIn.float)
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            val p = pop.value
            val scale = lerp(CycloMotion.MenuStartScale, 1f, p)
            scaleX = scale
            scaleY = scale
            alpha = p.coerceIn(0f, 1f)
        },
    ) {
        content()
    }
}

/**
 * search-box: a caixa de escrever/ditar com "What are you looking for?", com o vidro do Figma
 * (desfoque de fundo, brilho e borda em gradiente). Abre com foco e teclado.
 *
 * [onFinished] é chamado quando se acaba de escrever: Enter, teclado fechado (também pelo gesto de
 * voltar) ou fim do ditado. Espera um instante, para a passagem entre o ditado e o teclado (em que
 * nenhum dos dois está ativo durante uns frames) não contar.
 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onFinished: () -> Unit,
    onEnter: () -> Unit,
    open: Boolean,
    progress: () -> Float,
) {
    val effects = LocalVisualEffects.current
    val density = LocalDensity.current
    val input = rememberRoutineNameInput(onQueryChange)
    val ime = WindowInsets.ime
    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        var wasActive = false
        snapshotFlow { ime.getBottom(density) > 0 || input.isBusy }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    wasActive = true
                } else if (wasActive) {
                    delay(SearchFinishDelayMs)
                    wasActive = false
                    latestOnFinished()
                }
            }
    }
    // A caixa ocupa o seu lugar só à medida que abre (a lista desce com ela).
    Box(
        modifier = Modifier
            .layoutHeightFraction(progress)
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = p
                if (effects.animationsEnabled) translationY = (1f - p) * -12.dp.toPx()
            },
    ) {
        Column {
            RoutineNameInput(
                state = input,
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.saved_search_placeholder),
                lift = false,
                search = true,
                onEnter = onEnter,
                modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin),
            )
            Spacer(Modifier.height(CycloDimens.Spacing))
        }
    }
    // Ao abrir, a caixa fica logo com o teclado.
    LaunchedEffect(open) {
        if (open) input.openKeyboard()
    }
}

/** Mede o conteúdo inteiro mas só ocupa a fração [fraction] da altura (para abrir e fechar sem saltos). */
private fun Modifier.layoutHeightFraction(fraction: () -> Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * fraction().coerceIn(0f, 1f)).toInt()
            layout(placeable.width, height) { placeable.place(0, 0) }
        },
    )

/**
 * Cartão de uma rotina (routine 1, 2, 3): 345 x 164 dp, raio 15. Nome em cima; o sol e a hora;
 * o calendário e a repetição; e os quatro botões de 39 dp encostados à direita, 15 dp entre eles.
 */
@Composable
private fun RoutineCard(
    routine: Routine,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onAddWidget: () -> Unit,
) {
    val effects = LocalVisualEffects.current
    val active by animateFloatAsState(
        targetValue = if (routine.active) 1f else 0f,
        animationSpec = CycloMotion.CardSpring,
        label = "routineActive",
    )
    val time = LocalTime.of(routine.wakeHour, routine.wakeMinute).format(LocalTimeFormat.current.formatter)
    val repeatText = repeatSummary(routine)
    val stateText = stringResource(if (routine.active) R.string.saved_state_on else R.string.saved_state_off)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Com letra grande, o cartão cresce em vez de cortar o texto.
            .heightIn(min = CardHeight)
            .cycloCard(shadowColor = null),
    ) {
        Column(
            modifier = Modifier
                .padding(CardPadding)
                // Os botões ficam por baixo do texto (com letra grande, o cartão cresce).
                .padding(bottom = ActionButtonSize + CardPadding)
                .semantics(mergeDescendants = true) { stateDescription = stateText },
        ) {
            BasicText(
                text = routine.name,
                style = CycloType.CardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            InfoRow(CycloAppIconPaths.Sunny) { BasicText(text = time, style = CycloType.CardValue) }
            Spacer(Modifier.height(12.dp))
            InfoRow(CycloAppIconPaths.CalendarToday) {
                BasicText(
                    text = repeatText.first,
                    style = CycloType.CardValue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = repeatText.second },
                )
            }
        }

        // fx upper-shadow: por cima do texto e por baixo dos botões.
        Canvas(modifier = Modifier.matchParentSize()) {
            val off = 1f - active
            if (off <= 0f) return@Canvas
            drawPath(
                smoothRoundRectPath(size.width, size.height, 15.dp.toPx()),
                color = DeactivatedVeil,
                alpha = off,
                blendMode = effects.luminosity,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            RoundActionButton(
                iconPath = CycloAppIconPaths.RoutineDelete,
                description = stringResource(R.string.saved_delete, routine.name),
                onClick = onDelete,
            )
            // Fixa no ecrã principal o widget desta rotina (com a hora recomendada para dormir).
            RoundActionButton(
                iconPath = CycloAppIconPaths.RoutineWidget,
                description = stringResource(R.string.saved_widget, routine.name),
                onClick = onAddWidget,
            )
            RoundActionButton(
                iconPath = CycloAppIconPaths.RoutineEdit,
                description = stringResource(R.string.saved_edit, routine.name),
                onClick = onEdit,
            )
            ActiveToggleButton(
                active = { active },
                isActive = routine.active,
                description = stringResource(R.string.saved_toggle, routine.name),
                onClick = onToggleActive,
            )
        }
    }
}

@Composable
private fun InfoRow(iconPath: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 18.dp)) {
        CycloIcon(path = iconPath, size = 15.dp, tint = CycloColors.TextPrimary)
        Spacer(Modifier.width(6.dp))
        content()
    }
}

/**
 * Linha da repetição: "Show just this once", ou os dias "S M T W T F S" com os da rotina a branco
 * e os outros a #A3A2A7 a 39 %.
 */
@Composable
private fun repeatSummary(routine: Routine): Pair<AnnotatedString, String> {
    if (routine.repeat == RepeatOption.JustOnce) {
        val text = stringResource(R.string.repeat_just_once)
        return AnnotatedString(text) to text
    }
    val mask = routine.repeatMask
    val letters = Weekday.entries.map { stringResource(it.shortLabel) }
    val names = Weekday.entries.map { stringResource(it.pluralName) }
    val annotated = buildAnnotatedString {
        Weekday.entries.forEachIndexed { index, day ->
            if (index > 0) append(" ")
            withStyle(SpanStyle(color = if (mask.hasDay(day)) CycloColors.TextPrimary else DayOff)) {
                append(letters[index])
            }
        }
    }
    val spoken = Weekday.entries.filter { mask.hasDay(it) }.joinToString(", ") { names[it.ordinal] }
    return annotated to spoken
}

/**
 * Botão redondo de 39 dp (delete-, add-routine-widget-, edit-routine-b): #A3A2A7 a 15 % em
 * Luminosity, borda em gradiente #313233 (24 %, 9 %, 24 %) e sombra interior 0, 6, desfoque 15.
 * A zona de toque tem 48 dp (sobra para fora do círculo).
 */
@Composable
private fun RoundActionButton(iconPath: String, description: String, onClick: () -> Unit) {
    val effects = LocalVisualEffects.current
    ActionButtonFrame(description = description, onClick = onClick, role = Role.Button) {
        Canvas(modifier = Modifier.size(ActionButtonSize)) {
            val blend = effects.luminosity
            val radius = size.width / 2f
            val circle = Path().apply { addOval(Rect(Offset(radius, radius), radius)) }
            drawPath(circle, CycloColors.TextSecondary.copy(alpha = 0.15f), blendMode = blend)
            drawInnerShadow(circle, Color.Black.copy(alpha = 0.15f), Offset(0f, 6.dp.toPx()), 15.dp.toPx())
            val edge = CycloColors.GlassBorder
            drawCircle(
                brush = Brush.verticalGradient(
                    0f to edge.copy(alpha = 0.24f),
                    0.5f to edge.copy(alpha = 0.09f),
                    1f to edge.copy(alpha = 0.24f),
                ),
                radius = radius - 0.5.dp.toPx(),
                style = Stroke(1.dp.toPx()),
                blendMode = blend,
            )
        }
        CycloIcon(path = iconPath, size = ActionButtonSize, tint = CycloColors.TextPrimary)
    }
}

/**
 * activate/deactivate-routine-b: ligado, círculo #4A3C99 com o visto branco; desligado, #A3A2A7
 * a 24 % com o visto a 24 %. Muda com uma mola.
 */
@Composable
private fun ActiveToggleButton(active: () -> Float, isActive: Boolean, description: String, onClick: () -> Unit) {
    val effects = LocalVisualEffects.current
    val view = LocalView.current
    ActionButtonFrame(
        description = description,
        onClick = {
            CycloHaptics.toggle(view, !isActive)
            onClick()
        },
        role = Role.Switch,
        haptic = false,
        toggleable = ToggleableState(isActive),
    ) {
        Canvas(modifier = Modifier.size(ActionButtonSize)) {
            val a = active().coerceIn(0f, 1f)
            val blend = effects.luminosity
            val radius = size.width / 2f
            val circle = Path().apply { addOval(Rect(Offset(radius, radius), radius)) }
            val offFill = CycloColors.TextSecondary.copy(alpha = 0.24f)
            drawPath(circle, lerpColor(offFill, CycloColors.Accent, a))
            drawInnerShadow(circle, Color.Black.copy(alpha = 0.15f), Offset(0f, 6.dp.toPx()), 15.dp.toPx())
            drawCircle(
                color = lerpColor(CycloColors.TextSecondary, CycloColors.Accent, a).copy(alpha = 0.24f),
                radius = radius - 0.5.dp.toPx(),
                style = Stroke(1.dp.toPx()),
                blendMode = blend,
            )
        }
        CycloIcon(
            path = CycloAppIconPaths.RoutineActive,
            size = ActionButtonSize,
            tint = CycloColors.TextPrimary,
            alpha = { lerp(0.24f, 1f, active()) },
        )
    }
}

@Composable
private fun ActionButtonFrame(
    description: String,
    onClick: () -> Unit,
    role: Role,
    haptic: Boolean = true,
    toggleable: ToggleableState? = null,
    content: @Composable () -> Unit,
) {
    val outset = (CycloDimens.TouchTarget - ActionButtonSize) / 2
    Box(modifier = Modifier.size(ActionButtonSize)) {
        Box(
            modifier = Modifier
                .layout { measurable, _ ->
                    val extra = outset.roundToPx()
                    val target = CycloDimens.TouchTarget.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(target, target))
                    layout(ActionButtonSize.roundToPx(), ActionButtonSize.roundToPx()) { placeable.place(-extra, -extra) }
                }
                .cycloPressable(onClick = onClick, haptic = haptic, pressedScale = CycloMotion.IconButtonPressScale, role = role)
                .semantics {
                    contentDescription = description
                    if (toggleable != null) toggleableState = toggleable
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(ActionButtonSize)) { content() }
        }
    }
}
