package com.halo.app.di.tools.host

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MessagingComposeIntentTest {
    @Test fun smsOpensComposerWithActualRecipientAndDraft() {
        val intent = MessagingTools.composeIntent("+37120000000", "I'll be ten minutes late.", false)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto", intent.data!!.scheme)
        assertEquals("+37120000000", android.net.Uri.decode(intent.data!!.schemeSpecificPart))
        assertEquals("I'll be ten minutes late.", intent.getStringExtra("sms_body"))
    }
    @Test fun emailUsesSendToRatherThanBroadAttachmentShare() {
        val intent = MessagingTools.composeIntent("person@example.com", "Draft", true)
        assertEquals(Intent.ACTION_SENDTO, intent.action); assertEquals("mailto", intent.data!!.scheme)
        assertEquals("Draft", intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
