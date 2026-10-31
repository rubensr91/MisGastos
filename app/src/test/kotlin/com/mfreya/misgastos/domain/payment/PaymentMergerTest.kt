package com.mfreya.misgastos.domain.payment

import com.mfreya.misgastos.domain.parse.ParsedExpense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMergerTest {

    private fun candidate(
        merchant: String,
        confidence: Int,
        rawText: String,
        sourcePackage: String = "de.banco",
        sourceTitle: String = "",
    ) = MergeCandidate(
        parsed = ParsedExpense(
            amountCents = 759L,
            currency = "EUR",
            merchant = merchant,
            merchantRaw = merchant,
            confidence = confidence,
            normalizedText = rawText,
        ),
        sourcePackage = sourcePackage,
        sourceTitle = sourceTitle,
        rawText = rawText,
        postedAt = 60_000L,
        ingestedAt = 60_000L,
    )

    private val good = candidate("Amazon", 95, "Amazon. Gastaste 7,59 EUR en Amazon")
    private val poor = candidate(
        merchant = "Desconocido",
        confidence = 60,
        rawText = "AMAZON. 7,59 EUR con Visa 4797",
        sourcePackage = "com.google.wallet",
        sourceTitle = "AMAZON",
    )

    @Test
    fun knownMerchantBeatsUnknownNoMatterTheConfidence() {
        assertEquals(good, PaymentMerger.better(good, poor))
        assertEquals(good, PaymentMerger.better(poor, good))
    }

    @Test
    fun qualityPutsTheKnownMerchantFarAhead() {
        assertTrue(PaymentMerger.quality(good) > PaymentMerger.quality(poor))
    }

    @Test
    fun higherConfidenceWinsWhenBothKnowTheMerchant() {
        val weak = candidate("Amazon", 60, "texto corto")
        val strong = candidate("Amazon", 95, "texto largo con el verbo de gasto")
        assertEquals(strong, PaymentMerger.better(weak, strong))
    }

    @Test
    fun withEqualConfidenceAndMerchantTheLongerTextWins() {
        val short = candidate("Amazon", 95, "Amazon")
        val long = candidate("Amazon", 95, "Amazon. Gastaste 7,59 EUR en Amazon")
        assertEquals(long, PaymentMerger.better(short, long))
    }

    @Test
    fun mergeIsSymmetricSoArrivalOrderDoesNotMatter() {
        assertEquals(PaymentMerger.merge(good, poor), PaymentMerger.merge(poor, good))
    }

    @Test
    fun mergeKeepsTheBetterMerchantAndItsOrigin() {
        val fields = PaymentMerger.merge(good, poor)
        assertEquals("Amazon", fields.merchant)
        assertEquals("de.banco", fields.sourcePackage)
    }

    @Test
    fun mergeKeepsTheLongestRawTextEvenIfItLosesTheMerchant() {
        val longButPoor = poor.copy(
            rawText = "AMAZON. Compra de 7,59 EUR con la tarjeta Visa terminada en 4797",
        )
        val fields = PaymentMerger.merge(longButPoor, good)
        assertEquals("Amazon", fields.merchant)
        assertEquals(longButPoor.rawText, fields.rawText)
    }

    @Test
    fun mergeKeepsTheLongestTitle() {
        val withTitle = poor.copy(sourceTitle = "AMAZON.COM")
        assertEquals("AMAZON.COM", PaymentMerger.merge(good, withTitle).sourceTitle)
        assertEquals("Amazon", PaymentMerger.merge(good, poor.copy(sourceTitle = "Amazon")).sourceTitle)
    }

    @Test
    fun mergeKeepsTheHighestConfidence() {
        assertEquals(95, PaymentMerger.merge(good, poor).parseConfidence)
        assertEquals(95, PaymentMerger.merge(poor, good).parseConfidence)
    }

    @Test
    fun identicalNoticesStayIdentical() {
        val fields = PaymentMerger.merge(good, good)
        assertEquals("Amazon", fields.merchant)
        assertEquals(good.rawText, fields.rawText)
        assertEquals(good.sourcePackage, fields.sourcePackage)
    }
}
