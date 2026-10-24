package com.halo.app.di.tools

import com.halo.app.ai.*
import com.halo.app.di.tools.host.VoiceConfigurationTools
import com.halo.app.context.CanvasWorkflowBridge
import com.halo.app.learning.LearningConfigurationOwner
import com.halo.app.study.ActiveStudyToolsOwner
import com.halo.runtime.cognition.memory.LearningPreferences
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.Storage
import org.junit.Assert.*
import org.junit.Test

/** Real definition factories and central descriptor helper; no source, provider or action runs. */
class CompleteConfigurationDescriptorTest {
    @Test fun registeredVoiceLearningProviderAndActiveStudyContractsRetainCanonicalApprovalAndCapabilities() {
        val provider = object : ProviderConfigurationControl {
            override suspend fun providerConfigurations(): List<ProviderConfigurationSnapshot> = error("No source reads in schema review")
            override suspend fun providerConfiguration(providerId: String): ProviderConfigurationSnapshot? = error("No source reads in schema review")
            override suspend fun requestProviderCheck(providerId: String, expectedVersion: String,
                command: ProviderConfigurationCommand, admitted: () -> Boolean): ProviderConfigurationSnapshot? = error("No provider requests")
        }
        val executors = listOf<ToolExecutor>(
            VoiceConfigurationTools({ error("No voice settings reads") }, { _, _ -> error("No settings writes") }, { false }, { false }),
            LearningConfigurationTools(LearningConfigurationOwner(MemoryStorage(), LearningPreferences())) { false },
            ProviderConfigurationTools(provider) { false }, StudySessionTools(ActiveStudyToolsOwner()) { false },
            CanvasWorkflowTools(CanvasWorkflowBridge()), SpeechInventoryTools(object : SpeechInventoryControl {
                override suspend fun current(): SpeechInventorySnapshot = error("No inventory reads in schema review")
                override suspend fun dispatch(modelId: String, sourceVersion: String, assetVersion: String,
                    action: String, admitted: () -> Boolean, artifactVersion: String?): String = error("No inventory actions in schema review")
            }) { false })
        val definitions = executors.flatMap { it.definitions() }
        assertEquals(19, definitions.size)
        val descriptors = definitions.map { definition ->
            val descriptor = requireNotNull(HaloToolDescriptors.assistant(definition)) { "Missing ${definition.name}" }
            val contract = requireNotNull(descriptor.operability)
            val expected = VoiceConfigurationTools.operability(definition.name) ?: LearningConfigurationTools.operability(definition.name)
                ?: ProviderConfigurationOperability.forDefinition(definition) ?: StudySessionOperability.forDefinition(definition)
                ?: CanvasWorkflowOperability.forDefinition(definition) ?: SpeechInventoryOperability.forDefinition(definition)
            assertEquals(expected, contract)
            assertTrue(descriptor.requirements.contains(ToolRequirement.Capability(contract.capabilityId)))
            if (contract.operation == ToolOperation.QUERY) {
                assertEquals(ToolRiskClass.READ_ONLY, descriptor.security.risk)
                assertEquals(ConfirmationPolicy.NONE, descriptor.security.confirmation)
            } else {
                assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
                assertEquals(ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
                assertEquals(if (definition.name in ProviderConfigurationOperability.writes) ToolRiskClass.EXTERNAL_SIDE_EFFECT
                    else ToolRiskClass.SENSITIVE, descriptor.security.risk)
                assertTrue(definition.name, contract.sourceVersionArguments.isNotEmpty())
            }
            if (definition.name in setOf("halo_get_voice_configuration", "halo_set_voice_configuration"))
                assertEquals(12000, descriptor.execution.maxOutputChars)
            assertNotNull(descriptor.presentation)
            assertFalse(descriptor.presentation!!.title.startsWith("halo_"))
            descriptor
        }
        val registry = ToolRegistry(executors, descriptors)
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("complete-config-domains", definitions.map { it.name }.toSet()))).isEmpty())
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
