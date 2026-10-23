package com.halo.sdk.provider

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class GuardedRemoteClientTest {
    @Test fun httpErrorResponsesNeverProduceCompletedReceipts() {
        MockWebServer().use { server ->
            val policy = Policy(); policy.change(true)
            val client = OkHttpClient().withProviderDispatchPolicy(policy, "openai")
            for (code in listOf(401, 403, 429, 500)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("fixture"))
                client.newCall(Request.Builder().url(server.url("/error")).build()).execute().use { it.body!!.string() }
                assertEquals(RemoteDispatchOutcome.FAILED, policy.outcomes.last())
            }
        }
    }
    @Test fun privateRejectsBeforeDnsConnectOrRequestBytesForEveryOwnerClass() {
        MockWebServer().use { server ->
            val policy = Policy()
            for (provider in listOf("openai", "anthropic", "gemini", "openrouter", "groq", "cerebras", "xai",
                "opencode.zen", "companion.opencode", "custom.openaicompatible", "provider.ollama", "provider.elevenlabs.tts",
                "web.public", "mcp.configured", "google.mail")) {
                val client = OkHttpClient().withProviderDispatchPolicy(policy, provider)
                val call = client.newCall(Request.Builder().url(server.url("/synthetic")).build())
                try { call.execute().close(); fail("Private must block $provider") } catch (_: IOException) { }
            }
            assertEquals(0, server.requestCount)
            assertEquals(15, policy.blocked)
        }
    }

    @Test fun queuedCallCannotBorrowAuthorityAfterPrivatePowerPrivatePower() {
        MockWebServer().use { server ->
            val policy = Policy(); policy.change(true)
            val client = OkHttpClient().withProviderDispatchPolicy(policy, "openai")
            val stale = client.newCall(Request.Builder().url(server.url("/stale")).build())
            policy.change(false); policy.change(true)
            try { stale.execute().close(); fail() } catch (_: IOException) { }
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("synthetic"))
            client.newCall(Request.Builder().url(server.url("/current")).build()).execute().use {
                assertEquals("synthetic", it.body!!.string())
            }
            assertEquals(1, server.requestCount)
            assertEquals(RemoteDispatchOutcome.REPLIED, policy.outcomes.last())
        }
    }

    @Test fun leaseSurvivesResponseHeadersAndCancelsAnActiveBody() {
        MockWebServer().use { server ->
            val policy = Policy(); policy.change(true)
            server.enqueue(MockResponse().setBody("a".repeat(10000)).throttleBody(1, 1, TimeUnit.SECONDS))
            val client = OkHttpClient().withProviderDispatchPolicy(policy, "openai")
            val call = client.newCall(Request.Builder().url(server.url("/stream")).build())
            call.execute().use { response ->
                assertTrue(policy.outcomes.isEmpty())
                assertEquals(1, policy.active.size)
                policy.change(false)
                assertTrue(call.isCanceled())
                try { response.body!!.string(); fail("Body must stop") } catch (_: IOException) { }
            }
            assertTrue(policy.active.isEmpty())
            assertEquals(RemoteDispatchOutcome.CANCELLED, policy.outcomes.single())
        }
    }

    @Test fun redirectsDoNotDelegateToAnUndisclosedDestination() {
        MockWebServer().use { first -> MockWebServer().use { other ->
            val policy = Policy(); policy.change(true)
            first.enqueue(MockResponse().setResponseCode(302).addHeader("Location", other.url("/not-authorized")))
            val client = OkHttpClient().withProviderDispatchPolicy(policy, "openai")
            client.newCall(Request.Builder().url(first.url("/start")).build()).execute().use { assertEquals(302, it.code) }
            assertEquals(1, first.requestCount)
            assertEquals(0, other.requestCount)
        } }
    }

    @Test fun inheritedIntentCannotAuthorizeAnotherProvider() {
        MockWebServer().use { server ->
            val policy = Policy(); policy.change(true)
            val client = OkHttpClient().withProviderDispatchPolicy(policy, "anthropic")
            RemoteDispatchContext.intent.set(RemoteDispatchIntent(RemoteDispatchRequest("openai", RemoteOperation.INFERENCE,
                setOf(RemoteDataCategory.TEXT)), policy.revision))
            val call = try { client.newCall(Request.Builder().url(server.url("/mismatch")).build()) }
                finally { RemoteDispatchContext.intent.remove() }
            try { call.execute().close(); fail() } catch (_: IOException) { }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun typedProviderHelpersCannotReplaceAnInheritedProvider() {
        val policy = Policy()
        val storage = object : com.halo.sdk.runtime.Storage, RemoteDispatchPolicySource {
            override val remoteDispatchPolicy: RemoteDispatchPolicy = policy
            override fun getString(key: String, defaultValue: String?) = defaultValue
            override fun putString(key: String, value: String?) {}
            override fun getInt(key: String, defaultValue: Int) = defaultValue
            override fun putInt(key: String, value: Int) {}
            override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
            override fun putBoolean(key: String, value: Boolean) {}
            override fun remove(key: String) {}
            override fun clear() {}
        }
        RemoteDispatchContext.intent.set(RemoteDispatchIntent(RemoteDispatchRequest("openai",
            RemoteOperation.INFERENCE, setOf(RemoteDataCategory.WORKSPACE)), policy.revision))
        try {
            try { storage.remoteAiContext("anthropic", AiRequest(text = "synthetic"), AiContext.EMPTY); fail() }
            catch (_: RemoteDispatchBlocked) { }
            try { storage.remoteSpeechContext("provider.elevenlabs.tts"); fail() }
            catch (_: RemoteDispatchBlocked) { }
        } finally { RemoteDispatchContext.intent.remove() }
    }

    @Test fun derivedWorkspaceToolArgumentsCannotBeRelabeledAsPlainWebText() {
        MockWebServer().use { server ->
            val policy = Policy().apply { change(true); allowedCategories = setOf(RemoteDataCategory.TEXT) }
            val client = OkHttpClient().withRemoteDispatchPolicy(policy) {
                RemoteDispatchRequest("web.public", RemoteOperation.WEB, setOf(RemoteDataCategory.TEXT)) }
            RemoteDispatchContext.provenance.set(RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), policy.revision))
            val call = try { client.newCall(Request.Builder().url(server.url("/synthetic-derived-query")).build()) }
                finally { RemoteDispatchContext.provenance.remove() }
            try { call.execute().close(); fail("Workspace provenance must survive tool argument generation") } catch (_: IOException) { }
            assertEquals(0, server.requestCount)
            assertEquals(1, policy.blocked)
        }
    }

    @Test fun oldToolProvenanceCannotBorrowANewerTypedProviderIntent() {
        MockWebServer().use { server ->
            val policy = Policy().apply { change(true) }
            val old = policy.revision
            policy.change(false); policy.change(true)
            val client = OkHttpClient().withProviderDispatchPolicy(policy, "openai")
            RemoteDispatchContext.provenance.set(RemoteDispatchProvenance(setOf(RemoteDataCategory.TEXT), old))
            RemoteDispatchContext.intent.set(RemoteDispatchIntent(RemoteDispatchRequest("openai", RemoteOperation.INFERENCE,
                setOf(RemoteDataCategory.TEXT)), policy.revision))
            val call = try { client.newCall(Request.Builder().url(server.url("/synthetic-stale-tool")).build()) }
                finally { RemoteDispatchContext.provenance.remove(); RemoteDispatchContext.intent.remove() }
            try { call.execute().close(); fail("A new provider intent cannot upgrade an old tool's authority") } catch (_: IOException) { }
            assertEquals(0, server.requestCount)
        }
    }

    private class Policy : RemoteDispatchPolicy {
        @Volatile override var revision = 0L
        var allowed = false
        var allowedCategories = RemoteDataCategory.entries.toSet()
        var blocked = 0
        val active = mutableSetOf<Lease>()
        val outcomes = mutableListOf<RemoteDispatchOutcome>()
        @Synchronized fun change(allow: Boolean) {
            revision++; allowed = allow
            active.toList().forEach { it.finish(RemoteDispatchOutcome.CANCELLED); it.cancel() }
        }
        @Synchronized override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long,
            recordActivity: Boolean, cancel: () -> Unit): RemoteDispatchLease {
            if (!allowed || revision != expectedRevision || !allowedCategories.containsAll(request.categories)) { blocked++; throw RemoteDispatchBlocked() }
            return Lease(revision, cancel).also(active::add)
        }
        inner class Lease(val version: Long, val cancel: () -> Unit) : RemoteDispatchLease {
            var finished = false
            override fun requireCurrent() = synchronized(this@Policy) {
                if (finished || !allowed || revision != version) throw RemoteDispatchBlocked()
            }
            override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) = synchronized(this@Policy) {
                if (!finished) { finished = true; active.remove(this); outcomes.add(outcome) }; Unit
            }
        }
    }
}
