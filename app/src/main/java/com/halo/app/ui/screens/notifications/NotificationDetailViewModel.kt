package com.halo.app.ui.screens.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.halo.app.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun replyOutcomeCopy(outcome: NotificationReplyOutcome, app: String): String = when (outcome) {
    NotificationReplyOutcome.DISPATCHED -> "Reply handed to $app\nDelivery not confirmed"
    NotificationReplyOutcome.NOT_LIVE -> "This notification is no longer available for reply"
    NotificationReplyOutcome.NO_REPLY_ACTION -> "The reply action changed or is no longer available. Review it again"
    NotificationReplyOutcome.EMPTY_REPLY -> "Enter a reply of 1–500 characters"
    NotificationReplyOutcome.FAILED -> "Could not hand the reply to $app"
}
data class ReplyConfirmation(val text: String, val fingerprint: String)
class NotificationDetailViewModel(private val key: String, val source: NotificationInboxSource,
    private val context: Context, private val dispatcher: NotificationReplyDispatcher) : ViewModel() {
    val item = source.state.map { it.items.firstOrNull { row -> row.key == key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val draft = MutableStateFlow("")
    val composing = MutableStateFlow(false)
    val confirmation = MutableStateFlow<ReplyConfirmation?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    fun edit(text: String) { draft.value = text.take(500); confirmation.value = null }
    fun confirmReply() {
        val reply = item.value?.reply as? ReplyCapability.Available ?: return
        if (draft.value.trim().isNotEmpty()) confirmation.value = ReplyConfirmation(draft.value.trim(), reply.fingerprint)
    }
    fun send() {
        val confirmed = confirmation.value ?: return
        confirmation.value = null
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                source.refresh()
                val current = source.currentActionable(key)
                val reply = current?.reply as? ReplyCapability.Available
                val live = LiveNotificationOwner.connected() && LiveNotificationOwner.snapshot()?.any { it.key == key } == true
                val outcome = if (current == null || !live || reply?.fingerprint != confirmed.fingerprint) NotificationReplyOutcome.NOT_LIVE
                    else dispatcher.dispatch(context, key, confirmed.text, confirmed.fingerprint)
                message.value = replyOutcomeCopy(outcome, current?.appLabel ?: "the app")
                if (outcome == NotificationReplyOutcome.DISPATCHED) { draft.value = ""; composing.value = false }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "The action could not be completed" }
            finally { busy.value = false; source.refresh() }
        }
    }
    fun dismiss() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = source.currentActionable(key)
                message.value = if (current?.dismissible == true && LiveNotificationOwner.dismiss(key)) "Dismissal requested"
                    else "This notification is no longer available for dismissal"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "The action could not be completed" }
            finally { busy.value = false; source.refresh() }
        }
    }
    fun leave() { draft.value = ""; composing.value = false; confirmation.value = null }
    class Factory(private val key: String, private val source: NotificationInboxSource, private val context: Context,
        private val dispatcher: NotificationReplyDispatcher) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationDetailViewModel(key, source, context.applicationContext, dispatcher) as T
    }
}
