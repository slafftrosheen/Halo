package com.halo.app.ui.shell

import com.halo.app.daily.DailyAssistantAction
import java.net.URLEncoder

/** AppShell's navigation mapping; the projector never carries route strings. */
internal fun dailyOwnerRoute(action: DailyAssistantAction): String {
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    return when (action) {
        DailyAssistantAction.OpenInbox -> "notifications"
        is DailyAssistantAction.OpenActivity -> action.runId?.let { "activity?runId=${encode(it)}" } ?: "activity"
        DailyAssistantAction.OpenLearningSuggestions -> "notebook/memory?section=Suggestions"
        is DailyAssistantAction.OpenStudy -> "notebook/study/${encode(action.sessionId)}?review=${action.review}"
        is DailyAssistantAction.OpenAutomation -> action.routineId?.let { "automation?routineId=${encode(it)}" } ?: "automation"
        DailyAssistantAction.OpenDevices -> "devices"
        DailyAssistantAction.OpenVoice -> "voice_settings"
        is DailyAssistantAction.OpenProvider -> action.providerId?.let { "settings/intelligence/providers/${encode(it)}" } ?: "provider_setup"
    }
}
