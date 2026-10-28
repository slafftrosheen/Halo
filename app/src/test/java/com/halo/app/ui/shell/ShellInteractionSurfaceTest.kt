package com.halo.app.ui.shell

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HostlessHaloTheme
import com.halo.runtime.cognition.interaction.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShellInteractionSurfaceTest {
    @get:Rule val compose = createComposeRule()

    private fun progress() = InteractionSurface(
        id = InteractionSurfaceId("shell-pending"), runId = null, interactionId = null, inputRequestId = null,
        kind = InteractionSurfaceKind.PROGRESS, title = null, message = null,
        content = InteractionContent.Progress("Hidden progress", InteractionProgressPhase.WAITING),
        actions = emptyList(), presentation = InteractionPresentationPolicy(),
        createdAt = Instant.EPOCH, expiresAt = null,
        provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.AGENT_RUN),
    )

    @Test fun passiveProgressDoesNotMountBehindTheWorkingSurfaceOrResolveAnything() {
        val responses = mutableListOf<InteractionResponse>()
        compose.setContent { HostlessHaloTheme {
            ShellInteractionSurface(progress(), responses::add)
            Text("Working content")
        } }
        compose.onNodeWithText("Working content").assertIsDisplayed()
        compose.onNodeWithText("Hidden progress").assertDoesNotExist()
        assertTrue(responses.isEmpty())
    }

    @Test fun pendingApprovalAfterProgressStillRequiresAnExplicitTypedResponse() {
        val current = mutableStateOf(progress())
        val responses = mutableListOf<InteractionResponse>()
        compose.setContent { HostlessHaloTheme { ShellInteractionSurface(current.value, responses::add) } }
        compose.runOnIdle {
            current.value = current.value.copy(kind = InteractionSurfaceKind.CONFIRMATION,
                content = InteractionContent.Confirmation("Review this action"))
        }
        compose.onNodeWithText("Approve").assertIsDisplayed()
        assertTrue(responses.isEmpty())
        compose.onNodeWithText("Decline").performClick()
        assertEquals(listOf(InteractionResponse.Denied(current.value.id)), responses)
    }
}
