package com.halo.app.ui.everyday

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.HaloApplication
import com.halo.app.data.notebook.*
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.organizer.android.OrganizerAndroidClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class OrganizerLinkChoice(val id: String,val title: String,val kind: String,val searchText: String=title)

data class OrganizerPageState(
    val loading: Boolean = true, val busy: Boolean = false, val kids: Boolean = true,
    val records: List<OrganizerRecord> = emptyList(), val trash: List<OrganizerRecord> = emptyList(),
    val parentTasks: List<OrganizerRecord> = emptyList(),
    val subtasks: List<OrganizerRecord> = emptyList(),
    val calendarEvents: List<OrganizerRecord> = emptyList(),
    val agenda: List<OrganizerOccurrence> = emptyList(), val conflicts: Set<String> = emptySet(),
    val receipts: List<DeliveryReceipt> = emptyList(), val notes: List<WorkspaceObject> = emptyList(),
    val overrides: List<OccurrenceOverride> = emptyList(), val selected: OrganizerRecord? = null,
    val missing: Boolean = false, val error: String? = null, val notice: String? = null,
    val draft: OrganizerDraft? = null, val import: CalendarImportPreview? = null,
    val imported: CalendarImportResult? = null, val undoId: String? = null, val undoVersion: Long? = null,
    val exportFile: WorkspaceFile? = null,
    val selectedOccurrence: OrganizerOccurrence? = null, val occurrenceMissing: Boolean = false,
    val linkChoices: List<OrganizerLinkChoice> = emptyList(),
    val outstandingIds: Set<String> = emptySet(),
    val todayOccurrenceIds: Set<String> = emptySet(),
    val selectedResultAllowed: Boolean = false,
)

