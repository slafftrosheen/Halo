package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*

/** Geo coordinates and documented HTTPS Maps URLs only; no arbitrary map schemes/components. */
class NativeMapTools(private val context: Context, private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_native_map", "halo_native_map",
        "Open validated coordinates, share a user-provided location pin, or request driving/walking/transit/cycling directions through documented Google Maps URLs. Dispatch never proves navigation started or arrival.",
        mapOf("type" to "object", "properties" to mapOf(
            "action" to mapOf("type" to "string", "enum" to listOf("coordinates", "share_pin", "directions")),
            "latitude" to mapOf("type" to "number", "minimum" to -90, "maximum" to 90),
            "longitude" to mapOf("type" to "number", "minimum" to -180, "maximum" to 180),
            "destination" to mapOf("type" to "string", "maxLength" to 500),
            "mode" to mapOf("type" to "string", "enum" to listOf("driving", "walking", "transit", "bicycling"))),
            "required" to listOf("action"), "additionalProperties" to false)))
    internal fun intentFor(args: Map<String, Any>): Intent {
        val action = args["action"]
        if (action == "directions") {
            require(args.keys == setOf("action", "destination", "mode"))
            val destination = args["destination"] as? String ?: error("destination required")
            val mode = args["mode"] as? String ?: error("mode required")
            require(destination.isNotBlank() && destination.length <= 500 && mode in setOf("driving", "walking", "transit", "bicycling"))
            val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                .appendQueryParameter("api", "1").appendQueryParameter("destination", destination)
                .appendQueryParameter("travelmode", mode).appendQueryParameter("dir_action", "navigate").build()
            return Intent(Intent.ACTION_VIEW, uri)
        }
        require(action in setOf("coordinates", "share_pin") && args.keys == setOf("action", "latitude", "longitude"))
        val latitude = (args["latitude"] as? Number)?.toDouble() ?: error("latitude required")
        val longitude = (args["longitude"] as? Number)?.toDouble() ?: error("longitude required")
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val coordinates = "$latitude,$longitude"
        return if (action == "coordinates") Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=${Uri.encode(coordinates)}"))
        else Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            Uri.parse("https://www.google.com/maps/search/").buildUpon().appendQueryParameter("api", "1")
                .appendQueryParameter("query", coordinates).build().toString()), null)
    }
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName != "halo_native_map") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (!isForeground()) return reject("Open Halo to review this map handoff", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        return try {
            context.startActivity(intentFor(toolCall.arguments).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "Map handoff requested; navigation, location sharing, or destination effect not confirmed"), ActionTruth.DISPATCHED)
        } catch (_: android.content.ActivityNotFoundException) { reject("No map/browser/share handler", ToolRejectionKind.CAPABILITY_UNAVAILABLE) }
        catch (_: SecurityException) { reject("Android denied map handoff", ToolRejectionKind.PERMISSION_REQUIRED) }
        catch (_: IllegalArgumentException) { reject("Invalid coordinates or directions fields", ToolRejectionKind.INVALID_ARGUMENTS) }
        catch (_: IllegalStateException) { reject("Missing map fields", ToolRejectionKind.INVALID_ARGUMENTS) }
    }
}
