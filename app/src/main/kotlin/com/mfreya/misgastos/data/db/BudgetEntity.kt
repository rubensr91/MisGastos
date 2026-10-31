package com.mfreya.misgastos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Limite mensual que el usuario ha fijado para una categoria. La categoria se guarda
 * con su texto (el mismo identificador que usan los gastos), asi que no hay que traducir
 * nada en la base de datos.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val category: String,
    val monthlyCents: Long,
    val updatedAt: Long,
)
