package com.halo.app.ui.components

import com.halo.app.audio.HaloSpeechOutputCoordinator
import com.halo.app.study.SentenceSegmenter
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class ReadAloudState(
    val id: String,
    val sentences: List<String>,
    val providerId: String,
    val cloudApproved: Boolean,
    val language: String?,
    val index: Int = 0,
    val preparing: Boolean = false,
    val playing: Boolean = false,
    val speed: Double = 1.0,
    val range: IntRange? = null,
    val notice: String? = null,
    val limited: Boolean = false,
) {
    val current: String get() = sentences.getOrNull(index).orEmpty()
}

/** Sentence transport only: synthesis, audio focus and output routing stay in the canonical coordinator. */
class HaloReadAloudController(
    private val bus: EventBus,
    private val output: HaloSpeechOutputCoordinator,
    private val startSpeaker: () -> Unit,
    private val selectedProvider: () -> String,
    providerChanges: Flow<String>,
    private val scope: CoroutineScope,
    private val context: android.content.Context,
    private val storage: Storage,
) {
    private val mutable = MutableStateFlow<ReadAloudState?>(null)
    val state = mutable.asStateFlow()
    private val rate = MutableStateFlow(storage.getString("read_aloud_speed")?.toDoubleOrNull()
        ?.takeIf { it in listOf(.75, 1.0, 1.25, 1.5) } ?: 1.0)
    val speedPreference = rate.asStateFlow()
    private val follow = MutableStateFlow(storage.getBoolean("read_aloud_follow_words", true))
    val followWords = follow.asStateFlow()
    private val autoRead = MutableStateFlow(storage.getBoolean("read_aloud_auto_answers", false))
    val autoReadAnswers = autoRead.asStateFlow()
    private val preferenceError = MutableStateFlow<String?>(null)
    val preferenceNotice = preferenceError.asStateFlow()
    fun setFollowWords(value: Boolean) {
        try { storage.putBoolean("read_aloud_follow_words", value); follow.value = value; preferenceError.value = null }
        catch (_: Exception) { preferenceError.value = "Could not save your change. Try again." }
    }
    fun setAutoReadAnswers(value: Boolean) {
        try { storage.putBoolean("read_aloud_auto_answers", value); autoRead.value = value; preferenceError.value = null }
        catch (_: Exception) { preferenceError.value = "Could not save your change. Try again." }
    }
    private var transaction: String? = null
    private var publishing: Job? = null
    private var acceptedStop = VoiceFieldCancellation.epoch.value
    private var media: android.media.session.MediaSession? = null

    init {
        scope.launch(Dispatchers.Main.immediate) { state.collect { current ->
            if (current == null || current.notice in setOf("Finished reading", "Finished reading the first part")) {
                media?.release(); media = null
                com.halo.app.audio.HaloAudioForegroundService.readingSession(null)
            } else {
                val session = media ?: android.media.session.MediaSession(context, "halo read aloud").apply {
                    setCallback(object : android.media.session.MediaSession.Callback() {
                        override fun onPlay() { if (state.value?.let { !it.playing && !it.preparing } == true) toggle() }
                        override fun onPause() { if (state.value?.let { it.playing || it.preparing } == true) toggle() }
                        override fun onStop() { stop() }
                        override fun onSkipToNext() { sentence(1) }
                        override fun onSkipToPrevious() { sentence(-1) }
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                    setMetadata(android.media.MediaMetadata.Builder().putString(
                        android.media.MediaMetadata.METADATA_KEY_TITLE, "halo · Read aloud").build())
                    isActive = true
                }.also {
                    media = it
                    com.halo.app.audio.HaloAudioForegroundService.readingSession(it.sessionToken)
                }
                val status = when {
                    current.preparing -> android.media.session.PlaybackState.STATE_BUFFERING
                    current.playing -> android.media.session.PlaybackState.STATE_PLAYING
                    else -> android.media.session.PlaybackState.STATE_PAUSED
                }
                session.setPlaybackState(android.media.session.PlaybackState.Builder()
                    .setActions(android.media.session.PlaybackState.ACTION_PLAY or android.media.session.PlaybackState.ACTION_PAUSE or
                        android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_STOP or
                        android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(status, android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN, current.speed.toFloat()).build())
            }
        } }
        scope.launch(Dispatchers.Main.immediate) { bus.events().collect { event ->
            val current = state.value ?: return@collect
            val id = when (event) {
                is TtsStarted -> event.transactionId
                is TtsRangeStarted -> event.transactionId
                is TtsCompleted -> event.transactionId
                is TtsCancelled -> event.transactionId
                is TtsFailed -> event.transactionId
                else -> return@collect
            }
            if (id != transaction) {
                if (event is TtsStarted) { cancelAudio(); mutable.value = null }
                return@collect
            }
            when (event) {
                is TtsStarted -> mutable.value = current.copy(preparing = false, playing = true)
                is TtsRangeStarted -> if (current.playing && event.start >= 0 && event.end > event.start && event.end <= current.current.length)
                    mutable.value = current.copy(range = event.start until event.end)
                is TtsCompleted -> {
                    transaction = null
                    if (current.index < current.sentences.lastIndex) {
                        mutable.value = current.copy(index = current.index + 1, range = null, playing = false)
                        speak()
                    } else mutable.value = current.copy(playing = false, preparing = false, range = null,
                        notice = if (current.limited) "Finished reading the first part" else "Finished reading")
                }
                is TtsCancelled -> { transaction = null; mutable.value = null }
                is TtsFailed -> {
                    transaction = null
                    mutable.value = current.copy(preparing = false, playing = false, range = null,
                        notice = "Reading aloud is unavailable with the current voice settings.")
                }
                else -> Unit
            }
        } }
        scope.launch(Dispatchers.Main.immediate) { providerChanges.distinctUntilChanged().collect { provider ->
            val current = state.value ?: return@collect
            if (provider != current.providerId) {
                cancelAudio()
                mutable.value = current.copy(preparing = false, playing = false, range = null,
                    notice = "Voice provider changed. Please choose Read aloud again.")
            }
        } }
        scope.launch(Dispatchers.Main.immediate) { VoiceFieldCancellation.epoch.collect { epoch ->
            if (epoch != acceptedStop) { cancelAudio(); mutable.value = null }
        } }
    }

    fun start(id: String, text: String, providerId: String, cloudApproved: Boolean, language: String?) {
        scope.launch(Dispatchers.Main.immediate) {
            acceptedStop = VoiceFieldCancellation.epoch.value
            cancelAudio()
            val parts = SentenceSegmenter.ranges(text)
            val sentences = parts.map { it.text }
            if (sentences.isEmpty()) return@launch
            mutable.value = ReadAloudState(id, sentences, providerId, cloudApproved, language, speed = rate.value,
                limited = (parts.lastOrNull()?.end ?: 0) < text.trimEnd().length)
            speak()
        }
    }
    fun stop() { scope.launch(Dispatchers.Main.immediate) { cancelAudio(); mutable.value = null } }
    fun toggle() { scope.launch(Dispatchers.Main.immediate) {
        val current = state.value ?: return@launch
        if (current.playing || current.preparing) {
            cancelAudio()
            mutable.value = current.copy(playing = false, preparing = false, range = null)
        } else speak() // Resume restarts the current sentence; no simulated sample-accurate seek.
    } }
    fun sentence(delta: Int) { scope.launch(Dispatchers.Main.immediate) {
        val current = state.value ?: return@launch
        val play = current.playing || current.preparing
        cancelAudio()
        mutable.value = current.copy(index = (current.index + delta).coerceIn(0, current.sentences.lastIndex),
            playing = false, preparing = false, range = null, notice = null)
        if (play) speak()
    } }
    fun speed(value: Double) { scope.launch(Dispatchers.Main.immediate) {
        if (value !in listOf(0.75, 1.0, 1.25, 1.5)) return@launch
        try { storage.putString("read_aloud_speed", value.toString()); preferenceError.value = null }
        catch (_: Exception) { preferenceError.value = "Could not save your change. Try again."; return@launch }
        rate.value = value
        val current = state.value ?: return@launch
        val play = current.playing || current.preparing
        cancelAudio()
        mutable.value = current.copy(speed = value, preparing = false, playing = false, range = null)
        if (play) speak()
    } }
    private fun cancelAudio() {
        publishing?.cancel(); publishing = null
        val old = transaction; transaction = null
        old?.let(output::cancelScopedTransaction)
    }
    private fun speak() {
        val current = state.value ?: return
        if (selectedProvider() != current.providerId) {
            mutable.value = current.copy(preparing = false, playing = false,
                notice = "Voice provider changed. Please choose Read aloud again.")
            return
        }
        val tx = "read:${current.id}:${UUID.randomUUID()}"
        transaction = tx
        mutable.value = current.copy(preparing = true, playing = false, range = null, notice = null)
        publishing = scope.launch(Dispatchers.Main.immediate) {
            try {
                startSpeaker()
                ensureActive()
                if (transaction != tx || state.value?.id != current.id) return@launch
                bus.publish(TtsRequested(text = current.current, transactionId = tx,
                    target = SpeechOutputTarget.HostDefault, fallback = SpeechOutputFallback.DISALLOW_PHONE,
                    language = current.language, speechRate = current.speed,
                    requiredProviderId = current.providerId, localOnly = !current.cloudApproved))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (transaction == tx) {
                    transaction = null
                    mutable.value = state.value?.copy(preparing = false, playing = false,
                        notice = "Reading aloud is unavailable with the current voice settings.")
                }
            }
        }
    }
}
