package com.halo.app.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HaloVisualOptions(val higherContrast: Boolean = false, val lessMotion: Boolean = false, val guideLight: Boolean = true)
val LocalHaloAccentInk = staticCompositionLocalOf { Color(0xFF047A5E) }

/** App-wide options are restored on IO; writes cannot race the initial restore. */
class VisualPreferences private constructor(context: Context) {
    private val disk = context.applicationContext.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(HaloVisualOptions())
    val state = mutable.asStateFlow()
    private val loaded = scope.async {
        mutable.value = HaloVisualOptions(disk.getBoolean("contrast", false), disk.getBoolean("motion", false), disk.getBoolean("guide", true))
    }
    suspend fun update(transform: (HaloVisualOptions) -> HaloVisualOptions) = withContext(Dispatchers.IO) {
        loaded.await()
        mutex.withLock {
            val next = transform(mutable.value)
            check(disk.edit().putBoolean("contrast", next.higherContrast).putBoolean("motion", next.lessMotion)
                .putBoolean("guide", next.guideLight).commit()) { "Unable to save visual preferences" }
            mutable.value = next
        }
    }
    companion object {
        @Volatile private var instance: VisualPreferences? = null
        fun get(context: Context): VisualPreferences = instance ?: synchronized(this) {
            instance ?: VisualPreferences(context).also { instance = it }
        }
    }
}

@Composable fun rememberVisualPreferences(): HaloVisualOptions {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) { VisualPreferences.get(context) }
    return preferences.state.collectAsStateWithLifecycle().value
}
