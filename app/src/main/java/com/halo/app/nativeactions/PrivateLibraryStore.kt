package com.halo.app.nativeactions

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.EverydayLocalDataCleanup
import com.halo.app.data.toolkit.HaloMapCoordinate
import com.halo.app.data.toolkit.HaloMapsStore
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.workspace.PrivateLibraryChunk
import com.halo.app.data.workspace.PrivateLibraryClaim
import com.halo.app.data.workspace.privateLibraryClaim
import com.halo.app.ui.screens.home.ComposerDraftStore
import com.halo.runtime.cognition.context.PayloadOwner
import com.halo.runtime.cognition.context.PayloadOwnerKind
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal sealed interface PrivateLibraryTarget {
    val id: String
    data class Item(override val id: String, val title: String, val updatedAt: Long) : PrivateLibraryTarget
    data class Collection(override val id: String, val name: String) : PrivateLibraryTarget
    data class StudyCard(override val id: String, val sessionId: String, val question: String, val createdAt: Long,
        val lastReviewedAt: Long?) : PrivateLibraryTarget
    data class Organizer(override val id: String, val title: String, val version: Long) : PrivateLibraryTarget
    data class Person(override val id: String, val name: String, val photoId: String, val photoVersion: String) : PrivateLibraryTarget
    data class Conversation(override val id: String, val title: String, val updatedAt: Long) : PrivateLibraryTarget
}

internal data class PrivateLibraryPlan(
    val target: PrivateLibraryTarget, val title: String, val titles: List<String>,
    internal val digest: String, internal val organizerVersions: Map<String, Long>,
    internal val conversationIds: Set<String>, internal val automationItems: List<PrivateLibraryItem>,
    val includesToolResults: Boolean = false,
)
internal data class PrivateLibrarySummary(val id: String, val title: String, val collection: Boolean, val itemCount: Int)
internal data class PrivateLibraryListing(val entries: List<PrivateLibrarySummary>, val unavailable: Int)
internal data class PrivateLibraryView(val title: String, val items: List<PrivateLibraryPreview>)
internal data class PrivateLibraryPreview(val itemId: String, val title: String, val note: String?, val media: List<PrivateLibraryMediaEntry>,
    val canvasNodeCount: Int? = null)
internal data class PrivateLibraryMediaEntry(val id: String, val title: String, val mimeType: String)
internal data class PrivateLibraryMedia(val mimeType: String, val bytes: ByteArray)
internal class PrivateLibraryReviewChanged : IllegalStateException("The reviewed Library items changed")

internal interface PrivateLibraryFileAdapter {
    suspend fun capture(header: WorkspaceObjectEntity, protectedIds: Set<String> = setOf(header.id)): JSONObject
    suspend fun afterSeal(access: VaultSession, envelopeId: String, header: WorkspaceObjectEntity, payload: JSONObject,
        protectedIds: Set<String> = setOf(header.id))
    suspend fun restore(access: VaultSession, envelopeId: String, header: WorkspaceObjectEntity, payload: JSONObject)
    suspend fun restoreAliases(items: List<Pair<WorkspaceObjectEntity, JSONObject>>) = Unit
    suspend fun preview(access: VaultSession, envelopeId: String, itemId: String, payload: JSONObject, maxBytes: Int): ByteArray?
    fun openPreview(access: VaultSession, envelopeId: String, itemId: String, payload: JSONObject): PrivateLibraryReadSource
    suspend fun settle()
}

