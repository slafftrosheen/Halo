package com.halo.app.capture

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ai.readRuntimeAiContextPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/** Public metadata only. Never logs credentials, response bodies, user media or network names. */
@RunWith(AndroidJUnit4::class)
class CloudImageTransportPhoneTest {
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun record(stage: String, fields: JSONObject.() -> Unit = {}) {
        val row = JSONObject().put("runId", requireNotNull(args.getString("runId")))
            .put("stage", stage).put("atMs", System.currentTimeMillis())
        row.fields()
        File(context.filesDir, "cloud-image-transport.jsonl").appendText("$row\n")
    }

    @Test fun privacyModeBoundaries() = runBlocking {
        require(args.getString("allowCloudDiagnostics") == "true")
        check(!Graph.kidsPreferences.getEnabled())
        val previous = Graph.privacyModes.mode.value
        try {
            val note = Graph.notebookUseCases.createNote("Synthetic privacy QA", "QA privacy mode ${args.getString("runId")}")
            Graph.workspaceMediaAiPolicy.setAccess(note.id, com.halo.app.data.workspace.WorkspaceAiAccess.EXCLUDE_FROM_AI)
            Graph.setPrivacyMode(com.halo.app.ai.PrivacyMode.CLOUD)
            assertTrue(Graph.storage.readRuntimeAiContextPolicy().allowCloudImages)
            assertTrue(Graph.storage.readRuntimeAiContextPolicy().allowCloudMemories)
            assertTrue(Graph.aiProviderRouter.paidMediaAllowed)
            assertTrue(Graph.workspaceMediaAiPolicy.permitsObject(note.id, false))
            Graph.setPrivacyMode(com.halo.app.ai.PrivacyMode.PRIVATE)
            assertFalse(Graph.storage.readRuntimeAiContextPolicy().allowCloudImages)
            assertFalse(Graph.aiProviderRouter.paidMediaAllowed)
            assertFalse(Graph.workspaceMediaAiPolicy.permitsObject(note.id, false))
            assertTrue(Graph.workspaceMediaAiPolicy.permitsObject(note.id, true))
            try {
                Graph.aiProviderRouter.execute(com.halo.sdk.provider.AiRequest(text = "Synthetic privacy dispatch check", modelId = "minimax/minimax-m3.1-flash:free"),
                    com.halo.sdk.provider.AiWorkloadSpec(pinnedTarget = com.halo.sdk.provider.AiModelRef("openrouter", "minimax/minimax-m3.1-flash:free"), allowFallback = false),
                    com.halo.sdk.provider.AiContext.EMPTY)
                fail("Private mode must block an explicitly selected cloud model")
            } catch (failure: com.halo.sdk.provider.AiProviderException) {
                assertTrue(failure.message.orEmpty().contains("Private / Cloud"))
                record("PRIVATE_DISPATCH_BLOCKED") { put("type", failure.type.name) }
            }
            record("PRIVACY_BOUNDARIES_PASSED")
        } finally { Graph.setPrivacyMode(previous) }
    }

