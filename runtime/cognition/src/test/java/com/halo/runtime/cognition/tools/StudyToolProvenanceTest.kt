package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.sdk.runtime.skills.SkillRisk
import org.junit.Assert.*
import org.junit.Test

class StudyToolProvenanceTest {
    @Test fun savedStudyResultsRetainWorkspacePrivacyAndOneNativeOwnerIncludingWriteReceipts() {
        val names = setOf("halo_study_with_halo", "halo_get_study_session", "halo_study_sessions", "halo_study_state", "halo_study_card_get", "halo_study_select_exercise",
            "halo_study_submit_attempt", "halo_study_set_preferences", "halo_study_complete", "halo_study_delete",
            "halo_study_card_create", "halo_study_card_edit", "halo_study_card_accept", "halo_study_card_review", "halo_study_card_delete")
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        for (name in names) {
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(name))
            val owner = skills.single { name in it.tools }
            assertEquals("halo.study", owner.id.value)
            assertEquals(if (name in setOf("halo_study_delete", "halo_study_card_delete")) SkillRisk.DESTRUCTIVE
                else if (name in setOf("halo_get_study_session", "halo_study_sessions", "halo_study_state", "halo_study_card_get")) SkillRisk.READ_ONLY
                else SkillRisk.REVERSIBLE, owner.toolDefinitions.single { it.toolId == name }.risk)
        }
    }
}
