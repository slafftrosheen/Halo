package com.halo.providers.litert.runtime

import com.halo.sdk.provider.AiRequest

data class LiteRtMappedContent(
    val text: String?,
    val imageBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiteRtMappedContent) return false
        if (text != other.text) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        return result
    }
}

object LiteRtContentMapper {

    fun mapContent(request: AiRequest): LiteRtMappedContent {
        return LiteRtMappedContent(
            text = request.text,
            imageBytes = request.image
        )
    }
}
