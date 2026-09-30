package com.halo.cognition.language

sealed interface Intent

sealed interface SystemFactIntent : Intent
data object CurrentTimeIntent : SystemFactIntent
data object CurrentDateIntent : SystemFactIntent

data object OpenIntent : Intent
data object CallIntent : Intent
data object NavigateIntent : Intent
data object PlayIntent : Intent
data object PauseIntent : Intent
data object ReminderIntent : Intent
data object SetAlarmIntent : Intent
data object ShowAlarmsIntent : Intent
data object SearchIntent : Intent
data object QuestionIntent : Intent
data object ConversationIntent : Intent

/** Positive action evidence outside the host ownership table; never an unknown fallback. */
data class ToolActionIntent(val reason: ToolActionReason) : Intent
enum class ToolActionReason { ACTION_INTENT, EXPLICIT_TOOL_REQUEST, WORKSPACE_LOOKUP, SCREEN_OPERATION }
