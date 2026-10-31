package com.mfreya.misgastos.domain

import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Como se agrupan los gastos en la pantalla de agrupación. */
enum class GroupMode { MERCHANT, CATEGORY, PERIOD }

/** Granularidad de los tramos de fechas. */
enum class PeriodBucket { DAY, WEEK, MONTH }

/**
 * Tramo de fechas elegido a mano en el calendario. Los dos extremos están
 * incluidos: si [start] y [end] son el mismo día, el tramo es un solo día.
 */
data class DateRange(val start: LocalDate, val end: LocalDate) {

    val isSingleDay: Boolean get() = start == end

    /** Días que abarca el tramo, contando los dos extremos. */
    fun lengthDays(): Long = ChronoUnit.DAYS.between(start, end) + 1

    /** Intercambia las fechas si vienen invertidas. */
    fun normalized(): DateRange = if (end < start) DateRange(end, start) else this
}

/**
 * Un grupo de gastos ya agregado. [key] identifica el grupo: el nombre del
 * comercio, el identificador de la categoría o la fecha ISO del inicio del tramo.
 * [periodStart]/[periodEnd] solo se rellenan cuando el grupo viene de [PeriodBucket].
 */
data class ExpenseGroup(
    val key: String,
    val totalCents: Long,
    val count: Int,
    val expenses: List<Expense>,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
) {
    /** Peso del grupo sobre el total global, entre 0 y 1. */
    fun shareOf(totalCents: Long): Float =
        if (totalCents <= 0L) 0f else this.totalCents.toFloat() / totalCents

    /** Ticket medio del grupo en céntimos. */
    fun averageCents(): Long = if (count <= 0) 0L else totalCents / count

    /** Categoría más frecuente dentro del grupo, para colorear y etiquetar. */
    fun dominantCategory(): String =
        expenses.groupingBy { it.category }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: Category.UNCLASSIFIED
}

object GroupingCalculator {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Convierte el epoch millis del gasto al día local en el que se publicó. */
    fun toLocalDate(postedAt: Long): LocalDate =
        Instant.ofEpochMilli(postedAt).atZone(zone).toLocalDate()

    /** Inicio del tramo al que pertenece [date]. Las semanas empiezan en lunes. */
    fun periodStart(date: LocalDate, bucket: PeriodBucket): LocalDate = when (bucket) {
        PeriodBucket.DAY -> date
        PeriodBucket.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong())
        PeriodBucket.MONTH -> date.withDayOfMonth(1)
    }

    /** Epoch millis del primer instante del día local de [date]. */
    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Se queda con los gastos publicados dentro del tramo, extremos incluidos.
     * Sin tramo devuelve la lista tal cual, para que el filtro sea opcional.
     */
    fun inRange(expenses: List<Expense>, range: DateRange?): List<Expense> {
        val window = range?.normalized() ?: return expenses
        val from = startOfDayMillis(window.start)
        val to = startOfDayMillis(window.end.plusDays(1))
        return expenses.filter { it.postedAt >= from && it.postedAt < to }
    }

    /** Último día (incluido) del tramo que empieza en [start]. */
    fun periodEnd(start: LocalDate, bucket: PeriodBucket): LocalDate = when (bucket) {
        PeriodBucket.DAY -> start
        PeriodBucket.WEEK -> start.plusDays(6)
        PeriodBucket.MONTH -> start.plusMonths(1).minusDays(1)
    }

    fun byMerchant(expenses: List<Expense>): List<ExpenseGroup> =
        expenses.groupBy { it.merchant }
            .map { (merchant, group) ->
                ExpenseGroup(
                    key = merchant,
                    totalCents = group.sumOf { it.amountCents },
                    count = group.size,
                    expenses = group.sortedByDescending { it.postedAt },
                )
            }
            .sortedWith(compareByDescending<ExpenseGroup> { it.totalCents }.thenBy { it.key })

    fun byCategory(expenses: List<Expense>): List<ExpenseGroup> =
        expenses.groupBy { it.category }
            .map { (category, group) ->
                ExpenseGroup(
                    key = category,
                    totalCents = group.sumOf { it.amountCents },
                    count = group.size,
                    expenses = group.sortedByDescending { it.postedAt },
                )
            }
            .sortedWith(compareByDescending<ExpenseGroup> { it.totalCents }.thenBy { it.key })

    /** Tramos ordenados del más reciente al más antiguo. Solo devuelve los que tienen gastos. */
    fun byPeriod(
        expenses: List<Expense>,
        bucket: PeriodBucket = PeriodBucket.MONTH,
    ): List<ExpenseGroup> =
        expenses.groupBy { expense ->
            periodStart(toLocalDate(expense.postedAt), bucket)
        }
            .map { (start, group) ->
                ExpenseGroup(
                    key = start.toString(),
                    totalCents = group.sumOf { it.amountCents },
                    count = group.size,
                    expenses = group.sortedByDescending { it.postedAt },
                    periodStart = start,
                    periodEnd = periodEnd(start, bucket),
                )
            }
            .sortedByDescending { it.periodStart }

    fun group(
        expenses: List<Expense>,
        mode: GroupMode,
        bucket: PeriodBucket = PeriodBucket.MONTH,
    ): List<ExpenseGroup> = when (mode) {
        GroupMode.MERCHANT -> byMerchant(expenses)
        GroupMode.CATEGORY -> byCategory(expenses)
        GroupMode.PERIOD -> byPeriod(expenses, bucket)
    }

    /** Número de días que abarca el tramo, para poder escribir "3 días" o "1 día". */
    fun periodLengthDays(start: LocalDate, end: LocalDate): Long =
        ChronoUnit.DAYS.between(start, end) + 1
}