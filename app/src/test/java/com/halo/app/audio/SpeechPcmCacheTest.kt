package com.halo.app.audio

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SpeechPcmCacheTest {
    @Test fun modelVoiceLanguageAndSpeedHaveSeparateCacheIdentity() {
        val dir=Files.createTempDirectory("speech-cache").toFile()
        try {
            val cache=SpeechPcmCache(dir); val request=SpeechRequest("private passage",voiceId="3",language="en",voiceSettings=mapOf("speed" to 1.0))
            val key=cache.key("model-v1:cpu",request)
            assertNotEquals(key,cache.key("model-v2:cpu",request))
            assertNotEquals(key,cache.key("model-v1:cpu",request.copy(voiceId="4")))
            assertNotEquals(key,cache.key("model-v1:cpu",request.copy(language="lv")))
            assertNotEquals(key,cache.key("model-v1:cpu",request.copy(voiceSettings=mapOf("speed" to 1.25))))
            val pcm=PcmSpeech(24000,1,PcmEncoding.SIGNED_16_LE,listOf(byteArrayOf(1,2,3,4)))
            cache.write(key,pcm); assertArrayEquals(pcm.chunks.single(),cache.read(key)!!.chunks.single())
            assertFalse(dir.listFiles()!!.any {it.name.contains("private")})
        } finally {dir.deleteRecursively()}
    }
    @Test fun quotaExpiryAndCorruptEntriesCannotReturnAudio() {
        val dir=Files.createTempDirectory("speech-cache").toFile()
        try {
            val cache=SpeechPcmCache(dir,maxBytes=25)
            val pcm=PcmSpeech(24000,1,PcmEncoding.SIGNED_16_LE,listOf(byteArrayOf(1,2,3,4)))
            cache.write("first",pcm); cache.write("second",pcm)
            assertTrue(dir.listFiles()!!.sumOf {it.length()} <= 25)
            java.io.File(dir,"broken.pcm").writeText("broken")
            assertNull(cache.read("broken"))
            val remaining=dir.listFiles()!!.singleOrNull(); remaining?.setLastModified(1)
            assertNull(remaining?.let {cache.read(it.nameWithoutExtension)})
        } finally {dir.deleteRecursively()}
    }
}
