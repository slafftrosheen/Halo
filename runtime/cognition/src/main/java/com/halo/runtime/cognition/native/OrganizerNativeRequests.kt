package com.halo.runtime.cognition.native

import com.halo.cognition.language.*
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.tools.CapabilityId
import java.util.UUID

/** Resolved semantic mapping used by the existing native dispatcher; no execution or Android dependencies. */
object OrganizerNativeRequests {
    fun requestFor(interpretation: Interpretation): NativeCapabilityRequest? {
        if(interpretation.confidence<0.8f) return null
        val args: Map<String,Any>
        val tool: String
        val capability: String
        when(val intent=interpretation.intent) {
            is NativeClockIntent -> {
                val view = intent.action in setOf("show_alarms", "show_timers")
                val modify = intent.action in setOf("dismiss_next_alarm", "snooze_alarm", "dismiss_expired_timers")
                if (!view && !modify) return null
                val systemClock=intent.systemClock
                if(systemClock) {
                    tool=if(view) "halo_clock_view" else "halo_clock_modify"
                    capability=if(view) "host.clock.view" else "host.clock.modify"
                    args=mapOf("action" to intent.action)
                } else {
                    tool=if(view) "halo_organizer_query" else "halo_organizer_clock_action"
                    capability=if(view) "workspace.read" else "workspace.write"
                    args=if(view) mapOf("kind" to if(intent.action=="show_alarms") "ALARM" else "TIMER")
                        else mapOf("action" to intent.action.uppercase(java.util.Locale.ROOT))
                }
            }
            is NativeSystemAlarmIntent -> {tool="halo_set_alarm";capability="host.alarm.set";args=mapOf("timeExpression" to intent.timeExpression)}
            is TimerIntent -> {
                tool=if(intent.systemClock) "halo_timer" else "halo_organizer_create"
                capability=if(intent.systemClock) "host.timer.set" else "workspace.write"
                args=if(intent.systemClock) mapOf("seconds" to intent.seconds)
                    else mapOf("record" to mapOf("kind" to "TIMER","title" to "Timer","timerDurationMs" to intent.seconds.toLong()*1000L))
            }
            SetAlarmIntent -> {
                tool="halo_organizer_alarm";capability="workspace.write"
                val expression=interpretation.transcript.trim().trimEnd('.').replace(Regex("^(?:set (?:an? )?alarm(?: for)?|alarm for|wake me at)\\s+",RegexOption.IGNORE_CASE),"")
                val resolved=when(val time=interpretation.entities.filterIsInstance<TimeEntity>().singleOrNull()?.value) {
                    is TemporalValue.TimeOfDay -> mapOf("type" to "CLOCK","time" to time.time.toString())+
                        (time.dayRelation?.let {mapOf("dayRelation" to it.name)} ?: emptyMap())
                    is TemporalValue.InstantValue -> mapOf("type" to "INSTANT","instant" to time.instant.toString())
                    else -> null
                }
                args=mapOf("timeExpression" to expression)+(resolved?.let {mapOf("resolvedTime" to it)} ?: emptyMap())
            }
            else -> return null
        }
        return NativeCapabilityRequest(interpretation.metadata["interactionId"] ?: interpretation.origin?.captureId ?: UUID.randomUUID().toString(),
            CapabilityId(capability),tool,args,interpretation.intent::class.simpleName ?: "NativeIntent")
    }
}
