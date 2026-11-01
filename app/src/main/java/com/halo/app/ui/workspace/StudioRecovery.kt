package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioProject

/** Presentation of real owner results. A retry never gains a new revision implicitly. */
enum class StudioOperation(val failureText: Int) {
    OPEN(R.string.studio_failed_open), CREATE(R.string.studio_failed_create),
    EDIT(R.string.studio_failed_edit), SAVE(R.string.studio_failed_save),
    COPY(R.string.studio_failed_copy), RENAME(R.string.studio_failed_rename),
    HISTORY(R.string.studio_failed_history), RESTORE(R.string.studio_failed_restore),
    IMPORT(R.string.studio_failed_import), EXPORT(R.string.studio_failed_export),
    INSERT(R.string.studio_failed_insert), CAMERA(R.string.studio_failed_camera),
    AI_REVIEW(R.string.studio_failed_ai), DISCARD(R.string.studio_failed_discard),
}

enum class StudioFailureReason { FAILED, CHANGED, ACCESS, INTERRUPTED }
enum class StudioRetainedWork { UNKNOWN, DRAFT, SAVED }

data class StudioFailure(
    val operation: StudioOperation,
    val reason: StudioFailureReason = StudioFailureReason.FAILED,
    val retained: StudioRetainedWork = StudioRetainedWork.UNKNOWN,
    val canRetry: Boolean = false,
)

internal fun studioRetainedWork(project: StudioProject?): StudioRetainedWork = when {
    project == null -> StudioRetainedWork.UNKNOWN
    project.hasUnsavedChanges -> StudioRetainedWork.DRAFT
    else -> StudioRetainedWork.SAVED
}

@Composable
internal fun studioFailureText(failure: StudioFailure): String = buildString {
    if (failure.reason != StudioFailureReason.INTERRUPTED) append(stringResource(failure.operation.failureText))
    append(" ")
    append(stringResource(when (failure.reason) {
        StudioFailureReason.CHANGED -> R.string.studio_recovery_changed
        StudioFailureReason.ACCESS -> R.string.studio_recovery_access
        StudioFailureReason.INTERRUPTED -> R.string.studio_recovery_interrupted
        StudioFailureReason.FAILED -> R.string.studio_recovery_failed
    }))
    append(" ")
    append(stringResource(when (failure.retained) {
        StudioRetainedWork.DRAFT -> R.string.studio_recovery_draft
        StudioRetainedWork.SAVED -> R.string.studio_recovery_saved
        StudioRetainedWork.UNKNOWN -> R.string.studio_recovery_unknown
    }))
}

@Composable
internal fun StudioRecoveryNotice(state: StudioUiState, retry: () -> Unit, dismiss: () -> Unit) {
    val failure = state.failure ?: return
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(studioFailureText(failure), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failure.canRetry) TextButton(onClick = retry, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.studio_retry)) }
            TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.studio_recovery_dismiss))
            }
        }
    }
}
