package com.mfreya.misgastos.domain.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El prefiltro tiene que ser conservador: solo puede descartar lo que el parser
 * descartaria igualmente. Por eso estos tests comprueban tambien el caso molesto
 * "importe sin verbo", que el parser acepta con confianza baja.
 */
class ExpensePreFilterTest {

    private fun body(value: String, title: String? = null): Boolean =
        ExpensePreFilter.looksLikeExpense(title, value)

    @Test
    fun textoBancarioTipicoEsGasto() {
        assertTrue(body("Gastaste 12,50 € en Mercadona"))
        assertTrue(ExpensePreFilter.looksLikeExpense("Tu Banco", "Gastaste 45,90 € en REPSOL"))
    }

    @Test
    fun importeEnCuerpoEsGasto() {
        assertTrue(ExpensePreFilter.looksLikeExpense("Notificación", "Importe: 7,40 €  Comercio: REPSOL"))
    }

    @Test
    fun soloElVerboConCifrasYaEsGasto() {
        assertTrue(body("Compra con tarjeta 8,99 € en Amazon.es"))
        assertTrue(body("Transacción de 15,00 €"))
        assertTrue(body("Cargo en tu cuenta 5,00 €"))
        assertTrue(body("Has pagado 20 € en Uber Eats"))
    }

    @Test
    fun importeSinVerboSiguePasandoPorqueElParserLoAcepta() {
        // El parser necesita 30 puntos y el símbolo de divisa da 40, asi que este texto
        // llegaria hasta el final con confianza baja. El prefiltro no puede cortarlo.
        assertTrue(body("12,50 € en Amazon.es"))
    }

    @Test
    fun eurosEnLetrasTambienCuentan() {
        assertTrue(body("Compra con tarjeta 8,99 EUR en Amazon.es"))
        assertTrue(body("Gastaste 45,90 EUR en REPSOL"))
    }

    @Test
    fun mensajeDeChatNoEsGasto() {
        assertFalse(body("Si amor"))
        assertFalse(ExpensePreFilter.looksLikeExpense("Mariana", "Qué ropa tengo que llevar?"))
        assertFalse(
            ExpensePreFilter.looksLikeExpense(
                "Watcher.Guru",
                "JUST IN: US-bound oil tanker's propulsion system has been hacked.",
            ),
        )
    }

    @Test
    fun recordatorioSinImporteNoEsGasto() {
        assertFalse(body("Recordatorio: tienes 5 notificaciones"))
        assertFalse(body("Nueva notificación disponible"))
    }

    @Test
    fun verboSinCifrasNoEsGasto() {
        assertFalse(body("Pago pendiente"))
        assertFalse(body("Compra realizada"))
    }

    @Test
    fun cifrasSinPistasDeGastoNoEsGasto() {
        assertFalse(body("Reunión mañana a las 10"))
        assertFalse(body("Batería al 15 %"))
        assertFalse(ExpensePreFilter.looksLikeExpense(null, "5"))
    }

    @Test
    fun verboEnTituloYCifrasEnCuerpoEsGasto() {
        assertTrue(ExpensePreFilter.looksLikeExpense("Pago realizado", "43,20"))
    }

    @Test
    fun sinNadaQueMirarNoEsGasto() {
        assertFalse(ExpensePreFilter.looksLikeExpense(null, ""))
        assertFalse(ExpensePreFilter.looksLikeExpense("", ""))
        assertFalse(ExpensePreFilter.looksLikeExpense("Solo un titulo", ""))
        assertFalse(ExpensePreFilter.looksLikeExpense(null, "   "))
    }
}