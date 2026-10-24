package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.sdk.runtime.skills.SkillRisk
import org.junit.Assert.*
import org.junit.Test

class NoteToolProvenanceTest {
    @Test fun noteOperationsHaveOneWorkspaceOwnerAndPrivacyIncludingWriteReceipts() {
        val names = setOf("halo_note_get", "halo_note_read_block", "halo_note_rename", "halo_note_insert_block", "halo_note_edit_block", "halo_note_move_block", "halo_note_indent_block",
            "halo_note_split_block", "halo_note_merge_block", "halo_note_duplicate_block", "halo_note_delete_block", "halo_note_insert_reference", "halo_note_set_reference", "halo_note_replace_content",
            "halo_note_archive", "halo_note_trash", "halo_note_restore", "halo_note_remove_from_inbox", "halo_note_purge", "halo_note_purge_retry", "halo_note_attachments", "halo_note_reuse_image", "halo_note_unlink_attachment", "halo_note_purge_status")
        val skills = DefaultSkillRegistry.defaultBuiltInSkills()
        for (name in names) {
            assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(name))
            val owner = skills.single { name in it.tools }
            assertEquals("halo.notebook", owner.id.value)
            assertEquals(if (name in setOf("halo_note_delete_block", "halo_note_replace_content", "halo_note_trash", "halo_note_purge", "halo_note_purge_retry", "halo_note_unlink_attachment")) SkillRisk.DESTRUCTIVE
                else if (name in setOf("halo_note_get", "halo_note_read_block", "halo_note_purge_status", "halo_note_attachments")) SkillRisk.READ_ONLY else SkillRisk.REVERSIBLE,
                owner.toolDefinitions.single { it.toolId == name }.risk)
        }
    }
}
