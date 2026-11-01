package com.halo.sdk.provider

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

class PublicDownloadPolicyTest {
    @Test fun revokedResponseBodyRetainsPolicyReasonInsteadOfConnectionAdvice() {
        val policy = Policy()
        val client = OkHttpClient().withModelDownloadPolicy(policy).newBuilder().addInterceptor { response(it.request()) }.build()
        client.newCall(Request.Builder().url("https://example.org/model").build()).execute().use { result ->
            policy.switch()
            assertThrows(RemoteDispatchBlocked::class.java) { result.body!!.string() }
        }
    }
    @Test fun multiStageAcquisitionRetainsRevisionAcrossLocalWorkAndRejectsAReboundTag() = kotlinx.coroutines.runBlocking<Unit> {
        val policy = Policy(); val admitted = policy.revision; var sent = 0
        val client = OkHttpClient().withModelDownloadPolicy(policy).newBuilder().addInterceptor { sent++; response(it.request()) }.build()
        kotlinx.coroutines.withContext(modelAcquisitionContext(admitted)) {
            policy.switch(); policy.switch()
            assertEquals(admitted, policy.modelAcquisitionRevision())
            assertThrows(IOException::class.java) { client.newCall(Request.Builder().url("https://example.org/next-stage")
                .tag(RemoteRequestRevision::class.java, RemoteRequestRevision(policy.revision)).build()).execute() }
        }
        assertEquals(0, sent)
    }
    private class Policy : RemoteDispatchPolicy {
        override var revision = 1L
        var power = true
        val leases = mutableListOf<RemoteDispatchLease>()
        var cancel: (() -> Unit)? = null
        override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean,
            cancel: () -> Unit): RemoteDispatchLease {
            if (!power || revision != expectedRevision) throw RemoteDispatchBlocked()
            this.cancel = cancel
            return object : RemoteDispatchLease {
                var done = false
                override fun requireCurrent() { if (done || !power || revision != expectedRevision) throw RemoteDispatchBlocked() }
                override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) { done = true }
            }.also(leases::add)
        }
        fun switch() { power = !power; revision++; cancel?.invoke() }
    }
    private fun response(request: Request, code: Int = 200, target: String? = null) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body("fixture".toResponseBody()).apply { target?.let { header("Location", it) } }.build()

    @Test fun localAndMissingAuthorityRejectBeforeDnsAndTransport() {
        var dns = 0; var transport = 0
        for (policy in listOf(DenyRemoteDispatchPolicy, Policy().apply { power = false })) {
            val client = OkHttpClient.Builder().dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> {
                dns++; return listOf(InetAddress.getByName("8.8.8.8")) } }).build()
                .withModelDownloadPolicy(policy).newBuilder().addInterceptor { transport++; response(it.request()) }.build()
            assertThrows(IOException::class.java) { client.newCall(Request.Builder().url("https://example.org/model").build()).execute() }
        }
        assertEquals(0, dns); assertEquals(0, transport)
    }

    @Test fun cdnRedirectKeepsOneAuthorityAndDropsCredentials() {
        val policy = Policy(); val seen = mutableListOf<Request>()
        val client = OkHttpClient().withModelDownloadPolicy(policy).newBuilder().addInterceptor {
            seen += it.request()
            if (seen.size == 1) response(it.request(), 302, "https://cdn.example.org/model") else response(it.request())
        }.build()
        client.newCall(Request.Builder().url("https://example.org/start").header("Authorization", "fixture-secret")
            .header("Cookie", "fixture-cookie").header("Range", "bytes=3-").header("If-None-Match", "old").build()).execute().close()
        assertEquals(2, seen.size); assertEquals(1, policy.leases.size)
        assertNull(seen[1].header("Authorization")); assertNull(seen[1].header("Cookie"))
        assertNull(seen[1].header("If-None-Match")); assertEquals("bytes=3-", seen[1].header("Range"))
    }

    @Test fun privateRedirectsDowngradesLoopsAndTooManyHopsNeverReachTheirDestination() {
        for (target in listOf("https://127.0.0.1/secret", "https://[::1]/secret", "https://localhost./secret",
            "http://example.org/secret", "https://example.org:8443/secret", "https://example.org/start")) {
            var sent = 0
            val client = OkHttpClient().withModelDownloadPolicy(Policy()).newBuilder().addInterceptor {
                sent++; response(it.request(), 302, target)
            }.build()
            assertThrows(IOException::class.java) { client.newCall(Request.Builder().url("https://example.org/start").build()).execute() }
            assertEquals(target, 1, sent)
        }
        var sent = 0
        val loop = OkHttpClient().withModelDownloadPolicy(Policy()).newBuilder().addInterceptor {
            sent++; response(it.request(), 302, "https://example.org/hop$sent")
        }.build()
        assertThrows(IOException::class.java) { loop.newCall(Request.Builder().url("https://example.org/start").build()).execute() }
        assertEquals(6, sent)
    }

    @Test fun transitionDuringRedirectCannotAcquireNewPowerAuthority() {
        val policy = Policy(); var sent = 0
        val client = OkHttpClient().withModelDownloadPolicy(policy).newBuilder().addInterceptor {
            sent++; policy.switch(); policy.switch(); response(it.request(), 302, "https://cdn.example.org/model")
        }.build()
        assertThrows(IOException::class.java) { client.newCall(Request.Builder().url("https://example.org/start").build()).execute() }
        assertEquals(1, sent)
    }

    @Test fun restoredQueuedRevisionCannotBorrowNewMode() {
        val policy = Policy(); val old = policy.revision; policy.switch(); policy.switch()
        var sent = 0
        val client = OkHttpClient().withModelDownloadPolicy(policy).newBuilder().addInterceptor { sent++; response(it.request()) }.build()
        assertThrows(IOException::class.java) { client.newCall(Request.Builder().url("https://example.org/model")
            .tag(RemoteRequestRevision::class.java, RemoteRequestRevision(old)).build()).execute() }
        assertEquals(0, sent)
    }

    @Test fun publicDnsRejectsMixedPrivateAnswersWithoutASecondLookup() {
        var lookups = 0
        val dns = PublicNetworkPolicy.dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> {
            lookups++; return listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("192.168.1.10")) } })
        assertThrows(java.net.UnknownHostException::class.java) { dns.lookup("example.org") }
        assertEquals(1, lookups)
        assertFalse(PublicNetworkPolicy.publicAddress(InetAddress.getByName("100.64.0.1")))
        assertFalse(PublicNetworkPolicy.publicAddress(InetAddress.getByName("2001:db8::1")))
    }
}
