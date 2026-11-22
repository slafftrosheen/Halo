package com.halo.app.ui.screens.study

import com.halo.app.data.study.StudySessionRecord
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.study.StudySessionLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class StudyCourseProgress(val label: String) { DONE("Done"), NOW("Now"), LATER("Later") }

data class StudyCourseLesson(val id: String, val title: String?, val progress: StudyCourseProgress)

data class StudyCourseOutline(val version: String, val lessons: List<StudyCourseLesson>)

internal sealed interface StudyCourseRead {
    data object Loading : StudyCourseRead
    data object Unavailable : StudyCourseRead
    data class Ready(val outline: StudyCourseOutline) : StudyCourseRead
}

internal fun captureStudyCourseAuthority(): () -> Unit {
    val preferences = com.halo.app.di.ManualDependencyGraph.kidsPreferences
    val revision = preferences.adultActionRevision()
    return { preferences.requireAdultAction(revision) }
}

/** One ordered shelf of saved lessons. Completion always comes from the saved Study session. */
internal class StudyCourseOrder(private val storage: Storage) {
    private data class Order(val revision: Long, val ids: List<String>)
    private val mutex = Mutex()
    private val changes = MutableStateFlow(0)
    private val observeChanges = changes.asStateFlow()

    fun observe(records: Flow<List<StudySessionRecord>>, activeId: String?): Flow<StudyCourseRead> =
        combine(records, observeChanges) { sessions, _ ->
            val read: StudyCourseRead = StudyCourseRead.Ready(outline(sessions, activeId))
            read
        }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(StudyCourseRead.Unavailable)
            }

    suspend fun outline(records: List<StudySessionRecord>, activeId: String?): StudyCourseOutline =
        mutex.withLock { withContext(Dispatchers.IO) { project(read(), records, activeId) } }

    suspend fun move(version: String, id: String, movement: Int, activeId: String?, requireAuthority: () -> Unit,
        records: suspend () -> List<StudySessionRecord>): StudyCourseOutline = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(movement in listOf(-1, 1))
            val order = read()
            val sessions = records()
            val before = project(order, sessions, activeId)
            check(before.version == version) { "Course outline changed" }
            val ids = before.lessons.map(StudyCourseLesson::id).toMutableList()
            require(ids.size <= 10000)
            val previous = ids.indexOf(id)
            val next = previous + movement
            require(previous in ids.indices && next in ids.indices)
            val moved = ids[previous]
            ids[previous] = ids[next]
            ids[next] = moved
            val saved = Order(Math.incrementExact(order.revision), ids)
            requireAuthority()
            storage.putString(KEY, JSONObject().put("revision", saved.revision).put("ids", JSONArray(ids)).toString())
            check(read() == saved)
            changes.update { it + 1 }
            project(saved, sessions, activeId)
        }
    }

    private fun read(): Order {
        val raw = storage.getString(KEY) ?: return Order(0, emptyList())
        val json = JSONObject(raw)
        val revision = json.getLong("revision").also { require(it >= 0) }
        val list = json.getJSONArray("ids")
        require(list.length() <= 10000)
        val ids = (0 until list.length()).map { list.getString(it) }
        require(ids.distinct().size == ids.size && ids.none(String::isBlank))
        return Order(revision, ids)
    }

    private fun project(order: Order, records: List<StudySessionRecord>, activeId: String?): StudyCourseOutline {
        val byId = records.associateBy(StudySessionRecord::id)
        val known = order.ids.toSet()
        val sorted = order.ids.mapNotNull(byId::get) + records.filter { it.id !in known }
            .sortedWith(compareBy(StudySessionRecord::createdAtEpochMs, StudySessionRecord::id))
        val incomplete = sorted.filter { it.lifecycle != StudySessionLifecycle.COMPLETED }
        val current = incomplete.find { it.id == activeId }?.id ?: incomplete.firstOrNull()?.id
        val version = MessageDigest.getInstance("SHA-256").apply {
            update(order.revision.toString().toByteArray(Charsets.UTF_8))
            for (record in sorted) {
                update("\u0000${record.id}\u0000${record.updatedAtEpochMs}\u0000${record.lifecycle.name}".toByteArray(Charsets.UTF_8))
            }
        }.digest().joinToString("") { "%02x".format(it) }
        val lessons = sorted.map { session ->
            val progress = when {
                session.lifecycle == StudySessionLifecycle.COMPLETED -> StudyCourseProgress.DONE
                session.id == current -> StudyCourseProgress.NOW
                else -> StudyCourseProgress.LATER
            }
            StudyCourseLesson(session.id, session.goal?.takeIf(String::isNotBlank), progress)
        }
        return StudyCourseOutline("study-course:$version", lessons)
    }

    private companion object { const val KEY = "study_course_order_v1" }
}
