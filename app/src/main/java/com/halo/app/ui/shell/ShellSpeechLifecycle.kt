package com.halo.app.ui.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Navigation entries may stop while the native host remains visible. */
internal fun shellSpeechHostLifecycle(host: LifecycleOwner?, fallback: Lifecycle): Lifecycle = host?.lifecycle ?: fallback

/** Host foreground boundary only. Unmounting a route/composition is not backgrounding. */
internal fun bindShellSpeechLifecycle(lifecycle: Lifecycle, changingConfiguration: () -> Boolean,
    onBackground: () -> Unit): AutoCloseable {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !changingConfiguration()) onBackground()
    }
    lifecycle.addObserver(observer)
    return AutoCloseable { lifecycle.removeObserver(observer) }
}
