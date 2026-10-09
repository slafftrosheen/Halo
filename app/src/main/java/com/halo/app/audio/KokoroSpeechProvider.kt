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
import java.util.zip.ZipInputStream

data class KokoroStatus(val phase: String, val message: String, val modelVersion: String? = null)

/** Synthesis only. The existing routed player owns all playback. Models are explicitly imported. */
class KokoroSpeechProvider(private val context: Context, private val storage: Storage,
    private val resources: NativeResourceCoordinator) : PcmSpeechSynthesisProvider, NativeResourceOwner {
    override val metadata=ProviderMetadata(ID,"Kokoro · on this phone",local=true)
    override val nativeResourceKind=NativeResourceKind.HEAVY_TTS
    override val nativeResourceId=ID
    override val defaultResourcePolicy=WorkloadResourcePolicy(RuntimeWorkloadClass.TTS,512L*1024*1024,true,ResidencyPreference.ON_DEMAND)
    private val root=File(context.noBackupFilesDir,"kokoro")
    private val cache=SpeechPcmCache(File(context.cacheDir,"kokoro-pcm"))
    private val lock=Mutex()
    private val mutable=MutableStateFlow(KokoroStatus(if(directory()!=null) "Installed" else "Missing","Import an English Kokoro v0.19 model pack; no download runs automatically",storage.getString(VERSION)))
    val state=mutable.asStateFlow()
    private val generation=java.util.concurrent.atomic.AtomicLong()
    private fun directory(): File? = storage.getString(VERSION)?.let { version ->
        version.takeIf { Regex("[0-9a-f]{64}").matches(it) }?.let { File(root,it).takeIf { dir -> File(dir,"model.onnx").isFile && File(dir,"voices.bin").isFile && File(dir,"tokens.txt").isFile && File(dir,"espeak-ng-data").isDirectory } }
    }
    private fun engine(directory: File)=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
        kokoro=OfflineTtsKokoroModelConfig(model=File(directory,"model.onnx").absolutePath,
            voices=File(directory,"voices.bin").absolutePath,tokens=File(directory,"tokens.txt").absolutePath,
            dataDir=File(directory,"espeak-ng-data").absolutePath),numThreads=2,provider="cpu"),maxNumSentences=1))
    suspend fun importPack(uri: Uri) = withContext(Dispatchers.IO) { lock.withLock {
        root.mkdirs()
        val temp=File(root,"incoming-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val old=directory()
        mutable.value=KokoroStatus("Loading","Importing selected pack; previous model is kept until validation succeeds")
        var lease=false
        try {
            val names=mutableSetOf<String>(); var total=0L; var entries=0
            context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
                while(true) {
                    ensureActive()
                    val entry=zip.nextEntry ?: break
                    require(++entries <= 4096) { "Too many archive entries" }
                    val name=entry.name.removePrefix("kokoro-en-v0_19/")
                    require(!name.startsWith("/") && '\\' !in name && name.split('/').none { it==".." }) { "Unsafe model archive path" }
                    if(entry.isDirectory) continue
                    require(names.add(name) && names.size <= 4096) { "Duplicate files or too many entries" }
                    require(name in setOf("model.onnx","voices.bin","tokens.txt","LICENSE","README.md") || name.startsWith("espeak-ng-data/")) { "Choose the English Kokoro v0.19 pack" }
                    val file=File(temp,name); require(file.canonicalPath.startsWith(temp.canonicalPath+File.separator))
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        val buffer=ByteArray(8192)
                        while(true) { ensureActive(); val n=zip.read(buffer); if(n<0) break
                            total+=n; require(total <= 768L*1024*1024) { "Model pack exceeds storage budget" }; output.write(buffer,0,n) }
                    }
                }
            } } ?: error("Model pack cannot be opened")
            require(File(temp,"model.onnx").length() > 1024*1024 && File(temp,"voices.bin").length()>0 && File(temp,"tokens.txt").length()>0 && File(temp,"espeak-ng-data").isDirectory) { "Incomplete model pack" }
            check(resources.acquire(this@KokoroSpeechProvider)) { "Native runtime busy; retry import after the active turn" }; lease=true
            resources.mark(this@KokoroSpeechProvider,NativeResourceState.LOADING,"Validating explicitly imported speech pack")
            engine(temp).release()
            ensureActive()
            val digest=MessageDigest.getInstance("SHA-256")
            temp.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(temp).path }.forEach { file ->
                digest.update(file.relativeTo(temp).invariantSeparatorsPath.toByteArray())
                file.inputStream().use { input -> val buffer=ByteArray(8192); while(true) { ensureActive(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
            }
            val version=digest.digest().joinToString("") { "%02x".format(it) }
            val destination=File(root,version)
            if(destination.exists()) temp.deleteRecursively() else check(temp.renameTo(destination)) { "Model installation failed" }
            storage.putString(VERSION,version); cache.clear()
            if(old != null && old != destination) old.deleteRecursively()
            mutable.value=KokoroStatus("Installed","English pack validated. Ready is reported after successful synthesis.",version)
        } catch(cancelled: CancellationException) { mutable.value=KokoroStatus(if(old!=null) "Installed" else "Missing","Import cancelled; previous model kept",storage.getString(VERSION)); throw cancelled }
        catch(error: Exception) { mutable.value=KokoroStatus("Failed",error.message ?: "Import failed; previous model kept",storage.getString(VERSION)); throw error }
        finally { temp.deleteRecursively(); if(lease) withContext(NonCancellable) { resources.release(this@KokoroSpeechProvider) } }
    } }
    suspend fun removePack() = lock.withLock {
        directory()?.deleteRecursively(); storage.putString(VERSION,null); cache.clear()
        mutable.value=KokoroStatus("Missing","Model removed; choose Android TTS or import a pack")
    }
    fun clearCache() = cache.clear()
    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? = withContext(Dispatchers.Default) { lock.withLock {
        val directory=directory() ?: throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE,"Import Kokoro in Speech output settings")
        require(request.language == null || request.language?.substringBefore('-').equals("en",true)) { "This Kokoro pack supports English only; choose Android TTS for other languages" }
        require(request.text.length in 1..24000)
        val voice=(request.voiceId ?: storage.getString("kokoro_voice","3"))?.toIntOrNull() ?: 3
        require(voice in 0..10) { "Unsupported Kokoro voice" }
        val speed=(request.voiceSettings["speed"] ?: 1.0).coerceIn(0.6,1.5).toFloat()
        val key=cache.key("sherpa-1.13.8-cpu:${directory.name}",request.copy(voiceId=voice.toString()))
        cache.read(key)?.let { return@withLock it }
            val turn=generation.incrementAndGet(); var lease=false; var native: OfflineTts?=null
        try {
            check(resources.acquire(this@KokoroSpeechProvider)) { "Native runtime busy; retry speech when the current turn finishes" }; lease=true
            resources.mark(this@KokoroSpeechProvider,NativeResourceState.LOADING,"Kokoro synthesis")
            native=engine(directory)
            resources.mark(this@KokoroSpeechProvider,NativeResourceState.IN_USE,"Kokoro synthesis")
            val chunks=mutableListOf<ByteArray>(); var rate=0; var bytes=0
            val job=currentCoroutineContext()[Job]
            val segments=com.halo.app.study.SentenceSegmenter.ranges(request.text)
            require(segments.lastOrNull()?.end == request.text.trimEnd().length) { "Speech exceeds sentence budget; choose a shorter passage" }
            for(segment in segments.map { it.text }) {
                ensureActive(); check(turn==generation.get()) { "Speech stopped" }
                val audio=native.generateWithCallback(segment,voice,speed) { if(job?.isActive != false && turn==generation.get()) 1 else 0 }
                ensureActive(); check(turn==generation.get()) { "Speech stopped" }
                require(audio.samples.isNotEmpty() && audio.sampleRate > 0) { "Kokoro returned no audio" }
                if(rate != 0) check(rate==audio.sampleRate)
                rate=audio.sampleRate
                val buffer=java.nio.ByteBuffer.allocate(audio.samples.size*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                audio.samples.forEach { sample -> buffer.putShort((sample.coerceIn(-1f,1f)*32767).toInt().toShort()) }
                bytes+=buffer.capacity(); require(bytes <= 32*1024*1024)
                chunks+=buffer.array()
            }
            val pcm=PcmSpeech(rate,1,PcmEncoding.SIGNED_16_LE,chunks)
            runCatching { cache.write(key,pcm) }
            mutable.value=KokoroStatus("Ready","Synthesized on this phone; playback still uses the selected output route",directory.name)
            pcm
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) { mutable.value=KokoroStatus("Failed",error.message ?: "Synthesis failed",directory.name); throw error }
        finally { withContext(NonCancellable) { native?.release(); if(lease) resources.release(this@KokoroSpeechProvider) } }
    } }
    override fun stopCurrent() { generation.incrementAndGet() }
    override suspend fun health()=directory()!=null
    override suspend fun releaseIfIdle(): Boolean { if(!lock.tryLock()) return false; lock.unlock(); return true }
    override suspend fun synthesize(request: SpeechRequest)=SpeechResult(0,false)
    override fun stream(text: String)=emptyFlow<com.halo.sdk.host.AudioFrame>()
    companion object { const val ID="provider.kokoro.tts"; private const val VERSION="kokoro_pack_version" }
}
