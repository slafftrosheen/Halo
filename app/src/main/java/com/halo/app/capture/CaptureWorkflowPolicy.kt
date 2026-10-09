package com.halo.app.capture

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.multimodal.*

object CaptureWorkflowPolicy {
    fun permitsModel(model: AiModel, policy: AiContextPolicy, objectAllowed: Boolean): Boolean =
        AiCapability.TEXT in model.inputCapabilities && model.hasVerifiedImageInput() && policy.includeImages && objectAllowed &&
            (model.executionLocation == AiExecutionLocation.ON_DEVICE || policy.allowCloudImages)
}
