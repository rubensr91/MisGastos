package com.mfreya.misgastos.domain.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cascada de heurísticas del comercio, probadas de forma aislada. */
class MerchantParserTest {

    @Test
    fun `etiqueta explícita`() {
        val result = MerchantParser.parse("Comercio: REPSOL", 0, 0)
        assertEquals("REPSOL", result.name)
        assertTrue(result.known)
    }

    @Test
    fun `etiqueta de establecimiento`() {
        assertEquals("Panadería La Espiga", MerchantParser.parse("Establecimiento: Panadería La Espiga", 0, 0).name)
    }

    @Test
    fun `conector en`() {
        assertEquals("Mercadona", MerchantParser.parse("Gastaste 12,50 € en Mercadona", 9, 16).name)
    }

    @Test
    fun `corta en el fin de frase`() {
        assertEquals(
            "GLOVO",
            MerchantParser.parse("Has gastado 45,90 € en GLOVO. Ya puedes revisarlo", 0, 0).name,
        )
    }

    @Test
    fun `mantiene abreviaturas`() {
        assertEquals(
            "MERCADONA S.L",
            MerchantParser.parse("Gastaste 12,50 € en MERCADONA S.L.", 9, 16).name,
        )
    }

    @Test
    fun `recorta coletillas`() {
        assertEquals(
            "Amazon",
            MerchantParser.parse("Gastaste 25,00 € en Amazon con tarjeta", 9, 16).name,
        )
    }

    @Test
    fun `descarta segmentos numericos y palabras clave`() {
        val result = MerchantParser.parse("Gastaste 12,50 €", 9, 16)
        assertEquals(MerchantParser.UNKNOWN, result.name)
        assertFalse(result.known)
    }

    @Test
    fun `descarta referencias a la cuenta`() {
        assertEquals(
            MerchantParser.UNKNOWN,
            MerchantParser.parse("Gastaste 12,50 € en tu cuenta bancaria principal", 9, 16).name,
        )
    }

    @Test
    fun `recorta a 48 caracteres`() {
        val result = MerchantParser.parse(
            "Transferencia bancaria urgente con referencia 12345678901234567890 ejemplo largo del texto",
            0,
            0,
        )
        assertEquals(48, result.name.length)
    }

    @Test
    fun `conserva el texto original del comercio`() {
        val result = MerchantParser.parse("Gastaste 12,50 € en Mercadona con tarjeta", 9, 16)
        assertEquals("Mercadona con tarjeta", result.raw)
        assertEquals("Mercadona", result.name)
    }
}