package com.halo.runtime.cognition.skills

import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.skills.SkillId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GlobalToolPolicyTest {
    private class BatchStorage : Storage {
        val strings = mutableMapOf<String, String>()
        var batches = 0
        var failBatch = false
        override fun getString(key: String, defaultValue: String?) = strings[key] ?: defaultValue
        override fun putString(key: String, value: String?) { error("Global policy must use the batch storage contract") }
        override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
            batches++
            check(!failBatch) { "Disk unavailable" }
            strings.forEach { (key, value) -> if (value != null) this.strings[key] = value }
        }
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun putInt(key: String, value: Int) = Unit
        override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
        override fun putBoolean(key: String, value: Boolean) = Unit
        override fun remove(key: String) { strings.remove(key) }
        override fun clear() { strings.clear() }
    }
    @Test fun globalAllowIsOneDurableBatchAndSurvivesRecreation() = runTest {
        val storage = BatchStorage()
        val registry = DefaultSkillRegistry(storage)
        registry.setApprovalPolicyForAll(ToolApprovalPolicy.ALWAYS_ALLOW)
        assertEquals(1, storage.batches)
        val reopened = DefaultSkillRegistry(storage)
        reopened.skills.value.forEach { skill ->
            assertEquals(ToolApprovalPolicy.ALWAYS_ALLOW, reopened.approvalPolicy(skill.id))
        }
    }
    @Test fun failedBatchDoesNotReportPoliciesAsSaved() = runTest {
        val storage = BatchStorage()
        val registry = DefaultSkillRegistry(storage)
        val initial = registry.approvalPolicy(SkillId("halo.messaging"))
        storage.failBatch = true
        try { registry.setApprovalPolicyForAll(ToolApprovalPolicy.ALWAYS_ALLOW); fail("Expected storage failure") }
        catch (_: IllegalStateException) { }
        assertEquals(initial, registry.approvalPolicy(SkillId("halo.messaging")))
    }
}
