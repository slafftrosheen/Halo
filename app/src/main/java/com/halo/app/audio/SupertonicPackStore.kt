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
    val voice: Int = 0, val message: String? = null)
data class SupertonicInstalledPack(val fingerprint: String, val selected: Boolean)

/** Local, user-imported model bytes. Audible authorization is recorded separately per voice/language. */
class SupertonicPackStore(private val context: Context, private val storage: Storage) {
    private val root = File(context.noBackupFilesDir, "supertonic")
    private val mutex = Mutex()
    private val required = SpeechPackRegistry.supertonic.requiredFiles
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val languages = setOf("en", "lv", "ru", "pl")
    private fun snapshot(message: String? = null) = SupertonicPackState(
        storage.getString("supertonic_pack_version")?.takeIf { it.isNotBlank() },
        storage.getString("supertonic_language")?.takeIf { it in languages } ?: "en",
        storage.getString("supertonic_voice")?.toIntOrNull()?.takeIf { it in 0..9 } ?: 0, message)
    private val mutable = MutableStateFlow(snapshot())
    val state = mutable.asStateFlow()
    fun selection() = mutable.value
    private fun dir(hash: String) = hash.takeIf { hashPattern.matches(it) }?.let { File(root,it) }
        ?.takeIf { it.isDirectory && File(it,"pack.properties").isFile && required.all { name ->
            File(it,name).isFile && File(it,name).length()>0
        } }
    private fun sha(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes=ByteArray(65536)
            while(true) { val n=input.read(bytes); if(n<0) break; digest.update(bytes,0,n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun fingerprint(directory: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        required.sorted().forEach { name ->
            digest.update(name.toByteArray(Charsets.UTF_8))
            File(directory,name).inputStream().use { input ->
                val bytes=ByteArray(65536)
                while(true) { val n=input.read(bytes); if(n<0) break; digest.update(bytes,0,n) }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun resolve(hash: String): QualifiedSupertonicPack? {
        val directory=dir(hash) ?: return null
        val p=Properties().apply { File(directory,"pack.properties").inputStream().use { load(it) } }
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
    suspend fun importArchive(uri: Uri): String = withContext(Dispatchers.IO) { mutex.withLock {
        root.mkdirs()
        require(root.usableSpace>1024L*1024*1024) { "Supertonic requires at least 1 GiB free space for import" }
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
            val input=context.contentResolver.openInputStream(uri) ?: error("Cannot open model archive")
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
                required.forEach { setProperty("sha."+it,sha(File(temp,it))) }
            }
            File(temp,"pack.properties").outputStream().use { p.store(it,null) }
            val destination=File(root,hash)
            if(!destination.exists()) require(temp.renameTo(destination)) { "Failed to install Supertonic pack" }
            mutable.value=snapshot("Imported. Select this pack and preview its language and voice.")
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
            mutable.value=snapshot("Audio confirmed for "+lang+" / voice "+voice)
        } }
    suspend fun remove(hash: String) = withContext(Dispatchers.IO) { mutex.withLock {
        val directory=dir(hash) ?: return@withLock
        require(directory.deleteRecursively()) { "Supertonic pack removal failed" }
        if(selection().fingerprint==hash) storage.putString("supertonic_pack_version","")
        storage.putString("supertonic.qualified."+hash,"")
        mutable.value=snapshot()
    } }
    companion object { const val RUNTIME="sherpa-onnx/1.13.8" }
}
