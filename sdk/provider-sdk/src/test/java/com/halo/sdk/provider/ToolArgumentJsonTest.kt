package com.halo.sdk.provider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ToolArgumentJsonTest {
    @Test fun nestedValuesKeepTheirTypesAndExactRevisionIdentity() {
        val decoded = ToolArgumentJson.decode("""{"steps":["Create","Read"],"record":{"tags":["qa",{"enabled":true}]},"revision":9007199254740993,"optional":null,"literal":"[1,2]"}""")
        assertEquals(listOf("Create", "Read"), decoded["steps"])
        assertEquals(mapOf("tags" to listOf("qa", mapOf("enabled" to true))), decoded["record"])
        assertEquals(9007199254740993L, decoded["revision"])
        assertSame(JSONObject.NULL, decoded["optional"])
        assertEquals("[1,2]", decoded["literal"])
    }

    @Test fun malformedOversizedAndExcessivelyNestedArgumentsFailClosed() {
        var deep = JSONObject().put("value", true)
        repeat(65) { deep = JSONObject().put("child", deep) }
        val wide = JSONObject().put("items", JSONArray(List(65_536) { 0 }))
        for (value in listOf("[]", "not json", JSONObject().put("text", "x".repeat(1024 * 1024 + 1)), deep, wide)) {
            val error = runCatching { ToolArgumentJson.decode(value) }.exceptionOrNull()
            assertTrue(error is AiProviderException)
            assertEquals(AiErrorType.INVALID_RESPONSE, (error as AiProviderException).type)
        }
    }
}
