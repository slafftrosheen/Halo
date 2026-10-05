package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.sdk.runtime.ObservationOrigin
import java.time.Instant

data class DirectUserCommandAuthority(
    val interactionId: InteractionId,
    val toolId: String,
    val argumentsDigest: String,
    val origin: InteractionOrigin,
    val issuedAt: Instant,
) {
    fun matches(interaction: InteractionId, tool: String, digest: String, now: Instant = Instant.now()): Boolean =
        interactionId == interaction && toolId == tool && argumentsDigest == digest &&
            origin.source in setOf(com.halo.sdk.runtime.Source.USER, com.halo.sdk.runtime.Source.MICROPHONE) &&
            !now.isBefore(issuedAt) && now.isBefore(issuedAt.plusSeconds(30))
}
