package com.halo.app.interaction

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText

/** Exact app-authored metadata only. Never localize arbitrary source/target text.
 * The portable Confirmation currently retains flattened copy, not UiTargetPreview.
 * These two fixed compositions are the only preflight forms mapped here.
 */
private const val screenPrivacyAction = "Show private details in full screen"
private const val screenPrivacyConsequence = "People nearby may see personal and private cards"
private const val screenPrivacySubject = "$screenPrivacyAction $screenPrivacyConsequence"
private const val screenPrivacyDetail = "halo Screen · $screenPrivacyAction · $screenPrivacyConsequence"

private val configurationApprovalTitles = setOf(
    "Read Screen layout", "Preview Screen layout", "Apply Screen layout", "Change Screen settings",
    "Undo Screen layout change", "Save Screen preset", "Remove Screen preset", "List saved devices",
    "Read saved device", "Read device preferences", "Change device image quality", "Retry saved device",
    "Connect saved device", "Disconnect saved device", "Change automatic device connection", "Forget saved device",
    "List notification settings apps", "Read app notification policy", "Change app notification policy",
    "Read digest delivery settings", "Change digest delivery settings", "Read mode profile", "Edit mode profile",
    "Read voice settings", "Change voice settings", "Read learning settings", "Change learning settings",
    "List saved routines", "Read saved routine", "Read routine progress", "Change routine scheduling",
    "Save routine changes", "Delete saved routine", "Run saved routine", "List answer providers",
    "Read provider readiness", "Refresh provider model list", "Check provider connection",
    "Read active study lesson", "Change active study lesson",
    "Read Canvas review status", "Save image color palette", "Prepare local transcription for review",
    "Save reviewed Canvas text", "Edit Canvas review draft", "Discard Canvas review draft", "Export Canvas to a note",
    "List local speech models", "Manage local speech model",
)

@Composable
internal fun approvalSubjectCopy(subject: String): String = when (subject) {
    screenPrivacySubject -> uiText(screenPrivacyAction) + " " + uiText(screenPrivacyConsequence)
    screenPrivacyAction -> uiText(screenPrivacyAction)
    in configurationApprovalTitles -> uiText(subject)
    else -> subject
}

@Composable
internal fun approvalDetailCopy(detail: String, subject: String): String {
    if (subject != screenPrivacySubject && subject != screenPrivacyAction) return detail
    return when (detail) {
        screenPrivacyDetail -> "halo Screen · " + uiText(screenPrivacyAction) + " · " + uiText(screenPrivacyConsequence)
        screenPrivacyConsequence -> uiText(screenPrivacyConsequence)
        else -> detail
    }
}