/** A normal owner and its sealed backup are swapped at a single commit boundary. There is no readable tombstone. */
internal class PrivateLibraryStore(
    private val database: AppDatabase,
    private val files: PrivateLibraryFileAdapter? = null,
    private val study: PrivateLibraryStudyAdapter? = null,
    private val studySessions: PrivateLibraryStudySessionAdapter? = null,
    private val organizer: PrivateLibraryOrganizerAdapter? = null,
    private val noteAssets: PrivateLibraryNoteAssets? = null,
    private val people: PrivateLibraryPersonAdapter? = null,
    private val history: PrivateLibraryHistory? = null,
    private val automation: PrivateLibraryAutomation? = null,
    private val chatDrafts: ComposerDraftStore? = null,
    private val toolPayloads: PrivateLibraryPayloads? = null,
    private val reconcileLegacyOrigins: suspend () -> Unit = {},
    private val withdrawPublicProjections: suspend (Set<String>) -> Unit = {},
    private val refreshWidgets: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.privateLibraryDao()
    private val mutex = Mutex()
    private val memory = PrivateLibraryMemory(database)

    suspend fun prepare(access: VaultSession, target: PrivateLibraryTarget): PrivateLibraryPlan = withContext(Dispatchers.IO) {
        access.requireOpen()
        reconcileLegacyOrigins()
        // Linked photos may own a People profile that has not yet been migrated from the old index.
        people?.prepareStorage()
        suspend fun review(drainIfNeeded: Boolean) = withChatDrafts {
            withStudySidecars {
                withToolPayloads {
                    toolPayloads?.settle()
                    database.withTransaction {
                        val current = refreshReviewTarget(target)
                        current to capture(current, drainIfNeeded)
                    }
                }
            }
        }
        val (reviewed, snapshot) = try { review(drainIfNeeded = history != null) }
        catch (drain: ReviewNeedsConversationDrain) {
            requireNotNull(history).withConversationWithdrawal(drain.ids) { review(drainIfNeeded = false) }
        }
        access.requireOpen()
        val digest = withPayload(snapshot, ::digest)
        PrivateLibraryPlan(reviewed, snapshot.title, snapshot.items.map { it.header.title }, digest, organizerVersions(snapshot),
            conversationIds(snapshot), snapshot.items.filter { it.automation != null }, snapshot.toolResults.isNotEmpty())
    }

    suspend fun lock(access: VaultSession, plan: PrivateLibraryPlan) {
        access.requireOpen()
        val receipt = AtomicReference<LockReceipt?>()
        try {
            withContext(Dispatchers.IO) {
                val seal: suspend () -> Unit = {
                    val withdraw: suspend () -> Unit = {
                        withChatDrafts(plan.conversationIds) {
                            withStudySidecars {
                                val transaction: suspend () -> Unit = { lockItems(access, plan, receipt) }
                                if (plan.organizerVersions.isEmpty()) transaction()
                                else requireNotNull(organizer).withWithdrawal(plan.organizerVersions, transaction)
                            }
                        }
                    }
                    if (plan.automationItems.isEmpty()) withdraw()
                    else requireNotNull(automation).withWithdrawal(plan.automationItems, withdraw)
                }
                if (plan.conversationIds.isEmpty()) seal()
                else requireNotNull(history).withConversationWithdrawal(plan.conversationIds, seal)
            }
        } finally {
            // The UI can close after Room commits but before the IO continuation delivers a result.
            // The exact envelope determines whether to invalidate already-published views.
            receipt.get()?.let { committed ->
                withContext(NonCancellable + Dispatchers.IO) {
                    if (dao.summary(committed.envelopeId) != null) withdrawPublicProjections(committed.items)
                }
            }
        }
    }

    private data class LockReceipt(val envelopeId: String, val items: Set<String>)

    private suspend fun lockItems(access: VaultSession, plan: PrivateLibraryPlan, receipt: AtomicReference<LockReceipt?>) = mutate {
        check(dao.envelopes().size < maxEntries)
        access.requireOpen()
        val snapshot = capture(plan.target)
        if (organizerVersions(snapshot) != plan.organizerVersions || conversationIds(snapshot) != plan.conversationIds)
            throw PrivateLibraryReviewChanged()
        val id = UUID.randomUUID().toString()
        val sealed = withPayload(snapshot) { payload ->
            if (digest(payload) != plan.digest) throw PrivateLibraryReviewChanged()
            encrypt(access, id, payload)
        }
        val parts = chunk(id, sealed) + sealSummary(access, id, snapshot)
        try { dao.putChunks(parts) }
        finally { sealed.fill(0); parts.forEach { it.bytes.fill(0) } }
        val privateIds = snapshot.items.mapTo(linkedSetOf()) { it.header.id }
        receipt.set(LockReceipt(id, privateIds))
        dao.putClaims(claimFingerprints(snapshot).map { PrivateLibraryClaim(it, id) })
        if (snapshot.toolResults.isNotEmpty()) requireNotNull(toolPayloads).withdraw(access, id, snapshot.toolResults)
        for (item in snapshot.items) {
            access.requireOpen()
            item.noteAssets?.let { requireNotNull(noteAssets).afterSeal(item.header, it, access, id, privateIds) }
        }
        for (item in snapshot.items) {
            access.requireOpen()
            item.file?.let { requireNotNull(files).afterSeal(access, id, item.header, it, privateIds) }
        }
        snapshot.items.forEach { item ->
            access.requireOpen()
            val itemId = item.header.id
            when {
                item.studyCard != null -> requireNotNull(study).withdraw(item)
                item.studySession != null -> requireNotNull(studySessions).withdraw(item)
                item.organizer != null -> requireNotNull(organizer).withdraw(item)
                item.person != null -> requireNotNull(people).withdraw(item)
                item.automation != null -> requireNotNull(automation).withdraw(item)
                item.history != null -> {
                    dao.withdrawRelations(itemId)
                    requireNotNull(history).withdraw(item)
                }
                else -> {
                    dao.withdrawRelations(itemId)
                    database.workspaceContextDao().remove(itemId)
                    if (item.memory != null) memory.withdraw(item)
                    check(dao.withdrawPublicItem(itemId) == if (hasWorkspaceHeader(item)) 1 else 0)
                    dao.withdrawProvenance(itemId)
                    EverydayLocalDataCleanup.notePurged(database, itemId)
                }
            }
        }
        snapshot.collection?.let { check(database.notebookOrganizationDao().deleteCollection(it.id) == 1) }
        access.requireOpen()
        Unit
    }

    suspend fun list(access: VaultSession): PrivateLibraryListing = withContext(Dispatchers.IO) {
        access.requireOpen()
        var failed = 0
        val entries = dao.envelopes().mapNotNull { id ->
            try { readSummary(access, id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { access.requireOpen(); failed++; null }
        }
        access.requireOpen()
        PrivateLibraryListing(entries.sortedBy { it.title.lowercase() }, failed)
    }

    suspend fun view(access: VaultSession, id: String): PrivateLibraryView = withContext(Dispatchers.IO) {
        val snapshot = read(access, id)
        access.requireOpen()
        PrivateLibraryView(snapshot.title, snapshot.items.map {
            val board = it.canvas
            val canvasText = board?.nodes?.filter { node -> node.type in readableCanvasNodes && node.text.isNotBlank() }
                ?.take(50)?.joinToString("\n\n") { node -> node.text.take(1000) }
            val place = it.provenance?.takeIf { origin -> origin.sourceId == placeOrigin }
                ?.let { origin -> JSONObject(origin.metadata).let { raw -> HaloMapCoordinate(raw.getDouble("latitude"), raw.getDouble("longitude")) } }
            val text = when {
                it.studyCard != null -> it.studyCard.getString("answer")
                it.organizer != null -> requireNotNull(organizer).preview(it)
                it.history != null -> requireNotNull(history).preview(it)
                it.automation != null -> requireNotNull(automation).preview(it)
                place != null -> listOfNotNull(it.note?.body?.takeIf { body -> body != it.header.title }, place.toString()).joinToString("\n\n")
                else -> it.note?.body ?: canvasText ?: it.studio?.visibleText()
            }
            val excerpts = snapshot.toolResults.asSequence().filter { result -> result.references.any { ref ->
                ref.ownerId == it.header.id && ref.mediaType.substringBefore(';').trim().lowercase() in readableToolResultTypes
            } }.take(2).mapNotNull { result -> requireNotNull(toolPayloads).preview(access, id, result) }.toList()
            val previewText = (listOfNotNull(text?.takeIf(String::isNotBlank)) + excerpts)
                .joinToString("\n\n").takeIf(String::isNotBlank)
            PrivateLibraryPreview(it.header.id, it.header.title, previewText, previewMedia(it).map { media ->
                val mime = media.getString("mimeType")
                val label = media.optJSONObject("link")?.optString("caption").orEmpty().ifBlank {
                    media.optString("name").takeUnless { it == "null" }.orEmpty()
                }
                PrivateLibraryMediaEntry(media.optString("id", it.header.id), label, mime)
            },
                board?.nodes?.size)
        })
    }

    suspend fun media(access: VaultSession, envelopeId: String, itemId: String, mediaId: String): PrivateLibraryMedia? {
        val decoded = AtomicReference<ByteArray?>()
        var delivered = false
        try {
            return withContext(Dispatchers.IO) {
                checkUuid(itemId)
                val contents = read(access, envelopeId).items.singleOrNull { it.header.id == itemId } ?: return@withContext null
                val file = previewMedia(contents).singleOrNull { it.optString("id", itemId) == mediaId }
                    ?: return@withContext null
                if (contents.file != null) {
                    val content = requireNotNull(files).preview(access, envelopeId, itemId, file, maxMedia)
                        ?.also(decoded::set) ?: return@withContext null
                    access.requireOpen()
                    return@withContext PrivateLibraryMedia(file.getString("mimeType"), content)
                }
                val content = requireNotNull(noteAssets).readPreview(access, envelopeId, itemId, file, maxMedia)
                    ?.also(decoded::set) ?: return@withContext null
                access.requireOpen()
                PrivateLibraryMedia(file.getString("mimeType"), content)
            }.also { delivered = true }
        } finally {
            if (!delivered) decoded.get()?.fill(0)
        }
    }

    suspend fun openMedia(access: VaultSession, envelopeId: String, itemId: String, mediaId: String): PrivateLibraryReadSource {
        val opened = AtomicReference<PrivateLibraryReadSource?>()
        var delivered = false
        try {
            return withContext(Dispatchers.IO) {
                val contents = read(access, envelopeId).items.single { it.header.id == itemId }
                val file = previewMedia(contents).single { it.optString("id", itemId) == mediaId }
                val reader = if (contents.file != null) requireNotNull(files).openPreview(access, envelopeId, itemId, file)
                else requireNotNull(noteAssets).openPreview(access, envelopeId, itemId, file)
                opened.set(reader)
                access.requireOpen()
                reader
            }.also { delivered = true }
        } finally {
            if (!delivered) opened.get()?.close()
        }
    }

    private fun previewMedia(item: PrivateLibraryItem): List<JSONObject> =
        listOfNotNull(item.file?.takeIf { it.has("storedSize") }) +
            item.noteAssets?.let { requireNotNull(noteAssets).mediaEntries(it) }.orEmpty()

    suspend fun restore(access: VaultSession, id: String) {
        if (withContext(Dispatchers.IO) { withChatDrafts { withStudySidecars { restoreItems(access, id) } } }) organizer?.enqueueRestored()
        runCatching(refreshWidgets)
    }

    private suspend fun restoreItems(access: VaultSession, id: String) = mutate {
        val snapshot = read(access, id)
        requireDependenciesAvailable(snapshot)
        val links = database.notebookOrganizationDao()
        val notes = database.notebookDao()
        snapshot.collection?.let { links.insertCollection(it) }
        snapshot.items.filter(::isWorkspace).forEach { item ->
            access.requireOpen()
            if (hasWorkspaceHeader(item)) notes.insertObject(item.header)
            item.draft?.let { notes.upsertDraft(it) }
            when {
                item.note != null -> {
                    notes.insertNote(item.note)
                    if (item.blocks.isNotEmpty()) dao.restoreBlocks(item.blocks)
                }
                item.canvas != null -> {
                    val canvas = database.notebookCanvasDao()
                    canvas.insertDocument(item.canvas.document)
                    item.canvas.nodes.forEach { canvas.insertNode(it) }
                    item.canvas.edges.forEach { canvas.insertEdge(it) }
                }
                item.studio != null -> item.studio.restore(database)
                item.memory != null -> memory.restore(item)
                else -> requireNotNull(files).restore(access, id, item.header, requireNotNull(item.file))
            }
            item.memberships.forEach { member -> if (dao.collection(member.collectionId) != null) links.addMember(member) }
            item.favorite?.let { links.addFavorite(it) }
            item.alias?.takeIf { it.expiresAt > clock() }?.let { database.workspaceLibraryNameDao().save(it) }
            item.provenance?.let { database.workspaceContextDao().provenance(it) }
            item.working?.let { database.workspaceContextDao().add(it) }
        }
        val attachments = snapshot.items.mapNotNull { item -> item.noteAssets?.let { item.header to it } }
        if (attachments.isNotEmpty()) requireNotNull(noteAssets).restore(attachments, access, id)
        val fileItems = snapshot.items.mapNotNull { item -> item.file?.let { item.header to it } }
        if (fileItems.isNotEmpty()) requireNotNull(files).restoreAliases(fileItems)
        snapshot.items.filter { it.history != null }.forEach { item ->
            access.requireOpen()
            requireNotNull(history).restore(item)
            if (item.header.type == "CONVERSATION" && item.composerDraftCaptured) {
                chatDrafts?.restorePrivateDraft(item.header.id, item.composerDraft)
            }
        }
        memory.restoreLinks(snapshot.items.filter { it.memory != null })
        if (snapshot.relations.isNotEmpty()) dao.restoreRelations(snapshot.relations)
        for (item in organizerItemsInParentOrder(snapshot)) {
            access.requireOpen()
            requireNotNull(organizer).restore(item)
        }
        for (item in snapshot.items) {
            access.requireOpen()
            if (item.studySession != null) requireNotNull(studySessions).restore(item)
            if (item.person != null) requireNotNull(people).restore(item)
            if (item.automation != null) requireNotNull(automation).restore(item)
        }
        snapshot.items.filter { it.studyCard != null }.forEach { requireNotNull(study).restore(it) }
        if (snapshot.toolResults.isNotEmpty()) requireNotNull(toolPayloads).restore(access, id, snapshot.toolResults)
        access.requireOpen()
        check(dao.removeChunks(id) > 0)
        check(dao.removeClaims(id) == claimFingerprints(snapshot).size)
        snapshot.items.any { it.organizer != null }
    }

    private fun claimFingerprints(snapshot: PrivateLibrarySnapshot): Set<String> = buildSet {
        snapshot.collection?.let { add(privateLibraryClaim(it.id)) }
        for (item in snapshot.items) {
            add(privateLibraryClaim(item.header.id))
            item.noteAssets?.let { addAll(requireNotNull(noteAssets).claimFingerprints(it)) }
            item.noteAssets?.let { addAll(attachmentIds(it).map(::privateLibraryClaim)) }
            if (item.studySession != null) {
                requireNotNull(studySessions).ownedIds(item).forEach { add(privateLibraryClaim(it)) }
            }
            if (item.history != null) requireNotNull(history).ownedIds(item).forEach { add(privateLibraryClaim(it)) }
            if (item.automation != null) requireNotNull(automation).ownedIds(item).forEach { add(privateLibraryClaim(it)) }
        }
    }

    private class ReviewNeedsConversationDrain(val ids: Set<String>) : IllegalStateException()

    private suspend fun capture(target: PrivateLibraryTarget, drainIfNeeded: Boolean = false): PrivateLibrarySnapshot {
        val collection = (target as? PrivateLibraryTarget.Collection)?.let { reviewed ->
            checkUuid(reviewed.id)
            requireNotNull(dao.collection(reviewed.id)).also { check(it.name == reviewed.name) }
        }
        val root = when (target) {
            is PrivateLibraryTarget.Item -> workspaceSelection(target.id)
            is PrivateLibraryTarget.Collection -> null
            is PrivateLibraryTarget.Organizer -> Selection(Owner.PLAN, target.id)
            is PrivateLibraryTarget.StudyCard -> Selection(Owner.CARD, target.id)
            is PrivateLibraryTarget.Person -> Selection(Owner.PERSON, target.id)
            is PrivateLibraryTarget.Conversation -> Selection(Owner.CONVERSATION, target.id)
        }
        val starting = root?.let { listOf(it) } ?: database.notebookOrganizationDao().guardedMembers(requireNotNull(collection).id)
            .map { workspaceSelection(it.objectId) }
        val selected = discoverDependencies(starting)
        if (drainIfNeeded) {
            val chats = selected.filter { it.owner == Owner.CONVERSATION }.mapTo(linkedSetOf(), Selection::id)
            if (chats.isNotEmpty() && selected.any { it.owner == Owner.RUN &&
                    database.agentRunDao().get(it.id)?.run?.state in activeRunStates }) {
                throw ReviewNeedsConversationDrain(chats)
            }
        }
        val protectedIds = selected.mapTo(hashSetOf(), Selection::id)
        val approvedHistory = selected.mapNotNull(::historyRef).toSet()
        val toolResults = toolPayloads?.capture(selected.flatMap { payloadOwners(it) }.toSet()).orEmpty()
        val toolResultOwners = toolResults.flatMap { it.references }.mapTo(hashSetOf()) { it.ownerId }
        val items = selected.map { selection ->
            val id = selection.id
            if (selection.owner in setOf(Owner.PLAN, Owner.CONVERSATION, Owner.RUN, Owner.ROUTINE, Owner.WATCH) ||
                selection.owner == Owner.WORKSPACE && (id.matches(legacyMemoryId) || id.startsWith("learning:"))) {
                require(id.matches(organizerId))
            } else checkUuid(id)
            val reviewed = target.takeIf { selection == root }
            if (reviewed is PrivateLibraryTarget.Conversation) {
                val chat = requireNotNull(database.conversationDao().getConversation(id))
                if (chat.title != reviewed.title || chat.updatedAt != reviewed.updatedAt || chat.archived)
                    throw PrivateLibraryReviewChanged()
            }
            val item = when (selection.owner) {
                Owner.WORKSPACE -> captureWorkspace(id, reviewed as? PrivateLibraryTarget.Item, protectedIds)
                Owner.PLAN -> (reviewed as? PrivateLibraryTarget.Organizer)?.let { requireNotNull(organizer).capture(it) }
                    ?: requireNotNull(organizer).captureLinked(id)
                Owner.PERSON -> (reviewed as? PrivateLibraryTarget.Person)?.let { requireNotNull(people).capture(it) }
                    ?: requireNotNull(people).captureLinked(id)
                Owner.CARD -> requireNotNull(study).capture(reviewed as PrivateLibraryTarget.StudyCard)
                Owner.STUDY -> requireNotNull(studySessions).capture(id, approvedHistory)
                Owner.CONVERSATION, Owner.RUN -> requireNotNull(history).capture(
                    requireNotNull(historyRef(selection)), protectedIds, toolResults)
                Owner.ROUTINE -> requireNotNull(requireNotNull(automation).captureRoutine(id))
                Owner.WATCH -> requireNotNull(requireNotNull(automation).captureWatch(id))
            }
            if (selection.owner == Owner.CONVERSATION && chatDrafts != null) {
                item.copy(composerDraft = chatDrafts.privateDraft(id), composerDraftCaptured = true)
            } else item
        }
        val runTokens = selected.filter { it.owner == Owner.RUN }.joinToString("") { "|${it.id}|" }
        val episodeTokens = items.filter { it.history != null }
            .flatMap { requireNotNull(history).ownedEpisodeIds(it) }.distinct().joinToString("") { "|${it}|" }
        val automationTokens = items.filter { it.automation != null }.joinToString("") { "|${it.header.id}|" }
        for (item in items) {
            check(!dao.hasUnsealedOwners(item.header.id, item.note != null && noteAssets != null,
                item.header.id in toolResultOwners, runTokens, episodeTokens, automationTokens)) {
                "A linked item is still in use outside the Library"
            }
        }
        val relationOwners = selected.filter { it.owner in setOf(Owner.WORKSPACE, Owner.CONVERSATION) }
        val relations = relationOwners.flatMap { dao.relations(it.id) }.distinctBy { it.id }.sortedBy { it.id }
        return PrivateLibrarySnapshot(collection, items, root?.id, relations, toolResults)
    }

    private enum class Owner { WORKSPACE, PLAN, CARD, PERSON, STUDY, CONVERSATION, RUN, ROUTINE, WATCH }
    private data class Selection(val owner: Owner, val id: String)

    private suspend fun workspaceSelection(id: String) = Selection(
        if (database.notebookDao().getWorkspaceObject(id)?.type == "CONVERSATION") Owner.CONVERSATION else Owner.WORKSPACE,
        id,
    )

    private suspend fun payloadOwners(selection: Selection): List<PayloadOwner> = buildList {
        val (owner, id) = selection
        if (owner == Owner.RUN) add(PayloadOwner(PayloadOwnerKind.AGENT_RUN, id))
        if (owner == Owner.CONVERSATION) add(PayloadOwner(PayloadOwnerKind.CONVERSATION, id))
        if (owner == Owner.WORKSPACE || owner == Owner.CONVERSATION) {
            if (database.notebookDao().getWorkspaceObject(id) != null) add(PayloadOwner(PayloadOwnerKind.WORKSPACE, id))
            if (owner == Owner.WORKSPACE && database.memoryRecordDao().getRecord(id) != null)
                add(PayloadOwner(PayloadOwnerKind.MEMORY, id))
        }
    }

    private fun historyRef(selection: Selection): PrivateHistoryRef? = when (selection.owner) {
        Owner.CONVERSATION -> PrivateHistoryRef(PrivateHistoryKind.CONVERSATION, selection.id)
        Owner.RUN -> PrivateHistoryRef(PrivateHistoryKind.AGENT_RUN, selection.id)
        else -> null
    }

    private fun PrivateHistoryRef.selection() = Selection(
        if (kind == PrivateHistoryKind.CONVERSATION) Owner.CONVERSATION else Owner.RUN, id,
    )

    private suspend fun discoverDependencies(starting: List<Selection>): List<Selection> {
        val chosen = linkedSetOf<Selection>()
        val pending = ArrayDeque(starting)
        while (pending.isNotEmpty()) {
            val selected = pending.removeFirst()
            if (!chosen.add(selected)) continue
            check(chosen.size <= PrivateLibraryCodec.maxItems)
            val id = selected.id
            fun enqueue(choice: Selection) {
                if (choice !in chosen) pending.addLast(choice)
            }
            suspend fun add(owner: Owner, ids: List<String>) {
                for (dependent in ids) {
                    val choice = if (owner == Owner.WORKSPACE) workspaceSelection(dependent) else Selection(owner, dependent)
                    enqueue(choice)
                }
            }
            add(Owner.WORKSPACE, dao.dependentWorkspaceIds(id))
            add(Owner.PLAN, dao.dependentOrganizerIds(id))
            add(Owner.PERSON, dao.dependentAlbumPeopleIds(id))
            for (owner in toolPayloads?.sharedOwners(id).orEmpty()) {
                enqueue(when (owner.kind) {
                    PayloadOwnerKind.AGENT_RUN -> Selection(Owner.RUN, owner.id)
                    PayloadOwnerKind.CONVERSATION -> Selection(Owner.CONVERSATION, owner.id)
                    PayloadOwnerKind.WORKSPACE, PayloadOwnerKind.MEMORY -> workspaceSelection(owner.id)
                })
            }
            suspend fun addAutomation(sourceId: String) {
                val adapter = automation ?: return
                for (owner in adapter.relatedOwners(sourceId)) {
                    val kind = if (owner.type == PrivateLibraryAutomation.ROUTINE) Owner.ROUTINE else Owner.WATCH
                    enqueue(Selection(kind, owner.id))
                }
            }
            addAutomation(id)
            if (selected.owner == Owner.ROUTINE || selected.owner == Owner.WATCH) {
                val adapter = requireNotNull(automation)
                val item = requireNotNull(if (selected.owner == Owner.ROUTINE) adapter.captureRoutine(id) else adapter.captureWatch(id))
                for (run in adapter.runIds(item)) {
                    if (database.agentRunDao().get(run) != null) enqueue(Selection(Owner.RUN, run))
                }
            }
            history?.let { archive ->
                archive.linked(id).forEach { enqueue(it.selection()) }
                historyRef(selected)?.let { reference ->
                    archive.related(reference).forEach { enqueue(it.selection()) }
                    for (relatedId in archive.relatedOwnedIds(reference)) {
                        add(Owner.WORKSPACE, dao.dependentWorkspaceIds(relatedId))
                        add(Owner.PLAN, dao.dependentOrganizerIds(relatedId))
                        addAutomation(relatedId)
                    }
                }
            }
            val linkedStudy = studySessions?.linkedSessions(id) ?: dao.directStudyDependencies(id).also { check(it.isEmpty()) }
            add(Owner.STUDY, linkedStudy.filterNot { selected.owner == Owner.STUDY && it == id })
            if (selected.owner == Owner.STUDY) {
                val sessions = requireNotNull(studySessions)
                add(Owner.WORKSPACE, sessions.derivedWorkspaceIds(id))
                sessions.relatedHistory(id).forEach { enqueue(it.selection()) }
            }
            if (selected.owner == Owner.WORKSPACE) {
                val header = database.notebookDao().getWorkspaceObject(id)
                if (noteAssets != null) add(Owner.WORKSPACE, noteAssets.linkedWorkspaceIds(id))
                if (header?.type == "NOTE" && noteAssets != null) {
                    for (attachment in noteAssets.ownedAttachmentIds(id).sorted()) {
                        add(Owner.WORKSPACE, dao.dependentWorkspaceIds(attachment).filterNot { it == id })
                        add(Owner.PLAN, dao.dependentOrganizerIds(attachment))
                        addAutomation(attachment)
                        history?.linked(attachment).orEmpty().forEach { enqueue(it.selection()) }
                        val linked = studySessions?.linkedSessions(attachment)
                            ?: dao.directStudyDependencies(attachment).also { check(it.isEmpty()) }
                        add(Owner.STUDY, linked)
                    }
                }
                val place = database.workspaceContextDao().provenanceFor(id)?.takeIf { it.sourceId == placeOrigin }
                if (place != null) {
                    val raw = JSONObject(place.metadata)
                    val point = HaloMapCoordinate(raw.getDouble("latitude"), raw.getDouble("longitude"))
                    val owner = requireNotNull(header)
                    val saved = com.halo.app.data.toolkit.HaloSavedPlace(id, owner.updatedAt.toString(), owner.title, point)
                    val linked = database.organizerDao().records(includeDeleted = true).filter { record ->
                        HaloMapsStore.matchesPlace(saved, JSONObject(record.body).optString("location"), emptyList(), true)
                    }.map { it.id }
                    add(Owner.PLAN, linked)
                }
            }
        }
        check(chosen.distinctBy(Selection::id).size == chosen.size) { "Linked Library owners share an identity" }
        return chosen.toList()
    }

    private suspend fun captureWorkspace(id: String, reviewed: PrivateLibraryTarget.Item?, protectedIds: Set<String>): PrivateLibraryItem {
        val existing = database.notebookDao().getWorkspaceObject(id)
        if (existing?.type == "MEMORY" || existing == null &&
            (id.matches(legacyMemoryId) || database.memoryRecordDao().getRecord(id) != null)) return memory.capture(id, existing)
        val header = requireNotNull(existing)
        check(header.type in workspaceTypes)
        if (reviewed != null) check(header.lifecycle == "ACTIVE" && header.title == reviewed.title && header.updatedAt == reviewed.updatedAt)
        val origin = database.workspaceContextDao().provenanceFor(id)
        val attachments = if (header.type == "NOTE") noteAssets?.capture(header, protectedIds) else null
        val note = if (header.type == "NOTE") {
            check(header.contentRef == "note:$id" && (header.source != "DEVICE_CAPTURE" || attachments != null))
            requireNotNull(dao.note(id))
        } else null
        val blocks = if (note != null) database.notebookDocumentDao().getBlocks(id) else emptyList()
        val board = if (header.contentRef == "canvas:$id" && header.type in setOf("CANVAS", "ARTIFACT")) {
            val owner = database.notebookCanvasDao()
            val doc = requireNotNull(owner.getDocument(id))
            val nodes = owner.getNodes(id).sortedBy { it.id }
            val edges = owner.getEdges(id).sortedBy { it.id }
            val nodeIds = nodes.mapTo(hashSetOf()) { it.id }
            check(edges.all { it.fromNodeId in nodeIds && it.toNodeId in nodeIds })
            PrivateLibraryCanvas(doc, nodes, edges)
        } else null
        val studio = if (header.type == "ARTIFACT" && header.contentRef == "studio:$id") PrivateLibraryStudio.capture(database, id) else null
        val file = if (note == null && board == null && studio == null) {
            check(header.type == "FILE" || header.type == "MEDIA")
            requireNotNull(files).capture(header, protectedIds)
        } else null
        return PrivateLibraryItem(header, note, blocks, dao.memberships(id), dao.favorite(id), dao.alias(id), file, board,
            provenance = origin.takeIf { file == null && header.source != "DEVICE_CAPTURE" },
            studio = studio, noteAssets = attachments, working = database.workspaceContextDao().selectedEntry(id),
            draft = database.notebookDao().getDraft(id))
    }

    private fun isWorkspace(item: PrivateLibraryItem) = item.history == null && item.header.type in workspaceTypes
    private fun hasWorkspaceHeader(item: PrivateLibraryItem) = item.memory?.getBoolean("hasHeader") ?: true

    private fun organizerVersions(snapshot: PrivateLibrarySnapshot): Map<String, Long> =
        snapshot.items.filter { it.organizer != null }.associate { it.header.id to requireNotNull(organizer).record(it).version }

    private fun conversationIds(snapshot: PrivateLibrarySnapshot): Set<String> =
        snapshot.items.filter { it.header.type == "CONVERSATION" }.mapTo(linkedSetOf()) { it.header.id }

    private suspend fun refreshReviewTarget(target: PrivateLibraryTarget): PrivateLibraryTarget {
        if (target !is PrivateLibraryTarget.Conversation) return target
        val current = requireNotNull(database.conversationDao().getConversation(target.id))
        check(!current.archived)
        return target.copy(title = current.title, updatedAt = current.updatedAt)
    }

    private fun organizerItemsInParentOrder(snapshot: PrivateLibrarySnapshot): List<PrivateLibraryItem> {
        val plans = snapshot.items.filter { it.organizer != null }.associateBy { it.header.id }
        val parents = plans.mapValues { requireNotNull(organizer).record(it.value).parentId }
        val ordered = linkedSetOf<String>()
        val visiting = hashSetOf<String>()
        fun visit(id: String) {
            if (id in ordered) return
            check(visiting.add(id)) { "The Planner hierarchy contains a cycle" }
            parents[id]?.takeIf(plans::containsKey)?.let(::visit)
            visiting.remove(id)
            ordered.add(id)
        }
        plans.keys.forEach(::visit)
        return ordered.map(plans::getValue)
    }

    private suspend fun requireDependenciesAvailable(snapshot: PrivateLibrarySnapshot) {
        val own = snapshot.items.mapTo(hashSetOf()) { it.header.id }
        snapshot.items.filter { it.studySession != null }.forEach { own += requireNotNull(studySessions).ownedIds(it) }
        snapshot.items.filter { it.history != null }.forEach { own += requireNotNull(history).ownedIds(it) }
        snapshot.items.filter { it.automation != null }.forEach { own += requireNotNull(automation).ownedIds(it) }
        snapshot.items.forEach { it.noteAssets?.let { assets -> own += attachmentIds(assets) } }
        snapshot.toolResults.forEach { result -> result.references.forEach { own += it.id } }
        val linked = buildSet {
            snapshot.relations.forEach { link -> add(link.fromId); add(link.toId) }
            snapshot.items.filter { it.organizer != null }.forEach { item ->
                val plan = requireNotNull(organizer).record(item)
                plan.parentId?.let(::add)
                plan.links.forEach { add(it.objectId) }
            }
            snapshot.items.filter { it.studySession != null }.forEach { addAll(requireNotNull(studySessions).sourceIds(it)) }
            snapshot.items.filter { it.memory != null }.forEach { addAll(memory.linkedIds(it)) }
            snapshot.items.filter { it.history != null }.forEach { addAll(requireNotNull(history).sourceIds(it)) }
            withPayload(snapshot) { bytes -> uuidRef.findAll(bytes.toString(Charsets.UTF_8)).forEach { add(it.value) } }
        } - own
        for (batch in linked.asSequence().map(::privateLibraryClaim).chunked(400)) {
            check(dao.claims(batch).isEmpty()) { "Restore the linked Private item first" }
        }
    }

    private fun attachmentIds(payload: JSONObject): List<String> {
        val assets = payload.getJSONArray("assets")
        return buildList {
            for (index in 0 until assets.length()) {
                val row = assets.getJSONObject(index)
                add(row.getString("id"))
                row.optJSONObject("link")?.getString("id")?.let(::add)
            }
        }
    }

    private suspend fun <T> withStudySidecars(action: suspend () -> T): T {
        val sessions = studySessions
        return if (sessions == null) action() else sessions.withSidecars(action)
    }

    private suspend fun <T> withToolPayloads(action: suspend () -> T): T =
        if (toolPayloads == null) action() else toolPayloads.withAccess(action)

    private suspend fun <T> withChatDrafts(protecting: Set<String> = emptySet(), action: suspend () -> T): T {
        val owner = chatDrafts ?: return action()
        return owner.withPrivateAccess(protecting, isSealed = { id -> dao.claims(listOf(privateLibraryClaim(id))).isNotEmpty() },
            action = action)
    }

    private suspend fun read(access: VaultSession, id: String): PrivateLibrarySnapshot {
        checkUuid(id)
        access.requireOpen()
        val chunks = dao.chunks(id)
        check(chunks.isNotEmpty() && chunks.size <= maxPayload / chunkSize + 2)
        var length = 0
        chunks.forEachIndexed { index, row ->
            check(row.part == index && row.bytes.size in 1..chunkSize)
            length += row.bytes.size
        }
        check(length in 29..maxPayload + 28)
        val encrypted = ByteArray(length)
        var offset = 0
        for (row in chunks) { row.bytes.copyInto(encrypted, offset); offset += row.bytes.size; row.bytes.fill(0) }
        val plain = try { decrypt(access, id, encrypted) } finally { encrypted.fill(0) }
        return try { PrivateLibraryCodec.decode(plain) } finally { plain.fill(0) }
    }

    private suspend fun <T> mutate(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            withToolPayloads {
                check(!database.inTransaction())
                settleFileTransitions()
                try {
                    database.withTransaction {
                        database.openHelper.writableDatabase.query("PRAGMA secure_delete=ON").use { it.moveToFirst() }
                        block()
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { settleFileTransitions() }
                }
            }
        }
    }

    private suspend fun settleFileTransitions() {
        try { toolPayloads?.settle() }
        finally { try { files?.settle() } finally { noteAssets?.settle() } }
    }

    private fun <T> withPayload(snapshot: PrivateLibrarySnapshot, use: (ByteArray) -> T): T {
        val bytes = PrivateLibraryCodec.encode(snapshot)
        return try { check(bytes.size in 1..maxPayload); use(bytes) } finally { bytes.fill(0) }
    }

    private fun encrypt(access: VaultSession, id: String, bytes: ByteArray): ByteArray = access.withKey { key ->
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == 12)
        cipher.updateAAD(aad(id))
        cipher.iv + cipher.doFinal(bytes)
    }

    private fun decrypt(access: VaultSession, id: String, bytes: ByteArray): ByteArray = access.withKey { key ->
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD(aad(id))
        cipher.doFinal(bytes, 12, bytes.size - 12)
    }

    private fun aad(id: String) = "halo-private-library-v1:$id".toByteArray(Charsets.UTF_8)

    private fun sealSummary(access: VaultSession, id: String, snapshot: PrivateLibrarySnapshot): PrivateLibraryChunk {
        val data = JSONObject().put("title", snapshot.title).put("collection", snapshot.collection != null)
            .put("count", snapshot.items.size).toString().toByteArray(Charsets.UTF_8)
        return try {
            check(data.size <= maxSummary)
            PrivateLibraryChunk(id, -1, encrypt(access, "summary:$id", data))
        } finally { data.fill(0) }
    }

    private suspend fun readSummary(access: VaultSession, id: String): PrivateLibrarySummary {
        checkUuid(id)
        val bytes = requireNotNull(dao.summary(id))
        check(bytes.size in 29..maxSummary + 28)
        val plain = try { decrypt(access, "summary:$id", bytes) } finally { bytes.fill(0) }
        return try {
            val data = JSONObject(plain.toString(Charsets.UTF_8))
            PrivateLibrarySummary(id, data.getString("title"), data.getBoolean("collection"), data.getInt("count"))
        } finally { plain.fill(0) }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun checkUuid(id: String) { check(UUID.fromString(id).toString() == id) }
    private fun chunk(id: String, bytes: ByteArray) = bytes.indices.step(chunkSize).mapIndexed { index, start ->
        PrivateLibraryChunk(id, index, bytes.copyOfRange(start, (start + chunkSize).coerceAtMost(bytes.size)))
    }

    private companion object {
        const val chunkSize = 128 * 1024
        const val maxPayload = 12 * 1024 * 1024
        const val maxMedia = 8 * 1024 * 1024
        const val maxSummary = 4 * 1024
        const val maxEntries = 200
        val workspaceTypes = setOf("NOTE", "CANVAS", "ARTIFACT", "FILE", "MEDIA", "MEMORY")
        val activeRunStates = setOf("CREATED", "PLANNING", "EXECUTING", "WAITING_FOR_APPROVAL", "WAITING_FOR_CLARIFICATION")
        val legacyMemoryId = Regex("mem_[0-9]+")
        val organizerId = Regex("[A-Za-z0-9_.:-]{1,200}")
        val uuidRef = Regex("(?<![a-fA-F0-9])[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}(?![a-fA-F0-9])")
        val readableCanvasNodes = setOf("TEXT", "STICKY", "GROUP", "TAG", "FRAME")
        val readableToolResultTypes = setOf("text/plain", "application/json")
        const val placeOrigin = "halo-maps-place"
    }
}
