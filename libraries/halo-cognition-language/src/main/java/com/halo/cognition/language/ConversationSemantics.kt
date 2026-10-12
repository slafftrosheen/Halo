package com.halo.cognition.language

/** Conservative anchored language cues. Mentioning an app or action is not an instruction. */
object ConversationSemantics {
    private val requestPrefix = Regex("^(?:(?:can|could|would|will) you\\s+(?:please\\s+)?|please\\s+)", RegexOption.IGNORE_CASE)
    fun commandText(text: String): String = text.trim().replaceFirst(requestPrefix, "").trimEnd('.', '!', '?')
    private val modelOnly = Regex("\\b(?:don['’]?t|do not) (?:search|use tools)\\b|answer from your model", RegexOption.IGNORE_CASE)
    private val explanation = Regex("^(?:how|why|what|who|when|where|explain|describe|tell me (?:about|how)|write|translate|summari[sz]e|give me|help me brainstorm)\\b", RegexOption.IGNORE_CASE)
    private val workspace = Regex("^(?:find|search|use|read|look up) my (?:notes?|notebook|documents?|files?|memories)\\b", RegexOption.IGNORE_CASE)
    private val screen = Regex("^(?:read this screen and|press|tap|click|scroll|swipe)\\b", RegexOption.IGNORE_CASE)
    /** Composite requests with unresolved app goals must not degrade into a single app name. */
    private val appWorkflow = Regex(
        "^(?:open|launch|start)\\s+.+?\\s+(?:and\\s+then|then|and)\\s+(?:find|search|look\\s+for|tap|click|press|type|enter|scroll|select|choose|read|show)\\b|" +
            "^(?:find|search|look\\s+for)\\s+.+?\\s+(?:in|inside|within)\\s+.+\\b|" +
            "^search\\s+(?:in|inside)\\s+.+?\\s+for\\s+.+$",
        RegexOption.IGNORE_CASE
    )
    private val device = Regex("^(?:turn (?:on|off) (?:the )?(?:flashlight|torch)|send .+ (?:a )?message)\\b", RegexOption.IGNORE_CASE)
    private val flexibleDevice = Regex("^(?:set|change|adjust|increase|decrease|lower|raise) (?:my |the )?(?:phone |screen |media )?(?:volume|brightness|ringer)\\b|^(?:turn|switch) (?:on|off) (?:my |the )?(?:wifi|wi-fi|bluetooth)\\b|^reply to (?:the |my )?(?:notification|message)\\b|^dismiss (?:the |all |my )?notifications?\\b|^copy .+ to (?:the )?clipboard\\b", RegexOption.IGNORE_CASE)
    fun answerFromModel(text: String): Boolean = modelOnly.containsMatchIn(text.replace('\u2019', '\''))
    fun isExplanation(text: String): Boolean = explanation.containsMatchIn(text.trim()) ||
        Regex("^search engines?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())
    fun actionReason(text: String): ToolActionReason? {
        if (answerFromModel(text)) return null
        val command = commandText(text)
        return when {
            appWorkflow.containsMatchIn(command) -> ToolActionReason.SCREEN_OPERATION
            workspace.containsMatchIn(command) -> ToolActionReason.WORKSPACE_LOOKUP
            screen.containsMatchIn(command) -> ToolActionReason.SCREEN_OPERATION
            device.containsMatchIn(command) -> ToolActionReason.ACTION_INTENT
            flexibleDevice.containsMatchIn(command) -> ToolActionReason.ACTION_INTENT
            else -> null
        }
    }
}
