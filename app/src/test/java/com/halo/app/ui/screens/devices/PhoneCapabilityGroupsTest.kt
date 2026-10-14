package com.halo.app.ui.screens.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCapabilityGroupsTest {
    @Test fun sevenGroupsHaveDisjointCapabilityNames() {
        assertEquals(7, phoneCapabilityGroups.size)
        val labels = phoneCapabilityGroups.flatMap { it.names }
        assertEquals("No capability may inflate two group counts", labels.size, labels.toSet().size)
        assertTrue(phoneCapabilityGroups.all { it.names.isNotEmpty() })
    }

    @Test fun reportedAvailabilityDoesNotInventPermissionOrUnpublishedAbilities() {
        val reported = listOf("Microphone", "Open applications", "Write workspace")
        val result = groupPhoneCapabilities(reported)
        assertEquals(7, result.size)
        assertEquals(3, result.count { it.hasAvailableCapability })
        assertTrue(result[0].hasAvailableCapability)
        assertFalse(result[1].hasAvailableCapability)
        assertTrue(result[3].hasAvailableCapability)
        assertTrue(result[6].hasAvailableCapability)
        assertEquals(listOf("Microphone"), result[0].available)
        assertTrue(groupPhoneCapabilities(emptyList()).all { !it.hasAvailableCapability })
    }

    @Test fun unknownCapabilityRemainsAvailableForTechnicalInspection() {
        val extra = "Local device extension"
        val reported = listOf("Microphone", extra, extra)
        assertEquals(listOf(extra), ungroupedPhoneCapabilities(reported))
        assertEquals(1, groupPhoneCapabilities(reported).count { it.hasAvailableCapability })
    }

    @Test fun groupForReadAndWriteWorkspaceIsNotConfusedWithPhoneControls() {
        val summary = groupPhoneCapabilities(listOf("Read workspace", "Write workspace"))
        assertEquals(1, summary.count { it.hasAvailableCapability })
        assertEquals(2, summary.last().available.size)
    }
}
