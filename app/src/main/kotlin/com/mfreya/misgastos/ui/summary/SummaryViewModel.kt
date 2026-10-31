package com.mfreya.misgastos.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mfreya.misgastos.data.BudgetRepository
import com.mfreya.misgastos.data.ExpenseRepository
import com.mfreya.misgastos.domain.BudgetCalculator
import com.mfreya.misgastos.domain.BudgetStatus
import com.mfreya.misgastos.domain.MonthCloseCalculator
import com.mfreya.misgastos.domain.MonthCloseReport
import com.mfreya.misgastos.domain.StatsCalculator
import com.mfreya.misgastos.domain.Summary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SummaryViewModel(
    repository: ExpenseRepository,
    budgets: BudgetRepository,
) : ViewModel() {

    val summary: StateFlow<Summary?> = repository.observeAll()
        .map { expenses ->
            if (expenses.isEmpty()) null else StatsCalculator.build(expenses)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Ventana del cierre de mes: 3, 6 o 12 meses. */
    private val _monthRange = MutableStateFlow(6)
    val monthRange: StateFlow<Int> = _monthRange

    val monthClose: StateFlow<MonthCloseReport?> = combine(
        repository.observeAll(),
        monthRange,
    ) { expenses, months ->
        if (expenses.isEmpty()) null else MonthCloseCalculator.build(expenses, months = months)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /**
     * Estado de cada presupuesto frente a lo gastado este mes. Vacio mientras el usuario no
     * haya fijado ningun limite, y en ese caso la pantalla no pinta la seccion.
     */
    val budgetStatus: StateFlow<List<BudgetStatus>> = combine(
        budgets.observeLimits(),
        repository.observeAll(),
    ) { limits, expenses ->
        BudgetCalculator.limitsOf(limits, StatsCalculator.ofMonth(expenses))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun setMonthRange(months: Int) {
        _monthRange.value = months
    }
}