package com.halo.sdk.provider

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import java.io.IOException
import okio.buffer

/**
 * Captures intent at newCall, authorizes again before DNS/connect, and retains the
 * revocable lease until the response body is consumed/closed (including SSE).
 * A queued call cannot acquire a newer mode/profile's authority after a switch.
 */
fun OkHttpClient.withRemoteDispatchPolicy(
    policy: RemoteDispatchPolicy,
    publicGetRedirects: Boolean = false,
    classify: (okhttp3.Request) -> RemoteDispatchRequest,
): OkHttpClient {
    class Pending(val revision: Long, val conflictingRevision: Boolean, val host: String, val intent: RemoteDispatchIntent?, val costs: RemoteCostObservation?, val provenance: RemoteDispatchProvenance?) {
        @Volatile var lease: RemoteDispatchLease? = null
        @Volatile var httpSuccessful = false
        @Volatile var revoked = false
    }
    val pending = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Call, Pending>())
    val previousFactory = eventListenerFactory
    return newBuilder()
        .apply { if (publicGetRedirects) {
            dns(PublicNetworkPolicy.dns(this@withRemoteDispatchPolicy.dns))
            proxy(java.net.Proxy.NO_PROXY)
            cookieJar(okhttp3.CookieJar.NO_COOKIES)
            authenticator(okhttp3.Authenticator.NONE)
            proxyAuthenticator(okhttp3.Authenticator.NONE)
            cache(null)
        } }
        // A configured destination cannot delegate authority to another host via redirect.
        .followRedirects(false).followSslRedirects(false)
        .eventListenerFactory { call ->
            val intent = RemoteDispatchContext.intent.get()
            val provenance = RemoteDispatchContext.provenance.get()
            val revisions = listOfNotNull(provenance?.revision, intent?.revision, call.request().tag(RemoteRequestRevision::class.java)?.value)
            pending[call] = Pending(revisions.firstOrNull() ?: policy.revision, revisions.distinct().size > 1,
                call.request().url.host, intent, RemoteDispatchContext.costs.get(), provenance)
            val previous = previousFactory.create(call)
            object : ForwardingRemoteEventListener(previous) {
                override fun callStart(call: Call) { previous.callStart(call) }
                override fun responseHeadersEnd(call: Call, response: okhttp3.Response) {
                    pending[call]?.httpSuccessful = response.isSuccessful
                    previous.responseHeadersEnd(call, response)
                }
                override fun callEnd(call: Call) {
                    pending.remove(call)?.let { state -> state.lease?.finish(if (state.httpSuccessful)
                        RemoteDispatchOutcome.REPLIED else RemoteDispatchOutcome.FAILED) }
                    previous.callEnd(call)
                }
                override fun callFailed(call: Call, ioe: IOException) {
                    pending.remove(call)?.lease?.finish(if (call.isCanceled())
                        RemoteDispatchOutcome.CANCELLED else RemoteDispatchOutcome.FAILED)
                    previous.callFailed(call, ioe)
                }
                override fun canceled(call: Call) {
                    pending.remove(call)?.lease?.finish(RemoteDispatchOutcome.CANCELLED)
                    previous.canceled(call)
                }
            }
        }
        .addInterceptor { chain ->
            val call = chain.call()
            val state = pending[call] ?: throw RemoteDispatchBlocked()
            if (state.conflictingRevision) throw RemoteDispatchBlocked()
            val classified = classify(chain.request())
            val intent = state.intent
            if (intent != null && intent.request.providerId != classified.providerId) throw RemoteDispatchBlocked()
            val request = (intent?.request ?: classified).let { it.copy(categories = it.categories + state.provenance?.categories.orEmpty()) }
            val lease = policy.acquire(request, state.revision, cancel = { state.revoked = true; call.cancel() })
            state.lease = lease
            state.costs?.bind(lease)
            try {
                lease.requireCurrent()
                if (!publicGetRedirects) chain.proceed(chain.request()) else {
                    var next = chain.request()
                    var redirects = 0
                    val visited = mutableSetOf<String>()
                    while (true) {
                        lease.requireCurrent()
                        if (next.method != "GET") throw IOException("Public acquisition supports GET only")
                        val url = PublicNetworkPolicy.validate(next.url.toString())
                        if (!visited.add(url.toString())) throw IOException("Public redirect loop")
                        next = next.newBuilder().url(url).build()
                        val response = chain.proceed(next)
                        if (response.code !in setOf(301, 302, 303, 307, 308)) {
                            state.httpSuccessful = response.isSuccessful || response.code == 304
                            val body = response.body ?: return@addInterceptor response
                            // Revocation during a blocking stream is policy recovery, not a lost
                            // connection. User Pause still cancels the call without setting revoked.
                            val guarded = object : okhttp3.ResponseBody() {
                                private val stream = object : okio.ForwardingSource(body.source()) {
                                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                                        if (state.revoked) throw RemoteDispatchBlocked()
                                        return try { super.read(sink, byteCount) }
                                        catch (error: IOException) { if (state.revoked) throw RemoteDispatchBlocked() else throw error }
                                    }
                                }.buffer()
                                override fun contentType() = body.contentType()
                                override fun contentLength() = body.contentLength()
                                override fun source() = stream
                            }
                            return@addInterceptor response.newBuilder().body(guarded).build()
                        }
                        val location = response.header("Location")
                        response.close()
                        if (++redirects > 5 || location == null) throw IOException("Public redirect limit or missing destination")
                        val target = url.resolve(location) ?: throw IOException("Invalid redirect destination")
                        PublicNetworkPolicy.validate(target.toString())
                        next = next.newBuilder().url(target).apply {
                            if (url.host != target.host) {
                                removeHeader("Authorization"); removeHeader("Cookie"); removeHeader("Proxy-Authorization")
                                removeHeader("If-None-Match"); removeHeader("If-Modified-Since"); removeHeader("Referer")
                            }
                        }.build()
                    }
                    @Suppress("UNREACHABLE_CODE") error("Unreachable redirect state")
                }
            } catch (failure: Exception) {
                lease.finish(if (call.isCanceled()) RemoteDispatchOutcome.CANCELLED else RemoteDispatchOutcome.FAILED)
                if (state.revoked) throw RemoteDispatchBlocked()
                if (publicGetRedirects && failure !is IOException) throw IOException("Public destination rejected", failure)
                throw failure
            }
        }
        .addNetworkInterceptor { chain ->
            val state = pending[chain.call()] ?: throw RemoteDispatchBlocked()
            if (publicGetRedirects) PublicNetworkPolicy.validate(chain.request().url.toString())
            else if (chain.request().url.host != state.host) throw RemoteDispatchBlocked()
            (state.lease ?: throw RemoteDispatchBlocked()).requireCurrent()
            chain.proceed(chain.request())
        }.build()
}

/** Public artifacts may follow bounded HTTPS CDN redirects while retaining the original lease. */
fun OkHttpClient.withModelDownloadPolicy(policy: RemoteDispatchPolicy): OkHttpClient =
    withRemoteDispatchPolicy(policy, publicGetRedirects = true) {
        RemoteDispatchRequest(MODEL_DOWNLOAD_OWNER, RemoteOperation.CONNECTED_SERVICE, setOf(RemoteDataCategory.ACCOUNT_METADATA))
    }

/** Conservative metadata for a provider client. Rich context categories are checked by its request owner. */
fun OkHttpClient.withProviderDispatchPolicy(policy: RemoteDispatchPolicy, providerId: String): OkHttpClient =
    withRemoteDispatchPolicy(policy) { request ->
        RemoteDispatchRequest(providerId,
            if (request.method == "GET") RemoteOperation.PROVIDER_CHECK else RemoteOperation.INFERENCE,
            setOf(if (request.method == "GET") RemoteDataCategory.ACCOUNT_METADATA else RemoteDataCategory.TEXT))
    }
