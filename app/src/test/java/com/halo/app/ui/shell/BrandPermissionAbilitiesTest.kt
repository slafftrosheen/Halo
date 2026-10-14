package com.halo.app.ui.shell

import com.halo.app.R
import com.halo.app.ui.screens.devices.phoneCapabilityGroups
import org.junit.Assert.*
import org.junit.Test

class BrandPermissionAbilitiesTest {
    @Test fun permissionScreenUsesSameSevenAbilitiesAsDevices() {
        assertEquals(7, phoneCapabilityGroups.size)
        assertEquals(R.string.brand_phone_hear, phoneCapabilityGroups.first().title)
        assertEquals(R.string.brand_phone_workspace, phoneCapabilityGroups.last().title)
    }

    @Test fun microphoneStatusReflectsAndroidPermissionButNeverImpliesOtherGrants() {
        val groups = phoneCapabilityGroups
        assertEquals(PermissionAbilityAccess.MIC_GRANTED,
            permissionAbilityStatus(groups.first(), micGranted = true))
        assertEquals(PermissionAbilityAccess.MIC_MISSING,
            permissionAbilityStatus(groups.first(), micGranted = false))
        groups.drop(1).forEach { group ->
            assertEquals(PermissionAbilityAccess.CHECK_ACCESS,
                permissionAbilityStatus(group, micGranted = true))
            assertEquals(PermissionAbilityAccess.CHECK_ACCESS,
                permissionAbilityStatus(group, micGranted = false))
        }
    }
}
