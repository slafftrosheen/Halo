package com.halo.app.everyday

/** An expected revoked read, distinct from storage/programming failures. */
class EverydayDisclosureRevoked(message: String) : IllegalStateException(message)

/** Revokes an in-process disclosure when canonical sharing/content authority changes during a suspended read.
 * Repository writes invalidate inside their transaction. A coherent canonical version/grant snapshot
 * is also compared before/after reading, covering readers that begin during an uncommitted WAL write.
 * A rollback may conservatively revoke a reader. Process restart cannot resume an old coroutine/lease.
 */
object EverydayReadAuthority {
    private val revision=java.util.concurrent.atomic.AtomicLong()
    fun capture(): Long=revision.get()
    fun invalidate() {revision.incrementAndGet()}
    fun requireCurrent(admitted: Long) {
        if (revision.get()!=admitted) throw EverydayDisclosureRevoked("Everyday sharing or content changed during this read")
    }
}
