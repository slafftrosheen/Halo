package com.halo.app.audio

import com.halo.sdk.provider.*

object SpeechPackRegistry {
    private val required = setOf("model.onnx", "voices.bin", "tokens.txt", "espeak-ng-data")
    private fun voices(count: Int, languages: Set<String>) = (0 until count).map {
        SpeechPackVoice(it.toString(), "Kokoro ${it + 1}", languages)
    }
    val english = SpeechModelPack("kokoro-en-v0_19", KokoroSpeechProvider.ID, "k2-fsa", "Kokoro", "0.19",
        setOf("en"), voices(11, setOf("en")), "Sherpa Kokoro ONNX pack", "publisher default", required,
        "sherpa-onnx/1.13.8", KokoroModelDownload.URL, KokoroModelDownload.HASH, KokoroModelDownload.BYTES,
        512L * 1024 * 1024)
    val multilingual = SpeechModelPack("kokoro-multi-lang-v1_1", KokoroSpeechProvider.ID, "k2-fsa", "Kokoro", "1.1",
        setOf("en", "zh"), voices(103, setOf("en", "zh")), "Sherpa Kokoro ONNX pack", "publisher default", required,
        "sherpa-onnx/1.13.8", KokoroModelDownload.MULTI_URL, KokoroModelDownload.MULTI_HASH, KokoroModelDownload.MULTI_BYTES,
        768L * 1024 * 1024)
    val int8 = multilingual.copy(id = "kokoro-int8-experimental", quantization = "INT8", archiveUrl = null,
        archiveSha256 = null, archiveBytes = null, experimental = true)
    val supertonic = SpeechModelPack("supertonic-3-candidate", "provider.supertonic.tts", "Supertone", "Supertonic", "3",
        setOf("en", "lv", "ru", "pl"), (0..9).map { SpeechPackVoice(it.toString(), "Supertonic "+it, setOf("en","lv","ru","pl")) }, "Sherpa Supertonic 3 INT8 ONNX", "pack-specific",
        setOf("duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx",
            "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin"), "sherpa-onnx/1.13.8", archiveUrl=SupertonicPackStore.OFFICIAL_URL,
        archiveSha256=SupertonicPackStore.OFFICIAL_SHA256,
        archiveBytes=SupertonicPackStore.OFFICIAL_BYTES,
        estimatedWorkingBytes = 768L * 1024 * 1024,
        experimental = false)
    val candidates = listOf(english, multilingual, int8, supertonic)
}
