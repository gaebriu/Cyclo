package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * O motor de animações do Cyclo: as molas de todas as animações, num só sítio.
 *
 * Como no iOS (UIKit / SpringBoard), cada mola descreve-se pela [CycloSpring.response] (quanto tempo
 * leva, em segundos, um ciclo da mola sem amortecimento: o "peso" do movimento) e pela
 * [CycloSpring.dampingFraction] (1 = sem ressalto; abaixo de 1, um ressalto controlado). A rigidez
 * do Compose é (2π / response)².
 *
 * Regras da linguagem de movimento:
 * - Tudo o que o dedo larga parte com a velocidade dele (initialVelocity): sem saltos.
 * - Cada interação tem a sua mola (não a mesma para tudo): tocar responde depressa com um ressalto
 *   pequeno; ecrãs inteiros não ressaltam; o que entra pode passar um pouco e voltar; o que sai sai
 *   depressa e sem ressalto.
 * - Sem animações no sistema ([com.gaebriu.cyclo.ui.effects.VisualEffects.animationsEnabled]),
 *   usa-se [motionSpec], que dá [snap]: os movimentos passam a cortes ou fades.
 *
 * As molas que já existiam ([CycloMotion]) são estes presets, com os mesmos valores.
 */
class CycloSpring(val response: Float, val dampingFraction: Float) {
    /** Rigidez do Compose para esta resposta. */
    val stiffness: Float get() = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = dampingFraction, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

    /** A mola para valores Float (a mais usada). */
    val float: SpringSpec<Float> by lazy { spec() }

    /** Para tamanhos e deslocações em píxeis (AnimatedVisibility, animateContentSize...). */
    val intSize: SpringSpec<IntSize> by lazy { spec(IntSize.VisibilityThreshold) }
    val intOffset: SpringSpec<IntOffset> by lazy { spec(IntOffset.VisibilityThreshold) }
}

/** Os presets, do mais rápido ao mais lento. */
object CycloSprings {
    /** Uma mola a partir da rigidez do Compose (para os valores que já estavam assim). */
    fun fromStiffness(stiffness: Float, dampingRatio: Float) =
        CycloSpring((2.0 * PI / kotlin.math.sqrt(stiffness.toDouble())).toFloat(), dampingRatio)

    /** Sair (menus a fechar, cartões apagados): depressa e sem ressalto. */
    val Exit = fromStiffness(700f, Spring.DampingRatioNoBouncy)

    /** Pegar num elemento (long press do arrastar): cresce logo, com um ressalto curto. */
    val Lift = CycloSpring(response = 0.25f, dampingFraction = 0.7f)

    /** Resposta ao toque (zoom dos botões): imediata, a assentar com um ligeiro ressalto. */
    val Press = fromStiffness(500f, 0.65f)

    /** Entrar (menus a abrir, cartões a voltar): rápido, com um ressalto muito leve. */
    val Enter = fromStiffness(520f, 0.78f)

    /** Pequenas mudanças de estado (interruptores, contadores). */
    val Snappy = fromStiffness(420f, 0.78f)

    /** Largar um elemento no seu lugar (fim do arrastar): encaixa com um ressalto subtil. */
    val Drop = CycloSpring(response = 0.32f, dampingFraction = 0.78f)

    /** Os vizinhos a abrir espaço (reordenar, apagar, repor): coordenado, quase sem ressalto. */
    val Reflow = CycloSpring(response = 0.34f, dampingFraction = 0.86f)

    /** Repor (o "Undo"): zoom com um overshoot controlado. */
    val PopIn = CycloSpring(response = 0.42f, dampingFraction = 0.66f)

    /** Mudar de página com o dedo (Feed ⇄ rotinas guardadas): segue a velocidade, sem ressalto visível. */
    val Page = CycloSpring(response = 0.36f, dampingFraction = 0.94f)

    /** Menus, folhas, teclado, a barra de baixo: suave. */
    val Overlay = fromStiffness(Spring.StiffnessMediumLow, 0.85f)

    /** Fades e mudanças de estado sem movimento. */
    val Gentle = fromStiffness(Spring.StiffnessMediumLow, Spring.DampingRatioNoBouncy)

    /**
     * action-menu-big a abrir (todas as folhas de baixo): sobe com peso e assenta com um ressalto
     * quase invisível. Começa com a velocidade do dedo quando volta depois de um arrastar.
     */
    val SheetOpen = CycloSpring(response = 0.42f, dampingFraction = 0.88f)

    /** action-menu-big a fechar: um pouco mais rápido do que a abrir, sem ressalto, com a velocidade do dedo. */
    val SheetClose = CycloSpring(response = 0.34f, dampingFraction = 1f)

    /** Ecrãs inteiros a subir e a descer (Introduction 1, editor, definições): sem ressalto. */
    val Sheet = fromStiffness(280f, Spring.DampingRatioNoBouncy)

