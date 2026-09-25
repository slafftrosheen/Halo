package com.halo.runtime.cognition.device

import com.halo.sdk.runtime.resource.HaloResourceIds
import com.halo.sdk.runtime.resource.ResourceId

object CapabilityResourceBinding {
    fun resourcesForCapability(capabilityKind: String): Set<ResourceId> {
        val normalized = capabilityKind.lowercase().replace("_", ".")
        return when {
            normalized.contains("audio.input") -> setOf(HaloResourceIds.AudioInput)
            normalized.contains("audio.output") -> setOf(HaloResourceIds.AudioOutput)
            normalized.contains("camera") || normalized.contains("still") || normalized.contains("photo") ->
                setOf(HaloResourceIds.CameraSensor, HaloResourceIds.CameraCapture)
            normalized.contains("video") -> setOf(HaloResourceIds.CameraSensor, HaloResourceIds.VideoCapture)
            normalized.contains("display") -> setOf(HaloResourceIds.DisplayOutput)
            normalized.contains("media.transfer") || normalized.contains("transfer") -> setOf(HaloResourceIds.MediaTransfer)
            normalized.contains("screen") -> setOf(HaloResourceIds.ScreenCapture)
            normalized.contains("compute.inference") || normalized.contains("llm") -> setOf(HaloResourceIds.ComputeInference)
            normalized.contains("compute.asr") || normalized.contains("stt") -> setOf(HaloResourceIds.ComputeAsr)
            normalized.contains("mobility") || normalized.contains("drive") -> setOf(HaloResourceIds.MobilityDrive)
            normalized.contains("manipulator") -> setOf(HaloResourceIds.ManipulatorMotion)
            else -> emptySet()
        }
    }
}
