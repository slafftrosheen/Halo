package com.halo.cognition.language

data class NativeOpenApplicationIntent(val application: String) : NativeControlIntent
data class NativeMessageIntent(val recipient: String, val body: String, val channel: String) : NativeControlIntent
data class NativeAppActionIntent(val action: String) : NativeControlIntent
data class NativeReferenceIntent(val text: String) : NativeControlIntent
data class NativeCommandSequenceIntent(val operations: List<Intent>) : NativeControlIntent {
    init { require(operations.size in 2..4 && operations.none { it is NativeCommandSequenceIntent }) }
}

object AppCommandParser {
    fun parse(text: String): Intent? {
        val command = text.trim().trimEnd('.', '!')
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
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val name = command.substringAfter(' ').trim()
            if (name.isNotBlank() && !lower.contains(" and ") && !lower.contains("settings") && !lower.endsWith(" alarms")) return NativeOpenApplicationIntent(name)
        }
        if (" and " in lower || ";" in lower) {
            val parts = command.split(Regex("\\s+and\\s+|;\\s*", RegexOption.IGNORE_CASE))
            if (parts.size !in 2..4) return null
            val intents = parts.map { part -> NativeCommandParser.parse(part) ?: return null }
            if (intents.any { it is NativeReferenceIntent || it is NativeClarificationIntent || it is NativeCommandSequenceIntent }) return null
            return NativeCommandSequenceIntent(intents)
        }
        return null
    }
}

data object NativeStopIntent : NativeControlIntent

data object NativeContinueIntent : NativeControlIntent

data class NativeTargetMediaIntent(val action: String, val application: String) : NativeControlIntent
