package com.halo.app.context

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.openrouter.OpenRouterProvider
import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Requires native Private selection; never changes profiles, credentials, canonical objects or working set. */
@RunWith(AndroidJUnit4::class)
class SelectedSourcePrivateDispatchPhysicalTest {
    @Test(timeout = 30000) fun exactSelectedPassageIsDeniedBeforeDnsOrRequestBytes() = runBlocking {
        fun phase(value: String) { android.util.Log.i("Issue57PrivateQA", value) }
        phase("read-mode")
        val graph = ManualDependencyGraph
        val policy = graph.privacyModes
        assertEquals("Select Private through the native control first", PrivacyMode.PRIVATE, policy.mode.value)
        phase("read-session")
        val session = requireNotNull(graph.studyUseCases.getSession("6dcb8501-d113-4e17-9d59-54f272df52be"))
        assertEquals("HALO-QA-20261029-SELECTED-PASSAGE", session.goal)
        val source = session.sources.single()
        assertEquals("e38b5c57-7f62-42e6-bd79-cda25c86b396", source.objectId)
        assertEquals("1", source.revision)
        val anchor = requireNotNull(source.textAnchor)
        assertEquals(0, anchor.start); assertEquals(6, anchor.end)
        phase("resolve-source")
        val resolved = graph.studyUseCases.sourceReferences(session).single()
        assertFalse(resolved.stale)
        assertEquals("Copper", resolved.excerpt)
        phase("check-permissions")
        assertTrue(graph.workspaceMediaAiPolicy.permitsObject(source.objectId, true))
        assertFalse(graph.workspaceMediaAiPolicy.permitsObject(source.objectId, false))

        val dns = AtomicInteger(); val connects = AtomicInteger(); val headers = AtomicInteger(); val bodyBytes = java.util.concurrent.atomic.AtomicLong()
        val observed = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            // Independent containment if the production gate regresses; a DNS attempt still fails the assertions.
            .dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> {
                dns.incrementAndGet(); throw UnknownHostException("QA containment: no remote transport allowed")
            } })
            .eventListener(object : EventListener() {
                override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { connects.incrementAndGet() }
                override fun requestHeadersStart(call: Call) { headers.incrementAndGet() }
                override fun requestBodyEnd(call: Call, byteCount: Long) { bodyBytes.addAndGet(byteCount) }
            }).callTimeout(5, TimeUnit.SECONDS).build()
        phase("create-observed-provider")
        val provider = OpenRouterProvider(graph.storage, observed.withProviderDispatchPolicy(graph.remoteDispatchPolicy, "openrouter"))
        assertTrue("Configured OpenRouter credential is required; it is never printed", provider.hasCredential())
        val before = policy.activity.receipts.value.map { it.id }.toSet()
        var rejected = false
        phase("forced-private-dispatch")
        val diagnostic = java.util.Timer(true)
        diagnostic.schedule(object : java.util.TimerTask() { override fun run() {
            Thread.getAllStackTraces().forEach { (thread, frames) ->
                if (frames.any { it.className.contains("PrivacyModeStore") || it.className.contains("ManualDependencyGraph") ||
                        it.className.contains("GuardedRemoteClient") || it.className.contains("OpenRouterProvider") }) {
                    android.util.Log.i("Issue57PrivateQA", "thread=${thread.name} state=${thread.state}\n" + frames.take(35).joinToString("\n"))
                }
            }
        } }, 10000)
        try {
            withTimeout(8000) { provider.generate(
                AiRequest(text = "HALO-QA-20261029 Private selected-source denial check", modelId = "openrouter/free"),
                AiContext(conversation = listOf(AIMessage(Role.USER, resolved.excerpt!!)),
                    remoteDataCategories = setOf(RemoteDataCategory.WORKSPACE))) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { rejected = true }
        finally { diagnostic.cancel() }
        assertTrue("The forced negative request must reject", rejected)
        phase("read-receipt")
        val receipt = policy.activity.receipts.value.filter { it.id !in before && it.providerId == "openrouter" &&
            it.operation == RemoteOperation.INFERENCE && RemoteDataCategory.WORKSPACE in it.categories }.single()
        assertEquals(RemoteDispatchOutcome.BLOCKED, receipt.outcome)
        assertEquals(0, dns.get()); assertEquals(0, connects.get()); assertEquals(0, headers.get()); assertEquals(0L, bodyBytes.get())
        assertEquals(PrivacyMode.PRIVATE, policy.mode.value)
        assertEquals(session, graph.studyUseCases.getSession(session.id))
        val result = JSONObject().put("result", "PASS").put("scope", "Actual saved selected passage, native Private profile, production OpenRouter provider/dispatch policy; observed no DNS, connection, headers or body bytes. Not a successful cloud/model/voice check.")
            .put("sessionId", session.id).put("sourceId", source.objectId).put("sourceRevision", source.revision)
            .put("blockId", anchor.blockId).put("start", anchor.start).put("end", anchor.end)
            .put("remotePermitted", false).put("receiptId", receipt.id).put("outcome", receipt.outcome.name)
            .put("dns", dns.get()).put("connects", connects.get()).put("requestHeaders", headers.get()).put("bodyBytes", bodyBytes.get())
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        File(app.filesDir, "issue57-selected-source-private-result.json").writeText(result.toString(2))
        phase("complete")
        println(result.toString())
    }
}
