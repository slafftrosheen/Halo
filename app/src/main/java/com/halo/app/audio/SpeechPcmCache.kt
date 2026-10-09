package com.halo.app.audio

import com.halo.sdk.provider.*
import java.io.*
import java.security.MessageDigest

/** Private, bounded PCM cache. A versioned model/backend identity is required for each key. */
class SpeechPcmCache(private val root: File, private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = 64L*1024*1024, private val ttlMs: Long = 7L*24*60*60*1000) {
    fun key(identity: String, request: SpeechRequest): String = MessageDigest.getInstance("SHA-256")
        .digest("$identity|${request.voiceId}|${request.language}|${request.voiceSettings.toSortedMap()}|${request.text}".toByteArray())
        .joinToString("") { "%02x".format(it) }
    @Synchronized fun read(key: String): PcmSpeech? {
        prune()
        val file=File(root,"$key.pcm")
        if(!file.isFile) return null
        return try { DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt()==0x48414c31)
            val rate=input.readInt(); val channels=input.readInt(); val encoding=PcmEncoding.entries[input.readInt()]
            val size=input.readInt(); require(size in 1..32*1024*1024 && file.length()==size.toLong()+20)
            val data=ByteArray(size); input.readFully(data)
            PcmSpeech(rate,channels,encoding,listOf(data))
        } } catch (_: Exception) { file.delete(); null }
    }
    @Synchronized fun write(key: String, pcm: PcmSpeech) {
        root.mkdirs()
        val target=File(root,"$key.pcm"); val temp=File(root,"$key.tmp")
        try {
            DataOutputStream(temp.outputStream().buffered()).use { output ->
                output.writeInt(0x48414c31); output.writeInt(pcm.sampleRateHz); output.writeInt(pcm.channelCount); output.writeInt(pcm.encoding.ordinal)
                output.writeInt(pcm.chunks.sumOf { it.size }); pcm.chunks.forEach(output::write)
            }
            check(temp.renameTo(target)) { "Unable to retain speech cache" }
        } finally { temp.delete() }
        prune()
    }
    @Synchronized fun clear() { root.listFiles()?.forEach { it.delete() } }
    private fun prune() {
        root.listFiles()?.filter { it.lastModified() < now()-ttlMs || it.extension=="tmp" }?.forEach { it.delete() }
        val files=root.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        var size=files.sumOf { it.length() }
        files.forEach { if(size > maxBytes) { val bytes=it.length(); if(it.delete()) size-=bytes } }
    }
}
