package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.MemoryTask
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.WebTask

class CommandResponseMapper {

    fun mapResponse(task: Task, outcome: Outcome): String? {
        val isSuccess = outcome is Outcome.Success || outcome is Outcome.Partial

        return when (task) {
            is ApplicationTask -> {
                if (isSuccess) {
                    val label = (outcome as? Outcome.Success)?.message?.ifBlank { task.displayName }
                        ?: task.displayName.ifEmpty { task.packageName }
                    "Opening $label."
                } else if (outcome is Outcome.PermanentFailure) {
                    outcome.reason
                } else {
                    "Unable to open application."
                }
            }

            is CommunicationTask -> {
                if (isSuccess) {
                    val name = (outcome as? Outcome.Success)?.message?.ifBlank { task.targetId }
                        ?: task.targetId
                    "Opening the phone for $name."
                } else if (outcome is Outcome.PermanentFailure) {
                    outcome.reason
                } else {
                    "Unable to initiate call."
                }
            }

            is WebTask -> {
                if (isSuccess) {
                    "Searching."
                } else {
                    "Unable to perform web search."
                }
            }

            is NavigationTask -> {
                if (isSuccess) {
                    "Opening directions."
                } else {
                    "Unable to find destination."
                }
            }

            is MediaTask -> {
                if (isSuccess) {
                    if (task.mediaAction.equals("pause", ignoreCase = true)) "Music paused." else "Playing music."
                } else if (outcome is Outcome.PermanentFailure) {
                    outcome.reason
                } else {
                    "I couldn't find a media player to control."
                }
            }

            is MemoryTask -> {
                if (isSuccess) {
                    "Reminder set."
                } else {
                    "Unable to set reminder."
                }
            }

            is DeviceTask -> {
                when {
                    task.action.equals("time", ignoreCase = true) -> {
                        val msg = (outcome as? Outcome.Success)?.message
                        if (isSuccess && !msg.isNullOrBlank()) {
                            msg
                        } else {
                            "Unable to retrieve time."
                        }
                    }
                    task.action.equals("alarm", ignoreCase = true) -> {
                        if (isSuccess) "Alarm set." else "Unable to set alarm."
                    }
                    task.action.equals("settings", ignoreCase = true) -> {
                        if (isSuccess) "Opening settings." else "Unable to open settings."
                    }
                    else -> if (isSuccess) "Done." else "Unable to complete action."
                }
            }

            else -> null
        }
    }
}
