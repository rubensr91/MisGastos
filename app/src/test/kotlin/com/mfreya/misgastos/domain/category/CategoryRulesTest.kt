package com.mfreya.misgastos.domain.category

import com.mfreya.misgastos.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catalogo de reglas es el corazon de la categorizacion automatica. Estos tests fijan el
 * comportamiento con comercios reales: si uno de ellos cambia, se ve aqui.
 */
class CategoryRulesTest {

    private fun match(merchant: String, rawText: String = ""): String? =
        CategoryRules.match(merchant, rawText)

    @Test
    fun clasificaSupermercados() {
        assertEquals("Supermercado", match("MERCADONA"))
        assertEquals("Supermercado", match("Carrefour Express"))
        assertEquals("Supermercado", match("EROSKI CIUDAD"))
        assertEquals("Supermercado", match("LIDL"))
        assertEquals("Supermercado", match("DIA"))
    }

    @Test
    fun clasificaGasolineras() {
        assertEquals("Combustible", match("REPSOL"))
        assertEquals("Combustible", match("CEPSA"))
        assertEquals("Combustible", match("BP"))
        assertEquals("Combustible", match("TotalEnergies"))
    }

    @Test
    fun clasificaRestaurantesYComidaRapida() {
        assertEquals("Restaurante", match("BAR EL PILON"))
        assertEquals("Restaurante", match("GLOVO"))
        assertEquals("Restaurante", match("Uber Eats"))
        assertEquals("Restaurante", match("LA TABERNA DEL PUENTE"))
        assertEquals("Comida rápida", match("MCDONALDS"))
        assertEquals("Comida rápida", match("Burger King"))
        assertEquals("Comida rápida", match("TELEPIZZA"))
    }

    @Test
    fun clasificaTransporteYEstacionamiento() {
        assertEquals("Transporte", match("UBER"))
        assertEquals("Transporte", match("Cabify"))
        assertEquals("Transporte", match("RENFE"))
        assertEquals("Transporte", match("Metro Madrid"))
        assertEquals("Estacionamiento", match("PARKING ANDALUCIA"))
        assertEquals("Estacionamiento", match("aparcamiento centro"))
    }

    @Test
    fun clasificaSuscripcionesYComprasOnline() {
        assertEquals("Suscripciones", match("NETFLIX"))
        assertEquals("Suscripciones", match("Spotify"))
        assertEquals("Suscripciones", match("AMAZON PRIME"))
        assertEquals("Compras online", match("Amazon.es"))
        assertEquals("Compras online", match("ZARA"))
        assertEquals("Compras online", match("DECATHLON"))
    }

    @Test
    fun clasificaServicios() {
        assertEquals("Servicios", match("Iberdrola Clientes"))
        assertEquals("Servicios", match("MOVISTAR"))
        assertEquals("Servicios", match("Factura agua"))
        assertEquals("Servicios", match("Comunidad de propietarios"))
    }

    @Test
    fun clasificaViajesSaludEducacionYDeportes() {
        assertEquals("Viajes", match("RYANAIR"))
        assertEquals("Viajes", match("Booking.com"))
        assertEquals("Salud", match("FARMACIA CENTRAL"))
        assertEquals("Salud", match("CLINICA DENTAL"))
        assertEquals("Educación", match("UDEMY"))
        assertEquals("Deportes", match("GIMNASIO MCFIT"))
    }

    @Test
    fun clasificaOcioMascotasRegalosBancaImpuestosEfectivoYOtros() {
        assertEquals("Ocio", match("YELMO CINES"))
        assertEquals("Ocio", match("Museo del Prado"))
        assertEquals("Mascotas", match("KIWOKO"))
        assertEquals("Regalos", match("Interflora"))
        assertEquals("Banca", match("Comision mantenimiento"))
        assertEquals("Impuestos", match("Agencia Tributaria"))
        assertEquals("Efectivo", match("CAJERO REINTEGRO"))
        assertEquals("Otros", match("Varios"))
    }

    @Test
    fun losComerciosSinEvidenciaNoSeCategorizan() {
        assertNull(match(""))
        assertNull(match("XYZ TEMPORAL"))
        assertNull(match("AJUSTE DE CUENTA"))
        assertNull(match("   ", "   "))
    }

    @Test
    fun unaCoincidenciaSueltaEnElCuerpoNoBastaParaDecidir() {
        // MCDONALDS aparece una sola vez en el cuerpo del aviso: 1 punto, por debajo del minimo 2.
        assertNull(CategoryRules.match("XYZ TEMPORAL", "Aviso: compra en MCDONALDS con tarjeta"))
        assertNull(CategoryRules.match("XYZ TEMPORAL", "Aviso de tu banco, operacion con tarjeta"))
    }

    @Test
    fun dosCoincidenciasEnElCuerpoYaDeciden() {
        // "burger king" son dos tokens y suman 2 puntos, el minimo necesario.
        assertEquals(
            "Comida rápida",
            CategoryRules.match("XYZ TEMPORAL", "Aviso: compra en BURGER KING con tarjeta"),
        )
    }

    @Test
    fun elComercioPesaMasQueElTextoDelAviso() {
        // El cuerpo menciona "mercadona", pero el comercio es una gasolinera: gana el comercio.
        assertEquals(
            "Combustible",
            CategoryRules.match("REPSOL", "Pago en MERCADONA con tarjeta terminada en 1234"),
        )
    }

    @Test
    fun lasPalabrasClaveNoCasanPorSubcadena() {
        assertNull(match("BARCELONA FOTOGRAFIA"))
        assertNull(match("CAFETERA MARTI"))
        // "super" esta dentro de "supercontinente", pero no es el mismo token.
        assertNull(match("SUPERCONTINENTE"))
    }

    @Test
    fun unAvisoSinComercioUtilTomaElTextoCompleto() {
        // "repostaje" y "repsol" son dos palabras clave: 2 puntos, suficiente.
        assertEquals("Combustible", CategoryRules.match("Desconocido", "Repostaje en REPSOL 45,90 EUR"))
        // "mercadona" y "supermercado" tambien: 2 puntos.
        assertEquals(
            "Supermercado",
            CategoryRules.match("Desconocido", "Compra en MERCADONA, tienda supermercado"),
        )
        // Con una sola, el cuerpo se queda corto y se prefiere no decidir.
        assertNull(CategoryRules.match("Desconocido", "Compra en MERCADONA 12,50 EUR"))
    }

    @Test
    fun sePuedeInyectarUnCatalogoDePropias() {
        val rules = listOf(CategoryRule("Prueba", listOf("mi tienda")))
        assertEquals("Prueba", CategoryRules.match("MI TIENDA SL", "", rules))
        assertNull(CategoryRules.match("MERCADONA", "", rules))
    }

    @Test
    fun elCatalogoNoTieneCategoriasRepetidasNiPalabrasVacias() {
        val categories = CategoryRules.DEFAULT.map { it.category }
        assertEquals(categories.size, categories.distinct().size)
        assertTrue(CategoryRules.DEFAULT.all { it.keywords.isNotEmpty() })
        assertTrue(CategoryRules.DEFAULT.all { rule -> rule.keywords.all { it.isNotEmpty() } })
    }

    @Test
    fun todaReglaApuntaAUnaCategoriaDelCatalogo() {
        // Si alguien añade una categoria nueva pero olvida meterla en Category, el filtro por
        // categoria de la lista la dejaria fuera y los totales no cuadrarian.
        for (rule in CategoryRules.DEFAULT) {
            assertTrue(
                "categoria fuera del catalogo: ${rule.category}",
                Category.isKnown(rule.category),
            )
        }
    }
}