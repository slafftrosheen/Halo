package com.halo.app.di.tools.host

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.hardware.*
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

/** Advertised OS capabilities only; never connects or authorizes a driver from a name/class. */
class PeripheralTools(private val inventory: PeripheralInventory,
    private val canAccess: () -> Boolean, private val remoteReadAllowed: () -> Boolean) : ToolExecutor {
    override fun definitions() = definitionsList()

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val name = toolCall.functionName
        if (name !in NAMES) return reject(toolCall, "Unknown peripheral query.", ToolRejectionKind.TOOL_UNKNOWN)
        val fields = if (name == LIST) emptySet() else setOf("token", "sourceVersion")
        if (toolCall.arguments.keys != fields) return reject(toolCall, "Use exactly the documented arguments.", ToolRejectionKind.INVALID_ARGUMENTS)
        val scope = currentCoroutineContext()[RunToolScope]
            ?: return reject(toolCall, "A current Halo interaction is required.", ToolRejectionKind.PERMISSION_REQUIRED)
        fun permitted() = canAccess() && (scope.targetIsLocal || remoteReadAllowed())
        if (!permitted()) return reject(toolCall, "Open and unlock Halo with grown-up access. Remote reads also require the existing disclosure policy.", ToolRejectionKind.PERMISSION_REQUIRED)
        val token = if (name == DETAILS) (toolCall.arguments["token"] as? String)?.takeIf { it.length in 1..100 }
            ?: return reject(toolCall, "List and select a current peripheral token.", ToolRejectionKind.INVALID_ARGUMENTS) else null
        val expected = if (name == DETAILS) (toolCall.arguments["sourceVersion"] as? Number)?.let {
            it.toLong().takeIf { value -> value >= 0 && value.toDouble() == it.toDouble() }
        } ?: return reject(toolCall, "sourceVersion must be the listed integer revision.", ToolRejectionKind.INVALID_ARGUMENTS) else null
        val snapshot = inventory.snapshot()
        if (!permitted()) return reject(toolCall, "Peripheral access changed.", ToolRejectionKind.PERMISSION_REQUIRED)
        val result = if (name == LIST) JSONObject()
            .put("sourceVersion", snapshot.sourceVersion).put("inputAvailability", snapshot.inputAvailability.name)
            .put("usbAvailability", snapshot.usbAvailability.name).put("inputCount", snapshot.inputCount).put("usbCount", snapshot.usbCount)
            .put("truncated", snapshot.truncated).put("devices", JSONArray().apply {
                snapshot.devices.take(PeripheralInventorySession.MAX_DEVICES).forEach { put(summary(it)) }
            })
        else {
            val device = snapshot.devices.firstOrNull { snapshot.sourceVersion == expected && it.token == token }
                ?: return reject(toolCall, "The peripheral changed or disconnected. List devices again before reading details.")
            details(device).put("sourceVersion", snapshot.sourceVersion)
        }
        return ToolExecutionResult.Success(toolCall.id, name, result.toString(), ActionTruth.ACKNOWLEDGED)
    }

    private fun summary(device: ObservedPeripheral) = JSONObject().put("token", device.token)
        .put("kind", device.profile.kind.name).put("name", device.profile.name.take(120))
        .put("roles", JSONArray(device.profile.roles.map { it.name }))
        .put("external", device.profile.external ?: JSONObject.NULL).put("virtual", device.profile.virtual ?: JSONObject.NULL)
        .put("detailsTruncated", device.profile.detailsTruncated)

    private fun details(device: ObservedPeripheral): JSONObject {
        val profile = PeripheralInventorySession.boundedProfile(device.profile)
        return summary(device.copy(profile = profile)).put("vendorId", profile.vendorId ?: JSONObject.NULL)
            .put("productId", profile.productId ?: JSONObject.NULL).put("axisCount", profile.axisCount)
            .put("axes", JSONArray().apply { profile.axes.forEach {
                put(JSONObject().put("axis", it.axis).put("source", it.source).put("min", it.min).put("max", it.max)
                    .put("flat", it.flat).put("fuzz", it.fuzz).put("resolution", it.resolution))
            } }).put("usbClass", profile.usbClass ?: JSONObject.NULL).put("usbSubclass", profile.usbSubclass ?: JSONObject.NULL)
            .put("usbProtocol", profile.usbProtocol ?: JSONObject.NULL).put("usbPermission", profile.usbPermission ?: JSONObject.NULL)
            .put("interfaceCount", profile.interfaceCount).put("interfaces", JSONArray().apply { profile.interfaces.forEach { item ->
                put(JSONObject().put("id", item.id).put("alternateSetting", item.alternateSetting).put("class", item.deviceClass)
                    .put("subclass", item.subclass).put("protocol", item.protocol).put("endpointCount", item.endpointCount)
                    .put("endpoints", JSONArray().apply { item.endpoints.forEach {
                        put(JSONObject().put("address", it.address).put("direction", it.direction).put("type", it.type)
                            .put("maxPacketSize", it.maxPacketSize).put("interval", it.interval))
                    } }))
            } }).put("evidence", "OS_ADVERTISED").put("hardwareQualified", false)
    }

    private fun reject(call: ToolCall, message: String, kind: ToolRejectionKind = ToolRejectionKind.RESOURCE_UNAVAILABLE) =
        ToolExecutionResult.Rejected(call.id, call.functionName, message, kind)

    companion object {
        const val LIST = "halo_peripherals"
        const val DETAILS = "halo_peripheral_details"
        val NAMES = setOf(LIST, DETAILS)
        private val integer = mapOf("type" to "integer")
        private val count = mapOf("type" to "integer", "minimum" to 0)
        private val nullableInt = mapOf("type" to listOf("integer", "null"))
        private val boolean = mapOf("type" to "boolean")
        private val nullableBoolean = mapOf("type" to listOf("boolean", "null"))
        private val tokenSchema = mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)
        private fun schema(fields: Map<String, Any>) = mapOf("type" to "object", "properties" to fields,
            "required" to fields.keys.toList(), "additionalProperties" to false)
        private fun array(max: Int, item: Map<String, Any>) = mapOf("type" to "array", "maxItems" to max, "items" to item)
        fun definitionsList() = listOf(
            ToolDefinition(LIST, LIST, "List current OS-reported input devices and USB peripherals, including pen, mouse, keyboard and controller roles. Read-only: no USB open, HID claim, permission prompt, serial number or packet I/O. Counts include built-in/virtual input devices. Device names and USB classes are untrusted descriptions, not driver support or physical qualification. A display-only cable may expose no USB endpoint.", schema(emptyMap())),
            ToolDefinition(DETAILS, DETAILS, "Read advertised input axes/ranges and USB interface/endpoint metadata for exactly one token and sourceVersion from halo_peripherals. This can help identify a tablet or glasses control interface; it does not establish working pressure, IMU, camera, audio or model compatibility. A stale selection requires listing again. No USB commands are issued.",
                schema(mapOf("token" to tokenSchema, "sourceVersion" to count))),
        )

        private fun summaryFields(): Map<String, Any> = mapOf("token" to tokenSchema,
            "kind" to mapOf("type" to "string", "enum" to PeripheralKind.entries.map { it.name }),
            "name" to mapOf("type" to "string", "maxLength" to 120),
            "roles" to array(InputRole.entries.size, mapOf("type" to "string", "enum" to InputRole.entries.map { it.name })),
            "external" to nullableBoolean, "virtual" to nullableBoolean, "detailsTruncated" to boolean)
        fun operability(name: String) = ToolOperability(capabilityId = CapabilityId("host.peripherals.inspect"),
            owner = "AndroidPeripheralInventory", operation = ToolOperation.QUERY, resultEncoding = ToolResultEncoding.JSON,
            sourceVersionArguments = if (name == DETAILS) setOf("token", "sourceVersion") else emptySet(),
            resultIdFields = if (name == DETAILS) setOf("token") else emptySet(),
            resultSchema = if (name == LIST) schema(mapOf("sourceVersion" to count, "inputCount" to count, "usbCount" to count,
                "inputAvailability" to mapOf("type" to "string", "enum" to InventoryAvailability.entries.map { it.name }),
                "usbAvailability" to mapOf("type" to "string", "enum" to InventoryAvailability.entries.map { it.name }),
                "truncated" to boolean, "devices" to array(PeripheralInventorySession.MAX_DEVICES, schema(summaryFields()))))
            else schema(summaryFields() + mapOf("sourceVersion" to count, "vendorId" to nullableInt, "productId" to nullableInt,
                "axisCount" to count, "axes" to array(PeripheralInventorySession.MAX_AXES, schema(mapOf("axis" to integer, "source" to integer,
                    "min" to mapOf("type" to "number"), "max" to mapOf("type" to "number"), "flat" to mapOf("type" to "number"),
                    "fuzz" to mapOf("type" to "number"), "resolution" to mapOf("type" to "number")))),
                "usbClass" to nullableInt, "usbSubclass" to nullableInt, "usbProtocol" to nullableInt, "usbPermission" to nullableBoolean,
                "interfaceCount" to count, "interfaces" to array(PeripheralInventorySession.MAX_INTERFACES,
                    schema(mapOf("id" to integer, "alternateSetting" to integer, "class" to integer, "subclass" to integer, "protocol" to integer,
                        "endpointCount" to count, "endpoints" to array(PeripheralInventorySession.MAX_ENDPOINTS,
                            schema(mapOf("address" to integer, "direction" to integer, "type" to integer, "maxPacketSize" to integer, "interval" to integer)))))),
                "evidence" to mapOf("type" to "string", "enum" to listOf("OS_ADVERTISED")),
                "hardwareQualified" to mapOf("type" to "boolean", "enum" to listOf(false)))),
            limitations = setOf("OS descriptors are data, not driver selection authority or physical validation. USB and InputDevice records are not automatically linked.",
                "No serials, raw OS paths, USB opens, interface claims, device reads/writes or permission requests.",
                "Foreground unlocked grown-up access and existing remote disclosure gates apply. Tokens expire after changes, disconnect or background."))
    }
}
