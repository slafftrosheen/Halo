package com.halo.app.privacy

import android.content.Context
import com.halo.sdk.provider.AiExecutionLocation
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.RemoteProviderAnswerEvent
import com.halo.sdk.runtime.RemoteProviderRequestEvent
import com.halo.sdk.runtime.ProviderResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class SharedAnswerRequest(val recordedAt: Long, val service: String, val answered: Boolean, val requestKey: String = "", val providerKey: String = "")

/** A bounded local receipt of observed remote inference, never prompt/response or credentials.
 * Starts are recorded before execution, including cancelled requests; they do not prove delivery.
 */
class SharedAnswerHistory(context: Context, eventBus: EventBus, scope: CoroutineScope,
    private val destination: (String) -> Pair<String, AiExecutionLocation>?) {
    private val disk = context.applicationContext.getSharedPreferences("halo_shared_answer_history", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<List<SharedAnswerRequest>>(emptyList())
    val state = mutable.asStateFlow()
    private val initialized = scope.async(Dispatchers.IO) {
        mutable.value = runCatching {
            val array = JSONArray(disk.getString("entries", "[]"))
            (0 until array.length()).map { index -> array.getJSONObject(index).let {
                SharedAnswerRequest(it.getLong("at"), it.getString("service"), it.getBoolean("answered"), it.optString("key"), it.optString("provider"))
            } }.retained()
        }.getOrDefault(emptyList())
        persist(mutable.value) // Remove expired records from disk during restore, even without new requests.
    }
    init {
        scope.launch(Dispatchers.IO) {
            eventBus.events().collect { event ->
                when (event) {
                    is RemoteProviderRequestEvent -> {
                        val target = destination(event.providerId) ?: return@collect
                        if (target.second != AiExecutionLocation.REMOTE) return@collect
                        initialized.await()
                        mutex.withLock {
                            val key = requestKey(event.requestId)
                            if (mutable.value.none { it.requestKey == key }) {
                                val next = (listOf(SharedAnswerRequest(event.timestamp, target.first.take(100), false, key, requestKey(event.providerId))) + mutable.value).retained()
                                if (persist(next)) mutable.value = next
                            }
                        }
                    }
                    is RemoteProviderAnswerEvent -> finish(event.requestId, true, requestKey(event.providerId))
                    is ProviderResult -> finish(event.requestId, event.success)
                    else -> Unit
                }
            }
        }
    }
    init {
        scope.launch(Dispatchers.IO) {
            initialized.await()
            while (isActive) {
                delay(60L * 60 * 1000)
                mutex.withLock {
                    val next = mutable.value.retained()
                    if (next != mutable.value && persist(next)) mutable.value = next
                }
            }
        }
    }
    private suspend fun finish(requestId: String, success: Boolean, providerKey: String? = null) {
        initialized.await()
        mutex.withLock {
            val key = requestKey(requestId)
            if (mutable.value.none { it.requestKey == key && (providerKey == null || it.providerKey == providerKey) }) return@withLock
            val next = mutable.value.map { if (it.requestKey == key && (providerKey == null || it.providerKey == providerKey)) it.copy(answered = it.answered || success) else it }.retained()
            if (persist(next)) mutable.value = next
        }
    }
    private fun requestKey(requestId: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(requestId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    suspend fun clear() = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            check(persist(emptyList())) { "Unable to clear shared request history" }
            mutable.value = emptyList()
        }
    }
    private fun persist(entries: List<SharedAnswerRequest>): Boolean = runCatching {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("at", it.recordedAt).put("service", it.service).put("answered", it.answered).put("key", it.requestKey).put("provider", it.providerKey)) }
        disk.edit().putString("entries", array.toString()).commit()
    }.getOrDefault(false)
    private fun List<SharedAnswerRequest>.retained(): List<SharedAnswerRequest> {
        val oldest = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return filter { it.recordedAt >= oldest }.sortedByDescending { it.recordedAt }.take(1000)
    }
}
