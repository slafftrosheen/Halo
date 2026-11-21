package com.halo.app.y2027

/*
 * halo 2027 · Across your things (board 51). Routes the next question to the device in use.
 * Payments, bookings with money and deletes never leave the phone.
 */
enum class HaloDevice { PHONE, WATCH, GLASSES, EARBUDS, TABLET }
enum class DeviceState(val plain: String) { READY("Ready"), IN_USE("In use"), ASLEEP("Asleep"), NEEDS_A_LOOK("Needs a look") }

data class DeviceInfo(val device: HaloDevice, val state: DeviceState, val usedFor: String)

object FollowMe {
    fun route(step: PlanStep, devices: List<DeviceInfo>, followMe: Boolean, approveOnWatch: Boolean): HaloDevice {
        if (!followMe) return HaloDevice.PHONE
        if (step.hardStop == HardStop.MONEY || step.hardStop == HardStop.DELETE) return HaloDevice.PHONE
        val inUse = devices.firstOrNull { it.state == DeviceState.IN_USE && it.device != HaloDevice.EARBUDS }?.device
        return when {
            inUse == HaloDevice.WATCH && !approveOnWatch && step.effectiveKind == StepKind.ASK_FIRST -> HaloDevice.PHONE
            inUse != null -> inUse
            else -> HaloDevice.PHONE
        }
    }
}
