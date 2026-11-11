package com.gaebriu.cyclo.data

/**
 * A água do dia (water-meter-group do Feed, Cyclo-2 2066:148, e o cartão Water da Tracking,
 * beta-3.0): quanto é adequado para a idade e como avaliar o que se bebeu. Puro (HydrationTest).
 *
 * A avaliação não é "mais é melhor": sobe à medida que se chega à quantidade adequada, fica no topo
 * numa faixa à volta dela e volta a descer quando se bebe muito acima do que é preciso, até ao pior
 * nível nas quantidades excessivas (pouco → adequado → demasiado → nocivo).
 *
 * É uma estimativa para a app, não uma recomendação médica. As quantidades vêm das ingestões
 * adequadas de água total da EFSA (2010, para temperatura e atividade moderadas): 4–8 anos 1,6 L;
 * 9–13 anos 2,1 L (rapazes) e 1,9 L (raparigas); a partir dos 14 anos 2,5 L (homens) e 2,0 L
 * (mulheres), também para os mais velhos. Cerca de 80 % dessa água vem das bebidas (o resto vem da
 * comida), e a app não sabe o sexo: usa-se a média dos dois, a 80 %, arredondada a 50 mL.
 */
object Hydration {
    /** Cada toque no "+" ou no "−" (o "250 mL" do Figma). */
    const val StepMl = 250

    /** O máximo que se regista num dia (já muito para lá do nocivo). */
    const val MaxMl = 10_000

    /** Quantos níveis tem a avaliação (os da legenda, da melhor à pior). */
    const val Levels = 5

    /**
     * A água das bebidas adequada por dia para a idade [age] (null = ainda por escrever: a de um
     * adulto), em mL.
     */
    fun targetMl(age: Int?): Int = when {
        age == null -> AdultMl
        age <= 8 -> ChildMl
        age <= 13 -> PreteenMl
        else -> AdultMl
    }

    /** 4–8 anos: 1,6 L × 0,8. */
    const val ChildMl = 1300

    /** 9–13 anos: (2,1 + 1,9) / 2 × 0,8. */
    const val PreteenMl = 1600

    /** 14 anos ou mais: (2,5 + 2,0) / 2 × 0,8. */
    const val AdultMl = 1800

    /**
     * O nível de [ml] para a idade [age]: 0 (o melhor, à volta do adequado) a 4 (o pior: quase nada,
     * ou muito acima do adequado). As faixas, em percentagem do adequado:
     * - 0: de 90 % a 140 %;
     * - 1: de 70 % a 90 %, ou até 180 %;
     * - 2: de 45 % a 70 %, ou até 230 %;
     * - 3: de 20 % a 45 %, ou até 280 %;
     * - 4: abaixo de 20 %, ou acima de 280 %.
     * Para um adulto (1,8 L): o melhor de 1,75 a 2,5 L; a partir de ~5,25 L, o pior.
     */
    fun level(ml: Int, age: Int?): Int {
        val target = targetMl(age)
        // Em inteiros (mL × 100 contra o adequado × percentagem): sem arredondamentos nos limites.
        val scaled = ml.coerceAtLeast(0).toLong() * 100
        fun atLeast(percent: Int) = scaled >= target.toLong() * percent
        fun atMost(percent: Int) = scaled <= target.toLong() * percent
        return when {
            !atLeast(LowBounds[3]) -> 4
            !atLeast(LowBounds[2]) -> 3
            !atLeast(LowBounds[1]) -> 2
            !atLeast(LowBounds[0]) -> 1
            atMost(HighBounds[0]) -> 0
            atMost(HighBounds[1]) -> 1
            atMost(HighBounds[2]) -> 2
            atMost(HighBounds[3]) -> 3
            else -> 4
        }
    }

    /** Se [ml] já passou da faixa do melhor nível (bebeu demasiado), para o TalkBack. */
    fun isAbove(ml: Int, age: Int?): Boolean = ml.toLong() * 100 > targetMl(age).toLong() * HighBounds[0]

    /** Os limites de baixo de cada nível (0, 1, 2, 3), em % do adequado. */
    private val LowBounds = intArrayOf(90, 70, 45, 20)

    /** Os limites de cima de cada nível (0, 1, 2, 3), em % do adequado. */
    private val HighBounds = intArrayOf(140, 180, 230, 280)
}
