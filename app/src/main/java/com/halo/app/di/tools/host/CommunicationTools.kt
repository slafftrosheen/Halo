package com.halo.app.di.tools.host

import android.Manifest
import android.content.ActivityNotFoundException
import android.provider.ContactsContract
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.halo.host.android.resolver.AndroidContactResolver
import com.halo.host.android.resolver.ContactResolveResult
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class CommunicationTools(private val context: Context) : ToolExecutor {
    private val contactResolver = AndroidContactResolver(context)

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_find_contact",
            name = "halo_find_contact",
            description = "Look up a contact by name. Returns the resolved name, or the ambiguous choices to ask about.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("query" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)),
                "required" to listOf("query"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_dial_contact",
            name = "halo_dial_contact",
            description = "Open the dialer with the resolved phone number. Receipt confirms dispatch only; " +
                "the user still has to press the call button.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "phoneNumber" to mapOf("type" to "string", "description" to "Exact number to dial"),
                    "name" to mapOf("type" to "string", "description" to "Or a contact name to resolve first")
                ),
                "required" to emptyList<String>(),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_contact_editor", name = "halo_contact_editor",
            description = "Open the Android contact editor. User reviews and saves. Does not confirm a saved contact.",
            parameters = mapOf(
                "type" to "object", "properties" to mapOf(
                    "mode" to mapOf("type" to "string", "enum" to listOf("create", "choose_or_edit")),
                    "name" to mapOf("type" to "string", "maxLength" to 100),
                    "phone" to mapOf("type" to "string", "maxLength" to 40),
                    "email" to mapOf("type" to "string", "maxLength" to 254)),
                "required" to listOf("mode"), "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        return when (toolCall.functionName) {
            "halo_contact_editor" -> contactEditor(toolCall)
            "halo_find_contact" -> {
                val query = (toolCall.arguments["query"] as? String)?.trim()
                    ?: return reject("query is required", ToolRejectionKind.INVALID_ARGUMENTS)
                if (query.isEmpty()) return reject("query is empty", ToolRejectionKind.INVALID_ARGUMENTS)
                if (!isNumber(query) && !hasContactsPermission()) {
                    return reject(
                        "Halo does not have permission to read contacts. Ask the user to grant it, then try again.",
                        ToolRejectionKind.PERMISSION_REQUIRED,
                    )
                }
                when (val result = contactResolver.resolve(query)) {
                    is ContactResolveResult.Found -> ToolExecutionResult.Success(
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName,
                        output = ToolResultEnvelope.success(
                            actionTruth = ActionTruth.ACKNOWLEDGED,
                            details = "contact_found\nname=${result.name}\nnumber=${result.number}",
                        ),
                        actionTruth = ActionTruth.ACKNOWLEDGED,
                    )

                    is ContactResolveResult.MultipleMatches -> {
                        // A clarification is the honest outcome, not a fabricated success.
                        val choices = result.candidateNames.ifEmpty { listOf(query) }
                        ToolExecutionResult.Success(
                            toolCallId = toolCall.id,
                            toolId = toolCall.functionName,
                            output = ToolResultEnvelope.success(
                                actionTruth = ActionTruth.PROPOSED,
                                reasonCode = "NEEDS_CLARIFICATION",
                                details = buildString {
                                    append("status=ambiguous_multiple_matches\n")
                                    append("matches=").append(choices.joinToString("|")).append('\n')
                                    append("next=ASK_USER_WHICH_CONTACT_THEN_CALL_halo_dial_contact")
                                },
                            ),
                            actionTruth = ActionTruth.PROPOSED,
                        )
                    }

                    is ContactResolveResult.NoPhoneNumber -> ToolExecutionResult.Success(
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName,
                        output = ToolResultEnvelope.success(
                            actionTruth = ActionTruth.ACKNOWLEDGED,
                            reasonCode = "NO_PHONE_NUMBER",
                            details = "The contact exists but has no phone number Halo can dial.",
                        ),
                        actionTruth = ActionTruth.ACKNOWLEDGED,
                    )

                    is ContactResolveResult.NotFound -> ToolExecutionResult.Success(
                        toolCallId = toolCall.id,
                        toolId = toolCall.functionName,
                        output = ToolResultEnvelope.success(
                            actionTruth = ActionTruth.ACKNOWLEDGED,
                            reasonCode = "CONTACT_NOT_FOUND",
                            details = "No contact found for \"$query\"",
                        ),
                        actionTruth = ActionTruth.ACKNOWLEDGED,
                    )
                }
            }

            "halo_dial_contact" -> {
                val explicitNumber = (toolCall.arguments["phoneNumber"] as? String)?.trim()
                val name = (toolCall.arguments["name"] as? String)?.trim()
                if (explicitNumber.isNullOrEmpty() && name.isNullOrEmpty()) {
                    return reject(
                        "Provide either phoneNumber or name to dial",
                        ToolRejectionKind.INVALID_ARGUMENTS,
                    )
                }
                if (!hasContactsPermission() && name != null && explicitNumber == null) {
                    return reject(
                        "Halo does not have permission to read contacts, so it cannot resolve \"$name\".",
                        ToolRejectionKind.PERMISSION_REQUIRED,
                    )
                }

                val number = when {
                    !explicitNumber.isNullOrEmpty() -> explicitNumber
                    else -> when (val resolved = contactResolver.resolve(name!!)) {
                        is ContactResolveResult.Found -> resolved.number
                        is ContactResolveResult.MultipleMatches -> {
                            val choices = resolved.candidateNames.ifEmpty { listOf(name) }
                            return ToolExecutionResult.Success(
                                toolCallId = toolCall.id,
                                toolId = toolCall.functionName,
                                output = ToolResultEnvelope.success(
                                    actionTruth = ActionTruth.PROPOSED,
                                    reasonCode = "NEEDS_CLARIFICATION",
                                    details = "status=ambiguous_multiple_matches\nmatches=" +
                                        choices.joinToString("|") + "\nnext=ASK_USER_THEN_RETRY",
                                ),
                                actionTruth = ActionTruth.PROPOSED,
                            )
                        }
                        is ContactResolveResult.NoPhoneNumber -> return reject(
                            "\"$name\" has no phone number Halo can dial",
                            ToolRejectionKind.UNSUPPORTED_OPERATION,
                        )
                        is ContactResolveResult.NotFound -> return reject(
                            "No contact found for \"$name\". Ask the user for the exact name or number.",
                            ToolRejectionKind.INVALID_ARGUMENTS,
                        )
                    }
                }

                val dialable = canonicalDialNumber(number)
                if (dialable.isEmpty()) {
                    return reject("The phone number is not dialable", ToolRejectionKind.INVALID_ARGUMENTS)
                }

                // The URI must carry the actually resolved number. An escaped
                // "\$number" would have silently dialled the literal text "tel:$number".
                val dialUri = dialUriFor(dialable)
                val intent = Intent(Intent.ACTION_DIAL, dialUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) == null) {
                    return reject("This device has no app that can dial a number", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    output = ToolResultEnvelope.success(
                        actionTruth = ActionTruth.DISPATCHED,
                        details = buildString {
                            append("dial_uri=").append(dialUri).append('\n')
                            append(
                                "note: The dialer opened with the number pre-filled. The call is not placed " +
                                    "until the user confirms; this stays DISPATCHED."
                            )
                        },
                    ),
                    actionTruth = ActionTruth.DISPATCHED,
                )
            }

            else -> reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    private fun contactEditor(call: ToolCall): ToolExecutionResult {
        val mode = call.arguments["mode"] as? String ?: return ToolExecutionResult.Rejected(
            call.id, call.functionName, "Specify create or choose_or_edit", ToolRejectionKind.INVALID_ARGUMENTS)
        val name = (call.arguments["name"] as? String)?.trim().orEmpty()
        val phone = (call.arguments["phone"] as? String)?.trim().orEmpty()
        val email = (call.arguments["email"] as? String)?.trim().orEmpty()
        if (mode !in setOf("create", "choose_or_edit") ||
            name.length > 100 || phone.length > 40 || email.length > 254 ||
            (mode == "choose_or_edit" && name.isNotEmpty()) ||
            (email.isNotEmpty() && !email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) ||
            (phone.isNotEmpty() && canonicalDialNumber(phone).length !in 3..20)) {
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "Invalid contact editor options. Do not prefill names while editing an existing contact.",
                ToolRejectionKind.INVALID_ARGUMENTS)
        }
        val intent = contactEditorIntent(mode, name, phone, email)
        if (intent.resolveActivity(context.packageManager) == null)
            return ToolExecutionResult.Rejected(call.id, call.functionName,
                "No installed contacts editor handles this action", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        return try {
            context.startActivity(intent)
            ToolExecutionResult.Success(call.id, call.functionName,
                ToolResultEnvelope.success(actionTruth = ActionTruth.DISPATCHED,
                    details = "Contact editor opened; the user must review and save or cancel. No contact change confirmed."),
                ActionTruth.DISPATCHED)
        } catch (_: ActivityNotFoundException) {
            ToolExecutionResult.Rejected(call.id, call.functionName,
                "Contacts editor unavailable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        } catch (_: SecurityException) {
            ToolExecutionResult.Rejected(call.id, call.functionName,
                "Android denied contact editor access", ToolRejectionKind.PERMISSION_REQUIRED)
        }
    }

    internal fun contactEditorIntent(mode: String, name: String = "", phone: String = "", email: String = ""): Intent {
        val intent = if (mode == "create") Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
        } else Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        }
        if (phone.isNotBlank()) intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        if (email.isNotBlank()) intent.putExtra(ContactsContract.Intents.Insert.EMAIL, email)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun hasContactsPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun isNumber(text: String): Boolean =
        text.startsWith("+") || text.all { it.isDigit() || it in " -()+" }

    companion object {
        /** Strips formatting but preserves a leading international prefix. */
        fun canonicalDialNumber(number: String): String {
            val trimmed = number.trim()
            val plus = if (trimmed.startsWith("+")) "+" else ""
            return plus + trimmed.filter { it.isDigit() }
        }

        /**
         * Uri.parse keeps the literal number; Uri.fromParts would percent-encode a leading
         * "+" and dial something else. The test asserts the resolved number is present.
         */
        fun dialUriFor(number: String): Uri = Uri.parse("tel:$number")
    }
}
