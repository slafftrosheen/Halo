package com.halo.providers.openai

import com.halo.sdk.provider.ProviderSecretStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.math.BigInteger
import java.security.*
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatGptAccountState(val connected: Boolean = false, val subscriptionAccess: Boolean = false,
    val displayName: String? = null, val revision: String = UUID.randomUUID().toString())

/** Content-free diagnostics only. Never include tokens, callback URLs, claims or provider bodies. */
enum class ChatGptConnectionStage { REGISTERED, TOKEN_EXCHANGE, TOKEN_RECEIVED, VERIFY_IDENTITY,
    IDENTITY_VERIFIED, CONNECTED, DNS_FAILED, NETWORK_FAILED, HTTP_REJECTED, POLICY_BLOCKED }

enum class ChatGptSignInFailure { BROWSER_DENIED, REGISTRATION_INCOMPLETE, POLICY_BLOCKED, DNS, NETWORK, SERVER_REJECTED }
class ChatGptSignInException(val failure: ChatGptSignInFailure) : IllegalStateException(failure.name)

/** OpenAI's public Sign in with ChatGPT token-sharing flow, not an API-key substitute.
 * Credentials and refresh checkpoints are stored only in the host's protected secret store.
 * Contract: openai/sign-in-with-chatgpt-devkit, oauth.ts at 0a36fefeb913055c8c7a1a29b63d82b96b2e841a. */
