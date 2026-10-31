package com.mfreya.misgastos.domain.parse

/**
 * Resultado de interpretar el texto de una notificación de gasto.
 *
 * - [amountCents] importe en céntimos (evita errores de coma flotante).
 * - [merchant] nombre limpio y recortado, pensado para agrupar en estadísticas.
 * - [merchantRaw] fragmento tal cual aparece tras el importe, antes de limpiar.
 * - [confidence] 0..100; por debajo de 50 la app pide revisión manual.
 */
data class ParsedExpense(
    val amountCents: Long,
    val currency: String,
    val merchant: String,
    val merchantRaw: String,
    val confidence: Int,
    val normalizedText: String,
) {
    val merchantKnown: Boolean
        get() = merchant != MerchantParser.UNKNOWN
}