package com.halo.app.di.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class HostTools(private val context: Context) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_open_settings",
            name = "halo_open_settings",
            description = "Open device settings page.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "section" to mapOf("type" to "string")
                ),
                "required" to emptyList<String>(),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_prepare_contact",
            name = "halo_prepare_contact",
            description = "Prepare contact insert intent for user confirmation.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string"),
                    "phone" to mapOf("type" to "string"),
                    "email" to mapOf("type" to "string")
                ),
                "required" to listOf("name"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        return when (toolCall.functionName) {
            "halo_open_settings" -> {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Dispatched open settings intent")
            }
            "halo_prepare_contact" -> {
                val name = toolCall.arguments["name"]?.toString()?.trim().orEmpty()
                val phone = toolCall.arguments["phone"]?.toString()?.trim().orEmpty()
                val email = toolCall.arguments["email"]?.toString()?.trim().orEmpty()

                if (name.isBlank()) return reject("Name is required")

                val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                    if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                    if (email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Dispatched contact insert intent for '$name'")
            }
            else -> reject("Unsupported host tool: ${toolCall.functionName}")
        }
    }
}
