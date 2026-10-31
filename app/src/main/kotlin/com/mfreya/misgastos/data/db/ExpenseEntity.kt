package com.mfreya.misgastos.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mfreya.misgastos.domain.model.Expense

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["postedAt"]),
        Index(value = ["merchant"]),
        Index(value = ["category"]),
        Index(
            value = ["sourcePackage", "postedAt", "amountCents"],
            unique = true,
        ),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amountCents: Long,
    val currency: String,
    val merchant: String,
    val merchantRaw: String,
    val category: String,
    val postedAt: Long,
    val ingestedAt: Long,
    val rawText: String,
    val sourcePackage: String,
    val sourceTitle: String,
    val parseConfidence: Int,
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    amountCents = amountCents,
    currency = currency,
    merchant = merchant,
    merchantRaw = merchantRaw,
    category = category,
    postedAt = postedAt,
    ingestedAt = ingestedAt,
    rawText = rawText,
    sourcePackage = sourcePackage,
    sourceTitle = sourceTitle,
    parseConfidence = parseConfidence,
)