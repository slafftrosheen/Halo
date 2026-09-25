package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.host.android.resolver.AndroidContactResolver
import com.halo.host.android.resolver.ContactResolveResult
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class CommunicationTools(private val context: Context) : ToolExecutor {
    private val contactResolver = AndroidContactResolver(context)

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_find_contact",
            name = "halo_find_contact",
            description = "Find a contact by name.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("query" to mapOf("type" to "string")),
                "required" to listOf("query"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_dial_contact",
            name = "halo_dial_contact",
            description = "Open the dialer for a specific phone number.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("phoneNumber" to mapOf("type" to "string")),
                "required" to listOf("phoneNumber"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        return when (toolCall.functionName) {
            "halo_find_contact" -> {
                val query = toolCall.arguments["query"] as? String ?: return reject("Missing query")
                when (val result = contactResolver.resolve(query)) {
                    is ContactResolveResult.Found -> {
                        ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Found \${result.name} at \${result.phoneNumber}")
                    }
                    is ContactResolveResult.MultipleMatches -> {
                        ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Multiple matches found. Please clarify.")
                    }
                    is ContactResolveResult.NoPhoneNumber -> {
                        ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Contact found but has no phone number.")
                    }
                    is ContactResolveResult.NotFound -> {
                        ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "No contact found for \$query")
                    }
                }
            }
            "halo_dial_contact" -> {
                val phoneNumber = toolCall.arguments["phoneNumber"] as? String ?: return reject("Missing phone number")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:\$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) == null) {
                    return reject("No dialer app available")
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
            }
            else -> reject("Unknown tool")
        }
    }
}
