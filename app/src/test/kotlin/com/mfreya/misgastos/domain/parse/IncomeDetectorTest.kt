package com.mfreya.misgastos.domain.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeDetectorTest {

    private fun isIncome(body: String, title: String? = null) =
        IncomeDetector.looksLikeIncome(title, body)

    @Test
    fun `transferencia recibida es dinero que entra`() {
        assertTrue(isIncome("Has recibido una transferencia de 250,00 € de Juan"))
    }

    @Test
    fun `bizum recibido es dinero que entra`() {
        assertTrue(isIncome("Bizum recibido de Maria: 20,00 €"))
    }

    @Test
    fun `titulo con bizum recibido basta`() {
        assertTrue(isIncome("20,00 € de Maria", title = "Bizum recibido"))
    }

    @Test
    fun `nomina ingresada es dinero que entra`() {
        assertTrue(isIncome("Te hemos ingresado 1.850,00 € en tu cuenta. Nomina de octubre"))
    }

    @Test
    fun `ingreso en cuenta sin tilde tambien se detecta`() {
        assertTrue(isIncome("Ingreso en cuenta de 300,00 EUR"))
    }

    @Test
    fun `devolucion de compra no es un gasto`() {
        assertTrue(isIncome("Devolucion de Amazon: 24,99 €"))
    }

    @Test
    fun `reembolso no es un gasto`() {
        assertTrue(isIncome("Reembolso de 12,50 € en tu tarjeta"))
    }

    @Test
    fun `abono en cuenta no es un gasto`() {
        assertTrue(isIncome("Abono de 45,00 € en tu cuenta"))
    }

    @Test
    fun `transferencia a tu favor no es un gasto`() {
        assertTrue(isIncome("Transferencia a tu favor de 90,00 €"))
    }

    @Test
    fun `gasto normal no se confunde con ingreso`() {
        assertFalse(isIncome("Gastaste 12,50 € en Mercadona"))
        assertFalse(isIncome("Has pagado 20 € en Uber Eats"))
        assertFalse(isIncome("Compra con tarjeta 8,99 € en Amazon.es"))
    }

    @Test
    fun `importe recibido sin sustantivo tambien es ingreso`() {
        assertTrue(isIncome("Has recibido 20,00 € de Juan"))
        assertTrue(isIncome("Recibiste 35,00 € de Maria"))
    }

    @Test
    fun `aviso de cargo no se confunde con ingreso`() {
        assertFalse(isIncome("Has recibido un cargo de 5,00 € en Netflix"))
        assertFalse(isIncome("Has recibido una compra de 5,00 € en Netflix"))
    }

    @Test
    fun `texto sin pistas de dinero no es ingreso`() {
        assertFalse(isIncome("Recordatorio: tienes 5 notificaciones"))
        assertFalse(IncomeDetector.looksLikeIncome(null, ""))
    }
}