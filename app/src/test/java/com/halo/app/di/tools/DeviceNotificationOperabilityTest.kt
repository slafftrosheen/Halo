package com.halo.app.di.tools

import com.halo.app.data.notification.*
import com.halo.app.data.settings.*
import com.halo.runtime.cognition.tools.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class DeviceNotificationOperabilityTest {
    @Test fun domainSchemasHaveBothInputChannelsExactVersionGatesAndBoundedResults() {
        val f = DeviceSettingsFixture()
        val delivery = object : DigestDeliverySettingsRepository {
            override val state = MutableStateFlow(DigestDeliverySettings())
            override suspend fun setChannel(value: String) = Unit
            override suspend fun setWearableId(value: String) = Unit
        }
        val policies = object : NotificationPolicyControl {
            override suspend fun policySnapshot(pkg: String) = NotificationPolicySnapshot(pkg, NotificationAccessPolicy.BLOCK, "unused")
            override suspend fun compareAndSetPolicy(pkg: String, expectedVersion: String, policy: NotificationAccessPolicy) = false
        }
        val definitions = SavedDeviceToolExecutor(f.owner).definitions() +
            NotificationSettingsToolExecutor(delivery, policies, NotificationSettingsApps { emptyList() }, f.owner).definitions()
        assertEquals(14, definitions.size)
        val descriptors = definitions.map { definition ->
            val contract = requireNotNull(DeviceNotificationOperability.forDefinition(definition))
            assertEquals(ToolInputChannel.entries.toSet(), contract.channels)
            assertEquals(false, definition.parameters["additionalProperties"])
            val fields = definition.parameters["properties"] as Map<*, *>
            if (contract.operation == ToolOperation.EDIT) assertTrue("Write ${definition.name} needs a source version", "sourceVersion" in contract.sourceVersionArguments)
            assertTrue(contract.sourceVersionArguments.all { it in fields })
            val descriptor = requireNotNull(HaloToolDescriptors.assistant(definition)) { "Missing actual registered descriptor for ${definition.name}" }
            assertEquals(contract, descriptor.operability)
            if (contract.operation == ToolOperation.EDIT) {
                assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
                assertEquals(ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
            }
            descriptor
        }
        assertEquals(emptyList<ToolOperabilityProblem>(), ToolOperabilityReview.inspect(
            listOf(ToolFeatureOperability("device-notification-domain", definitions.map { it.name }.toSet())), descriptors))
        val policy = descriptors.single { it.name == "halo_notification_policy_set" }.operability!!
        assertEquals(setOf("sourceVersion", "appVersion"), policy.sourceVersionArguments)
        assertEquals(setOf("sourceVersion", "deviceVersion"), descriptors.single { it.name == "halo_digest_delivery_set" }.operability!!.sourceVersionArguments)
    }
}
