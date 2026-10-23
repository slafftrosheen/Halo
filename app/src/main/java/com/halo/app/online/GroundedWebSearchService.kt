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

/** Explicit query-only lookup. A normal answer request never enables the paid search plugin. */
class GroundedWebSearchService(
    private val storage: Storage, private val router: AiProviderRouter,
    private val allowOnline: () -> Boolean, private val client: OpenRouterOnlineClient = OpenRouterOnlineClient(),
    private val privacyModes: kotlinx.coroutines.flow.StateFlow<PrivacyMode>? = null,
) {
    private val consents = OneShotConsent()
    private val ledger = OnlineOperationLedger(storage)
    /** Local preflight for discovery. No challenge, reservation, probe or request is created. */
    fun availability(): OnlineProblem? = try {
        checkAccess(null)
        key()
        null
    } catch (rejected: OnlineOperationRejected) { rejected.problem }
    suspend fun prepare(query: String, language: String): OnlineConsentChallenge = withContext(Dispatchers.IO) {
        checkAccess(query)
        checkLanguage(language)
        consents.prepare(fingerprint(query, language, key()))
    }
    fun discard(challenge: OnlineConsentChallenge?) { consents.discard(challenge?.id) }
    suspend fun search(query: String, language: String, challenge: OnlineConsentChallenge,
        reservationUsd: BigDecimal, acknowledgeVariableCost: Boolean): GroundedWebAnswer = withContext(Dispatchers.IO) {
        checkAccess(query)
        checkLanguage(language)
        if (!acknowledgeVariableCost) throw OnlineOperationRejected(OnlineProblem.CONSENT_REQUIRED)
        if (reservationUsd < OpenRouterOnlineClient.SEARCH_FEE_ESTIMATE_USD)
            throw OnlineOperationRejected(OnlineProblem.INVALID_AMOUNT)
        val credential = key()
        consents.consume(challenge.id, fingerprint(query, language, credential))
        currentCoroutineContext().ensureActive()
        checkAccess(query)
        val record = ledger.start("SEARCH", "openrouter", OpenRouterOnlineClient.FREE_MODEL, reservationUsd)
        var reported: BigDecimal? = null
        var complete = false
        try {
            // Persistence can yield to settings changes. Recheck after the durable reservation.
            currentCoroutineContext().ensureActive()
            checkAccess(query)
            if (key() != credential) throw OnlineOperationRejected(OnlineProblem.CONSENT_EXPIRED)
            val costs = com.halo.sdk.provider.RemoteCostObservation()
            val root = withContext(com.halo.sdk.provider.RemoteDispatchContext.costs.asContextElement(costs)) {
                whileCloudMode(privacyModes) { client.search(credential, query.trim(), language) }
            }
            reported = OpenRouterOnlineClient.reportedCost(root)
            reported?.let { costs.providerReportedCostUsd(it.toPlainString()) }
            val result = OpenRouterOnlineClient.parseAnswer(root, System.currentTimeMillis())
            complete = true
            result
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
        whileCloudMode(privacyModes) { client.keyUsage(key()) }
    }
    private fun checkAccess(query: String?) {
        if (storage.savedPrivacyMode() == PrivacyMode.PRIVATE) throw OnlineOperationRejected(OnlineProblem.LOCAL_ONLY)
        if (!allowOnline()) throw OnlineOperationRejected(OnlineProblem.CHILD_RESTRICTED)
        if (!storage.remoteFeatureEnabled(OnlineFeature.WEB)) throw OnlineOperationRejected(OnlineProblem.FEATURE_DISABLED)
        if (storage !is ModeProfileStorage && router.routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY)
            throw OnlineOperationRejected(OnlineProblem.LOCAL_ONLY)
        if (query != null && query.trim().length !in 1..2000) throw OnlineOperationRejected(OnlineProblem.INVALID_RESPONSE)
        if (router.getAllProviders().none { it.id == "openrouter" }) throw OnlineOperationRejected(OnlineProblem.UNAVAILABLE)
    }
    private fun checkLanguage(language: String) {
        if (language !in setOf("en", "ru", "lv")) throw OnlineOperationRejected(OnlineProblem.INVALID_RESPONSE)
    }
    private fun key(): String = storage.getString("openrouter_api_key")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw OnlineOperationRejected(OnlineProblem.KEY_REQUIRED)
    private fun fingerprint(query: String, language: String, key: String): String = OneShotConsent.fingerprint(
        query.trim().toByteArray(), language.toByteArray(), key.toByteArray(), OpenRouterOnlineClient.FREE_MODEL.toByteArray())
}
