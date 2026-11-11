package com.gaebriu.cyclo.ui.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gaebriu.cyclo.R
import com.gaebriu.cyclo.data.CycloRepository
import com.gaebriu.cyclo.ui.components.ActionMenuItem
import com.gaebriu.cyclo.ui.components.AnchoredMenuHost
import com.gaebriu.cyclo.ui.components.CycloActionMenu
import com.gaebriu.cyclo.ui.components.CycloCircleButton
import com.gaebriu.cyclo.ui.components.CycloMeter
import com.gaebriu.cyclo.ui.components.MeterStyle
import com.gaebriu.cyclo.ui.components.cycloCard
import com.gaebriu.cyclo.ui.effects.BackGestureVelocity
import com.gaebriu.cyclo.ui.effects.Backdrop
import com.gaebriu.cyclo.ui.effects.LocalBackdrop
import com.gaebriu.cyclo.ui.effects.LocalVisualEffects
import com.gaebriu.cyclo.ui.effects.backdropSource
import com.gaebriu.cyclo.ui.effects.cycloLightSheen
import com.gaebriu.cyclo.ui.effects.rememberBackdropLayer
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.icons.CycloIconPaths
import com.gaebriu.cyclo.ui.main.MainEdgeFades
import com.gaebriu.cyclo.ui.scroll.cycloVerticalScroll
import com.gaebriu.cyclo.ui.settings.SectionLabel
import com.gaebriu.cyclo.ui.settings.SettingsButton
import com.gaebriu.cyclo.ui.settings.SettingsRow
import com.gaebriu.cyclo.ui.theme.CycloColors
import com.gaebriu.cyclo.ui.theme.CycloDimens
import com.gaebriu.cyclo.ui.theme.CycloMotion
import com.gaebriu.cyclo.ui.theme.CycloSprings
import com.gaebriu.cyclo.ui.theme.CycloType
import com.gaebriu.cyclo.widget.BedtimeWidgets
import com.gaebriu.cyclo.widget.WallpaperPalette
import com.gaebriu.cyclo.widget.WidgetLook
import com.gaebriu.cyclo.widget.WidgetStyle
import com.gaebriu.cyclo.widget.WidgetStyles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

// Medidas do Figma (Widget studio, Cyclo-2 2054:143, frame de 393 x 852).
/** O título começa a 111 dp do topo; o "Preview" a 179 dp (39 dp depois do título), como nas definições. */
private val TitleTop = 111.dp
private val TitleToContent = 39.dp
private val LabelToContent = 15.dp

/** Da wallpaper (user-wallpaper, até 454) ao "Looks" (480). */
private val PreviewToLooks = 26.dp
private val GroupGap = 15.dp

/** O widget fica a 39 dp do topo e do fundo da wallpaper (251 − 212 e 454 − 415), centrado. */
private val WallpaperPadding = 39.dp

/** Os grupos dos sliders (opacity-group, frosted-glass-group): 15 dp à volta, 15 entre o texto e o slider. */
private val GroupInset = 15.dp

/** A inclinação máxima do mockup, em graus (aprovado pelo utilizador: até 6° em X e em Y). */
private const val MaxTilt = 6f

/** A câmara do 3D (em unidades do Compose; 8 por omissão): mais longe, menos deformação na volta inteira. */
private const val CameraDistance = 16f

/** O gel do mockup (o mesmo dos botões, components/Pressable.kt): quanto comprime, com máximos em dp. */
private const val PressAmount = 1f - CycloMotion.ButtonPressScale
private val MaxShrinkX = 8.dp
private val MaxShrinkY = 5.dp
private const val HeightBias = 1.25f
private const val QuickTapImpulse = 9f

/** Os raios dos cantos do mockup (Subtle, Smooth), em dp do Figma. */
private fun cornerDp(style: WidgetStyle): Dp = style.cornerRadius.dp

