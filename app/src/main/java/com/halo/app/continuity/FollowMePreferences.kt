package com.halo.app.continuity

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FollowMeOptions(val enabled: Boolean = false, val watchApprovals: Boolean = false,
    val version: String = "initial")

/** Only the two user choices are saved. Companion activity and selected destinations stay in memory. */
class FollowMePreferences(context: Context) {
    private val disk = context.applicationContext.getSharedPreferences("halo_follow_me", Context.MODE_PRIVATE)
    private val writes = Mutex()
    private val mutable = MutableStateFlow(FollowMeOptions(
        enabled = disk.getBoolean("enabled", false), watchApprovals = disk.getBoolean("watch_approvals", false),
        version = disk.getString("version", "initial") ?: "initial"))
    val state = mutable.asStateFlow()

    suspend fun update(expected: FollowMeOptions, next: FollowMeOptions, requireAdult: () -> Unit) = withContext(Dispatchers.IO) {
        writes.withLock {
            check(mutable.value == expected) { "Follow Me choices changed" }
            requireAdult()
            if (expected.enabled == next.enabled && expected.watchApprovals == next.watchApprovals) return@withLock expected
            val updated = next.copy(version = java.util.UUID.randomUUID().toString())
            check(disk.edit().putBoolean("enabled", updated.enabled).putBoolean("watch_approvals", updated.watchApprovals)
                .putString("version", updated.version).commit())
            mutable.value = updated
            updated
        }
    }
}
