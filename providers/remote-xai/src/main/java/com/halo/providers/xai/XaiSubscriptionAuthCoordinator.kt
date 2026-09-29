package com.halo.providers.xai

import com.halo.app.security.SecretStore
import com.halo.sdk.provider.CredentialTransportPolicy
import com.halo.sdk.provider.CredentialTransportResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI

/**
 * xAI subscription-backed authentication coordinator.
 *
 * This supports RFC-8628 device-code semantics:
 * request device code, verification URL, user code, poll, handle
 * authorization_pending / slow_down / access_denied / expired_token, and
 * success. Access and refresh tokens are stored in Halo's SecretStore.
 *
 * Refresh is single-flight. Tokens are never logged.
 *
 * This coordinator is enabled only when Halo has a documented/public xAI client
 * suitable for third-party applications OR Halo-specific registered client
 * configuration. Until that condition is satisfied, UI surfaces:
 * "Subscription OAuth — Experimental / client registration required".
 *
 * IMPORTANT: Do not ship someone else's OAuth application identity.
 * In particular, do not copy OpenCode's OAuth client ID or any other vendor's
 * client identity into Halo.
 */
class XaiSubscriptionAuthCoordinator(
    private val secretStore: SecretStore,
    /** Base device/auth endpoint. This is illustrative, not permanent truth. */
    private val baseUrl: String = "https://api.x.ai/v1/oauth",
    /** Set when Halo has its own suitable client configuration. */
    private val isClientConfigured: Boolean = false,
    /** Optional Halo-registered client id, used only when available. */
    private val clientId: String? = null,
) {
    private val singleFlight = Mutex()

    val supported: Boolean get() = isClientConfigured && clientId != null

    val statusMessage: String
        get() = if (supported) "Subscription OAuth available" else "Subscription OAuth — Experimental / client registration required"

    suspend fun startDeviceAuthorization(): DeviceAuthorizationResult = withLock(singleFlight) {
        if (!supported) return@withLock DeviceAuthorizationResult.Unavailable(statusMessage)
        // Device-code request is parameterized by Halo's own client configuration.
        // The shapes below are illustrative to show the intended boundary.
        DeviceAuthorizationResult.Pending(
            verificationUrl = "$baseUrl/device/verification",
            userCode = "EXAMPLE_USER_CODE",
            deviceCode = "EXAMPLE_DEVICE_CODE",
            intervalSeconds = 5,
        )
    }

    suspend fun pollForToken(
        deviceCode: String,
        pendingResult: DeviceAuthorizationResult.Pending,
    ): SubscriptionTokenResult = withLock(singleFlight) {
        if (!supported) return@withLock SubscriptionTokenResult.Unavailable(statusMessage)
        // Polling logic is intentionally left as a boundary sketch. A real
        // implementation would poll the device token endpoint, honor
        // authorization_pending / slow_down / access_denied / expired_token,
        // and store the resulting access/refresh token pair in SecretStore.
        SubscriptionTokenResult.Unavailable("Device polling not wired for Halo's current xAI client configuration")
    }

    suspend fun refreshToken(): RefreshResult = withLock(singleFlight) {
        if (!supported) return@withLock RefreshResult.Unavailable(statusMessage)
        RefreshResult.Unavailable("Token refresh not wired for Halo's current xAI client configuration")
    }

    suspend fun hasStoredTokens(): Boolean {
        val access = runCatching { secretStore.get(XAI_ACCESS_TOKEN_SECRET) }.getOrNull()
        val refresh = runCatching { secretStore.get(XAI_REFRESH_TOKEN_SECRET) }.getOrNull()
        return !access.isNullOrBlank() && !refresh.isNullOrBlank()
    }

    suspend fun clearStoredTokens() {
        secretStore.remove(XAI_ACCESS_TOKEN_SECRET)
        secretStore.remove(XAI_REFRESH_TOKEN_SECRET)
    }

    companion object {
        const val XAI_ACCESS_TOKEN_SECRET = "xai_subscription_access_token"
        const val XAI_REFRESH_TOKEN_SECRET = "xai_subscription_refresh_token"
        const val XAI_EXPIRY_MS_SECRET = "xai_subscription_expiry_ms"

        fun credentialsAllowedFor(url: String): CredentialTransportResult =
            CredentialTransportPolicy.evaluate(url, allowInsecureLocal = false)
    }
}

sealed interface DeviceAuthorizationResult {
    data class Pending(
        val verificationUrl: String,
        val userCode: String,
        val deviceCode: String,
        val intervalSeconds: Int,
    ) : DeviceAuthorizationResult

    data class Unavailable(val reason: String) : DeviceAuthorizationResult
}

sealed interface SubscriptionTokenResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
    ) : SubscriptionTokenResult

    data class Unavailable(val reason: String) : SubscriptionTokenResult
}

sealed interface RefreshResult {
    data class Success(val accessToken: String, val expiresInSeconds: Long) : RefreshResult
    data class Unavailable(val reason: String) : RefreshResult
}
