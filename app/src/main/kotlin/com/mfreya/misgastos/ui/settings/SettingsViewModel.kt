package com.mfreya.misgastos.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mfreya.misgastos.data.AppSettings
import com.mfreya.misgastos.data.ExpenseRepository
import com.mfreya.misgastos.data.category.ExpenseClassifier
import com.mfreya.misgastos.data.BudgetRepository
import com.mfreya.misgastos.data.category.LearnedCategory
import com.mfreya.misgastos.data.db.SourcePackageCount
import com.mfreya.misgastos.domain.BudgetLimit
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.domain.model.ThemeMode
import com.mfreya.misgastos.ui.support.NotificationAccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val settings: AppSettings,
    private val repository: ExpenseRepository,
    private val classifier: ExpenseClassifier,
    private val budgets: BudgetRepository,
) : ViewModel() {

    var accessGranted by mutableStateOf(false)
        private set
    var listenerConnected by mutableStateOf(false)
        private set
    var batteryUnrestricted by mutableStateOf(true)
        private set

    val allowAllPackages: StateFlow<Boolean> = settings.allowAllPackages
    val allowedPackages: StateFlow<Set<String>> = settings.allowedPackages
    val storedCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val needsReview: StateFlow<List<Expense>> = repository.observeNeedsReview(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sourcePackages: StateFlow<List<String>> = repository.observeSourcePackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cuantos gastos aporta cada app, para que el usuario pueda decidir con datos. */
    val sourcePackageCounts: StateFlow<List<SourcePackageCount>> =
        repository.observeSourcePackageCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categorias reales de los gastos, para poder presupuestar tambien las creadas a mano. */
    val expenseCategories: StateFlow<List<String>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reglas memorizadas a partir de las correcciones manuales del usuario. */
    val learned: StateFlow<List<LearnedCategory>> = classifier.observeLearned()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val learnedCount: StateFlow<Int> = learned
        .map { rules -> rules.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Limites mensuales fijados por el usuario, uno por categoria. */
    val budgetLimits: StateFlow<List<BudgetLimit>> = budgets.observeLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    init {
        refresh()
    }

    fun refresh() {
        accessGranted = NotificationAccess.isGranted(appContext)
        listenerConnected = NotificationAccess.isListenerConnected(appContext)
        batteryUnrestricted = NotificationAccess.isIgnoringBatteryOptimizations(appContext)
    }

    suspend fun allExpenses(): List<Expense> = repository.observeAll().first()

    fun setAllowAllPackages(allowAll: Boolean) {
        settings.setAllowAllPackages(allowAll)
    }

    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        settings.setPackageAllowed(packageName, allowed)
    }

    /**
     * Olvida todos los gastos de una app y le quita el permiso de lectura, que es justo lo
     * que se espera al quitarle acceso a algo.
     */
    fun forgetPackage(sourcePackage: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val deleted = repository.deleteFromPackage(sourcePackage)
            settings.setPackageAllowed(sourcePackage, false)
            onResult(deleted)
        }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    fun updateMerchant(expense: Expense, merchant: String) {
        viewModelScope.launch { repository.updateMerchant(expense.id, merchant) }
    }

    fun updateCategory(expense: Expense, category: String) {
        viewModelScope.launch { repository.setCategory(expense, category) }
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.setThemeMode(mode)
    }

    /** Fija el limite de una categoria. Un importe de cero o menor la quita. */
    fun setBudget(category: String, monthlyCents: Long) {
        viewModelScope.launch { budgets.setLimit(category, monthlyCents) }
    }

    fun clearBudget(category: String) {
        viewModelScope.launch { budgets.clear(category) }
    }

    fun forgetLearned(merchantKey: String) {
        viewModelScope.launch { classifier.forget(merchantKey) }
    }

    fun forgetAllLearned() {
        viewModelScope.launch { classifier.forgetAll() }
    }
}