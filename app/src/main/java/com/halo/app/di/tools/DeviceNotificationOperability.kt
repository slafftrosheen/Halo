package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

/** Metadata for the registered canonical adapters, not an authority or availability grant. */
object DeviceNotificationOperability {
    private val id = mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
    private val token = mapOf("type" to "string", "maxLength" to 100)
    private val boolean = mapOf("type" to "boolean")
    private val nullableToken = mapOf("anyOf" to listOf(token, mapOf("type" to "null")))
    private fun obj(properties: Map<String, Any>, required: Set<String> = properties.keys) = mapOf(
        "type" to "object", "properties" to properties, "required" to required.toList(), "additionalProperties" to false)
    private val device = mapOf("deviceId" to id, "label" to mapOf("type" to "string", "maxLength" to 200),
        "kind" to id, "autoConnect" to boolean, "sourceVersion" to token, "sessionState" to id, "saved" to boolean)
    private val policy = mapOf("packageName" to id, "policy" to id, "sourceVersion" to token, "appVersion" to token)
    private val delivery = mapOf("channel" to id, "wearableId" to mapOf("type" to "string", "maxLength" to 200), "sourceVersion" to token)
    private val page = mapOf("anyOf" to listOf(mapOf("type" to "integer", "minimum" to 0, "maximum" to 10000), mapOf("type" to "null")))
    private val deviceNames = setOf("halo_saved_devices", "halo_saved_device_get", "halo_device_preferences", "halo_device_thumbnail_quality",
        "halo_saved_device_retry", "halo_saved_device_connect", "halo_saved_device_disconnect", "halo_saved_device_autoconnect", "halo_saved_device_forget")
    private val notificationNames = setOf("halo_notification_settings_apps", "halo_notification_policy_get", "halo_notification_policy_set",
        "halo_digest_delivery_get", "halo_digest_delivery_set")
    private val queries = setOf("halo_saved_devices", "halo_saved_device_get", "halo_device_preferences",
        "halo_notification_settings_apps", "halo_notification_policy_get", "halo_digest_delivery_get")
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        val name = definition.name
        if (name !in deviceNames && name !in notificationNames) return null
        val schema = when (name) {
            "halo_saved_devices" -> obj(mapOf("devices" to mapOf("type" to "array", "maxItems" to 64, "items" to obj(device)), "nextOffset" to page))
            "halo_saved_device_get" -> obj(device)
            "halo_device_preferences", "halo_device_thumbnail_quality" -> obj(mapOf("quality" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 5),
                "sourceVersion" to token) + if (name.endsWith("quality")) mapOf("saved" to boolean) else emptyMap())
            "halo_notification_settings_apps" -> obj(mapOf("apps" to mapOf("type" to "array", "maxItems" to 64,
                "items" to obj(mapOf("packageName" to id, "label" to mapOf("type" to "string", "maxLength" to 200), "appVersion" to token))), "nextOffset" to page))
            "halo_notification_policy_get" -> obj(policy)
            "halo_notification_policy_set" -> obj(policy + mapOf("saved" to boolean, "androidAccessGranted" to boolean))
            "halo_digest_delivery_get" -> obj(delivery + mapOf("wearables" to mapOf("type" to "array", "maxItems" to 64,
                "items" to obj(mapOf("deviceId" to id, "sourceVersion" to token, "kind" to id)))))
            "halo_digest_delivery_set" -> obj(delivery + mapOf("saved" to boolean, "delivered" to boolean, "audible" to boolean))
            else -> obj(mapOf("deviceId" to id, "sourceVersion" to nullableToken, "saved" to boolean, "requested" to boolean,
                "autoConnect" to mapOf("anyOf" to listOf(boolean, mapOf("type" to "null"))),
                "connectionConfirmed" to boolean, "physicalDisconnectionConfirmed" to boolean))
        }
        val fields = definition.parameters["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val owner = when {
            name in deviceNames -> "SavedDeviceActions"
            name.startsWith("halo_digest") -> "DigestDeliverySettingsRepository"
            name == "halo_notification_settings_apps" -> "NotificationSettingsAppSource"
            else -> "RoomNotificationRepository"
        }
        return ToolOperability(capabilityId = CapabilityId(if (name in deviceNames) "halo.devices.saved" else "halo.notifications.settings"),
            owner = owner, operation = if (name in queries) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, resultSchema = schema,
            sourceVersionArguments = fields.keys.filterIsInstance<String>().filter { it in setOf("sourceVersion", "appVersion", "deviceVersion") }.toSet(),
            resultIdFields = when {
                name == "halo_saved_device_get" || name.startsWith("halo_saved_device_") -> setOf("deviceId")
                name.startsWith("halo_notification_policy_") -> setOf("packageName")
                else -> emptySet()
            }, limitations = setOf(
                "Typed and STT-final use the same registered action; this does not attest spoken/provider or hardware acceptance.",
                "Existing Kids/grown-up, OS permissions, privacy/content categories and registry-enforced durable ALWAYS approval remain mandatory. All writes require a current authorized interaction.",
                "Query the exact target/source version first; never replay an uncertain write. Process-local device/policy versions expire after restart.",
                "Saved/configured/dispatched does not mean connected, physically disconnected, qualified, delivered or audible. No pairing or protected OS/security flow is automated."))
    }
}
