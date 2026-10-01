package com.halo.app.ui.theme

import android.content.Context
import com.halo.app.ui.shell.HaloScreenCatalog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AssistantPreferencesState(val fontScale: Float = 0f, val startScreen: String = "home")

/** App-only preferences. Persist before publishing; tools can verify a real write. */
class AssistantPreferences(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AssistantPreferencesState())
    val state = mutableState.asStateFlow()
    private fun disk() = context.getSharedPreferences("halo_assistant_preferences", Context.MODE_PRIVATE)
    private val loaded = scope.async {
        val preferences = disk()
        val scale = preferences.getFloat("fontScale", 0f).takeIf { it == 0f || it.isFinite() && it in 0.45f..1.5f } ?: 0f
        val screen = preferences.getString("startScreen", "home").takeIf { it in HaloScreenCatalog.screens } ?: "home"
        mutableState.value = AssistantPreferencesState(scale, screen)
    }
    suspend fun get(): AssistantPreferencesState { loaded.await(); return state.value }
    suspend fun update(fontScale: Float?, startScreen: String?): AssistantPreferencesState = withContext(Dispatchers.IO) {
        require(fontScale == null || fontScale == 0f || fontScale.isFinite() && fontScale in 0.45f..1.5f)
        require(startScreen == null || startScreen in HaloScreenCatalog.screens)
        loaded.await()
        mutex.withLock {
            val next = state.value.copy(fontScale = fontScale ?: state.value.fontScale, startScreen = startScreen ?: state.value.startScreen)
            check(disk().edit().putFloat("fontScale", next.fontScale).putString("startScreen", next.startScreen).commit()) {
                "Could not persist Halo preferences"
            }
            mutableState.value = next
            next
        }
    }
}
