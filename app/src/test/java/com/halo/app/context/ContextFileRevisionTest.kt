package com.halo.app.context

import com.halo.app.study.StudyTextRevisionVerifier
import org.junit.Assert.*
import org.junit.Test

class ContextFileRevisionTest {
    private val original="exact file revision".toByteArray()
    private val hash=java.security.MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
    @Test fun sameReadBytesVerifyKnownRevision() { assertTrue(StudyTextRevisionVerifier.matches(original,hash,true)) }
    @Test fun changedFileCannotPretendToBeOldRevision() { assertFalse(StudyTextRevisionVerifier.matches("changed source".toByteArray(),hash,true)) }
    @Test fun unknownOrPartialRevisionFailsClosed() {
        assertFalse(StudyTextRevisionVerifier.matches(original,null,true))
        assertFalse(StudyTextRevisionVerifier.matches(original,hash,false))
    }
}
