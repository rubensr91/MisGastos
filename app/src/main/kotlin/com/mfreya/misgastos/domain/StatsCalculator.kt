package com.mfreya.misgastos.domain

import com.mfreya.misgastos.domain.model.Expense
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyPoint(val date: LocalDate, val totalCents: Long)

data class MerchantTotal(val merchant: String, val totalCents: Long, val count: Int)

data class CategoryTotal(val category: String, val totalCents: Long, val count: Int)

data class Summary(
    val todayCents: Long,
    val monthCents: Long,
    val previousMonthCents: Long,
    val totalCount: Int,
    val monthCount: Int,
    val dailySeries: List<DailyPoint>,
    val movingAverage: List<Double>,
    val topMerchants: List<MerchantTotal>,
    val byCategory: List<CategoryTotal>,
)

object StatsCalculator {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayStart(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthStart(date: LocalDate): Long =
        date.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun total(expenses: List<Expense>): Long = expenses.sumOf { it.amountCents }

    fun totalBetween(expenses: List<Expense>, from: Long, to: Long): Long =
        expenses.filter { it.postedAt in from until to }.sumOf { it.amountCents }

    fun totalToday(expenses: List<Expense>, today: LocalDate = LocalDate.now()): Long =
        totalBetween(expenses, dayStart(today), dayStart(today.plusDays(1)))

    fun totalThisMonth(expenses: List<Expense>, today: LocalDate = LocalDate.now()): Long {
        val start = monthStart(today)
        return totalBetween(expenses, start, start.plusMonthLength())
    }

    fun totalPreviousMonth(expenses: List<Expense>, today: LocalDate = LocalDate.now()): Long {
        val start = monthStart(today.minusMonths(1))
        return totalBetween(expenses, start, monthStart(today))
    }

    fun dailySeries(
        expenses: List<Expense>,
        days: Int = 30,
        today: LocalDate = LocalDate.now(),
    ): List<DailyPoint> {
        val first = today.minusDays((days - 1).toLong())
        val buckets = LongArray(days)
        expenses.forEach { expense ->
            val date = expense.postedAt.toLocalDate()
            if (date >= first && date <= today) {
                val index = (date.toEpochDay() - first.toEpochDay()).toInt()
                if (index in 0 until days) buckets[index] += expense.amountCents
            }
        }
        return (0 until days).map { DailyPoint(first.plusDays(it.toLong()), buckets[it]) }
    }

    fun movingAverage(values: List<DailyPoint>, window: Int = 7): List<Double> {
        if (window <= 1) return values.map { it.totalCents / 100.0 }
        val result = ArrayList<Double>(values.size)
        values.forEachIndexed { index, _ ->
            val from = maxOf(0, index - window + 1)
            val slice = values.subList(from, index + 1)
            result += slice.sumOf { it.totalCents } / slice.size / 100.0
        }
        return result
    }

    fun topMerchants(expenses: List<Expense>, limit: Int = 5): List<MerchantTotal> =
        expenses.groupBy { it.merchant }
            .map { (merchant, group) ->
                MerchantTotal(merchant, group.sumOf { it.amountCents }, group.size)
            }
            .sortedWith(compareByDescending<MerchantTotal> { it.totalCents }.thenBy { it.merchant })
            .take(limit)

    fun byCategory(expenses: List<Expense>): List<CategoryTotal> =
        expenses.groupBy { it.category }
            .map { (category, group) ->
                CategoryTotal(category, group.sumOf { it.amountCents }, group.size)
            }
            .sortedByDescending { it.totalCents }

    /** Gastos cuyo momento cae dentro del mes natural de [today]. */
    fun ofMonth(expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<Expense> {
        val start = monthStart(today)
        val end = start.plusMonthLength()
        return expenses.filter { it.postedAt in start until end }
    }

    fun build(expenses: List<Expense>, today: LocalDate = LocalDate.now()): Summary {
        val series = dailySeries(expenses, 30, today)
        val monthExpenses = ofMonth(expenses, today)
        return Summary(
            todayCents = totalToday(expenses, today),
            monthCents = monthExpenses.sumOf { it.amountCents },
            previousMonthCents = totalPreviousMonth(expenses, today),
            totalCount = expenses.size,
            monthCount = monthExpenses.size,
            dailySeries = series,
            movingAverage = movingAverage(series),
            topMerchants = topMerchants(monthExpenses),
            byCategory = byCategory(monthExpenses),
        )
    }

    /** Ticket medio del mes en céntimos (0 si no hay gastos). */
    fun averageTicket(monthCents: Long, monthCount: Int): Long =
        if (monthCount <= 0) 0L else monthCents / monthCount

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    private fun Long.plusMonthLength(): Long =
        monthStart(
            Instant.ofEpochMilli(this).atZone(zone).toLocalDate().plusMonths(1),
        )
}