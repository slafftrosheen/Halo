package com.halo.runtime.cognition.apps

import com.halo.cognition.language.NativeMessageIntent
import java.time.Instant

sealed interface MessageRecipientResolution {
    data class Found(val recipient: String) : MessageRecipientResolution
    data class Choices(val names: List<String>) : MessageRecipientResolution
    data object Missing : MessageRecipientResolution
}
sealed interface MessageDraftResolution {
    data class Ready(val intent: NativeMessageIntent) : MessageDraftResolution
    data class NeedsChoice(val prompt: String) : MessageDraftResolution
    data class Missing(val prompt: String) : MessageDraftResolution
}
/** The entire draft and candidate list are short-lived memory; neither is journaled. */
class MessageDraftResolver(private val resolve: suspend (String) -> MessageRecipientResolution,
    private val now: () -> Instant = Instant::now) {
    private data class Pending(val draft: NativeMessageIntent, val choices: List<String>, val expires: Instant)
    private var pending: Pending? = null
    suspend fun prepare(intent: NativeMessageIntent): MessageDraftResolution {
        pending = null
        if (intent.channel == "email") return MessageDraftResolution.Ready(intent)
        return when (val result = resolve(intent.recipient)) {
            is MessageRecipientResolution.Found -> { pending = null; MessageDraftResolution.Ready(intent.copy(recipient = result.recipient)) }
            is MessageRecipientResolution.Choices -> {
                val names = result.names.distinct().take(6)
                pending = Pending(intent, names, now().plusSeconds(120))
                MessageDraftResolution.NeedsChoice("Which contact? " + names.mapIndexed { i, name -> "${i + 1}. ${name.take(100)}" }.joinToString("; "))
            }
            MessageRecipientResolution.Missing -> MessageDraftResolution.Missing("Please provide an exact contact or phone number.")
        }
    }
    suspend fun choose(text: String): MessageDraftResolution? {
        val current = pending ?: return null
        if (!now().isBefore(current.expires)) { pending = null; return MessageDraftResolution.Missing("That draft expired. Please compose it again.") }
        val normalized = text.lowercase().trim()
        val index = when {
            normalized in setOf("the first one", "first one", "choose 1") -> 0
            normalized in setOf("the second one", "second one", "choose 2") || normalized.startsWith("no, the other ") -> 1
            normalized.startsWith("choose ") -> current.choices.indexOfFirst { it.equals(text.substringAfter(' '), true) }
            else -> return null
        }
        val name = current.choices.getOrNull(index) ?: return MessageDraftResolution.Missing("Please choose one of the listed contacts.")
        return prepare(current.draft.copy(recipient = name))
    }
    fun clear() { pending = null }
}
