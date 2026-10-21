package com.halo.app.interaction

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.halo.app.ui.theme.HostlessHaloTheme as HaloTheme
import com.halo.runtime.cognition.interaction.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloApprovalRound2Test {
    @get:Rule val compose = createComposeRule()

    private val id = InteractionSurfaceId("confirm-review")
    private val surface = InteractionSurface(
        id = id, runId = null, interactionId = null, inputRequestId = null,
        kind = InteractionSurfaceKind.CONFIRMATION,
        title = "Permission required", message = "Check this action",
        content = InteractionContent.Confirmation(
            subject = "Send an email",
            detail = "Send the draft to the selected recipient",
            risk = InteractionRiskPresentation(InteractionRiskLevel.EXTERNAL_SIDE_EFFECT),
        ),
        actions = listOf(
            InteractionAction(InteractionActionId("yes"), "Send it", InteractionActionSemantic.CONFIRM),
            InteractionAction(InteractionActionId("no"), "Decline", InteractionActionSemantic.DENY),
        ),
        presentation = InteractionPresentationPolicy(),
        createdAt = Instant.EPOCH, expiresAt = null,
        provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.SYSTEM),
    )

    @Test fun compactApprovalNeverRespondsBeforeAnExplicitAction() {
        val emitted = mutableListOf<InteractionResponse>()
        compose.setContent {
            HaloTheme(darkTheme = true) {
                InteractionSurfaceRenderer(surface, onRespond = emitted::add,
                    mode = SurfaceRenderMode.COMPACT)
            }
        }
        compose.onNodeWithText("Needs your approval").assertExists()
        compose.onNodeWithText("Send an email").assertExists()
        compose.onNodeWithText("Send the draft to the selected recipient").assertExists()
        compose.onNodeWithText("This reaches beyond halo.").assertExists()
        assertTrue(emitted.isEmpty())
        compose.onNodeWithText("Send it").performClick()
        assertEquals(listOf(InteractionResponse.Confirmed(id)), emitted)
        compose.onNodeWithText("Decline").assertIsNotEnabled()
    }

    @Test fun explicitDeclineIsNotAnApproval() {
        val emitted = mutableListOf<InteractionResponse>()
        compose.setContent {
            HaloTheme {
                InteractionSurfaceRenderer(surface, onRespond = emitted::add,
                    mode = SurfaceRenderMode.COMPACT)
            }
        }
        compose.onNodeWithText("Decline").performClick()
        assertEquals(listOf(InteractionResponse.Denied(id)), emitted)
    }
}
