package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationBuilder
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.blackboard.Confidence

class ContactRule(
    private val knownContacts: Set<String> = emptySet()
) : InterpretationRule {
    override val priority: Int = 700

    override suspend fun applies(
        context: InterpretationContext
    ): Boolean = transcript(context)?.let(commandPattern::containsMatchIn) == true

    override suspend fun interpret(
        context: InterpretationContext
    ): Interpretation? {
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
        val confidence = context.observation.confidence

        return InterpretationBuilder()
            .person(contact, if (isKnown) 0.99f else 0.9f)
            .slot("target", contact)
            .confidence(
                Confidence(
                    lexical = confidence,
                    contextual = if (isKnown) 0.99f else 0.85f,
                    semantic = 0.98f
                )
            )
            .build()
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
            "\\b(?:today|tomorrow|tonight)(?:\\s+at\\s+\\d{1,2}(?::\\d{2})?(?:\\s*(?:am|pm))?)?",
            RegexOption.IGNORE_CASE
        )
        val pronouns = setOf("him", "her", "them")
    }
}
