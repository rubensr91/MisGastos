package com.mfreya.misgastos.domain.parse

internal object SpendingHints {

    val VERB = Regex(
        "(?i)\\b(gastaste|gastado|pagaste|pagado|compra|pago|importe|cargo|transacci[oó]n)\\b",
    )
}