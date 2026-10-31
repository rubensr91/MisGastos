package com.mfreya.misgastos.data.category

import com.mfreya.misgastos.data.db.MerchantCategoryDao
import com.mfreya.misgastos.data.db.MerchantCategoryEntity
import com.mfreya.misgastos.domain.category.CategoryRule
import com.mfreya.misgastos.domain.category.CategoryRules
import com.mfreya.misgastos.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El clasificador es la pieza que "aprende": cuando el usuario corrige la categoria de un gasto,
 * ese par comercio -> categoria se guarda y se aplica a los gastos siguientes del mismo comercio.
 */
class ExpenseClassifierTest {

    private class FakeMerchantCategoryDao : MerchantCategoryDao {
        private val rows = MutableStateFlow<Map<String, MerchantCategoryEntity>>(emptyMap())

        override fun observeAll(): Flow<List<MerchantCategoryEntity>> =
            rows.map { it.values.toList() }

        override fun observeCount(): Flow<Int> = rows.map { it.size }

        override suspend fun find(merchantKey: String): MerchantCategoryEntity? = rows.value[merchantKey]

        override suspend fun upsert(entity: MerchantCategoryEntity) {
            rows.value = rows.value + (entity.merchantKey to entity)
        }

        override suspend fun forget(merchantKey: String) {
            rows.value = rows.value - merchantKey
        }

        override suspend fun forgetAll() {
            rows.value = emptyMap()
        }

        fun snapshot(): List<MerchantCategoryEntity> = rows.value.values.toList()
    }

    private val dao = FakeMerchantCategoryDao()

    private fun classifier(
        rules: List<CategoryRule> = CategoryRules.DEFAULT,
        fixedTime: Long = 1_700_000_000_000L,
    ) = ExpenseClassifier(dao = dao, rules = rules, now = { fixedTime })

    @Test
    fun sinNadaAprendidoAplicaElCatalogoDeReglas() = runBlocking {
        assertEquals("Combustible", classifier().classify("REPSOL", "Pago con tarjeta"))
        assertEquals("Supermercado", classifier().classify("MERCADONA", "Pago con tarjeta"))
    }

    @Test
    fun sinEvidenciaDevuelveNull() = runBlocking {
        assertNull(classifier().classify("XYZ TEMPORAL", "Aviso de cuenta"))
        assertNull(classifier().classify("", ""))
    }

    @Test
    fun loAprendidoGanaALasReglas() = runBlocking {
        val subject = classifier()
        subject.learn("REPSOL", "Ocio")
        assertEquals("Ocio", subject.classify("REPSOL", "Pago con tarjeta"))
    }

    @Test
    fun loAprendidoSeAplicaConVariantesDelNombreDelComercio() = runBlocking {
        val subject = classifier()
        subject.learn("Mercadona", "Supermercado")
        assertEquals("Supermercado", subject.classify("MERCADONA", ""))
        assertEquals("Supermercado", subject.classify(" Mercadona ", ""))
        assertEquals("Supermercado", subject.classify("Mercadona, S.L.", ""))
    }

    @Test
    fun unaReglaAprendidaSirveComoParteDeUnComercioMasLargo() = runBlocking {
        val subject = classifier()
        subject.learn("repsol", "Ocio")
        assertEquals("Ocio", subject.classify("REPSOL Estacion de Servicio Madrid", ""))
    }

    @Test
    fun cuentaLasVecesQueSeHaConfirmadoUnaRegla() = runBlocking {
        val subject = classifier(fixedTime = 111L)
        subject.learn("Ocio Bar", "Ocio")
        subject.learn("Ocio Bar", "Ocio")
        subject.learn("Ocio Bar", "Comida rápida")

        val row = dao.find("ocio bar")
        assertEquals(3, row?.hits)
        assertEquals("Comida rápida", row?.category)
        assertEquals(111L, row?.learnedAt)
    }

    @Test
    fun noAprendeNadaConUnComercioVacio() = runBlocking {
        classifier().learn("   ", "Ocio")
        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun normalizaLaCategoriaAprendida() = runBlocking {
        val subject = classifier()
        subject.learn("Bar Central", "  Ocio  ")
        assertEquals("Ocio", subject.classify("Bar Central", ""))
    }

    @Test
    fun olvidarUnaReglaDevuelveElComercioAlCatalogoDeReglas() = runBlocking {
        val subject = classifier()
        subject.learn("REPSOL", "Ocio")
        subject.forget("repsol")
        assertTrue(dao.snapshot().isEmpty())
        assertEquals("Combustible", subject.classify("REPSOL", "Pago con tarjeta"))
    }

    @Test
    fun olvidarTodoVaciaLaTabla() = runBlocking {
        val subject = classifier()
        subject.learn("REPSOL", "Ocio")
        subject.learn("NETFLIX", "Compras online")
        subject.forgetAll()
        assertTrue(dao.snapshot().isEmpty())
        assertEquals("Combustible", subject.classify("REPSOL", ""))
    }

    @Test
    fun laTablaSeRellenaAlAprender() = runBlocking {
        val subject = classifier()
        subject.learn("MERCADONA", "Supermercado")
        subject.learn("NETFLIX", "Suscripciones")

        val learned = subject.observeLearned()
        assertEquals(2, dao.snapshot().size)
        assertEquals(2, subject.observeLearnedCount().first())
        assertEquals(listOf("mercadona", "netflix"), learned.first().map { it.merchantKey })
    }

    @Test
    fun invalidarLaCacheVuelveALeerDeLaBaseDeDatos() = runBlocking {
        val first = classifier()
        first.learn("REPSOL", "Ocio")
        assertEquals("Ocio", first.classify("REPSOL", ""))

        // Un "otro" clasificador con la misma tabla representa, por ejemplo, un proceso nuevo.
        val fresh = classifier()
        assertEquals("Ocio", fresh.classify("REPSOL", ""))
        fresh.invalidate()
        assertEquals("Ocio", fresh.classify("REPSOL", ""))
    }

    @Test
    fun conCatalogoVacioSoloCuentaLoAprendido() = runBlocking {
        val subject = classifier(rules = emptyList())
        assertNull(subject.classify("REPSOL", "Pago con tarjeta"))
        subject.learn("REPSOL", "Ocio")
        assertEquals("Ocio", subject.classify("REPSOL", ""))
    }

    @Test
    fun elegirUnaCategoriaDesconocidaSeAprendeIgual() = runBlocking {
        val subject = classifier()
        subject.learn("Panadería La Espiga", "Panadería")
        assertFalse(Category.isKnown("Panadería"))
        assertEquals("Panadería", subject.classify("Panadería La Espiga", ""))
    }

    @Test
    fun sinAprenderNadaNoSeFiltraPorLaCategoriaSinClasificar() = runBlocking {
        val subject = classifier()
        subject.learn("REPSOL", Category.UNCLASSIFIED)
        assertTrue(dao.snapshot().isEmpty())
        assertEquals("Combustible", subject.classify("REPSOL", ""))
    }
}