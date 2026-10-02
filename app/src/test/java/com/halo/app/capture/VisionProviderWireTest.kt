package com.halo.app.capture

import android.graphics.Bitmap
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.multimodal.*
import com.halo.runtime.cognition.vision.*
import com.halo.providers.gemini.GeminiProvider
import com.halo.providers.openai.OpenAiProvider
import com.halo.providers.openrouter.OpenRouterProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisionProviderWireTest {
    @Test fun preparedImageReachesAllThreeCloudWiresExactlyOnceWithTextAndMime() = runBlocking {
        val bodies = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer); bodies += buffer.readUtf8()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"candidates":[{"content":{"parts":[{"text":"HALO 42"}]},"finishReason":"STOP"}],"choices":[{"message":{"content":"HALO 42"},"finish_reason":"stop"}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val storage = MemoryStorage().apply {
            putString("gemini_api_key", "test"); putString("openai_api_key", "test"); putString("openrouter_api_key", "test")
        }
        val providers = listOf<AiInferenceProvider>(GeminiProvider(storage, client), OpenAiProvider(storage, client), OpenRouterProvider(storage, client))
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val bytes = try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } } finally { bitmap.recycle() }
        val ref = MediaAssetRef("test", MediaKind.IMAGE, "image/png", bytes.size.toLong(), null, 1, MediaProvenance.GENERATED)
        val resolver = object : MediaAssetResolver {
            override suspend fun metadata(ref: MediaAssetRef) = null
            override suspend fun open(ref: MediaAssetRef) = MediaAssetContent(bytes, "image/png")
        }
        val candidates = providers.map { provider -> VisionCandidate(provider,
            AiModel(if (provider.id == "gemini") "gemini-2.5-flash" else "test/vision", "Vision", provider.id,
                setOf(AiCapability.TEXT, AiCapability.IMAGE), 4096, false, false,
                capabilityEvidence = mapOf(AiCapability.IMAGE to CapabilityEvidence.AUTHORITATIVE_METADATA))) }
        val lab = VisionLab(resolver, AndroidImageInputPreparer(), MediaObjectPolicyResolver { _, _ -> true }, storage)
        val rows = lab.compare(ref, candidates, AiContextPolicy(allowCloudImages = true), "Describe this test image")
        assertTrue(rows.toString(), rows.all { it.stage == VisionStage.COMPLETE })
        assertEquals(3, bodies.size)
        bodies.forEach { body ->
            val json = JSONObject(body)
            assertTrue(body.contains("Describe this test image"))
            assertFalse(body.contains("tools"))
            if (json.has("contents")) {
                val parts = json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
                val images = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.has("inline_data") || it.has("inlineData") }
                assertEquals(1, images.size)
                val inline = images.single().optJSONObject("inline_data") ?: images.single().getJSONObject("inlineData")
                val encoded = inline.getString("data")
                val decoded = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                assertTrue(decoded.isNotEmpty())
                assertEquals("image/jpeg", inline.optString("mime_type", inline.optString("mimeType")))
                assertFalse(json.optJSONObject("systemInstruction")?.toString().orEmpty().contains(encoded))
            } else {
                val messages = json.getJSONArray("messages")
                val users = (0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.optString("role") == "user" }
                assertEquals(1, users.size)
                val content = users.single().getJSONArray("content")
                val images = (0 until content.length()).map { content.getJSONObject(it) }.filter { it.optString("type") == "image_url" }
                assertEquals(1, images.size)
                val url = images.single().getJSONObject("image_url").getString("url")
                assertTrue(url.startsWith("data:image/jpeg;base64,"))
                val decoded = android.util.Base64.decode(url.substringAfter(','), android.util.Base64.DEFAULT)
                assertEquals(0xff, decoded[0].toInt() and 255) // Actual JPEG bytes, not base64 encoded twice.
                assertFalse((0 until messages.length()).map { messages.getJSONObject(it) }.filter { it.optString("role") != "user" }.any { it.toString().contains(url) })
            }
        }
    }
    private class MemoryStorage : Storage {
        val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
