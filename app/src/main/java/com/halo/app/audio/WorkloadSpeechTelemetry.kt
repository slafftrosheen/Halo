package com.halo.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Last operation only, no text or endpoint identifiers. Timings describe API events, not audibility. */
data class LocalSpeechTiming(val pack: String,val voice: Int,val cacheHit: Boolean,val reused: Boolean,
    val initMs: Long?,val waitMs: Long?,val firstPcmMs: Long,val synthesisMs: Long?,val audioMs: Long,
    val realTimeFactor: Double?)
data class RoutedSpeechTiming(val firstPcmMs: Long?=null,val firstRoutedWriteMs: Long?=null,
    val completionMs: Long?=null,val status: String="NOT_RUN")
object WorkloadSpeechTelemetry {
    internal val synthesisState=MutableStateFlow<LocalSpeechTiming?>(null)
    val synthesis=synthesisState.asStateFlow()
    internal val playbackState=MutableStateFlow(RoutedSpeechTiming())
    val playback=playbackState.asStateFlow()
}
