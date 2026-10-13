package com.halo.app.nativeactions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** On-demand Gmail consent. No silent mail reads on startup and no persisted OAuth tokens. */
class NativeGoogleMailActivity : ComponentActivity() {
    private val requestId get() = intent.getStringExtra("requestId").orEmpty()
    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (!NativeActivityRequests.active(requestId)) { finish(); return@registerForActivityResult }
        if (result.resultCode != RESULT_OK) { fail("Google authorization cancelled. No mail read."); return@registerForActivityResult }
        try { read(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)) }
        catch (_: Exception) { fail("Google did not grant Gmail access. No mail read.") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (!NativeActivityRequests.active(requestId)) { finish(); return }
        if (savedInstanceState != null) return // Never re-authorize or re-read after recreation.
        android.app.AlertDialog.Builder(this).setTitle(nativeUi("Read Gmail?"))
            .setMessage(nativeUi("Authorize read-only access for this request. Halo keeps the access token in memory and does not send email content to a model in this flow."))
            .setNegativeButton(android.R.string.cancel) { _, _ -> fail("Gmail request cancelled.") }
            .setPositiveButton(nativeUi("Continue")) { _, _ ->
                if (!NativeActivityRequests.active(requestId)) { finish(); return@setPositiveButton }
                Identity.getAuthorizationClient(this).authorize(AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(GMAIL_READONLY))).build())
                    .addOnSuccessListener { result ->
                        if (!NativeActivityRequests.active(requestId) || isDestroyed) return@addOnSuccessListener
                        if (result.hasResolution()) {
                            val pending = result.pendingIntent
                            if (pending == null) fail("Google authorization unavailable.")
                            else consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        } else read(result)
                    }.addOnFailureListener {
                        fail("Gmail authorization unavailable. Register Halo's package and signing certificate as an Android OAuth client, enable Gmail API, and configure the consent screen/test users.")
                    }
            }.setOnCancelListener { fail("Gmail request cancelled.") }.show()
    }
    private fun read(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || GMAIL_READONLY !in result.grantedScopes) { fail("Read-only Gmail scope was not granted."); return }
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { GoogleMailReader().read(token,
                    intent.getStringExtra("query").orEmpty(), intent.getStringExtra("messageId")) { NativeActivityRequests.active(requestId) } }
                if (!NativeActivityRequests.active(requestId)) { finish(); return@launch }
                // Mail text stays in this secure local UI. The generic result tool cannot send it to an LLM.
                NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(true, "Read-only Gmail request completed. Private results are displayed in Halo; mail content is withheld from tool results."))
                setContentView(android.widget.ScrollView(this@NativeGoogleMailActivity).apply {
                    addView(android.widget.TextView(this@NativeGoogleMailActivity).apply { this.text = text; setPadding(32,32,32,32) })
                })
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { fail("Gmail request failed, access was revoked, or the preview was too large. No inbox contents guessed.") }
        }
    }
    private fun fail(message: String) {
        NativeActivityRequests.complete(requestId, NativeActivityRequests.Receipt(false, message))
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }
    companion object { const val GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly" }
}
