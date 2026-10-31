package com.mfreya.misgastos.domain.model

/**
 * Gasto ya persistido y listo para la UI. Los importes son siempre céntimos en [currency].
 */
data class Expense(
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
) {
    val amountDouble: Double get() = amountCents / 100.0

    val needsReview: Boolean get() = parseConfidence < LOW_CONFIDENCE_THRESHOLD

    companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 50
    }
}