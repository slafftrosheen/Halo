package com.halo.app.audio

import android.content.Context
import android.net.Uri
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

data class SupertonicPackState(val fingerprint: String? = null, val language: String = "en",
    val voice: Int = 0, val message: String? = null,
    /** Cached at explicit pack/voice transitions; Compose must not inspect native model files. */
    val selectedPackAvailable: Boolean = false,
    val audibleConfirmed: Boolean = false)
data class SupertonicInstalledPack(val fingerprint: String, val selected: Boolean)

/** Local, user-imported model bytes. Audible authorization is recorded separately per voice/language. */
class SupertonicPackStore(private val context: Context, private val storage: Storage) {
    private val root = File(context.noBackupFilesDir, "supertonic")
    val storageRoot: File get() = root
    private val mutex = Mutex()
    private val acquisitionLock = Mutex()
    private var downloadJob: Job? = null
    private val downloadMutable=MutableStateFlow<Long?>(null)
    val downloadProgress = downloadMutable.asStateFlow()
    fun cancelDownload() { downloadJob?.cancel() }
    fun hasOfficialPartial(): Boolean = File(root, "official-$OFFICIAL_SHA256.tar.bz2.part").let { it.isFile && it.length() > 0L }
    fun remainingOfficialDownloadBytes(): Long {
        val partial = File(root, "official-$OFFICIAL_SHA256.tar.bz2.part")
        val partialBytes = partial.length().takeIf { it <= OFFICIAL_BYTES } ?: 0L
        return (OFFICIAL_BYTES - partialBytes).coerceAtLeast(0L)
    }
    suspend fun discardOfficialPartial() = acquisitionLock.withLock {
        withContext(Dispatchers.IO) {
            val partial = File(root, "official-$OFFICIAL_SHA256.tar.bz2.part")
            check(!partial.exists() || partial.delete()) { "Unable to discard the partial Supertonic archive" }
        }
    }
    suspend fun discardOwnedOfficialPartial(owner: String): Boolean {
        requireOwner(owner)
        check(acquisitionLock.tryLock()) { "Another voice acquisition is active. Retry rollback later." }
        return try { withContext(Dispatchers.IO) {
            val partial = File(root, "official-$OFFICIAL_SHA256.tar.bz2.part")
            val receipt = File(root, partial.name + ".acquired")
            if (runCatching { receipt.readText() == owner }.getOrDefault(false)) {
                check(partial.canonicalFile.parentFile == root.canonicalFile && (!partial.exists() || partial.delete())) { "Unable to remove setup archive" }
                check(receipt.delete()) { "Unable to remove setup archive receipt" }
                true
            } else false
        } } finally { acquisitionLock.unlock() }
    }
    private val required = SpeechPackRegistry.supertonic.requiredFiles
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val languages = setOf("en", "lv", "ru", "pl")
    private fun snapshot(message: String? = null): SupertonicPackState {
        val selected=storage.getString("supertonic_pack_version")?.takeIf { it.isNotBlank() }
        val language=storage.getString("supertonic_language")?.takeIf { it in languages } ?: "en"
        val voice=storage.getString("supertonic_voice")?.toIntOrNull()?.takeIf { it in 0..9 } ?: 0
        // The lightweight file-presence check belongs to state construction, never
        // to each Compose recomposition or speech-provider catalogue lookup.
        val available=selected?.let { dir(it)!=null }==true
        return SupertonicPackState(selected,language,voice,message,available,
            available && selected!=null && approved(selected,language,voice))
    }
    private val mutable = MutableStateFlow(snapshot())
    val state = mutable.asStateFlow()
    fun selection() = mutable.value
    private fun dir(hash: String) = hash.takeIf { hashPattern.matches(it) }?.let { File(root,it) }
        ?.takeIf { it.isDirectory && File(it,"pack.properties").isFile && required.all { name ->
            File(it,name).isFile && File(it,name).length()>0
        } }
    private suspend fun sha(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes=ByteArray(65536)
            while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(bytes)
                    if(n<0) break
                    digest.update(bytes,0,n)
                }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun fingerprint(directory: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        required.sorted().forEach { name ->
            digest.update(name.toByteArray(Charsets.UTF_8))
            File(directory,name).inputStream().use { input ->
                val bytes=ByteArray(65536)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(bytes)
                    if(n<0) break
                    digest.update(bytes,0,n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun resolve(hash: String): QualifiedSupertonicPack? {
        val directory=dir(hash) ?: return null
        // An interrupted previous import must not crash the entire list of packs.
        val p=runCatching {
            Properties().apply { File(directory,"pack.properties").inputStream().use { load(it) } }
        }.getOrNull() ?: return null
        if(p.getProperty("fingerprint")!=hash || p.getProperty("format")!="supertonic-3-int8" ||
            p.getProperty("runtime")!=RUNTIME) return null
        val hashes=required.associateWith { p.getProperty("sha."+it) ?: return null }
        if(hashes.values.any { !hashPattern.matches(it) }) return null
        return QualifiedSupertonicPack(hash,directory,hashes,languages,(0..9).toSet(),RUNTIME,false)
    }
    fun current() = selection().fingerprint?.let(::resolve)?.let { p ->
        p.copy(audibleConfirmed=approved(p.fingerprint,selection().language,selection().voice))
    }
    fun approved(hash: String,lang: String,voice: Int): Boolean =
        storage.getString("supertonic.qualified."+hash)?.split(',')?.contains(lang+":"+voice)==true
    suspend fun installed(): List<SupertonicInstalledPack> = withContext(Dispatchers.IO) {
        mutex.withLock { root.listFiles().orEmpty().mapNotNull { file ->
            resolve(file.name)?.let { SupertonicInstalledPack(file.name,file.name==selection().fingerprint) }
        } }
    }
    /** The official publisher archive is pinned to its published bytes and SHA-256.
     * A successful download installs, but never selects or qualifies a voice.
     */
    suspend fun downloadOfficial(acquisitionOwner: String? = null): String = acquisitionLock.withLock {
        acquisitionOwner?.let(::requireOwner)
        downloadJob=currentCoroutineContext()[Job]
        val part=File(root,"official-"+OFFICIAL_SHA256+".tar.bz2.part")
        val ownership = File(root, part.name + ".acquired")
        val publicationOwner = acquisitionOwner?.takeIf { !part.exists() || runCatching { ownership.readText() == it }.getOrDefault(false) }
        try {
            withContext(Dispatchers.IO) {
                root.mkdirs()
                if (publicationOwner == null) check(!ownership.exists() || ownership.delete()) { "Unable to retain independent archive" }
                else ownership.writeText(publicationOwner)
                require(root.usableSpace > remainingOfficialDownloadBytes() + INSTALL_RESERVE_BYTES) {
                    "Not enough free space to install the Supertonic voice pack"
                }
                downloadMutable.value=part.length()
            }
            KokoroModelDownload.acquire(part,progress={ downloadMutable.value=it },
                url=OFFICIAL_URL,expectedBytes=OFFICIAL_BYTES,hash=OFFICIAL_SHA256)
            val selected=importArchive(Uri.fromFile(part), publicationOwner)
            withContext(Dispatchers.IO) { part.delete(); ownership.delete() }
            selected
        } finally {
            downloadMutable.value=null
            downloadJob=null
        }
    }

    suspend fun importArchive(uri: Uri, acquisitionOwner: String? = null): String = withContext(Dispatchers.IO) { mutex.withLock {
        root.mkdirs()
        acquisitionOwner?.let(::requireOwner)
        require(root.usableSpace>INSTALL_RESERVE_BYTES) { "Supertonic requires at least 1 GiB free space for import" }
        val temp=File(root,"incoming-"+java.util.UUID.randomUUID()).apply { mkdirs() }
        try {
            val seen=mutableSetOf<String>()
            var entries=0
            var bytesTotal=0L
            suspend fun copy(name: String,input: InputStream) {
                val leaf=name.substringAfterLast('/')
                if(leaf !in required) return
                require(seen.add(leaf)) { "Duplicate Supertonic model file" }
                File(temp,leaf).outputStream().use { output ->
                    val buffer=ByteArray(65536)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val count=input.read(buffer); if(count<0) break
                        bytesTotal+=count
                        require(bytesTotal<=768L*1024*1024) { "Supertonic archive exceeds extraction limit" }
                        output.write(buffer,0,count)
                    }
                }
            }
            val input=(if(uri.scheme=="file") File(requireNotNull(uri.path)).inputStream()
                else context.contentResolver.openInputStream(uri)) ?: error("Cannot open model archive")
            BufferedInputStream(input).use { buffered ->
                buffered.mark(4)
                val sig=ByteArray(3)
                require(buffered.read(sig)==3) { "Empty model archive" }
                buffered.reset()
                if(sig[0]==80.toByte() && sig[1]==75.toByte()) {
                    ZipInputStream(buffered).use { zip ->
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val entry=zip.nextEntry ?: break
                            require(++entries<=2048) { "Too many model entries" }
                            if(!entry.isDirectory) copy(entry.name,zip)
                            zip.closeEntry()
                        }
                    }
                } else if(sig[0]==66.toByte() && sig[1]==90.toByte() && sig[2]==104.toByte()) {
                    TarArchiveInputStream(BZip2CompressorInputStream(buffered)).use { tar ->
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val entry=tar.nextEntry ?: break
                            require(++entries<=2048 && !entry.isLink && !entry.isSymbolicLink && !entry.isSparse) {
                                "Unsafe model archive"
                            }
                            if(entry.isFile) copy(entry.name,tar)
                        }
                    }
                } else error("Import an official Supertonic .tar.bz2 or .zip archive")
            }
            require(seen==required && required.all { name ->
                val size=File(temp,name).length()
                if(name.endsWith(".onnx")) size>4096 else size>0
            }) {
                "Official Supertonic 3 INT8 pack requires all seven model files"
            }
            val hash=fingerprint(temp)
            val p=Properties().apply {
                setProperty("fingerprint",hash)
                setProperty("runtime",RUNTIME)
                setProperty("format","supertonic-3-int8")
                acquisitionOwner?.let { setProperty("acquisition.owner", it) }
                required.forEach { setProperty("sha."+it,sha(File(temp,it))) }
            }
            File(temp,"pack.properties").outputStream().use { p.store(it,null) }
            val destination=File(root,hash)
            if(!destination.exists()) require(temp.renameTo(destination)) { "Failed to install Supertonic pack" }
            else if (acquisitionOwner == null) {
                // An explicit independent import retains this pack; a previous setup may no longer remove it.
                val manifest = File(destination, "pack.properties")
                val retained = Properties().apply { manifest.inputStream().use { load(it) } }
                if (retained.remove("acquisition.owner") != null) {
                    val staged = File(destination, "pack.properties.retained")
                    staged.outputStream().use { retained.store(it, null) }
                    java.nio.file.Files.move(staged.toPath(), manifest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            mutable.value=snapshot("Imported pack; select it before preview")
            hash
        } finally { temp.deleteRecursively() }
    } }
    suspend fun select(hash: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val pack=requireNotNull(resolve(hash)) { "Incomplete Supertonic pack" }
        require(fingerprint(pack.directory)==hash) { "Supertonic model integrity mismatch" }
        storage.putString("supertonic_pack_version",hash)
        mutable.value=snapshot()
    } }
    suspend fun choose(lang: String,voice: Int) = withContext(Dispatchers.IO) { mutex.withLock {
        require(lang in languages && voice in 0..9)
        storage.putBatch(strings=mapOf("supertonic_language" to lang,"supertonic_voice" to voice.toString()))
        mutable.value=snapshot()
    } }
    suspend fun confirmAudible(hash: String,lang: String,voice: Int) =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(selection().fingerprint==hash && selection().language==lang && selection().voice==voice)
            require(resolve(hash)!=null)
            val key="supertonic.qualified."+hash
            val old=storage.getString(key)?.split(',')?.filter { it.isNotBlank() }.orEmpty().toSet()
            storage.putString(key,(old+(lang+":"+voice)).sorted().joinToString(","))
            mutable.value=snapshot("Audible voice confirmed")
        } }
    /**
     * Explicit tester attestation for one installed pack. Never invoked by installation,
     * startup, or provider selection. Hash and selection must still match at commit time.
     */
    suspend fun confirmAllAudible(hash: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(selection().fingerprint == hash) { "Supertonic selection changed" }
        val pack = requireNotNull(resolve(hash)) { "Supertonic pack is missing" }
        require(fingerprint(pack.directory) == hash) { "Supertonic pack integrity changed" }
        val allVoices = languages.flatMap { language -> (0..9).map { voice -> "$language:$voice" } }
        val key = "supertonic.qualified.$hash"
        val existing = storage.getString(key)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        storage.putString(key, (existing + allVoices).distinct().sorted().joinToString(","))
        mutable.value = snapshot("All 10 voices in EN, LV, RU and PL confirmed by tester")
    } }
    private fun requireOwner(owner: String) { require(java.util.UUID.fromString(owner).toString() == owner) }
    /** Existing atomic manifest is the acquisition receipt. No baseline difference can confer ownership. */
    suspend fun ownedBy(owner: String): Set<String> = withContext(Dispatchers.IO) { mutex.withLock {
        requireOwner(owner)
        root.listFiles().orEmpty().mapNotNull { directory ->
            val hash = directory.name
            if (!hashPattern.matches(hash) || directory.canonicalFile.parentFile != root.canonicalFile) null else runCatching {
                Properties().apply { File(directory, "pack.properties").inputStream().use { load(it) } }
                    .getProperty("acquisition.owner").takeIf { it == owner }?.let { hash }
            }.getOrNull()
        }.toSet()
    } }
    /** Called under the speech provider's pin/residency lock as well as this pack mutex. */
    suspend fun removeOwned(hash: String, owner: String): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        requireOwner(owner)
        val directory = dir(hash) ?: return@withLock false
        val properties = Properties().apply { File(directory, "pack.properties").inputStream().use { load(it) } }
        if (properties.getProperty("acquisition.owner") != owner) return@withLock false
        check(selection().fingerprint != hash) { "The pack is selected. Choose another voice before rollback." }
        check(directory.canonicalFile.parentFile == root.canonicalFile && fingerprint(directory) == hash) { "Pack identity changed; it was retained." }
        check(directory.deleteRecursively()) { "Unable to remove acquired pack" }
        storage.putString("supertonic.qualified." + hash, "")
        mutable.value = snapshot()
        true
    } }
    suspend fun remove(hash: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val directory=dir(hash) ?: return@withLock
        require(directory.deleteRecursively()) { "Supertonic pack removal failed" }
        if(selection().fingerprint==hash) storage.putString("supertonic_pack_version","")
        storage.putString("supertonic.qualified."+hash,"")
        mutable.value=snapshot()
    } }
    companion object {
        const val RUNTIME="sherpa-onnx/1.13.8"
        const val OFFICIAL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
        const val OFFICIAL_SHA256="82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"
        const val OFFICIAL_BYTES=128774318L
        const val INSTALL_RESERVE_BYTES=1024L*1024*1024
    }
}
