package com.gaebriu.cyclo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Tempos e curvas das animações. O Figma não os define, por isso são PROPOSTAS
 * para afinares à vista na app a correr.
 *
 * As molas vêm todas dos presets do motor de animações ([CycloSprings], em Springs.kt); aqui ficam
 * com o nome do sítio onde se usam. Animações novas usam os presets.
 */
object CycloMotion {
    // Introduction 0
    const val GlowDurationMs = 1800
    const val LettersStartDelayMs = 250

    /** O "Hello" dura 2 s, do início da primeira letra ao fim da última. */
    const val HelloDurationMs = 2000
    const val LetterStaggerMs = 100

    /** Espaçamento máximo entre letras do "Hello" (15 % do tamanho da letra), a fechar até zero. */
    const val HelloLetterSpacingEm = 0.15f

    const val ButtonsDelayMs = 1600
    const val ButtonsDurationMs = 700

    /** Distância de onde os botões sobem. */
    val ButtonsRise = 72.dp

    /** Desaceleração suave (Material 3, "emphasized decelerate"). */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /**
     * Subida e descida de ecrãs inteiros (Introduction 1...). Mola sem ressalto, ao estilo
     * de uma folha do iOS / Material You: reage ao toque e pode ser interrompida sem saltos.
     * O fade e o desfoque dos textos seguem o progresso desta mola, por isso têm a mesma duração.
     */
    val ScreenRiseSpring = CycloSprings.Sheet.float

    /** Ecrã que recua por baixo de outro: zoom out até 92 % e um véu #010101 a 24 %. */
    const val BackgroundScale = 0.92f
    const val BackgroundOverlayAlpha = 0.24f

    /** Paginação horizontal (Introduction 2...): o ecrã que sai desloca-se 30 % da largura. */
    const val PageParallax = 0.3f

    /** Desaparecimento do texto "Scroll or tap to interact". */
    const val HintFadeDurationMs = 350

    /** Transição da caixa do nome entre escrever e ouvir (ícones, borda, textos). */
    val ListeningSpring = CycloSprings.fromStiffness(Spring.StiffnessMediumLow, 0.8f).float

    /** Volta de um ciclo dos gradientes animados da caixa a ouvir (texto e borda, da esquerda para a direita). */
    const val ListeningShimmerMs = 2200

    /** A caixa do nome a ir para cima do teclado (e a voltar). Mola suave, ao estilo iOS / Material You. */
    val KeyboardSpring = CycloSprings.Overlay.float

    /** Fades de elementos soltos (ex.: o subtítulo do Introduction 3 com o teclado). Mola sem ressalto. */
    val FadeSpring = CycloSprings.Gentle.float

    /** Zoom out subtil de um botão enquanto está carregado: botões grandes e botões só de ícone. */
    const val ButtonPressScale = 0.96f
    const val IconButtonPressScale = 0.88f

    /** Mola do zoom dos botões: responde logo ao toque e assenta com um ligeiro ressalto. */
    val PressSpring = CycloSprings.Press.float

    /** Caixa do nome ao completar o ditado: a de escrever entra com zoom in, a de ouvir sai com zoom out. */
    const val CompletionZoom = 0.94f
    val CompletionSpring = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

    /** Menus e folhas que aparecem por cima do ecrã (entrada e saída). */
    val OverlaySpring = CycloSprings.Overlay.float

