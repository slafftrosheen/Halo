package com.halo.app.everyday

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/** An explicit UI/tool action carries revocable authority through suspension into its durable owner. */
object EverydayWriteAuthority {
    private val current = ThreadLocal<(() -> Unit)?>()
    private val maintenance=java.util.concurrent.atomic.AtomicBoolean()
    private val revision=java.util.concurrent.atomic.AtomicLong()
    fun beginMaintenance(): AutoCloseable {
        check(maintenance.compareAndSet(false,true)) {"Everyday restore is already running"}
        revision.incrementAndGet()
        EverydayReadAuthority.invalidate()
        val closed=java.util.concurrent.atomic.AtomicBoolean()
        return AutoCloseable {if(closed.compareAndSet(false,true)) maintenance.set(false)}
    }
    suspend fun <T> run(check: () -> Unit, action: suspend () -> T): T {
        val admitted=revision.get()
        val parent=current.get()
        val verify: ()->Unit={parent?.invoke();check();kotlin.check(!maintenance.get() && revision.get()==admitted) {"Everyday data changed during restore"}}
        verify()
        return withContext(current.asContextElement(verify)) { action() }
    }
    /** Background owners retain their existing independent policy; only scoped actions carry this lease. */
    fun requireCurrent() { check(!maintenance.get()) {"Everyday restore is in progress"};current.get()?.invoke() }
}
