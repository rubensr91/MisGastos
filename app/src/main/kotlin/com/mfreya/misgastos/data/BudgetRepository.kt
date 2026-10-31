package com.mfreya.misgastos.data

import com.mfreya.misgastos.data.db.BudgetDao
import com.mfreya.misgastos.data.db.BudgetEntity
import com.mfreya.misgastos.domain.BudgetLimit
import com.mfreya.misgastos.domain.BudgetCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Guarda los limites mensuales por categoria. El gasto de cada categoria no vive aqui: se
 * agrega aparte (ver [com.mfreya.misgastos.domain.BudgetCalculator]), asi que este repositorio
 * solo trabaja con la tabla `budgets`.
 */
class BudgetRepository(
    private val dao: BudgetDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeLimits(): Flow<List<BudgetLimit>> = dao.observeAll().map { rows ->
        rows.map { BudgetLimit(category = it.category, limitCents = it.monthlyCents) }
    }

    /**
     * Fija el limite de una categoria. Un importe de cero o negativo se interpreta como
     * "quitar el presupuesto", que es lo que hace el boton de borrar.
     */
    suspend fun setLimit(category: String, monthlyCents: Long) {
        val key = BudgetCalculator.normalize(category)
        if (monthlyCents <= 0L) {
            dao.delete(key)
            return
        }
        dao.upsert(BudgetEntity(category = key, monthlyCents = monthlyCents, updatedAt = now()))
    }

    suspend fun clear(category: String) {
        dao.delete(BudgetCalculator.normalize(category))
    }

    suspend fun clearAll() = dao.deleteAll()
}
