package com.halo.cognition.language

data class NativeOpenApplicationIntent(val application: String) : NativeControlIntent
data class NativeMessageIntent(val recipient: String, val body: String, val channel: String) : NativeControlIntent
data class NativeAppActionIntent(val action: String) : NativeControlIntent

/** Explicit, human-scripted screen step. Never carries a coordinate or a cached target token. */
enum class NativeScreenVerb { FIND, TAP, ENTER_TEXT, SCROLL_FORWARD, SCROLL_BACKWARD }
data class NativeScreenActionIntent(
    val verb: NativeScreenVerb,
    val target: String,
    val value: String? = null,
) : NativeControlIntent
data class NativeReferenceIntent(val text: String) : NativeControlIntent
data class NativeCommandSequenceIntent(val operations: List<Intent>) : NativeControlIntent {
    init { require(operations.size in 2..4 && operations.none { it is NativeCommandSequenceIntent }) }
}

object AppCommandParser {
    /** Deictic/ordinal targets need a fresh model-assisted observation, not a fabricated label. */
    private fun needsScreenDisambiguation(label: String): Boolean =
        Regex("^(?:it|this|that|here|there|first|second|third|last|next|previous|item|button|field|input|icon|result|one)\\b",
            RegexOption.IGNORE_CASE).containsMatchIn(label.trim())

    fun parse(text: String): Intent? {
        val command = ConversationSemantics.commandText(text)
        val lower = command.lowercase()
        if (lower in setOf("what can i do here", "what can i do here?", "current app capabilities")) return NativeAppActionIntent("inspect_capabilities")
        if (lower in setOf("continue", "done", "try again")) return NativeContinueIntent
        if (lower in setOf("stop", "cancel", "never mind", "stop that")) return NativeStopIntent
        if (lower in setOf("first one", "second one", "the first one", "the second one") || lower.startsWith("choose ") || lower.startsWith("no, the other ")) return NativeReferenceIntent(command)
        if (lower in setOf("open it", "open that app", "pause it", "turn it down", "turn it up", "do that again", "again", "no, the other john") ||
            Regex("^(?:make it|set it to) \\d{1,3}(?: percent|%)?$").matches(lower)) return NativeReferenceIntent(lower)
        if (lower in setOf("turn wi-fi on", "turn wifi on", "turn wi-fi off", "turn wifi off", "open internet panel")) return OpenSettingsIntent("internet")
        if (lower == "open volume panel") return OpenSettingsIntent("volume")
        when (lower) {
            "archive this", "archive this message", "archive this thread" -> return NativeAppActionIntent("archive_current")
            "mark this unread", "mark unread" -> return NativeAppActionIntent("mark_unread")
            "go back" -> return NativeAppActionIntent("navigate_back")
        }
        Regex("^(?:text|message|email) (.{1,100}?) (?:that|saying) (.{1,4096})$", RegexOption.IGNORE_CASE).matchEntire(command)?.let {
            return NativeMessageIntent(it.groupValues[1].trim(), it.groupValues[2], if (lower.startsWith("email ")) "email" else "sms")
        }
        // A sequence is never an application name. Only explicitly recognized, bounded
        // commands are eligible; unresolved goals stay with the tool-capable route.
        val separator = Regex("\\s+(?:and\\s+then|then|and)\\s+|\\s*;\\s*", RegexOption.IGNORE_CASE)
        if (separator.containsMatchIn(command)) {
            val parts = command.split(separator).map { it.trim() }
            if (parts.size !in 2..4 || parts.any { it.isBlank() }) return null
            val intents = parts.map { part -> NativeCommandParser.parse(part) ?: return null }
            if (intents.any { it is NativeReferenceIntent || it is NativeClarificationIntent || it is NativeCommandSequenceIntent }) return null
            return NativeCommandSequenceIntent(intents)
        }
        // A scroll is safe to script only if the host finds one eligible scroller.
        // The direction is semantic: no swipe coordinates, screen coordinates or
        // scroll-count guesses enter the portable intent.
        when (lower) {
            "scroll down", "scroll forward", "scroll the page down", "scroll the screen down" ->
                return NativeScreenActionIntent(NativeScreenVerb.SCROLL_FORWARD, "current screen")
            "scroll up", "scroll backward", "scroll back up", "scroll the page up", "scroll the screen up" ->
                return NativeScreenActionIntent(NativeScreenVerb.SCROLL_BACKWARD, "current screen")
        }
        Regex("^(?:find|locate) (?:the )?(.{1,100}?) (?:on|in) (?:the )?(?:current )?screen$", RegexOption.IGNORE_CASE).matchEntire(command)?.let {
            val target = it.groupValues[1].trim()
            if (needsScreenDisambiguation(target)) return null
            return NativeScreenActionIntent(NativeScreenVerb.FIND, target)
        }
        Regex("^(?:tap|press|click) (?:the )?(.{1,100})$", RegexOption.IGNORE_CASE).matchEntire(command)?.let {
            val target = it.groupValues[1].trim()
            if (needsScreenDisambiguation(target)) return null
            return NativeScreenActionIntent(NativeScreenVerb.TAP, target)
        }
        Regex("^(?:type|enter) (.{1,2000}?) (?:into|in) (?:the )?(.{1,100})$", RegexOption.IGNORE_CASE).matchEntire(command)?.let {
            val value = it.groupValues[1].trim().trim('"')
            val target = it.groupValues[2].trim()
            if (value.isNotBlank() && target.isNotBlank() && !needsScreenDisambiguation(target))
                return NativeScreenActionIntent(NativeScreenVerb.ENTER_TEXT, target, value)
        }
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val name = command.substringAfter(' ').trim()
            if (name.isNotBlank() && !lower.contains("settings") && !lower.endsWith(" alarms")) return NativeOpenApplicationIntent(name)
        }
        return null
    }
}

data object NativeStopIntent : NativeControlIntent

data object NativeContinueIntent : NativeControlIntent

data class NativeTargetMediaIntent(val action: String, val application: String) : NativeControlIntent
