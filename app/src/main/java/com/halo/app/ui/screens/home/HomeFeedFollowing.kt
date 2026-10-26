package com.halo.app.ui.screens.home

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Presentation-only scroll memory. Neither history inspection nor restoration changes a run. */
@Composable
internal fun rememberHomeFeedFollowing(scroll: ScrollState, conversationKey: String?, recoveryRequest: String? = null): MutableState<Boolean> {
    val positions = rememberSaveable { hashMapOf<String, Int>() }
    val modes = rememberSaveable { hashMapOf<String, Boolean>() }
    val key = conversationKey.orEmpty()
    val following = remember(key) { mutableStateOf(modes[key] ?: true) }
    // Recovery is rendered at the live tail. It must never seize a reader's position.
    LaunchedEffect(key, scroll) {
        if (!following.value) positions[key]?.let { scroll.scrollTo(it) }
        var previousPosition = scroll.value
        snapshotFlow { FeedScrollObservation(scroll.value, scroll.maxValue, scroll.isScrollInProgress, following.value) }
            .collect { (position, bottom, scrolling, live) ->
                if (scrolling && position < previousPosition) following.value = false
                else if (scrolling && position == bottom) following.value = true
                previousPosition = position
                positions[key] = position
                modes[key] = following.value
                if (!scrolling && live && following.value && bottom > position) scroll.scrollTo(bottom)
            }
    }
    DisposableEffect(key, scroll) {
        onDispose { positions[key] = scroll.value; modes[key] = following.value }
    }
    return following
}

private data class FeedScrollObservation(val position: Int, val bottom: Int, val scrolling: Boolean, val live: Boolean)
