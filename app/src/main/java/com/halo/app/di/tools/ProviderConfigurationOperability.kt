package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

object ProviderConfigurationOperability {
    val reads = setOf("halo_provider_catalog", "halo_provider_state")
    val writes = setOf("halo_provider_refresh_models", "halo_provider_test_connection")
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in reads + writes) return null
        return ToolOperability(capabilityId = CapabilityId("host.assistant.providers"), owner = "ProviderControlRepository",
            operation = if (definition.name in reads) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON,
            resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "providerId" to mapOf("type" to "string", "maxLength" to 200),
                "sourceVersion" to mapOf("type" to "string", "pattern" to "^provider-configuration:[a-f0-9]{64}$"),
                "providers" to mapOf("type" to "array", "maxItems" to 64, "items" to mapOf("type" to "object")),
                "models" to mapOf("type" to "array", "maxItems" to 30, "items" to mapOf("type" to "object")),
                "checkCompleted" to mapOf("type" to "boolean"), "generationTested" to mapOf("type" to "boolean"))),
            sourceVersionArguments = if (definition.name in writes) setOf("sourceVersion") else emptySet(),
            resultIdFields = if (definition.name == "halo_provider_catalog") emptySet() else setOf("providerId"),
            limitations = setOf("Personal provider/catalogue metadata requires existing Workspace category disclosure remotely.",
                "No keys, endpoint URLs, credential/OAuth edits, model selection, generation or new network client.",
                "Checks require grown-up/foreground/current interaction and canonical durable ALWAYS approval; NEVER_REPLAY uncertain requests.",
                "Exact process-local source token is consumed before check. Existing provider-check/account-metadata remote policy remains final authority.",
                "Completed authentication/catalogue check outcome is not inference, billing or hardware qualification."))
    }
}