    /** Regresso do scroll esticado (rubber band). */
    val Bounce = fromStiffness(200f, Spring.DampingRatioNoBouncy)

    /** O desfoque do ecrã por baixo do loading-data a aumentar: suave e sem ressalto. */
    val VeilIn = CycloSpring(response = 0.62f, dampingFraction = 1f)

    /** O mesmo desfoque a diminuir, à saída: um pouco mais rápido do que a entrar. */
    val VeilOut = CycloSpring(response = 0.5f, dampingFraction = 1f)

    /** O texto do loading-data a sair: mais rápido do que o véu, sem ressalto. */
    val ContentOut = CycloSpring(response = 0.4f, dampingFraction = 1f)

    /*
     * Gel (toque como um material flexível, ver components/Pressable.kt). Cada eixo tem a sua mola,
     * com uma ligeira diferença de fase: é essa diferença que se lê como gelatina.
     */

    /** Gel a comprimir na largura: imediato, sem ressalto (o dedo carrega). */
    val GelPressX = CycloSpring(response = 0.2f, dampingFraction = 1f)

    /** Gel a comprimir na altura: um pouco atrás da largura. */
    val GelPressY = CycloSpring(response = 0.24f, dampingFraction = 1f)

    /** Gel a recuperar na largura: passa um pouco da forma original e volta (ressalto controlado). */
    val GelReleaseX = CycloSpring(response = 0.34f, dampingFraction = 0.58f)

    /** Gel a recuperar na altura: mais lento e um pouco mais solto do que a largura. */
    val GelReleaseY = CycloSpring(response = 0.4f, dampingFraction = 0.54f)

    /** O reflexo de luz a seguir o dedo: quase imediato, só alisa o movimento (não é um cursor). */
    val LightFollow = CycloSpring(response = 0.14f, dampingFraction = 1f)

    /** O reflexo de luz a acender (dedo a tocar). */
    val LightIn = CycloSpring(response = 0.22f, dampingFraction = 1f)

    /** O reflexo de luz a apagar (dedo a sair): mais lento, como a luz a dissipar-se. */
    val LightOut = CycloSpring(response = 0.5f, dampingFraction = 1f)

    /** Menus (action-menu) a abrir na altura: escorrem do campo um pouco depois da largura. */
    val MenuPour = CycloSpring(response = 0.36f, dampingFraction = 0.74f)

    /** Menus (action-menu) a fechar na altura: recolhem um pouco antes da largura. */
    val MenuRetract = fromStiffness(900f, Spring.DampingRatioNoBouncy)

    /**
     * A bola do medidor de emoções a assentar num ponto depois de a largar: parte com a velocidade
     * do dedo e assenta com peso, a passar o ponto só o que a velocidade pede (Beta 2.2: 0,86; a
     * 0,72 da beta-1.4 passava ~6 % de um passo e voltava, um ressalto de brinquedo).
     */
    val MoodSettle = CycloSpring(response = 0.36f, dampingFraction = 0.86f)

    /**
     * A bola do medidor a crescer quando se segura e a voltar ao largar (Beta 2.2: firme, sem o
     * tremor da 0,66 de antes).
     */
    val MoodHold = CycloSpring(response = 0.26f, dampingFraction = 0.84f)

    /*
     * Gráficos da Tracking (Beta 2.0). As colunas e os blocos crescem da base na primeira entrada da
     * sessão, em cascata, todos a partir do MESMO relógio ([springProgress]); depois, quando um valor
     * muda (o sono de hoje registado, um café a mais), a coluna ou o bloco vai para o novo valor com
     * [ChartChange].
     */

    /** Uma coluna ou um bloco a crescer da base: assenta com um ressalto quase invisível. */
    val ChartGrow = CycloSpring(response = 0.6f, dampingFraction = 0.82f)

    /**
     * O sol do Introduction 2 a seguir as rodas da hora (Beta 2.2): vai atrás delas com algum peso
     * (não colado ao dedo) e, quando a roda pára, chega e passa um nadinha antes de assentar.
     */
    val SunFollow = CycloSpring(response = 0.55f, dampingFraction = 0.8f)

    /**
     * A roda da hora a assentar num valor quando se larga devagar (ou com os botões do TalkBack):
     * rápida e firme, a parte da velocidade do dedo (Beta 2.2; antes a 0,8 da rigidez média).
     */
    val PickerSettle = CycloSpring(response = 0.24f, dampingFraction = 0.86f)

    /**
     * A entrada do Feed (Beta 2.2): o brilho do topo a nascer, lento e sem ressalto, e cada bloco
     * da cascata ([com.gaebriu.cyclo.ui.effects.StaggeredReveal]) a aparecer e a subir.
     */
    val FeedGlow = CycloSpring(response = 1.1f, dampingFraction = 1f)
    val Reveal = CycloSpring(response = 0.6f, dampingFraction = 1f)

