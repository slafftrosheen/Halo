package com.halo.providers.litert.models

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.halo.sdk.provider.LocalComputeBackend

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk=[33])
class HuggingFaceLiteRtCatalogTest {
    private fun details(name: String="chat.litertlm", gated: Any=false, hash: String="a".repeat(64)): JSONObject =
        JSONObject().put("sha","b".repeat(40)).put("gated",gated).put("siblings",org.json.JSONArray().put(
            JSONObject().put("rfilename",name).put("lfs",JSONObject().put("sha256",hash).put("size",3000000))))
    @Test fun `discovery pins identity without inventing capabilities`() {
        val m=HuggingFaceLiteRtCatalog.project("litert-community/Test",details().put("cardData",JSONObject().put("license","apache-2.0"))).single()
        assertEquals("b".repeat(40),m.revision); assertEquals(3000000L,m.expectedBytes)
        assertFalse(m.supportsTools); assertFalse(m.supportsVision)
        assertEquals(LiteRtToolCallPreference.NONE,m.toolCallPreference)
        assertEquals(LocalComputeBackend.CPU,m.runtimeProfile.preferredBackend)
        assertEquals(listOf(m),HuggingFaceLiteRtCatalog.decode(HuggingFaceLiteRtCatalog.encode(listOf(m))))
    }
    @Test fun `gated models remain gated`() {
        assertFalse(HuggingFaceLiteRtCatalog.project("litert-community/Test",details(gated="manual")).single().directDownloadAllowed)
    }
    @Test fun `unsafe formats accelerator variants and missing pins are excluded`() {
        for(name in listOf("chat.gguf","../chat.litertlm","chat.mediatek.mt6993.litertlm","chat.qnn.litertlm"))
            assertTrue(HuggingFaceLiteRtCatalog.project("litert-community/Test",details(name)).isEmpty())
        assertTrue(HuggingFaceLiteRtCatalog.project("litert-community/Test",details(hash="unverified")).isEmpty())
        assertTrue(HuggingFaceLiteRtCatalog.project("litert-community/Test",details().put("sha","main")).isEmpty())
    }
    @Test fun `Hub discovery resolves file metadata rather than downloading moving main`() = kotlinx.coroutines.runBlocking {
        val server=okhttp3.mockwebserver.MockWebServer(); server.start()
        try {
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("[{\"id\":\"litert-community/Test\"}]"))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(details().put("pipeline_tag","text-generation").toString()))
            val models=HuggingFaceLiteRtCatalog(endpoint=server.url("/").toString().trimEnd('/')).discover("Test & model")
            assertEquals(1,models.size)
            assertEquals("Test & model",server.takeRequest().requestUrl?.queryParameter("search"))
            assertTrue(server.takeRequest().path!!.endsWith("?blobs=true"))
            assertTrue(models.single().downloadUrl.contains("/resolve/"+"b".repeat(40)+"/"))
        } finally { server.shutdown() }
    }

    @Test fun `read token format rejects header injection and non ASCII secrets`() {
        assertTrue(isHuggingFaceReadToken("hf_test123"))
        for(value in listOf("hf_secret\r\nAuthorization: leaked","hf_😀","hf_","hf_"+"a".repeat(501)))
            assertFalse(isHuggingFaceReadToken(value))
    }

}
