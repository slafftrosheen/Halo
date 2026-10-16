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
    private val resources: NativeResourceCoordinator) : SegmentedPcmSpeechProvider, NativeResourceOwner {
    override val metadata=ProviderMetadata(ID,"Kokoro · on this phone",local=true)
    override val nativeResourceKind=NativeResourceKind.HEAVY_TTS
    override val nativeResourceId=ID
    override val defaultResourcePolicy get() = WorkloadResourcePolicy(RuntimeWorkloadClass.TTS,
        if (multilingualInstalled()) 768L*1024*1024 else 512L*1024*1024, true,
        if (storage.getBoolean("kokoro_warm", false)) ResidencyPreference.WARM_EJECTABLE else ResidencyPreference.ON_DEMAND,
        idleGraceMillis = storage.getInt("kokoro_idle_seconds", 30).coerceIn(5, 120) * 1000L)
    private var resident: OfflineTts? = null
    private var residentIdentity: String? = null
    private fun releaseEngine() { resident?.release(); resident=null; residentIdentity=null }
    private fun nativeEngine(directory: File): OfflineTts {
        val identity = "${directory.name}:${storage.getInt("kokoro_threads", 2).coerceIn(1,4)}"
        if (residentIdentity != identity) {
            releaseEngine()
            val start=System.nanoTime()
            resident=engine(directory); residentIdentity=identity
            android.util.Log.i("HaloSpeechTiming", "stage=TTS_INIT elapsedMs=${(System.nanoTime()-start)/1_000_000} backend=CPU")
        }
        return requireNotNull(resident)
    }
    private val root=File(context.noBackupFilesDir,"kokoro")
    private val cache=SpeechPcmCache(File(context.cacheDir,"kokoro-pcm"))
    private val lock=Mutex()
    private val streamPacks=mutableMapOf<String,Int>()
    private val mutable=MutableStateFlow(KokoroStatus(if(directory()!=null) "Installed" else "Missing",
        if(directory()!=null) "Voice pack installed. Play a test phrase to check synthesis." else "Download or import a Kokoro voice pack.",storage.getString(VERSION)))
    val state=mutable.asStateFlow()
    private val acquisitionLock=Mutex()
    private var downloadJob: Job? = null
    val downloadProgress=MutableStateFlow<Long?>(null)
    fun cancelDownload() { downloadJob?.cancel() }
    suspend fun downloadPack(packId: String = SpeechPackRegistry.multilingual.id) = acquisitionLock.withLock {
        val pack = SpeechPackRegistry.candidates.single { it.id == packId && !it.experimental }
        downloadJob=currentCoroutineContext()[Job]
        mutable.value=KokoroStatus("Downloading","Downloading selected voice version…",storage.getString(VERSION))
        val part=File(root,"official-${pack.archiveSha256}.tar.bz2.part")
        val zip=File(context.cacheDir,"kokoro-install.zip")
        try {
            withContext(Dispatchers.IO) {
                root.mkdirs()
                require(root.usableSpace > requireNotNull(pack.archiveBytes) + 2L*768*1024*1024) { "Free at least 1.5 GB to install this voice" }
            }
            downloadProgress.value=part.length()
            KokoroModelDownload.acquire(part, progress = { downloadProgress.value=it }, url=requireNotNull(pack.archiveUrl),
                expectedBytes=requireNotNull(pack.archiveBytes), hash=requireNotNull(pack.archiveSha256))
            mutable.value=KokoroStatus("Loading","Installing voice…",storage.getString(VERSION))
            KokoroModelDownload.toImportZip(part,zip)
            installPack(Uri.fromFile(zip), pack.id, pack.archiveSha256)
            part.delete()
        } catch(cancelled: CancellationException) {
            mutable.value=KokoroStatus(if(directory()!=null) "Installed" else "Missing","Download paused",storage.getString(VERSION))
            throw cancelled
        } catch(error: Exception) {
            mutable.value=KokoroStatus("Failed","Couldn't download voice. Retry to continue.",storage.getString(VERSION))
            throw error
        } finally { zip.delete(); downloadProgress.value=null; downloadJob=null }
    }
    private val generation=java.util.concurrent.atomic.AtomicLong()
    private fun directory(fingerprint: String? = storage.getString(VERSION)): File? = fingerprint?.let { version ->
        version.takeIf { Regex("[0-9a-f]{64}").matches(it) }?.let { File(root,it).takeIf { dir -> File(dir,"model.onnx").isFile && File(dir,"voices.bin").isFile && File(dir,"tokens.txt").isFile && File(dir,"espeak-ng-data").isDirectory } }
    }
    fun supportsLanguage(language: String?): Boolean = language == null || language.substringBefore('-').substringBefore('_').lowercase() in
        (if (directory()?.let { File(it,"lexicon-zh.txt").isFile } == true) setOf("en","zh") else setOf("en"))
    fun multilingualInstalled(): Boolean = directory()?.let { File(it,"lexicon-zh.txt").isFile } == true
    fun publisherPackInstalled(): Boolean = directory()!=null && storage.getString(PUBLISHER_HASH)==KokoroModelDownload.MULTI_HASH
    private fun engine(directory: File)=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
        kokoro=OfflineTtsKokoroModelConfig(model=File(directory,"model.onnx").absolutePath,
            voices=File(directory,"voices.bin").absolutePath,tokens=File(directory,"tokens.txt").absolutePath,
            dataDir=File(directory,"espeak-ng-data").absolutePath,
            lexicon=listOf("lexicon-us-en.txt","lexicon-zh.txt").map { File(directory,it) }.filter { it.isFile }.joinToString(",") { it.absolutePath }),numThreads=storage.getInt("kokoro_threads",2).coerceIn(1,4),provider="cpu"),
            ruleFsts=listOf("date-zh.fst","number-zh.fst","phone-zh.fst").map { File(directory,it) }.filter { it.isFile }.joinToString(",") { it.absolutePath },maxNumSentences=1))
    // A publisher download, manual import and removal cannot write to the pack store together.
    suspend fun importPack(uri: Uri) = acquisitionLock.withLock { installPack(uri, null, null) }
    private suspend fun installPack(uri: Uri, modelId: String?, publisherHash: String?) = withContext(Dispatchers.IO) { lock.withLock {
        root.mkdirs()
        val temp=File(root,"incoming-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val old=directory()
        mutable.value=KokoroStatus("Loading","Importing selected pack; previous model is kept until validation succeeds")
        var lease=false
        try {
            val names=mutableSetOf<String>(); var total=0L; var entries=0; var experimental=false
            (if(uri.scheme == "file") File(requireNotNull(uri.path)).inputStream() else context.contentResolver.openInputStream(uri))?.use { input -> ZipInputStream(input).use { zip ->
                while(true) {
                    ensureActive()
                    val entry=zip.nextEntry ?: break
                    if(entry.name.contains("int8")) experimental=true
                    require(++entries <= 4096) { "Too many archive entries" }
                    val relative=if(entry.name.substringBefore('/') in KokoroModelDownload.PACK_FOLDERS) entry.name.substringAfter('/') else entry.name
                    val name=if(relative=="model.int8.onnx") "model.onnx" else relative
                    require(!name.startsWith("/") && '\\' !in name && name.split('/').none { it==".." }) { "Unsafe model archive path" }
                    if(entry.isDirectory) continue
                    require(names.add(name) && names.size <= 4096) { "Duplicate files or too many entries" }
                    require(name in setOf("model.onnx","voices.bin","tokens.txt","LICENSE","README.md","lexicon-us-en.txt","lexicon-gb-en.txt","lexicon-zh.txt","date-zh.fst","number-zh.fst","phone-zh.fst") || name.startsWith("espeak-ng-data/") || name.startsWith("dict/")) { "Choose a supported Kokoro pack" }
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
            ensureActive()
            val digest=MessageDigest.getInstance("SHA-256")
            temp.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(temp).path }.forEach { file ->
                digest.update(file.relativeTo(temp).invariantSeparatorsPath.toByteArray())
                file.inputStream().use { input -> val buffer=ByteArray(8192); while(true) { ensureActive(); val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
            }
            val version=digest.digest().joinToString("") { "%02x".format(it) }
            val destination=File(root,version)
            if(destination.exists()) temp.deleteRecursively() else check(temp.renameTo(destination)) { "Model installation failed" }
            File(destination,"halo-pack.properties").outputStream().use { output ->
                java.util.Properties().apply {
                    setProperty("fingerprint",version); setProperty("modelId",modelId.orEmpty())
                    setProperty("publisherHash",publisherHash.orEmpty()); setProperty("verified","true")
                    setProperty("experimental",experimental.toString())
                }.store(output,null)
            }
            mutable.value=KokoroStatus(if(old!=null) "Installed" else "Choose a model",
                "Voice pack installed. Select it explicitly before playback.",storage.getString(VERSION))

        } catch(cancelled: CancellationException) { mutable.value=KokoroStatus(if(old!=null) "Installed" else "Missing","Import cancelled; previous model kept",storage.getString(VERSION)); throw cancelled }
        catch(error: Exception) { mutable.value=KokoroStatus("Failed",error.message ?: "Import failed; previous model kept",storage.getString(VERSION)); throw error }
        finally { temp.deleteRecursively(); if(lease) withContext(NonCancellable) { resources.release(this@KokoroSpeechProvider) } }
    } }
    suspend fun installedPacks(): List<InstalledSpeechPack> = withContext(Dispatchers.IO) { lock.withLock {
        root.listFiles().orEmpty().filter { it.isDirectory && Regex("[0-9a-f]{64}").matches(it.name) }.map { dir ->
            val manifest=java.util.Properties().apply {
                File(dir,"halo-pack.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
            }
            InstalledSpeechPack(dir.name, manifest.getProperty("modelId")?.takeIf { it.isNotBlank() },
                directory(dir.name)!=null, manifest.getProperty("verified")=="true", dir.name==storage.getString(VERSION))
        }
    } }
    private fun contentHash(dir: File): String {
        val hash=MessageDigest.getInstance("SHA-256")
        dir.walkTopDown().filter { it.isFile && it.name!="halo-pack.properties" }
            .sortedBy { it.relativeTo(dir).path }.forEach { file ->
                hash.update(file.relativeTo(dir).invariantSeparatorsPath.toByteArray())
                file.inputStream().use { input -> val bytes=ByteArray(65536); while(true) {
                    val n=input.read(bytes); if(n<0) break; hash.update(bytes,0,n)
                } }
            }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
    suspend fun selectPack(fingerprint: String) = withContext(Dispatchers.IO) { lock.withLock {
        val dir=requireNotNull(directory(fingerprint)) { "Incomplete voice pack" }
        val manifest=java.util.Properties().apply {
            File(dir,"halo-pack.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
        }
        check(manifest.getProperty("experimental")!="true") { "Experimental voice requires non-silent PCM and audible qualification" }
        check(contentHash(dir)==fingerprint) { "Voice pack fingerprint changed" }
        // A response between sentence chunks retains the previously pinned engine.
        if (streamPacks.isEmpty()) {
            releaseEngine()
            resources.release(this@KokoroSpeechProvider)
        }
        // A voice ID from the 103-voice multilingual pack is not necessarily legal for
        // the 11-voice English pack. Preserve per-pack preferences and migrate the old
        // global voice preference without making an out-of-range native request.
        val previous=storage.getString(VERSION)
        val currentVoice=storage.getString("kokoro_voice","3") ?: "3"
        val allowedVoices=if (File(dir,"lexicon-zh.txt").isFile) 103 else 11
        val requestedVoice=storage.getString("kokoro_voice.$fingerprint") ?: currentVoice
        val nextVoice=requestedVoice.toIntOrNull()?.takeIf { it in 0 until allowedVoices }?.toString() ?: "3"
        val allowedLanguages=if (allowedVoices==103) setOf("en","zh") else setOf("en")
        val priorLanguage=storage.getString("kokoro_language")
        val nextLanguage=priorLanguage?.takeIf {
            it.substringBefore('-').substringBefore('_').lowercase() in allowedLanguages
        } ?: "en"
        val updated=mutableMapOf<String,String?>(
            VERSION to fingerprint, PUBLISHER_HASH to null,
            "kokoro_voice" to nextVoice, "kokoro_language" to nextLanguage)
        if (previous!=null) updated["kokoro_voice.$previous"]=currentVoice
        storage.putBatch(strings=updated)
        mutable.value=KokoroStatus("Installed","Voice pack selected; physical acceptance pending",fingerprint)
    } }
    suspend fun removePack(fingerprint: String? = storage.getString(VERSION)) = acquisitionLock.withLock {
        withContext(Dispatchers.IO) { lock.withLock {
            val dir=directory(fingerprint) ?: return@withLock
            check(streamPacks[dir.name] == null) { "Voice version is pinned by an active response" }
            // The same mutex owns synthesis; no native engine can reference files during removal.
            if (dir.name==storage.getString(VERSION)) {
                if (streamPacks.isEmpty()) {
                    releaseEngine()
                    resources.release(this@KokoroSpeechProvider)
                }
                storage.putBatch(strings=mapOf(VERSION to null, PUBLISHER_HASH to null))
            }
            check(dir.deleteRecursively()) { "Voice pack removal failed" }
            cache.clear()
            mutable.value=KokoroStatus(if(directory()!=null) "Installed" else "Missing","Voice pack removed",storage.getString(VERSION))
        } }
    }
    override fun pinSpeechRequest(request: SpeechRequest): SpeechRequest {
        if(request.voiceSettings["_kokoroPinned"] == 1.0) return request
        return request.copy(
        modelId = request.modelId ?: storage.getString(VERSION),
        voiceId = request.voiceId ?: storage.getString("kokoro_voice","3"),
        language = request.language ?: storage.getString("kokoro_language"),
        voiceSettings = request.voiceSettings + mapOf("_kokoroPinned" to 1.0,
            "speed" to ((storage.getString("kokoro_speed")?.toDoubleOrNull() ?: 1.0) * (request.voiceSettings["speed"] ?: 1.0))))
    }
    override fun streamPcm(request: SpeechRequest): Flow<PcmSpeech> = flow {
        val pinned=pinSpeechRequest(request)
        val turn=generation.get()
        val identity=requireNotNull(pinned.modelId)
        val segments=com.halo.app.study.SentenceSegmenter.ranges(pinned.text)
        require(segments.lastOrNull()?.end==pinned.text.trimEnd().length)
        lock.withLock { streamPacks[identity]=(streamPacks[identity] ?: 0)+1 }
        try {
        for (segment in segments) {
            currentCoroutineContext().ensureActive()
            if (generation.get()!=turn) throw CancellationException("Speech stopped")
            emit(requireNotNull(synthesizeToPcm(pinned.copy(text=segment.text))))
        }
        } finally { withContext(NonCancellable+Dispatchers.Default) { lock.withLock {
            val count=(streamPacks[identity] ?: 1)-1
            if(count==0) streamPacks.remove(identity) else streamPacks[identity]=count
            if(streamPacks.isEmpty() && !storage.getBoolean("kokoro_warm",false)) {
                releaseEngine(); resources.release(this@KokoroSpeechProvider)
            }
        } } }
    }.buffer(0)
    /** Keep native resource evidence consistent when the user disables warm residency. */
    suspend fun setWarmEnabled(enabled: Boolean) = withContext(Dispatchers.IO) { lock.withLock {
        storage.putBoolean("kokoro_warm", enabled)
        if (!enabled && streamPacks.isEmpty()) {
            releaseEngine()
            resources.release(this@KokoroSpeechProvider)
        }
    } }
    fun clearCache() = cache.clear()
    override suspend fun synthesizeToPcm(request: SpeechRequest): PcmSpeech? = withContext(Dispatchers.Default) { lock.withLock {
        val timingStart=System.nanoTime()
        val pinned=pinSpeechRequest(request)
        val directory=directory(pinned.modelId) ?: throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE,"Import Kokoro in Speech output settings")
        val packManifest=java.util.Properties().apply {
            File(directory,"halo-pack.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
        }
        check(packManifest.getProperty("experimental")!="true") { "Experimental pack is not qualified for speech" }
        val lang = pinned.language
        if (lang != null && lang.substringBefore('-').substringBefore('_').lowercase() !in
            (if(File(directory,"lexicon-zh.txt").isFile) setOf("en","zh") else setOf("en"))) {
            mutable.value=KokoroStatus("Unsupported language","Use Android TTS for this language, or enable synthesis fallback.",directory.name)
            throw SpeechProviderException(SpeechErrorCode.MODEL_NOT_AVAILABLE,"Kokoro does not support the requested language")
        }
        require(request.text.length in 1..24000)
        val voice=pinned.voiceId?.toIntOrNull() ?: 3
        require(voice >= 0) { "Unsupported Kokoro voice" }
        val speed=(pinned.voiceSettings["speed"] ?: 1.0).coerceIn(0.6,1.5).toFloat()
        val key=cache.key("sherpa-1.13.8-cpu-audio-v2:${directory.name}",pinned.copy(voiceId=voice.toString()))
        cache.read(key)?.let {
            mutable.value=KokoroStatus("Ready","Cached speech is ready for playback.",directory.name)
            WorkloadSpeechTelemetry.synthesisState.value=LocalSpeechTiming(directory.name,voice,true,false,null,null,
                (System.nanoTime()-timingStart)/1_000_000,null,it.chunks.sumOf { chunk -> chunk.size.toLong() }*1000/(2L*it.sampleRateHz),null)
            return@withLock it
        }
            val turn=generation.get(); var lease=false; var native: OfflineTts?=null; var succeeded=false
        try {
            check(resources.acquire(this@KokoroSpeechProvider)) { "Native runtime busy; retry speech when the current turn finishes" }; lease=true
            resources.mark(this@KokoroSpeechProvider,NativeResourceState.LOADING,"Kokoro synthesis")
            val acquiredAt=System.nanoTime()
            val engineIdentity="${directory.name}:${storage.getInt("kokoro_threads",2).coerceIn(1,4)}"
            val reused=resident!=null && residentIdentity==engineIdentity
            val initStart=System.nanoTime()
            native=nativeEngine(directory)
            val initMs=if(reused) null else (System.nanoTime()-initStart)/1_000_000
            val synthStart=System.nanoTime()
            var firstPcmMs: Long?=null
            require(voice < native.numSpeakers()) { "Selected voice is not available in this pack" }
            resources.mark(this@KokoroSpeechProvider,NativeResourceState.IN_USE,"Kokoro synthesis")
            val chunks=mutableListOf<ByteArray>(); var rate=0; var bytes=0
            val job=currentCoroutineContext()[Job]
            val segments=com.halo.app.study.SentenceSegmenter.ranges(request.text)
            require(segments.lastOrNull()?.end == request.text.trimEnd().length) { "Speech exceeds sentence budget; choose a shorter passage" }
            for(segment in segments.map { it.text }) {
                ensureActive(); check(turn==generation.get()) { "Speech stopped" }
                val audio=native.generateWithCallback(segment,voice,speed,
                    KokoroGenerationCallback { job?.isActive != false && turn==generation.get() })
                ensureActive(); check(turn==generation.get()) { "Speech stopped" }
                KokoroAudioValidation.validate(audio.samples,audio.sampleRate)
                if(firstPcmMs==null) firstPcmMs=(System.nanoTime()-timingStart)/1_000_000
                if(rate != 0) check(rate==audio.sampleRate)
                rate=audio.sampleRate
                val buffer=java.nio.ByteBuffer.allocate(audio.samples.size*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                audio.samples.forEach { sample -> buffer.putShort((sample.coerceIn(-1f,1f)*32767).toInt().toShort()) }
                bytes+=buffer.capacity(); require(bytes <= 32*1024*1024)
                chunks+=buffer.array()
            }
            val pcm=PcmSpeech(rate,1,PcmEncoding.SIGNED_16_LE,chunks)
            val synthMs=(System.nanoTime()-synthStart)/1_000_000
            val audioMs=bytes.toLong()*1000/(2L*rate)
            WorkloadSpeechTelemetry.synthesisState.value=LocalSpeechTiming(directory.name,voice,false,reused,initMs,
                (acquiredAt-timingStart)/1_000_000,requireNotNull(firstPcmMs),synthMs,audioMs,
                if(audioMs>0) synthMs.toDouble()/audioMs else null)
            runCatching { cache.write(key,pcm) }
            mutable.value=KokoroStatus("Ready","Synthesized on this phone; playback still uses the selected output route",directory.name)
            succeeded=true
            pcm
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Exception) { mutable.value=KokoroStatus("Failed",error.message ?: "Synthesis failed",directory.name); throw error }
        finally { withContext(NonCancellable) { if(lease) {
                if (succeeded && (storage.getBoolean("kokoro_warm",false) || streamPacks.isNotEmpty()) && resources.budget.value.pressure==NativeMemoryPressure.NORMAL)
                    resources.mark(this@KokoroSpeechProvider, NativeResourceState.RESIDENT,"Kokoro idle")
                else { releaseEngine(); resources.release(this@KokoroSpeechProvider) }
            } } }
    } }
    override fun stopCurrent() { generation.incrementAndGet() }
    override suspend fun health()=withContext(Dispatchers.IO) { directory()?.let { dir ->
        val manifest=java.util.Properties().apply { File(dir,"halo-pack.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) } }
        manifest.getProperty("experimental")!="true"
    } ?: false }
    override suspend fun releaseIfIdle(): Boolean {
        if(!lock.tryLock()) return false
        return try { releaseEngine(); true } finally { lock.unlock() }
    }
    override suspend fun close() { stopCurrent(); lock.withLock { releaseEngine() }; resources.release(this) }
    override suspend fun synthesize(request: SpeechRequest)=SpeechResult(0,false)
    override fun stream(text: String): Flow<com.halo.sdk.host.AudioFrame> = streamPcm(SpeechRequest(text)).transform { pcm ->
        for (chunk in pcm.chunks) {
            val shorts=ShortArray(chunk.size/2)
            java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            emit(com.halo.sdk.host.AudioFrame(shorts,pcm.sampleRateHz,pcm.channelCount,System.nanoTime()))
        }
    }
    companion object { const val ID="provider.kokoro.tts"; private const val VERSION="kokoro_pack_version"; private const val PUBLISHER_HASH="kokoro_publisher_hash" }
}
