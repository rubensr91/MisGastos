package com.mfreya.misgastos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets ORDER BY category")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
