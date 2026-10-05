package com.halo.app.notification

/** Process-local platform keys only; notification contents never enter this tracker. */
class NotificationLifecycleTracker {
    private val active = mutableSetOf<String>()
    @Synchronized fun seed(keys: Collection<String>) { active.clear(); active.addAll(keys) }
    @Synchronized fun posted(key: String, ownPackage: Boolean, groupSummary: Boolean): Boolean =
        active.add(key) && !ownPackage && !groupSummary
    @Synchronized fun removed(key: String) { active.remove(key) }
    @Synchronized fun clear() { active.clear() }
}

/** Always delivers the repository update; only the automation edge is deduplicated. */
internal class NotificationPostRouting(private val lifecycle: NotificationLifecycleTracker) {
    fun posted(key: String, ownPackage: Boolean, groupSummary: Boolean, deliver: (Boolean) -> Unit) {
        deliver(lifecycle.posted(key, ownPackage, groupSummary))
    }
}
