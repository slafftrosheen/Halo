package com.halo.app.capture

import com.halo.sdk.runtime.multimodal.MediaAssetRef

enum class CaptureSource { PHONE_CAMERA, MANAGED_WEARABLE_CAMERA, IMPORTED_IMAGE, SHARED_IMAGE, ASSISTANT_SCREENSHOT }
enum class CaptureQuality { ORIGINAL, AI_THUMBNAIL, UNKNOWN }
enum class CaptureStatus { REQUESTED, RECEIVING, RECEIVED, REVIEWABLE, SAVING, SAVED, ANALYZING, COMPLETED, FAILED, CANCELLED }
enum class CaptureAction { ASK, STUDY, SAVE, EXTRACT, REMEMBER }
data class ContextualCaptureState(val captureId: String, val source: CaptureSource, val label: String,
    val mediaRef: MediaAssetRef, val width: Int, val height: Int, val quality: CaptureQuality,
    val workspaceObjectId: String? = null, val status: CaptureStatus = CaptureStatus.REVIEWABLE,
    val selectedAction: CaptureAction? = null, val error: String? = null,
    val sourceAssetId: String? = null, val captureTimestampMs: Long? = null, val trigger: String? = null)
sealed interface ContextualCaptureNavigation {
    data object Ask : ContextualCaptureNavigation
    data class Study(val objectId: String) : ContextualCaptureNavigation
    data class OpenNote(val objectId: String) : ContextualCaptureNavigation
    data object Memory : ContextualCaptureNavigation
}
enum class CaptureExtractionMode(val prompt: String) {
    READ_TEXT("Extract only clearly readable text from this image. Preserve original language, punctuation and line breaks. Mark uncertain words as [unclear]. Do not guess obscured characters."),
    IDENTIFY_OBJECTS("Identify only objects visibly supported by this image. State uncertainty and do not infer hidden features."),
    KEY_INFORMATION("Extract only visible key information. Mark unclear or missing values as [unclear]. Do not infer missing numbers, dates or fields.")
}
