package com.halo.app.ui.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

/** Only the resumed UI owns a request; claim it before navigation to avoid duplicate hosts. */
internal object HaloScreenRequests {
    fun claim(pending: MutableStateFlow<String?>, resumed: Boolean, expectedValue: String? = pending.value): String? {
        if (!resumed) return null
        val screen = expectedValue ?: return null
        if (!pending.compareAndSet(screen, null)) return null
        return screen.takeIf { it in HaloScreenCatalog.screens }
    }

    /** Collect requests directly: a repeated route must not depend on Compose state/key changes. */
    suspend fun collectResumed(lifecycle: Lifecycle, pending: MutableStateFlow<String?>, navigate: (String) -> Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            pending.collect { observed ->
                if (observed != null) {
                    claim(pending, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), observed)?.let(navigate)
                }
            }
        }
    }
}
