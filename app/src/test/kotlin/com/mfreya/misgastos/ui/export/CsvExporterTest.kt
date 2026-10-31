package com.mfreya.misgastos.ui.export

import com.mfreya.misgastos.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun expense(
        amountCents: Long = 1234L,
        merchant: String = "Mercadona",
        merchantRaw: String = merchant,
        rawText: String = "Pago en Mercadona",
    ) = Expense(
        amountCents = amountCents,
        currency = "EUR",
        merchant = merchant,
        merchantRaw = merchantRaw,
        category = "Supermercado",
        postedAt = 1_700_000_000_000L,
        ingestedAt = 1_700_000_000_000L,
        rawText = rawText,
        sourcePackage = "com.example.bank",
        sourceTitle = "Banco",
        parseConfidence = 90,
    )

    @Test
    fun primeraLineaEsLaCabecera() {
        val csv = CsvExporter.build(listOf(expense()))
        val lines = csv.lines()

        assertEquals(
            "fecha,hora,importe,moneda,comercio,categoria,app_origen,confianza,texto_original",
            lines.first(),
        )
        assertEquals(2, lines.count { it.isNotBlank() })
    }

    @Test
    fun usaPuntoDecimalYSinSeparadorDeMiles() {
        val csv = CsvExporter.build(listOf(expense(amountCents = 123_456L)))

        assertTrue(csv.contains(",1234.56,"))
    }

    @Test
    fun neutralizaFormulasAlPrincipioDeUnaCelda() {
        val csv = CsvExporter.build(
            listOf(expense(merchant = "=cmd|' /C calc'!A0", merchantRaw = "=cmd|' /C calc'!A0")),
        )

        assertTrue(csv.contains("'=cmd|' /C calc'!A0"))
        assertFalse(csv.lines()[1].startsWith("="))
    }

    @Test
    fun neutralizaFormulaAunqueEmpiecePorEspacios() {
        val csv = CsvExporter.build(listOf(expense(merchant = "  @SUM(A1)")))

        assertTrue(csv.contains("'  @SUM(A1)"))
    }

    @Test
    fun entrecomillaComasYDuplicaComillas() {
        val csv = CsvExporter.build(listOf(expense(merchant = "Farmacia, \"Central\"")))

        assertTrue(csv.contains("\"Farmacia, \"\"Central\"\"\""))
    }

    @Test
    fun aplanaSaltosDeLineaDentroDeUnaCelda() {
        val csv = CsvExporter.build(listOf(expense(rawText = "linea1\nlinea2\r\nlinea3")))

        assertTrue(csv.contains("linea1 linea2 linea3"))
        assertEquals(2, csv.lines().count { it.isNotBlank() })
    }

    @Test
    fun elBomNoFormaParteDelContenidoConstruido() {
        val csv = CsvExporter.build(listOf(expense()))

        assertFalse(csv.startsWith("\uFEFF"))
    }
}