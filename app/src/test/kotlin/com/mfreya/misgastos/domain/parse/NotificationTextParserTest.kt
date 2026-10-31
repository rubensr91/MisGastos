package com.mfreya.misgastos.domain.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formatos reales de notificación de banco. Cada banco escribe a su manera,
 * así que el parser es heurístico y estos tests son su contrato.
 */
class NotificationTextParserTest {

    private fun parse(body: String, title: String? = null) =
        NotificationTextParser.parse(title, body)

    private fun assertExpense(
        body: String,
        cents: Long,
        merchant: String,
        title: String? = null,
    ): ParsedExpense {
        val parsed = parse(body, title)
        assertNotNull("Se esperaba gasto en: $body", parsed)
        assertEquals("Importe incorrecto en: $body", cents, parsed!!.amountCents)
        assertEquals("Comercio incorrecto en: $body", merchant, parsed.merchant)
        assertEquals("EUR", parsed.currency)
        return parsed
    }

    // --- Casos canónicos ---------------------------------------------------

    @Test
    fun `formato base del banco`() {
        val parsed = assertExpense("Gastaste 12,50 € en Mercadona", 1250, "Mercadona")
        assertEquals("EUR", parsed.currency)
        assertTrue("Confianza alta esperada", parsed.confidence >= 80)
    }

    @Test
    fun `decimal con punto y sin espacio antes del simbolo`() {
        assertExpense("Gastaste 3.20€ en BAR EL PILON", 320, "BAR EL PILON")
    }

    @Test
    fun `millares con punto y decimal con coma`() {
        assertExpense("Gastaste 1.234,56 € en Amazon", 123456, "Amazon")
    }

    @Test
    fun `frase previa y coletilla posterior`() {
        assertExpense(
            "Pago realizado. Has gastado 45,90 € en GLOVO. Ya puedes revisarlo",
            4590,
            "GLOVO",
        )
    }

    @Test
    fun `compra con tarjeta y dominio`() {
        assertExpense("Compra con tarjeta 8,99 € en Amazon.es", 899, "Amazon.es")
    }

    @Test
    fun `punto doble sin espacio no parte el comercio`() {
        assertExpense("Gastaste 12,50€ en Netflix..Disponible el 03/04", 1250, "Netflix")
    }

    @Test
    fun `importe y comercio con etiquetas`() {
        assertExpense("Importe: 7,40 €  Comercio: REPSOL", 740, "REPSOL")
    }

    @Test
    fun `verbo pagado y comercio con espacios`() {
        assertExpense("Has pagado 20 € en Uber Eats", 2000, "Uber Eats")
    }

    @Test
    fun `simbolo EUR en vez de euro`() {
        assertExpense("Gastaste 1.234,56 EUR en Amazon", 123456, "Amazon")
    }

    @Test
    fun `espacios duros y nbsp se normalizan`() {
        assertExpense("Gastaste\u00A012,50\u00A0€ en Mercadona", 1250, "Mercadona")
    }

    @Test
    fun `espacios repetidos se colapsan`() {
        assertExpense("Gastaste   12,50   €   en   Mercadona", 1250, "Mercadona")
    }

    // --- Casos límite -------------------------------------------------------

    @Test
    fun `comercio muy largo se recorta a 48 caracteres`() {
        val parsed = parse(
            "Gastaste 1000 € en Transferencia bancaria urgente con referencia 12345678901234567890 ejemplo",
        )
        assertNotNull(parsed)
        assertEquals(100_000, parsed!!.amountCents)
        assertEquals(48, parsed.merchant.length)
        assertTrue(parsed.merchant.startsWith("Transferencia bancaria urgente"))
    }

    @Test
    fun `sin verbo de gasto se descarta`() {
        assertNull(parse("Recordatorio: tienes 5 notificaciones"))
    }

    @Test
    fun `aviso sin cifras no genera gasto`() {
        assertNull(parse("Nueva notificación disponible"))
    }

    @Test
    fun `importe suelto sin verbo se acepta con baja confianza`() {
        val parsed = assertExpense("12,50 €", 1250, MerchantParser.UNKNOWN)
        assertTrue("Confianza baja esperada: ${parsed.confidence}", parsed.confidence in 40..69)
    }

    @Test
    fun `millares sin decimales`() {
        assertExpense("Gastaste 1.234 € en Amazon", 123_400, "Amazon")
    }

    @Test
    fun `importe entero se escala a centimos`() {
        assertExpense("Gastaste 45 € en REPSOL", 4500, "REPSOL")
    }

