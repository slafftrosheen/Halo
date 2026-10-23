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
internal fun rememberHomeFeedFollowing(scroll: ScrollState, conversationKey: String?): MutableState<Boolean> {
    val following = remember(conversationKey) { mutableStateOf(true) }
    LaunchedEffect(conversationKey, scroll) {
        var previousPosition = scroll.value
        snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.isScrollInProgress) }
            .collect { (position, bottom, scrolling) ->
                if (scrolling && position < previousPosition) following.value = false
                else if (position == bottom) following.value = true
                previousPosition = position
                if (!scrolling && following.value && bottom > position) scroll.scrollTo(bottom)
            }
    }
    return following
}
