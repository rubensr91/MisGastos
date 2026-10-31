package com.mfreya.misgastos.domain.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La normalizacion es la base de dos cosas: la clave con la que se memoriza una correccion manual y
 * la comparacion por palabras clave sin falsos positivos por subcadenas.
 */
class MerchantKeysTest {

    @Test
    fun normalizeQuitaAcentosYPasaAMinusculas() {
        assertEquals("bar el pilon", MerchantKeys.normalize("BAR EL PILÓN"))
        assertEquals("panaderia el mano", MerchantKeys.normalize("Panadería El Maño"))
    }

    @Test
    fun normalizeColapsaLosSimbolosEnEspacios() {
        assertEquals("amazon es", MerchantKeys.normalize("Amazon.es"))
        assertEquals("repsol estacion servicio", MerchantKeys.normalize("REPSOL  Estacion-Servicio"))
        assertEquals("cafe 2", MerchantKeys.normalize("Café  ¡2!"))
    }

    @Test
    fun normalizeDeTextoVacioOCTodoSimbolosEsCadenaVacia() {
        assertEquals("", MerchantKeys.normalize(""))
        assertEquals("", MerchantKeys.normalize("   "))
        assertEquals("", MerchantKeys.normalize("!!!---"))
    }

    @Test
    fun normalizeEsEstableEntreVariantesDelMismoComercio() {
        assertEquals("mercadona", MerchantKeys.normalize("MERCADONA"))
        assertEquals("mercadona", MerchantKeys.normalize(" Mercadona "))
        assertEquals("cafeteria central", MerchantKeys.normalize("Cafetería  Central"))
    }

    @Test
    fun unaVarianteDelComercioContieneLosTokensDelNombreAprendido() {
        // No se busca igualdad de cadenas: "Mercadona, S.L." genera tokens de mas, y lo que importa
        // es que la secuencia del nombre aprendido ("mercadona") aparezca dentro de la variante.
        val aprendido = MerchantKeys.tokenize("mercadona")
        val variante = MerchantKeys.tokenize("Mercadona, S.L.")
        assertTrue(MerchantKeys.containsTokens(variante, aprendido))
        assertFalse(MerchantKeys.containsTokens(aprendido, variante))
    }

    @Test
    fun tokenizeSeparaPorSimbolos() {
        assertEquals(listOf("amazon", "es"), MerchantKeys.tokenize("Amazon.es"))
        assertEquals(listOf("bar", "el", "pilon"), MerchantKeys.tokenize("BAR EL PILÓN"))
        assertEquals(listOf("glovo"), MerchantKeys.tokenize("Glovo*"))
    }

    @Test
    fun tokenizeDeTextoSinPalabrasDevuelveVacio() {
        assertTrue(MerchantKeys.tokenize("").isEmpty())
        assertTrue(MerchantKeys.tokenize("%%%").isEmpty())
    }

    @Test
    fun containsTokensEncuentraSecuenciaCompleta() {
        val haystack = MerchantKeys.tokenize("REPSOL Estacion de Servicio Madrid")
        assertTrue(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("estacion")))
        assertTrue(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("repsol")))
    }

    @Test
    fun containsTokensExigeQueLosTokensSeanConsecutivos() {
        val haystack = MerchantKeys.tokenize("Uber Eats Madrid")
        assertTrue(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("uber eats")))
        assertFalse(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("eats uber")))
    }

    @Test
    fun containsTokensNoEncajaPorPrefijoNiPorSubcadena() {
        val haystack = MerchantKeys.tokenize("BARCELONA CENTER")
        assertFalse(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("bar")))
        assertFalse(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("barcelona center shopping")))
    }

    @Test
    fun containsTokensConAgujaVaciaOMayorQueElPajarDevuelveFalse() {
        val haystack = MerchantKeys.tokenize("REPSOL")
        assertFalse(MerchantKeys.containsTokens(haystack, emptyList()))
        assertFalse(MerchantKeys.containsTokens(haystack, MerchantKeys.tokenize("repsol gas")))
    }
}