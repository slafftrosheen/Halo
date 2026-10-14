package com.halo.app.ui.mini

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Supports multiple resumed windows without retaining a paused/destroyed Activity. */
internal object HaloHostVisibility {
    private val hosts = mutableSetOf<Any>()
    private val visible = MutableStateFlow(false)
    val active = visible.asStateFlow()
    @Synchronized fun resumed(owner: Any) { hosts.add(owner); visible.value = hosts.isNotEmpty() }
    @Synchronized fun hidden(owner: Any) { hosts.remove(owner); visible.value = hosts.isNotEmpty() }
}
