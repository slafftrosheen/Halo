package com.halo.app.audio

import android.content.Context
import android.net.Uri
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

data class QualifiedSupertonicPack(
    val fingerprint: String,
    val directory: File,
    val fileSha256: Map<String,String>,
    val languages: Set<String>,
    val voices: Set<Int>,
    val qualificationRuntime: String,
    val audibleConfirmed: Boolean,
)

/**
 * Real Supertonic 3 INT8 host provider. The app owns the packs, model selection,
 * qualification and output route; this class owns only native synthesis.
 */
class SupertonicSpeechProvider(
    context: Context,
    private val storage: Storage,
    private val resources: NativeResourceCoordinator,
) : SegmentedPcmSpeechProvider, NativeResourceOwner {
    val packs = SupertonicPackStore(context,storage)
    override val metadata=ProviderMetadata(ID,"Supertonic 3",local=true)
    override val nativeResourceId=ID
    override val nativeResourceKind=NativeResourceKind.HEAVY_TTS
    override val defaultResourcePolicy get()=WorkloadResourcePolicy(RuntimeWorkloadClass.TTS,
        768L*1024*1024,true,
        if(storage.getBoolean("supertonic_warm",false)) ResidencyPreference.WARM_EJECTABLE else ResidencyPreference.ON_DEMAND,
        idleGraceMillis=storage.getInt("supertonic_idle_seconds",30).coerceIn(5,120)*1000L)
    private val lock=Mutex()
    private val generation=java.util.concurrent.atomic.AtomicLong()
    private val pinnedStreams=mutableMapOf<String,Int>()
    private var resident: OfflineTts?=null
    private var residentIdentity: String?=null
    private var validatedIdentity: String?=null
    // A provider tile may recompose frequently. Read only the cached model/approval
    // projection; the native executor verifies exact hashes on model initialization.
    fun configured(): Boolean = packs.state.value.let { it.selectedPackAvailable && it.audibleConfirmed }
    override suspend fun health()=configured()
    override fun stopCurrent() { generation.incrementAndGet() }
    private fun releaseEngine() {
        resident?.release(); resident=null; residentIdentity=null; validatedIdentity=null
    }
    override suspend fun releaseIfIdle(): Boolean {
        if(!lock.tryLock()) return false
        return try {
            if(pinnedStreams.isNotEmpty() &&
                resources.budget.value.pressure!=NativeMemoryPressure.CRITICAL) false
            else {
                // Under CRITICAL pressure a stream may need a fresh admission on its
                // next sentence; keep its pinned model ID, but shed idle native RAM.
                releaseEngine()
                true
            }
        } finally { lock.unlock() }
    }
    suspend fun importPack(uri: Uri)=packs.importArchive(uri)
    suspend fun installedPacks()=packs.installed()
    suspend fun selectPack(hash: String) = lock.withLock {
        packs.select(hash)
        if(pinnedStreams.isEmpty()) {
            releaseEngine()
            resources.release(this@SupertonicSpeechProvider)
        }
    }
    suspend fun chooseVoice(language: String,voice: Int)=packs.choose(language,voice)
    suspend fun confirmAudible(hash: String,language: String,voice: Int)=packs.confirmAudible(hash,language,voice)
    suspend fun removePack(hash: String)=lock.withLock {
        check(pinnedStreams[hash]==null) { "The selected Supertonic version is in use" }
        if(residentIdentity?.substringBefore(':')==hash) {
            releaseEngine()
            resources.release(this@SupertonicSpeechProvider)
        }
        packs.remove(hash)
    }
    suspend fun setWarm(enabled: Boolean)=lock.withLock {
        withContext(Dispatchers.IO) { storage.putBoolean("supertonic_warm",enabled) }
        if(!enabled && pinnedStreams.isEmpty()) {
            releaseEngine()
            resources.release(this@SupertonicSpeechProvider)
        }
    }
    override fun pinSpeechRequest(request: SpeechRequest): SpeechRequest {
        if(request.voiceSettings["_supertonicPinned"]==1.0) return request
        val selected=packs.selection()
        val model=request.modelId ?: selected.fingerprint
        // Pin all native and synthesis tuning for the full spoken response. A
        // settings edit while speaking must not re-create the engine mid-sentence.
        val threads=storage.getInt("supertonic_threads",2).coerceIn(1,4)
        val steps=storage.getInt("supertonic_steps",8).coerceIn(4,16)
        val preference=storage.getString("supertonic_speed")?.toDoubleOrNull() ?: 1.0
        val effectiveSpeed=(preference*(request.voiceSettings["speed"] ?: 1.0)).coerceIn(0.6,1.5)
        return request.copy(modelId=model,voiceId=request.voiceId ?: selected.voice.toString(),
            language=request.language ?: selected.language,
            voiceSettings=request.voiceSettings+mapOf(
                "_supertonicPinned" to 1.0,
                "_supertonicThreads" to threads.toDouble(),
                "_supertonicSteps" to steps.toDouble(),
                "speed" to effectiveSpeed))
    }
    override fun streamPcm(request: SpeechRequest): Flow<PcmSpeech> = flow {
        val pinned=pinSpeechRequest(request)
        val model=requireNotNull(pinned.modelId) { "Select a Supertonic pack" }
        val epoch=generation.get()
        val parts=com.halo.app.study.SentenceSegmenter.ranges(pinned.text)
        require(parts.isNotEmpty() && parts.last().end==pinned.text.trimEnd().length)
        lock.withLock { pinnedStreams[model]=(pinnedStreams[model] ?: 0)+1 }
        try {
            for(part in parts) {
                currentCoroutineContext().ensureActive()
                if(epoch!=generation.get()) throw CancellationException("Supertonic speech stopped")
                emit(synthesizeToPcm(pinned.copy(text=part.text)))
            }
        } finally {
            withContext(NonCancellable+Dispatchers.IO) {
                lock.withLock {
                    val remaining=(pinnedStreams[model] ?: 1)-1
                    if(remaining==0) pinnedStreams.remove(model) else pinnedStreams[model]=remaining
                    if(pinnedStreams.isEmpty() && !storage.getBoolean("supertonic_warm",false)) {
                        releaseEngine()
                        resources.release(this@SupertonicSpeechProvider)
                    }
                }
            }
        }
    }.buffer(0)

    private suspend fun verifiedPack(hash: String): QualifiedSupertonicPack {
        val pack=requireNotNull(packs.resolve(hash)) { "Supertonic model not installed" }
        if(validatedIdentity!=hash) withContext(Dispatchers.IO) {
            for(name in SpeechPackRegistry.supertonic.requiredFiles) {
                val file=File(pack.directory,name)
                require(file.isFile && file.canonicalPath.startsWith(pack.directory.canonicalPath+File.separator))
                val digest=MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val bytes=ByteArray(65536)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n=input.read(bytes); if(n<0) break; digest.update(bytes,0,n)
                    }
                }
                require(digest.digest().joinToString("") { "%02x".format(it) }==pack.fileSha256[name]) {
                    "Supertonic model integrity changed"
                }
            }
            validatedIdentity=hash
        }
        return pack
    }
    private fun createNative(pack: QualifiedSupertonicPack, threads: Int): OfflineTts {
        fun path(name: String)=File(pack.directory,name).absolutePath
        return OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
            supertonic=OfflineTtsSupertonicModelConfig(
                path("duration_predictor.int8.onnx"),path("text_encoder.int8.onnx"),
                path("vector_estimator.int8.onnx"),path("vocoder.int8.onnx"),
                path("tts.json"),path("unicode_indexer.bin"),path("voice.bin")),
            numThreads=threads,provider="cpu")))
    }
    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech {
        val token = generation.get()
        val pinned = pinSpeechRequest(request)
        return withContext(Dispatchers.Default) { lock.withLock {
        ensureActive()
        if (token != generation.get()) throw CancellationException("Supertonic speech stopped while queued")
        val hash=requireNotNull(pinned.modelId) { "Select a Supertonic model" }
        val language=pinned.language?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: "en"
        val voice=pinned.voiceId?.toIntOrNull() ?: error("Invalid Supertonic voice")
        val preview=pinned.voiceSettings["_supertonicPreview"]==1.0
        val threads=(pinned.voiceSettings["_supertonicThreads"]?.toInt() ?: 2).coerceIn(1,4)
        val steps=(pinned.voiceSettings["_supertonicSteps"]?.toInt() ?: 8).coerceIn(4,16)
        require(request.text.length in 1..4096)
        val pack=requireNotNull(packs.resolve(hash)) {
            "Supertonic model not installed"
        }
        require(language in pack.languages && voice in pack.voices) { "Unsupported Supertonic language/voice" }
        if(!preview && !packs.approved(hash,language,voice)) {
            throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE,
                "Confirm audible Supertonic output for this language and voice in Speech output settings")
        }
        val operationStart=System.nanoTime()
        check(resources.acquire(this@SupertonicSpeechProvider)) { "Native runtime busy" }
        val acquiredAt=System.nanoTime()
        var initMs: Long?=null
        var reused=false
        var succeeded=false
        try {
            currentCoroutineContext().ensureActive()
            if(token!=generation.get()) throw CancellationException("Supertonic speech stopped")
            resources.mark(this@SupertonicSpeechProvider,NativeResourceState.LOADING,"Supertonic TTS")
            val identity=hash+":"+threads
            reused=residentIdentity==identity && resident!=null
            if(!reused) {
                // Release previous native ownership before fingerprint validation.
                releaseEngine()
                currentCoroutineContext().ensureActive()
                val valid=verifiedPack(hash)
                val initStart=System.nanoTime()
                resident=createNative(valid,threads)
                initMs=(System.nanoTime()-initStart)/1_000_000
                residentIdentity=identity
            }
            resources.mark(this@SupertonicSpeechProvider,NativeResourceState.IN_USE,"Supertonic TTS")
            require(voice<requireNotNull(resident).numSpeakers()) {
                "Selected Supertonic voice is not exposed by this installed model"
            }
            val job=currentCoroutineContext()[Job]
            val synthesisStart=System.nanoTime()
            val audio=requireNotNull(resident).generateWithConfigAndCallback(pinned.text,
                GenerationConfig(sid=voice,speed=(pinned.voiceSettings["speed"] ?: 1.0).coerceIn(0.6,1.5).toFloat(),
                    numSteps=steps,
                    extra=mapOf("lang" to language)),
                SherpaGenerationCallback { job?.isActive!=false && token==generation.get() })
            ensureActive()
            if(token!=generation.get()) throw CancellationException("Supertonic speech stopped")
            KokoroAudioValidation.validate(audio.samples,audio.sampleRate,"Supertonic")
            require(audio.samples.size<=16*1024*1024) { "Supertonic speech segment too long" }
            val synthesisMs=(System.nanoTime()-synthesisStart)/1_000_000
            val bytes=java.nio.ByteBuffer.allocate(audio.samples.size*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            audio.samples.forEach { sample -> bytes.putShort((sample.coerceIn(-1f,1f)*32767).toInt().toShort()) }
            val audioMs=audio.samples.size.toLong()*1000L/audio.sampleRate
            WorkloadSpeechTelemetry.synthesisState.value=LocalSpeechTiming(
                hash,voice,cacheHit=false,reused=reused,initMs=initMs,
                waitMs=(acquiredAt-operationStart)/1_000_000,
                firstPcmMs=(System.nanoTime()-operationStart)/1_000_000,
                synthesisMs=synthesisMs,audioMs=audioMs,
                realTimeFactor=if(audioMs>0) synthesisMs.toDouble()/audioMs else null)
            succeeded=true
            PcmSpeech(audio.sampleRate,1,PcmEncoding.SIGNED_16_LE,listOf(bytes.array()))
        } finally {
            withContext(NonCancellable) {
                if(succeeded && (pinnedStreams.isNotEmpty() || storage.getBoolean("supertonic_warm",false)) &&
                    resources.budget.value.pressure==NativeMemoryPressure.NORMAL) {
                    resources.mark(this@SupertonicSpeechProvider,NativeResourceState.RESIDENT,"Supertonic idle")
                } else {
                    releaseEngine()
                    resources.release(this@SupertonicSpeechProvider)
                }
            }
        }
    } } }
    override suspend fun synthesize(request: SpeechRequest)=SpeechResult(0,false)
    override fun stream(text: String): Flow<com.halo.sdk.host.AudioFrame> = streamPcm(SpeechRequest(text)).transform { pcm ->
        for(chunk in pcm.chunks) {
            val samples=ShortArray(chunk.size/2)
            java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            emit(com.halo.sdk.host.AudioFrame(samples,pcm.sampleRateHz,pcm.channelCount,System.nanoTime()))
        }
    }
    override suspend fun close() {
        stopCurrent()
        withContext(NonCancellable) { lock.withLock { releaseEngine(); resources.release(this@SupertonicSpeechProvider) } }
    }
    companion object { const val ID="provider.supertonic.tts" }
}
