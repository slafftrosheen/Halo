package com.halo.app.mcp

import com.halo.app.data.mcp.McpServerEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class McpReliabilityTest {
    private val server=McpServerEntity("s","Fixture","https://fixture.test/mcp",true,"NONE",null,"UNTRUSTED",0,0,null,null,null)
    private val session=McpClient.Session(McpProtocol.PREFERRED_VERSION,"Fixture","1",null,"session",true)
    private fun transport(result: (JSONObject)->JSONObject) = object : McpTransport {
        override suspend fun post(endpoint:String,body:String,bearerToken:String?,sessionId:String?,protocolVersion:String?):McpHttpResponse {
            val request=JSONObject(body)
            return McpHttpResponse(200,result(request).toString(),"session","application/json")
        }
    }
    private fun response(request:JSONObject,result:JSONObject)=JSONObject().put("jsonrpc","2.0").put("id",request.getLong("id")).put("result",result)

    @Test fun mismatchedResponseAfterCallIsUnknownAndNeverRetried() = runBlocking {
        var calls=0
        val client=McpClient(transport { request -> calls++; response(request,JSONObject()).put("id",999) })
        val failure=runCatching { client.callTool(server,session,"write",JSONObject()) }.exceptionOrNull() as McpProtocolException
        assertTrue(failure.completionUnknown); assertEquals(1,calls)
    }
    @Test fun responseAndErrorCannotBothBeAccepted() {
        val message=JSONObject().put("jsonrpc","2.0").put("id",1).put("result",JSONObject()).put("error",JSONObject().put("code",-1))
        assertTrue(runCatching { McpJson.resultOf(message,McpStage.TOOLS_CALL,1) }.isFailure)
    }
    @Test fun repeatedCursorFailsInsteadOfPublishingAPartialCatalogue() = runBlocking {
        var calls=0
        val client=McpClient(transport { request -> calls++; response(request,JSONObject().put("tools",JSONArray()).put("nextCursor","same")) })
        assertTrue(runCatching { client.listTools(server,session) }.isFailure)
        assertEquals(2,calls)
    }
    @Test fun oversizedSchemaIsRejectedWithoutTruncatingItsJson() {
        val tool=JSONObject().put("name","write").put("inputSchema",JSONObject().put("description","x".repeat(McpProtocol.MAX_SCHEMA_CHARS)))
        assertTrue(runCatching { McpJson.parseTools(JSONObject().put("tools",JSONArray().put(tool))) }.isFailure)
    }
    @Test fun outputBoundariesDiscloseMissingContentAndPreserveUnicode() {
        val raw="🧠".repeat(McpProtocol.MAX_RESULT_CHARS)
        val result=McpJson.parseCallResult(JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text",raw))))
        assertTrue(result.truncated); assertTrue(result.text.contains("remaining content was not supplied"))
        assertTrue(result.text.length <= McpProtocol.MAX_RESULT_CHARS)
        assertFalse(result.text.contains('\uFFFD'))
        val prefix=result.text.substringBefore('\n')
        assertFalse(prefix.last().isHighSurrogate())
    }
    @Test fun sseNotificationAfterResponseDoesNotReplaceIt() {
        val correct="""{"jsonrpc":"2.0","id":7,"result":{}}"""
        val raw="data: $correct\n\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\n"
        assertEquals(correct,matchingSseData(raw,7))
        assertTrue(runCatching { matchingSseData(raw,8) }.isFailure)
    }
}
