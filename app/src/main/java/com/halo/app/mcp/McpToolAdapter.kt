package com.halo.app.mcp

import com.halo.app.data.mcp.McpToolEntity
import com.halo.app.data.mcp.McpTrustLevel
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolExecutionPolicy
import com.halo.runtime.cognition.tools.ToolId
import com.halo.runtime.cognition.tools.ToolPresentation
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.runtime.cognition.tools.ToolSecurityPolicy
import com.halo.runtime.cognition.tools.ToolVisibility
import com.halo.sdk.provider.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * MINING-P1-001 MCP → Halo tool translation.
 *
 * Two invariants, both borrowed from Brownie's *mechanics* but implemented with Halo's own trust
 * model:
 *
 * 1. A remote name is never registered globally. The canonical id is
 *    `mcp.<serverId>.<remoteToolName>`, sanitized, with the original name kept separately.
 * 2. A server describes its tool; it never classifies risk. Imported tools default to
 *    [ToolRiskClass.SENSITIVE] and the risk is stored in Halo's own catalogue.
 */
object McpToolAdapter {

    const val NAMESPACE_PREFIX = "mcp"

    fun sanitize(segment: String): String = segment
        .lowercase()
        .map { character -> if (character.isLetterOrDigit() || character == '_') character else '_' }
        .joinToString("")
        .trim('_')
        .take(MAX_SEGMENT)
        .ifBlank { "unnamed" }

    /** Canonical Halo namespace id. Stable while the remote name is stable. */
    fun namespaceId(serverId: String, remoteName: String): String =
        "$NAMESPACE_PREFIX.${sanitize(serverId)}.${sanitize(remoteName)}"

    /**
     * Default risk for an imported remote tool. Trust levels do NOT lower this: trusting a server's
     * identity is not the same as permitting its tools to run unattended.
     */
    fun defaultRisk(trust: McpTrustLevel): ToolRiskClass = when (trust) {
        // Still SENSITIVE: the server's own claim is never the authority.
        McpTrustLevel.LOCAL_TRUSTED, McpTrustLevel.USER_APPROVED, McpTrustLevel.UNTRUSTED ->
            ToolRiskClass.SENSITIVE
    }

    fun toEntity(
        serverId: String,
        trust: McpTrustLevel,
        tool: McpRemoteTool,
        nowMs: Long,
    ): McpToolEntity = McpToolEntity(
        id = namespaceId(serverId, tool.name),
        serverId = serverId,
        remoteName = tool.name,
        displayName = tool.name,
        description = tool.description,
        inputSchemaJson = tool.inputSchemaJson,
        risk = defaultRisk(trust).name,
        enabled = true,
        lastRefreshedAt = nowMs,
    )

    fun toDefinition(entity: McpToolEntity): ToolDefinition = ToolDefinition(
        id = entity.id,
        name = entity.id,
        description = buildString {
            append(entity.description.ifBlank { "Remote tool ${entity.remoteName}" })
            append(" (MCP server ${entity.serverId}; untrusted external content)")
        }.take(MAX_DESCRIPTION),
        parameters = jsonSchemaToParameters(entity.inputSchemaJson),
    )

