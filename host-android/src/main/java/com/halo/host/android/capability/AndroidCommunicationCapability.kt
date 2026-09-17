package com.halo.host.android.capability

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.host.android.resolver.AndroidContactResolver
import com.halo.host.android.resolver.ContactResolveResult
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task

class AndroidCommunicationCapability(
    private val context: Context,
    private val contactResolver: AndroidContactResolver = AndroidContactResolver(context)
) : Capability {
    override val id: String = "capability.communication.android"

    override suspend fun supports(task: Task): Boolean {
        return task is CommunicationTask && task.targetId != "user"
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is CommunicationTask)
        return try {
            val resolveResult = contactResolver.resolve(task.targetId)
            when (resolveResult) {
                is ContactResolveResult.Found -> {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(resolveResult.number)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    Outcome.Success(resolveResult.name)
                }
                is ContactResolveResult.NotFound -> {
                    Outcome.PermanentFailure("No contact named ${task.targetId} was found")
                }
                is ContactResolveResult.MultipleMatches -> {
                    Outcome.PermanentFailure("That contact has multiple phone numbers")
                }
                is ContactResolveResult.NoPhoneNumber -> {
                    Outcome.PermanentFailure("No phone number found for ${task.targetId}")
                }
            }
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to initiate call")
        }
    }
}
