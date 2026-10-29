package com.halo.app.screen

import android.content.res.Resources
import com.halo.app.daily.DailyAssistantItem
import com.halo.app.daily.DailyAssistantKind
import com.halo.app.language.resolveUiText

/** Only typed app-owned facts are translated. Legacy/source prose remains verbatim. */
internal object ScreenSummaryCopy {
    const val ROUTINE_ACTIONS_FINISHED = "Actions completed: %1\$d of %2\$d"

    fun summary(item: ScreenItem, resources: Resources): String {
        val facts = item.appSummary as? ScreenAppSummary.RoutineActionsFinished
        return if (item.sourceType == ScreenSourceType.AUTOMATION && facts?.valid == true)
            resolveUiText(resources, ROUTINE_ACTIONS_FINISHED, facts.completed, facts.total)
        else item.summary.orEmpty()
    }

    fun dailyTitle(item: DailyAssistantItem, resources: Resources): String =
        if (item.kind == DailyAssistantKind.STUDY_REVIEW) resolveUiText(resources, "Study review")
        else item.title
}
