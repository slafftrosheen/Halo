package checks

import com.halo.sdk.runtime.wearables.media.*
import com.halo.drivers.heycyan.media.*
import com.halo.drivers.heycyan.media.repository.*
import com.halo.drivers.heycyan.media.sync.StreamingMediaDownloadStrategy
import com.halo.drivers.heycyan.wifi.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private var checks = 0
private fun verify(name: String, condition: Boolean) { check(condition) { name }; checks++; println("PASS $name") }
private suspend fun fails(block: suspend () -> Unit) = try { block(); false } catch (_: Exception) { true }
private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun photo(id: String, name: String = "$id.jpg", size: Long? = null) = HeyCyanMedia.Photo(id, name, size, null, null, null)
private val one = DeviceMediaEntry("a", "photo.jpg", "PHOTO", 3)
private val two = DeviceMediaEntry("b", "movie.mp4", "VIDEO", 3)
private class Fixture : DeviceMediaTransport {
    var manifest = listOf(one, two)
    var calls = 0
    var closed = 0
    var allowed = true
    var downloaded = mutableListOf<String>()
    var download: suspend (DeviceMediaEntry) -> DeviceMediaDownload = { DeviceMediaDownload("/fixture", hash(byteArrayOf(1,2,3)), 3) }
    override suspend fun <T> withSession(deviceId: String, allowed: () -> Boolean, block: suspend (DeviceMediaSession) -> T): T {
        check(deviceId == "device-a" && allowed()); calls++
        try { return block(object : DeviceMediaSession {
            override val files = manifest
            override suspend fun download(file: DeviceMediaEntry, progress: (Long, Long?) -> Unit): DeviceMediaDownload {
                downloaded += file.id; progress(1, file.size); return download(file)
            }
        }) } finally { closed++ }
    }
    val manager = DeviceMediaManager(this, DeviceMediaImporter { _, file, result, access ->
        check(access()); DeviceMediaSavedFile("saved-${file.id}", result.sha256, file.name, "application/octet-stream")
    }, { allowed })
    suspend fun list() = manager.refresh("device-a")
    suspend fun copy(state: DeviceMediaState) = manager.importSelected("device-a", state.sourceVersion, state.files.map { it.token }.toSet())
}
private class Connection(val data: ByteArray, private val code: Int = 200, private val length: Long = data.size.toLong(),
    private val customInput: InputStream? = null, private val closeInput: () -> Unit = {}) : HttpURLConnection(URL("http://127.0.0.1/files/x")) {
    var disconnected = false
    override fun connect() {}
    override fun disconnect() { disconnected = true; closeInput() }
    override fun usingProxy() = false
    override fun getResponseCode() = code
    override fun getContentLengthLong() = length
    override fun getInputStream() = customInput ?: ByteArrayInputStream(data)
}
private fun api(connection: Connection, limit: Long = 256L * 1024 * 1024) = DefaultHeyCyanMediaApi(InetAddress.getLoopbackAddress(),
    connectionFactory = { connection }, maxDownloadBytes = limit)

