package com.mfreya.misgastos.domain.parse

import java.text.Normalizer

/**
 * Detecta avisos que describen **dinero que entra** (nomina, bizum recibido, devolucion,
 * transferencia a tu favor...) para no contarlos como gasto.
 *
 * La app solo guarda gastos, asi que un ingreso que se colase sumaria al total del mes y
 * deformaria los graficos. Este detector se aplica al texto ya normalizado del aviso, antes
 * de decidir si se guarda.
 *
 * Se buscan formulas concretas en lugar de palabras sueltas: "abono" o "recibido" por si
 * solas aparecen tambien en avisos de gasto ("recibido un cargo"), mientras que "abono de"
 * o "has recibido una transferencia" describen dinero entrante sin ambiguedad.
 *
 * Kotlin puro, sin dependencias de Android: testeable en la JVM.
 */
object IncomeDetector {

    /**
     * Formulas que solo aparecen cuando el dinero entra en la cuenta. El texto se compara
     * plegado (minusculas y sin tildes), asi que aqui van sin acentos.
     */
    private val RECEIPT_PHRASES = listOf(
        // Ingresos y transferencias entrantes
        "ingreso recibido",
        "abono recibido",
        "te han ingresado",
        "te hemos ingresado",
        "se ha ingresado",
        "ingreso de",
        "ingreso por",
        "ingreso en tu cuenta",
        "ingreso en cuenta",
        "te han pagado",
        "te ha pagado",
        "te han transferido",
        "te ha transferido",
        "te hemos transferido",
        "transferencia recibida",
        "transferencia a tu favor",
        // Bizum entrante
        "bizum recibido",
        "te ha enviado un bizum",
        "te han enviado un bizum",
        // Abonos, devoluciones y reembolsos
        "abono de",
        "abono por",
        "abono en tu cuenta",
        "se ha abonado",
        "te hemos abonado",
        "reembolso",
        "devolucion de",
        "devolucion del",
        "devolucion tarjeta",
        "te hemos devuelto",
        "te han devuelto",
        // Nominas y prestaciones
        "nomina",
        "salario",
        "pension",
    )

    private val RECEIPT = Regex(
        "\\b(?:" + RECEIPT_PHRASES.joinToString("|") { Regex.escape(it) } + ")\\b",
    )

    private val RECEIVED = Regex("\\b(?:has recibido|hemos recibido|ha recibido|recibiste)\\b")

    private val RECEIVED_CHARGE = Regex(
        "\\b(?:has|hemos|ha) recibido (?:un |una |el |la |tu )?" +
            "(?:cargo|compra|cobro|factura|recibo|adeudo)\\b",
    )

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val SPACES = Regex("[ \\t\\u00A0\\u202F\\u2007\\u2009]+")

    fun looksLikeIncome(title: String?, body: String): Boolean =
        matches(title) || matches(body)

    private fun matches(text: String?): Boolean {
        if (text == null) return false
        val folded = fold(text)
        if (RECEIPT.containsMatchIn(folded)) return true
        return RECEIVED.containsMatchIn(folded) && !RECEIVED_CHARGE.containsMatchIn(folded)
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(SPACES, " ")
}