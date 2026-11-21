package com.halo.app.ui.toolkit

import com.halo.host.android.display.AndroidExternalDisplayPresenter
import java.util.UUID
import com.halo.sdk.runtime.display.ExternalDisplaySession
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HaloCaptionDisplayChoice(val token: String, val name: String, val sourceVersion: Long)
internal data class HaloCaptionDisplayStatus(val name: String, val token: String, val sourceVersion: Long)

/** An explicitly confirmed, connection-scoped view of Record captions. Nothing survives revocation. */
internal class HaloRecordCaptionDisplay(
    private val output: AndroidExternalDisplayPresenter,
    private val cleanup: CoroutineScope,
) {
    private data class Grant(val generation: Long, val ownerToken: String, val authorized: () -> Boolean)
    private var grant: Grant? = null
    private val generation = AtomicLong()
    private val dispatch = Mutex()
    private val mutableState = MutableStateFlow<HaloCaptionDisplayStatus?>(null)
    val state = mutableState.asStateFlow()

    suspend fun start(choice: HaloCaptionDisplayChoice, waiting: String, authorized: () -> Boolean): Boolean {
        val selectedGeneration = generation.incrementAndGet()
        return dispatch.withLock {
            fun allowed() = selectedGeneration == generation.get() && authorized()
            val current = output.snapshot()
            if (!allowed() || mutableState.value != null || current.activeToken != null ||
                current.revision != choice.sourceVersion || current.endpoints.none { it.token == choice.token }) return@withLock false
            val ownerToken = UUID.randomUUID().toString()
            val receipt = output.showLiveCaptions(choice.token, waiting, choice.sourceVersion, ownerToken, true, ::allowed)
            if (receipt.status != "DISPATCHED") return@withLock false
            if (!allowed()) {
                output.clearLiveCaptions(ownerToken)
                return@withLock false
            }
            grant = Grant(selectedGeneration, ownerToken, authorized)
            mutableState.value = HaloCaptionDisplayStatus(choice.name, choice.token, receipt.sourceVersion)
            true
        }
    }

    suspend fun offer(caption: String): Boolean = dispatch.withLock {
        val current = mutableState.value ?: return@withLock false
        val access = grant ?: return@withLock false
        fun allowed() = access.generation == generation.get() && access.authorized()
        if (!allowed() || output.state.value.revision != current.sourceVersion) return@withLock false
        val page = ExternalDisplaySession.paginate(caption)?.firstOrNull() ?: return@withLock true
        val receipt = output.showLiveCaptions(current.token, page, current.sourceVersion, access.ownerToken, false, ::allowed)
        if (receipt.status != "DISPATCHED") return@withLock false
        if (!allowed()) {
            output.clearLiveCaptions(access.ownerToken)
            return@withLock false
        }
        mutableState.value = current.copy(sourceVersion = receipt.sourceVersion)
        true
    }

    /** Revoke synchronously; asynchronous cleanup only clears the exact output this grant owns. */
    fun revoke() {
        generation.incrementAndGet()
        val previous = grant
        mutableState.value = null
        grant = null
        if (previous != null) cleanup.launch {
            runCatching { output.clearLiveCaptions(previous.ownerToken) }
        }
    }
}
