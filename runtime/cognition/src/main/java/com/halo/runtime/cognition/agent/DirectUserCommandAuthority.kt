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
)