    @Test
    fun `verbo compra con preposicion`() {
        assertExpense("Compra de 9,99€ en Amazon", 999, "Amazon")
    }

    @Test
    fun `numero de tarjeta no se confunde con el importe`() {
        assertExpense(
            "Compra con tarjeta terminada en 1234 por 15,00 € en Amazon",
            1500,
            "Amazon",
        )
    }

    @Test
    fun `ultimos cuatro digitos no se confunden con el importe`() {
        assertExpense(
            "Pago con tarjeta terminada en 6437 por 8,00 € en Cafetería Central",
            800,
            "Cafetería Central",
        )
    }

    @Test
    fun `abreviaturas no cortan el comercio`() {
        assertExpense("Gastaste 12,50 € en MERCADONA S.L.", 1250, "MERCADONA S.L")
    }

    @Test
    fun `interrogacion abre frase nueva`() {
        assertExpense("Gastaste 15,00 € en Burger King ¿Sabes algo?", 1500, "Burger King")
    }

    @Test
    fun `salto de linea no arrastra texto al comercio`() {
        assertExpense(
            "Gastaste 12,50 € en Mercadona\nDisponible para revisar",
            1250,
            "Mercadona",
        )
    }

    @Test
    fun `coletilla con tarjeta se recorta`() {
        assertExpense("Gastaste 25,00 € en Amazon con tarjeta", 2500, "Amazon")
    }

    @Test
    fun `segunda frase tras el punto se descarta`() {
        assertExpense("Gastaste 30,00 € en Netflix. Disponible desde hoy", 3000, "Netflix")
    }

    @Test
    fun `se queda con el primer importe cuando hay varios`() {
        assertExpense(
            "Copia: Has gastado 12,50 € en Mercadona. Saldo 300,00 €",
            1250,
            "Mercadona",
        )
    }

    @Test
    fun `el importe manda aunque el titulo tenga cifras`() {
        assertExpense("Tu tarjeta *1234 ha gastado 45,90 € en GLOVO", 4590, "GLOVO")
    }

    @Test
    fun `importe sin simbolo pero con verbo`() {
        assertExpense("Gastaste 45 en REPSOL", 4500, "REPSOL")
    }

    @Test
    fun `sin comercio devuelve desconocido`() {
        assertExpense("Importe total: 1.500,00 €", 150_000, MerchantParser.UNKNOWN)
    }

    // --- Título separado del cuerpo ---------------------------------------

    @Test
    fun `titulo con el verbo y cuerpo con el importe`() {
        val parsed = assertExpense("12,50 € en Mercadona", 1250, "Mercadona", title = "Gastaste")
        assertTrue(parsed.confidence >= 80)
    }

    @Test
    fun `titulo del banco sin verbo`() {
        assertExpense("Has gastado 45,90 € en GLOVO", 4590, "GLOVO", title = "BBVA")
    }

    @Test
    fun `titulo y cuerpo vacios no generan gasto`() {
        assertNull(parse("", null))
        assertNull(parse("   ", ""))
    }

    @Test
    fun `el texto normalizado se guarda para busqueda y csv`() {
        val parsed = assertExpense("Gastaste   12,50   €   en Mercadona", 1250, "Mercadona")
        assertEquals("Gastaste 12,50 € en Mercadona", parsed.normalizedText)
    }

    @Test
    fun `el comercio desconocido se marca como tal`() {
        val parsed = assertExpense("12,50 €", 1250, MerchantParser.UNKNOWN)
        assertTrue(!parsed.merchantKnown)
        val known = assertExpense("Gastaste 12,50 € en Mercadona", 1250, "Mercadona")
        assertTrue(known.merchantKnown)
    }

    // --- Dinero recibido ---------------------------------------------------

    @Test
    fun `transferencia recibida se marca como ingreso`() {
        val parsed = parse("Has recibido una transferencia de 250,00 € de Juan")
        assertNotNull(parsed)
        assertEquals(TransactionKind.INCOME, parsed!!.kind)
    }

    @Test
    fun `nomina ingresada se marca como ingreso`() {
        val parsed = parse("Te hemos ingresado 1.850,00 €. Nomina de octubre")
        assertNotNull(parsed)
        assertEquals(TransactionKind.INCOME, parsed!!.kind)
    }

    @Test
    fun `un gasto normal sigue siendo gasto`() {
        val parsed = assertExpense("Gastaste 12,50 € en Mercadona", 1250, "Mercadona")
        assertEquals(TransactionKind.EXPENSE, parsed.kind)
    }
}