class OrganizerViewModel(private val app: HaloApplication, private val saved: SavedStateHandle, initialId: String?, private val initialKind: OrganizerKind? = null,
    initialOccurrence: Instant? = null) : ViewModel() {
    private val mutable = MutableStateFlow(OrganizerPageState(
        draft = saved.get<android.os.Bundle>("organizer.draft")?.let { runCatching { OrganizerDraft.restore(it) }.getOrNull() },
        undoId = saved["organizer.undoId"], undoVersion = saved["organizer.undoVersion"]))
    val state = mutable.asStateFlow()
    val query = saved.getStateFlow("organizer.query", "")
    val day = saved.getStateFlow("organizer.day", LocalDate.now().toString())
    val kind = saved.getStateFlow("organizer.kind", "TODAY")
    private var selectedId: String? = if(saved.contains("organizer.selected")) saved["organizer.selected"] else initialId
    private var selectedOriginal: Instant? = saved.get<String>("organizer.occurrence")?.let(Instant::parse) ?: initialOccurrence.takeIf {
        selectedId==initialId && saved.get<Boolean>("organizer.occurrenceCleared")!=true }
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var observing: Job? = null
    init { observe() }
    fun query(value: String) { saved["organizer.query"] = value.take(200) }
    fun day(value: LocalDate) { saved["organizer.day"] = value.toString() }
    fun kind(value: String) { require(value in OrganizerKind.entries.map { it.name } + listOf("AGENDA", "TRASH") + OrganizerSection.entries.map { it.name }); saved["organizer.query.${kind.value}"] = query.value
        saved["organizer.kind"] = value
        saved["organizer.query"] = saved.get<String>("organizer.query.$value") ?: ""
    }
    fun open(id: String?) { selectedId = id; saved["organizer.selected"] = id; selectedOriginal=null;saved.remove<String>("organizer.occurrence");saved["organizer.occurrenceCleared"]=true;refreshes.tryEmit(Unit) }
    fun retry() { observe() }
    fun refreshView() { refreshes.tryEmit(Unit) }
    private fun observe() {
        observing?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        observing = viewModelScope.launch {
            try {
                app.awaitOrganizerBootstrap(); Graph.kidsPreferences.getEnabled()
                merge(Graph.organizerRepository.observe().map { Unit }, Graph.organizerRepository.observeReceipts().map { Unit },
                    Graph.kidsPreferences.enabled.map { Unit }, Graph.briefRepository.observeSettings().map { Unit },
                    Graph.collectorRepository.observe().map { Unit },
                    query.map { Unit }, day.map { Unit }, kind.map { Unit }, refreshes)
                    .collectLatest { refresh() }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { mutable.update { it.copy(loading = false, records = emptyList(), agenda = emptyList(),
                  selected = null, error = "Your organizer could not be opened. Your saved data is kept; try again.") } }
        }
    }
    private suspend fun refresh() {
        val kids = Graph.kidsPreferences.getEnabled()
        val sharing = if (kids) Graph.briefRepository.settings().childRecordIds else emptySet()
        val rows = Graph.organizerRepository.records(includeDeleted = !kids).filter { !kids || it.id in sharing }
        val current = selectedId?.let { id -> rows.find { it.id == id && it.deletedAt == null } }
        val selectedResultAllowed = !kids && current?.let { Graph.organizerOperations.canReadResult(it) } == true
        val zone = ZoneId.systemDefault(); val date = LocalDate.parse(day.value)
        val from = date.atStartOfDay(zone).toInstant(); val until = date.plusDays(1).atStartOfDay(zone).toInstant()
        val active = rows.filter { it.deletedAt == null }
        val exceptions = active.filter { it.time != null }.associate { it.id to Graph.organizerRepository.overrides(it.id) }
        selectedOriginal?.let { saved["organizer.occurrence"]=it.toString() }
        val selectedOccurrence=if(current != null && selectedOriginal != null) OrganizerRecurrence.exact(current,exceptions[current.id].orEmpty(),selectedOriginal!!,zone) else null
        val words = query.value.lowercase(java.util.Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
        fun matches(row: OrganizerRecord) = words.all { it in "${row.title}\n${row.details}\n${row.location}".lowercase(java.util.Locale.ROOT) }
        val allAgenda = withContext(Dispatchers.Default) {
            val occurrences = mutableListOf<OrganizerOccurrence>()
            active.forEach { record ->
                ensureActive()
                occurrences += OrganizerRecurrence.between(record, exceptions[record.id].orEmpty(), from, until, zone)
                require(occurrences.size <= 10000)
            }
            occurrences.sortedBy { it.start }
        }
        val today = Instant.now().atZone(zone).toLocalDate()
        val todayOccurrences = if (date == today) allAgenda else withContext(Dispatchers.Default) {
            val todayFrom = today.atStartOfDay(zone).toInstant(); val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
            val items = mutableListOf<OrganizerOccurrence>()
            active.forEach { record ->
                ensureActive(); items += OrganizerRecurrence.between(record, exceptions[record.id].orEmpty(), todayFrom, todayEnd, zone)
                require(items.size <= 10000)
            }
            items
        }
        val receipts = current?.let { Graph.organizerRepository.receipts(it.id) }.orEmpty()
        val outstandingIds = mutableSetOf<String>()
        active.filter { it.kind == OrganizerKind.REMINDER }.forEach { record ->
            if (com.halo.app.everyday.briefing.OutstandingReminders.project(record, exceptions[record.id].orEmpty(), Graph.organizerRepository.receipts(record.id), today.atStartOfDay(zone).toInstant(), zone).isNotEmpty()) outstandingIds += record.id
        }
        val notes = if (kids) emptyList() else Graph.workspaceRepository.observe(WorkspaceQuery()).first()
            .filter { it.lifecycle == WorkspaceObjectLifecycle.ACTIVE }
        val choices=if(kids) emptyList() else buildList {
            addAll(notes.map {OrganizerLinkChoice(it.id.value,it.title,it.type.name)})
            addAll(Graph.studyRepository.observeActiveSessions().first().map { OrganizerLinkChoice("study:${it.id}", it.goal?.takeIf(String::isNotBlank) ?: "Study", "STUDY") })
            addAll(active.filter {it.kind in setOf(OrganizerKind.TASK,OrganizerKind.EVENT) && it.id!=current?.id}.map {OrganizerLinkChoice("organizer:${it.id}",it.title,it.kind.name)})
            addAll(Graph.appDatabase.agentRunDao().linkCandidates().map {OrganizerLinkChoice("run:${it.id}",it.request.take(120),"ACTIVITY",it.request)})
            addAll(Graph.currentTimelineEvents().filter {it.presentationId.length<=240}.map {OrganizerLinkChoice("timeline:${it.presentationId}",it.title.take(200),"TIMELINE",it.title)})
        }
        if (kids != Graph.kidsPreferences.enabled.value) return
        mutable.update { old -> old.copy(loading = false, kids = kids,
            linkChoices=choices, outstandingIds=outstandingIds, todayOccurrenceIds=todayOccurrences.filter { !it.completed }.map { it.record.id }.toSet(), selectedResultAllowed=selectedResultAllowed,
            records = active.filter(::matches), parentTasks = if (kids) emptyList() else active.filter { it.kind == OrganizerKind.TASK && it.status == OrganizerStatus.ACTIVE },
            calendarEvents = if (kids) emptyList() else active.filter { it.kind == OrganizerKind.EVENT },
            subtasks = current?.let { parent -> active.filter { it.parentId == parent.id } }.orEmpty(),
            trash = if (kids) emptyList() else rows.filter { it.deletedAt != null && matches(it) },
            agenda = allAgenda.filter { matches(it.record) }, conflicts = OrganizerRecurrence.conflicts(allAgenda).keys,
            receipts = receipts, selected = current, missing = selectedId != null && current == null,
            selectedOccurrence=selectedOccurrence,occurrenceMissing=current != null && selectedOriginal != null && selectedOccurrence==null,
            overrides = current?.let { exceptions[it.id] }.orEmpty(), notes = notes,
            draft = old.draft.takeUnless { kids }, undoId = old.undoId.takeUnless { kids }, undoVersion = old.undoVersion.takeUnless { kids }) }
        if (initialKind != null && saved.get<Boolean>("organizer.createConsumed") != true) {
            saved["organizer.createConsumed"] = true
            if (!kids && state.value.draft == null) new(initialKind)
        }
        if (kids) { saved.remove<android.os.Bundle>("organizer.draft"); clearUndo() }
        else if (state.value.exportFile == null) saved.get<String>("organizer.exportId")?.let { id ->
            val file = Graph.workspaceFileRepository.getFile(WorkspaceObjectId(id))
            mutable.update { it.copy(exportFile = file) }
        }
    }
    private fun work(uncertain: Boolean = false, action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                app.awaitOrganizerBootstrap(); check(!Graph.kidsPreferences.getEnabled())
                val revision = Graph.kidsPreferences.adultActionRevision()
                com.halo.app.everyday.EverydayWriteAuthority.run({ Graph.kidsPreferences.requireAdultAction(revision) }, action)
                try { refresh() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { it.copy(error = "The action finished, but the updated view could not be loaded. Retry to view the saved result.") } }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ScheduleCancellationPending) { mutable.update { it.copy(error = "Android schedule cancellation is still pending. The item stays in Trash. Check scheduling again, then retry permanent removal.") } }
            catch (_: DuplicateCalendarUids) { mutable.update { it.copy(error = "The selection contains multiple versions of the same calendar event. Select one version of each event or export the calendars separately.") } }
            catch (_: OrganizerConflict) { mutable.update { it.copy(error = if (uncertain) "Read the current work and execution record before continuing. Do not repeat an uncertain operation." else "This item changed, has remaining subtasks or needs a recurrence decision. Reopen it and review before trying again.") } }
            catch (_: IllegalArgumentException) { mutable.update { it.copy(error = if (uncertain) "Read the current work and execution record before continuing. Do not repeat an uncertain operation." else "Check the dates, time zone, duration and recurrence limits. Your saved item has not been replaced.") } }
            catch (_: Exception) { mutable.update { it.copy(error = if (uncertain) "Read the current work and execution record before continuing. Do not repeat an uncertain operation." else "This action could not finish. Your saved data is kept; reopen and try again.") } }
            finally { mutable.update { it.copy(busy = false, loading = false) } }
        }
    }
    fun commitmentAction(record: OrganizerRecord, action: String) = work(uncertain = true) {
        when (action) {
            "PREPARE" -> Graph.organizerOperations.prepare(record.id, record.version)
            "ARM" -> Graph.organizerOperations.arm(record.id, record.version)
            "RUN_NOW" -> Graph.organizerOperations.runNow(record.id, record.version)
            "STOP" -> Graph.organizerOperations.stop(record.id, record.version)
            "TAKE_OVER" -> Graph.organizerOperations.transfer(record.id, record.version, CommitmentOwner.ME)
            "DELEGATE" -> Graph.organizerOperations.transfer(record.id, record.version, CommitmentOwner.HALO)
            "MAKE_SHARED" -> Graph.organizerOperations.transfer(record.id, record.version, CommitmentOwner.SHARED)
            "WATCH" -> Graph.organizerOperations.watch(record.id, record.version)
            "PROMOTE" -> Graph.organizerOperations.promote(record.id, record.version)
            "ACKNOWLEDGE" -> Graph.organizerOperations.acknowledge(record.id, record.version)
            "ACKNOWLEDGE_RECOVERY" -> Graph.organizerOperations.acknowledgeRecovery(record.id, record.version)
            else -> Graph.organizerUseCases.stopwatch(record.id, record.version, action)
        }
    }
    fun saveCommitment(original: OrganizerRecord?, title: String, intent: String, responsibility: CommitmentOwner,
        purpose: CommitmentPurpose, actions: List<OrganizerPlannedAction>, refs: List<OrganizerSourceRef>, onSaved: () -> Unit) = work(uncertain = true) {
        val record = Graph.organizerOperations.saveDefinition(original?.id, original?.version, title, intent, responsibility, purpose, actions, refs)
        selectedId = record.id; saved["organizer.selected"] = record.id
        mutable.update { it.copy(notice = "Commitment saved. Review the plan before enabling Halo work.") }
        onSaved()
    }
    fun promoteCapture(record: OrganizerRecord, kind: OrganizerKind, time: OrganizerTime?, owner: CommitmentOwner,
        purpose: CommitmentPurpose, onSaved: () -> Unit) = work(uncertain = true) {
        Graph.organizerOperations.promote(record.id, record.version, kind, time, owner, purpose)
        mutable.update { it.copy(notice = "Inbox item promoted. Its original identity and links are kept.") }; onSaved()
    }
    fun saveResearch(query: String, provider: String, language: String, onSaved: () -> Unit) = work(uncertain = true) {
        Graph.organizerResearch.saveSource(com.halo.app.everyday.collectors.ResearchSourceQuery(query, provider), language, true)
        mutable.update { it.copy(notice = "Research source saved. Refresh in Power to retrieve current evidence.") }; onSaved()
    }
    fun refreshResearch(source: com.halo.app.everyday.collectors.CollectorSource) = work(uncertain = true) {
        Graph.collectorCoordinator.refreshDetailed(source.id, manual = true, expectedVersion = source.version)
    }
    fun new(kind: OrganizerKind, parentId: String? = null, sourceTitle: String? = null, sourceUrl: String? = null, sourceLabel: String? = null) {
        if (state.value.kids || state.value.busy) return
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val start = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
        val record = OrganizerRecord(java.util.UUID.randomUUID().toString(), kind, "New item", time =
            if (kind in setOf(OrganizerKind.TIMER, OrganizerKind.TASK)) null else OrganizerTime(start,
                if (kind == OrganizerKind.EVENT) start.plusHours(1) else null, ZoneId.systemDefault().id),
            timer = if (kind == OrganizerKind.TIMER) TimerState(300_000, 300_000) else null,
            parentId = parentId, sourceUrl = sourceUrl, sourceLabel = sourceLabel, createdAt = now)
        draft(OrganizerDraft(record, true, title = sourceTitle.orEmpty()))
    }
    fun edit(record: OrganizerRecord, occurrence: OrganizerOccurrence? = null) = work {
        val base = Graph.organizerUseCases.requireCurrent(record.id, record.version)
        val override = occurrence?.let { Graph.organizerRepository.overrides(base.id).find { entry -> entry.originalStart == it.originalStart } }
        draft(if (occurrence == null) OrganizerDraft(base, false) else OrganizerDraft.forOccurrence(base, occurrence, override))
    }
    fun draft(value: OrganizerDraft?) {
        saved["organizer.draft"] = value?.bundle()
        mutable.update { it.copy(draft = value, error = null) }
    }
    fun save() = work {
        val draft = requireNotNull(state.value.draft)
        if (draft.occurrence == null) draft.links.forEach { id ->
            requireNotNull(resolveLink(id))
        }
        val record = draft.record(Instant.ofEpochMilli(System.currentTimeMillis()), OrganizerAndroidClock.elapsedMs(), OrganizerAndroidClock.bootId(app))
        val value = if (draft.occurrence != null) Graph.organizerRepository.saveOverride(
            (draft.originalOverride ?: OccurrenceOverride(record.id, draft.occurrence, OccurrenceDisposition.REPLACED, record.time))
                .copy(disposition = if (draft.originalOverride?.disposition == OccurrenceDisposition.COMPLETED) OccurrenceDisposition.COMPLETED else OccurrenceDisposition.REPLACED,
                    replacementTime = record.time, replacementTitle = record.title, replacementDetails = record.details, replacementLocation = record.location), draft.base.version)
        else Graph.organizerRepository.save(record, if (draft.creating) null else draft.base.version, draft.resetOccurrences)
        this@OrganizerViewModel.draft(null); selectedId = value.id; saved["organizer.selected"] = value.id
        mutable.update { it.copy(notice = "Item saved. Delivery status is shown separately.") }
    }
    fun complete(record: OrganizerRecord, occurrence: OrganizerOccurrence? = null) = work {
        if (record.status == OrganizerStatus.COMPLETED || occurrence?.completed == true)
            Graph.organizerUseCases.reopenTask(record.id, record.version, occurrence?.originalStart)
        else Graph.organizerUseCases.completeTask(record.id, record.version, occurrence?.originalStart)
    }
    fun timer(record: OrganizerRecord, action: String) = work {
        when (action) {
            "pause" -> Graph.organizerUseCases.pauseTimer(record.id, record.version)
            "resume" -> Graph.organizerUseCases.resumeTimer(record.id, record.version)
            "stop" -> Graph.organizerUseCases.stopTimer(record.id, record.version)
            else -> error("Unsupported timer action")
        }
    }
    fun cancel(record: OrganizerRecord) = work { Graph.organizerUseCases.cancel(record.id, record.version) }
    fun remove(record: OrganizerRecord) = work {
        val deleted = Graph.organizerRepository.delete(record.id, record.version)
        saved["organizer.undoId"] = deleted.id; saved["organizer.undoVersion"] = deleted.version
        mutable.update { it.copy(undoId = deleted.id, undoVersion = deleted.version, notice = "Item moved to trash. You can undo this change.") }
        selectedId = null; saved["organizer.selected"] = null
    }
    private fun clearUndo() {
        saved.remove<String>("organizer.undoId"); saved.remove<Long>("organizer.undoVersion")
        mutable.update { it.copy(undoId = null, undoVersion = null) }
    }
    fun undo() = work { val current = state.value
        Graph.organizerRepository.restore(requireNotNull(current.undoId), requireNotNull(current.undoVersion)); clearUndo()
    }
    fun restore(record: OrganizerRecord) = work { Graph.organizerRepository.restore(record.id, record.version); clearUndo() }
    fun purge(record: OrganizerRecord) = work { Graph.organizerRepository.purgeDeleted(record.id, record.version); clearUndo() }
    fun skip(occurrence: OrganizerOccurrence) = work { Graph.organizerRepository.saveOverride(
        OccurrenceOverride(occurrence.record.id, occurrence.originalStart, OccurrenceDisposition.CANCELLED), occurrence.record.version) }
    fun resetOccurrence(record: OrganizerRecord, override: OccurrenceOverride) = work {
        Graph.organizerRepository.removeOverride(record.id, override.originalStart, record.version)
    }
    fun snooze(receipt: DeliveryReceipt, minutes: Long) = work {
        Graph.organizerDelivery.snooze(receipt.occurrenceKey, receipt.recordVersion, Duration.ofMinutes(minutes), receipt.triggerAt)
    }
    fun dismiss(receipt: DeliveryReceipt) = work { check(Graph.organizerDelivery.dismiss(receipt.occurrenceKey, receipt.recordVersion, receipt.triggerAt)) }
    fun reconcile() = work { Graph.organizerDelivery.reconcilePending() }
    fun previewImport(uri: Uri) = work {
        val preview = withContext(Dispatchers.IO) {
            val bytes = requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val size = input.read(buffer); if (size < 0) break; require(output.size() + size <= 2 * 1024 * 1024); output.write(buffer, 0, size) }; output.toByteArray()
            }
            OrganizerIcs.preview(bytes, "halo:manual-import", ZoneId.systemDefault(), Instant.ofEpochMilli(System.currentTimeMillis()))
        }
        mutable.update { it.copy(import = preview, imported = null) }
    }
    fun cancelImport() { if (!state.value.busy) mutable.update { it.copy(import = null, imported = null) } }
    fun importCalendar() = work {
        val result = Graph.organizerRepository.importCalendar(requireNotNull(state.value.import))
        mutable.update { it.copy(import = null, imported = result, notice = "Calendar import finished. Existing event IDs were kept.") }
    }
    fun prepareCalendarExport(expected: Map<String, Long>) = work {
        val file=Graph.organizerCalendars.prepareExport(expected)
        saved["organizer.exportId"] = file.objectId.value
        mutable.update { it.copy(exportFile = file, notice = "Calendar copy saved in Workspace. Choose a destination to export this exact copy.") }
    }
    fun dismissExport() { saved.remove<String>("organizer.exportId"); mutable.update { it.copy(exportFile = null) } }
    private suspend fun resolveLink(id: String): String? {
        check(!Graph.kidsPreferences.getEnabled())
        return when {
            id.startsWith("organizer:") -> Graph.organizerRepository.get(id.removePrefix("organizer:"))?.takeIf {it.deletedAt==null}?.let {EverydayRoutes.organizer(it.id)}
            id.startsWith("run:") -> Graph.appDatabase.agentRunDao().get(id.removePrefix("run:"))?.let {"activity?runId=${Uri.encode(id.removePrefix("run:"))}"}
            id.startsWith("timeline:") -> Graph.currentTimelineEvents().find {it.presentationId==id.removePrefix("timeline:")}?.let {"activity?eventId=${Uri.encode(it.presentationId)}"}
            id.startsWith("study:") -> Graph.studyRepository.getSession(id.removePrefix("study:"))?.takeIf { it.lifecycle != com.halo.sdk.runtime.study.StudySessionLifecycle.ARCHIVED }
                ?.let { "notebook/study/${Uri.encode(it.id)}" }
            else -> Graph.workspaceRepository.get(WorkspaceObjectId(id))?.let(::organizerWorkspaceRoute)
        }
    }
    fun openLink(id: String,navigate: (String)->Unit) {
        viewModelScope.launch {
            try {val revision=Graph.kidsPreferences.adultActionRevision()
                val route=requireNotNull(resolveLink(id));Graph.kidsPreferences.requireAdultAction(revision);navigate(route)
            } catch(cancelled: CancellationException) {throw cancelled}
              catch(_: Exception) {mutable.update {it.copy(error="This related item is unavailable. Reopen its instrument to review current information.")}}
        }
    }
    fun openNote(id: String, navigate: (String) -> Unit) {
        viewModelScope.launch {
            try { val note = Graph.workspaceRepository.get(WorkspaceObjectId(id))
                check(note?.type == WorkspaceObjectType.NOTE && note.lifecycle == WorkspaceObjectLifecycle.ACTIVE)
                if (Graph.kidsPreferences.getEnabled()) error("Parent item")
                navigate(id)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { mutable.update { it.copy(error = "This linked note is unavailable. It may have been moved to trash.") } }
        }
    }
}
