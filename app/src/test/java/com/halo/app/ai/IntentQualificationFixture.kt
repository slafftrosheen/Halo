package com.halo.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.*
import com.halo.providers.litert.runtime.LocalModelSession
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.robolectric.Shadows

/** Synthetic runtime/configuration only: never model-quality or physical speed evidence. */
internal class IntentQualificationFixture(scope: CoroutineScope) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val storage = MemoryStorage()
    val model = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
    val inventory = Inventory(LocalModelDescriptor(model.id, model.displayName, "/fixture/${model.filename}",
        model.expectedBytes, 0, model.sha256, 4096, false, false, LocalModelValidation.VERIFIED_ARTIFACT))
    val artifact get() = inventory.artifact
    val runtime = Runtime()
    val provider = LiteRtLocalProvider(context, inventoryRepository = inventory, storage = storage,
        modelSession = LocalModelSession({ runtime }, scope))
    init { packageRevision(1000) }
    val proofs = WorkloadQualificationStore(context, storage)
    fun packageRevision(value: Long) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime = value
        Shadows.shadowOf(context.packageManager).installPackage(info)
    }
    suspend fun warm() {
        check(withContext(Dispatchers.Default) { provider.loadModelIfNeeded(model.id) })
        check(provider.residentModelIdForIntent() == model.id)
        check(!provider.inferenceBusyForIntent())
        check(provider.modelSession.isResidentFor(model.id, artifact.path, artifact.sha256,
            provider.loadConfigFor(artifact).copy(computeBackend = LocalComputeBackend.CPU)))
    }
    fun router(qualified: (LocalModelDescriptor, LiteRtLocalProvider) -> Boolean = { a, p -> proofs.isIntentQualified(p, a) }) =
        LocalIntentModelRouter(storage, inventory, qualified) { provider }
    suspend fun certify(completed: Boolean = true, attempted: Int = 48, warm: Int = attempted,
        matched: Int = attempted, noAdvice: Int = 0, elapsed: List<Long> = List(attempted) { 20 },
        fixture: String = LocalIntentModelRouter.FIXTURE_SHA256): Boolean = proofs.recordIntentSuite(provider, artifact,
        requireNotNull(proofs.intentEnvironment(provider, artifact)), fixture, completed, attempted, warm, matched, noAdvice, elapsed)
    fun optIn(value: Boolean = true) { storage.putBoolean(LocalIntentModelRouter.ENABLED_KEY, value) }

    class MemoryStorage : Storage {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        var writes = 0
        var afterStringRead: (String) -> Unit = {}
        override fun getString(key: String, defaultValue: String?): String? {
            val observed = values[key] as? String ?: defaultValue
            afterStringRead(key)
            return observed
        }
        override fun putString(key: String, value: String?) { writes++; if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { writes++; values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { writes++; values[key] = value }
        override fun remove(key: String) { writes++; values.remove(key) }
        override fun clear() { writes++; values.clear() }
    }
    class Inventory(var artifact: LocalModelDescriptor) : LocalModelInventoryRepository {
        override val inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Ready(listOf(artifact), 1))
        var resolves = 0
        override suspend fun refresh(reason: ModelInventoryRefreshReason) { error("Unexpected inventory refresh") }
        override suspend fun resolveModel(modelId: String): LocalModelDescriptor? { resolves++; return artifact.takeIf { it.id == modelId } }
        override fun invalidate(reason: String) = Unit
    }
    class Runtime : LocalInferenceRuntime {
        override val format = LocalModelFormat.LITERT_LM
        override val state = MutableStateFlow(LocalModelSessionState.IDLE)
        override var loadedModelId: String? = null
        val generations = mutableListOf<RuntimeGenerationConfig>()
        var beforeReply: () -> Unit = {}
        override fun isLoaded() = loadedModelId != null
        override suspend fun loadModel(modelId: String, modelPath: String, config: RuntimeLoadConfig): Result<RuntimeLoadResult> {
            loadedModelId = modelId
            return Result.success(RuntimeLoadResult(LocalComputeBackend.CPU, 0, config.contextSize, null))
        }
        override suspend fun generate(config: RuntimeGenerationConfig, onToken: suspend (String) -> Unit): Result<RuntimeGenerationResult> {
            generations += config; beforeReply()
            return Result.success(RuntimeGenerationResult("ACTION", emptyList(), 1, 1, 1.0, 1))
        }
        override suspend fun cancelGeneration() = Unit
        override suspend fun unloadModel(): Result<Unit> { loadedModelId = null; return Result.success(Unit) }
    }
}
