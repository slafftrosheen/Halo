package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.HaloScreenCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

internal enum class FamilyKind { LIST, PLAN, NOTE }
internal data class FamilyPost(val id: String, val kind: FamilyKind, val author: String, val text: String, val done: Boolean = false)
internal data class EmergencyCard(
    val enabled: Boolean = false,
    val allergies: String = "",
    val medicine: String = "",
    val contact: String = "",
    val language: String = "",
) {
    val hasContent get() = listOf(allergies, medicine, contact, language).any(String::isNotBlank)
}
internal data class CheckInPlan(val id: String, val recipient: String, val at: Long, val message: String, val reminderPosted: Boolean = false)

internal class HaloLifeOwner private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("halo_life_local_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableCheckIn = MutableStateFlow(readCheckIn())
    val checkIn = mutableCheckIn.asStateFlow()
    private val mutableEmergency = MutableStateFlow(readEmergency())
    val emergency = mutableEmergency.asStateFlow()
    private val mutableFamily = MutableStateFlow(readFamily())
    val family = mutableFamily.asStateFlow()

    private fun readCheckIn(): CheckInPlan? = prefs.getString("checkin_id", null)?.let { id ->
        CheckInPlan(id, prefs.getString("checkin_recipient", "").orEmpty(), prefs.getLong("checkin_at", 0),
            prefs.getString("checkin_message", "").orEmpty(), prefs.getBoolean("checkin_reminded", false))
    }
    private fun readEmergency() = EmergencyCard(prefs.getBoolean("emergency_public", false),
        prefs.getString("emergency_allergies", "").orEmpty(), prefs.getString("emergency_medicine", "").orEmpty(),
        prefs.getString("emergency_contact", "").orEmpty(), prefs.getString("emergency_language", "").orEmpty())

    private fun readFamily(): List<FamilyPost> = runCatching {
        val saved = JSONArray(prefs.getString("family", "[]"))
        List(saved.length().coerceAtMost(100)) { index ->
            val item = saved.getJSONObject(index)
            FamilyPost(item.getString("id"), FamilyKind.valueOf(item.getString("kind")), item.getString("author"),
                item.getString("text"), item.optBoolean("done"))
        }
    }.getOrDefault(emptyList())

    suspend fun scheduleCheckIn(expectedId: String?, recipient: String, hour: Int, message: String) = withContext(Dispatchers.IO) {
        require(recipient.matches(Regex("[+]?[0-9 ()-]{5,32}")) && recipient.count(Char::isDigit) >= 5)
        require(hour in 4..11 && message.isNotBlank() && message.length <= 500)
        mutex.withLock {
            val family = requireAdultToolkit(context)
            check(mutableCheckIn.value?.id == expectedId)
            val now = LocalDateTime.now()
            val sameDay = now.toLocalDate().atTime(hour, 0)
            val date = if (sameDay > now.plusMinutes(1)) sameDay else sameDay.plusDays(1)
            val at = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val plan = CheckInPlan(UUID.randomUUID().toString(), recipient, at, message)
            val request = OneTimeWorkRequestBuilder<HaloCheckInReminderWorker>()
                .setInputData(workDataOf("plan" to plan.id))
                .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("halo-check-in-${plan.id}", ExistingWorkPolicy.KEEP, request).result.get()
            requireAdultToolkit(context, family)
            check(prefs.edit().putString("checkin_id", plan.id).putString("checkin_recipient", recipient)
                .putString("checkin_message", message).putLong("checkin_at", at).putBoolean("checkin_reminded", false).commit())
            mutableCheckIn.value = plan
        }
    }

    suspend fun clearCheckIn(expectedId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireAdultToolkit(context)
            check(mutableCheckIn.value?.id == expectedId)
            check(prefs.edit().remove("checkin_id").remove("checkin_recipient").remove("checkin_message")
                .remove("checkin_at").remove("checkin_reminded").commit())
            mutableCheckIn.value = null
            notificationManager.cancel(CHECK_IN_NOTICE)
        }
    }

    suspend fun deliverCheckIn(id: String) = withContext(Dispatchers.IO) {
        val preferences = ManualDependencyGraph.kidsPreferences
        check(withTimeoutOrNull(5_000) { preferences.loadedState.first { it } } == true)
        val family = preferences.presentationRevision()
        mutex.withLock {
            val plan = mutableCheckIn.value?.takeIf { it.id == id && !it.reminderPosted } ?: return@withLock
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                preferences.enabled.value) return@withLock
            val channel = "halo_morning_check_in_v1"
            channel(channel, R.string.halo_checkin_title, NotificationManager.IMPORTANCE_DEFAULT)
            val intent = Intent(context, com.halo.app.MainActivity::class.java)
                .putExtra(HaloScreenCatalog.EXTRA_SCREEN, "checkin")
            val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.halo_ic_timer)
                .setContentTitle(context.getString(R.string.halo_checkin_title))
                .setContentText(context.getString(R.string.halo_checkin_reminder))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(PendingIntent.getActivity(context, CHECK_IN_NOTICE, intent, pendingFlags))
                .setAutoCancel(true).build()
            preferences.requirePresentation(family)
            check(!preferences.enabled.value)
            notificationManager.notify(CHECK_IN_NOTICE, notification)
            check(prefs.edit().putBoolean("checkin_reminded", true).commit())
            mutableCheckIn.value = plan.copy(reminderPosted = true)
        }
    }

    suspend fun saveEmergency(expected: EmergencyCard, card: EmergencyCard) = withContext(Dispatchers.IO) {
        require(listOf(card.allergies, card.medicine, card.contact, card.language).all { it.length <= 240 })
        require(!card.enabled || card.hasContent)
        mutex.withLock {
            requireAdultToolkit(context)
            check(mutableEmergency.value == expected)
            check(prefs.edit().putString("emergency_allergies", card.allergies).putString("emergency_medicine", card.medicine)
                .putString("emergency_contact", card.contact).putString("emergency_language", card.language)
                .putBoolean("emergency_public", card.enabled).commit())
            mutableEmergency.value = card
            val androidAllows = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!card.enabled || !androidAllows) notificationManager.cancel(EMERGENCY_NOTICE)
            else {
                val channel = "halo_emergency_card_v1"
                channel(channel, R.string.halo_emergency_title, NotificationManager.IMPORTANCE_LOW)
                val intent = Intent(context, HaloEmergencyActivity::class.java)
                notificationManager.notify(EMERGENCY_NOTICE, NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.halo_ic_timer).setContentTitle(context.getString(R.string.halo_emergency_title))
                    .setContentText(context.getString(R.string.halo_emergency_open))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true)
                    .setContentIntent(PendingIntent.getActivity(context, EMERGENCY_NOTICE, intent, pendingFlags)).build())
            }
        }
    }

    suspend fun addFamily(kind: FamilyKind, author: String, text: String) = withContext(Dispatchers.IO) {
        require(author.isNotBlank() && author.length <= 36 && text.isNotBlank() && text.length <= 1200)
        mutex.withLock {
            requireAdultToolkit(context)
            check(mutableFamily.value.size < 100)
            saveFamily(mutableFamily.value + FamilyPost(UUID.randomUUID().toString(), kind, author, text))
        }
    }

    suspend fun toggleFamily(expected: FamilyPost) = withContext(Dispatchers.IO) {
        require(expected.kind == FamilyKind.LIST)
        mutex.withLock {
            requireAdultToolkit(context)
            val current = mutableFamily.value
            check(current.any { it == expected })
            saveFamily(current.map { if (it.id == expected.id) it.copy(done = !it.done) else it })
        }
    }

    suspend fun importFamily(post: FamilyPost): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireAdultToolkit(context)
            check(mutableFamily.value.size < 100)
            val existing = mutableFamily.value.firstOrNull { it.id == post.id }
            if (existing != null) {
                check(existing == post) { "Family entry changed. Review changes on the receiving phone." }
                false
            } else {
                saveFamily(mutableFamily.value + post)
                true
            }
        }
    }

    suspend fun removeFamily(expected: FamilyPost) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireAdultToolkit(context)
            val current = mutableFamily.value
            check(current.any { it == expected })
            saveFamily(current.filterNot { it.id == expected.id })
        }
    }

    private fun saveFamily(posts: List<FamilyPost>) {
        val encoded = JSONArray(posts.map {
            JSONObject().put("id", it.id).put("kind", it.kind.name).put("author", it.author).put("text", it.text).put("done", it.done)
        })
        check(prefs.edit().putString("family", encoded.toString()).commit())
        mutableFamily.value = posts
        com.halo.app.nativeactions.EverydayWidgetUpdates.enqueue(context)
    }

    private val notificationManager get() = requireNotNull(context.getSystemService(NotificationManager::class.java))
    private fun channel(id: String, label: Int, importance: Int) {
        if (Build.VERSION.SDK_INT >= 26) notificationManager.createNotificationChannel(
            android.app.NotificationChannel(id, context.getString(label), importance))
    }
    companion object {
        private const val CHECK_IN_NOTICE = 84510
        private const val EMERGENCY_NOTICE = 84511
        private const val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        @Volatile private var instance: HaloLifeOwner? = null
        fun get(context: Context): HaloLifeOwner = instance ?: synchronized(this) {
            instance ?: HaloLifeOwner(context.applicationContext).also { instance = it }
        }
    }
}

class HaloCheckInReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("plan") ?: return Result.failure()
        return try { HaloLifeOwner.get(applicationContext).deliverCheckIn(id); Result.success() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}

/** Recheck native adult authority at the actual edit or external handoff. */
internal fun requireAdultToolkit(context: Context, expectedFamily: Long? = null): Long {
    val preferences = ManualDependencyGraph.kidsPreferences
    val revision = preferences.presentationRevision()
    check(preferences.loadedState.value && !preferences.enabled.value && (expectedFamily == null || revision == expectedFamily))
    check(context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false)
    return revision
}

/** A family entry can travel through the Android file picker. Payloads never contain chat, Memory, photos, or login data. */
internal object HaloFamilyTransfer {
    const val MAX_BYTES = 16_000
    fun encode(post: FamilyPost): ByteArray = JSONObject().put("schema", "halo-family-entry/1")
        .put("id", post.id).put("kind", post.kind.name).put("author", post.author)
        .put("text", post.text).put("done", post.done).toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): FamilyPost {
        require(bytes.size in 1..MAX_BYTES)
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        val item = JSONObject(utf8)
        require(item.keys().asSequence().toSet() == setOf("schema", "id", "kind", "author", "text", "done"))
        require(item.getString("schema") == "halo-family-entry/1")
        val id = item.getString("id")
        check(UUID.fromString(id).toString() == id)
        val kind = FamilyKind.valueOf(item.getString("kind"))
        val author = item.getString("author").trim()
        val text = item.getString("text").trim()
        require(author.isNotBlank() && author.length <= 36 && text.isNotBlank() && text.length <= 1200)
        val done = item.getBoolean("done")
        require(kind == FamilyKind.LIST || !done)
        return FamilyPost(id, kind, author, text, done)
    }
}
