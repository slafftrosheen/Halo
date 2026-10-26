package com.halo.app.ui.theme

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

enum class HaloAccent { MINT, BLUE, LILAC, CORAL, SAGE, ROSE }
enum class HaloTone { NATURAL, WARM, COOL, PAPER, SLATE }
enum class HaloBackgroundStyle { STILL, HALO, MIST }
data class HaloPalette(val accent: HaloAccent = HaloAccent.MINT, val tone: HaloTone = HaloTone.NATURAL,
    val background: HaloBackgroundStyle = HaloBackgroundStyle.HALO)
val LocalHaloPalette = androidx.compose.runtime.staticCompositionLocalOf { HaloPalette() }
data class HaloPalettes(val light: HaloPalette = HaloPalette(), val dark: HaloPalette = HaloPalette())

/** Separate light/dark choices; no main-thread disk reads and no initial-load overwrite of user edits. */
class PalettePreferences(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val writes = Mutex()
    private val generations = arrayOf(AtomicLong(), AtomicLong())
    private val mutable = MutableStateFlow(HaloPalettes())
    val state = mutable.asStateFlow()
    private fun disk() = context.getSharedPreferences("halo_palettes", Context.MODE_PRIVATE)
    private fun read(dark: Boolean): HaloPalette {
        val prefix = if (dark) "dark" else "light"
        return HaloPalette(runCatching { HaloAccent.valueOf(disk().getString("$prefix.accent", "MINT")!!) }.getOrDefault(HaloAccent.MINT),
            runCatching { HaloTone.valueOf(disk().getString("$prefix.tone", "NATURAL")!!) }.getOrDefault(HaloTone.NATURAL),
            runCatching { HaloBackgroundStyle.valueOf(disk().getString("$prefix.background", "HALO")!!) }.getOrDefault(HaloBackgroundStyle.HALO))
    }
    private val loaded = scope.async {
        val restored = HaloPalettes(read(false), read(true))
        mutable.update { current -> HaloPalettes(
            if (generations[0].get() == 0L) restored.light else current.light,
            if (generations[1].get() == 0L) restored.dark else current.dark) }
    }
    private var lastWrite: Job? = null
    fun select(dark: Boolean, palette: HaloPalette) {
        val index = if (dark) 1 else 0
        val generation = generations[index].incrementAndGet()
        mutable.update { if (dark) it.copy(dark = palette) else it.copy(light = palette) }
        lastWrite = scope.launch {
            loaded.await()
            writes.withLock {
                if (generations[index].get() != generation) return@withLock
                val prefix = if (dark) "dark" else "light"
                if (!disk().edit().putString("$prefix.accent", palette.accent.name).putString("$prefix.tone", palette.tone.name).putString("$prefix.background", palette.background.name).commit())
                    android.util.Log.w("HaloPalette", "Could not persist colour choices")
                else com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(context)
            }
        }
    }
    /** A preview is local UI state until this durable, shared owner acknowledges Apply. */
    suspend fun selectPersisted(dark: Boolean, palette: HaloPalette) = withContext(scope.coroutineContext.minusKey(Job)) {
        loaded.await()
        writes.withLock {
            val prefix = if (dark) "dark" else "light"
            check(disk().edit().putString("$prefix.accent", palette.accent.name)
                .putString("$prefix.tone", palette.tone.name)
                .putString("$prefix.background", palette.background.name).commit()) { "Could not save appearance" }
            generations[if (dark) 1 else 0].incrementAndGet()
            mutable.update { if (dark) it.copy(dark = palette) else it.copy(light = palette) }
            com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(context)
        }
    }
    internal suspend fun awaitReady() { loaded.await(); scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
}
