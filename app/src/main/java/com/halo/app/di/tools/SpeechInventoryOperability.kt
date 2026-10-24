package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

object SpeechInventoryOperability {
    val reads = setOf("halo_speech_inventory")
    val writes = setOf("halo_speech_model_action")
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in reads + writes) return null
        return ToolOperability(capabilityId = CapabilityId("host.speech.inventory"), owner = "VoiceSettingsViewModel/SpeechModelInventoryRepository/SpeechModelInstaller",
            operation = if (definition.name in reads) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "sourceVersion" to mapOf("type" to "string", "pattern" to "^speech-inventory:[a-f0-9]{64}$"),
                "modelId" to mapOf("type" to "string", "maxLength" to 200), "requestId" to mapOf("type" to "string", "maxLength" to 200),
                "assets" to mapOf("type" to "array", "maxItems" to 16, "items" to mapOf("type" to "object")),
                "completionConfirmed" to mapOf("type" to "boolean"))),
            sourceVersionArguments = if (definition.name in writes) setOf("sourceVersion", "assetVersion", "artifactVersion") else emptySet(),
            resultIdFields = if (definition.name in writes) setOf("modelId") else emptySet(),
            limitations = setOf("Pinned native catalog only. No URL, custom pack/file import, credential, microphone, provider inference or qualification authority.",
                "Writes require grown-up, foreground, scoped interaction, capture/testing stopped and canonical durable ALWAYS approval. NEVER_REPLAY uncertain dispatch.",
                "Removal retains the existing repository's selected/active model and transfer/resource guards; pause affects only this native Voice owner's exact download.",
                "Install/resume can consume network/storage and preserve native download verification; installed/selected does not mean accurate, suitable or tested.",
                "Inventory/selection is personal configuration metadata and uses existing Workspace disclosure/provenance gates remotely."))
    }
}
