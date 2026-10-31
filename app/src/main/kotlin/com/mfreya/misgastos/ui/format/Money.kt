package com.mfreya.misgastos.ui.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formateo de importes. Todo el dinero vive en `Long` céntimos; aquí solo se formatea para
 * mostrar. Se usa el locale del sistema para el separador decimal y de miles.
 */
object Money {

    const val SYMBOL = "€"

    private val currency: Currency? = runCatching { Currency.getInstance("EUR") }.getOrNull()

    /** Importe completo con símbolo: "12,50 €". */
    fun format(cents: Long, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        if (currency != null) format.currency = currency
        return format.format(cents / 100.0)
    }

    /** Importe con decimales solo si los hay, para cifras grandes en tablas: "1.234 €". */
    fun formatWhole(cents: Long, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        return "${format.format(cents / 100.0)} $SYMBOL"
    }

    /**
     * Versión corta para ejes y leyendas de gráficos, donde el ancho es escaso.
     * "9,50 €" · "1,2 k€" · "15 k€".
     */
    fun formatCompact(cents: Long, locale: Locale = Locale.getDefault()): String {
        val value = cents / 100.0
        val format = NumberFormat.getNumberInstance(locale)
        return when {
            value < 0 -> "-" + formatCompact(-cents, locale)
            value < 1_000 -> format.apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
                .format(value) + " $SYMBOL"

            value < 10_000 -> format.apply { maximumFractionDigits = 1; minimumFractionDigits = 0 }
                .format(value / 1_000) + " k$SYMBOL"

            else -> format.apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
                .format(value / 1_000) + " k$SYMBOL"
        }
    }

    /** Solo el número, sin símbolo. Para CSV y para concatenar en porcentajes. */
    fun formatPlain(cents: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
            .format(cents / 100.0)

    /** Reparto porcentual redondeado, con el resto absorbido por el mayor para que sume 100. */
    fun percentages(cents: List<Long>): List<Int> {
        val total = cents.sum()
        if (total <= 0L) return List(cents.size) { 0 }
        val exact = cents.map { it * 100.0 / total }
        val rounded = exact.map { Math.round(it).toInt() }
        val diff = 100 - rounded.sum()
        if (diff != 0) {
            val target = exact.indices.maxByOrNull { exact[it] - rounded[it] } ?: return rounded
            return rounded.toMutableList().apply { this[target] += diff }
        }
        return rounded
    }
}