    /** Offline raster fixture verifies persistence separately from paid provider availability. */
    @Test fun imagePersistenceAcrossNotebookAndCanvas() = runBlocking {
        require(args.getString("allowCloudDiagnostics") == "true")
        val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = try {
            bitmap.eraseColor(android.graphics.Color.rgb(120, 220, 185))
            java.io.ByteArrayOutputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
            }
        } finally { bitmap.recycle() }
        val image = com.halo.sdk.provider.AiImageResult(bytes, "image/png", "qa.synthetic", "raster-fixture")
        val note = Graph.notebookUseCases.createNote("Offline persistence fixture, not AI generated", "QA image persistence ${args.getString("runId")}")
        val attachment = Graph.notebookAssetRepository.addGenerated(note.id, image, "Synthetic mint raster fixture")
        assertTrue(Graph.notebookAssetRepository.readImage(attachment).contentEquals(bytes))
        assertEquals(96, attachment.width)
        assertEquals(96, attachment.height)
        val canvas = Graph.notebookCanvasRepository.create("QA image persistence ${args.getString("runId")}")
        val node = Graph.notebookCanvasRepository.addReference(canvas, note.id, 40f, 40f, image = true)
        assertTrue(Graph.appDatabase.notebookCanvasDao().getNodes(canvas).any { it.id == node && it.type == "IMAGE" })
        record("PERSISTENCE_FIXTURE_PASSED") { put("noteId", note.id); put("canvasId", canvas); put("bytes", bytes.size) }
    }

    /** One explicitly requested paid operation; existing local budget limits are never changed. */
    @Test fun generateSyntheticImageAndSave() = runBlocking {
        require(args.getString("allowCloudGeneration") == "true")
        check(!Graph.kidsPreferences.getEnabled())
        check(Graph.aiProviderRouter.routingPolicy.value != com.halo.sdk.provider.AiRoutingPolicy.LOCAL_ONLY)
        val providerId = requireNotNull(args.getString("providerId"))
        val modelId = requireNotNull(args.getString("modelId"))
        val reservation = requireNotNull(args.getString("reservationUsd")).toBigDecimal()
        require(reservation > java.math.BigDecimal.ZERO && reservation <= java.math.BigDecimal("0.10"))
        val service = Graph.workspaceImageService
        val previousMode = Graph.privacyModes.mode.value
        val previousProvider = Graph.storage.getString("workspace_image_provider")
        val previousModel = Graph.storage.getString("workspace_image_model")
        try {
            Graph.setPrivacyMode(com.halo.app.ai.PrivacyMode.CLOUD)
            val choice = service.catalogue(providerId).models.single { it.model.id == modelId }
            val prompt = "A simple flat mint green circle centered on a white square background, no text."
            val approval = service.prepareSpend(choice, prompt, null, "image/png")
            record("GENERATION_STARTED") { put("provider", providerId); put("model", modelId) }
            val image = service.generate(choice, prompt, null, "image/png", approval, reservation, true)
            val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size))
            try { record("GENERATION_DECODED") { put("width", bitmap.width); put("height", bitmap.height)
                put("bytes", image.bytes.size); put("mime", image.mimeType) } } finally { bitmap.recycle() }
            File(context.filesDir, "qa-cloud-generated-image.bin").writeBytes(image.bytes)
            val note = Graph.notebookUseCases.createNote("Synthetic cloud image QA", "QA generated image ${args.getString("runId")}")
            val attachment = Graph.notebookAssetRepository.addGenerated(note.id, image, prompt)
            assertTrue(Graph.notebookAssetRepository.readImage(attachment).contentEquals(image.bytes))
            record("GENERATION_SAVED") { put("noteId", note.id); put("attachmentId", attachment.id) }
            val canvas = Graph.notebookCanvasRepository.create("QA generated image ${args.getString("runId")}")
            val node = Graph.notebookCanvasRepository.addReference(canvas, note.id, 40f, 40f, image = true)
            record("GENERATION_CANVAS_LINKED") { put("canvasId", canvas); put("nodeId", node) }
        } catch (failure: Exception) {
            record("GENERATION_FAILED") { put("exception", failure.javaClass.simpleName)
                if (failure is com.halo.sdk.provider.AiProviderException) { put("type", failure.type.name); put("http", failure.details.httpStatus) } }
            throw failure
        } finally {
            Graph.setPrivacyMode(previousMode)
            Graph.storage.putString("workspace_image_provider", previousProvider)
            Graph.storage.putString("workspace_image_model", previousModel)
        }
    }

    @Test fun publicTlsAndImageCatalogue() = runBlocking {
        require(args.getString("allowCloudDiagnostics") == "true")
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        record("NETWORK") {
            put("device", android.os.Build.MODEL); put("api", android.os.Build.VERSION.SDK_INT)
            put("validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            put("vpn", caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            put("wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            put("cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        }
        for (host in listOf("openrouter.ai", "api.openai.com", "generativelanguage.googleapis.com")) {
            try {
                val addresses = Dns.SYSTEM.lookup(host)
                record("DNS") { put("host", host); put("ipv4", addresses.count { it is Inet4Address })
                    put("ipv6", addresses.count { it is java.net.Inet6Address }) }
            } catch (failure: Exception) { record("DNS_FAILED") { put("host", host); put("exception", failure.javaClass.simpleName) } }
            for (mode in listOf("DEFAULT", "TLS12", "IPV4")) {
                val builder = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false).followRedirects(false)
                if (mode == "TLS12") builder.connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2).build()))
                if (mode == "IPV4") builder.dns(object : Dns {
                    override fun lookup(hostname: String) = Dns.SYSTEM.lookup(hostname).filterIsInstance<Inet4Address>()
                })
                val client = builder.build()
                val start = System.currentTimeMillis()
                try {
                    client.newCall(Request.Builder().url("https://$host/").get().build()).execute().use { response ->
                        record("TLS_OK") { put("host", host); put("mode", mode); put("http", response.code)
                            put("tls", response.handshake?.tlsVersion?.javaName); put("protocol", response.protocol.toString())
                            put("elapsedMs", System.currentTimeMillis() - start) }
                    }
                } catch (failure: Exception) {
                    record("TLS_FAILED") { put("host", host); put("mode", mode)
                        put("causes", generateSequence<Throwable>(failure) { it.cause }.take(6).map { it.javaClass.simpleName }.toList().joinToString(","))
                        put("elapsedMs", System.currentTimeMillis() - start) }
                } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
            }
        }
        for (provider in Graph.workspaceImageService.providers) {
            try {
                val models = provider.listImageModels()
                record("IMAGE_CATALOGUE") { put("provider", provider.id); put("count", models.size)
                    put("models", org.json.JSONArray(models.map { JSONObject().put("id", it.id).put("editing", it.supportsEditing) })) }
            } catch (failure: Exception) { record("CATALOGUE_FAILED") { put("provider", provider.id); put("exception", failure.javaClass.simpleName)
                if (failure is com.halo.sdk.provider.AiProviderException) { put("type", failure.type.name); put("http", failure.details.httpStatus) } } }
        }
    }
}
