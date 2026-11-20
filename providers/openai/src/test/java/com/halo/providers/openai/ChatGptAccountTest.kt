package com.halo.providers.openai

import com.halo.sdk.provider.ProviderSecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatGptAccountTest {
    private class Secrets : ProviderSecretStore {
        var value: String? = null
        override suspend fun get(key: String) = value
        override suspend fun put(key: String, value: String?) { this.value = value }
        override suspend fun remove(key: String) { value = null }
    }
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        val secrets = Secrets()
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        var clock = 1_800_000_000_000L
        var nonce = ""
        var identityNonce: String? = null
        var scope = ChatGptAccount.SCOPES
        var unavailableKeys = false
        var wrongIssuer = false
        var refreshes = 0
        val bodies = mutableListOf<String>()
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(5, TimeUnit.SECONDS).build()
        fun owner() = ChatGptAccount(secrets, client, server.url("/").toString().trimEnd('/')) { clock }
        val account = owner()
        init {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path) {
                        "/.well-known/openid-configuration" -> MockResponse().setBody(JSONObject()
                            .put("issuer", server.url("/").toString().trimEnd('/'))
                            .put("authorization_endpoint", server.url("/authorize"))
                            .put("token_endpoint", server.url("/token"))
                            .put("jwks_uri", server.url("/keys"))
                            .put("revocation_endpoint", server.url("/revoke")).toString())
                        "/keys" -> if (unavailableKeys) MockResponse().setResponseCode(503) else {
                            val key = pair.public as RSAPublicKey
                            MockResponse().setBody(JSONObject().put("keys", JSONArray().put(JSONObject()
                                .put("kid", "fixture").put("kty", "RSA").put("alg", "RS256")
                                .put("n", encode(key.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()))
                                .put("e", encode(key.publicExponent.toByteArray())))).toString())
                        }
                        "/token" -> {
                            val body = request.body.readUtf8(); bodies += body
                            val refresh = body.contains("grant_type=refresh_token")
                            if (refresh) refreshes++
                            MockResponse().setBody(JSONObject().put("access_token", if (refresh) "rotated-access" else "fixture-access")
                                .put("refresh_token", if (refresh) "rotated-refresh" else "fixture-refresh")
                                .put("token_type", "Bearer").put("expires_in", 3600)
                                .put("scope", scope).put("id_token", jwt(if (refresh) null else identityNonce ?: nonce)).toString())
                        }
                        "/revoke" -> { bodies += request.body.readUtf8(); MockResponse().setBody("") }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        }
        fun jwt(claimNonce: String?): String {
            val header = encode("""{"alg":"RS256","kid":"fixture"}""".toByteArray())
            val claims = JSONObject().put("iss", if (wrongIssuer) "https://wrong.example" else server.url("/").toString().trimEnd('/'))
                .put("aud", "issued_client").put("sub", "fixture-subject").put("name", "Test account")
                .put("exp", clock / 1000 + 300).put("iat", clock / 1000)
            claimNonce?.let { claims.put("nonce", it) }
            val input = "$header.${encode(claims.toString().toByteArray())}"
            val sig = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(input.toByteArray()) }.sign()
            return "$input.${encode(sig)}"
        }
        suspend fun authorization(): HttpUrl = account.begin("http://127.0.0.1:49201/auth/callback").toHttpUrl().also { nonce = it.queryParameter("nonce")!! }
        fun callback(auth: HttpUrl, state: String = auth.queryParameter("state")!!) = "http://127.0.0.1:49201/auth/callback".toHttpUrl()
            .newBuilder().addQueryParameter("state", state).addQueryParameter("code", "one-time-code")
            .addQueryParameter("client_id", "issued_client").build().toString()
        override fun close() { server.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun pkceVerifiedIdentityAndGrantedScopeAreRequiredForSubscriptionAccess() = runBlocking {
        Fixture().use { f ->
            val auth = f.authorization()
            assertEquals("dynamic_agent_client", auth.queryParameter("client_id"))
            assertEquals("S256", auth.queryParameter("code_challenge_method"))
            assertEquals(ChatGptAccount.RESOURCE, auth.queryParameter("resource"))
            assertFalse(f.account.acceptsCallback(f.callback(auth, "unrelated")))
            assertTrue(f.account.acceptsCallback(f.callback(auth)))
            f.account.complete(f.callback(auth))
            assertTrue(f.account.state.value.subscriptionAccess)
            assertEquals("fixture-access", f.account.accessToken())
            assertTrue(f.bodies.single().contains("code_verifier="))
            assertFalse(auth.toString().contains("fixture-access"))
            assertEquals("issued_client", f.authorization().queryParameter("client_id"))
        }
    }
    @Test fun mismatchedNonceNeverAuthorizesAndRegistrationSurvivesForRetry() = runBlocking {
        Fixture().use { f ->
            val auth = f.authorization(); f.identityNonce = "wrong"
            assertTrue(runCatching { f.account.complete(f.callback(auth)) }.isFailure)
            assertFalse(f.account.state.value.connected)
            assertTrue(runCatching { f.account.accessToken() }.isFailure)
            assertEquals("issued_client", f.authorization().queryParameter("client_id"))
        }
    }
    @Test fun identitySignInWithoutUsageScopeCannotSpendSubscriptionOrApiCredits() = runBlocking {
        Fixture().use { f ->
            f.scope = "openid profile offline_access"
            val auth = f.authorization(); f.account.complete(f.callback(auth))
            assertTrue(f.account.state.value.connected)
            assertFalse(f.account.state.value.subscriptionAccess)
            assertTrue(runCatching { f.account.accessToken() }.isFailure)
            assertEquals(0, f.refreshes)
        }
    }
    @Test fun refreshRotationIsRecoveredAfterKeyOutageAndRestartWithoutReusingConsumedGrant() = runBlocking {
        Fixture().use { f ->
            f.account.complete(f.callback(f.authorization()))
            f.clock += 3_600_000
            f.unavailableKeys = true
            assertTrue(runCatching { f.account.accessToken() }.isFailure)
            assertEquals(1, f.refreshes)
            assertTrue(f.secrets.value!!.contains("pendingRefresh"))
            f.unavailableKeys = false
            f.clock += 600_000 // Verification uses the checkpoint's receipt time, not the expired ID token's present time.
            val restarted = f.owner()
            assertEquals("rotated-access", restarted.accessToken())
            assertEquals(1, f.refreshes)
            assertFalse(f.secrets.value!!.contains("pendingRefresh"))
            assertTrue(restarted.disconnect())
            assertFalse(restarted.state.value.connected)
            assertFalse(f.secrets.value!!.contains("rotated-refresh"))
            assertTrue(f.bodies.last().contains("token=rotated-refresh"))
        }
    }
    companion object { private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) }
}
