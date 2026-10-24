package com.halo.app.ui.screens.home

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

/** Any upward scroll releases following, including gestures shorter than the viewport. */
@Composable
internal fun rememberHomeFeedFollowing(scroll: ScrollState, conversationKey: String?, recoveryRequest: String? = null): MutableState<Boolean> {
    val following = remember(conversationKey) { mutableStateOf(true) }
    LaunchedEffect(conversationKey, scroll, recoveryRequest) {
        following.value = recoveryRequest == null
        if (recoveryRequest != null) scroll.scrollTo(0)
        var previousPosition = scroll.value
        snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.isScrollInProgress) }
            .collect { (position, bottom, scrolling) ->
                if (scrolling && position < previousPosition) following.value = false
                else if (position == bottom) following.value = true
                previousPosition = position
                if (!scrolling && recoveryRequest == null && following.value && bottom > position) scroll.scrollTo(bottom)
            }
    }
    return following
}
