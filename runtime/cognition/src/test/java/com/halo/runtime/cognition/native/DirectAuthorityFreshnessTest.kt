package com.halo.runtime.cognition.native

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.DirectUserCommandAuthority
import com.halo.runtime.cognition.blackboard.InteractionOrigin
import com.halo.sdk.runtime.Source
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DirectAuthorityFreshnessTest {
    @Test fun exactFreshHumanAuthorityOnly() {
        val now = Instant.parse("2026-10-04T10:00:00Z")
        val interaction = InteractionId("interaction")
        val authority = DirectUserCommandAuthority(interaction, "halo_open_app", "digest", InteractionOrigin(Source.MICROPHONE), now)
        assertTrue(authority.matches(interaction, "halo_open_app", "digest", now.plusSeconds(1)))
        assertTrue(authority.copy(origin = InteractionOrigin(Source.USER)).matches(interaction, "halo_open_app", "digest", now))
        assertFalse(authority.matches(interaction, "halo_open_app", "digest", now.plusSeconds(31)))
        assertFalse(authority.matches(interaction, "halo_open_app", "other", now))
        assertFalse(authority.matches(InteractionId("other"), "halo_open_app", "digest", now))
        assertFalse(authority.matches(interaction, "another-tool", "digest", now))
        assertFalse(authority.copy(origin = InteractionOrigin(Source.SYSTEM)).matches(interaction, "halo_open_app", "digest", now))
        assertFalse(authority.matches(interaction, "halo_open_app", "digest", now.minusSeconds(1)))
    }
}
