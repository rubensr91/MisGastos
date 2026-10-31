package com.mfreya.misgastos.ui.list

import androidx.annotation.StringRes
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.PeriodBucket

/**
 * Como se agrupan las cabeceras de la lista de gastos.
 *
 * El tramo de cada periodo lo calcula [com.mfreya.misgastos.domain.GroupingCalculator], que ya
 * resuelve el lunes de la semana y el primer dia del mes, de forma que aqui solo hace falta
 * decidir el criterio y la etiqueta del selector.
 */
enum class GroupingMode(val bucket: PeriodBucket, @StringRes val labelRes: Int) {
    DAY(PeriodBucket.DAY, R.string.expenses_grouping_day),
    WEEK(PeriodBucket.WEEK, R.string.expenses_grouping_week),
    MONTH(PeriodBucket.MONTH, R.string.expenses_grouping_month),
}