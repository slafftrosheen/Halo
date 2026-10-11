package com.halo.runtime.cognition.context

import com.halo.runtime.cognition.skills.BundledSkillInstructionSource
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.UseSkillToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.skills.SkillId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDemandSkillTest {
    @Test fun `every shipped runtime skill has loadable operational guidance`() = runTest {
        val source = BundledSkillInstructionSource()
        com.halo.runtime.cognition.skills.DefaultSkillRegistry.defaultBuiltInSkills().forEach { skill ->
            assertTrue(skill.id.value, source.load(skill.id)?.body?.isNotBlank() == true)
        }
        assertEquals(null, source.load(SkillId("missing.skill")))
    }

    @Test fun `app and workspace guidance keep discovered identities and receipt boundaries`() = runTest {
        val source = BundledSkillInstructionSource()
        assertTrue(source.load(SkillId("halo.current_app"))!!.body.contains("fresh returned token"))
        assertTrue(source.load(SkillId("halo.study"))!!.body.contains("preserve the user's study language"))
        assertTrue(source.load(SkillId("halo.sharing"))!!.body.contains("does not prove a recipient"))
    }

    @Test
    fun `bundled skill instruction source loads known skills`() = runTest {
        val source = BundledSkillInstructionSource()
        val notebookSkill = source.load(SkillId("halo.notebook"))

        assertNotNull(notebookSkill)
        assertEquals("halo.notebook", notebookSkill?.skillId?.value)
        assertTrue(notebookSkill!!.body.contains("Search and manage Notebook"))
    }

    @Test
    fun `use skill tool executor executes and returns instructions`() = runTest {
        val source = BundledSkillInstructionSource()
        val executor = UseSkillToolExecutor(source, null)

        val toolCall = ToolCall(
            id = "call-1",
            functionName = "halo_use_skill",
            arguments = mapOf("skillId" to "halo.memory")
        )

        val result = executor.execute(toolCall)

        assertTrue(result is ToolExecutionResult.Success)
        val success = result as ToolExecutionResult.Success
        assertTrue(success.output.contains("Loaded operational guidance for skill halo.memory"))
    }
}
