package com.halo.app.ui.screens.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.provider.*
import com.halo.app.ai.readRuntimeAiContextPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

data class KidsProjectState(
    val loading: Boolean = true,
    val idea: String = "",
    val kind: String? = null,
    val details: String = "",
    val canvasId: String? = null,
    val canvasReady: Boolean = false,
    val keptHash: String? = null,
    val keptWhere: String? = null,
    val updatedAt: Long = 0,
    val operation: String? = null,
    val draft: String = "",
    val editing: Boolean = false,
    val noteId: String? = null,
    val busy: Boolean = false,
    val notice: String? = null,
    val executionLabel: String? = null,
    val authorship: String = "SELF",
    val providerId: String? = null,
    val modelId: String? = null,
)

data class SavedKidsProject(
    val noteId: String, val title: String, val kind: String?, val details: String,
    val canvasId: String?, val canvasReady: Boolean, val keptHash: String?,
    val keptWhere: String?, val authorship: String, val providerId: String?, val modelId: String?, val updatedAt: Long,
)

/** Parent-controlled provider routing; Keep is the only operation that creates/updates a child's note. */
class KidsProjectViewModel : ViewModel() {
    private val mutable = MutableStateFlow(KidsProjectState())
    val state = mutable.asStateFlow()
    private val savedProjects = MutableStateFlow<List<SavedKidsProject>>(emptyList())
    val library = savedProjects.asStateFlow()
    private val libraryKey = "kids_project_library_v1"
    private var libraryReady = true
    private var generation: Job? = null
    private var request: String? = null
    private var saving = false
    private val writes = Mutex()
    private var editSave: Job? = null
    private val key = "kids_project_draft_v1"
    init { viewModelScope.launch {
        try { restoreLibrary() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { libraryReady = false }
        try {
            val saved = Graph.storage.getString(key)?.let(::JSONObject)
            mutable.value = KidsProjectState(loading = false, idea = saved?.optString("idea").orEmpty(),
                draft = saved?.optString("draft").orEmpty(), editing = saved?.let { it.optBoolean("editing", it.optString("draft").isNotBlank()) } == true, noteId = saved?.optString("note")?.takeIf(String::isNotBlank),
                executionLabel = saved?.optString("where")?.takeIf(String::isNotBlank),
                kind = saved?.optString("kind")?.takeIf { it in setOf("Story", "Character", "Project") },
                details = saved?.optString("details").orEmpty(), canvasId = saved?.optString("canvas")?.takeIf(String::isNotBlank),
                canvasReady = saved?.optBoolean("canvasReady") == true,
                keptHash = saved?.optString("kept")?.takeIf(String::isNotBlank),
                keptWhere = saved?.optString("keptWhere")?.takeIf(String::isNotBlank), updatedAt = saved?.optLong("updated") ?: 0,
                authorship = saved?.optString("authorship")?.takeIf(String::isNotBlank) ?: if (saved == null) "SELF" else "UNKNOWN",
                providerId = saved?.optString("provider")?.takeIf(String::isNotBlank),
                modelId = saved?.optString("model")?.takeIf(String::isNotBlank),
                notice = if (libraryReady) null else "Could not load My projects. Your current draft is kept.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = KidsProjectState(loading = false, notice = "Could not restore the project. Your saved notes are still in Notebook.") }
    } }
    private suspend fun persist(snapshot: KidsProjectState) {
        writes.withLock { withContext(Dispatchers.IO) {
            Graph.storage.putString(key, JSONObject().put("idea", snapshot.idea.take(600))
                .put("draft", snapshot.draft.take(12000)).put("editing", snapshot.editing).put("note", snapshot.noteId.orEmpty()).put("where", snapshot.executionLabel.orEmpty())
                .put("authorship", snapshot.authorship).put("provider", snapshot.providerId.orEmpty()).put("model", snapshot.modelId.orEmpty())
                .put("kind", snapshot.kind.orEmpty()).put("details", snapshot.details.take(1200))
                .put("canvas", snapshot.canvasId.orEmpty()).put("canvasReady", snapshot.canvasReady)
                .put("kept", snapshot.keptHash.orEmpty()).put("keptWhere", snapshot.keptWhere.orEmpty()).put("updated", snapshot.updatedAt).toString())
        } }
    }
    private suspend fun restoreLibrary() = withContext(Dispatchers.IO) {
        val raw = Graph.storage.getString(libraryKey) ?: return@withContext
        val items = org.json.JSONArray(raw)
        savedProjects.value = (0 until items.length().coerceAtMost(200)).map { index ->
            val item = items.getJSONObject(index)
            SavedKidsProject(item.getString("note"), item.getString("title"),
                item.optString("kind").takeIf(String::isNotBlank), item.optString("details"),
                item.optString("canvas").takeIf(String::isNotBlank), item.optBoolean("canvasReady"),
                item.optString("kept").takeIf(String::isNotBlank), item.optString("where").takeIf(String::isNotBlank),
                item.optString("authorship", "UNKNOWN"), item.optString("provider").takeIf(String::isNotBlank),
                item.optString("model").takeIf(String::isNotBlank), item.optLong("updated"))
        }.sortedByDescending { it.updatedAt }
    }

    private suspend fun rememberSaved(snapshot: KidsProjectState) {
        val noteId = snapshot.noteId ?: return
        check(libraryReady)
        check(savedProjects.value.size < 200 || savedProjects.value.any { it.noteId == noteId })
        val item = SavedKidsProject(noteId, snapshot.idea.take(80), snapshot.kind, snapshot.details,
            snapshot.canvasId, snapshot.canvasReady, snapshot.keptHash, snapshot.keptWhere,
            snapshot.authorship, snapshot.providerId, snapshot.modelId, snapshot.updatedAt)
        val items = (listOf(item) + savedProjects.value.filterNot { it.noteId == noteId }).take(200)
        writes.withLock { withContext(Dispatchers.IO) {
            Graph.storage.putString(libraryKey, org.json.JSONArray().apply { items.forEach { project ->
                put(JSONObject().put("note", project.noteId).put("title", project.title).put("kind", project.kind.orEmpty())
                    .put("details", project.details).put("canvas", project.canvasId.orEmpty()).put("canvasReady", project.canvasReady)
                    .put("kept", project.keptHash.orEmpty()).put("where", project.keptWhere.orEmpty())
                    .put("authorship", project.authorship).put("provider", project.providerId.orEmpty())
                    .put("model", project.modelId.orEmpty()).put("updated", project.updatedAt))
            } }.toString())
            savedProjects.value = items
        } }
    }

    private fun canSwitch(): Boolean = !state.value.loading && !state.value.busy &&
        (isKept() || (state.value.idea.isBlank() && state.value.draft.isBlank()))

    fun newProject() {
        if (!canSwitch() || !libraryReady || library.value.size >= 200) {
            mutable.value = state.value.copy(notice = "Keep or discard your changes before switching projects. My projects must be available and have room.")
            return
        }
        editSave?.cancel(); saving = true
        mutable.value = state.value.copy(busy = true, operation = "SAVING")
        viewModelScope.launch {
            try {
                if (isKept()) rememberSaved(state.value)
                val next = KidsProjectState(loading = false, updatedAt = System.currentTimeMillis())
                persist(next); mutable.value = next
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(busy = false, operation = null,
                notice = "Could not start a new project. Your current draft is kept.") }
            finally { saving = false; mutable.value = state.value.copy(busy = false, operation = null) }
        }
    }

    fun openProject(noteId: String) {
        if (!canSwitch()) {
            mutable.value = state.value.copy(notice = "Keep or discard your changes before switching projects.")
            return
        }
        val item = library.value.firstOrNull { it.noteId == noteId } ?: return
        editSave?.cancel(); saving = true
        mutable.value = state.value.copy(busy = true, operation = "SAVING")
        viewModelScope.launch {
            try {
                if (isKept()) rememberSaved(state.value)
                val note = Graph.notebookUseCases.getNote(noteId) ?: error("Note unavailable")
                val unchanged = item.keptHash == hash(note.body)
                val next = KidsProjectState(loading = false, idea = note.title, kind = item.kind, details = item.details,
                    draft = note.body, editing = true, noteId = note.id, canvasId = item.canvasId, canvasReady = item.canvasReady,
                    keptHash = hash(note.body), keptWhere = if (unchanged) item.keptWhere else "AI execution location unavailable",
                    executionLabel = if (unchanged) item.keptWhere else "AI execution location unavailable",
                    authorship = if (unchanged) item.authorship else "UNKNOWN",
                    providerId = if (unchanged) item.providerId else null, modelId = if (unchanged) item.modelId else null,
                    updatedAt = System.currentTimeMillis())
                persist(next); mutable.value = next
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(busy = false, operation = null,
                notice = "Could not open this project. Your current draft is kept.") }
            finally { saving = false; mutable.value = state.value.copy(busy = false, operation = null) }
        }
    }

    private fun scheduleSave() {
        editSave?.cancel()
        editSave = viewModelScope.launch {
            delay(400)
            try { persist(state.value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(notice = "Could not save this draft locally. Keep a copy in Notebook.") }
        }
    }
    fun chooseKind(kind: String) {
        if (state.value.busy || kind !in setOf("Story", "Character", "Project")) return
        mutable.value = state.value.copy(kind = kind, updatedAt = System.currentTimeMillis()); scheduleSave()
    }
    fun editIdea(idea: String) {
        if (state.value.loading || state.value.busy) return
        mutable.value = state.value.copy(idea = idea.take(600), updatedAt = System.currentTimeMillis())
        scheduleSave()
    }
    fun editDetails(details: String) {
        if (state.value.loading || state.value.busy) return
        mutable.value = state.value.copy(details = details.take(1200), updatedAt = System.currentTimeMillis()); scheduleSave()
    }
    fun beginManual(idea: String) {
        if (state.value.loading || state.value.busy) return
        mutable.value = state.value.copy(idea = idea.take(600), editing = true,
            kind = state.value.kind ?: "Story", executionLabel = "On this phone",
            authorship = if (state.value.draft.isBlank()) "SELF" else state.value.authorship,
            providerId = if (state.value.draft.isBlank()) null else state.value.providerId,
            modelId = if (state.value.draft.isBlank()) null else state.value.modelId,
            notice = null, updatedAt = System.currentTimeMillis())
        scheduleSave()
    }
    fun editDraft(text: String) {
        if (state.value.loading || state.value.busy) return
        mutable.value = state.value.copy(draft = text.take(12000),
            authorship = if (state.value.authorship in setOf("AI_ASSISTED", "MIXED")) "MIXED" else state.value.authorship,
            updatedAt = System.currentTimeMillis()); scheduleSave()
    }
    fun isKept(): Boolean = state.value.noteId != null && state.value.keptHash == hash(state.value.draft)
    private fun hash(text: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    suspend fun prepareCanvas(): String? {
        if (state.value.busy || !isKept()) return null
        editSave?.cancel(); saving = true
        mutable.value = state.value.copy(busy = true, operation = "SAVING")
        return try {
            val note = state.value.noteId?.let { Graph.notebookUseCases.getNote(it) } ?: error("Note unavailable")
            check(note.body == state.value.draft)
            val repository = Graph.notebookCanvasRepository
            val previous = state.value.canvasId
            val id = if (previous != null) {
                check(repository.observe(previous).first() != null)
                previous
            } else repository.create(note.title).also { created ->
                mutable.value = state.value.copy(canvasId = created)
                persist(state.value)
            }
            Graph.workspaceMediaAiPolicy.setAccess(id, com.halo.app.data.workspace.WorkspaceAiAccess.LOCAL_ONLY)
            if (!state.value.canvasReady) {
                val nodes = repository.snapshot(id).first
                if (nodes.none { it.type == "TEXT" && it.text == note.body }) repository.addTextNode(id, 80f, 80f, note.body)
                if (nodes.none { it.refObjectId == note.id }) repository.addReference(id, note.id, 520f, 80f)
                mutable.value = state.value.copy(canvasReady = true)
                persist(state.value)
            }
            rememberSaved(state.value)
            id
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutable.value = state.value.copy(notice = "Could not open the project canvas. Your saved note is kept.")
            null
        } finally { saving = false; mutable.value = state.value.copy(busy = false, operation = null) }
    }
    fun generate(idea: String) {
        if (state.value.loading || state.value.busy || idea.isBlank()) return
        editSave?.cancel()
        val id = UUID.randomUUID().toString()
        request = id
        mutable.value = state.value.copy(idea = idea.take(600), busy = true, operation = "GENERATING", notice = null)
        generation = viewModelScope.launch {
            try {
                persist(state.value)
                val prompt = "Help a child create a short story, character or project from their idea. " +
                    "Keep it age appropriate. Preserve the child's authorship. Return only an editable draft. " +
                    "Use the language of their idea. Kind: ${state.value.kind ?: "Story"}. " +
                    "Idea: " + idea.take(600) + "\nChild's choices: " + state.value.details.take(1200)
                val mayRemote = Graph.kidsPreferences.cloudAllowed.value && Graph.storage.readRuntimeAiContextPolicy().allowCloudWorkspace
                val aiRequest = AiRequest(text = prompt, maxOutputTokens = 1200, toolUseMode = ToolUseMode.NONE)
                val workload = AiWorkloadSpec(kind = AiWorkloadKind.QUESTION,
                    requiredCapabilities = setOf(AiCapability.TEXT), allowRemote = mayRemote, allowFallback = true)
                val target = Graph.aiProviderRouter.resolveExecutionTarget(aiRequest, workload)
                val result = withTimeout(90_000) { Graph.aiProviderRouter.execute(aiRequest,
                    workload.copy(allowRemote = mayRemote && Graph.kidsPreferences.cloudAllowed.value &&
                        Graph.storage.readRuntimeAiContextPolicy().allowCloudWorkspace), AiContext.EMPTY) }
                val where = if (result.providerId == target.providerId && (result.resolvedModelId ?: result.modelId) == target.modelId)
                    if (target.model.executionLocation == AiExecutionLocation.ON_DEVICE) "AI on this phone" else "AI off this phone"
                    else "AI execution location unavailable"
                ensureActive()
                if (request != id) return@launch
                check(result.toolCalls.isEmpty() && result.text.isNotBlank()) { "No usable draft" }
                val next = state.value.copy(draft = result.text.take(12000), editing = true, busy = false, operation = null, updatedAt = System.currentTimeMillis(), executionLabel = where,
                    authorship = "AI_ASSISTED", providerId = result.providerId, modelId = result.resolvedModelId ?: result.modelId,
                    notice = "Draft ready. Review it before choosing Keep.")
                // Accept synchronously after the request check. Stop during the subsequent disk write
                // cannot resurrect an unseen generated draft on the next app start.
                if (request != id) return@launch
                mutable.value = next
                persist(next)
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException && request == id)
                    mutable.value = state.value.copy(busy = false, operation = null, notice = "This took too long. Your idea is kept; try again or write it yourself.")
                else throw cancelled
            } catch (_: Exception) {
                if (request == id) mutable.value = state.value.copy(busy = false,
                    notice = "The selected model could not make this draft. Your idea is kept. Try again or write it yourself.")
            }
        }
    }
    fun stop() {
        request = null; generation?.cancel(); generation = null
        mutable.value = state.value.copy(busy = saving, operation = if (saving) "SAVING" else null, notice = if (saving) "Finishing the local save." else "Stopped. Your previous draft is kept.")
    }
    fun keep(text: String) {
        if (state.value.busy || text.isBlank()) return
        saving = true
        mutable.value = state.value.copy(busy = true, operation = "SAVING")
        viewModelScope.launch {
            try {
                val snapshot = state.value
                val existing = snapshot.noteId?.let { Graph.notebookUseCases.getNote(it) }
                val note = if (existing == null) Graph.notebookUseCases.createNote(text, snapshot.idea.take(80))
                    else { check(Graph.notebookUseCases.saveNote(existing.id, text)); existing }
                mutable.value = state.value.copy(noteId = note.id)
                Graph.workspaceMediaAiPolicy.setAccess(note.id,
                    com.halo.app.data.workspace.WorkspaceAiAccess.LOCAL_ONLY)
                val next = snapshot.copy(draft = text, noteId = note.id, keptHash = hash(text), keptWhere = snapshot.executionLabel, busy = false, operation = null,
                    updatedAt = System.currentTimeMillis(), notice = "Saved to Notebook.")
                mutable.value = next
                persist(next)
                rememberSaved(next)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(busy = false, operation = null, notice = "Could not save. Your draft is kept here; try again.") }
            finally { saving = false }
        }
    }
    fun discard() {
        if (state.value.busy) return
        editSave?.cancel(); saving = true
        mutable.value = state.value.copy(busy = true, operation = "SAVING")
        viewModelScope.launch {
            try {
                val before = state.value
                val kept = before.noteId?.let { Graph.notebookUseCases.getNote(it) }
                val saved = library.value.firstOrNull { it.noteId == before.noteId }
                val unchanged = kept != null && hash(kept.body) == before.keptHash
                val next = if (kept != null) before.copy(draft = kept.body, editing = true, busy = false,
                    operation = null, keptHash = hash(kept.body),
                    executionLabel = if (hash(kept.body) == before.keptHash) before.keptWhere else "AI execution location unavailable",
                    authorship = if (unchanged) saved?.authorship ?: "UNKNOWN" else "UNKNOWN",
                    providerId = if (unchanged) saved?.providerId else null,
                    modelId = if (unchanged) saved?.modelId else null,
                    notice = "Unkept changes discarded. Your saved project is restored.")
                else KidsProjectState(loading = false,
                    updatedAt = System.currentTimeMillis(), notice = "Draft discarded. Saved notes are kept.")
                persist(next); mutable.value = next
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(busy = false, operation = null, notice = "Could not discard the draft. Try again.") }
            finally { saving = false; mutable.value = state.value.copy(busy = false, operation = null) }
        }
    }
}
