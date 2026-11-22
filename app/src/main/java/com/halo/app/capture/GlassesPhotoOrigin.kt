package com.halo.app.capture

import com.halo.app.data.LibraryCaptureOrigin
import java.util.Locale
import org.json.JSONObject

internal fun glassesPhotoOrigins(
    captures: List<LibraryCaptureOrigin>,
    registeredGlasses: Set<String>,
): List<LibraryCaptureOrigin> {
    val ids = registeredGlasses.mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
    return captures.filter { capture ->
        runCatching {
            val metadata = JSONObject(capture.metadata)
            val deviceId = when {
                capture.sourceId.startsWith("device-media:") && metadata.optString("kind") == "DEVICE_MEDIA_IMPORT" ->
                    metadata.optString("deviceId")
                metadata.optString("sourceKind") == CaptureSource.MANAGED_WEARABLE_CAMERA.name -> capture.sourceId
                else -> ""
            }
            deviceId.isNotEmpty() && deviceId.lowercase(Locale.ROOT) in ids
        }.getOrDefault(false)
    }
}
