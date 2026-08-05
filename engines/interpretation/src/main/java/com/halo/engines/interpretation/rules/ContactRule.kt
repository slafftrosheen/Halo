package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.RuleMatch
import com.halo.engines.interpretation.model.PersonEntity
import com.halo.engines.speech.SpeechRecognizedPayload

class ContactRule(
    private val knownContacts: Set<String> = emptySet()
) : InterpretationRule {
    override val priority: Int = 700

    override suspend fun match(context: InterpretationContext): RuleMatch? {
        val transcript = transcript(context) ?: return null
        val matched = commandPattern.find(transcript) ?: return null
        val contactAndTime = matched.groupValues[1]
        val timeIndex = timePattern.find(contactAndTime)?.range?.first ?: contactAndTime.length
        val requestedContact = contactAndTime
            .substring(0, timeIndex)
            .trim(' ', ',', '.', '!', '?')
            .takeIf { it.isNotBlank() }
            ?: return null
        val contact = resolveContact(requestedContact, context) ?: return null
        val isKnown = knownContacts.any { it.equals(contact, ignoreCase = true) }

        return RuleMatch(
            confidence = if (isKnown) 0.99f else 0.9f,
            entities = listOf(PersonEntity(contact, if (isKnown) 0.99f else 0.9f)),
            consumedTokens = requestedContact.tokenCount(),
            diagnostics = "Extracted contact $contact"
        )
    }

    private suspend fun resolveContact(
        requestedContact: String,
        context: InterpretationContext
    ): String? {
        if (requestedContact.lowercase() in pronouns) {
            return context.workingMemory.focusedPerson?.name
        }

        return requestedContact.titlecaseWords()
    }

    private fun transcript(context: InterpretationContext): String? =
        (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun String.tokenCount(): Int =
        trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun String.titlecaseWords(): String =
        split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { character -> character.titlecase() }
            }

    private companion object {
        val commandPattern = Regex(
            "^\\s*(?:call|text|message)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val timePattern = Regex(
            "\\b(?:today|tomorrow|tonight)(?:\\s+(?:at\\s+)?(?:\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|morning|afternoon|evening))?",
            RegexOption.IGNORE_CASE
        )
        val pronouns = setOf("him", "her", "them")
    }
}
