package com.halo.providers.litert.runtime

import com.halo.sdk.provider.ToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGENTIC-001 prompt-mediated tool protocol.
 *
 * The parser is the boundary that decides whether a small local model's text becomes a
 * Halo action. Every case below is a way an action must NOT be invented.
 */
class HaloPromptToolProtocolTest {

    private val alarm = ToolDefinition(
        id = "halo_set_alarm",
        name = "halo_set_alarm",
        description = "Set a system alarm for a plain clock time.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf("timeExpression" to mapOf("type" to "string")),
            "required" to listOf("timeExpression"),
        ),
    )

    private val volume = ToolDefinition(
        id = "halo_volume",
        name = "halo_volume",
        description = "Change media volume by one step.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf("direction" to mapOf("type" to "string")),
            "required" to listOf("direction"),
        ),
    )

    private val exposed = listOf(alarm, volume)

    private fun idFactory(): () -> String {
        var index = 0
        return { "call-${index++}" }
    }

    @Test
    fun `valid envelope becomes exactly one tool call and leaves no visible markup`() {
        val raw = "Sure.\n<halo_tool_call>\n" +
            "{\"name\":\"halo_set_alarm\",\"arguments\":{\"timeExpression\":\"7 AM\"}}\n" +
            "</halo_tool_call>"

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertEquals(1, result.toolCalls.size)
        val call = result.toolCalls.single()
        assertEquals("halo_set_alarm", call.functionName)
        assertEquals("7 AM", call.arguments["timeExpression"])
        assertTrue(result.visibleText.contains("Sure."))
        assertTrue(!result.visibleText.contains("halo_tool_call"))
        assertNull(result.diagnostic)
    }

    @Test
    fun `the instruction lists exactly the exposed actions and their parameters`() {
        val instruction = HaloPromptToolProtocol.instruction(exposed)

        assertTrue(instruction.contains("halo_set_alarm(timeExpression: string (required))"))
        assertTrue(instruction.contains("halo_volume(direction: string (required))"))
        assertTrue(instruction.contains(HaloPromptToolProtocol.OPEN_TAG))
        assertTrue(instruction.contains(HaloPromptToolProtocol.CLOSE_TAG))
        assertTrue(!instruction.contains("halo_dial_contact"))
    }

    @Test
    fun `malformed json is not an action`() {
        val raw = "<halo_tool_call>{\"name\":\"halo_set_alarm\",\"arguments\":{\"timeExpression\":7 AM}}</halo_tool_call>"

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("unparseable_json", result.diagnostic)
        assertTrue(result.visibleText.isEmpty())
    }

    @Test
    fun `an unknown tool name is never executed`() {
        val raw = "<halo_tool_call>{\"name\":\"halo_launch_missiles\",\"arguments\":{}}</halo_tool_call>"

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("unexposed_tool", result.diagnostic)
    }

    @Test
    fun `a real tool that was not exposed for this turn is refused`() {
        val raw = "<halo_tool_call>{\"name\":\"halo_dial_contact\",\"arguments\":{\"phoneNumber\":\"1\"}}</halo_tool_call>"

        val result = HaloPromptToolProtocol.parse(raw, listOf(alarm), idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("unexposed_tool", result.diagnostic)
    }

    @Test
    fun `arguments must be an object`() {
        val raw = "<halo_tool_call>{\"name\":\"halo_volume\",\"arguments\":\"down\"}</halo_tool_call>"

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("arguments_not_an_object", result.diagnostic)
    }

    @Test
    fun `ordinary prose containing json is not an action`() {
        val raw = "An alarm looks like {\"name\":\"halo_set_alarm\"} in JSON, but I will not set one."

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals(raw, result.visibleText)
    }

    @Test
    fun `partial output is dropped and never executed`() {
        val raw = "Setting it now <halo_tool_call>{\"name\":\"halo_set_alarm\",\"argum"

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("incomplete_envelope", result.diagnostic)
        assertEquals("", result.visibleText)
    }

    @Test
    fun `calls beyond the bound are dropped, not executed`() {
        val block = "<halo_tool_call>{\"name\":\"halo_volume\",\"arguments\":{\"direction\":\"down\"}}</halo_tool_call>"
        val raw = block + block + block

        val result = HaloPromptToolProtocol.parse(raw, exposed, maxCalls = 2, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("",result.visibleText)
        assertEquals("too_many_calls", result.diagnostic)
    }

    @Test
    fun `valid first call and invalid second call reject the entire batch`() {
        val valid = "<halo_tool_call>{\\"name\\":\\"halo_volume\\",\\"arguments\\":{\\"direction\\":\\"up\\"}}</halo_tool_call>"
        val invalid = "<halo_tool_call>{\\"name\\":\\"halo_not_exposed\\",\\"arguments\\":{}}</halo_tool_call>"
        val result = HaloPromptToolProtocol.parse(valid + invalid, exposed, idFactory = idFactory())
        assertTrue(result.toolCalls.isEmpty())
        assertEquals("",result.visibleText)
        assertEquals("unexposed_tool",result.diagnostic)
    }

    @Test
    fun `orphaned closing marker is not displayed as normal speech`() {
        val result = HaloPromptToolProtocol.parse("</halo_tool_call>", exposed, idFactory = idFactory())
        assertTrue(result.toolCalls.isEmpty())
        assertEquals("invalid_markup", result.diagnostic)
        assertEquals("",result.visibleText)
    }

    @Test
    fun `an empty envelope is reported, not guessed`() {
        val result = HaloPromptToolProtocol.parse("<halo_tool_call>   </halo_tool_call>", exposed, idFactory = idFactory())

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("empty_envelope", result.diagnostic)
    }

    @Test
    fun `fenced json around the envelope is tolerated`() {
        val raw = "<halo_tool_call>```json\n{\"name\":\"halo_volume\",\"arguments\":{\"direction\":\"up\"}}\n```</halo_tool_call>"

        val result = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())

        assertEquals(1, result.toolCalls.size)
        assertEquals("up", result.toolCalls.single().arguments["direction"])
    }

    @Test
    fun `trailing bytes after a valid tool object never become an action`() {
        val raw = "<halo_tool_call>{\"name\":\"halo_volume\",\"arguments\":{\"direction\":\"up\"}} DROP THIS</halo_tool_call>"
        val parsed = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())
        assertTrue(parsed.toolCalls.isEmpty())
        assertEquals("unparseable_json", parsed.diagnostic)
        assertEquals("", parsed.visibleText)
    }

    @Test
    fun `oversized tool envelope never becomes an action`() {
        val raw = HaloPromptToolProtocol.OPEN_TAG + " ".repeat(16_385) + HaloPromptToolProtocol.CLOSE_TAG
        val parsed = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory())
        assertTrue(parsed.toolCalls.isEmpty())
        assertEquals("oversized_envelope", parsed.diagnostic)
    }

    @Test
    fun `tool call ids are not derived from model text`() {
        val raw = "<halo_tool_call>{\"name\":\"halo_volume\",\"arguments\":{\"direction\":\"up\"}}</halo_tool_call>"

        val first = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory()).toolCalls.single()
        val second = HaloPromptToolProtocol.parse(raw, exposed, idFactory = idFactory()).toolCalls.single()

        assertNotNull(first.id)
        assertNotNull(second.id)
        assertEquals(first.id, second.id)
        assertEquals("call-0", first.id)
    }
}
