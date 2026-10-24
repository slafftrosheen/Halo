package com.halo.app.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.*

data class ProviderModelMetadata(val id: String, val name: String, val source: String,
    val capabilities: List<String>)
data class ProviderConfigurationSnapshot(val providerId: String, val name: String, val location: String,
    val configured: Boolean, val selected: Boolean, val connection: String, val catalogue: String,
    val sourceVersion: String, val models: List<ProviderModelMetadata>,
    val diagnosticStage: String? = null, val diagnosticOutcome: String? = null)
enum class ProviderConfigurationCommand { REFRESH_MODELS, TEST_CONNECTION }

/** Non-secret projections only. The implementation owns exact configuration admission. */
interface ProviderConfigurationControl {
    suspend fun providerConfigurations(): List<ProviderConfigurationSnapshot>
    suspend fun providerConfiguration(providerId: String): ProviderConfigurationSnapshot?
    suspend fun requestProviderCheck(providerId: String, expectedVersion: String,
        command: ProviderConfigurationCommand, admitted: () -> Boolean): ProviderConfigurationSnapshot?
}

/** One explicit metadata check. The existing guarded transport retains final network authority. */
internal suspend fun <T> providerMetadataCheck(policy: RemoteDispatchPolicy?, providerId: String,
    local: Boolean, action: suspend () -> T): T {
    if (local) return action()
    val owner = policy ?: throw RemoteDispatchBlocked()
    val revision = RemoteDispatchContext.provenance.get()?.revision ?: owner.revision
    val request = RemoteDispatchRequest(providerId, RemoteOperation.PROVIDER_CHECK, setOf(RemoteDataCategory.ACCOUNT_METADATA))
    return coroutineScope {
        val operation = currentCoroutineContext().job
        val lease = owner.acquire(request, revision, recordActivity = false) { operation.cancel() }
        try {
            lease.requireCurrent()
            val result = withContext(RemoteDispatchContext.intent.asContextElement(RemoteDispatchIntent(request, revision))) { action() }
            lease.requireCurrent()
            lease.finish(RemoteDispatchOutcome.COMPLETED)
            result
        } catch (cancelled: CancellationException) { lease.finish(RemoteDispatchOutcome.CANCELLED); throw cancelled }
        catch (error: Exception) { lease.finish(RemoteDispatchOutcome.FAILED); throw error }
    }
}
