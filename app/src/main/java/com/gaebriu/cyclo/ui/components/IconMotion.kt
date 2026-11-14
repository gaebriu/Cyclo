package com.gaebriu.cyclo.ui.components

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.gaebriu.cyclo.ui.haptics.CycloHaptics
import com.gaebriu.cyclo.ui.theme.CycloSpring
import com.gaebriu.cyclo.ui.theme.CycloSprings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** À volta de que ponto o ícone roda: o centro, o topo (pendurado) ou a ponta esquerda (a etiqueta). */
enum class IconPivot { Center, Top, Left }

/**
 * A entrada de cada ícone grande das Introduction (beta-3.2: refeitas; as da 3.1 baloiçavam com
 * pouco amortecimento e pareciam de desenho animado). Cada uma é uma pequena coreografia em
 * camadas, sempre pela mesma ordem: o holofote acende primeiro e o ícone chega a seguir, com um
 * canal principal (subir, rodar, crescer) e um secundário desfasado (a largura e a altura com molas
 * diferentes, como o gel dos botões: lê-se como matéria, não como mola). Amortecimentos ≥ 0,72 e
 * ângulos pequenos: assenta à primeira, sem tremer. O sino é a exceção pensada: toca uma vez.
 */
enum class IconMotionStyle(val pivot: IconPivot) {
    /** Cama: pousa. Desce 7 dp a abrandar, um pouco esticada, e assenta com um achatar muito leve. */
    Rest(IconPivot.Center),

    /** Sol: nasce. Sobe 12 dp devagar, cresce de 82 % e os raios rodam 40° até ao lugar. */
    Dawn(IconPivot.Center),

    /** Etiqueta: pendurada pelo furo, vira-se para nós (largura de 86 %) e balança uma vez, 8°. */
    Tag(IconPivot.Left),

    /** Calendário: uma folha que cai 10 dp e assenta com um pequeno impacto (achata 4 % e recupera). */
    Page(IconPivot.Center),

    /** Widgets: encaixam, de 78 % e −10°, com a altura um nadinha atrás da largura. */
    Assemble(IconPivot.Center),

    /** Sino: toca, pendurado pelo topo, de 9° com uma só oscilação visível; cresce de 92 %. */
    Chime(IconPivot.Top),
}

/**
 * O movimento de um ícone com holofote ([IntroIconHeader]): canais lidos só ao desenhar (rotação,
 * escala na largura e na altura, subida em dp e a luz do holofote), por cima do que o ecrã já faz
 * (o sol continua a rodar com as rodas). Em repouso: 0°, 1, 1, 0 e 1.
 *
 * - [enter]: só ao avançar (quem chama decide pela direção). Um tique muito leve quando o ícone
 *   chega ao lugar pela primeira vez (o canal principal passa pelo repouso).
 * - [settle]: uma interrupção (sair da página, mexer no controlo) nunca corta: cada canal continua
 *   de onde está, com a velocidade que tem, numa mola rápida e sem ressalto até ao repouso.
 * - [nudge]: uma interação (a primeira letra, abrir o menu, ligar um lembrete) dá um empurrão ao
 *   canal principal, somado à velocidade que já tiver.
 *
 * As corrotinas correm no [scope] do fluxo (não no de um efeito nem de uma página): avançar
 * depressa não deixa nenhuma animação congelada a meio.
 */
