package com.mfreya.misgastos.domain.category

import java.text.Normalizer

/**
 * Normalizacion y tokenizacion de nombres de comercio.
 *
 * Se usa para dos cosas: construir las claves con las que se memoriza una correccion manual
 * (`MERCADONA`, `Mercadona ` y `mercadona` deben ser la misma clave) y comparar una palabra clave
 * contra el texto de una notificacion sin falsos positivos por subcadenas (`bar` no debe aparecer
 * dentro de `barcelona`).
 */
object MerchantKeys {

    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val DIACRITICS = Regex("\\p{Mn}+")

    /** "BAR EL PILÓN" -> "bar el pilon" */
    fun normalize(value: String): String =
        NON_ALNUM.replace(fold(value), " ").trim()

    /** "Amazon.es" -> ["amazon", "es"] */
    fun tokenize(value: String): List<String> =
        NON_ALNUM.split(fold(value)).filter { it.isNotEmpty() }

    /** Comprueba si [needle] aparece como secuencia consecutiva de tokens dentro de [haystack]. */
    fun containsTokens(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (start in 0..(haystack.size - needle.size)) {
            var matches = true
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private fun fold(value: String): String =
        DIACRITICS.replace(Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD), "")
}
