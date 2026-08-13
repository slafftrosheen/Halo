package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Raw data class mapping to the BuddyBot C++ RuntimeSnapshot struct.
 */
data class BuddyBotSnapshot(
    val capturedAtMs: Long,
    val rangeDistanceMm: Int,
    val rangeValid: Boolean,
    val batteryPercent: Int,
    val imuValid: Boolean,
    val accelXG: Float,
    val accelYG: Float,
    val accelZG: Float,
    val safetyState: Int,
    val safetyFault: Int,
    val estopped: Boolean,
    val faulted: Boolean,
    val driveAvailable: Boolean,
    val driveActive: Boolean,
    val driveMode: Int
)

class BuddyBotGateway(private val eventBus: EventBus) : EmbodimentGateway {
    
    // We assume an external transport passes the decoded snapshots here
    suspend fun onSnapshotReceived(snapshot: BuddyBotSnapshot) {
        val now = System.currentTimeMillis()
        
        // Example freshness policy: if the snapshot is older than 250ms, it is stale
        // Wait, snapshot.capturedAtMs is from the bot's clock. We assume it's a monotonic 
        // counter or synchronized time. For simplicity, we just use the delta if we know it.
        // If not synced, we assume it's FRESH upon receipt and let deduplicator handle it.
        val freshness = Freshness.FRESH 

        val deviceId = "buddybot_1"

        // Map Range
        eventBus.publish(ObservationUpdated(
            observation = Observation(
                modality = Modality.SENSOR,
                source = Source.ROBOT,
                sourceDeviceId = deviceId,
                freshness = freshness,
                payload = RangeObservationPayload(snapshot.rangeDistanceMm, snapshot.rangeValid)
            )
        ))

        // Map IMU
        eventBus.publish(ObservationUpdated(
            observation = Observation(
                modality = Modality.SENSOR,
                source = Source.ROBOT,
                sourceDeviceId = deviceId,
                freshness = freshness,
                payload = ImuObservationPayload(
                    available = snapshot.imuValid,
                    valid = snapshot.imuValid,
                    accelXG = snapshot.accelXG,
                    accelYG = snapshot.accelYG,
                    accelZG = snapshot.accelZG
                )
            )
        ))

        // Map Safety
        eventBus.publish(ObservationUpdated(
            observation = Observation(
                modality = Modality.SYSTEM,
                source = Source.ROBOT,
                sourceDeviceId = deviceId,
                freshness = freshness,
                payload = SafetyObservationPayload(
                    state = snapshot.safetyState,
                    fault = snapshot.safetyFault,
                    estopped = snapshot.estopped,
                    faulted = snapshot.faulted
                )
            )
        ))

        // Map Battery
        eventBus.publish(ObservationUpdated(
            observation = Observation(
                modality = Modality.SYSTEM,
                source = Source.ROBOT,
                sourceDeviceId = deviceId,
                freshness = freshness,
                payload = BatteryObservationPayload(snapshot.batteryPercent)
            )
        ))

        // Map Drive (Motion)
        eventBus.publish(ObservationUpdated(
            observation = Observation(
                modality = Modality.SYSTEM,
                source = Source.ROBOT,
                sourceDeviceId = deviceId,
                freshness = freshness,
                payload = MotionObservationPayload(
                    available = snapshot.driveAvailable,
                    active = snapshot.driveActive,
                    mode = snapshot.driveMode
                )
            )
        ))
    }

    override suspend fun dispatch(intent: EmbodimentIntent) {
        when (intent.kind) {
            IntentKind.MOVE -> {
                when (intent.driveMode) {
                    DriveMode.MANUAL_VECTOR -> {
                        sendManualDrive(intent.vectorX, intent.vectorY)
                    }
                    DriveMode.FORWARD -> {
                        sendManualDrive(0f, 1f) // Example Y=forward
                    }
                    else -> {}
                }
            }
            IntentKind.STOP -> {
                // Determine if it's an emergency stop or a standard stop
                // BuddyBot physical semantics: Send [0, 0] for soft stop, or hardware STOP for estop
                sendManualDrive(0f, 0f)
            }
            else -> {
                // Other BuddyBot intents (ARM, DISARM, SET_MOOD, etc.)
            }
        }
    }

    private fun sendManualDrive(x: Float, y: Float) {
        // Here we send the semantic (x, y) vector to the physical bot.
        // It relies on BuddyBot's SafetySupervisor to ensure the command is safe.
    }
}
