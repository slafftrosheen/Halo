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
    private val mutations = Any()
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
        val generation = synchronized(mutations) {
            val next = generations[index].incrementAndGet()
            mutable.update { if (dark) it.copy(dark = palette) else it.copy(light = palette) }
            next
        }
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
            synchronized(mutations) {
                generations[if (dark) 1 else 0].incrementAndGet()
                mutable.update { if (dark) it.copy(dark = palette) else it.copy(light = palette) }
            }
            com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(context)
        }
    }
    /** Apply both local light/dark drafts in a single palette-owner disk transaction. */
    suspend fun selectPalettesPersisted(palettes: HaloPalettes) = withContext(scope.coroutineContext.minusKey(Job)) {
        loaded.await()
        writes.withLock {
            val editor = disk().edit()
            listOf("light" to palettes.light, "dark" to palettes.dark).forEach { (prefix, palette) ->
                editor.putString("$prefix.accent", palette.accent.name)
                    .putString("$prefix.tone", palette.tone.name)
                    .putString("$prefix.background", palette.background.name)
            }
            check(editor.commit()) { "Could not save appearance" }
            synchronized(mutations) {
                generations.forEach { it.incrementAndGet() }
                mutable.value = palettes
            }
            com.halo.app.nativeactions.HaloNativeWidget.refreshAsync(context)
        }
    }

    /** Called by the mode owner on IO, with its write lock held (mode -> palettes lock order).
     * No preview state is published until both disk writes and concurrency checks succeed.
     * Optimistic select() calls can still arrive: their generations abort this Apply and
     * their queued durable writes run after compensation releases this lock.
     */
    internal suspend fun applyWithModePersisted(
        expected: HaloPalettes,
        requested: HaloPalettes,
        writeMode: suspend () -> Unit,
        publishMode: () -> Unit,
    ) {
        loaded.await()
        writes.withLock {
            val originalDisk = diskSnapshot()
            val before = synchronized(mutations) {
                check(mutable.value == expected) { "Appearance changed; review the current choices" }
                generations.map { it.get() }
            }
            var attempted = false
            try {
                currentCoroutineContext().ensureActive()
                attempted = true // commit(false) or an exception can still leave storage uncertain.
                check(writePair(requested)) { "Could not save appearance" }
                writeMode()
                currentCoroutineContext().ensureActive()
                synchronized(mutations) {
                    check(generations.map { it.get() } == before) { "Appearance changed during Apply" }
                    publishMode() // Checks its own generation before changing either published snapshot.
                    generations.forEach { it.incrementAndGet() }
                    mutable.value = requested
                }
            } catch (failure: Exception) {
                if (attempted) {
                    val restored = withContext(NonCancellable) { runCatching { restoreSnapshot(originalDisk) }.getOrDefault(false) }
                    if (!restored) throw AppearanceApplyException(rollbackUncertain = true, cause = failure)
                }
                throw failure
            }
        }
    }

    private fun diskSnapshot(): Map<String, String?> = buildMap {
        listOf("light", "dark").forEach { prefix ->
            listOf("accent", "tone", "background").forEach { field ->
                val key = "$prefix.$field"
                put(key, disk().getString(key, null))
            }
        }
    }

    private fun restoreSnapshot(snapshot: Map<String, String?>): Boolean {
        val editor = disk().edit()
        snapshot.forEach { (key, value) -> editor.putString(key, value) }
        return editor.commit()
    }

    private fun writePair(palettes: HaloPalettes): Boolean {
        val editor = disk().edit()
        listOf("light" to palettes.light, "dark" to palettes.dark).forEach { (prefix, palette) ->
            editor.putString("$prefix.accent", palette.accent.name)
                .putString("$prefix.tone", palette.tone.name)
                .putString("$prefix.background", palette.background.name)
        }
        return editor.commit()
    }

    internal suspend fun awaitReady() { loaded.await(); scope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
}
