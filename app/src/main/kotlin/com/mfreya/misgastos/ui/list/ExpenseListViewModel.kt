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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    val selectedIds: Set<Long> = emptySet(),
)

class ExpenseListViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Texto del buscador, que es el unico criterio de la lista ahora mismo. */
    val currentQuery: StateFlow<String> = query

    /** Ids de los gastos seleccionados en modo multi-seleccion. Vacío cuando no hay seleccion. */
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val currentSelectedIds: StateFlow<Set<Long>> = selectedIds

    /** True cuando hay al menos un gasto seleccionado. */
    val selectionMode: Flow<Boolean> = selectedIds.map { it.isNotEmpty() }

    /**
     * La lista se pide entera (del mas reciente al mas antiguo) y solo se acota con el
     * texto de busqueda, que el DAO filtra en SQL sobre comercio, categoria y texto
     * original.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListUiState> = combine(
        query.flatMapLatest { text ->
            repository.observe(ExpenseFilter(query = text.ifBlank { null }))
        },
        selectedIds,
    ) { filtered, ids ->
        ListUiState(
            expenses = filtered,
            totalCents = StatsCalculator.total(filtered),
            loading = false,
            selectedIds = ids,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
        clearSelection()
    }

    private val grouping = MutableStateFlow(GroupingMode.DAY)

    /**
     * Criterio de las cabeceras de la lista. Vive en el ViewModel para que la eleccion se
     * conserve al salir y volver de la pantalla, igual que el texto de busqueda.
     */
    val currentGrouping: StateFlow<GroupingMode> = grouping

    fun setGrouping(mode: GroupingMode) {
        grouping.value = mode
        clearSelection()
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

    /** Entra en modo seleccion con un solo gasto (long-press en una fila). */
    fun enterSelection(id: Long) {
        selectedIds.value = setOf(id)
    }

    /** Alterna la seleccion de un gasto. Si no hay seleccion, entra en modo con ese gasto. */
    fun toggleSelection(id: Long) {
        val current = selectedIds.value
        selectedIds.value = when {
            current.isEmpty() -> setOf(id)
            current.contains(id) -> current - id
            else -> current + id
        }
    }

    /** Sale del modo seleccion y limpia la seleccion. */
    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    /**
     * Borra todos los gastos seleccionados en una transaccion. Devuelve los gastos borrados
     * para que el llamante pueda mostrar snackbar con deshacer (restaurar cada uno).
     */
    suspend fun deleteSelected(): List<Expense> {
        val toDelete = uiState.value.expenses.filter { it.id in selectedIds.value }
        repository.deleteAll(toDelete.map { it.id })
        clearSelection()
        return toDelete
    }

    /**
     * Combina exactamente dos gastos seleccionados en uno nuevo con los valores dados.
     * Devuelve el gasto creado para que el llamante pueda mostrar snackbar con deshacer
     * (borrar el creado y restaurar los dos originales). Devuelve `null` si no hay
     * exactamente dos seleccionados.
     */
    suspend fun mergeSelected(
        amountCents: Long,
        merchant: String,
        category: String,
        postedAt: Long,
    ): Expense? {
        val toMerge = uiState.value.expenses.filter { it.id in selectedIds.value }
        if (toMerge.size != 2) return null
        val primary = toMerge.maxByOrNull { it.postedAt }!!
        val secondary = toMerge.first { it.id != primary.id }
        val merged = repository.mergeExpenses(
            primary = primary,
            secondary = secondary,
            amountCents = amountCents,
            merchant = merchant,
            category = category,
            postedAt = postedAt,
        )
        clearSelection()
        return merged
    }
}