    /**
     * Crescimento da barra de progresso. O limiar de visibilidade é fino: com o de omissão (0,01)
     * a mola parava ~3,5 dp antes do fim e a barra saltava esse bocado no último frame.
     */
    val ProgressSpring = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 130f,
        visibilityThreshold = 0.0005f,
    )

    /**
     * Fade do ecrã de abertura (#010101) para o primeiro ecrã. Curto, porque a entrada do
     * Introduction 0 já começa por trás da abertura.
     */
    const val SplashFadeDurationMs = 250L

    /**
     * Rotação do sol do Introduction 2 por cada valor que muda: uma volta inteira por volta do
     * seletor (360 / 24 = 15° por hora, 360 / 60 = 6° por minuto). Sentido horário ao aumentar,
     * anti-horário ao diminuir.
     */
    const val SunDegreesPerTurn = 360f

    /** Mola da rotação do sol: sem ressalto e suave, para acompanhar mudanças rápidas sem saltos. */
    val SunRotationSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 90f)

    /**
     * Regresso do scroll à ponta depois de esticar (rubber band) e o bounce de um lançamento que
     * bate na ponta, como no iOS: mola criticamente amortecida, sem oscilar.
     */
    val ScrollBounceSpring = CycloSprings.Bounce.float

    /** A caixa do nome do Introduction 5 a alargar ao escrever (e a voltar): a mesma mola do teclado. */
    val NameFieldExpandSpring = KeyboardSpring

    /** Introduction 6: tempo de espera depois das animações, antes de ir para o Feed. */
    const val AllSetHoldMs = 1000L

    /** Troca entre o botão Next e o "Done" roxo no último passo da criação de uma rotina. */
    val ButtonSwapSpring = FadeSpring

    /**
     * Entrada do Feed (ao abrir a app ou ao acabar o tutorial): cada bloco aparece com fade,
     * desfoque 6 a 0 e uma subida curta, um a seguir ao outro. PROPOSTAS.
     */
    const val FeedRevealStaggerMs = 90
    const val FeedRevealDurationMs = 900
    val FeedRevealRise = 24.dp

    /** O brilho do topo do Feed a nascer (como o do Introduction 0). */
    const val FeedGlowDurationMs = 1600

    /** Troca entre o Feed e as rotinas guardadas sem animações no sistema: só o fade. */
    const val TabSwitchDurationMs = 320

    /** Feed ⇄ rotinas guardadas com o dedo: fração da largura a partir da qual a página muda ao largar. */
    const val PageSwipeThreshold = 0.5f

    /** Velocidade (larguras por segundo) a partir da qual um lançamento muda de página, mesmo curto. */
    const val PageFlingVelocity = 0.55f

    /** A página que sai, enquanto a outra entra, fica um pouco mais escura (opacidade mínima). */
    const val PageOutgoingAlpha = 0.4f

    /** Reordenar (long press e arrastar): o cartão cresce um pouco e ganha sombra. */
    const val DragLiftScale = 1.04f

    /** Arrastar perto do topo ou do fundo faz scroll, até esta velocidade (dp por frame a 60 Hz). */
    const val DragAutoScrollMaxDp = 14f


    /** Quanto tempo a barra de baixo (undo-action) fica à vista antes de sair sozinha. */
    const val MessageDurationMs = 4000L

    /** A barra de baixo (undo-action) a entrar e a sair. */
    val MessageSpring = OverlaySpring

    /** Caixa de pesquisa das rotinas guardadas a abrir e fechar. */
    val SearchSpring = KeyboardSpring

    /** Cartões das rotinas a mudar (ligar/desligar, aparecer, desaparecer). */
    val CardSpring = CycloSprings.Gentle.float

    /**
     * Brilho do topo ao mudar entre o Feed e as rotinas guardadas: volta a crescer e a acender, e
     * passa para o gradiente da outra página. Um pouco mais longo do que a troca do conteúdo. PROPOSTA.
     */
    const val GlowTabDurationMs = 900

    /** Cores do brilho a mudar de faixa horária (ex.: às 15 h). Lento, para quase não se notar. PROPOSTA. */
    const val SkyChangeDurationMs = 2400

    /**
     * Número da cafeína a rolar: o novo entra por baixo (ou por cima, ao tirar) e o antigo sai,
     * com fade e desfoque. Mola rápida com um ressalto muito leve. PROPOSTA.
     */
    val CounterSpring = CycloSprings.Snappy.float

    /** Quanto o número da cafeína se desloca a rolar, em fração da sua altura. */
    const val CounterRollDistance = 0.7f

    /**
     * Bola do medidor de emoções a assentar num ponto depois de a largar: parte com a velocidade do
     * dedo e assenta com um ressalto curto. PROPOSTA.
     */
    val MoodSettleSpring = CycloSprings.MoodSettle.float

    /** A bola do medidor cresce um pouco enquanto se segura (e volta com uma mola). */
    const val MoodBallHeldScale = 1.2f
    val MoodBallScaleSpring = CycloSprings.MoodHold.float

    /** Quanto o lançamento conta para escolher o ponto (segundos de movimento projetado). */
    const val MoodFlingProjectionS = 0.12f

    /** Resistência para lá das pontas do medidor (fração do que o dedo anda) e o máximo que passa. */
    const val MoodRubberBand = 0.3f
    const val MoodRubberBandMax = 0.06f

    /** Menus (action-menu) a abrir e a fechar: de 90 % com desfoque a 100 % nítido. */
    const val MenuStartScale = 0.9f

    /** Menus (action-menu) a abrir: mola com um ressalto muito leve no zoom. PROPOSTA. */
    val MenuOpenSpring = CycloSprings.Enter.float

    /** Menus (action-menu) a fechar: mais rápido e sem ressalto. PROPOSTA. */
    val MenuCloseSpring = CycloSprings.Exit.float
}
