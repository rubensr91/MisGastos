package com.mfreya.misgastos.ingest

import com.mfreya.misgastos.data.ExpenseRepository
import com.mfreya.misgastos.data.FakeExpenseDao
import com.mfreya.misgastos.data.category.ExpenseClassifier
import com.mfreya.misgastos.data.db.MerchantCategoryDao
import com.mfreya.misgastos.data.db.MerchantCategoryEntity
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.parse.ParsedExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIngestorTest {

    private fun parser(amountCents: Long?): (String?, String) -> ParsedExpense? = { _, _ ->
        amountCents?.let {
            ParsedExpense(
                amountCents = it,
                currency = "EUR",
                merchant = "Mercadona",
                merchantRaw = "Mercadona",
                confidence = 90,
                normalizedText = "Gastaste 12,50 en Mercadona",
            )
        }
    }

    private fun ingestor(
        dao: FakeExpenseDao,
        allowed: (String) -> Boolean,
        parser: (String?, String) -> ParsedExpense?,
    ) = NotificationIngestor(ExpenseRepository(dao), allowed, parser)

    /** Parser fijo con el comercio y la confianza que se le pasan, para simular cada emisor. */
    private fun parsed(merchant: String, confidence: Int) = { _: String?, _: String ->
        ParsedExpense(
            amountCents = 759L,
            currency = "EUR",
            merchant = merchant,
            merchantRaw = merchant,
            confidence = confidence,
            normalizedText = "",
        )
    }

    @Test
    fun insertsExpenseWhenNotificationIsParsed() = runBlocking {
        val dao = FakeExpenseDao()
        val result = ingestor(dao, { true }, parser(1250))
            .ingest("com.bank", "Pago", "Gastaste 12,50 EUR en Mercadona", 1_000L, "raw", 2_000L)

        val inserted = result as IngestResult.Inserted
        assertEquals(1250L, inserted.expense.amountCents)
        assertEquals("Mercadona", inserted.expense.merchant)
        assertEquals(1_000L, inserted.expense.postedAt)
        assertEquals(2_000L, inserted.expense.ingestedAt)
        assertEquals("com.bank", inserted.expense.sourcePackage)
        assertEquals("Pago", inserted.expense.sourceTitle)
        assertEquals("raw", inserted.expense.rawText)
        assertEquals("EUR", inserted.expense.currency)
        assertEquals("Sin categoría", inserted.expense.category)
        assertFalse(inserted.expense.needsReview)
    }

    @Test
    fun returnsDuplicateForSamePackageTimestampAndAmount() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = ingestor(dao, { true }, parser(1250))

        val first = sut.ingest("com.bank", "Pago", "Gastaste 12,50 EUR en Mercadona", 1_000L, "raw", 2_000L)
        val second = sut.ingest("com.bank", "Pago", "Gastaste 12,50 EUR en Mercadona", 1_000L, "raw", 3_000L)

        assertTrue(first is IngestResult.Inserted)
        assertTrue(second is IngestResult.Merged)
        assertEquals(1, dao.count())
    }

    // --- Fusion de los dos avisos del mismo pago (banco + Google Wallet) ---

    @Test
    fun bankAndGoogleWalletOfTheSamePaymentLeaveOneRow() = runBlocking {
        val dao = FakeExpenseDao()
        val bank = ingestor(dao, { true }, parsed("Amazon", 95))
        val wallet = ingestor(dao, { true }, parsed("Desconocido", 60))

        val first = bank.ingest("de.banco", "Amazon", "Gastaste 7,59 EUR en Amazon", 60_000L, "Amazon. Gastaste 7,59 EUR en Amazon", 60_000L)
        val second = wallet.ingest("com.google.wallet", "AMAZON", "7,59 EUR con Visa 4797", 61_000L, "AMAZON. 7,59 EUR con Visa 4797", 61_000L)

        assertTrue(first is IngestResult.Inserted)
        assertTrue(second is IngestResult.Merged)
        assertEquals(1, dao.count())
        val row = dao.rows.value.single()
        assertEquals("Amazon", row.merchant)
        assertEquals("de.banco", row.sourcePackage)
        assertEquals("Amazon. Gastaste 7,59 EUR en Amazon", row.rawText)
    }

    @Test
    fun theBetterNoticeWinsEvenWhenItArrivesSecond() = runBlocking {
        val dao = FakeExpenseDao()
        val wallet = ingestor(dao, { true }, parsed("Desconocido", 60))
        val bank = ingestor(dao, { true }, parsed("CATERING MARIO ROMAN", 95))

        wallet.ingest("com.google.wallet", "S.CATERING MARIO ROMAN", "12,00 EUR con Visa 4797", 60_000L, "S.CATERING MARIO ROMAN. 12,00 EUR con Visa 4797", 60_000L)
        bank.ingest("de.banco", "CATERING MARIO ROMAN", "Gastaste 12,00 EUR en CATERING MARIO ROMAN", 61_000L, "Gastaste 12,00 EUR en CATERING MARIO ROMAN", 61_000L)

        val row = dao.rows.value.single()
        assertEquals(1, dao.count())
        assertEquals("CATERING MARIO ROMAN", row.merchant)
        assertEquals("de.banco", row.sourcePackage)
        assertEquals(95, row.parseConfidence)
    }

    @Test
    fun mergeKeepsTheLongestTextAndTitleFromEitherNotice() = runBlocking {
        val dao = FakeExpenseDao()
        val short = ingestor(dao, { true }, parsed("Desconocido", 60))
        val long = ingestor(dao, { true }, parsed("Desconocido", 95))

        short.ingest("com.google.wallet", "BASILICA", "8,00 EUR", 60_000L, "BASILICA. 8,00 EUR", 60_000L)
        long.ingest("de.banco", "Iglesia Arcos", "Gastaste 8,00 EUR en Iglesia Arcos", 61_000L, "Iglesia Arcos. Gastaste 8,00 EUR en Iglesia Arcos", 61_000L)

        val row = dao.rows.value.single()
        assertEquals("Iglesia Arcos. Gastaste 8,00 EUR en Iglesia Arcos", row.rawText)
        assertEquals("Iglesia Arcos", row.sourceTitle)
    }

    @Test
    fun theSamePaymentWithTheSameAmountTwoSecondsApartIsMerged() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = ingestor(dao, { true }, parsed("Amazon", 95))

        sut.ingest("de.banco", "Amazon", "Gastaste 7,59 EUR en Amazon", 60_000L, "banco", 60_000L)
        val other = sut.ingest("com.google.wallet", "AMAZON", "7,59 EUR con Visa", 62_000L, "google", 62_000L)

        assertTrue(other is IngestResult.Merged)
        assertEquals(1, dao.count())
    }

    @Test
    fun sameAmountOutsideTheWindowIsANewExpense() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = ingestor(dao, { true }, parsed("Amazon", 95))

        sut.ingest("de.banco", "Amazon", "Gastaste 7,59 EUR en Amazon", 60_000L, "banco", 60_000L)
        val later = 60_000L + NotificationIngestor.MERGE_WINDOW_MILLIS + 1_000L
        val other = sut.ingest("de.banco", "Amazon", "Gastaste 7,59 EUR en Amazon", later, "banco", later)

        assertTrue(other is IngestResult.Inserted)
        assertEquals(2, dao.count())
    }

    @Test
    fun differentAmountInsideTheWindowIsANewExpense() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = ingestor(dao, { true }, parser(1250))

        sut.ingest("de.banco", "Pago", "Gastaste 12,50 EUR en Mercadona", 60_000L, "a", 60_000L)
        val other = ingestor(dao, { true }, parser(3000))
            .ingest("de.banco", "Pago", "Gastaste 30,00 EUR en GLOVO", 61_000L, "b", 61_000L)

        assertTrue(other is IngestResult.Inserted)
        assertEquals(2, dao.count())
    }

    @Test
    fun mergingRecalculatesTheCategoryWithTheWinningMerchant() = runBlocking {
        val dao = FakeExpenseDao()
        val classifier = ExpenseClassifier(
            dao = object : MerchantCategoryDao {
                override fun observeAll(): Flow<List<MerchantCategoryEntity>> =
                    MutableStateFlow(emptyList())

                override fun observeCount(): Flow<Int> = MutableStateFlow(0)
                override suspend fun find(merchantKey: String): MerchantCategoryEntity? = null
                override suspend fun upsert(entity: MerchantCategoryEntity) = Unit
                override suspend fun forget(merchantKey: String) = Unit
                override suspend fun forgetAll() = Unit
            },
        )
        val wallet = NotificationIngestor(
            ExpenseRepository(dao, classifier), { true }, parsed("Desconocido", 60),
        )
        val bank = NotificationIngestor(
            ExpenseRepository(dao, classifier), { true }, parsed("REPSOL", 95),
        )

        wallet.ingest("com.google.wallet", "X", "60,00 EUR con Visa", 60_000L, "", 60_000L)
        // Sin el nombre del comercio no hay regla que acierte, asi que queda sin clasificar.
        assertEquals(Category.UNCLASSIFIED, dao.rows.value.single().category)

        bank.ingest("de.banco", "REPSOL", "Gastaste 60,00 EUR en REPSOL", 61_000L, "", 61_000L)

        assertEquals(1, dao.count())
        val row = dao.rows.value.single()
        assertEquals("REPSOL", row.merchant)
        assertEquals("Combustible", row.category)
    }

    @Test
    fun returnsNotAnExpenseWhenParserFails() = runBlocking {
        val dao = FakeExpenseDao()
        val result = ingestor(dao, { true }, parser(null))
            .ingest("com.bank", "Aviso", "Recordatorio: tienes 5 notificaciones", 1_000L, "raw", 2_000L)

        assertEquals(IngestResult.NotAnExpense, result)
        assertEquals(0, dao.count())
    }

    @Test
    fun returnsPackageNotAllowedWithoutParsing() = runBlocking {
        val dao = FakeExpenseDao()
        var parserCalled = false
        val result = ingestor(dao, { false }, { _, _ ->
            parserCalled = true
            null
        }).ingest("com.otro", "Pago", "Gastaste 12,50 EUR en Mercadona", 1_000L, "raw", 2_000L)

        assertEquals(IngestResult.PackageNotAllowed, result)
        assertFalse(parserCalled)
        assertEquals(0, dao.count())
    }

    @Test
    fun allowAllPackagesAcceptsAnySource() = runBlocking {
        val dao = FakeExpenseDao()
        val result = ingestor(dao, { true }, parser(500))
            .ingest("com.cualquiera", null, "Gastaste 12,50 EUR en Mercadona", 1L, "raw", 2L)

        assertTrue(result is IngestResult.Inserted)
    }

    @Test
    fun discardsIncomingMoneyWithoutStoringIt() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = NotificationIngestor(ExpenseRepository(dao), { true })
        val result = sut.ingest(
            sourcePackage = "com.bank",
            title = "Bizum",
            body = "Bizum recibido de Maria: 20,00 EUR",
            postedAt = 1_000L,
            rawText = "Bizum. Bizum recibido de Maria: 20,00 EUR",
        )

        assertEquals(IngestResult.Income, result)
        assertEquals(0, dao.count())
    }

    @Test
    fun usesRealParserEndToEnd() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = NotificationIngestor(ExpenseRepository(dao), { true })
        val result = sut.ingest(
            sourcePackage = "com.bank",
            title = "Tarjeta",
            body = "Gastaste 12,50 EUR en Mercadona",
            postedAt = 1_000L,
            rawText = "Tarjeta. Gastaste 12,50 EUR en Mercadona",
        )

        val inserted = result as IngestResult.Inserted
        assertEquals(1250L, inserted.expense.amountCents)
        assertEquals("Mercadona", inserted.expense.merchant)
    }
}