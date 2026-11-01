package com.halo.app.ui.zone5

import com.halo.app.R

/** Presentation over the live lease: no commands or callbacks are owned by these groups. */
internal enum class LocalActionGroup(val label: Int) {
    CONVERSATION(R.string.zone5_group_conversation), ATTACHMENTS(R.string.zone5_group_attachments),
    CARDS(R.string.zone5_group_cards), LAYOUT(R.string.zone5_group_layout), DAY(R.string.zone5_group_day),
    SEARCH(R.string.zone5_group_search), SECTIONS(R.string.zone5_group_sections),
    CONNECTIONS(R.string.zone5_group_connections), MANAGEMENT(R.string.zone5_group_management),
    REVIEW(R.string.zone5_group_review), HISTORY(R.string.zone5_group_history),
    HELP(R.string.zone5_group_help), TASK(R.string.zone5_group_task)
}
internal fun localActionGroup(family: String?, id: String): LocalActionGroup {
    val action = id.removePrefix("page:").substringAfterLast('.')
    return when (family) {
        "home" -> when (action) {
            "attach" -> LocalActionGroup.ATTACHMENTS
            "history" -> LocalActionGroup.HISTORY
            "today" -> LocalActionGroup.DAY
            else -> LocalActionGroup.CONVERSATION
        }
        "screen" -> when (action) {
            "add" -> LocalActionGroup.CARDS
            "customize", "appearance", "settings", "ambient" -> LocalActionGroup.LAYOUT
            else -> LocalActionGroup.DAY
        }
        "settings", "advanced_settings" -> when (action) {
            "search" -> LocalActionGroup.SEARCH
            "setup", "about", "report" -> LocalActionGroup.HELP
            else -> LocalActionGroup.SECTIONS
        }
        "devices" -> when (action) {
            "add" -> LocalActionGroup.CONNECTIONS
            "help" -> LocalActionGroup.HELP
            else -> LocalActionGroup.MANAGEMENT
        }
        "activity", "notifications" -> when (action) {
            "review" -> LocalActionGroup.REVIEW
            "history" -> LocalActionGroup.HISTORY
            else -> LocalActionGroup.HELP
        }
        else -> LocalActionGroup.TASK
    }
}
