package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition
object LocalModelRoleOperability {
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in setOf("halo_local_role_state", "halo_local_role_select")) return null
        val read = definition.name == "halo_local_role_state"
        return ToolOperability(capabilityId = CapabilityId("host.assistant.local_roles"),
            owner = "ProviderControlRepository/DefaultAiProviderRouter", operation = if (read) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "role" to mapOf("type" to "string"), "sourceVersion" to mapOf("type" to "string", "pattern" to "^local-role:[a-f0-9]{64}$"),
                "selection" to mapOf("type" to "string"), "providerId" to mapOf("type" to listOf("string", "null")),
                "modelId" to mapOf("type" to listOf("string", "null")), "localPolicy" to mapOf("type" to "boolean"),
                "candidates" to mapOf("type" to "array", "maxItems" to 30, "items" to mapOf("type" to "object")),
                "nextOffset" to mapOf("type" to listOf("integer", "null")), "generationPerformed" to mapOf("type" to "boolean"),
                "qualificationMeasured" to mapOf("type" to "boolean"), "profileChanged" to mapOf("type" to "boolean"))),
            sourceVersionArguments = if (read) emptySet() else setOf("sourceVersion"),
            resultIdFields = setOf("providerId", "modelId"),
            limitations = setOf("Personal configuration metadata uses existing Workspace disclosure authority.",
                "Existing mode-profile tools retain default CHAT/provider/model/routing fields. Remote selection, automatic role policy and disclosure expansion remain manual review.",
                "Only non-CHAT explicit installed eligible local role targets, safe local inheritance and disable. No generation, download, test, new router or measured qualification claim.",
                "Writes require foreground grown-up interaction and durable ALWAYS approval; exact router/catalogue/candidate/privacy snapshot admission; never replay uncertain writes."))
    }
}
