package com.mfreya.misgastos.domain

import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense

/**
 * Como va una categoria frente al limite mensual que el usuario le ha fijado.
 *
 * `spentCents` es siempre el gasto del mes en curso, de modo que el estado se puede
 * calcular en Kotlin (y probar en la JVM) sin escribir SQL de agregacion.
 */
data class BudgetStatus(
    val category: String,
    val spentCents: Long,
    val limitCents: Long,
) {
    /** True si no hay limite util con el que comparar. */
    val hasLimit: Boolean get() = limitCents > 0L

    /** Avance sobre el limite, acotado a 1.0 para que la barra nunca se salga. */
    val progress: Float
        get() = if (!hasLimit) 0f else (spentCents.toDouble() / limitCents.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()

    val isOver: Boolean get() = hasLimit && spentCents > limitCents

    /** Cuanto queda; cero (nunca negativo) cuando ya se ha superado. */
    val remainingCents: Long get() = (limitCents - spentCents).coerceAtLeast(0L)

    /** Cuanto se ha pasado del limite; cero cuando no se ha superado. */
    val overCents: Long get() = (spentCents - limitCents).coerceAtLeast(0L)
}

/** Un limite mensual sin el gasto asociado, para editarlo en Ajustes. */
data class BudgetLimit(
    val category: String,
    val limitCents: Long,
)

/**
 * Calcula el estado de los presupuestos a partir del gasto ya agregado por categoria.
 *
 * Se mantiene en el dominio, sin Room, para poder probarlo en la JVM: la base de datos
 * solo guarda los limites y el gasto se agrega con [StatsCalculator.byCategory].
 */
object BudgetCalculator {

    /** Estado de una sola categoria. */
    fun status(category: String, spentCents: Long, limitCents: Long): BudgetStatus =
        BudgetStatus(
            category = category,
            spentCents = spentCents.coerceAtLeast(0L),
            limitCents = limitCents,
        )

    /**
     * Une los limites guardados con el gasto del mes en curso. El gasto viene ya filtrado
     * por el mes (ver [StatsCalculator.ofMonth]), aqui solo se agrupa por categoria.
     *
     * El orden es: primero las que se han pasado, despues las mas cerca del limite y
     * al final las que tienen gasto minimo, cada grupo ordenado por categoria.
     */
    fun limitsOf(
        limits: List<BudgetLimit>,
        monthExpenses: List<Expense>,
    ): List<BudgetStatus> {
        if (limits.isEmpty()) return emptyList()
        val spent = StatsCalculator.byCategory(monthExpenses)
            .associate { it.category to it.totalCents }
        return limits
            .map { limit -> status(limit.category, spent[limit.category] ?: 0L, limit.limitCents) }
            .sortedWith(
                compareByDescending<BudgetStatus> { it.isOver }
                    .thenByDescending { it.progress }
                    .thenBy { it.category },
            )
    }

    /** Normaliza el texto de la categoria antes de guardarlo como clave. */
    fun normalize(category: String): String = Category.normalize(category)
}
