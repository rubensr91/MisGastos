package com.mfreya.misgastos.domain.parse

enum class TransactionKind {
    EXPENSE,
    INCOME,
}

/**
 * Resultado de interpretar el texto de una notificación de gasto.
 *
 * - [amountCents] importe en céntimos (evita errores de coma flotante).
 * - [merchant] nombre limpio y recortado, pensado para agrupar en estadísticas.
 * - [merchantRaw] fragmento tal cual aparece tras el importe, antes de limpiar.
 * - [confidence] 0..100; por debajo de 50 la app pide revisión manual.
 * - [kind] si el aviso describe un gasto o dinero recibido. Solo se guardan los gastos.
 */
data class ParsedExpense(
    val amountCents: Long,
    val currency: String,
    val merchant: String,
    val merchantRaw: String,
    val confidence: Int,
    val normalizedText: String,
    val kind: TransactionKind = TransactionKind.EXPENSE,
) {
    val merchantKnown: Boolean
        get() = merchant != MerchantParser.UNKNOWN
}