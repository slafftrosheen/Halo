package com.halo.app.everyday

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** A nested effect rechecks the exact suspended owner as well as its mode and restore lease. */
object EverydayEffectAuthority {
    private class Lease(val verify: suspend () -> Unit) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Lease>
    }
    suspend fun <T> run(verify: suspend () -> Unit, action: suspend () -> T): T {
        val parent = currentCoroutineContext()[Lease]
        val lease = Lease { EverydayWriteAuthority.requireCurrent(); parent?.verify?.invoke(); verify() }
        lease.verify()
        return withContext(lease) { action() }
    }
    suspend fun requireCurrent() {
        EverydayWriteAuthority.requireCurrent()
        currentCoroutineContext()[Lease]?.verify?.invoke()
    }
}
