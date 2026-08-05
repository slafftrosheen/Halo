package com.halo.engines.speech

sealed interface SpeechState
object Idle : SpeechState
object Listening : SpeechState
object DetectingSpeech : SpeechState
object Recording : SpeechState
object Transcribing : SpeechState
