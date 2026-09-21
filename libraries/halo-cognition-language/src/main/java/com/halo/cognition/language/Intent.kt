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
data object SearchIntent : Intent
data object QuestionIntent : Intent
data object ConversationIntent : Intent
