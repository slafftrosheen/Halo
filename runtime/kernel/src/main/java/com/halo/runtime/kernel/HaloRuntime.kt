package com.halo.runtime.kernel

/**
 * Deprecated source-compatibility marker for clients that imported the old
 * kernel runtime type. Runtime ownership now lives in the Android app module.
 */
@Deprecated(
    message = "Use com.halo.app.HaloRuntime instead",
    replaceWith = ReplaceWith("com.halo.app.HaloRuntime")
)
class HaloRuntime private constructor()