fun main() = runBlocking {
    val root = Files.createTempDirectory("halo-device-files-").toFile()
    try {
        val f = Fixture(); val listed = f.list()
        verify("browse returns opaque selections and closes session", listed.phase == "READY" && listed.files.size == 2 && f.closed == 1 && f.downloaded.isEmpty())
        verify("wrong device and stale version cannot start a transfer", fails { f.manager.importSelected("other", listed.sourceVersion, setOf(listed.files[0].token)) } &&
            fails { f.manager.importSelected("device-a", "stale", setOf(listed.files[0].token)) } && f.calls == 1)
        verify("unknown selection is rejected before network", fails { f.manager.importSelected("device-a", listed.sourceVersion, setOf("unknown")) } && f.calls == 1)
        val imported = f.copy(listed)
        verify("photo and video reach same Library owner with receipts", imported.phase == "COMPLETED" && imported.saved.map { it.objectId } == listOf("saved-a", "saved-b"))
        verify("consumed version cannot replay imports", fails { f.copy(listed) } && f.calls == 2)
        verify("stale request retains previous receipt", f.manager.state.value.saved == imported.saved)
        val g = Fixture(); val stale = g.list(); g.manifest = listOf(one.copy(size = 4), two)
        verify("changed manifest rejects before any downloads", fails { g.copy(stale) } && g.downloaded.isEmpty() && g.closed == 2)
        val partial = Fixture(); partial.download = { if (it == one) error("failed") else DeviceMediaDownload("/fixture", "a".repeat(64), 3) }
        val result = partial.copy(partial.list())
        verify("partial failure names failed item and retains successful copy", result.phase == "PARTIAL" && result.failedFiles == listOf(one) && result.saved.single().objectId == "saved-b")
        val forbidden = Fixture(); val state = forbidden.list(); forbidden.allowed = false
        verify("revoked access cannot begin copy", fails { forbidden.copy(state) } && forbidden.calls == 1)
        val tooLarge = Fixture(); tooLarge.manifest = listOf(one.copy(size = DeviceMediaManager.MAX_FILE_BYTES + 1))
        verify("oversized files stay listable but cannot download", fails { tooLarge.copy(tooLarge.list()) } && tooLarge.calls == 1)
        val unsafe = Fixture(); unsafe.manifest = listOf(one.copy(name = "../secret"))
        verify("path-bearing manifest cannot enter catalog", fails { unsafe.list() } && unsafe.manager.state.value.files.isEmpty())
        val duplicate = Fixture(); duplicate.manifest = listOf(one, one)
        verify("duplicate remote identity rejected", fails { duplicate.list() })
        val cancel = Fixture(); val initial = cancel.list(); val entered = CompletableDeferred<Unit>()
        cancel.download = { file -> if (file == two) { entered.complete(Unit); awaitCancellation() }; DeviceMediaDownload("/fixture", "b".repeat(64), 3) }
        val job = launch { cancel.copy(initial) }; entered.await()
        val running = cancel.manager.state.value
        verify("concurrent transfer rejected", fails { cancel.list() } && cancel.calls == 2)
        verify("wrong cancellation target rejected", !cancel.manager.cancel("other", running.operationId!!) && !cancel.manager.cancel("device-a", "old"))
        verify("exact operation accepts cancellation", cancel.manager.cancel("device-a", running.operationId!!))
        withTimeout(3000) { job.join() }
        verify("cancellation retains committed files and closes session", cancel.manager.state.value.phase == "CANCELLED" && cancel.manager.state.value.saved.size == 1 && cancel.closed == 2)
        verify("new operation can start after cancellation", cancel.list().phase == "READY")

        val cache = File(root, "cache"); val repo = FileHeyCyanMediaRepository(cache)
        suspend fun put(repository: FileHeyCyanMediaRepository, media: HeyCyanMedia, bytes: ByteArray, digest: String = hash(bytes)): StoredHeyCyanMedia {
            val session = repository.beginWrite(media); session.write(bytes, 0, bytes.size)
            return repository.commit(session, digest).getOrThrow()
        }
        val bytes = byteArrayOf(1, 2, 3); val saved = put(repo, photo("remote-a", "a b.jpg", 3), bytes)
        verify("cache survives repository recreation", FileHeyCyanMediaRepository(cache).find("remote-a")?.sha256 == hash(bytes))
        val collision = put(repo, photo("remote-b", "a_b.jpg", 3), bytes)
        verify("sanitized filename collisions have distinct immutable bytes", saved.localPath != collision.localPath && File(saved.localPath).exists())
        verify("hash mismatch does not replace good cached copy", fails { put(repo, photo("remote-a", size = 3), byteArrayOf(4,5,6), "0".repeat(64)) } && repo.find("remote-a")?.localPath == saved.localPath)
        verify("wrong length cannot publish", fails { put(repo, photo("short", size = 4), bytes) } && repo.find("short") == null)
        val session = repo.beginWrite(photo("abort")); session.write(bytes, 0, 3); repo.abort(session)
        verify("abort leaves no indexed or partial file", repo.find("abort") == null && cache.walkTopDown().none { it.extension == "part" })
        val foreign = repo.beginWrite(photo("foreign")); val another = FileHeyCyanMediaRepository(File(root, "another"))
        verify("foreign session cannot be committed", fails { another.commit(foreign).getOrThrow() }); repo.abort(foreign)
        val escape = FileHeyCyanMediaRepository(File(root, "escape"), object : MediaPathResolver { override fun pathFor(media: HeyCyanMedia, identity: String) = File(root, "outside").path })
        verify("custom path resolver cannot escape cache", fails { escape.beginWrite(photo("escape")) })
        File(collision.localPath).delete()
        verify("missing blob cannot authorize cache hit after restart", FileHeyCyanMediaRepository(cache).find("remote-b") == null)
        File(cache, "media-index-v1.bin").writeBytes(byteArrayOf(1,2))
        verify("corrupt index never authorizes skip and preserves blobs", FileHeyCyanMediaRepository(cache).list().isEmpty() && File(saved.localPath).exists())
        val limited = FileHeyCyanMediaRepository(File(root, "limited"), maxFileBytes = 2)
        val write = limited.beginWrite(photo("limited")); verify("stream size cap enforced before writing", fails { write.write(bytes, 0, 3) }); limited.abort(write)

        val parser = DefaultHeyCyanMediaConfigParser()
        verify("JSON object and array and line manifests supported", parser.parse("{\"files\":[\"a.jpg\"]}").getOrThrow().items.size == 1 && parser.parse("[\"b.mp4\"]").isSuccess && parser.parse("# comment\na.opus\nb.bin").getOrThrow().items.size == 2)
        verify("JSON string escapes decoded", parser.parse("[\"a\\u0020b.jpg\"]").getOrThrow().items.single().filename == "a b.jpg")
        verify("error objects and malformed JSON rejected", listOf("{\"error\":[\"oops\"]}", "[\"a\",]", "[123]", "[\"a\"]oops", "{\"files\":[\"a\"],\"x\":1}", "[\"a\\q\"]", "<html>failure</html>").all { parser.parse(it).isFailure })
        verify("manifest traversal duplicates and control characters rejected", listOf("[\"../a\"]", "[\"a\",\"a\"]", "[\"a\\n\"]").all { parser.parse(it).isFailure })
        val networkRepo = FileHeyCyanMediaRepository(File(root, "http"))
        val connection = Connection(bytes); val response = StreamingMediaDownloadStrategy(api(connection)).download(photo("http", size = 3), networkRepo) {}
        verify("HTTP stream hashes and commits through actual repository", response.isSuccess && networkRepo.find("http")?.sha256 == hash(bytes) && connection.disconnected && !connection.instanceFollowRedirects)
        val existing = File(root, "destination").also { it.writeText("old") }
        verify("partial HTTP response cannot replace existing destination", api(Connection(bytes, 206)).download(photo("partial"), existing.path).isFailure && existing.readText() == "old")
        verify("redirects are rejected", api(Connection(bytes, 302)).download(photo("redirect"), existing.path).isFailure)
        verify("HTTP header disagreement rejected", api(Connection(bytes)).download(photo("length", size = 4), existing.path).isFailure)
        verify("chunked response byte limit enforced", api(Connection(bytes, length = -1), limit = 2).download(photo("large"), existing.path).isFailure && existing.readText() == "old")
        verify("empty response rejected", api(Connection(byteArrayOf())).download(photo("empty"), existing.path).isFailure)
        verify("oversized manifest rejected", api(Connection(ByteArray(1024 * 1024 + 1), length = -1)).getMediaConfig().isFailure)
        val readStarted = CountDownLatch(1); val stopped = CountDownLatch(1)
        val input = object : InputStream() { override fun read(): Int { readStarted.countDown(); check(stopped.await(5, TimeUnit.SECONDS)); throw IOException("disconnected") } }
        val blocked = Connection(byteArrayOf(), length = -1, customInput = input, closeInput = { stopped.countDown() })
        val copying = launch(Dispatchers.Default) { api(blocked).download(photo("blocked"), existing.path) }
        withContext(Dispatchers.IO) { check(readStarted.await(3, TimeUnit.SECONDS)) }
        copying.cancel(); withTimeout(3000) { copying.join() }
        verify("cancel interrupts blocked HTTP read and keeps prior bytes", blocked.disconnected && existing.readText() == "old" && root.listFiles()!!.none { it.extension == "part" })
        verify("JPEG and MP4 identified from bytes", DeviceMediaFormat.mime(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte())) == "image/jpeg" && DeviceMediaFormat.mime("0000ftypisom".toByteArray()) == "video/mp4")
        verify("Ogg Opus recognized and raw recordings remain binary", DeviceMediaFormat.mime("OggS....OpusHead".toByteArray()) == "audio/ogg" && DeviceMediaFormat.mime(bytes) == "application/octet-stream")
        verify("short or unknown container header remains binary", DeviceMediaFormat.mime("0000ftyp".toByteArray()) == "application/octet-stream" && DeviceMediaFormat.mime("0000ftypfake".toByteArray()) == "application/octet-stream")
        println("$checks device media checks passed (synthetic host fixtures; no hardware qualification)")
    } finally { root.deleteRecursively() }
}
