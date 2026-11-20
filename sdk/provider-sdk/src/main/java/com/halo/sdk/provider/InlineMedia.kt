package com.halo.sdk.provider

/** Bounded inline inputs; large uploads need a separate, explicit file workflow. */
data class AiInlineMedia(val capability: AiCapability, val mimeType: String, val bytes: ByteArray)

fun AiRequest.inlineMedia(): List<AiInlineMedia> {
    if (!appendTextAsUserMessage) return emptyList()
    val total = (image?.size?.toLong() ?: 0) + (audio?.size?.toLong() ?: 0) + (video?.size?.toLong() ?: 0)
    if (total > 12L * 1024 * 1024) throw AiProviderException(AiErrorType.INVALID_REQUEST,
        "Inline media exceeds Halo's 12 MB limit. Use a shorter clip or smaller image.")
    fun part(capability: AiCapability, bytes: ByteArray?, prefix: String, fallback: String? = null): AiInlineMedia? {
        if (bytes == null) return null
        val mime = mimeTypes.firstOrNull { it.startsWith(prefix) } ?: fallback
        if (bytes.isEmpty() || mime == null || !mime.matches(Regex("[a-z]+/[a-zA-Z0-9.+-]+")))
            throw AiProviderException(AiErrorType.INVALID_REQUEST, "Media requires non-empty encoded bytes and a supported MIME type")
        return AiInlineMedia(capability, mime, bytes)
    }
    return listOfNotNull(part(AiCapability.IMAGE, image, "image/", "image/jpeg"),
        part(AiCapability.AUDIO, audio, "audio/"), part(AiCapability.VIDEO, video, "video/"))
}
