package com.halo.app.ui.zone6

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.halo.app.R
import com.halo.app.ui.presence.LocalShellPageActions
import com.halo.app.ui.presence.LocalShellPageOwnerFilter
import com.halo.app.ui.presence.ShellPageProjection

private val appearanceActions = setOf(
    "edit-home", "page.settings.look", "page.study.edit-learning-view",
)

internal fun ShellPageProjection?.editPageAction() = this?.actions?.firstOrNull { action ->
    action.id in appearanceActions || action.id.substringAfterLast('.') in
        setOf("appearance", "arrange", "layout", "customize", "preferences")
}

/** Resolve the current owner again on long press, so a title on an exiting page never acts on the next page. */
@Composable
internal fun Modifier.haloEditablePageTitle(): Modifier {
    if (LocalHaloPageGuidance.current == null) return this
    val holder = LocalShellPageActions.current ?: return this
    val allowed = LocalShellPageOwnerFilter.current
    val projection = holder.value?.takeIf { allowed(it.owner) } ?: return this
    val action = projection.editPageAction()?.takeIf { it.enabled } ?: return this
    val currentAllowed by rememberUpdatedState(allowed)
    val open = {
        holder.value === projection && currentAllowed(projection.owner) && projection.invoke(action.id)
    }
    val latestOpen by rememberUpdatedState(open)
    val editLabel = stringResource(R.string.coach_edit_page)
    return pointerInput(projection, action.id) {
        detectTapGestures(onLongPress = { latestOpen() })
    }.semantics {
        customActions = listOf(CustomAccessibilityAction(editLabel) { latestOpen() })
    }
}
