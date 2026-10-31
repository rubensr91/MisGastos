package com.mfreya.misgastos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantCategoryDao {

    @Query("SELECT * FROM merchant_categories")
    fun observeAll(): Flow<List<MerchantCategoryEntity>>

    @Query("SELECT COUNT(*) FROM merchant_categories")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM merchant_categories WHERE merchantKey = :merchantKey LIMIT 1")
    suspend fun find(merchantKey: String): MerchantCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MerchantCategoryEntity)

    @Query("DELETE FROM merchant_categories WHERE merchantKey = :merchantKey")
    suspend fun forget(merchantKey: String)

    @Query("DELETE FROM merchant_categories")
    suspend fun forgetAll()
}