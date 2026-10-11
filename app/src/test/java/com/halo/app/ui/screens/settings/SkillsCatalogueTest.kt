package com.halo.app.ui.screens.settings

import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.runtime.skills.SkillId
import org.junit.Assert.*
import org.junit.Test

class SkillsCatalogueTest {
    @Test fun searchMatchesTheTranslatedNameShownToTheUser() {
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        val names = mapOf(SkillId("halo.alarms") to "Будильники и таймеры")
        assertEquals(listOf("halo.alarms"), visibleSkills(skills, "будильники", names).map { it.id.value })
        assertTrue(visibleSkills(skills, "halo_timer", names).any { it.id.value == "halo.alarms" })
    }
    @Test fun searchFindsWorkspaceAndPhoneToolsWithoutRepeatingLegacyBundles() {
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        assertTrue(visibleSkills(skills, "halo_get_workspace_object").any { it.id.value == "halo.notebook" })
        assertTrue(visibleSkills(skills, "HALO_OPEN_APP").any { it.id.value == "halo.apps" })
        assertFalse(visibleSkills(skills, "").any { it.id.value in setOf("halo.phone", "halo.phone.actions", "halo.reminders") })
        assertTrue(visibleSkills(skills, " no such skill ").isEmpty())
    }
}
