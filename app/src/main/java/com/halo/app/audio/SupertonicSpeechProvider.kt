package com.halo.app.audio

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Host installation boundary. No default paths, model downloads or implicit qualification. */
data class QualifiedSupertonicPack(
    val fingerprint: String,
    val directory: File,
    val fileSha256: Map<String,String>,
    val languages: Set<String>,
    val voices: Set<Int>,
    val qualificationRuntime: String,
    val audibleConfirmed: Boolean,
)

/** Separate native model format; never feeds Supertonic assets to Kokoro. */
class SupertonicSpeechProvider(private val resources: NativeResourceCoordinator,
    private val installedSelection: () -> QualifiedSupertonicPack? = { null }) : SegmentedPcmSpeechProvider, NativeResourceOwner {
    override val metadata=ProviderMetadata(ID,"Supertonic 3",local=true)
    override val nativeResourceId=ID
    override val nativeResourceKind=NativeResourceKind.HEAVY_TTS
    override val defaultResourcePolicy=WorkloadResourcePolicy(RuntimeWorkloadClass.TTS,768L*1024*1024,true)
    private val lock=Mutex()
    private val generation=java.util.concurrent.atomic.AtomicLong()
    fun configured()=installedSelection()?.let { it.audibleConfirmed && it.qualificationRuntime=="sherpa-onnx/1.13.8" } == true
    override suspend fun health()=configured()
    override fun stopCurrent() { generation.incrementAndGet() }
    override suspend fun releaseIfIdle()=true // No native engine outlives its guarded call.
    override fun pinSpeechRequest(request: SpeechRequest)=request.copy(modelId=request.modelId ?: installedSelection()?.fingerprint)
    override fun streamPcm(request: SpeechRequest): Flow<PcmSpeech> = flow {
        val pinned=pinSpeechRequest(request)
        val token=generation.get()
        for(segment in com.halo.app.study.SentenceSegmenter.ranges(pinned.text)) {
            if(token!=generation.get()) throw CancellationException("Speech stopped")
            emit(requireNotNull(synthesizeToPcm(pinned.copy(text=segment.text))))
        }
    }.buffer(0)
    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech = withContext(Dispatchers.Default) { lock.withLock {
        val pack=installedSelection()?.takeIf { configured() && (request.modelId==null || request.modelId==it.fingerprint) }
            ?: throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE,"Supertonic needs an installed, qualified pack")
        val language=request.language?.substringBefore('-') ?: "en"
        require(language in pack.languages)
        val voice=request.voiceId?.toIntOrNull() ?: pack.voices.minOrNull() ?: error("No qualified voice")
        require(voice in pack.voices)
        require(request.text.length in 1..4096 && pack.fingerprint.matches(Regex("[a-f0-9]{64}")))
        val required=SpeechPackRegistry.supertonic.requiredFiles
        require(pack.fileSha256.keys.containsAll(required))
        withContext(Dispatchers.IO) { required.forEach { name ->
            val file=File(pack.directory,name)
            require(file.canonicalPath.startsWith(pack.directory.canonicalPath+File.separator) && file.isFile)
            val hash=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val bytes=ByteArray(65536); while(true) {
                ensureActive(); val n=input.read(bytes); if(n<0) break; hash.update(bytes,0,n)
            } }
            require(hash.digest().joinToString("") { "%02x".format(it) }==pack.fileSha256[name])
        } }
        check(resources.acquire(this@SupertonicSpeechProvider)) { "Native runtime busy" }
        var native: OfflineTts?=null
        val token=generation.get()
        try {
            resources.mark(this@SupertonicSpeechProvider,NativeResourceState.LOADING,"Supertonic")
            fun path(name: String)=File(pack.directory,name).absolutePath
            native=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
                supertonic=OfflineTtsSupertonicModelConfig(path("duration_predictor.onnx"),path("text_encoder.onnx"),
                    path("vector_estimator.onnx"),path("vocoder.onnx"),path("tts.json"),path("unicode_indexer.bin"),path("voice.bin")),
                numThreads=2,provider="cpu")))
            resources.mark(this@SupertonicSpeechProvider,NativeResourceState.IN_USE,"Supertonic")
            val job=currentCoroutineContext()[Job]
            val audio=native.generateWithConfigAndCallback(request.text,GenerationConfig(sid=voice,
                speed=(request.voiceSettings["speed"] ?: 1.0).coerceIn(.6,1.5).toFloat(),extra=mapOf("lang" to language))) {
                if(job?.isActive!=false && token==generation.get()) 1 else 0
            }
            ensureActive(); if(token!=generation.get()) throw CancellationException("Speech stopped")
            KokoroAudioValidation.validate(audio.samples,audio.sampleRate)
            require(audio.samples.size<=16*1024*1024)
            val buffer=java.nio.ByteBuffer.allocate(audio.samples.size*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            audio.samples.forEach { buffer.putShort((it.coerceIn(-1f,1f)*32767).toInt().toShort()) }
            PcmSpeech(audio.sampleRate,1,PcmEncoding.SIGNED_16_LE,listOf(buffer.array()))
        } finally { withContext(NonCancellable) { try { native?.release() } finally { resources.release(this@SupertonicSpeechProvider) } } }
    } }
    override suspend fun synthesize(request: SpeechRequest)=SpeechResult(0,false)
    override fun stream(text: String)=emptyFlow<com.halo.sdk.host.AudioFrame>()
    companion object { const val ID="provider.supertonic.tts" }
}
