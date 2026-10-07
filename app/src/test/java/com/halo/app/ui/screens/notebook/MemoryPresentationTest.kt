package com.halo.app.ui.screens.notebook

import com.halo.app.data.memory.MemoryRecord
import com.halo.app.data.memory.MemoryStatus
import com.halo.runtime.cognition.memory.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class MemoryPresentationTest {
    private fun record(origin: MemoryProposalOrigin, confirmed: Boolean = true, expiry: Long? = null) = MemoryRecord(
        "private-id", "Use Celsius", MemoryKind.PREFERENCE, MemoryScope.GLOBAL, null, 1, 2, null, null,
        null, MemoryStatus.ACTIVE, origin = origin, userConfirmed = confirmed, validUntil = expiry)
    @Test fun everyOriginRetainsAuthorshipSeparatelyFromApproval() {
        val labels = MemoryProposalOrigin.entries.map { MemoryPresentation.item(record(it)) }
        assertEquals(6, labels.map { it.originLabel }.distinct().size)
        assertTrue(labels.all { it.confirmationLabel == "Approved by you" })
        assertEquals("Suggested by Halo", labels[2].originLabel)
        assertFalse(labels.drop(2).any { it.originLabel.contains("You asked") })
    }
    @Test fun importConfirmationDoesNotImpersonateAuthorship() {
        val before = MemoryPresentation.item(record(MemoryProposalOrigin.IMPORT, false))
        val after = MemoryPresentation.item(record(MemoryProposalOrigin.IMPORT))
        assertEquals("Imported · not verified", before.confirmationLabel)
        assertEquals(before.originLabel, after.originLabel)
        assertEquals("Unknown", before.confidenceLabel)
    }
    @Test fun staleAndMissingScopeAreTruthful() {
        assertTrue(MemoryPresentation.item(record(MemoryProposalOrigin.USER_EXPLICIT, expiry = 10), now = 10).stale)
        assertEquals("Original scope unavailable", MemoryPresentation.scope(MemoryScope.PROJECT, null))
        assertEquals("Everywhere", MemoryPresentation.scope(MemoryScope.GLOBAL, null))
    }
    @Test fun datesRespectDeviceZoneAndHumanLabels() {
        val now = java.time.Instant.parse("2026-10-07T01:00:00Z").toEpochMilli()
        assertEquals("Today", MemoryPresentation.date(now, now, ZoneId.of("Europe/Riga"), Locale.US))
        assertEquals("Yesterday", MemoryPresentation.date(now - 86400000, now, ZoneId.of("Europe/Riga"), Locale.US))
    }
    @Test fun filtersDoNotInventKinds() {
        assertTrue(MemoryGroup.PEOPLE.includes(MemoryKind.RELATIONSHIP))
        assertFalse(MemoryGroup.PROJECTS.includes(MemoryKind.FACT))
        assertTrue(MemoryGroup.OTHER.includes(MemoryKind.INSTRUCTION))
    }
}