    /**
     * Os interruptores das definições (Beta 2.2: preset; antes rigidez 520 e 0,72, fora do motor):
     * rápidos, com um ressalto quase impercetível, iguais a ligar e a desligar.
     */
    val Toggle = CycloSpring(response = 0.28f, dampingFraction = 0.78f)

    /** O sol a crescer um pouco com a velocidade a que roda, e a voltar quando abranda. */
    val SunSwell = CycloSpring(response = 0.32f, dampingFraction = 1f)

    /** Um valor do gráfico a mudar: mais rápido do que a entrada, sem tremer. */
    val ChartChange = CycloSpring(response = 0.42f, dampingFraction = 0.9f)

    /** O sleep-evaluation-prompt a sair (registado ou fechado): encolhe sem ressalto, mais rápido do que entra. */
    val CardCollapse = CycloSpring(response = 0.38f, dampingFraction = 1f)

    /*
     * Widget Studio (beta-3.0): o mockup do widget é um objeto físico pequeno, que reage ao dedo.
     */

    /**
     * A inclinação do mockup (até 6°) a seguir o dedo: rápida, sem ressalto, só alisa os eventos do
     * toque (como a luz, [LightFollow], mas com um pouco mais de peso, por ser o objeto a mexer-se).
     */
    val Tilt = CycloSpring(response = 0.2f, dampingFraction = 1f)

    /** O mockup a voltar a ficar direito quando o dedo sai: suave e sem ressalto, mais lento do que segue. */
    val TiltRelease = CycloSpring(response = 0.45f, dampingFraction = 1f)

    /**
     * O mockup a dar uma volta inteira (360° no eixo Y) ao repor as predefinições: arranca com peso,
     * acelera, e trava a chegar ao fim, a passar só uns 4° antes de assentar (amortecimento 0,82):
     * um objeto com massa a rodar, não uma rotação linear.
     */
    val Spin = CycloSpring(response = 1.1f, dampingFraction = 0.82f)
}

/**
 * Onde está, ao fim de [seconds], uma mola [spring] que parte do repouso em 0 para 1, com a
 * velocidade inicial [velocity] (unidades por segundo). É a solução exata da mola, por isso vários
 * elementos podem segui-la a partir do MESMO relógio (sincronizados ao frame), em vez de cada um
 * ter a sua animação.
 */
fun springProgress(seconds: Float, spring: CycloSpring, velocity: Float = 0f): Float {
    if (seconds <= 0f) return 0f
    val w = 2.0 * PI / spring.response
    val z = spring.dampingFraction.toDouble()
    val t = seconds.toDouble()
    // Deslocamento até ao alvo: começa em 1, com a velocidade ao contrário.
    val v = -velocity.toDouble()
    val x = when {
        z < 1.0 -> {
            val wd = w * sqrt(1.0 - z * z)
            exp(-z * w * t) * (cos(wd * t) + (v + z * w) / wd * sin(wd * t))
        }
        z == 1.0 -> exp(-w * t) * (1.0 + (v + w) * t)
        else -> {
            val root = sqrt(z * z - 1.0)
            val r1 = -w * (z - root)
            val r2 = -w * (z + root)
            val c2 = (v - r1) / (r2 - r1)
            (1.0 - c2) * exp(r1 * t) + c2 * exp(r2 * t)
        }
    }
    return (1.0 - x).toFloat()
}

/** Transição suave de 0 a 1 entre [from] e [to] (velocidade zero nas duas pontas, sem esquinas). */
fun smoothStep(value: Float, from: Float = 0f, to: Float = 1f): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** [spec] com as animações ligadas; um corte sem elas. */
fun <T> motionSpec(animationsEnabled: Boolean, spec: FiniteAnimationSpec<T>): AnimationSpec<T> =
    if (animationsEnabled) spec else snap()

/**
 * Resistência do iOS para lá de um limite (rubber band): cada vez anda menos do que o dedo.
 * [overflow] é o que o dedo passou do limite e [dimension] o tamanho de referência (ex.: a largura).
 */
fun rubberBand(overflow: Float, dimension: Float, coefficient: Float = 0.55f): Float {
    if (dimension <= 0f) return 0f
    return sign(overflow) * (1f - 1f / (abs(overflow) * coefficient / dimension + 1f)) * dimension
}

/**
 * Para onde vai um lançamento: a posição mais a distância que a velocidade ainda percorreria a
 * desacelerar como no iOS (taxa 0,998/ms), para escolher a página ou o lugar onde assentar.
 */
fun projectedEnd(position: Float, velocity: Float, decelerationRate: Float = 0.998f): Float =
    position + velocity / 1000f * decelerationRate / (1f - decelerationRate)
