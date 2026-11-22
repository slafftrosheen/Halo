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

    @Test fun responsesApiReturnsOnlyCitationsWithACompletedSearchAndValidTextRange() {
        val root = JSONObject("""{"status":"completed","model":"gpt-5.4-mini","output":[
            {"type":"web_search_call","status":"completed"},
            {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Rīga report.","annotations":[
                {"type":"url_citation","start_index":5,"end_index":11,"url":"https://example.com/report","title":"Report"},
                {"type":"url_citation","start_index":0,"end_index":4,"url":"https://127.0.0.1/private"},
                {"type":"url_citation","start_index":0,"end_index":90,"url":"https://example.com/invalid"}
            ]}]}
        ]}""")
        val answer = OpenAiWebSearchClient.parseAnswer(root, 123L)
        assertEquals("openai", answer.providerId)
        assertEquals(WebCitation(5, 11, "https://example.com/report"), answer.citations.single())
        assertEquals("Report", answer.sources.single().title)
        assertNull(answer.reportedCostUsd)
        root.getJSONArray("output").getJSONObject(0).put("status", "failed")
        assertThrows(OnlineOperationRejected::class.java) { OpenAiWebSearchClient.parseAnswer(root, 123L) }
    }
}
