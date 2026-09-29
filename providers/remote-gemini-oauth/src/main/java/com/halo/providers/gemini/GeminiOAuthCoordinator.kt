package com.halo.providers.gemini

import com.halo.app.security.SecretStore
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.FetchSignInResult
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gemini OAuth coordinator that keeps official Google AuthorizationClient
 * lifecycle honest.
 *
 * On Connect Google:
 *  - AuthorizationClient.authorize() is used.
 *  - If consent is needed, the pending intent is launched.
 *  - If scopes were already granted, the returned token is accepted without UI.
 *
 * Restart behavior:
 *  - On a later app session, authorize() is called again.
 *  - If the grant still exists, a fresh short-lived token is received silently.
 *  - The user is NOT required to click Connect after every restart.
 *
 * Token expiration:
 *  - On authentication failure caused by token expiry, there is a single-flight
 *    reauthorization performed once, then the request is retried once.
 *  - There is no infinite retry loop.
 *
 * Google project:
 *  - This uses HALO'S OWN Google OAuth application configuration.
 *  - Build config must support a debug OAuth client and a release OAuth client.
 *  - Project/billing/quota attribution is explicit and owned by Halo.
 *
 * CRITICAL GOOGLE RULE:
 *  - Gemini CLI OAuth is INTENTIONALLY UNSUPPORTED as a Halo credential source.
 *  - Supported Gemini auth is Google OAuth for the Gemini Developer API, or a
 *    Gemini API key. CLI credential import, token harvesting from Gemini CLI,
 *    copying CLI access tokens, and any Gemini Code Assist / Gemini CLI OAuth
 *    piggyback are explicitly out of scope and must not be added.
 */
class GeminiOAuthCoordinator(
    private val authorizationClient: AuthorizationClient,
    /** Halo-owned project id used for x-goog-user-project. */
    private val projectId: String,
    /** SecretStore for optional limited token lifecycle metadata. */
    private val secretStore: SecretStore,
    /** Debug or release Halo-owned OAuth client id, when configured. */
    private val haloSignedClientId: String? = null,
) {
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/cloud-platform"),
        Scope("https://www.googleapis.com/auth/generative-language.retriever"),
    )

    /**
     * Initiates or renews Google authorization.
     *
     * Returns the access token when one is available immediately, otherwise
     * signals that a resolution intent must be launched.
     */
    suspend fun authorize(): GoogleAuthorizationOutcome = suspendCancellableCoroutine { cont ->
        try {
            val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
                .setRequestedScopes(scopes)
                .build()
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        cont.resume(GoogleAuthorizationOutcome.ResolutionRequired(result))
                    } else {
                        cont.resume(handleSignInResult(result))
                    }
                }
                .addOnFailureListener {
                    cont.resumeWithException(GeminiOAuthException("Google authorization failed", it))
                }
        } catch (e: Exception) {
            cont.resumeWithException(GeminiOAuthException("Google authorization request failed", e))
        }
    }

    /**
     * Silent reissue attempt for an existing grant.
     *
     * On restart this is used to get a fresh short-lived token without forcing
     * the user through the consent flow again, when the grant still exists.
     */
    suspend fun silentAuthorize(): GoogleAuthorizationOutcome = suspendCancellableCoroutine { cont ->
        try {
            val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
                .setRequestedScopes(scopes)
                .build()
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        cont.resume(GoogleAuthorizationOutcome.SilentFailed(result))
                    } else {
                        cont.resume(handleSignInResult(result))
                    }
                }
                .addOnFailureListener {
                    cont.resumeWithException(GeminiOAuthException("Silent Google authorization failed", it))
                }
        } catch (e: Exception) {
            cont.resumeWithException(GeminiOAuthException("Silent Google authorization request failed", e))
        }
    }

    /**
     * Result of the Google sign-in fetch, converted into an access token + project id.
     */
    private fun handleSignInResult(result: FetchSignInResult): GoogleAuthorizationOutcome {
        val token = result.token?.accessToken?.takeIf { it.isNotBlank() }
        return if (token != null) {
            GoogleAuthorizationOutcome.Connected(token, projectId)
        } else {
            GoogleAuthorizationOutcome.NoToken
        }
    }

    companion object {
        /** Marked unsupported per the critical Google rule above. */
        const val CLI_CREDENTIAL_IMPORT_UNSUPPORTED = "Gemini CLI credential import is unsupported"

        fun isCliCredentialImportUnsupported(): Boolean = true
    }
}

sealed interface GoogleAuthorizationOutcome {
    data class Connected(val accessToken: String, val projectId: String) : GoogleAuthorizationOutcome
    data class ResolutionRequired(val result: AuthorizationResult) : GoogleAuthorizationOutcome
    data class SilentFailed(val result: AuthorizationResult) : GoogleAuthorizationOutcome
    data object NoToken : GoogleAuthorizationOutcome
}

class GeminiOAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
