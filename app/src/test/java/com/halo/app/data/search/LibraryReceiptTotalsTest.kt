package com.halo.app.data.search

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryReceiptTotalsTest {
    private val november = ReceiptQuery("Rimi", YearMonth.of(2026, 11))

    @Test fun questionIdentifiesTheMerchantAndPurchaseMonth() {
        val today = LocalDate.of(2027, 1, 4)
        assertEquals(november, parseReceiptQuery("How much did I spend at Rimi in November?", today))
        assertEquals(november, parseReceiptQuery("Сколько я потратил в Rimi за ноябрь 2026?", today))
        assertEquals(ReceiptQuery("Rimi", YearMonth.of(2026, 12)), parseReceiptQuery("Receipt totals at Rimi last month", today))
    }

    @Test fun usesThePrintedTotalAndCurrency() {
        val text = "Rimi Latvia\nPirkuma čeks\n12.11.2026\nPVN 2,10 EUR\nKopā 13,40 EUR\n"
        assertEquals(Triple(LocalDate.of(2026, 11, 12), "EUR", BigDecimal("13.40")), parseSavedReceipt(text, "Scan", november))
        assertNull(parseSavedReceipt(text.replace("13,40 EUR", "13,40").replace("2,10 EUR", "2,10"), "Scan", november))
        assertNull(parseSavedReceipt(text + "Grand total 23,40 EUR", "Scan", november))
        assertNull(parseSavedReceipt(text.replace("Rimi", "Rimington"), "Scan", november))
        assertNull(parseSavedReceipt("Rimi\nInvoice\n2026-11-12\nAmount due 13,40 EUR", "Invoice", november))
    }
}
