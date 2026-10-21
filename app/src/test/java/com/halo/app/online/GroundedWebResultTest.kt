package com.halo.app.online

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroundedWebResultTest {
    @Test fun plainModelLinksNeverBecomeSources() {
        val body = JSONObject("""{"choices":[{"finish_reason":"stop","message":{"content":"See https://example.com"}}]}""")
        assertThrows(OnlineOperationRejected::class.java) { OpenRouterOnlineClient.parseAnswer(body, 123L) }
    }
    @Test fun citationMetadataIsBoundedAndDeduplicated() {
        val body = JSONObject("""{"model":"resolved/free","choices":[{"finish_reason":"stop","message":{"content":"An answer.","annotations":[
            {"type":"url_citation","url_citation":{"url":"https://example.com/a","title":"Source"}},
            {"type":"url_citation","url_citation":{"url":"https://example.com/a","title":"Duplicate"}},
            {"type":"url_citation","url_citation":{"url":"javascript:alert(1)","title":"Unsafe"}},
            {"type":"url_citation","url_citation":{"url":"https://127.0.0.1/private","title":"Local"}},
            {"type":"url_citation","url_citation":{"url":"https://user:password@example.com/private","title":"Credentials"}}
        ]}}]}""")
        val result = OpenRouterOnlineClient.parseAnswer(body, 123L)
        assertEquals(1, result.sources.size)
        assertEquals("https://example.com/a", result.sources.single().url)
        assertEquals(123L, result.retrievedAtMs)
    }
    @Test fun truncatedOrToolResponsesAreNotGroundedSuccess() {
        for (finish in listOf("length", "tool_calls", "error")) {
            val body = JSONObject("""{"choices":[{"finish_reason":"$finish","message":{"content":"Unfinished"}}]}""")
            assertThrows(OnlineOperationRejected::class.java) { OpenRouterOnlineClient.parseAnswer(body, 0L) }
        }
    }
    @Test fun missingUsageIsUnknownNotZero() {
        assertNull(OpenRouterOnlineClient.reportedCost(JSONObject("{}")))
        assertNull(OpenRouterOnlineClient.reportedCost(JSONObject("""{"usage":{"cost":-1}}""")))
        assertEquals("0.007", OpenRouterOnlineClient.reportedCost(JSONObject("""{"usage":{"cost":0.007}}"""))?.toPlainString())
    }
}
