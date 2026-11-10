package com.halo.sdk.runtime.embodiment

/** Immutable host-supplied factories. Recognition never implies an installed adapter or READY. */
class DeviceAdapterRegistry<A>(registrations: List<Pair<DeviceKind, A>>) {
    private val adapters: Map<DeviceKind, A>

    init {
        require(registrations.none { it.first == DeviceKind.UNKNOWN }) { "Unknown devices cannot own an adapter" }
        require(registrations.map { it.first }.distinct().size == registrations.size) { "Duplicate device adapter" }
        adapters = registrations.toMap()
    }

    fun supports(kind: DeviceKind): Boolean = adapters.containsKey(kind)
    fun requireAdapter(kind: DeviceKind): A = adapters[kind]
        ?: throw UnsupportedOperationException("No installed adapter for ${kind.label}")
}
