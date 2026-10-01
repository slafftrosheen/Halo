package com.halo.host.android.assistant

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Native app action handles stay in the Android session; models only receive opaque tokens. */
interface NativeAppActionSource {
    suspend fun list(): List<String>
    fun dispatch(index: Int)
}
data class NativeAppAction(val token: String, val name: String)
class NativeAssistantActions(private val now: () -> Long = System::currentTimeMillis) {
    private var owner: String? = null
    private var source: NativeAppActionSource? = null
    private var generation = 0L
    private var listedAt = 0L
    private var tokens = emptyMap<String, Int>()
    fun attach(owner: String, source: NativeAppActionSource) {
        this.owner = owner; this.source = source; invalidate()
    }
    fun detach(owner: String) { if (this.owner == owner) { this.owner = null; source = null; invalidate() } }
    fun invalidate() { generation++; tokens = emptyMap() }
    suspend fun list(): List<NativeAppAction> = withContext(Dispatchers.Main.immediate) {
        val current = source ?: error("Invoke Halo as the native assistant over an app first")
        val revision = generation
        tokens = emptyMap()
        val names = current.list().take(12)
        check(current === source && revision == generation) { "Native app actions changed; discover again" }
        listedAt = now()
        names.mapIndexed { index, name -> NativeAppAction(UUID.randomUUID().toString(), name.take(150)) }
            .also { actions -> tokens = actions.mapIndexed { index, action -> action.token to index }.toMap() }
    }
    suspend fun dispatch(token: String) = withContext(Dispatchers.Main.immediate) {
        val current = source ?: error("Native assistant session is unavailable")
        check(now() - listedAt in 0..30_000) { "Native action observation expired; discover again" }
        val index = tokens[token] ?: error("Native action token is stale or unknown; discover again")
        invalidate() // Never replay the same observation, including failed platform dispatch.
        current.dispatch(index)
    }
    companion object { val shared = NativeAssistantActions() }
}
