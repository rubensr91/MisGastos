package com.mfreya.misgastos.domain

import com.mfreya.misgastos.domain.model.Expense
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Un mes ya cerrado: cuanto se gasto, cuantos movimientos hubo y como varia frente al mes
 * anterior. Es un dato puro, sin Android, para poder testearlo en la JVM.
 */
data class MonthClose(
    val periodStart: LocalDate,
    val totalCents: Long,
    val count: Int,
    val averageCents: Long,
    /** Gasto del mes natural anterior, o 0 si no hay nada registrado en ese mes. */
    val previousTotalCents: Long,
) {

    /** Si hay un mes anterior con gasto con el que comparar. */
    val hasPrevious: Boolean get() = previousTotalCents > 0L

    /**
     * Variacion en porcentaje frente al mes anterior. `null` cuando no hay base de comparacion
     * (no habia gastos en el mes anterior). Un aumento de gasto es un numero positivo.
     */
    val variationPercent: Int?
        get() {
            if (!hasPrevious) return null
            val exact = (totalCents - previousTotalCents) * 100.0 / previousTotalCents
            // Sin redondeo simetrico: 12,5 % sube como 13 % y baja como -12 %, porque lo que
            // interesa es cuanto ha cambiado, no un redondeo que esconda el signo.
            return if (exact > 0) ceil(exact).toInt() else floor(exact).toInt()
        }

    /** `true` si se gasto mas que el mes anterior, `null` si no hay comparacion. */
    val isRising: Boolean? get() = variationPercent?.let { it > 0 }
}

/** Informe del cierre de mes: la ventana de meses mas el total del ano en curso. */
data class MonthCloseReport(
    val months: List<MonthClose>,
    val yearTotalCents: Long,
    val yearCount: Int,
    val year: Int,
)

/**
 * Agrega los gastos por meses naturales (la unidad de "mes" es el mes del calendario). Se apoya
 * en [GroupingCalculator.byPeriod] con granularidad mensual, asi que los tramos son los mismos
 * que muestra la pantalla de agrupar.
 */
object MonthCloseCalculator {

    /** Ventanas de meses que ofrece la interfaz. */
    val RANGE_OPTIONS = listOf(3, 6, 12)

    fun build(
        expenses: List<Expense>,
        months: Int = 6,
        today: LocalDate = LocalDate.now(),
    ): MonthCloseReport {
        val window = months.coerceIn(1, 24)
        val byMonth = GroupingCalculator.byPeriod(expenses, PeriodBucket.MONTH)
            .associate { group -> group.periodStart to group }

        val currentMonth = today.withDayOfMonth(1)
        val monthsWithData = (0 until window).filter { step ->
            byMonth.containsKey(currentMonth.minusMonths(step.toLong()))
        }
        val oldestWithData = monthsWithData.maxOrNull() ?: -1
        val recent = buildList {
            for (step in 0..oldestWithData) {
                val start = currentMonth.minusMonths(step.toLong())
                val group = byMonth[start]
                val previous = byMonth[start.minusMonths(1)]?.totalCents ?: 0L
                add(
                    MonthClose(
                        periodStart = start,
                        totalCents = group?.totalCents ?: 0L,
                        count = group?.count ?: 0,
                        averageCents = group?.averageCents() ?: 0L,
                        previousTotalCents = previous,
                    ),
                )
            }
        }

        val year = today.year
        val fromMillis = GroupingCalculator.startOfDayMillis(LocalDate.of(year, 1, 1))
        val toMillis = GroupingCalculator.startOfDayMillis(LocalDate.of(year + 1, 1, 1))
        val yearExpenses = expenses.filter { it.postedAt in fromMillis until toMillis }

        return MonthCloseReport(
            months = recent,
            yearTotalCents = yearExpenses.sumOf { it.amountCents },
            yearCount = yearExpenses.size,
            year = year,
        )
    }
}