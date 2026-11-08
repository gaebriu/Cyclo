package com.gaebriu.cyclo.ui.main

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * O "cromado" das páginas principais (Beta 2.4): o que o mini-menu, a barra de navegação e o
 * brilho do topo mostram em cada posição das páginas.
 *
 * Numa posição qualquer, cada valor é o de sempre ([at]): a lupa só nas rotinas guardadas, o "+"
 * no Feed e nas rotinas guardadas, o ícone de cada separador quando a pílula chega a ele, a pílula
 * a esticar entre dois separadores, o brilho a encolher a meio e a passar para o gradiente das
 * rotinas guardadas. Com o dedo as páginas só mudam para a vizinha, e aí nada muda.
 *
 * Um salto por cima de uma página (tocar na Tracking estando no Feed, ou a notificação do "Log
 * sleep") passava pelos valores da página do meio: a lupa das rotinas guardadas aparecia e voltava
 * a desaparecer, o ícone delas acendia, a pílula esticava duas vezes e o brilho encolhia duas vezes.
 * Agora, num salto, cada valor vai diretamente do que se via ao da página de destino, ao ritmo da
 * mesma mola das páginas: a pílula estica uma só vez e o brilho encolhe uma só vez. A posição da
 * pílula continua a seguir as páginas.
 *
 * Um salto interrompido (outro toque, o dedo, o gesto de voltar) continua a partir do que se está
 * a ver, sem saltos: guarda-se o que se via e o movimento seguinte parte daí.
 */
@Stable
internal class PageChrome(private val lastPage: Int, private val searchPage: Int, private val trackingPage: Int) {
    private var mode by mutableIntStateOf(ModeNone)
    private var start by mutableFloatStateOf(0f)
    private var target by mutableFloatStateOf(0f)
    // O que se via quando o salto (ou o movimento a seguir a ele) começou.
    private var snapshot by mutableStateOf(FloatArray(Count))

    /** Os valores na posição [p] (ver [Search], [Plus], [Icon0], [Saved], [Stretch]). */
    fun values(p: Float, out: FloatArray = FloatArray(Count)): FloatArray {
        when (mode) {
            ModeJump -> {
                val raw = progress(p)
                // Para lá do destino (o ressalto da mola) valem os valores de sempre, que no destino são iguais.
                if (raw >= 1f) return at(p, out)
                val t = raw.coerceIn(0f, 1f)
                at(target, out)
                val from = snapshot
                for (i in 0 until Stretch) out[i] = lerp(from[i], out[i], t)
                // Uma só gota: parte do que tinha e estica-se uma vez a caminho do destino.
                out[Stretch] = maxOf((1f - t) * from[Stretch], sin(t * PI.toFloat()))
                return out
            }
            ModeFollowDrag, ModeFollowSettle -> {
                val w = if (mode == ModeFollowDrag) {
                    (abs(p - start) / FollowDistance).coerceIn(0f, 1f)
                } else {
                    progress(p).coerceIn(0f, 1f)
                }
                at(p, out)
                val from = snapshot
                for (i in 0 until Count) out[i] = lerp(from[i], out[i], w)
                return out
            }
            else -> return at(p, out)
        }
    }

    fun value(p: Float, index: Int): Float = values(p)[index]

    /** As páginas vão assentar em [to], a partir de [from] (toque, fim do dedo, voltar). */
    fun onSettle(from: Float, to: Float) {
        val crossesPage = hasPageBetween(from, to)
        if (!crossesPage && mode == ModeNone) return
        snapshot = values(from)
        start = from
        target = to
        mode = if (crossesPage) ModeJump else ModeFollowSettle
    }

    /** O dedo pegou nas páginas em [from]. */
    fun onDragStart(from: Float) {
        if (mode == ModeNone) return
        snapshot = values(from)
        start = from
        mode = ModeFollowDrag
    }

    /** As páginas assentaram no destino: daqui em diante valem os valores de sempre. */
    fun onSettled() {
        mode = ModeNone
    }

    private fun progress(p: Float): Float {
        val span = target - start
        return if (abs(span) < 1e-4f) 1f else (p - start) / span
    }

    private fun hasPageBetween(a: Float, b: Float): Boolean {
        val low = minOf(a, b) + Epsilon
        val high = maxOf(a, b) - Epsilon
        return ceil(low) <= floor(high)
    }

    /** Os valores de sempre, na posição [p]. */
    private fun at(p: Float, out: FloatArray): FloatArray {
        out[Search] = (1f - abs(p - searchPage)).coerceIn(0f, 1f)
        out[Plus] = (trackingPage - p).coerceIn(0f, 1f)
        for (i in 0..lastPage) out[Icon0 + i] = (1f - abs(p - i)).coerceIn(0f, 1f)
        out[Saved] = p.coerceIn(0f, 1f)
        val q = p.coerceIn(0f, lastPage.toFloat())
        out[Stretch] = sin((q - floor(q)) * PI.toFloat())
        return out
    }

    companion object {
        /** A lupa (rotinas guardadas). */
        const val Search = 0
        /** O "+" (Feed e rotinas guardadas). */
        const val Plus = 1
        /** O ícone do Feed; os seguintes são os dos outros separadores, pela ordem. */
        const val Icon0 = 2
        /** O brilho: de 0 (gradiente do Feed) a 1 (o das rotinas guardadas e da Tracking). */
        const val Saved = 5
        /** A pílula esticada (e o brilho encolhido) entre dois separadores: 0 a 1. */
        const val Stretch = 6
        const val Count = 7

        private const val ModeNone = 0
        private const val ModeJump = 1
        private const val ModeFollowDrag = 2
        private const val ModeFollowSettle = 3

        /** Pegando a meio de um salto, o cromado chega aos valores da posição do dedo em 1/4 de página. */
        private const val FollowDistance = 0.25f
        private const val Epsilon = 0.001f
    }
}
