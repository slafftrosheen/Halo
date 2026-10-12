package com.halo.app.di.tools.host

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaToolsSelectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun multipleInactivePlayersRequireAnExplicitTarget() = runTest {
        val one = MediaSession(context, "test-media-one")
        val two = MediaSession(context, "test-media-two")
        try {
            one.setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f).build())
            two.setPlaybackState(PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f).build())
            val tools = MediaTools(context, activeSessions = {
                listOf(one.controller, two.controller)
            })
            val result = tools.execute(ToolCall("media", "halo_media_control", mapOf("action" to "pause")))
            assertTrue("Must not dispatch to first arbitrary controller", result is ToolExecutionResult.Rejected)
            assertTrue((result as ToolExecutionResult.Rejected).reason.contains("Multiple media sessions"))
        } finally {
            one.release()
            two.release()
        }
    }

    @Test fun invalidOrConflictingMediaTargetsAreRejected() = runTest {
        val session = MediaSession(context, "test-media")
        try {
            val tools = MediaTools(context, activeSessions = { listOf(session.controller) })
            val both = tools.execute(ToolCall("media", "halo_media_control", mapOf(
                "action" to "pause", "targetPackage" to "com.example.a", "targetApp" to "Example")))
            assertTrue(both is ToolExecutionResult.Rejected)
        } finally {
            session.release()
        }
    }
}
