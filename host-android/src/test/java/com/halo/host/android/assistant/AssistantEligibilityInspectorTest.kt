package com.halo.host.android.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantEligibilityInspectorTest {

    @Test
    fun `inspect produces structured snapshot without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inspector = AssistantEligibilityInspector(context)

        val snapshot = inspector.inspect()

        assertNotNull(snapshot)
        assertNotNull(snapshot.problems)
    }

    @Test
    fun `evaluateState maps snapshot to integration state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inspector = AssistantEligibilityInspector(context)

        val snapshot = AssistantEligibilitySnapshot(
            voiceInteractionServiceDeclared = true,
            bindPermissionCorrect = true,
            activeVoiceInteractionService = true
        )

        val state = inspector.evaluateState(snapshot)

        assertTrue(state is AssistantIntegrationState.Active)
    }
}
