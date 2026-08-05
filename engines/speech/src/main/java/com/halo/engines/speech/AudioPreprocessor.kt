package com.halo.engines.speech

interface AudioPreprocessor {
    fun process(frame: AudioFrame): AudioFrame
}

class DefaultAudioPreprocessor : AudioPreprocessor {
    override fun process(frame: AudioFrame): AudioFrame {
        return frame
    }
}
