package com.halo.providers.sherpa.stt

import java.io.File

/**
 * Local model inventory for Halo's optional Sherpa speech stack.
 *
 * Models are never downloaded or loaded implicitly. This catalog only describes
 * what an explicit setup action must install and provides conservative validation
 * before native Sherpa code is allowed to touch the files.
 */
object SherpaSpeechModelCatalog {
    const val WHISPER_TINY_ID = "whisper-tiny"
    const val WHISPER_TINY_DIRECTORY = "sherpa-whisper-tiny"
    const val SILERO_VAD_ID = "silero-vad"
    const val SILERO_VAD_FILE = "silero_vad.onnx"
    const val KWS_ENGLISH_ID = "kws-english-zipformer"
    const val KWS_ENGLISH_DIRECTORY = "sherpa-kws-english"
    const val KWS_ENGLISH_KEYWORDS_FILE = "halo-keywords.txt"

    data class FileSpec(
        val name: String,
        val minimumBytes: Long
    )

    data class ModelSpec(
        val id: String,
        val files: List<FileSpec>,
        val sourceUrl: String
    )

    val whisperTiny = ModelSpec(
        id = WHISPER_TINY_ID,
        files = listOf(
            FileSpec("tiny-encoder.int8.onnx", 10_000_000L),
            FileSpec("tiny-decoder.int8.onnx", 70_000_000L),
            FileSpec("tiny-tokens.txt", 100_000L)
        ),
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
    )

    val sileroVad = ModelSpec(
        id = SILERO_VAD_ID,
        files = listOf(FileSpec(SILERO_VAD_FILE, 100_000L)),
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    )

    /**
     * English Zipformer KWS model. Halo's keyword file is intentionally excluded
     * from this model inventory: the upstream archive's example keywords must not
     * silently become Halo's active wake phrase.
     */
    val kwsEnglish = ModelSpec(
        id = KWS_ENGLISH_ID,
        files = listOf(
            FileSpec("encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx", 4_000_000L),
            FileSpec("decoder-epoch-12-avg-2-chunk-16-left-64.onnx", 900_000L),
            FileSpec("joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx", 100_000L),
            FileSpec("tokens.txt", 3_000L),
            FileSpec("bpe.model", 100_000L)
        ),
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
    )

    fun isInstalled(directory: File, spec: ModelSpec): Boolean =
        directory.isDirectory && spec.files.all { fileSpec ->
            val file = File(directory, fileSpec.name)
            file.isFile && file.length() >= fileSpec.minimumBytes
        }

    fun missingFiles(directory: File, spec: ModelSpec): List<String> =
        spec.files.mapNotNull { fileSpec ->
            val file = File(directory, fileSpec.name)
            if (!file.isFile || file.length() < fileSpec.minimumBytes) fileSpec.name else null
        }
}
