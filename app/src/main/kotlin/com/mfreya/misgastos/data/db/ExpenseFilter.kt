package com.mfreya.misgastos.data.db

enum class ExpenseSort {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    AMOUNT_ASC,
}

data class ExpenseFilter(
    val from: Long? = null,
    val to: Long? = null,
    val category: String? = null,
    val query: String? = null,
    val sort: ExpenseSort = ExpenseSort.DATE_DESC,
) {
    val hasCriteria: Boolean
        get() = from != null || to != null || category != null || !query.isNullOrBlank()
}