package com.halo.providers.sherpa.stt

import java.io.File

enum class SpeechModelFamily { WHISPER, OMNILINGUAL_CTC }
enum class SpeechModelTier { FAST, BALANCED, QUALITY, EXPERIMENTAL }

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
        val minimumBytes: Long,
        val expectedBytes: Long = 0L,
        val sha256: String? = null,
        val role: String = ""
    )

    data class ModelSpec(
        val id: String,
        val files: List<FileSpec>,
        val sourceUrl: String,
        val providerId: String = "",
        val displayName: String = id,
        val family: SpeechModelFamily = SpeechModelFamily.WHISPER,
        val tier: SpeechModelTier = SpeechModelTier.FAST,
        val directoryName: String = "",
        val repository: String = "",
        val revision: String = "",
        val multilingual: Boolean = true,
        val supportedLanguageTags: Set<String>? = null,
        val recommendedThreads: Int = 2,
        val whisperTailPaddings: Int? = null,
        val description: String = ""
    ) {
        val expectedBytes: Long get() = files.sumOf { it.expectedBytes }
        fun supportsLanguage(tag: String): Boolean = tag == "auto" || supportedLanguageTags?.contains(tag) != false
        fun file(role: String): String = files.single { it.role == role }.name
        fun effectiveThreads(processors: Int = Runtime.getRuntime().availableProcessors()): Int =
            recommendedThreads.coerceAtMost((processors - 1).coerceAtLeast(1))
    }

    private val whisperLanguages = setOf("af","am","ar","as","az","ba","be","bg","bn","bo","br","bs","ca","cs","cy","da","de","el","en","es","et","eu","fa","fi","fo","fr","gl","gu","ha","haw","he","hi","hr","ht","hu","hy","id","is","it","ja","jw","ka","kk","km","kn","ko","la","lb","ln","lo","lt","lv","mg","mi","mk","ml","mn","mr","ms","mt","my","ne","nl","nn","no","oc","pa","pl","ps","pt","ro","ru","sa","sd","si","sk","sl","sn","so","sq","sr","su","sv","sw","ta","te","tg","th","tk","tl","tr","tt","uk","ur","uz","vi","yi","yo","zh")

    val whisperTiny = ModelSpec(
        id = "whisper-tiny", providerId = "speech.sherpa_onnx.whisper_tiny",
        displayName = "Whisper Tiny", family = SpeechModelFamily.WHISPER,
        tier = SpeechModelTier.FAST, directoryName = "sherpa-whisper-tiny",
        repository = "csukuangfj/sherpa-onnx-whisper-tiny", revision = "65176e2deb88badc814a94058666cadccc29b61c",
        sourceUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c",
        supportedLanguageTags = whisperLanguages, recommendedThreads = 2,
        whisperTailPaddings = 300, description = "Lowest latency and memory use. Best for simple commands. Lower recognition accuracy.",
        files = listOf(
            FileSpec("tiny-encoder.int8.onnx", 10000000L, 12937772L, "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434", "encoder"),
            FileSpec("tiny-decoder.int8.onnx", 70000000L, 89855401L, "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925", "decoder"),
            FileSpec("tiny-tokens.txt", 100000L, 816730L, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", "tokens")
        )
    )

    val whisperBase = ModelSpec(
        id = "whisper-base", providerId = "speech.sherpa_onnx.whisper_base",
        displayName = "Whisper Base", family = SpeechModelFamily.WHISPER,
        tier = SpeechModelTier.BALANCED, directoryName = "sherpa-whisper-base",
        repository = "csukuangfj/sherpa-onnx-whisper-base", revision = "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
        sourceUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/tree/bb53ee204431c90d314c1cc08d28d23e5b7927cc",
        supportedLanguageTags = whisperLanguages, recommendedThreads = 3,
        whisperTailPaddings = 300, description = "Better recognition than Tiny while remaining practical for normal phone-side Halo use.",
        files = listOf(
            FileSpec("base-encoder.int8.onnx", 29120534L, 29120534L, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11", "encoder"),
            FileSpec("base-decoder.int8.onnx", 130672026L, 130672026L, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d", "decoder"),
            FileSpec("base-tokens.txt", 816730L, 816730L, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", "tokens")
        )
    )

    val whisperSmall = ModelSpec(
        id = "whisper-small", providerId = "speech.sherpa_onnx.whisper_small",
        displayName = "Whisper Small", family = SpeechModelFamily.WHISPER,
        tier = SpeechModelTier.QUALITY, directoryName = "sherpa-whisper-small",
        repository = "csukuangfj/sherpa-onnx-whisper-small", revision = "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
        sourceUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/tree/8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
        supportedLanguageTags = whisperLanguages, recommendedThreads = 4,
        whisperTailPaddings = 300, description = "Better multilingual recognition. Higher RAM use and slower cold start. Recommended for quality testing.",
        files = listOf(
            FileSpec("small-encoder.int8.onnx", 112442483L, 112442483L, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9", "encoder"),
            FileSpec("small-decoder.int8.onnx", 262226114L, 262226114L, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee", "decoder"),
            FileSpec("small-tokens.txt", 816730L, 816730L, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126", "tokens")
        )
    )

    val omnilingual300m = ModelSpec(
        id = "omnilingual-300m", providerId = "speech.sherpa_onnx.omnilingual_300m",
        displayName = "Omnilingual ASR 300M", family = SpeechModelFamily.OMNILINGUAL_CTC,
        tier = SpeechModelTier.EXPERIMENTAL, directoryName = "sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12",
        repository = "csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12", revision = "6abf1ece20cd2308bdb7d13cd78ec1c44fa4c094",
        sourceUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/tree/6abf1ece20cd2308bdb7d13cd78ec1c44fa4c094",
        supportedLanguageTags = null, recommendedThreads = 4,
        whisperTailPaddings = null, description = "Broad multilingual CTC recognizer. Useful for comparison with Whisper.",
        files = listOf(
            FileSpec("model.int8.onnx", 365352120L, 365352120L, "e7c4e54ee4c4c47829cc6667d5d00ed8ea7bef1dcfeef0fce766f77752a2726c", "model"),
            FileSpec("tokens.txt", 86423L, 86423L, "a7a044c52cb29cbe8b0dc1953e92cefd4ca16b0ed968177b6beab21f9a7d0b31", "tokens")
        )
    )

    val recognitionModels = listOf(whisperTiny, whisperBase, whisperSmall, omnilingual300m)
    fun byId(id: String): ModelSpec? = recognitionModels.firstOrNull { it.id == id }
    fun resolve(selection: String, installed: (ModelSpec) -> Boolean): ModelSpec? =
        if (selection == "AUTOMATIC") listOf(whisperBase, whisperTiny).firstOrNull(installed)
        else recognitionModels.firstOrNull { it.providerId == selection && installed(it) }

    val sileroVad = ModelSpec(
        id = SILERO_VAD_ID,
        files = listOf(FileSpec(SILERO_VAD_FILE, 100_000L, 643854L, "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6")),
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
            FileSpec("tokens.txt", 3_000L)
        ),
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
    )

    private fun validFile(directory: File, spec: ModelSpec, fileSpec: FileSpec): Boolean {
        val file = File(directory, fileSpec.name)
        if (!file.isFile || file.length() < fileSpec.minimumBytes) return false
        // Preserve pre-catalog Tiny/VAD/KWS installations. New recognition artifacts
        // must have been checksum-verified by the installer before becoming Ready.
        if (fileSpec.expectedBytes > 0 && file.length() != fileSpec.expectedBytes) return false
        if (spec.id == WHISPER_TINY_ID || spec.expectedBytes == 0L || spec.providerId.isBlank()) return true
        val proof = File(directory, "${fileSpec.name}.verified")
        return proof.isFile && proof.readText() == "${fileSpec.sha256}:${file.length()}:${file.lastModified()}"
    }

    fun isInstalled(directory: File, spec: ModelSpec): Boolean =
        directory.isDirectory && spec.files.all { validFile(directory, spec, it) }

    fun missingFiles(directory: File, spec: ModelSpec): List<String> =
        spec.files.filterNot { validFile(directory, spec, it) }.map { it.name }
}
