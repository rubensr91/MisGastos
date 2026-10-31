package com.mfreya.misgastos.domain.parse

/**
 * Filtro previo y muy barato para descartar notificaciones que no pueden ser un gasto
 * **antes de tocar la base de datos**.
 *
 * La app recibe todas las notificaciones del móvil (WhatsApp, redes, correo, avisos del
 * sistema...) y para cada una abre el parser y consulta la base. Aqui se cortan de raiz
 * las que no llevan ni una pista de gasto, con dos busquedas de regex y sin construir
 * objetos.
 *
 * La condicion es exactamente la que despues exige el parser para devolver algo (un verbo
 * de gasto o el simbolo de la divisa, mas algun numero), asi que este filtro **nunca
 * descarta un gasto que el parser habria aceptado**: la decision sigue siendo del parser.
 *
 * Kotlin puro, sin dependencias de Android: testeable en la JVM.
 */
object ExpensePreFilter {

    private val CURRENCY_MARK = Regex("(?i)(€|\\beur\\b)")
    private val DIGIT = Regex("\\d")

    fun looksLikeExpense(title: String?, body: String): Boolean {
        if (!hasEvidence(title) && !hasEvidence(body)) return false
        return hasDigit(title) || hasDigit(body)
    }

    private fun hasEvidence(text: String?): Boolean =
        text != null && (SpendingHints.VERB.containsMatchIn(text) || CURRENCY_MARK.containsMatchIn(text))

    private fun hasDigit(text: String?): Boolean = text != null && DIGIT.containsMatchIn(text)
}