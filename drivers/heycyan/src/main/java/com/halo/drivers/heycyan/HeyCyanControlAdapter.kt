package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.sdk.host.*

/** Preserve vendor identity; only the already-supported voice/camera classes are assignable. */
internal fun normalizeHeyCyanControl(type: ButtonType, timestamp: Long, delivery: ControlDelivery): AccessoryControlEvent.Button {
    val (control, action) = when (type) {
        ButtonType.VOICE_BUTTON -> AccessoryControl.ASSISTANT to AccessoryControlAction.SINGLE
        ButtonType.CAMERA_BUTTON -> AccessoryControl.CAMERA to AccessoryControlAction.SINGLE
        ButtonType.SINGLE_TAP -> AccessoryControl.PRIMARY to AccessoryControlAction.SINGLE
        ButtonType.DOUBLE_TAP -> AccessoryControl.PRIMARY to AccessoryControlAction.DOUBLE
        ButtonType.LONG_PRESS -> AccessoryControl.PRIMARY to AccessoryControlAction.LONG
        ButtonType.UNKNOWN -> AccessoryControl.CUSTOM to AccessoryControlAction.PRESS
    }
    val evidence = when (type) {
        ButtonType.VOICE_BUTTON, ButtonType.CAMERA_BUTTON -> ControlEvidence.VERIFIED_VENDOR
        ButtonType.UNKNOWN -> ControlEvidence.UNKNOWN
        else -> ControlEvidence.OBSERVED
    }
    return AccessoryControlEvent.Button(control, action, timestamp, delivery.copy(evidence = evidence, classified = true))
}
