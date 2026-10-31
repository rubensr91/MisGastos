package com.mfreya.misgastos.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mfreya.misgastos.data.ExpenseRepository
import com.mfreya.misgastos.data.db.ExpenseFilter
import com.mfreya.misgastos.domain.StatsCalculator
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.ui.format.Dates
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(
    val expenses: List<Expense> = emptyList(),
    val totalCents: Long = 0L,
    val loading: Boolean = true,
)

class ExpenseListViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Texto del buscador, que es el unico criterio de la lista ahora mismo. */
    val currentQuery: StateFlow<String> = query

    /**
     * La lista se pide entera (del mas reciente al mas antiguo) y solo se acota con el
     * texto de busqueda, que el DAO filtra en SQL sobre comercio, categoria y texto
     * original.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListUiState> = query.flatMapLatest { text ->
        repository.observe(ExpenseFilter(query = text.ifBlank { null }))
    }.map { filtered ->
        ListUiState(
            expenses = filtered,
            totalCents = StatsCalculator.total(filtered),
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    private val grouping = MutableStateFlow(GroupingMode.DAY)

    /**
     * Criterio de las cabeceras de la lista. Vive en el ViewModel para que la eleccion se
     * conserve al salir y volver de la pantalla, igual que el texto de busqueda.
     */
    val currentGrouping: StateFlow<GroupingMode> = grouping

    fun setGrouping(mode: GroupingMode) {
        grouping.value = mode
    }

    /**
     * Categorias que ya existen en los gastos guardados, incluidas las que el usuario se
     * haya inventado, para ofrecerlas tambien en los selectores de categoria.
     */
    val categories: StateFlow<List<String>> = repository.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Anade un gasto escrito a mano (pago con tarjeta fisica, efectivo, etc). El repositorio
     * lo marca con el origen reservado "manual" y la confianza 100, y si la categoria se deja
     * en "Sin categoria" la deduce de las reglas a partir del comercio. Devuelve `false` si el
     * guardado falla.
     */
    fun addExpense(
        amountCents: Long,
        merchant: String,
        category: String,
        date: LocalDate,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val ok = runCatching {
                repository.createExpense(
                    amountCents = amountCents,
                    merchant = merchant,
                    category = category,
                    postedAt = Dates.withCurrentTime(date),
                )
            }.isSuccess
            onResult(ok)
        }
    }

    fun delete(expense: Expense) {
        viewModelScope.launch { repository.delete(expense.id) }
    }

    fun restore(expense: Expense, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.restore(expense)) }
    }

    fun updateCategory(expense: Expense, category: String) {
        viewModelScope.launch { repository.setCategory(expense, category) }
    }

    /**
     * Guarda a la vez importe, comercio y categoria de un gasto. La categoria se aplica con
     * `setCategory`, asi que la correccion tambien queda aprendida para los proximos gastos
     * de ese comercio. Devuelve `false` si el guardado falla (por ejemplo, si el importe
     * nuevo choca con el indice unico `(sourcePackage, postedAt, amountCents)`).
     */
    fun updateExpense(
        expense: Expense,
        amountCents: Long,
        merchant: String,
        category: String,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val ok = runCatching {
                repository.updateExpense(expense.id, amountCents, merchant, category)
            }.getOrDefault(false)
            onResult(ok)
        }
    }
}