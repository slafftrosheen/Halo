package com.halo.app.ui.everyday

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.halo.app.everyday.organizer.CalendarImportIssue
import com.halo.app.language.resolveUiText

internal fun calendarImportIssueText(resources: Resources, issue: CalendarImportIssue): String {
    val message=resolveUiText(resources,issue.reasonCode.message,*issue.reasonArguments.toTypedArray())
    return if(issue.seriesWithheld) resolveUiText(resources,"Series withheld: %1\$s",message) else message
}

@Composable internal fun calendarImportIssueText(issue: CalendarImportIssue): String = calendarImportIssueText(LocalResources.current,issue)
