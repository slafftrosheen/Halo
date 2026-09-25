package com.halo.runtime.cognition.device

import com.halo.sdk.runtime.resource.HaloResourceIds
import com.halo.sdk.runtime.resource.ResourceId

/**
 * Maps semantic capabilities onto scarce Runtime resources.
 *
 * Capability ids are exact contracts. A small alias table exists only for
 * pre-RUNTIME-020.1 compatibility; there is no substring/fuzzy matching.
 */
object CapabilityResourceBinding {
    fun resourcesForCapability(capabilityKind: String): Set<ResourceId> =
        when (canonicalCapabilityId(capabilityKind)) {
            "device.audio.input", "host.audio.input" ->
                setOf(HaloResourceIds.AudioInput)
            "device.audio.output", "host.audio.output" ->
                setOf(HaloResourceIds.AudioOutput)
            "device.camera.still", "host.camera.still" ->
                setOf(HaloResourceIds.CameraSensor, HaloResourceIds.CameraCapture)
            "device.camera.video" ->
                setOf(HaloResourceIds.CameraSensor, HaloResourceIds.VideoCapture)
            "device.display.output" ->
                setOf(HaloResourceIds.DisplayOutput)
            "device.media.transfer" ->
                setOf(HaloResourceIds.MediaTransfer)
            "host.screen.capture" ->
                setOf(HaloResourceIds.ScreenCapture)
            "runtime.compute.inference" ->
                setOf(HaloResourceIds.ComputeInference)
            "runtime.compute.asr" ->
                setOf(HaloResourceIds.ComputeAsr)
            "device.mobility" ->
                setOf(HaloResourceIds.MobilityDrive)
            "device.manipulator" ->
                setOf(HaloResourceIds.ManipulatorMotion)
            else -> emptySet()
        }

    private fun canonicalCapabilityId(raw: String): String = when (
        raw.trim().lowercase().replace('_', '.')
    ) {
        "audio.input" -> "device.audio.input"
        "audio.output" -> "device.audio.output"
        "camera.sensor", "camera.still", "camera.photo" -> "device.camera.still"
        "video.capture", "camera.video" -> "device.camera.video"
        "display.output" -> "device.display.output"
        "media.transfer" -> "device.media.transfer"
        "screen.capture" -> "host.screen.capture"
        "compute.inference", "llm" -> "runtime.compute.inference"
        "compute.asr", "stt" -> "runtime.compute.asr"
        "mobility.drive" -> "device.mobility"
        "manipulator.motion" -> "device.manipulator"
        else -> raw.trim().lowercase().replace('_', '.')
    }
}