class ChatGptAccount(
    private val secrets: ProviderSecretStore,
    private val client: OkHttpClient,
    private val issuer: String = "https://auth.openai.com",
    private val diagnostic: (ChatGptConnectionStage) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ChatGptAccountState())
    val state = mutableState.asStateFlow()
    private var saved: JSONObject? = null
    private var loaded = false
    private var pending: Attempt? = null
    private var discovery: JSONObject? = null
    private data class Attempt(val redirect: String, val state: String, val nonce: String, val verifier: String, val clientId: String?)

    suspend fun load() = mutex.withLock { loadLocked() }
    private suspend fun loadLocked() {
        if (loaded) return
        saved = secrets.get(STORE)?.let { require(it.length <= 131072); JSONObject(it).also { json -> require(json.getInt("schema") == 1) } }
        loaded = true
        publish()
    }
    private suspend fun persist(value: JSONObject) {
        secrets.put(STORE, value.toString())
        saved = JSONObject(value.toString())
        publish()
    }
    private fun publish() {
        val record = saved
        mutableState.value = ChatGptAccountState(connected = !record?.optString("subject").isNullOrBlank(),
            subscriptionAccess = record?.optJSONObject("tokens")?.let { tokens -> scopes(tokens).containsAll(REQUIRED) } == true,
            displayName = record?.optString("name")?.takeIf { it.isNotBlank() }, revision = UUID.randomUUID().toString())
    }
    /** Host must bind this exact loopback address before opening the returned browser URL. */
    suspend fun begin(redirectUri: String): String = mutex.withLock {
        loadLocked()
        val redirect = redirectUri.toHttpUrl()
        require(redirect.scheme == "http" && redirect.host == "127.0.0.1" && redirect.port in 1024..65535 &&
            redirect.encodedPath == "/auth/callback" && redirect.query == null && redirect.fragment == null &&
            redirect.username.isEmpty() && redirect.password.isEmpty())
        val config = discover()
        val record = saved?.let { JSONObject(it.toString()) } ?: JSONObject().put("schema", 1)
        val host = record.optString("hostId").ifBlank { "urn:uuid:${UUID.randomUUID()}" }
        record.put("hostId", host)
        persist(record)
        val attempt = Attempt(redirectUri, random(), random(), random(), record.optString("clientId").takeIf { it.isNotBlank() })
        pending = attempt
        config.getString("authorization_endpoint").toHttpUrl().newBuilder()
            .addQueryParameter("client_id", attempt.clientId ?: "dynamic_agent_client")
            .addQueryParameter("response_type", "code").addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("scope", SCOPES).addQueryParameter("resource", RESOURCE)
            .addQueryParameter("state", attempt.state).addQueryParameter("nonce", attempt.nonce)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("code_challenge", encode(MessageDigest.getInstance("SHA-256").digest(attempt.verifier.toByteArray(Charsets.US_ASCII))))
            .addQueryParameter("ext_agent_host_id", host)
            .apply { if (attempt.clientId == null) addQueryParameter("agent_name_hint", "Halo") }
            .build().toString()
    }
    /** Unrelated/malformed loopback requests do not consume the pending transaction. */
    suspend fun acceptsCallback(url: String): Boolean = mutex.withLock {
        val attempt = pending ?: return@withLock false
        val uri = runCatching { url.toHttpUrl() }.getOrNull() ?: return@withLock false
        val target = attempt.redirect.toHttpUrl()
        uri.scheme == target.scheme && uri.host == target.host && uri.port == target.port && uri.encodedPath == target.encodedPath &&
            uri.queryParameterValues("state").size == 1 &&
            MessageDigest.isEqual(uri.queryParameter("state").orEmpty().toByteArray(), attempt.state.toByteArray())
    }
    suspend fun complete(url: String) = mutex.withLock {
        val attempt = pending ?: error("Start ChatGPT sign-in again")
        val uri = url.toHttpUrl()
        val target = attempt.redirect.toHttpUrl()
        require(uri.scheme == target.scheme && uri.host == target.host && uri.port == target.port && uri.encodedPath == target.encodedPath &&
            uri.queryParameterValues("state").size == 1 && MessageDigest.isEqual(
                uri.queryParameter("state").orEmpty().toByteArray(), attempt.state.toByteArray())) { "ChatGPT sign-in state did not match" }
        pending = null
        if (uri.queryParameter("error") != null) throw ChatGptSignInException(ChatGptSignInFailure.BROWSER_DENIED)
        val code = uri.queryParameter("code")
        require(!code.isNullOrBlank() && code.length <= 8192 && uri.queryParameterValues("code").size == 1)
        val returnedClient = uri.queryParameter("client_id")
        val clientId = returnedClient ?: attempt.clientId
        if (uri.queryParameterValues("client_id").size > 1 || clientId == null ||
            !clientId.matches(Regex("[A-Za-z0-9_-]{1,200}")) || clientId == "dynamic_agent_client" ||
            (attempt.clientId != null && returnedClient != null && returnedClient != attempt.clientId)
        ) throw ChatGptSignInException(ChatGptSignInFailure.REGISTRATION_INCOMPLETE)
        // Registration survives an expired code; retries must not create another application.
        val record = JSONObject(requireNotNull(saved).toString()).put("clientId", clientId)
        persist(record)
        diagnostic(ChatGptConnectionStage.REGISTERED)
        diagnostic(ChatGptConnectionStage.TOKEN_EXCHANGE)
        val response = tokenRequest(FormBody.Builder().add("grant_type", "authorization_code").add("client_id", clientId)
            .add("code", code).add("code_verifier", attempt.verifier).add("redirect_uri", attempt.redirect).add("resource", RESOURCE).build())
        val receivedAt = now()
        diagnostic(ChatGptConnectionStage.TOKEN_RECEIVED)
        val tokens = signInTokens(response, receivedAt)
        val idToken = response.getString("id_token")
        diagnostic(ChatGptConnectionStage.VERIFY_IDENTITY)
        val identity = verifyIdentity(idToken, clientId, attempt.nonce, receivedAt)
        diagnostic(ChatGptConnectionStage.IDENTITY_VERIFIED)
        val oldSubject = record.optString("subject").takeIf { it.isNotBlank() }
        check(oldSubject == null || oldSubject == identity.getString("sub")) { "Disconnect the previous ChatGPT account before changing accounts" }
        record.remove("pendingRefresh")
        record.put("subject", identity.getString("sub")).put("name", identity.optString("name").take(200))
        if (tokens == null) record.remove("tokens") else record.put("tokens", tokens)
        persist(record)
        diagnostic(ChatGptConnectionStage.CONNECTED)
    }
    suspend fun cancelSignIn() = mutex.withLock { pending = null }

    suspend fun accessToken(): String = mutex.withLock {
        loadLocked()
        val record = saved?.let { JSONObject(it.toString()) } ?: error("Sign in with ChatGPT first")
        check(!record.optString("subject").isBlank()) { "Sign in with ChatGPT first" }
        var tokens = record.optJSONObject("tokens") ?: error("This ChatGPT connection has not granted subscription access")
        check(scopes(tokens).containsAll(REQUIRED)) { "This ChatGPT connection has not granted subscription access" }
        if (record.has("pendingRefresh") || tokens.getLong("expiresAt") <= now() + 60000) {
            val clientId = record.getString("clientId")
            var checkpoint = record.optJSONObject("pendingRefresh")
            if (checkpoint == null) {
                val refresh = tokens.optString("refreshToken").takeIf { it.isNotBlank() } ?: error("ChatGPT connection expired. Sign in again")
                val response = tokenRequest(FormBody.Builder().add("grant_type", "refresh_token").add("client_id", clientId)
                    .add("refresh_token", refresh).add("resource", RESOURCE).build())
                val receivedAt = now()
                tokens = tokenFields(response, tokens.getString("scope"), receivedAt, requireRefresh = true)
                if (response.has("id_token")) {
                    val idToken = response.getString("id_token").also { require(it.isNotBlank()) }
                    checkpoint = JSONObject().put("tokens", tokens).put("idToken", idToken).put("receivedAt", receivedAt)
                    record.put("pendingRefresh", checkpoint)
                    persist(record) // Preserve rotated refresh token if key retrieval fails or process dies.
                }
            }
            if (checkpoint != null) {
                val identity = verifyIdentity(checkpoint.getString("idToken"), clientId, null, checkpoint.getLong("receivedAt"))
                check(identity.getString("sub") == record.getString("subject")) { "ChatGPT account changed. Sign in again" }
                tokens = checkpoint.getJSONObject("tokens")
                record.put("name", identity.optString("name").take(200))
            }
            record.put("tokens", tokens); record.remove("pendingRefresh"); persist(record)
        }
        check(scopes(tokens).containsAll(REQUIRED) && tokens.getLong("expiresAt") > now()) { "ChatGPT subscription access is unavailable" }
        tokens.getString("accessToken")
    }
    /** Returns false if local logout succeeded but OpenAI revocation could not be confirmed. */
    suspend fun disconnect(expectedRevision: String? = null, revokeRemote: Boolean = true): Boolean = mutex.withLock {
        loadLocked()
        check(expectedRevision == null || state.value.revision == expectedRevision) { "ChatGPT account changed" }
        pending = null
        val previous = saved ?: return@withLock true
        val retained = JSONObject().put("schema", 1)
        listOf("clientId", "hostId").forEach { key -> previous.optString(key).takeIf { it.isNotBlank() }?.let { retained.put(key, it) } }
        persist(retained)
        val refresh = (previous.optJSONObject("pendingRefresh")?.optJSONObject("tokens") ?: previous.optJSONObject("tokens"))?.optString("refreshToken")
        if (refresh.isNullOrBlank()) return@withLock true
        if (!revokeRemote) return@withLock false
        try {
            val endpoint = discover().optString("revocation_endpoint").takeIf { it.isNotBlank() } ?: return@withLock false
            fetch(Request.Builder().url(endpoint).post(FormBody.Builder().add("token", refresh)
                .add("token_type_hint", "refresh_token").add("client_id", previous.getString("clientId")).build()).build(), emptyOk = true)
            true
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    }
    // A successful identity-only sign-in can omit access credentials. If ChatGPT
    // says it granted offline access or token sharing, credentials are mandatory.
    private fun signInTokens(json: JSONObject, at: Long): JSONObject? {
        val scope = json.getString("scope").also { require(it.length <= 8192) }
        val granted = scope.split(Regex("\\s+")).toSet()
        val access = json.opt("access_token")
        if (access is String && access.isNotEmpty()) return tokenFields(json, scope, at)
        require(access == null || access == JSONObject.NULL || access == "") { "ChatGPT returned invalid credentials" }
        require("offline_access" !in granted && "chatgpt.tokens.use.direct" !in granted) {
            "ChatGPT did not return the granted access credentials"
        }
        return null
    }
    private fun tokenFields(json: JSONObject, previousScope: String?, at: Long, requireRefresh: Boolean = false): JSONObject {
        val scope = if (json.has("scope")) json.getString("scope") else requireNotNull(previousScope) { "ChatGPT did not confirm permissions" }
        require(scope.length <= 8192)
        val expires = (json.get("expires_in") as? Number)?.toDouble() ?: error("ChatGPT returned incomplete credentials")
        require(expires.isFinite() && expires > 0 && expires <= 365 * 24 * 3600)
        require(json.getString("token_type").equals("bearer", true))
        val access = json.getString("access_token").also { require(it.length in 1..32768 && it.none(Char::isISOControl)) }
        val refresh = json.optString("refresh_token").also { require(it.length <= 32768) }
        require(!(requireRefresh || "offline_access" in scope.split(' ')) || refresh.isNotBlank())
        return JSONObject().put("accessToken", access).put("refreshToken", refresh).put("scope", scope).put("expiresAt", at + (expires * 1000).toLong())
    }
    private fun scopes(tokens: JSONObject) = tokens.optString("scope").split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
    private suspend fun discover(): JSONObject {
        discovery?.let { return it }
        val json = JSONObject(fetch(Request.Builder().url("$issuer/.well-known/openid-configuration").build()))
        require(json.getString("issuer") == issuer)
        val origin = issuer.toHttpUrl()
        for (key in listOf("authorization_endpoint", "token_endpoint", "jwks_uri", "revocation_endpoint")) {
            if (key == "revocation_endpoint" && !json.has(key)) continue
            val endpoint = json.getString(key).toHttpUrl()
            require(endpoint.scheme == origin.scheme && endpoint.host == origin.host && endpoint.port == origin.port &&
                endpoint.username.isEmpty() && endpoint.password.isEmpty() && endpoint.fragment == null) { "Unverified ChatGPT sign-in endpoint" }
        }
        discovery = json
        return json
    }
    private suspend fun tokenRequest(body: FormBody): JSONObject = JSONObject(fetch(Request.Builder()
        .url(discover().getString("token_endpoint")).post(body).build()))
    private suspend fun verifyIdentity(token: String, clientId: String, nonce: String?, receivedAt: Long): JSONObject {
        require(token.length <= 32768)
        val parts = token.split('.').also { require(it.size == 3) }
        val header = JSONObject(decode(parts[0]).toString(Charsets.UTF_8))
        require(header.getString("alg") == "RS256" && !header.has("crit"))
        val keys = JSONObject(fetch(Request.Builder().url(discover().getString("jwks_uri")).build())).getJSONArray("keys")
        require(keys.length() in 1..100)
        val matching = (0 until keys.length()).map { keys.getJSONObject(it) }.filter {
            it.optString("kid") == header.getString("kid") && it.optString("kty") == "RSA" &&
                it.optString("alg", "RS256") == "RS256" && it.optString("use", "sig") == "sig"
        }
        val key = matching.single()
        val modulus = BigInteger(1, decode(key.getString("n")))
        require(modulus.bitLength() in 2048..8192)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, BigInteger(1, decode(key.getString("e")))))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey); verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        check(verifier.verify(decode(parts[2]))) { "ChatGPT identity signature could not be verified" }
        val claims = JSONObject(decode(parts[1]).toString(Charsets.UTF_8))
        val audience = when (val aud = claims.get("aud")) {
            is String -> listOf(aud)
            is org.json.JSONArray -> (0 until aud.length()).map { aud.getString(it) }
            else -> error("Invalid identity audience")
        }
        val seconds = receivedAt / 1000
        require(claims.getString("iss") == issuer && clientId in audience && claims.getString("sub").isNotBlank())
        require((claims.get("exp") as? Number)?.toLong()?.let { it > seconds - 5 } == true)
        require((claims.get("iat") as? Number)?.toLong()?.let { it <= seconds + 5 && it > 0 } == true)
        if (claims.has("nbf")) require(claims.getLong("nbf") <= seconds + 5)
        require(nonce == null || claims.optString("nonce") == nonce)
        require(!claims.has("azp") || claims.getString("azp") == clientId)
        require(audience.size <= 1 || claims.optString("azp") == clientId)
        return claims
    }
    private suspend fun fetch(request: Request, emptyOk: Boolean = false): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (continuation.isActive) {
                    val (stage, failure) = when (e) {
                        is com.halo.sdk.provider.RemoteDispatchBlocked ->
                            ChatGptConnectionStage.POLICY_BLOCKED to ChatGptSignInFailure.POLICY_BLOCKED
                        is java.net.UnknownHostException -> ChatGptConnectionStage.DNS_FAILED to ChatGptSignInFailure.DNS
                        else -> ChatGptConnectionStage.NETWORK_FAILED to ChatGptSignInFailure.NETWORK
                    }
                    diagnostic(stage)
                    continuation.resumeWithException(ChatGptSignInException(failure))
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (!it.isSuccessful) {
                        diagnostic(ChatGptConnectionStage.HTTP_REJECTED)
                        throw ChatGptSignInException(ChatGptSignInFailure.SERVER_REJECTED)
                    }
                    val body = it.body ?: error("ChatGPT returned no response")
                    require(body.contentLength() <= 1024 * 1024)
                    val bytes = body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        val output = java.io.ByteArrayOutputStream()
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 1024 * 1024 && (emptyOk || bytes.isNotEmpty()))
                    bytes.toString(Charsets.UTF_8)
                } }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
    companion object {
        private const val STORE = "chatgpt_subscription_account_v1"
        const val RESOURCE = "https://api.openai.com/v1"
        const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
        private val REQUIRED = setOf("resource.invoke", "chatgpt.tokens.use.direct")
        private fun random() = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
        private fun encode(value: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
        private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
    }
}
