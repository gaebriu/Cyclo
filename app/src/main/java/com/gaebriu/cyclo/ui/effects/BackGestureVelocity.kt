package com.gaebriu.cyclo.ui.effects

import android.os.SystemClock

/**
 * A velocidade do gesto de voltar preditivo (progresso por segundo), a partir dos eventos de
 * progresso que vão chegando (Beta 2.2). O sistema não a dá: sem ela, ao largar, a mola que acaba o
 * movimento recomeçava parada e o ecrã perdia a continuidade com o dedo. Quem usa multiplica pelo
 * alcance do gesto ([com.gaebriu.cyclo.ui.theme.CycloMotion.PageBackReach]) para a ter nas unidades
 * da posição que move, e passa-a como velocidade inicial da mola.
 */
class BackGestureVelocity {
    private var lastProgress = 0f
    private var lastAt = -1L

    /** Progresso por segundo, filtrado (os eventos chegam ao ritmo dos fotogramas, com tremor). */
    var progressPerSecond = 0f
        private set

    fun add(progress: Float) {
        val now = SystemClock.uptimeMillis()
        if (lastAt >= 0L && now > lastAt) {
            val instant = (progress - lastProgress) * 1000f / (now - lastAt)
            progressPerSecond += (instant - progressPerSecond) * Smoothing
        }
        lastProgress = progress
        lastAt = now
    }

    private companion object {
        /** O peso de cada evento novo no filtro. */
        const val Smoothing = 0.6f
    }
}
