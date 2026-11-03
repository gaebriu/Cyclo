package com.gaebriu.cyclo.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Um cartão a partir-se em partículas (apagar uma rotina): o cartão é fotografado e cortado numa
 * grelha de pedaços pequenos, que se espalham a partir de um ponto com velocidade, gravidade,
 * rotação, encolhem e desvanecem. Todas as partículas desenham-se num só Canvas (não há um
 * composable por partícula), e só enquanto há explosões a decorrer.
 */
@Stable
class ShatterState {
    internal val bursts = mutableStateListOf<Burst>()
    internal var frameNanos by mutableLongStateOf(0L)

    /**
     * Parte [image] (o cartão, já desenhado) que está em [topLeft] (coordenadas do [ShatterOverlay]),
     * a partir de [origin] (relativo ao cartão; ex.: o botão de apagar).
     */
    fun burst(image: ImageBitmap, topLeft: Offset, origin: Offset, density: Density) {
        bursts += Burst(image, topLeft, origin, density.density)
    }

    internal class Burst(
        val image: ImageBitmap,
        val topLeft: Offset,
        origin: Offset,
        dpToPx: Float,
    ) {
        /** A primeira frame em que se desenha (0 = ainda não começou). */
        var startNanos = 0L
        val particles: List<Particle>

        init {
            val random = Random(image.hashCode())
            val cell = (ParticleCellDp * dpToPx).roundToInt().coerceAtLeast(4)
            val columns = (image.width + cell - 1) / cell
            val rows = (image.height + cell - 1) / cell
            val maxDistance = hypot(image.width.toFloat(), image.height.toFloat()).coerceAtLeast(1f)
            particles = buildList {
                for (row in 0 until rows) {
                    for (column in 0 until columns) {
                        val x = column * cell
                        val y = row * cell
                        val w = min(cell, image.width - x)
                        val h = min(cell, image.height - y)
                        if (w <= 0 || h <= 0) continue
                        val cx = x + w / 2f
                        val cy = y + h / 2f
                        // Sai a direito a partir do ponto de origem, com um desvio aleatório.
                        val angle = kotlin.math.atan2(cy - origin.y, cx - origin.x) + (random.nextFloat() - 0.5f) * 1.1f
                        val distance = hypot(cx - origin.x, cy - origin.y) / maxDistance
                        val speed = (MinSpeedDp + random.nextFloat() * (MaxSpeedDp - MinSpeedDp)) * dpToPx
                        add(
                            Particle(
                                src = IntOffset(x, y),
                                size = IntSize(w, h),
                                start = Offset(x.toFloat(), y.toFloat()),
                                vx = cos(angle) * speed,
                                // Um impulso para cima, como no iOS: as partículas sobem e caem.
                                vy = sin(angle) * speed - UpwardKickDp * dpToPx * (0.6f + random.nextFloat() * 0.6f),
                                spin = (random.nextFloat() - 0.5f) * MaxSpinDegrees * 2f,
                                // Onda: as mais perto do ponto de origem partem primeiro.
                                delay = distance * WaveSeconds + random.nextFloat() * 0.03f,
                                life = LifeSeconds * (0.75f + random.nextFloat() * 0.35f),
                            ),
                        )
                    }
                }
            }
        }

        val durationSeconds: Float = particles.maxOfOrNull { it.delay + it.life } ?: 0f
    }

    internal class Particle(
        val src: IntOffset,
        val size: IntSize,
        val start: Offset,
        val vx: Float,
        val vy: Float,
        val spin: Float,
        val delay: Float,
        val life: Float,
    )
}

/** Onde as explosões se desenham: por cima da lista, do tamanho dela. */
@Composable
fun ShatterOverlay(state: ShatterState, modifier: Modifier = Modifier) {
    // O relógio só corre enquanto há explosões.
    val running = state.bursts.isNotEmpty()
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (state.bursts.isNotEmpty()) {
            withFrameNanos { now ->
                state.frameNanos = now
                state.bursts.forEach { if (it.startNanos == 0L) it.startNanos = now }
                state.bursts.removeAll { burst ->
                    burst.startNanos > 0 && (now - burst.startNanos) / 1e9f > burst.durationSeconds
                }
            }
        }
    }
    if (!running) return
    val gravity = remember { GravityDp }
    Canvas(modifier = modifier) {
        val now = state.frameNanos
        val g = gravity * density
        state.bursts.forEach { burst ->
            // A primeira frame da explosão marca o seu início.
            val start = if (burst.startNanos == 0L) now else burst.startNanos
            val elapsed = ((now - start) / 1e9f).coerceAtLeast(0f)
            burst.particles.forEach { p ->
                val t = elapsed - p.delay
                val progress = if (t <= 0f) 0f else (t / p.life).coerceIn(0f, 1f)
                if (progress >= 1f) return@forEach
                val tt = max(t, 0f)
                val x = burst.topLeft.x + p.start.x + p.vx * tt
                val y = burst.topLeft.y + p.start.y + p.vy * tt + 0.5f * g * tt * tt
                // Desvanece e encolhe com uma curva de saída (rápido no fim).
                val alpha = (1f - progress).pow(1.3f)
                val scale = 1f - 0.4f * progress
                val pivot = Offset(x + p.size.width / 2f, y + p.size.height / 2f)
                rotate(p.spin * progress, pivot) {
                    scale(scale, scale, pivot) {
                        drawImage(
                            image = burst.image,
                            srcOffset = p.src,
                            srcSize = p.size,
                            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                            dstSize = p.size,
                            alpha = alpha,
                        )
                    }
                }
            }
        }
    }
}

// Física das partículas (PROPOSTAS para afinar à vista).
private const val ParticleCellDp = 9f
private const val MinSpeedDp = 90f
private const val MaxSpeedDp = 420f
private const val UpwardKickDp = 160f
private const val GravityDp = 1400f
private const val MaxSpinDegrees = 160f
private const val WaveSeconds = 0.12f
private const val LifeSeconds = 0.8f
