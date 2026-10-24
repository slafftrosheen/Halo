package com.halo.app.audio

import com.halo.sdk.runtime.Storage

/** The existing local voice preference transaction shared by UI and semantic configuration. */
object LocalKokoroVoiceSelection {
    fun select(storage: Storage, version: String, count: Int, id: Int) {
        require(version.isNotBlank() && version.length <= 200 && count in setOf(11, 103) && id in 0 until count)
        storage.putBatch(strings = mapOf("kokoro_voice" to id.toString(), "kokoro_voice.$version" to id.toString()))
        check(storage.getString("kokoro_voice") == id.toString() && storage.getString("kokoro_voice.$version") == id.toString()) { "Local voice preference was not saved" }
    }
}