/**
 * O Widget Studio (beta-3.0, Figma Cyclo-2 2054:143): abre-se pelas definições do widget no
 * lançador ([com.gaebriu.cyclo.widget.WidgetStudioActivity]) e muda o aspeto dos dois widgets, em
 * todas as instâncias (global).
 *
 * - Sobe e desce como as definições (o mesmo `cover`, [CycloMotion.ScreenRiseSpring], o voltar
 *   preditivo a seguir o dedo); o scroll é o das definições ([cycloVerticalScroll]).
 * - O preview: a wallpaper verdadeira (a janela mostra-a por baixo, `windowShowWallpaper`) de ponta a
 *   ponta, com o widget a 39 dp de cima e de baixo, centrado. O widget é o MESMO bitmap do ecrã
 *   principal ([BedtimeWidgets.studioBitmap]): o que se vê aqui é o que lá fica.
 * - O mockup reage ao dedo: inclina até 6° para o lado tocado ([CycloSprings.Tilt]), comprime como
 *   gel e acende a luz ([cycloLightSheen]); não faz scroll da página.
 * - Opacity e Frosted glass são medidores de três pontos ([MeterStyle.Accent]) com os valores do
 *   Figma; Corner style abre o action-menu-corner-style (Componentes 5). Tudo se guarda logo.
 * - Restore defaults: os sliders voltam às predefinições com a mola do medidor e o widget dá uma
 *   volta inteira no eixo Y ([CycloSprings.Spin]), a face de trás já com as predefinições.
 */
@Composable
fun WidgetStudio(appWidgetId: Int, onFinished: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val repository = remember(app) { CycloRepository(app) }
    val data = repository.data.collectAsState(initial = null).value
    // As "Advanced textures" das definições valem aqui também.
    val base = LocalVisualEffects.current
    val effects = remember(base, data?.advancedTextures) { base.copy(advancedTextures = data?.advancedTextures ?: base.advancedTextures) }
    CompositionLocalProvider(LocalVisualEffects provides effects) {
        StudioPage(appWidgetId, data, onFinished)
    }
}

