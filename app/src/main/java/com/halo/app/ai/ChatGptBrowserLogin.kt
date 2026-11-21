package com.halo.app.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.providers.openai.ChatGptAccount
import com.halo.providers.openai.ChatGptSignInException
import com.halo.providers.openai.ChatGptSignInFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.net.ServerSocket

data class ChatGptLoginState(val busy: Boolean = false, val message: String? = null)

/** Browser owns passwords/MFA. The host owns the bounded, loopback-only OAuth callback. */
class ChatGptBrowserLogin(
    private val context: Context,
    val account: ChatGptAccount,
    private val scope: CoroutineScope,
    private val allowed: () -> Boolean,
    private val onlineAllowed: () -> Boolean,
    private val authorization: Flow<Boolean>,
    private val changed: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ChatGptLoginState())
    val state = mutableState.asStateFlow()
    @Volatile private var job: Job? = null
    @Synchronized fun start() {
        check(allowed() && onlineAllowed() && job?.isCompleted != false) { "Open Power mode and account settings first" }
        mutableState.value = ChatGptLoginState(true, "Finish signing in in your browser, then return to Halo.")
        job = scope.launch {
            var message = "ChatGPT sign-in stopped. You can try again."
            try {
                withTimeout(5 * 60_000L) {
                    coroutineScope {
                        val work = async(Dispatchers.IO) { authenticate() }
                        val watch = launch { authorization.first { !it }; work.cancel(CancellationException("Account authorization changed")) }
                        try { work.await() } finally { watch.cancel() }
                    }
                }
                changed()
                message = if (account.state.value.subscriptionAccess)
                    "ChatGPT connected. Fetch your models to choose an answer model."
                    else "Signed in, but ChatGPT did not grant subscription access to this account."
            } catch (_: TimeoutCancellationException) {
                message = "ChatGPT sign-in timed out. Start again and return to Halo after approving access."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ChatGptSignInException) {
                message = when (failure.failure) {
                    ChatGptSignInFailure.BROWSER_DENIED -> "ChatGPT sign-in was declined in the browser."
                    ChatGptSignInFailure.REGISTRATION_INCOMPLETE -> "ChatGPT did not complete app registration. Try signing in again."
                    ChatGptSignInFailure.POLICY_BLOCKED -> "Unlock your phone, open Halo and enable Power to finish signing in."
                    ChatGptSignInFailure.DNS, ChatGptSignInFailure.NETWORK ->
                        "Could not reach ChatGPT. Check your connection and try signing in again."
                    ChatGptSignInFailure.SERVER_REJECTED -> "ChatGPT rejected the sign-in request. Try signing in again."
                }
            } catch (_: android.content.ActivityNotFoundException) {
                message = "Halo could not open a browser. Set a default browser and try signing in again."
            } catch (_: Exception) {
                message = "ChatGPT sign-in could not finish. Return to Halo and try again."
            } finally {
                withContext(NonCancellable) { account.cancelSignIn() }
                mutableState.value = ChatGptLoginState(false, message)
            }
        }
    }
    fun cancel() { job?.cancel() }
    suspend fun disconnect(expectedRevision: String): Boolean {
        check(allowed() && job?.isCompleted != false && account.state.value.revision == expectedRevision) { "Account changed. Read its status again." }
        val revoked = account.disconnect(expectedRevision, revokeRemote = onlineAllowed())
        changed()
        mutableState.value = ChatGptLoginState(false, if (revoked) "ChatGPT disconnected."
            else "ChatGPT was removed from Halo. Remote disconnection could not be confirmed; remove Halo in ChatGPT settings.")
        return revoked
    }
    private suspend fun authenticate() = coroutineScope {
        val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 1000
        val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { runCatching { server.close() } }
        }
        try {
            val origin = "http://127.0.0.1:${server.localPort}"
            val browser = account.begin("$origin/auth/callback")
            withContext(Dispatchers.Main.immediate) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(browser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            while (true) {
                ensureActive()
                val socket = try { server.accept() } catch (_: java.net.SocketTimeoutException) { continue }
                val accepted = socket.use {
                    it.soTimeout = 3000
                    val input = it.getInputStream()
                    // Do not use an unbounded BufferedReader on data received from another app.
                    var count = 0
                    fun line(): String {
                        val bytes = java.io.ByteArrayOutputStream()
                        while (true) {
                            val byte = input.read()
                            require(byte >= 0 && ++count <= 16384)
                            if (byte == 10) break
                            if (byte != 13) bytes.write(byte)
                        }
                        return bytes.toString("US-ASCII")
                    }
                    val target = runCatching {
                        val first = line().split(' ')
                        require(first.size == 3 && first[0] == "GET" && first[2] in setOf("HTTP/1.0", "HTTP/1.1") && first[1].startsWith("/auth/callback?"))
                        val headers = mutableListOf<Pair<String, String>>()
                        while (true) {
                            val header = line(); if (header.isEmpty()) break
                            require(':' in header)
                            headers += header.substringBefore(':').lowercase() to header.substringAfter(':').trim()
                        }
                        require(headers.filter { header -> header.first == "host" }.map { header -> header.second } == listOf("127.0.0.1:${server.localPort}"))
                        require(headers.none { header -> header.first in setOf("content-length", "transfer-encoding") })
                        "$origin${first[1]}".takeIf { callback -> account.acceptsCallback(callback) }
                    }.getOrNull()
                    val html = if (target != null) "<!doctype html><meta charset=utf-8><title>Halo</title><p>Return to Halo to finish checking your ChatGPT connection. You can close this tab.</p>"
                        else "Invalid sign-in request. Return to the browser tab that started sign-in."
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    runCatching { it.getOutputStream().apply {
                        write(("HTTP/1.1 ${if (target != null) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray(Charsets.US_ASCII))
                        write(bytes); flush()
                    } }
                    target
                }
                if (accepted != null) {
                    // On phones the browser may retain foreground/network authority after the
                    // callback. Finish only after the user returns, within the existing timeout.
                    mutableState.value = ChatGptLoginState(true, "Return to Halo to finish checking your ChatGPT connection.")
                    finishChatGptAfterReturn(allowed, onlineAllowed) {
                        mutableState.value = ChatGptLoginState(true, "Checking your ChatGPT connection…")
                        account.complete(accepted)
                    }
                    break
                }
            }
        } finally { closeOnCancel.cancel(); server.close() }
    }
}

internal suspend fun finishChatGptAfterReturn(allowed: () -> Boolean, onlineAllowed: () -> Boolean,
    complete: suspend () -> Unit) {
    while (true) {
        currentCoroutineContext().ensureActive()
        if (!onlineAllowed()) throw CancellationException("Account authorization changed")
        if (allowed()) { complete(); return }
        delay(100)
    }
}
