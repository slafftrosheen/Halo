package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.WebTask
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandResponseMapperTest {

    private val mapper = CommandResponseMapper()

    @Test
    fun testApplicationTaskResponse() {
        val task = ApplicationTask(packageName = "com.google.android.youtube", intent = "open", displayName = "YouTube")
        val outcome = Outcome.Success("YouTube")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("Opening YouTube.", response)
    }

    @Test
    fun testCommunicationTaskResponse() {
        val task = CommunicationTask(targetId = "John", payload = "")
        val outcome = Outcome.Success("John")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("Opening the phone for John.", response)
    }

    @Test
    fun testMediaTaskPlayResponse() {
        val task = MediaTask(mediaAction = "play", mediaId = "")
        val outcome = Outcome.Success("Resumed playback")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("Playing music.", response)
    }

    @Test
    fun testMediaTaskPauseResponse() {
        val task = MediaTask(mediaAction = "pause", mediaId = "")
        val outcome = Outcome.Success("Paused playback")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("Music paused.", response)
    }

    @Test
    fun testNavigationTaskResponse() {
        val task = NavigationTask(coordinates = "Riga")
        val outcome = Outcome.Success("Directions to Riga")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("Opening directions.", response)
    }

    @Test
    fun testWebTaskResponse() {
        val task = WebTask(url = "https://www.google.com/search?q=cats")
        val outcome = Outcome.Success("cats")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("Searching.", response)
    }

    @Test
    fun testDeviceTimeTaskResponse() {
        val task = DeviceTask(action = "time")
        val outcome = Outcome.Success("It's 12:34 PM.")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("It's 12:34 PM.", response)
    }

    @Test
    fun testMediaTaskFailureResponse() {
        val task = MediaTask(mediaAction = "play", mediaId = "Daft Punk")
        val outcome = Outcome.PermanentFailure("No media player is available")
        val response = mapper.mapResponse(task, outcome)
        assertEquals("No media player is available", response)
    }

    @Test
    fun testExecutionSpeaksOnceUsingFinalMeaningfulSuccess() {
        val results = listOf(
            ExecutionResultPublished(
                executionId = "execution-1",
                planId = "plan-1",
                task = DeviceTask(action = "time"),
                outcome = Outcome.Success("It's 12:34 PM.")
            ),
            ExecutionResultPublished(
                executionId = "execution-1",
                planId = "plan-1",
                task = MediaTask(mediaAction = "play", mediaId = "Daft Punk"),
                outcome = Outcome.Success("Playback requested")
            )
        )

        assertEquals("Playing music.", mapper.mapExecution(results))
    }

    @Test
    fun testExecutionFailureOverridesEarlierSuccess() {
        val results = listOf(
            ExecutionResultPublished(
                executionId = "execution-2",
                planId = "plan-2",
                task = ApplicationTask(packageName = "com.example.app", intent = "open", displayName = "Example"),
                outcome = Outcome.Success("Example")
            ),
            ExecutionResultPublished(
                executionId = "execution-2",
                planId = "plan-2",
                task = MediaTask(mediaAction = "play", mediaId = "Daft Punk"),
                outcome = Outcome.PermanentFailure("No media player is available")
            )
        )

        assertEquals("No media player is available", mapper.mapExecution(results))
    }
}
