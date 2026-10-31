package com.mfreya.misgastos.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One "correction learned from the user": the normalised merchant text that was
 * matched, the category the user chose for it, and how many times the rule fired.
 */
@Entity(tableName = "merchant_categories")
data class MerchantCategoryEntity(
    @PrimaryKey
    val merchantKey: String,
    val category: String,
    val hits: Int,
    val learnedAt: Long,
)