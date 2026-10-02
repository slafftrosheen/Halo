package com.halo.runtime.cognition.apps

import com.halo.cognition.language.NativeMessageIntent
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MessageDraftResolverTest {
    @Test fun twoJohnsRequireChoiceAndRetainDraftOnlyInMemory() = runTest {
        val resolver = MessageDraftResolver({ query -> when (query) {
            "John" -> MessageRecipientResolution.Choices(listOf("John Smith", "John Brown"))
            "John Brown" -> MessageRecipientResolution.Found("+37120000002")
            else -> MessageRecipientResolution.Missing
        } })
        val draft = NativeMessageIntent("John", "I'll be late.", "sms")
        assertTrue(resolver.prepare(draft) is MessageDraftResolution.NeedsChoice)
        val selected = resolver.choose("the second one") as MessageDraftResolution.Ready
        assertEquals("+37120000002", selected.intent.recipient); assertEquals(draft.body, selected.intent.body)
        assertNull(resolver.choose("the second one"))
    }
    @Test fun expiredDraftCannotOpenComposer() = runTest {
        var at = Instant.EPOCH
        val resolver = MessageDraftResolver({ MessageRecipientResolution.Choices(listOf("John Smith", "John Brown")) }, { at })
        resolver.prepare(NativeMessageIntent("John", "secret draft", "sms")); at = at.plusSeconds(121)
        assertTrue(resolver.choose("the first one") is MessageDraftResolution.Missing)
        assertNull(resolver.choose("the first one"))
    }
}
