package com.halo.app.ui.screens.kids

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class KidsSky { STARS, SEA, GARDEN, MOON }

internal data class KidsPresentationSettings(
    val sky: KidsSky = KidsSky.STARS,
    val allowedSkies: Set<KidsSky> = KidsSky.entries.toSet(),
    val preReader: Boolean = true,
    val readAloud: Boolean = true,
    val still: Boolean = false,
    val handDemos: Boolean = true,
)

/** Family presentation and adult-action authority. Processing follows the app-wide mode. */
class KidsPreferences(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val actionRevision = java.util.concurrent.atomic.AtomicLong(0)
    private val changing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val mutable = MutableStateFlow(false)
    private val ready = MutableStateFlow(false)
    val loadedState = ready.asStateFlow()
    private val error = MutableStateFlow<String?>(null)
    val loadError = error.asStateFlow()
    val enabled = mutable.asStateFlow()
    internal val pictureTools by lazy { com.halo.app.ui.toolkit.HaloKidsPictureTools(context) }
    private val presentation = MutableStateFlow(KidsPresentationSettings())
    internal val appearance = presentation.asStateFlow()
    private fun disk() = context.getSharedPreferences("halo_kids_ui", Context.MODE_PRIVATE)
    private val loaded = scope.async {
        try {
            val disk = disk()
            val allowed = disk.getStringSet("allowed_skies", null)?.let { names ->
                KidsSky.entries.filterTo(linkedSetOf()) { it.name in names }
            }?.takeIf { it.isNotEmpty() } ?: KidsSky.entries.toSet()
            val sky = allowed.firstOrNull { it.name == disk.getString("sky", null) } ?: allowed.first()
            presentation.value = KidsPresentationSettings(sky, allowed,
                preReader = disk.getBoolean("pre_reader", true),
                readAloud = disk.getBoolean("read_aloud", true),
                still = disk.getBoolean("still", false),
                handDemos = disk.getBoolean("hand_demos", true))
            mutable.value = disk.getBoolean("enabled", false)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = true; error.value = "Could not read Kids settings. Local mode stays on until a grown-up can save the settings." }
        finally { ready.value = true }
    }
    internal suspend fun chooseSky(sky: KidsSky) = withContext(Dispatchers.IO) {
        loaded.await()
        writes.withLock {
            val settings = presentation.value
            check(ready.value && error.value == null && sky in settings.allowedSkies) { "Sky is unavailable" }
            check(disk().edit().putString("sky", sky.name).commit()) { "Could not save sky" }
            presentation.value = settings.copy(sky = sky)
        }
    }

    /** The verified grown-up sheet commits a single draft; the child's current allowed sky is kept. */
    internal suspend fun applyGrownUpAppearance(draft: KidsPresentationSettings) = withContext(Dispatchers.IO) {
        loaded.await()
        writes.withLock {
            val allowed = draft.allowedSkies.toSet()
            require(allowed.isNotEmpty())
            val selected = presentation.value.sky.takeIf { it in allowed } ?: KidsSky.entries.first { it in allowed }
            check(disk().edit().putStringSet("allowed_skies", allowed.mapTo(linkedSetOf()) { it.name })
                .putString("sky", selected.name).putBoolean("pre_reader", draft.preReader)
                .putBoolean("read_aloud", draft.readAloud).putBoolean("still", draft.still)
                .putBoolean("hand_demos", draft.handDemos).commit()) {
                "Could not save Kids appearance"
            }
            presentation.value = draft.copy(sky = selected, allowedSkies = allowed)
        }
    }
    suspend fun getEnabled(): Boolean { loaded.await(); return enabled.value }
    fun adultActionRevision(): Long = actionRevision.get().also { requireAdultAction(it) }
    fun presentationRevision(): Long {
        check(ready.value && !changing.get()) { "Family presentation is changing" }
        return actionRevision.get()
    }
    fun requirePresentation(revision: Long) { check(presentationRevision()==revision) { "Family presentation changed" } }
    fun requireAdultAction(revision: Long) {
        check(ready.value && !mutable.value && !changing.get() && actionRevision.get() == revision) { "Adult action authority changed" }
    }
    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        loaded.await()
        writes.withLock {
            changing.set(true); actionRevision.incrementAndGet()
            try {
                check(disk().edit().putBoolean("enabled", value).commit()) { "Could not save Kids mode" }
                mutable.value = value
                error.value = null
            } finally { changing.set(false) }
        }
    }
}
