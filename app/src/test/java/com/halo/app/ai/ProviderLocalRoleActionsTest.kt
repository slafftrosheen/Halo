package com.halo.app.ai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.engines.ai.catalog.AiModelCatalogRepository
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderLocalRoleActionsTest {
    private class MemoryStorage : Storage {
        val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private class Provider(override val id: String = "provider.litert.local", override val isLocal: Boolean = true) : AiInferenceProvider, CredentialProvider {
        override val displayName = "Fixture"
        override val status = MutableStateFlow(ProviderStatus.READY)
        val model = AiModel("text", "Text", id, setOf(AiCapability.TEXT), 4096, false, false,
            executionLocation = if (isLocal) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE)
        override fun hasCredential() = !isLocal
        override fun credential() = if (isLocal) null else Credential(id, "fixture-only")
        override fun setCredential(credential: Credential) = Unit
        override fun clearCredential() = Unit
        var available = true
        var generations = 0
        override suspend fun listModels() = if (available) listOf(model) else emptyList()
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            generations++; error("Selection must never generate")
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }
    private class Fixture {
        val storage = MemoryStorage()
        val local = Provider()
        val remote = Provider("remote", false)
        val entry = AiModelCatalogEntry(local.model, AiModelRuntimeMetadata(AiModelRef(local.id, "text"),
            AiExecutionLocation.ON_DEVICE, true, false, false, false), source = ModelCatalogueSource.LOCAL_INSTALLED)
        val catalogState = MutableStateFlow(AiModelCatalogState(models = listOf(entry)))
        val catalogue = object : AiModelCatalogRepository {
            override val state = catalogState
            override suspend fun refresh() = Unit
            override suspend fun refresh(providerId: String) { assertEquals(local.id, providerId) }
        }
        val router = DefaultAiProviderRouter(listOf(local, remote), storage, defaultInferenceProviderId = local.id, defaultInferenceModelId = "text")
        val control = ProviderControlRepository(router, catalogue, storage)
        val owner get() = control.localRoleTools
        val target = AiModelRef(local.id, "text")
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val f = Fixture(); store.put("control", f.control); f.router.selectTarget(f.target)
            withContext(Dispatchers.Default) { withTimeout(5000) { f.control.rows.first { rows -> rows.any { it.descriptor.providerId == f.local.id && it.configured } } } }
            block(f)
            assertEquals(0, f.local.generations); assertEquals(0, f.remote.generations)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun explicitInstalledLocalRoleUsesActualRouterAndRejectsReplay() = runTest { fixture { f ->
        val before = f.owner.snapshot(AiModelRole.FAST)!!; assertTrue(f.target in before.candidates)
        val result = f.owner.select(AiModelRole.FAST, before.sourceVersion, AiRoleSelection.EXPLICIT, f.target) { true }!!
        assertEquals(AiRoleBinding(AiModelRole.FAST, f.local.id, "text", AiRoleSelection.EXPLICIT), f.router.roleBinding(AiModelRole.FAST))
        assertEquals(f.router.roleBinding(AiModelRole.FAST), result.binding)
        assertNull(f.owner.select(AiModelRole.FAST, before.sourceVersion, AiRoleSelection.DISABLED, null) { true })
    } }
    @Test fun interveningAssignmentAndUnavailableInventoryReject() = runTest { fixture { f ->
        val before = f.owner.snapshot(AiModelRole.FAST)!!
        f.router.setRoleBinding(f.router.roleBinding(AiModelRole.FAST))
        assertNull(f.owner.select(AiModelRole.FAST, before.sourceVersion, AiRoleSelection.EXPLICIT, f.target) { true })
        val current = f.owner.snapshot(AiModelRole.FAST)!!; f.local.available = false
        assertNull(f.owner.select(AiModelRole.FAST, current.sourceVersion, AiRoleSelection.EXPLICIT, f.target) { true })
    } }
    @Test fun modeChangeRemoteTargetAndAutomaticRemainManual() = runTest { fixture { f ->
        val before = f.owner.snapshot(AiModelRole.FAST)!!
        f.router.selectTarget(AiModelRef(f.remote.id, "text"))
        assertNull(f.owner.select(AiModelRole.FAST, before.sourceVersion, AiRoleSelection.EXPLICIT, f.target) { true })
        val current = f.owner.snapshot(AiModelRole.FAST)!!; assertFalse(current.localPolicy)
        assertNull(f.owner.select(AiModelRole.FAST, current.sourceVersion, AiRoleSelection.INHERIT_DEFAULT, null) { true })
        assertNull(f.owner.select(AiModelRole.FAST, current.sourceVersion, AiRoleSelection.AUTOMATIC, null) { true })
        assertNull(f.owner.snapshot(AiModelRole.CHAT))
    } }
    @Test fun explicitLocalRolePreservesConfiguredRemoteDefaultAndPolicy() = runTest { fixture { f ->
        withContext(Dispatchers.Default) { withTimeout(5000) { f.control.rows.first { rows ->
            rows.any { it.descriptor.providerId == f.remote.id && it.configured }
        } } }
        f.router.selectTarget(AiModelRef(f.remote.id, "text"))
        val policy = f.router.routingPolicy.value
        val before = f.owner.snapshot(AiModelRole.FAST)!!
        assertFalse(before.localPolicy); assertTrue(f.target in before.candidates)
        val after = f.owner.select(AiModelRole.FAST, before.sourceVersion, AiRoleSelection.EXPLICIT, f.target) { true }!!
        assertEquals(AiRoleBinding(AiModelRole.FAST, f.local.id, "text", AiRoleSelection.EXPLICIT), after.binding)
        assertEquals(after.binding, f.router.roleBinding(AiModelRole.FAST))
        assertEquals(f.remote.id, f.router.selectedProviderId.value)
        assertEquals("text", f.router.selectedModelId.value); assertEquals(policy, f.router.routingPolicy.value)
        assertNull(f.owner.select(AiModelRole.FAST, after.sourceVersion, AiRoleSelection.INHERIT_DEFAULT, null) { true })
    } }
    @Test fun disabledAndSafeInheritancePreserveDefaultAndRequireGate() = runTest { fixture { f ->
        var state = f.owner.snapshot(AiModelRole.FAST)!!
        assertNull(f.owner.select(AiModelRole.FAST, state.sourceVersion, AiRoleSelection.DISABLED, null) { false })
        state = f.owner.select(AiModelRole.FAST, state.sourceVersion, AiRoleSelection.DISABLED, null) { true }!!
        assertEquals(AiRoleSelection.DISABLED, state.binding.selection)
        state = f.owner.select(AiModelRole.FAST, state.sourceVersion, AiRoleSelection.INHERIT_DEFAULT, null) { true }!!
        assertEquals(AiRoleSelection.INHERIT_DEFAULT, state.binding.selection)
        assertEquals(f.local.id, f.router.selectedProviderId.value); assertEquals("text", f.router.selectedModelId.value)
    } }
    @Test fun changedCatalogueAndMissingInstalledEvidenceInvalidate() = runTest { fixture { f ->
        val before = f.owner.snapshot(AiModelRole.FAST)!!
        f.catalogState.value = AiModelCatalogState(models = listOf(f.entry.copy(runtime = f.entry.runtime.copy(installed = false))))
        val now = f.owner.snapshot(AiModelRole.FAST)!!; assertTrue(now.candidates.isEmpty())
        assertNotEquals(before.sourceVersion, now.sourceVersion)
        assertNull(f.owner.select(AiModelRole.FAST, before.sourceVersion, AiRoleSelection.EXPLICIT, f.target) { true })
    } }
    @Test fun registeredAdapterRequiresInteractionAndUsesActualBindingReadback() = runTest { fixture { f ->
        val tool = com.halo.app.di.tools.LocalModelRoleTools(f.owner) { true }
        val read = tool.execute(ToolCall("read", "halo_local_role_state", mapOf("role" to "FAST"))) as com.halo.runtime.cognition.tools.ToolExecutionResult.Success
        val source = org.json.JSONObject(read.output).getString("sourceVersion")
        val call = ToolCall("write", "halo_local_role_select", mapOf("role" to "FAST", "sourceVersion" to source, "selection" to "DISABLED"))
        assertTrue(tool.execute(call) is com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)
        val changed = tool.execute(call, com.halo.runtime.cognition.tools.ToolExecutionContext("explicit", false))
        assertTrue(changed is com.halo.runtime.cognition.tools.ToolExecutionResult.Success)
        assertEquals(AiRoleSelection.DISABLED, f.router.roleBinding(AiModelRole.FAST).selection)
    } }

}
