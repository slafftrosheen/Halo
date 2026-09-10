package com.halo.engines.ai

import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ProviderRequest
import com.halo.sdk.runtime.ProviderResult
import com.halo.sdk.runtime.ProviderFailure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.util.UUID

import kotlin.time.Duration.Companion.seconds

/**
 * Routes AI requests to the appropriate provider based on capabilities and configuration.
 */
class DefaultAiProviderRouter(
    private val providers: List<AiProvider>,
    private val storage: Storage,
    private val eventBus: EventBus? = null,
    private val logger: RuntimeLogger? = null
) : AiProviderRouter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var pollingJob: Job? = null

    private val _selectedProviderId = MutableStateFlow<String?>(
        storage.getString("active_ai_provider") ?: providers.firstOrNull()?.id
    )
    override val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    private val _selectedModelId = MutableStateFlow<String?>(
        storage.getString("active_ai_model")
    )
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _diagnosticsFlow = MutableStateFlow<List<AiProviderDiagnostic>>(emptyList())
    override val diagnosticsFlow: StateFlow<List<AiProviderDiagnostic>> = _diagnosticsFlow.asStateFlow()

    init {
        scope.launch {
            refreshDiagnostics()
        }
    }

    override fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                refreshDiagnostics()
                delay(30.seconds)
            }
        }
    }

    override fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private val _routingPolicy = MutableStateFlow<AiRoutingPolicy>(
        storage.getString("ai_routing_policy")?.let { 
            runCatching { enumValueOf<AiRoutingPolicy>(it) }.getOrDefault(AiRoutingPolicy.AUTOMATIC)
        } ?: AiRoutingPolicy.AUTOMATIC
    )
    
    fun setRoutingPolicy(policy: AiRoutingPolicy) {
        _routingPolicy.value = policy
        storage.putString("ai_routing_policy", policy.name)
    }

    override fun selectProvider(id: String) {
        if (providers.any { it.id == id }) {
            val providerChanged = _selectedProviderId.value != id
            _selectedProviderId.value = id
            storage.putString("active_ai_provider", id)
            if (providerChanged) {
                _selectedModelId.value = null
                storage.putString("active_ai_model", null)
            }
            scope.launch {
                refreshDiagnostics()
            }
        }
    }

    override fun selectModel(modelId: String?) {
        _selectedModelId.value = modelId
        storage.putString("active_ai_model", modelId)
        scope.launch {
            refreshDiagnostics()
        }
    }

    override fun getAllProviders(): List<AiProvider> = providers

    suspend fun refreshDiagnostics(): List<AiProviderDiagnostic> {
        val selectedId = _selectedProviderId.value
        val list = providers.map { provider ->
            val status = provider.getStatus()
            AiProviderDiagnostic(
                providerId = provider.id,
                displayName = provider.displayName,
                state = status.state,
                message = status.message,
                capabilities = provider.getCapabilities(),
                selected = provider.id == selectedId,
                selectedModelId = if (provider.id == selectedId) _selectedModelId.value else null,
                local = provider.isLocal
            )
        }
        _diagnosticsFlow.value = list
        return list
    }

    override suspend fun diagnostics(): List<AiProviderDiagnostic> {
        return refreshDiagnostics()
    }

    override suspend fun getEligibleProvider(request: AiRequest): AiProvider {
        return resolveExecutionTarget(request).provider
    }

    override suspend fun resolveExecutionTarget(
        request: AiRequest
    ): AiExecutionTarget {

        val policy = _routingPolicy.value
        val selectedId = _selectedProviderId.value
        val preferredProvider = providers.firstOrNull { it.id == selectedId }

        return when (policy) {
            AiRoutingPolicy.EXPLICIT -> {
                resolveExplicitTarget(
                    preferredProvider = preferredProvider,
                    request = request
                )
            }

            AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> {
                resolvePreferredWithFallbackTarget(
                    preferredProvider = preferredProvider,
                    request = request
                )
            }

            AiRoutingPolicy.LOCAL_ONLY -> {
                resolveAutomaticTarget(
                    preferredProvider = preferredProvider,
                    request = request,
                    localOnly = true
                )
            }

            AiRoutingPolicy.AUTOMATIC -> {
                resolveAutomaticTarget(
                    preferredProvider = preferredProvider,
                    request = request,
                    localOnly = false
                )
            }
        }
    }

    private suspend fun resolveExplicitTarget(
        preferredProvider: AiProvider?,
        request: AiRequest
    ): AiExecutionTarget {

        val provider = preferredProvider
            ?: throw AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                "Explicitly selected provider not found: ${_selectedProviderId.value}"
            )

        val status = provider.getStatus()

        if (status.state != ProviderStatus.State.READY) {
            throw AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                "Explicitly selected provider is not ready: " +
                    "${provider.id}, state=${status.state}, message=${status.message}"
            )
        }

        return resolveTargetForProvider(
            provider = provider,
            request = request,
            allowSelectedModelFallback = false
        )
    }

    private suspend fun resolvePreferredWithFallbackTarget(
        preferredProvider: AiProvider?,
        request: AiRequest
    ): AiExecutionTarget {

        if (preferredProvider != null) {
            val status = preferredProvider.getStatus()

            if (status.state == ProviderStatus.State.READY) {
                try {
                    return resolveTargetForProvider(
                        provider = preferredProvider,
                        request = request,
                        allowSelectedModelFallback = false
                    )
                } catch (e: AiProviderException) {
                    if (e.type == AiErrorType.MODEL_NOT_FOUND || e.type == AiErrorType.MODEL_UNAVAILABLE) {
                        throw e
                    }
                }
            }
        }

        return getFallbackExecutionTarget(request, failedProviderId = preferredProvider?.id)
            ?: throw AiProviderException(
                AiErrorType.PROVIDER_UNAVAILABLE,
                "No provider/model target could satisfy the request"
            )
    }

    private suspend fun resolveAutomaticTarget(
        preferredProvider: AiProvider?,
        request: AiRequest,
        localOnly: Boolean
    ): AiExecutionTarget {

        val orderedProviders = buildList {
            preferredProvider?.let { add(it) }

            providers
                .filter { it.id != preferredProvider?.id }
                .forEach { add(it) }
        }

        val failures = mutableListOf<String>()

        for (provider in orderedProviders) {

            if (localOnly && !provider.isLocal) {
                failures += "${provider.id}: not local"
                continue
            }

            val status = provider.getStatus()

            if (status.state != ProviderStatus.State.READY) {
                failures +=
                    "${provider.id}: state=${status.state}"
                continue
            }

            val result = runCatching {
                resolveTargetForProvider(
                    provider = provider,
                    request = request,
                    allowSelectedModelFallback =
                        provider.id != preferredProvider?.id
                )
            }

            val target = result.getOrNull()

            if (target != null) {
                return target
            }

            val error = result.exceptionOrNull()
            if (error is AiProviderException && (error.type == AiErrorType.MODEL_NOT_FOUND || error.type == AiErrorType.MODEL_UNAVAILABLE)) {
                if (provider.id == preferredProvider?.id) {
                    throw error
                }
            }

            error?.let {
                failures += "${provider.id}: ${it.message}"
            }
        }

        throw AiProviderException(
            AiErrorType.PROVIDER_UNAVAILABLE,
            buildString {
                append("No provider/model target could satisfy the request.")
                if (failures.isNotEmpty()) {
                    append("\n")
                    append(
                        failures.joinToString(
                            separator = "\n"
                        ) { "- $it" }
                    )
                }
            }
        )
    }

    private suspend fun resolveTargetForProvider(
        provider: AiProvider,
        request: AiRequest,
        allowSelectedModelFallback: Boolean
    ): AiExecutionTarget {

        val providerCapabilities =
            provider.getCapabilities()

        validateProviderCapabilities(
            providerCapabilities,
            request
        )

        val models = provider.listModels()

        if (models.isEmpty()) {
            throw AiProviderException(
                AiErrorType.MODEL_UNAVAILABLE,
                "Provider ${provider.id} did not advertise any models"
            )
        }

        /*
         * An explicit request.modelId is ALWAYS strict.
         *
         * Never silently replace an explicitly requested model with another
         * provider/model.
         */
        if (request.modelId != null) {

            val model = models.firstOrNull {
                it.id == request.modelId
            } ?: throw AiProviderException(
                AiErrorType.MODEL_NOT_FOUND,
                "Model ${request.modelId} is not advertised by provider ${provider.id}"
            )

            validateModelInput(model, request)

            return AiExecutionTarget(
                provider = provider,
                model = model,
                providerCapabilities = providerCapabilities
            )
        }

        /*
         * The user-selected model is provider-scoped.
         *
         * Preferred provider:
         *     selected model must exist and be compatible.
         *
         * Fallback provider:
         *     selected model from the failed provider must NOT be reused.
         */
        val selectedModelId =
            if (allowSelectedModelFallback) {
                null
            } else {
                _selectedModelId.value
            }

        if (selectedModelId != null) {

            val selectedModel =
                models.firstOrNull {
                    it.id == selectedModelId
                } ?: throw AiProviderException(
                    AiErrorType.MODEL_NOT_FOUND,
                    "Selected model $selectedModelId is not advertised by provider ${provider.id}"
                )

            validateModelInput(
                selectedModel,
                request
            )

            return AiExecutionTarget(
                provider = provider,
                model = selectedModel,
                providerCapabilities = providerCapabilities
            )
        }

        /*
         * Automatic/fallback provider selection.
         *
         * Choose the first advertised model that can actually consume the
         * request.
         */
        val compatibleModel =
            models.firstOrNull {
                supportsModelInput(
                    model = it,
                    request = request
                )
            } ?: throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Provider ${provider.id} has no advertised model compatible with this request"
            )

        return AiExecutionTarget(
            provider = provider,
            model = compatibleModel,
            providerCapabilities = providerCapabilities
        )
    }

    private suspend fun getFallbackExecutionTarget(
        request: AiRequest,
        failedProviderId: String?
    ): AiExecutionTarget? {
        val policy = _routingPolicy.value
        val fallbackProviders = providers.filter { it.id != failedProviderId }

        for (provider in fallbackProviders) {
            val status = provider.getStatus()

            if (status.state != ProviderStatus.State.READY) {
                continue
            }

            if (policy == AiRoutingPolicy.LOCAL_ONLY && !provider.isLocal) {
                continue
            }

            val target = runCatching {
                resolveTargetForProvider(
                    provider = provider,
                    request = request,
                    allowSelectedModelFallback = true
                )
            }.getOrNull()

            if (target != null) {
                return target
            }
        }
        return null
    }

    private fun validateProviderCapabilities(
        capabilities: Set<AiCapability>,
        request: AiRequest
    ) {
        val missing = mutableListOf<String>()

        if (
            request.text != null &&
            !capabilities.contains(AiCapability.TEXT)
        ) {
            missing += "TEXT"
        }

        if (
            request.image != null &&
            !capabilities.contains(AiCapability.IMAGE)
        ) {
            missing += "IMAGE"
        }

        if (
            request.audio != null &&
            !capabilities.contains(AiCapability.AUDIO)
        ) {
            missing += "AUDIO"
        }

        if (
            request.video != null &&
            !capabilities.contains(AiCapability.VIDEO)
        ) {
            missing += "VIDEO"
        }

        if (missing.isNotEmpty()) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Provider is missing capabilities: ${missing.joinToString()}"
            )
        }
    }

    private fun validateModelInput(
        model: AiModel,
        request: AiRequest
    ) {
        if (!supportsModelInput(model, request)) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Model ${model.id} does not support this request"
            )
        }
    }

    private fun supportsModelInput(
        model: AiModel,
        request: AiRequest
    ): Boolean {

        val capabilities =
            model.inputCapabilities

        return (
            request.text == null ||
                capabilities.contains(AiCapability.TEXT)
            ) &&
            (
                request.image == null ||
                    capabilities.contains(AiCapability.IMAGE)
            ) &&
            (
                request.audio == null ||
                    capabilities.contains(AiCapability.AUDIO)
            ) &&
            (
                request.video == null ||
                    capabilities.contains(AiCapability.VIDEO)
            )
    }

    override suspend fun execute(
        request: AiRequest,
        context: AiContext
    ): AiResponse {

        val policy = _routingPolicy.value

        val target =
            resolveExecutionTarget(request)

        val finalRequest =
            request.copy(
                modelId = target.modelId
            )

        val requestId =
            UUID.randomUUID().toString()

        logger?.info(
            "AiProviderRouter",
            "AI execute start provider=${target.providerId} model=${target.modelId}"
        )

        eventBus?.publish(
            ProviderRequest(
                requestId = requestId,
                providerId = target.providerId
            )
        )

        return try {

            finalRequest.validateAgainst(
                target.providerCapabilities
            )

            validateModelInput(
                target.model,
                finalRequest
            )

            val response =
                target.provider.generate(
                    finalRequest,
                    context
                )

            validateResponse(
                target,
                response
            )

            eventBus?.publish(
                ProviderResult(
                    requestId = requestId,
                    success = true
                )
            )

            response

        } catch (e: CancellationException) {
            throw e

        } catch (e: Throwable) {

            eventBus?.publish(
                ProviderFailure(
                    requestId = requestId,
                    providerId = target.providerId,
                    reason = e.message ?: "Execution failed"
                )
            )

            if (policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC) {
                val fallbackTarget = getFallbackExecutionTarget(request, failedProviderId = target.providerId)
                if (fallbackTarget != null) {
                    val fallbackRequest = request.copy(modelId = fallbackTarget.modelId)
                    val fallbackRequestId = UUID.randomUUID().toString()
                    eventBus?.publish(ProviderRequest(requestId = fallbackRequestId, providerId = fallbackTarget.providerId))
                    try {
                        fallbackRequest.validateAgainst(fallbackTarget.providerCapabilities)
                        validateModelInput(fallbackTarget.model, fallbackRequest)
                        val fallbackResp = fallbackTarget.provider.generate(fallbackRequest, context)
                        validateResponse(fallbackTarget, fallbackResp)
                        eventBus?.publish(ProviderResult(requestId = fallbackRequestId, success = true))
                        return fallbackResp
                    } catch (fbErr: CancellationException) {
                        throw fbErr
                    } catch (fbErr: Throwable) {
                        eventBus?.publish(ProviderFailure(requestId = fallbackRequestId, providerId = fallbackTarget.providerId, reason = fbErr.message ?: "Fallback failed"))
                        throw fbErr
                    }
                }
            }

            throw e
        }
    }

    private fun validateResponse(
        target: AiExecutionTarget,
        response: AiResponse
    ) {
        AiResponseValidator.validate(
            provider = target.provider,
            capabilities = target.providerCapabilities,
            expectedModelId = target.modelId,
            response = response
        )
    }
}