@Composable
private fun StudioPage(appWidgetId: Int, data: com.gaebriu.cyclo.data.CycloData?, onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val density = LocalDensity.current
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()
    val kind = remember(appWidgetId) { BedtimeWidgets.kindOf(app, appWidgetId) }

    var style by remember { mutableStateOf(WidgetStyles.load(app)) }
    // O que o preview mostra: segue a bola dos sliders enquanto ela anda (o guardado só muda quando
    // ela assenta).
    var shown by remember { mutableStateOf(style) }
    var palette by remember { mutableStateOf(WallpaperPalette.current(app)) }

    // A wallpaper pode ter mudado com o Studio em segundo plano.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) palette = WallpaperPalette.current(app)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun commit(new: WidgetStyle) {
        style = new
        shown = new
        StudioSync.save(app, new)
    }

    // cover: 0 = fechado (por baixo do ecrã), 1 = aberto. Como as definições na app.
    val cover = remember { Animatable(0f, visibilityThreshold = 0.0005f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A subida só começa depois de a janela ter desenhado (o primeiro desenho do Studio é o
        // mais pesado): senão a mola corria às escondidas e a página aparecia já no sítio.
        repeat(2) { withFrameNanos { } }
        cover.animateTo(1f, CycloMotion.ScreenRiseSpring)
    }
    fun close(velocity: Float = 0f) {
        if (closing) return
        closing = true
        scope.launch {
            cover.animateTo(0f, CycloMotion.ScreenRiseSpring, initialVelocity = velocity)
            onFinished()
        }
    }

    var cornerMenuOpen by remember { mutableStateOf(false) }
    var cornerAnchor by remember { mutableStateOf(Rect.Zero) }

    // A volta inteira do Restore defaults: o ângulo (0 a 360) e a imagem da face da frente antes de
    // passar os 90° (depois disso, e na face de trás, já é a das predefinições).
    val spin = remember { Animatable(0f) }
    var spinFrom by remember { mutableStateOf<ImageBitmap?>(null) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topExtra = (statusTop - 24.dp).coerceAtLeast(0.dp)
    val bottomInset = maxOf(CycloDimens.MinBottomInset, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    val textShadow = remember(density) { Shadow(Color.Black.copy(alpha = 0.24f), Offset.Zero, with(density) { 15.dp.toPx() }) }
    val labelStyle = remember(textShadow) { CycloType.SectionLabel.copy(shadow = textShadow) }
    val groupTitleStyle = remember(textShadow) { CycloType.CardTitle.copy(shadow = textShadow) }
    val content = rememberBackdropLayer()
    val scroll = rememberScrollState()
    val rampPx = with(density) { 48.dp.toPx() }
    val bottomAmount by remember(scroll, rampPx) {
        derivedStateOf {
            if (scroll.maxValue == Int.MAX_VALUE) 1f else ((scroll.maxValue - scroll.value) / rampPx).coerceIn(0f, 1f)
        }
    }

    // Onde está a "janela" para a wallpaper, em coordenadas da página: o resto da página é #010101.
    val pageCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var hole by remember { mutableStateOf(Rect.Zero) }

    val opacityLevels = WidgetStyle.Opacities.map { stringResource(R.string.studio_percent, (it * 100).roundToInt()) }
    val frostLevels = WidgetStyle.Frosts.map { stringResource(R.string.studio_dp, it.roundToInt()) }
    val cornerLabels = listOf(stringResource(R.string.studio_corner_subtle), stringResource(R.string.studio_corner_smooth))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val c = cover.value
                if (effects.animationsEnabled) translationY = (1f - c) * size.height else alpha = c
            }
            .onGloballyPositioned { pageCoords[0] = it }
            // A página é #010101, menos a janela da wallpaper (que a janela da Activity mostra por baixo).
            .drawBehind {
                val r = hole
                if (r.isEmpty) {
                    drawRect(CycloColors.Background)
                } else {
                    clipRect(r.left, r.top, r.right, r.bottom, clipOp = ClipOp.Difference) {
                        drawRect(CycloColors.Background)
                    }
                }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // O cartão tem 345 dp no Figma (393 de ecrã); em ecrãs estreitos encolhe com a proporção.
            val cardWidth = min(BedtimeWidgets.CardWidth, (maxWidth - CycloDimens.ScreenMargin * 2).value).dp
            val cardHeight = (cardWidth.value * BedtimeWidgets.cardHeight(kind) / BedtimeWidgets.CardWidth).dp
            val pxPerUnit = density.density * cardWidth.value / BedtimeWidgets.CardWidth

            // Os bitmaps do preview, desenhados fora da thread principal (o vidro é calculado píxel a
            // píxel). Guardam-se os últimos e as predefinições (a face de trás da volta).
            val images = remember(data, palette, pxPerUnit, kind) { mutableStateMapOf<WidgetStyle, ImageBitmap>() }
            LaunchedEffect(shown, data, palette, pxPerUnit, kind) {
                val current = data ?: return@LaunchedEffect
                for (s in listOf(shown, WidgetStyle.Default)) {
                    if (s in images) continue
                    val image = withContext(Dispatchers.Default) {
                        BedtimeWidgets.studioBitmap(app, appWidgetId, kind, current, WidgetLook(s, palette), pxPerUnit).asImageBitmap()
                    }
                    if (images.size >= MaxImages) images.keys.filter { it != shown && it != WidgetStyle.Default }.forEach { images.remove(it) }
                    images[s] = image
                }
            }
            var lastImage by remember { mutableStateOf<ImageBitmap?>(null) }
            val image = images[shown] ?: lastImage
            if (image != null) lastImage = image

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(content)
                    .cycloVerticalScroll(scroll)
                    .padding(top = TitleTop + topExtra, bottom = bottomInset + CycloDimens.ScreenMargin),
            ) {
                Column(modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin)) {
                    BasicText(
                        text = stringResource(R.string.studio_title),
                        style = CycloType.Greeting.copy(shadow = textShadow),
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(TitleToContent))
                    BasicText(text = stringResource(R.string.studio_preview), style = labelStyle, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(LabelToContent))
                }

                // preview-group: a wallpaper de ponta a ponta e o widget no meio.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight + WallpaperPadding * 2)
                        .onGloballyPositioned { coords ->
                            val page = pageCoords[0]
                            if (page != null && page.isAttached && coords.isAttached) hole = page.localBoundingBoxOf(coords, clipBounds = false)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    WidgetMockup(
                        image = image,
                        spinFrom = spinFrom,
                        spin = { spin.value },
                        corner = cornerDp(shown) * (cardWidth.value / BedtimeWidgets.CardWidth),
                        modifier = Modifier.size(cardWidth, cardHeight),
                        description = stringResource(R.string.studio_preview_description),
                    )
                }

                Column(modifier = Modifier.padding(horizontal = CycloDimens.ScreenMargin)) {
                    Spacer(Modifier.height(PreviewToLooks))
                    SectionLabel(stringResource(R.string.studio_looks), labelStyle)

                    // opacity-group (2054:150).
                    SliderGroup(
                        title = stringResource(R.string.studio_opacity),
                        titleStyle = groupTitleStyle,
                        levels = opacityLevels,
                        step = style.opacity,
                        onStep = { shown = shown.copy(opacity = it) },
                        onChosen = { if (it != style.opacity) commit(style.copy(opacity = it)) },
                    )
                    Spacer(Modifier.height(GroupGap))
                    // frosted-glass-group (2056:54).
                    SliderGroup(
                        title = stringResource(R.string.studio_frosted_glass),
                        titleStyle = groupTitleStyle,
                        levels = frostLevels,
                        step = style.frost,
                        onStep = { shown = shown.copy(frost = it) },
                        onChosen = { if (it != style.frost) commit(style.copy(frost = it)) },
                    )
                    Spacer(Modifier.height(GroupGap))
                    // corner-style-group (2061:145): abre o action-menu-corner-style.
                    SettingsRow(
                        label = stringResource(R.string.studio_corner_style),
                        value = cornerLabels[style.corner],
                        arrow = true,
                        arrowTint = CycloColors.TextSecondary,
                        onClickAt = { cornerAnchor = it; cornerMenuOpen = true },
                    )
                    Spacer(Modifier.height(GroupGap))
                    // back-to-default-group (2064:246).
                    SettingsButton(
                        text = stringResource(R.string.studio_restore_defaults),
                        onClick = {
                            if (style == WidgetStyle.Default || spinFrom != null) return@SettingsButton
                            val from = image
                            commit(WidgetStyle.Default)
                            if (!effects.animationsEnabled || from == null) {
                                CycloHaptics.confirm(view)
                                return@SettingsButton
                            }
                            spinFrom = from
                            scope.launch {
                                try {
                                    spin.snapTo(0f)
                                    spin.animateTo(360f, CycloSprings.Spin.float)
                                    CycloHaptics.confirm(view)
                                } finally {
                                    spin.snapTo(0f)
                                    spinFrom = null
                                }
                            }
                        },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides Backdrop.of(content)) {
            MainEdgeFades(topExtra = topExtra, bottomAmount = { bottomAmount })

            // close-and-save-b (2062:234): o visto, fixo, a 48 dp do topo e 24 dp da esquerda. As
            // mudanças já estão guardadas (guardar é automático): só fecha.
            CycloCircleButton(
                iconPath = CycloIconPaths.Check,
                // O visto vem na grelha de 15 dos menus: centrado no círculo de 39.
                iconOffset = 12f,
                contentDescription = stringResource(R.string.close),
                onClick = { close() },
                modifier = Modifier
                    .padding(top = statusTop + 24.dp, start = CycloDimens.ScreenMargin)
                    .offset(x = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2, y = -(CycloDimens.TouchTarget - CycloDimens.CircleButtonSize) / 2),
            )

            AnchoredMenuHost(
                visible = cornerMenuOpen,
                anchor = { cornerAnchor },
                onDismiss = { cornerMenuOpen = false },
                bottomMargin = bottomInset,
                alignEnd = true,
            ) {
                // action-menu-corner-style (Componentes 5, 2064:278): Subtle e Smooth, o visto na atual.
                CycloActionMenu(
                    items = cornerLabels.map(::ActionMenuItem),
                    selectedIndex = style.corner,
                    compact = true,
                    onItemClick = { index ->
                        cornerMenuOpen = false
                        if (index != style.corner) commit(style.copy(corner = index))
                    },
                )
            }
        }
    }

    // O voltar preditivo: o Studio desce a seguir ao dedo (como as definições) e, ao largar, fecha.
    PredictiveBackHandler(enabled = !closing) { progress ->
        val gesture = BackGestureVelocity()
        try {
            progress.collect { event ->
                gesture.add(event.progress)
                cover.snapTo(1f - event.progress * CycloMotion.PageBackReach)
            }
            close(-gesture.progressPerSecond * CycloMotion.PageBackReach)
        } catch (e: CancellationException) {
            scope.launch {
                cover.animateTo(1f, CycloMotion.ScreenRiseSpring, initialVelocity = -gesture.progressPerSecond * CycloMotion.PageBackReach)
            }
        }
    }
    // Declarado depois: com o menu aberto, voltar fecha primeiro o menu.
    BackHandler(enabled = cornerMenuOpen) { cornerMenuOpen = false }
}

/** Quantas imagens do preview ficam guardadas (cada uma tem ~1,5 MB). */
private const val MaxImages = 4

/**
 * Um grupo com um slider (opacity-group, frosted-glass-group): 345 x 87, raio 15, #101011 em
 * Luminosity; o título a 15 dp e o medidor de três pontos 15 dp por baixo dele.
 */
@Composable
private fun SliderGroup(
    title: String,
    titleStyle: androidx.compose.ui.text.TextStyle,
    levels: List<String>,
    step: Int,
    onStep: (Int) -> Unit,
    onChosen: (Int) -> Unit,
) {
    val last = levels.size - 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cycloCard(shadowColor = null)
            .padding(GroupInset),
    ) {
        BasicText(text = title, style = titleStyle)
        Spacer(Modifier.height(GroupInset))
        CycloMeter(
            value = step / last.toFloat(),
            levels = levels,
            description = title,
            style = MeterStyle.Accent,
            onStepChange = { onStep(it.coerceIn(0, last)) },
            onValueChange = { value -> onChosen((value * last).roundToInt().coerceIn(0, last)) },
        )
    }
}

/**
 * O widget no preview: o bitmap do widget real, com a física de um objeto pequeno nas mãos.
 * - Toque: um háptico leve ao pousar o dedo; o gel comprime (como os botões); a luz acende por baixo
 *   do dedo; o objeto inclina até [MaxTilt] para o lado tocado e segue o dedo ([CycloSprings.Tilt]),
 *   e ao largar volta a ficar direito ([CycloSprings.TiltRelease]). Arrastar aqui não faz scroll.
 * - [spin]: a volta inteira do Restore defaults (graus); até aos 90° a face da frente mostra
 *   [spinFrom] (o antes), depois disso (e de costas, sem ficar ao espelho) mostra [image].
 * - Sem animações: só o háptico e a luz.
 */
@Composable
private fun WidgetMockup(
    image: ImageBitmap?,
    spinFrom: ImageBitmap?,
    spin: () -> Float,
    corner: Dp,
    description: String,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val effects = LocalVisualEffects.current
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val pressX = remember { Animatable(0f) }
    val pressY = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    Box(
        modifier = modifier
            .graphicsLayer {
                cameraDistance = CameraDistance * density
                rotationX = tiltX.value
                // A volta é no sentido dos ponteiros do relógio visto de cima: a direita vem para a
                // frente e a esquerda vai para trás (o rotationY positivo faz o contrário).
                rotationY = tiltY.value - spin()
                val px = pressX.value
                val py = pressY.value
                if ((px != 0f || py != 0f) && size.width > 0f && size.height > 0f) {
                    val shrinkX = (PressAmount * size.width).coerceAtMost(MaxShrinkX.toPx())
                    val shrinkY = (PressAmount * size.height * HeightBias).coerceAtMost(MaxShrinkY.toPx())
                    scaleX = 1f - px * shrinkX / size.width
                    scaleY = 1f - py * shrinkY / size.height
                    transformOrigin = TransformOrigin(origin.x, origin.y)
                }
            }
            .pointerInput(effects.animationsEnabled) {
                val animate = effects.animationsEnabled
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    CycloHaptics.buttonTap(view)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    fun follow(position: Offset) {
                        if (!animate) return
                        val fx = (position.x / w).coerceIn(0f, 1f)
                        val fy = (position.y / h).coerceIn(0f, 1f)
                        // O lado tocado afunda (como carregar num objeto apoiado ao meio).
                        val targetY = (fx - 0.5f) * 2f * MaxTilt
                        val targetX = -(fy - 0.5f) * 2f * MaxTilt
                        scope.launch { tiltY.animateTo(targetY, CycloSprings.Tilt.float) }
                        scope.launch { tiltX.animateTo(targetX, CycloSprings.Tilt.float) }
                    }
                    if (animate) {
                        origin = Offset(
                            0.5f + ((down.position.x / w).coerceIn(0f, 1f) - 0.5f) * 0.35f,
                            0.5f + ((down.position.y / h).coerceIn(0f, 1f) - 0.5f) * 0.35f,
                        )
                        scope.launch { pressX.animateTo(1f, CycloSprings.GelPressX.float) }
                        scope.launch { pressY.animateTo(1f, CycloSprings.GelPressY.float) }
                    }
                    follow(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        follow(change.position)
                        // O dedo é do mockup: a página não faz scroll por baixo dele.
                        change.consume()
                    }
                    if (animate) {
                        scope.launch { tiltX.animateTo(0f, CycloSprings.TiltRelease.float) }
                        scope.launch { tiltY.animateTo(0f, CycloSprings.TiltRelease.float) }
                        val impulseX = (1f - pressX.value).coerceAtLeast(0f) * QuickTapImpulse
                        val impulseY = (1f - pressY.value).coerceAtLeast(0f) * QuickTapImpulse
                        scope.launch { pressX.animateTo(0f, CycloSprings.GelReleaseX.float, initialVelocity = impulseX) }
                        scope.launch { pressY.animateTo(0f, CycloSprings.GelReleaseY.float, initialVelocity = impulseY) }
                    }
                }
            }
            .clearAndSetSemantics { contentDescription = description },
    ) {
        // O widget: com a volta a meio, a face certa; senão, a imagem nova entra por cima da antiga.
        val spinning = spinFrom != null
        if (spinning) {
            Canvas(Modifier.fillMaxSize()) {
                val angle = spin()
                val front = if (angle < 90f) spinFrom else image
                val back = cos((angle) * PI / 180.0) < 0.0
                front ?: return@Canvas
                if (back) {
                    scale(-1f, 1f) { drawWidget(front) }
                } else {
                    drawWidget(front)
                }
            }
        } else {
            Crossfade(
                targetState = image,
                animationSpec = if (effects.animationsEnabled) CycloSprings.Gentle.float else snap(),
                label = "widgetPreview",
            ) { shownImage ->
                Canvas(Modifier.fillMaxSize()) { shownImage?.let { drawWidget(it) } }
            }
        }
        // A luz que segue o dedo, por cima do widget (só dentro da forma dele).
        Box(Modifier.fillMaxSize().cycloLightSheen(cornerRadius = corner, strength = 0.8f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWidget(image: ImageBitmap) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/**
 * Guarda o aspeto e redesenha os widgets do ecrã principal, fora do ecrã e da vida do Studio (o
 * Studio pode fechar logo a seguir a uma mudança). Mudanças seguidas: só a última conta.
 */
private object StudioSync {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    private var job: kotlinx.coroutines.Job? = null

    @Synchronized
    fun save(context: android.content.Context, style: WidgetStyle) {
        WidgetStyles.save(context, style)
        job?.cancel()
        job = scope.launch {
            kotlinx.coroutines.delay(ApplyDelayMs)
            val data = CycloRepository(context).data.first()
            BedtimeWidgets.updateAll(context, data, animate = false)
        }
    }

    /** Espera um pouco antes de redesenhar (várias mudanças seguidas fazem um só desenho). */
    private const val ApplyDelayMs = 150L
}
