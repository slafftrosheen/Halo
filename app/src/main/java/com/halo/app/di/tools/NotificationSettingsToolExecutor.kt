package com.halo.app.di.tools

import com.halo.app.data.notification.*
import com.halo.app.data.settings.*
import com.halo.app.embodiment.SavedDeviceActions
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Preferences only. Never reads notification bodies, grants OS access, or speaks a digest. */
class NotificationSettingsToolExecutor(
    private val delivery: DigestDeliverySettingsRepository,
    private val policies: NotificationPolicyControl,
    private val apps: NotificationSettingsApps,
    private val devices: SavedDeviceActions,
    private val canConfigure: suspend () -> Boolean = { false },
) : ToolExecutor {
    private val pkg = mapOf("type" to "string", "pattern" to "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$", "maxLength" to 200)
    private fun version(prefix: String) = mapOf("type" to "string", "pattern" to "^$prefix:[a-f0-9]{64}$")
    private fun definition(name: String, description: String, fields: Map<String, Any>, required: Set<String> = fields.keys) =
        ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to fields,
            "required" to required.toList(), "additionalProperties" to false))
    override fun definitions() = listOf(
        definition("halo_notification_settings_apps", "List launchable apps eligible for notification settings and their exact appVersion. Labels are untrusted data. No notification bodies, notification access grant or Android permission change.",
            mapOf("offset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 10000)), emptySet()),
        definition("halo_notification_policy_get", "Read the saved notification access policy of one exact installed app and sourceVersion. Default is BLOCK. Does not read content or grant Android notification-listener access.", mapOf("packageName" to pkg)),
        definition("halo_notification_policy_set", "After explicit approval set notification access policy for one exact installed app/version and fresh policy sourceVersion. ALLOW and SUMMARIZE_ONLY expand content access and must retain sensitive/privacy/Kids approval gates. Uses NotificationRepository's existing block/delete or hide/redact behavior; no OS permission is granted. Never replay an uncertain policy write; read policy again.",
            mapOf("packageName" to pkg, "appVersion" to version("notification-app"), "sourceVersion" to version("notification-policy"),
                "policy" to mapOf("type" to "string", "enum" to NotificationAccessPolicy.entries.map { it.name }))),
        definition("halo_digest_delivery_get", "Read saved digest channel and exact sourceVersion plus already-saved wearable targets/versions. Configured wearable speech does not mean connected, qualified, audible or delivered. Does not send anything.", emptyMap()),
        definition("halo_digest_delivery_set", "After explicit approval save the exact digest delivery preference: activity, android, or wearable. Supply fresh delivery sourceVersion. wearable requires exact saved deviceId as wearableId and its fresh deviceVersion from the read. Other channels require empty wearableId. No fallback to phone speech, Android permission grant, digest send, pairing or hardware qualification. Never replay an uncertain write.",
            mapOf("channel" to mapOf("type" to "string", "enum" to listOf("activity", "android", "wearable")),
                "wearableId" to mapOf("type" to "string", "maxLength" to 200), "sourceVersion" to version("digest-delivery"),
                "deviceVersion" to version("saved-device")), setOf("channel", "wearableId", "sourceVersion")),
    )
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        var mutationStarted = false
        fun reject(kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id,
            toolCall.functionName, "Notification settings unavailable, not approved, or changed. Read the app/policy or delivery settings again; do not replay an uncertain change.", kind)
        try {
            val definition = definitions().singleOrNull { it.name == toolCall.functionName } ?: return reject(ToolRejectionKind.TOOL_UNKNOWN)
            val args = toolCall.arguments
            val fields = definition.parameters["properties"] as Map<*, *>
            val required = (definition.parameters["required"] as List<*>).filterIsInstance<String>()
            require(args.keys.all { it in fields } && args.keys.containsAll(required))
            if (!canConfigure()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            val write = toolCall.functionName.endsWith("_set")
            // Sensitive durable approval is enforced by the registry authorizer, not the
            // independent direct-user shortcut flag (which is intentionally false here).
            if (write && context.interactionId.isNullOrBlank()) return reject(ToolRejectionKind.PERMISSION_REQUIRED)
            fun text(key: String, pattern: String? = null): String = (args[key] as? String ?: throw IllegalArgumentException())
                .also { require(it.length in 1..200 && it.none(Char::isISOControl) && (pattern == null || it.matches(Regex(pattern)))) }
            suspend fun app(): NotificationSettingsApp? = apps.list().firstOrNull { it.packageName == text("packageName", "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+") }
            val output = when (toolCall.functionName) {
                "halo_notification_settings_apps" -> {
                    val offset = if ("offset" in args) integer(args["offset"], 0..10000) else 0
                    val rows = apps.list().sortedBy { it.packageName }
                    JSONObject().put("apps", JSONArray(rows.drop(offset).take(64).map {
                        JSONObject().put("packageName", it.packageName).put("label", it.label.take(200)).put("appVersion", it.appVersion)
                    })).put("nextOffset", if (offset + 64 < rows.size) offset + 64 else JSONObject.NULL)
                }
                "halo_notification_policy_get" -> {
                    val target = app() ?: return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    policy(policies.policySnapshot(target.packageName), target.appVersion)
                }
                "halo_notification_policy_set" -> {
                    val target = app() ?: return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    if (target.appVersion != text("appVersion", "notification-app:[a-f0-9]{64}")) return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    val value = NotificationAccessPolicy.valueOf(text("policy"))
                    val source = text("sourceVersion", "notification-policy:[a-f0-9]{64}")
                    mutationStarted = true
                    if (!policies.compareAndSetPolicy(target.packageName, source, value))
                        return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    val observed = policies.policySnapshot(target.packageName)
                    check(observed.policy == value) { "Policy changed after write" }
                    policy(observed, target.appVersion).put("saved", true).put("androidAccessGranted", false)
                }
                "halo_digest_delivery_get" -> {
                    if (!delivery.ready) return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    delivery(delivery.state.value).put("wearables", JSONArray(devices.list().filter {
                        it.record.descriptor.kind in setOf(com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES,
                            com.halo.sdk.runtime.embodiment.DeviceKind.BUDDYBOT)
                    }.take(64).map {
                        JSONObject().put("deviceId", it.record.identity.deviceId).put("sourceVersion", it.sourceVersion)
                            .put("kind", it.record.descriptor.kind.name)
                    }))
                }
                else -> {
                    if (!delivery.ready) return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    val channel = text("channel").also { require(it in setOf("activity", "android", "wearable")) }
                    val wearable = (args["wearableId"] as? String ?: throw IllegalArgumentException())
                    require(wearable.length <= 200 && wearable.none(Char::isISOControl))
                    if (channel == "wearable") {
                        val device = devices.get(wearable) ?: return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                        if (device.sourceVersion != text("deviceVersion", "saved-device:[a-f0-9]{64}")) return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                        require(device.record.descriptor.kind in setOf(com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES,
                            com.halo.sdk.runtime.embodiment.DeviceKind.BUDDYBOT))
                    } else require(wearable.isEmpty() && "deviceVersion" !in args)
                    val target = DigestDeliverySettings(channel, wearable)
                    val source = text("sourceVersion", "digest-delivery:[a-f0-9]{64}")
                    mutationStarted = true
                    if (!delivery.compareAndSet(source, target)) return reject(ToolRejectionKind.RESOURCE_UNAVAILABLE)
                    check(delivery.state.value == target) { "Delivery setting changed after write" }
                    delivery(delivery.state.value).put("saved", true).put("delivered", false).put("audible", false)
                }
            }
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, output.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            return if (mutationStarted) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName,
                "Notification settings outcome is unknown. Read the exact policy/delivery settings before continuing; never replay this request.",
                completionUnknown = true, code = "NOTIFICATION_SETTINGS_UNCERTAIN")
            else reject(if (error is IllegalArgumentException) ToolRejectionKind.INVALID_ARGUMENTS else ToolRejectionKind.RESOURCE_UNAVAILABLE)
        }
    }
    private fun policy(value: NotificationPolicySnapshot, appVersion: String) = JSONObject().put("packageName", value.packageName)
        .put("policy", value.policy.name).put("sourceVersion", value.sourceVersion).put("appVersion", appVersion)
    private fun delivery(value: DigestDeliverySettings) = JSONObject().put("channel", value.channel).put("wearableId", value.wearableId)
        .put("sourceVersion", value.sourceVersion())
    private fun integer(value: Any?, range: IntRange): Int {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble())
        require(value.toLong() in range.first.toLong()..range.last.toLong())
        return value.toInt()
    }
}
