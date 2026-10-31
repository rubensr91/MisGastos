package com.mfreya.misgastos.domain.parse

/**
 * Localiza importes en el texto de una notificación.
 *
 * Sin dependencias de Android: es Kotlin puro y testeable en la JVM.
 * La conversión a céntimos se hace parseando la cadena, nunca con Double,
 * para no arrastrar errores de coma flotante a la base de datos.
 */
object AmountParser {

    private const val CENTS = 100L

    /**
     * Acepta millares con `.`, espacio duro o espacio normal, decimales de 1 o 2
     * cifras y el símbolo `€` o la palabra `EUR`/`EURs` detrás del número.
     * El lookbehind evita partir números largos por la mitad.
     */
    private val AMOUNT_REGEX = Regex(
        "(?<!\\d)(\\d{1,3}(?:[.\u00A0 ]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)" +
            "\\s*(?i:€|eur\\b|eurs\\b)?"
    )

    private val CURRENCY_WORD = Regex("(?i)eur\\b|eurs\\b")

    data class Candidate(
        val raw: String,
        val cents: Long,
        val start: Int,
        val end: Int,
        val hasCurrency: Boolean,
        val hasTwoDigitDecimal: Boolean,
    )

    fun findAll(text: String): List<Candidate> =
        AMOUNT_REGEX.findAll(text)
            .mapNotNull { match ->
                val digits = match.groups[1]?.value ?: return@mapNotNull null
                val cents = toCents(digits) ?: return@mapNotNull null
                Candidate(
                    raw = match.value,
                    cents = cents,
                    start = match.range.first,
                    end = match.range.last + 1,
                    hasCurrency = match.value.contains('€') || CURRENCY_WORD.containsMatchIn(match.value),
                    hasTwoDigitDecimal = digits.contains(',') || digits.substringAfterLast('.', "").length == 2,
                )
            }
            .toList()

    /**
     * Convierte el número textual en céntimos.
     * El separador más a la derecha es decimal; el resto se considera millares.
     * `1.234,56` -> 123456, `3.20` -> 320, `1.234` -> 123400, `20` -> 2000.
     */
    fun toCents(rawNumber: String): Long? {
        val compact = rawNumber.trim().replace("\u00A0", "").replace(" ", "")
        if (compact.isEmpty()) return null

        val lastComma = compact.lastIndexOf(',')
        val lastDot = compact.lastIndexOf('.')

        val integerPart: String
        val fractionPart: String

        when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) {
                    integerPart = compact.substring(0, lastComma)
                    fractionPart = compact.substring(lastComma + 1)
                } else {
                    integerPart = compact.substring(0, lastDot)
                    fractionPart = compact.substring(lastDot + 1)
                }
            }
            lastComma >= 0 -> {
                val after = compact.substring(lastComma + 1)
                if (after.length == 1 || after.length == 2) {
                    integerPart = compact.substring(0, lastComma)
                    fractionPart = after
                } else {
                    integerPart = compact
                    fractionPart = ""
                }
            }
            lastDot >= 0 -> {
                val after = compact.substring(lastDot + 1)
                if (after.length == 1 || after.length == 2) {
                    integerPart = compact.substring(0, lastDot)
                    fractionPart = after
                } else {
                    integerPart = compact
                    fractionPart = ""
                }
            }
            else -> {
                integerPart = compact
                fractionPart = ""
            }
        }

        val wholeDigits = integerPart.filter { it.isDigit() }
        if (wholeDigits.isEmpty()) return null
        val whole = wholeDigits.toLongOrNull() ?: return null
        val fraction = when (fractionPart.length) {
            1 -> (fractionPart.toLongOrNull() ?: 0L) * 10
            2 -> fractionPart.toLongOrNull() ?: 0L
            else -> 0L
        }
        return whole * CENTS + fraction
    }
}