package com.halo.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Small persisted UI preference store.
 *
 * Appearance is process-local state backed by SharedPreferences so the theme is available
 * before Room/runtime initialization and survives process death without introducing a new
 * persistence dependency into the app shell.
 */
class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "halo_appearance",
        Context.MODE_PRIVATE
    )

    private val mutableMode = MutableStateFlow(readMode())
    val mode: StateFlow<AppearanceMode> = mutableMode.asStateFlow()

    fun setMode(mode: AppearanceMode) {
        if (mutableMode.value == mode) return
        preferences.edit().putString(KEY_MODE, mode.name).apply()
        mutableMode.value = mode
    }

    private fun readMode(): AppearanceMode {
        val stored = preferences.getString(KEY_MODE, null)
        return stored
            ?.let { value -> runCatching { AppearanceMode.valueOf(value) }.getOrNull() }
            ?: AppearanceMode.SYSTEM
    }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
