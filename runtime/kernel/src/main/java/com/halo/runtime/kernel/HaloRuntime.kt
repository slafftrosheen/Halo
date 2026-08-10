package com.halo.runtime.kernel

/**
 * Placeholder for HaloRuntime.
 *
 * The actual implementation lives in :app module at com.halo.app.HaloRuntime.
 * This placeholder exists to satisfy package structure requirements.
 */
@Deprecated(
    message = "Use com.halo.app.HaloRuntime instead",
    replaceWith = ReplaceWith("com.halo.app.HaloRuntime")
)
class HaloRuntime {
    // Delegate to the app module implementation
    // This class should not be instantiated directly
}