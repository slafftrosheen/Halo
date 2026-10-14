package com.halo.app.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow

/** Only the resumed UI owns a request; claim it before navigation to avoid duplicate hosts. */
internal object HaloScreenRequests {
    fun claim(pending: MutableStateFlow<String?>, resumed: Boolean): String? {
        if (!resumed) return null
        val screen = pending.value ?: return null
        if (!pending.compareAndSet(screen, null)) return null
        return screen.takeIf { it in HaloScreenCatalog.screens }
    }
}
