package com.halo.app.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.runtime.cognition.context.ContextualNextActionKind

/** Runtime action kinds are portable; their visible copy belongs to the Android host. */
@Composable
internal fun contextualNextActionLabel(kind: ContextualNextActionKind): String = stringResource(when (kind) {
    ContextualNextActionKind.ASK_FOLLOW_UP -> R.string.next_action_follow_up
    ContextualNextActionKind.SAVE_RESULT -> R.string.next_action_save_result
    ContextualNextActionKind.STUDY_SOURCE -> R.string.next_action_study_source
    ContextualNextActionKind.CREATE_NOTE -> R.string.next_action_create_note
    ContextualNextActionKind.REVIEW_MEMORY -> R.string.next_action_review_memory
    ContextualNextActionKind.OPEN_SOURCE -> R.string.next_action_open_source
    ContextualNextActionKind.CONTINUE_STUDY -> R.string.next_action_continue_study
    ContextualNextActionKind.REVIEW_RELATED_RUN -> R.string.next_action_review_task
})
