package com.halo.runtime.cognition.turn

object TtsSanitizer {
    fun sanitize(text: String): String {
        // Remove <think>...</think> blocks
        val thinkRegex = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
        var cleaned = text.replace(thinkRegex, "")
        
        // Remove markdown code blocks if they contain JSON/XML/tool envelopes
        val codeBlockRegex = Regex("```(?:json|xml|tool|json_schema)?\\s*.*?```", RegexOption.DOT_MATCHES_ALL)
        cleaned = cleaned.replace(codeBlockRegex, "")
        
        // Remove markdown formatting
        cleaned = cleaned.replace(Regex("[*]{1,2}"), "")
        cleaned = cleaned.replace(Regex("`"), "")
        
        // Remove any remaining raw json fragments if they exist by themselves (heuristic)
        if (cleaned.trim().startsWith("{") && cleaned.trim().endsWith("}")) {
            return ""
        }
        
        return cleaned.trim()
    }
}
