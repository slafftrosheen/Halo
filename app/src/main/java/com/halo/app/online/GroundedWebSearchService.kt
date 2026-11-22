package com.halo.app.online

import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiRoutingPolicy
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import com.halo.app.ai.PrivacyMode
import com.halo.app.ai.savedPrivacyMode
import com.halo.app.ai.whileCloudMode
import com.halo.app.ai.remoteFeatureEnabled
import com.halo.app.ai.OnlineFeature
import com.halo.app.ai.ModeProfileStorage
import java.math.BigDecimal

data class WebSearchReview(val challenge: OnlineConsentChallenge, val providerId: String, val model: String, val searchMode: String) {
    val providerName get() = if (providerId == "openai") "OpenAI API" else "OpenRouter"
    val pricingUrl get() = if (providerId == "openai") "https://developers.openai.com/api/docs/pricing"
        else "https://openrouter.ai/docs/guides/features/plugins/web-search"
}

/** Explicit query-only lookup. A normal answer request never enables the paid search plugin. */
class GroundedWebSearchService(
    private val storage: Storage, private val router: AiProviderRouter,
    private val allowOnline: () -> Boolean, private val client: OpenRouterOnlineClient = OpenRouterOnlineClient(),
    private val privacyModes: kotlinx.coroutines.flow.StateFlow<PrivacyMode>? = null,
    private val openAi: OpenAiWebSearchClient? = null,
) {
    private val consents = OneShotConsent()
    private val ledger = OnlineOperationLedger(storage)
    private data class Route(val providerId: String, val model: String, val configurationRevision: Long?, val remoteRevision: Long?) {
        val searchMode get() = when {
            providerId == "openai" -> "native_openai"
            model == OpenRouterOnlineClient.FREE_MODEL -> "exa"
            else -> "native_or_exa"
        }
        fun review(challenge: OnlineConsentChallenge) = WebSearchReview(challenge, providerId, model, searchMode)
    }
    /** Local preflight for discovery. No challenge, reservation, probe or request is created. */
    fun availability(): OnlineProblem? = try {
        checkAccess(null)
        key(resolveRoute().providerId)
        null
    } catch (rejected: OnlineOperationRejected) { rejected.problem }
    suspend fun prepare(query: String, language: String): OnlineConsentChallenge = prepareReview(query, language).challenge
    suspend fun prepareReview(query: String, language: String): WebSearchReview = withContext(Dispatchers.IO) {
        checkAccess(query)
        checkLanguage(language)
        val route = resolveRoute()
        route.review(consents.prepare(fingerprint(query, language, key(route.providerId), route)))
    }
    fun reviewForApproval(query: String, language: String, id: String): WebSearchReview? = try {
        checkAccess(query)
        checkLanguage(language)
        val route = resolveRoute()
        if (consents.matches(id, fingerprint(query, language, key(route.providerId), route)))
            route.review(OnlineConsentChallenge(id)) else null
    } catch (_: OnlineOperationRejected) { null }
    fun discard(challenge: OnlineConsentChallenge?) { consents.discard(challenge?.id) }
    suspend fun search(query: String, language: String, challenge: OnlineConsentChallenge,
        reservationUsd: BigDecimal, acknowledgeVariableCost: Boolean): GroundedWebAnswer = withContext(Dispatchers.IO) {
        checkAccess(query)
        checkLanguage(language)
        if (!acknowledgeVariableCost) throw OnlineOperationRejected(OnlineProblem.CONSENT_REQUIRED)
        if (reservationUsd < OpenRouterOnlineClient.SEARCH_FEE_ESTIMATE_USD)
            throw OnlineOperationRejected(OnlineProblem.INVALID_AMOUNT)
        val route = resolveRoute()
        val credential = key(route.providerId)
        consents.consume(challenge.id, fingerprint(query, language, credential, route))
        currentCoroutineContext().ensureActive()
        checkAccess(query)
        val record = ledger.start("SEARCH", route.providerId, route.model, reservationUsd)
        var reported: BigDecimal? = null
        var complete = false
        try {
            // Persistence can yield to settings changes. Recheck after the durable reservation.
            currentCoroutineContext().ensureActive()
            checkAccess(query)
            if (key(route.providerId) != credential || resolveRoute() != route) throw OnlineOperationRejected(OnlineProblem.CONSENT_EXPIRED)
            val costs = com.halo.sdk.provider.RemoteCostObservation()
            val result = withContext(com.halo.sdk.provider.RemoteDispatchContext.costs.asContextElement(costs)) {
                whileCloudMode(privacyModes) {
                    if (resolveRoute() != route) throw OnlineOperationRejected(OnlineProblem.CONSENT_EXPIRED)
                    if (route.providerId == "openai") {
                        requireNotNull(openAi).search(credential, route.model, query.trim(), language)
                    } else {
                        val root = client.search(credential, query.trim(), language, route.model)
                        reported = OpenRouterOnlineClient.reportedCost(root)
                        reported?.let { costs.providerReportedCostUsd(it.toPlainString()) }
                        OpenRouterOnlineClient.parseAnswer(root, System.currentTimeMillis())
                    }
                }
            }
            complete = true
            result.copy(operationId = record)
        } finally {
            // Cancellation/timeout may still be charged. Preserve the reservation even on process recovery.
            withContext(NonCancellable + Dispatchers.IO) {
                // Retain the durable pre-dispatch reservation if final receipt persistence fails.
                runCatching { ledger.finish(record, reported, if (complete) "COMPLETED" else "UNCERTAIN") }
            }
        }
    }
    suspend fun usage(): OpenRouterKeyUsage = withContext(Dispatchers.IO) {
        if (storage.savedPrivacyMode() == PrivacyMode.PRIVATE) throw OnlineOperationRejected(OnlineProblem.LOCAL_ONLY)
        if (!allowOnline()) throw OnlineOperationRejected(OnlineProblem.CHILD_RESTRICTED)
        if (!storage.remoteFeatureEnabled(OnlineFeature.WEB)) throw OnlineOperationRejected(OnlineProblem.FEATURE_DISABLED)
        if (storage !is ModeProfileStorage && router.routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY)
            throw OnlineOperationRejected(OnlineProblem.LOCAL_ONLY)
        whileCloudMode(privacyModes) { client.keyUsage(key("openrouter")) }
    }
    private fun checkAccess(query: String?) {
        if (storage.savedPrivacyMode() == PrivacyMode.PRIVATE) throw OnlineOperationRejected(OnlineProblem.LOCAL_ONLY)
        if (!allowOnline()) throw OnlineOperationRejected(OnlineProblem.CHILD_RESTRICTED)
        if (!storage.remoteFeatureEnabled(OnlineFeature.WEB)) throw OnlineOperationRejected(OnlineProblem.FEATURE_DISABLED)
        if (storage !is ModeProfileStorage && router.routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY)
            throw OnlineOperationRejected(OnlineProblem.LOCAL_ONLY)
        if (query != null && query.trim().length !in 1..2000) throw OnlineOperationRejected(OnlineProblem.INVALID_RESPONSE)
    }
    private fun checkLanguage(language: String) {
        if (language !in setOf("en", "ru", "lv", "pl")) throw OnlineOperationRejected(OnlineProblem.INVALID_RESPONSE)
    }
    private fun configuredKey(provider: String): String? =
        storage.getString(if (provider == "openai") "openai_api_key" else "openrouter_api_key")?.trim()?.takeIf { it.isNotEmpty() }
    private fun key(provider: String): String = configuredKey(provider)
        ?: throw OnlineOperationRejected(OnlineProblem.KEY_REQUIRED)
    private fun resolveRoute(): Route {
        val revision = router.configurationRevision
        val provider = router.selectedProviderId.value
        val model = router.selectedModelId.value?.trim()
        val remoteRevision = router.remoteDispatchRevision
        val providers = router.getAllProviders()
        if (revision != router.configurationRevision) throw OnlineOperationRejected(OnlineProblem.CONSENT_EXPIRED)
        if (provider == "openai" && model != null && openAi != null && OpenAiWebSearchClient.supports(model) &&
            providers.any { it.id == "openai" } && configuredKey("openai") != null) {
            return Route("openai", model, revision, remoteRevision)
        }
        if (providers.none { it.id == "openrouter" }) throw OnlineOperationRejected(OnlineProblem.UNAVAILABLE)
        val openRouterModel = model?.removeSuffix(":online")
            ?.takeIf { provider == "openrouter" && OpenRouterOnlineClient.validModel(it) }
        return Route("openrouter", openRouterModel ?: OpenRouterOnlineClient.FREE_MODEL, revision, remoteRevision)
    }
    private fun fingerprint(query: String, language: String, key: String, route: Route): String = OneShotConsent.fingerprint(
        query.trim().toByteArray(), language.toByteArray(), key.toByteArray(), route.providerId.toByteArray(), route.model.toByteArray(),
        route.configurationRevision.toString().toByteArray(), route.remoteRevision.toString().toByteArray())
}
