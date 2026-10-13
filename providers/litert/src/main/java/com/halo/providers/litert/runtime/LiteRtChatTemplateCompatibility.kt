package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LiteRtModelCatalog

/** Compatibility repair for the pinned Qwen artifact's string-only embedded chat template. */
internal object LiteRtChatTemplateCompatibility {
    private val qwenTemplate by lazy {
        checkNotNull(javaClass.getResourceAsStream("/qwen3-1.7b-chat.jinja")) {
            "Missing Qwen chat template compatibility resource"
        }.bufferedReader().use { it.readText() }
    }

    fun overrideFor(sha256: String?): String? =
        if (sha256.equals(LiteRtModelCatalog.QWEN3_1_7B_DYNAMIC_INT4.sha256, ignoreCase = true)) qwenTemplate else null
}
