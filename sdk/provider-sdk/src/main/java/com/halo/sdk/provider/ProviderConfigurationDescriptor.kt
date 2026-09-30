package com.halo.sdk.provider

/** Small bounded schema for settings; values and credentials never belong to descriptors. */
enum class ProviderFieldKind { SECRET, TEXT, ENDPOINT, ENUM, OAUTH_ACTION }
data class ProviderConfigurationField(val id: String, val label: String, val kind: ProviderFieldKind, val options: List<String> = emptyList())
data class ProviderConfigurationDescriptor(
    val providerId: String,
    val displayName: String,
    val location: AiExecutionLocation,
    val fields: List<ProviderConfigurationField>,
    val protocol: String,
    val access: String,
) {
    init { require(fields.size <= 8) }
    val endpointEditable: Boolean get() = fields.any { it.kind == ProviderFieldKind.ENDPOINT }
    val supportsOAuth: Boolean get() = fields.any { it.kind == ProviderFieldKind.OAUTH_ACTION }
    val supportsModelDiscovery: Boolean get() = true
    val supportsConnectionTest: Boolean get() = true
}
interface ProviderConfigurationReporting { val configurationDescriptor: ProviderConfigurationDescriptor }
