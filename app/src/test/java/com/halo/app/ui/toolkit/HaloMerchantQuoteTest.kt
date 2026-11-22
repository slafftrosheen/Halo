package com.halo.app.ui.toolkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloMerchantQuoteTest {
    @Test fun reviewKeepsTheActualMerchantHostAndExactAmount() {
        val quote = MerchantQuote.parse("https://shop.example/store/checkout", "18,40", "eur")
        assertEquals("shop.example", quote.destination.host)
        assertEquals("18.40", quote.amount.toPlainString())
        assertEquals("EUR", quote.currency.currencyCode)
    }

    @Test fun onlyPublicCredentialFreeLinksAndValidCurrencyAmountsCanBeReviewed() {
        listOf(
            "http://shop.example/",
            "https://shop.example@elsewhere.example/",
            "https://shop.example/?cart=opaque",
            "https://shop.example/#secret",
        ).forEach { url ->
            assertTrue(runCatching { MerchantQuote.parse(url, "18.40", "EUR") }.isFailure)
        }
        assertTrue(runCatching { MerchantQuote.parse("https://shop.example/", "18.40", "JPY") }.isFailure)
        assertTrue(runCatching { MerchantQuote.parse("https://shop.example/", "0", "EUR") }.isFailure)
    }
}
