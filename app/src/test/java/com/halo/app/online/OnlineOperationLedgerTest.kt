package com.halo.app.online

import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class OnlineOperationLedgerTest {
    @Test fun pendingReservationSurvivesRecreationAndIsNotFree() {
        val storage = MemoryStorage()
        val first = OnlineOperationLedger(storage)
        first.start("IMAGE", "openrouter", "image/model", BigDecimal("0.20"))
        val snapshot = OnlineOperationLedger(storage).snapshot()
        assertEquals(1, snapshot.unconfirmed)
        assertEquals(0, snapshot.accountedUsd.compareTo(BigDecimal("0.20")))
    }
    @Test fun failedOrCancelledRequestKeepsItsReservation() {
        val ledger = OnlineOperationLedger(MemoryStorage())
        val id = ledger.start("SEARCH", "openrouter", "openrouter/free", BigDecimal("0.01"))
        ledger.finish(id, null, "UNCERTAIN")
        assertEquals(1, ledger.snapshot().unconfirmed)
        assertEquals(0, ledger.snapshot().accountedUsd.compareTo(BigDecimal("0.01")))
    }
    @Test fun authoritativeCostReplacesReservationAndFinishIsIdempotent() {
        val ledger = OnlineOperationLedger(MemoryStorage())
        val id = ledger.start("SEARCH", "openrouter", "openrouter/free", BigDecimal("0.02"))
        ledger.finish(id, BigDecimal("0.007"), "COMPLETED")
        ledger.finish(id, BigDecimal("0.007"), "COMPLETED")
        assertEquals(0, ledger.snapshot().accountedUsd.compareTo(BigDecimal("0.007")))
        assertEquals(0, ledger.snapshot().unconfirmed)
    }
    @Test fun concurrentOwnersShareTheBudgetBoundary() {
        val storage = MemoryStorage()
        val one = OnlineOperationLedger(storage)
        val two = OnlineOperationLedger(storage)
        one.configure(BigDecimal("0.10"), BigDecimal("0.10"))
        one.start("IMAGE", "openrouter", "image/model", BigDecimal("0.08"))
        assertThrows(OnlineOperationRejected::class.java) {
            two.start("SEARCH", "openrouter", "openrouter/free", BigDecimal("0.03"))
        }
    }
    @Test fun invalidMoneyAndCorruptedLedgerFailClosed() {
        val storage = MemoryStorage()
        val ledger = OnlineOperationLedger(storage)
        assertThrows(IllegalArgumentException::class.java) { ledger.configure(BigDecimal("-1"), null) }
        storage.putString(OnlineOperationLedger.RECORDS_KEY, "not-a-record")
        assertThrows(OnlineOperationRejected::class.java) {
            ledger.start("SEARCH", "openrouter", "openrouter/free", BigDecimal("0.01"))
        }
    }
    @Test fun consentIsExactSingleUseAndExpires() {
        var time = 1L
        val consent = OneShotConsent { time }
        val token = consent.prepare("request-A")
        assertThrows(OnlineOperationRejected::class.java) { consent.consume(token.id, "request-B") }
        val valid = consent.prepare("request-A")
        consent.consume(valid.id, "request-A")
        assertThrows(OnlineOperationRejected::class.java) { consent.consume(valid.id, "request-A") }
        val expired = consent.prepare("request-A")
        time += OneShotConsent.TTL_NANOS + 1
        assertThrows(OnlineOperationRejected::class.java) { consent.consume(expired.id, "request-A") }
    }
    internal class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
