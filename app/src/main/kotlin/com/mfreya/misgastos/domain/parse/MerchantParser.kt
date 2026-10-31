package com.mfreya.misgastos.domain.parse

import java.text.Normalizer

/**
 * Extrae el comercio de una notificación de gasto mediante una cascada de
 * heurísticas: etiqueta explícita ("Comercio: REPSOL"), conectores "en ...",
 * y por último el segmento más largo que no sea ruido bancario.
 *
 * Kotlin puro: se ejecuta en la JVM y tiene tests unitarios sin emulador.
 */
object MerchantParser {

    const val UNKNOWN = "Desconocido"
    const val MAX_LENGTH = 48

    private val EN_TRIGGER = Regex("(?i)\\ben\\s+([^\\n]+)")
    private val LABEL = Regex(
        "(?i)\\b(?:comercio|comerciante|establecimiento|destinatario|concepto)\\s*:\\s*([^\\n]+)"
    )

    private val SEGMENT_SPLIT = Regex("[.;:!?|,\\n]+| {2,}")

    /** Palabras que nunca son un comercio. */
    private val KEYWORD_SEGMENTS = setOf(
        "gastaste", "gastado", "has", "pagado", "pagaste", "compra", "pago", "importe",
        "aviso", "notificacion", "irregularidad", "operacion", "total", "nuevo",
        "nueva", "cuenta", "importe total", "movimiento", "cargo", "transaccion",
    )

    /** Coletillas que se recortan del final del comercio. */
    private val TAILS = listOf(
        "operacion realizada",
        "realizada con tarjeta",
        "con tarjeta de credito",
        "con tarjeta de debito",
        "con tu tarjeta",
        "con tarjeta",
        "en tu cuenta principal",
        "en tu cuenta",
        "de tu cuenta",
        "tu tarjeta",
        "tarjeta de debito",
        "tarjeta de credito",
    )

    private val REJECT_PREFIXES = listOf(
        "tu cuenta", "la cuenta", "el banco", "una cuenta", "su cuenta", "este banco",
    )

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

    data class Result(
        val name: String,
        val raw: String,
        val known: Boolean,
    )

    fun parse(text: String, amountStart: Int, amountEnd: Int): Result {
        val safeEnd = amountEnd.coerceIn(0, text.length)
        val safeStart = amountStart.coerceIn(0, safeEnd)
        val tail = text.substring(safeEnd)

        for (raw in cascade(text, tail)) {
            val cleaned = clean(raw) ?: continue
            return Result(name = cleaned, raw = raw.trim(), known = true)
        }
        return Result(name = UNKNOWN, raw = "", known = false)
    }

    private fun cascade(text: String, tail: String): List<String> = buildList {
        labelMatch(tail)?.let { add(it) }
        afterEn(tail)?.let { add(it) }
        bestSegment(tail)?.let { add(it) }
        labelMatch(text)?.let { add(it) }
        afterEn(text)?.let { add(it) }
        bestSegment(text)?.let { add(it) }
    }

    private fun labelMatch(text: String): String? = LABEL.find(text)?.groupValues?.get(1)

    private fun afterEn(text: String): String? = EN_TRIGGER.find(text)?.groupValues?.get(1)

    private fun bestSegment(text: String): String? =
        SEGMENT_SPLIT.split(text)
            .map { it.trim() }
            .map { afterEn(it) ?: it }
            .filter { it.length >= 3 }
            .filterNot { looksLikeAmount(it) }
            .filterNot { isKeywordSegment(it) }
            .filterNot { isRejectedPrefix(it) }
            .maxByOrNull { it.length }

    private fun clean(raw: String): String? {
        var value = cutAtSentenceEnd(raw)
        value = stripTails(value).trim()
        value = value.trim('.', '!', '?', ',', ';', ':', '|', '·').trim()
        if (value.length < 3) return null
        if (isRejectedPrefix(value)) return null
        if (value.length > MAX_LENGTH) value = value.take(MAX_LENGTH).trimEnd()
        return value.ifBlank { null }
    }

    /** Corta en `.`/`!`/`?` seguidos de mayúscula, evitando abreviaturas tipo "S.L.". */
    private fun cutAtSentenceEnd(text: String): String {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if ((char == '¿' || char == '¡') && index > 0 && text[index - 1].isWhitespace()) {
                return text.substring(0, index)
            }
            if (char == '.' || char == '!' || char == '?') {
                var next = index + 1
                while (next < text.length && text[next].isWhitespace()) next++
                if (next >= text.length) return text.substring(0, index)
                if (text[next].isUpperCase() && !isAbbreviation(text, next)) {
                    return text.substring(0, index)
                }
            }
            index++
        }
        return text
    }

    /** "S.L.", "U.S." o iniciales sueltas no marcan fin de frase. */
    private fun isAbbreviation(text: String, next: Int): Boolean {
        var cursor = next
        var letters = 0
        while (cursor < text.length && text[cursor].isUpperCase()) {
            letters++
            cursor++
        }
        if (letters > 3) return false
        return cursor >= text.length || text[cursor] == '.' || text[cursor] == ')'
    }

    private fun stripTails(text: String): String {
        var value = text.trim()
        var changed = true
        while (changed) {
            changed = false
            val folded = fold(value)
            for (tail in TAILS) {
                if (folded.length > tail.length && folded.endsWith(tail)) {
                    value = value.substring(0, value.length - tail.length).trim()
                    changed = true
                    break
                }
            }
        }
        return value
    }

    private fun looksLikeAmount(segment: String): Boolean {
        val meaningful = segment.filter { it.isLetterOrDigit() }
        return meaningful.isEmpty() || meaningful.all { it.isDigit() }
    }

    private fun isKeywordSegment(segment: String): Boolean {
        val folded = fold(segment)
        return KEYWORD_SEGMENTS.any { folded == it || folded.startsWith("$it ") }
    }

    private fun isRejectedPrefix(segment: String): Boolean {
        val folded = fold(segment)
        return REJECT_PREFIXES.any { folded.startsWith(it) }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
}