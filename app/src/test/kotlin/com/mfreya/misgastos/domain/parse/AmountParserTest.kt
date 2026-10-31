package com.mfreya.misgastos.domain.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conversión exacta a céntimos: nunca se usa coma flotante. */
class AmountParserTest {

    @Test
    fun `coma decimal`() {
        assertEquals(123456L, AmountParser.toCents("1.234,56"))
    }

    @Test
    fun `punto decimal`() {
        assertEquals(320L, AmountParser.toCents("3.20"))
    }

    @Test
    fun `punto de millares sin decimales`() {
        assertEquals(123400L, AmountParser.toCents("1.234"))
    }

    @Test
    fun `coma de millares`() {
        assertEquals(123400L, AmountParser.toCents("1,234"))
    }

    @Test
    fun `millares repetidos`() {
        assertEquals(123456700L, AmountParser.toCents("1.234.567"))
    }

    @Test
    fun `millares con espacio y decimal con coma`() {
        assertEquals(123456L, AmountParser.toCents("1 234,56"))
    }

    @Test
    fun `entero a centimos`() {
        assertEquals(2000L, AmountParser.toCents("20"))
    }

    @Test
    fun `un solo decimal`() {
        assertEquals(950L, AmountParser.toCents("9,5"))
    }

    @Test
    fun `importe minimo`() {
        assertEquals(99L, AmountParser.toCents("0,99"))
    }

    @Test
    fun `cadena vacia devuelve null`() {
        assertNull(AmountParser.toCents(""))
    }

    @Test
    fun `sin digitos devuelve null`() {
        assertNull(AmountParser.toCents("EUR"))
    }

    @Test
    fun `encuentra importe con posicion y simbolo`() {
        val candidates = AmountParser.findAll("Gastaste 12,50 € en Mercadona")
        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals(1250L, candidate.cents)
        assertEquals(9, candidate.start)
        assertEquals(16, candidate.end)
        assertTrue(candidate.hasCurrency)
        assertTrue(candidate.hasTwoDigitDecimal)
    }

    @Test
    fun `encuentra varios importes`() {
        val candidates = AmountParser.findAll("Has gastado 12,50 € en Mercadona. Saldo 300,00 €")
        assertEquals(listOf(1250L, 30000L), candidates.map { it.cents })
    }

    @Test
    fun `no parte numeros largos`() {
        val candidates = AmountParser.findAll("Tarjeta 6437 por 8,00 €")
        assertEquals(listOf(643700L, 800L), candidates.map { it.cents })
    }
}