    /**
     * Converts a remote JSON schema into Halo's parameter map. Unknown keywords are dropped rather
     * than trusted, and the schema is forced closed so the model cannot invent extra arguments.
     */
    fun jsonSchemaToParameters(schemaJson: String): Map<String, Any?> {
        val schema = runCatching { JSONObject(schemaJson) }.getOrNull() ?: return defaultParameters()
        val properties = schema.optJSONObject("properties") ?: return defaultParameters()
        val converted = LinkedHashMap<String, Any?>()
        val keys = properties.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = properties.optJSONObject(key) ?: continue
            converted[key] = convert(value, depth = 0)
        }
        if (converted.isEmpty()) return defaultParameters()
        return mapOf(
            "type" to "object",
            "properties" to converted,
            "required" to requiredNames(schema, converted.keys),
            "additionalProperties" to false,
        )
    }

    private fun requiredNames(schema: JSONObject, known: Set<String>): List<String> {
        val array = schema.optJSONArray("required") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val name = array.optString(index)
                if (name in known) add(name)
            }
        }
    }

    private fun convert(source: JSONObject, depth: Int): Map<String, Any?> {
        if (depth > MAX_SCHEMA_DEPTH) return mapOf("type" to "string")
        val result = LinkedHashMap<String, Any?>()
        val type = source.optString("type")
        val allowed = setOf("string", "number", "integer", "boolean", "array", "object")
        result["type"] = if (type in allowed) type else "string"
        source.optString("description").takeIf { it.isNotBlank() }?.let { result["description"] = it.take(300) }
        if (source.has("enum")) {
            val array = source.optJSONArray("enum")
            if (array != null) {
                val values = buildList {
                    for (index in 0 until minOf(array.length(), MAX_ENUM_VALUES)) add(array.opt(index))
                }
                if (values.isNotEmpty()) result["enum"] = values
            }
        }
        (source.opt("minLength") as? Number)?.let { result["minLength"] = it.toInt().coerceAtLeast(0) }
        (source.opt("maxLength") as? Number)?.let { result["maxLength"] = it.toInt().coerceAtMost(MAX_STRING_LENGTH) }
        (source.opt("minimum") as? Number)?.let { result["minimum"] = it.toDouble() }
        (source.opt("maximum") as? Number)?.let { result["maximum"] = it.toDouble() }
        (source.opt("default") as? String)?.let { result["default"] = it.take(300) }
        when (result["type"]) {
            "array" -> source.optJSONObject("items")?.let { result["items"] = convert(it, depth + 1) }
            "object" -> source.optJSONObject("properties")?.let { nested ->
                val converted = LinkedHashMap<String, Any?>()
                val keys = nested.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    nested.optJSONObject(key)?.let { converted[key] = convert(it, depth + 1) }
                }
                result["properties"] = converted
            }
        }
        return result
    }

    /** Fallback when a server publishes an unusable schema: one bounded free-form object. */
    private fun defaultParameters(): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "arguments" to mapOf("type" to "string", "description" to "JSON object of arguments", "maxLength" to 4_000),
        ),
        "required" to emptyList<String>(),
        "additionalProperties" to false,
    )

    /**
     * Halo-owned descriptor for a remote tool. Execution always flows through the ordinary
     * authorization path — an MCP server never gets an execution shortcut.
     */
    fun toDescriptor(entity: McpToolEntity): ToolDescriptor {
        val risk = runCatching { ToolRiskClass.valueOf(entity.risk) }.getOrDefault(ToolRiskClass.SENSITIVE)
        return ToolDescriptor(
            id = ToolId(entity.id),
            definition = toDefinition(entity),
            category = ToolCategory.AUTOMATION,
            keywords = setOf("mcp", sanitize(entity.serverId), sanitize(entity.remoteName)),
            visibility = ToolVisibility.DISCOVERABLE,
            security = ToolSecurityPolicy(
                risk = risk,
                confirmation = ConfirmationPolicy.USER_POLICY,
                // A remote server may be non-idempotent; Halo reconciles before any retry.
                replaySafety = ReplaySafety.RECONCILE_BEFORE_RETRY,
            ),
            execution = ToolExecutionPolicy(timeoutMs = DEFAULT_TIMEOUT_MS, maxOutputChars = MAX_OUTPUT_CHARS),
            presentation = ToolPresentation(
                title = "MCP · ${entity.displayName}",
                actionVerb = "Use",
                targetArgument = null,
            ),
        )
    }

    /** True when this tool id belongs to the given MCP server. */
    fun belongsToServer(toolId: String, serverId: String): Boolean =
        toolId.startsWith("$NAMESPACE_PREFIX.${sanitize(serverId)}.")

    /** Bounded, redacted tool JSON for diagnostics export. */
    fun summarize(entity: McpToolEntity): String = buildString {
        append(entity.id)
        append(" risk=").append(entity.risk)
        append(" enabled=").append(entity.enabled)
        append(" schemaChars=").append(entity.inputSchemaJson.length)
    }

    private const val MAX_SEGMENT = 64
    private const val MAX_DESCRIPTION = 400
    private const val MAX_SCHEMA_DEPTH = 4
    private const val MAX_ENUM_VALUES = 32
    private const val MAX_STRING_LENGTH = 4_000
    private const val DEFAULT_TIMEOUT_MS = 15_000L
    private const val MAX_OUTPUT_CHARS = 4_000
}

/** Convenience for callers that only need the enabled/disabled flag toggled. */
fun McpToolEntity.withEnabled(enabled: Boolean): McpToolEntity = copy(enabled = enabled)

/** Package-local helper so the executor can read optional keys without unchecked casts. */
internal fun JSONObject.optObjectOrNull(key: String): JSONObject? = optJSONObject(key)

internal fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
    }
}