@Stable
class IconMotion(val style: IconMotionStyle, private val scope: CoroutineScope, private val view: View) {
    val rotation = Animatable(0f)
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)

    /** Subida em dp (negativo = para cima). */
    val lift = Animatable(0f)
    val light = Animatable(1f)

    /** A página deste ícone está à vista: o holofote respira ([IntroIconHeader]). */
    val breathing = mutableStateOf(false)

    // Já a assentar: chamadas seguidas (o sol, a cada fotograma da roda) não relançam nada.
    private var settling = false

    // Cada entrada tem um número: as fases atrasadas de uma entrada antiga não correm depois de outra.
    private var generation = 0

    private fun channels() = listOf(rotation, scaleX, scaleY, lift)

    fun enter() {
        settling = false
        val run = ++generation
        scope.launch {
            light.snapTo(0f)
            light.animateTo(1f, CycloSprings.IconLight.float)
        }
        // O ícone chega um pouco depois da luz: é ela que o revela.
        fun later(ms: Long, block: suspend () -> Unit) = scope.launch {
            delay(ms)
            if (run == generation) block()
        }
        // O tique: quando o canal principal passa pelo repouso pela primeira vez.
        fun settleTick(anim: Animatable<Float, AnimationVector1D>, from: Float, rest: Float, spring: CycloSpring, delayMs: Long) = later(delayMs) {
            var ticked = false
            anim.animateTo(rest, spring.float) {
                if (!ticked && (value - rest) * (from - rest) <= 0f) {
                    ticked = true
                    CycloHaptics.iconSettle(view)
                }
            }
        }
        scope.launch {
            when (style) {
                IconMotionStyle.Rest -> {
                    lift.snapTo(-7f)
                    scaleX.snapTo(0.96f)
                    scaleY.snapTo(1.05f)
                    settleTick(lift, -7f, 0f, CycloSprings.IconSettle, IconDelayMs)
                    later(IconDelayMs) { scaleX.animateTo(1f, CycloSprings.IconDeformX.float) }
                    // A altura passa um nadinha do lugar (achata ao pousar) e volta.
                    later(IconDelayMs) { scaleY.animateTo(1f, CycloSprings.IconDeformY.float, initialVelocity = -0.5f) }
                }
                IconMotionStyle.Dawn -> {
                    lift.snapTo(12f)
                    rotation.snapTo(-40f)
                    scaleX.snapTo(0.82f)
                    scaleY.snapTo(0.82f)
                    later(IconDelayMs) { lift.animateTo(0f, CycloSprings.IconRise.float) }
                    settleTick(rotation, -40f, 0f, CycloSprings.IconTurn, IconDelayMs)
                    later(IconDelayMs) { scaleX.animateTo(1f, CycloSprings.IconGrow.float) }
                    later(IconDelayMs + 40) { scaleY.animateTo(1f, CycloSprings.IconGrow.float) }
                }
                IconMotionStyle.Tag -> {
                    rotation.snapTo(8f)
                    scaleX.snapTo(0.86f)
                    lift.snapTo(-3f)
                    settleTick(rotation, 8f, 0f, CycloSprings.IconSway, IconDelayMs)
                    later(IconDelayMs) { scaleX.animateTo(1f, CycloSprings.IconTurnIn.float) }
                    later(IconDelayMs) { lift.animateTo(0f, CycloSprings.IconSettle.float) }
                }
                IconMotionStyle.Page -> {
                    lift.snapTo(-10f)
                    rotation.snapTo(-3f)
                    scaleY.snapTo(1.03f)
                    settleTick(lift, -10f, 0f, CycloSprings.IconDrop, IconDelayMs)
                    later(IconDelayMs) { rotation.animateTo(0f, CycloSprings.IconDrop.float) }
                    // O impacto: quando a folha chega, achata depressa e recupera devagar.
                    later(IconDelayMs + PageImpactMs) {
                        scaleY.animateTo(0.96f, CycloSprings.IconImpact.float)
                        scaleY.animateTo(1f, CycloSprings.IconRecover.float)
                    }
                    later(IconDelayMs + PageImpactMs) {
                        scaleX.animateTo(1.02f, CycloSprings.IconImpact.float)
                        scaleX.animateTo(1f, CycloSprings.IconRecover.float)
                    }
                }
                IconMotionStyle.Assemble -> {
                    rotation.snapTo(-10f)
                    scaleX.snapTo(0.78f)
                    scaleY.snapTo(0.78f)
                    lift.snapTo(4f)
                    settleTick(rotation, -10f, 0f, CycloSprings.IconGrow, IconDelayMs)
                    later(IconDelayMs) { scaleX.animateTo(1f, CycloSprings.IconGrow.float) }
                    later(IconDelayMs + 50) { scaleY.animateTo(1f, CycloSprings.IconGrow.float) }
                    later(IconDelayMs) { lift.animateTo(0f, CycloSprings.IconSettle.float) }
                }
                IconMotionStyle.Chime -> {
                    rotation.snapTo(9f)
                    scaleX.snapTo(0.92f)
                    scaleY.snapTo(0.92f)
                    settleTick(rotation, 9f, 0f, CycloSprings.IconRing, IconDelayMs)
                    later(IconDelayMs) { scaleX.animateTo(1f, CycloSprings.IconSettle.float) }
                    later(IconDelayMs) { scaleY.animateTo(1f, CycloSprings.IconSettle.float) }
                }
            }
        }
    }

    fun settle() {
        if (settling) return
        settling = true
        generation++
        channels().forEach { anim ->
            val rest = if (anim === scaleX || anim === scaleY) 1f else 0f
            if (anim.isRunning || anim.value != rest) scope.launch { anim.animateTo(rest, CycloSprings.Exit.float) }
        }
        if (light.isRunning || light.value != 1f) scope.launch { light.animateTo(1f, CycloSprings.Exit.float) }
    }

    fun nudge() {
        settling = false
        when (style) {
            IconMotionStyle.Tag -> scope.launch {
                rotation.animateTo(0f, CycloSprings.IconSway.float, initialVelocity = rotation.velocity + 26f)
            }
            IconMotionStyle.Page -> {
                // Como tocar numa folha: achata e volta, com a largura um nadinha ao contrário.
                scope.launch { scaleY.animateTo(1f, CycloSprings.IconRecover.float, initialVelocity = scaleY.velocity - 0.55f) }
                scope.launch { scaleX.animateTo(1f, CycloSprings.IconRecover.float, initialVelocity = scaleX.velocity + 0.25f) }
            }
            IconMotionStyle.Chime -> scope.launch {
                rotation.animateTo(0f, CycloSprings.IconRing.float, initialVelocity = rotation.velocity + 70f)
            }
            else -> Unit
        }
    }

    private companion object {
        /** O ícone espera esta parte da luz (ms) antes de começar. */
        const val IconDelayMs = 70L

        /** Quando a folha do calendário chega ao lugar (a mola da queda já quase lá). */
        const val PageImpactMs = 200L
    }
}
