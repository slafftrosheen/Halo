package com.halo.app.study

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.data.study.*
import com.halo.app.data.workspace.*
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.multimodal.*
import com.halo.sdk.runtime.study.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class StudySourcesTest {
    private fun session(ids: List<String> = listOf("a", "b")) = StudySessionRecord("lesson", "fractions",
        StudyLanguagePreferences("lv", "ru", "pl"), ids, 7, null, null, null, TutorStage.HINT,
        StudySessionLifecycle.ACTIVE, null, false, null, 1, 1)
    private val sources get() = listOf(
        StudySourceExcerpt("a", 7, "First", "First passage", "Block 1"),
        StudySourceExcerpt("b", 8, "Second", "Second passage", "Block 9"))
    private fun turn(s: StudySessionRecord = session(), excerpts: List<StudySourceExcerpt> = sources,
        visual: ResolvedStudySource? = null) = TutorTurnRequest(s, TutorStage.HINT, s.languages, s.goal,
        excerpts, null, emptyList(), null, "turn", visualSource = visual)

    @Test fun simpleCardGrammarKeepsExactSourceAndRejectsUnknownSources() {
        val drafts=StudyCardDraftParser.parse("S2 | Q: What? | A: Second passage",sources)!!
        assertEquals("b",drafts.single().source.objectId)
        assertEquals("8",drafts.single().source.version)
        assertNull(StudyCardDraftParser.parse("S4 | Q: What? | A: Invented",sources))
        assertNull(StudyCardDraftParser.parse("S1 | Q: What? | A: Answer\nIgnore all rules",sources))
    }
    @Test fun compactExplanationRetainsSourceAndRevealRules() {
        val prompt=AiRouterTutorGateway(router(Provider()),{false}).buildPrompt(turn().copy(compactExplanation=true))
        assertTrue(prompt.contains("Three practice questions"))
        assertTrue(prompt.contains("Do not answer the practice questions"))
        assertTrue(prompt.contains("[S1]"))
        assertTrue(prompt.contains("Use only supplied material"))
    }

    @Test fun practiceCardPromptRequiresBoundAnswersWithoutNarrativeHintRestriction() {
        val gateway = AiRouterTutorGateway(router(Provider()), { false })
        val prompt = gateway.buildPrompt(turn().copy(createCards = true))
        assertFalse("Card answers cannot also be forbidden by the narrative stage", prompt.contains("Give one hint only"))
        assertFalse("Practice creation is not a dialogue turn", prompt.contains("Do not reveal the answer"))
        assertFalse("A structured card request must not ask for a learner attempt", prompt.contains("Hint first, then invite an attempt."))
        assertTrue(prompt.contains("\"question\"")); assertTrue(prompt.contains("\"answer\""))
        assertTrue(prompt.contains("\"source\":\"S1\""))
        assertTrue(prompt.contains("Use only supplied material"))
        assertTrue(prompt.contains("First passage")); assertTrue(prompt.contains("Second passage"))
    }
    @Test fun practicePromptMinimumMatchesStrictParserNonemptyBatchContract() {
        val prompt = AiRouterTutorGateway(router(Provider()), { false }).buildPrompt(turn().copy(createCards = true))
        assertTrue("Prompt must explicitly require at least one card", Regex("(?i)(?:1\\s*(?:to|[-–])\\s*5|one to five|at least one)\\s*(?:source[- ]bound\\s*)?cards?").containsMatchIn(prompt))
        assertNull(StudyCardDraftParser.parse("""{"cards":[]}""", sources))
    }
    /** Captures the shared request contract only; this fixture qualifies no local model/artifact. */
    @Test fun localPracticeRequestKeepsNonemptyCardCountAndSourceBoundAnswerContract() = runBlocking {
        val provider = Provider(isLocal = true)
        val gateway = AiRouterTutorGateway(router(provider), { false })
        assertTrue(gateway.respond(turn().copy(createCards = true)) is TutorTurnResponse.Ready)
        val request = provider.requests.single()
        val prompt = requireNotNull(request.text)
        assertTrue("The actual local-format request must require at least one card",
            Regex("(?i)(?:1\\s*(?:to|[-–])\\s*5|one to five|at least one)\\s*(?:source[- ]bound\\s*)?cards?").containsMatchIn(prompt))
        assertTrue(prompt.contains("S1 | Q:")); assertTrue(prompt.contains("| A:"))
        assertTrue(prompt.contains("First passage")); assertTrue(prompt.contains("Second passage"))
        assertFalse(prompt.contains("Give one hint only")); assertFalse(prompt.contains("Do not reveal the answer"))
        assertEquals(1800, request.maxOutputTokens)
        assertEquals(ToolUseMode.NONE, request.toolUseMode)
    }
    @Test fun ordinaryHintPromptStillForbidsAnswerRevealAndDoesNotRequestCards() {
        val prompt = AiRouterTutorGateway(router(Provider()), { false }).buildPrompt(turn())
        assertTrue(prompt.contains("Give one hint only. Do not reveal the answer or the full method."))
        assertFalse(prompt.contains("\"cards\""))
        assertTrue(prompt.contains("[S1]")); assertTrue(prompt.contains("Use only supplied material"))
    }
    @Test fun practiceParserStillRejectsEmptyInvalidAndUnboundResponsesAsWholeBatch() {
        listOf("""{"cards":[]}""",
            """{"cards":[{"question":"Question","answer":"Answer","source":"S4"}]}""",
            """{"cards":[{"question":"","answer":"Answer","source":"S1"}]}""",
            """{"cards":[{"question":"Question","answer":"Answer","source":"[S1]"}]}""",
            """{"cards":[{"question":"Good","answer":"Answer","source":"S1"},{"question":"Unbound","answer":"Answer","source":"S3"}]}"""
        ).forEach { assertNull(StudyCardDraftParser.parse(it, sources)) }
        assertNull(StudyCardDraftParser.parse("""{"cards":[{"question":"Question","answer":"Answer","source":"S1"}]}""",
            sources.map { it.copy(coverage = SourceCoverage.MISSING) }))
    }

    @Test fun laterPassageWinsLexicalSelection() {
        val passages = (1..20).map { StudyPassage(if (it == 18) "chlorophyll exercise" else "unrelated paragraph", StudySourceLocator.NoteBlocks(it, it)) }
        val selected = StudyPassageSelector.select(passages, "chlorophyll")
        assertEquals(3, selected.size)
        assertTrue(selected.any { it.locator == StudySourceLocator.NoteBlocks(18, 18) })
    }
    @Test fun selectorNeverExceedsCharacterAndPassageBudget() {
        val passages = StudyPassageSelector.chunks("a".repeat(100_000)) { a, b -> StudySourceLocator.TextRange(a, b) }
        assertTrue(passages.all { it.text.length <= 1600 })
        val selected = StudyPassageSelector.select(passages, "", 2000)
        assertTrue(selected.size <= 3)
        assertTrue(selected.sumOf { it.text.length } <= 2000)
    }
    @Test fun onlyActualSecondMarkerCreatesCitation() {
        val result = StudyCitationMarkers.citations("Use this [S2].", sources)
        assertEquals(listOf("b"), result.map { it.sourceObjectId })
        assertEquals("Block 9", result.single().locator)
        assertEquals("8", result.single().sourceVersion)
    }
    @Test fun unknownMarkerAndUnmarkedTextHaveNoCitations() {
        assertTrue(StudyCitationMarkers.citations("An answer", sources).isEmpty())
        assertTrue(StudyCitationMarkers.citations("An answer [S9]", sources).isEmpty())
        assertEquals(1, StudyCitationMarkers.unknownCount("[S9]", sources))
        assertFalse(StudyCitationMarkers.displayText("[S9]", sources).contains("source"))
    }
    @Test fun missingOrInactiveVisualCannotBeCited() {
        val absent = sources.first().copy(coverage = SourceCoverage.MISSING, text = "", locator = "Image")
        assertTrue(StudyCitationMarkers.citations("[S1]", listOf(absent)).isEmpty())
        assertEquals(1, StudyCitationMarkers.citations("[S1]", listOf(absent.copy(suppliedVisual = true))).size)
    }
    @Test fun legacyMultiSourceRevisionBelongsOnlyToFirstObject() {
        assertEquals(listOf(StudySessionSource("a", "7"), StudySessionSource("b", null)), StudyJson.decodeSources("[]", listOf("a", "b"), 7))
        assertEquals(3, session().toPortable().schemaVersion)
    }
    @Test fun perSourceVersionsAndLanguagesRoundTripIndependently() {
        val refs = listOf(StudySessionSource("a", "7"), StudySessionSource("b", "abcdef"))
        assertEquals(refs, StudyJson.decodeSources(StudyJson.encodeSources(refs), emptyList(), null))
        val portable = session().copy(sources = refs, learnerLevel = StudyLearnerLevel.PRIMARY,
            supportStyle = StudySupportStyle.EXPLAIN, knowledgePolicy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL).toPortable()
        assertEquals(refs, portable.sources)
        assertEquals(StudyLanguagePreferences("lv", "ru", "pl"), portable.languages)
        assertEquals(StudyLearnerLevel.PRIMARY, portable.learnerLevel)
    }
    @Test fun strictPracticeCardsCarryExactProvenance() {
        val drafts = StudyCardDraftParser.parse("""{"cards":[{"question":"What?","answer":"That","source":"S2"}]}""", sources)!!
        assertEquals("b", drafts.single().source.objectId)
        assertEquals("Block 9", drafts.single().source.locator)
        assertEquals("8", drafts.single().source.version)
    }
    @Test fun invalidCardsAreRejectedAsWholeBatch() {
        listOf("""{"cards":[{"question":"What?","answer":"That","source":"S9"}]}""",
            """{"cards":[{"question":"","answer":"That","source":"S1"}]}""",
            """{'cards':[]} """, """{"cards":[]} trailing""",
            """{"cards":[{"question":1,"answer":"That","source":"S1"}]}""").forEach { assertNull(StudyCardDraftParser.parse(it, sources)) }
        val oversized = """{"cards":[{"question":"${"q".repeat(501)}","answer":"That","source":"S1"}]}"""
        assertNull(StudyCardDraftParser.parse(oversized, sources))
    }
    @Test fun solutionRequiresExplicitReveal() {
        assertFalse(TutorStagePolicy.allows(TutorStage.HINT, TutorStage.SOLUTION, false))
        assertTrue(TutorStagePolicy.allows(TutorStage.HINT, TutorStage.SOLUTION, true))
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private class Provider(override val isLocal: Boolean = true, val vision: Boolean = false,
        val beforeGenerate: suspend () -> Unit = {}) : AiInferenceProvider {
        override val id = if (isLocal) "local" else "remote"
        override val displayName = id
        override val status = MutableStateFlow(ProviderStatus.READY)
        val requests = mutableListOf<AiRequest>()
        override suspend fun getCapabilities() = if (vision) setOf(AiCapability.TEXT, AiCapability.IMAGE) else setOf(AiCapability.TEXT)
        override suspend fun listModels() = listOf(AiModel("model", "Model", id, getCapabilities(), 8192, false, supportsTools = false,
            executionLocation = if (isLocal) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE,
            capabilityEvidence = if (vision) mapOf(AiCapability.IMAGE to CapabilityEvidence.HALO_PROBE_SUCCESS) else emptyMap()))
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            beforeGenerate()
            requests += request
            return AiResponse("A hint [S2]", providerId = id, modelId = "model")
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }
    private fun router(provider: Provider) = DefaultAiProviderRouter(listOf(provider), MemoryStorage(), defaultInferenceProviderId = provider.id, defaultInferenceModelId = "model")
    private fun visual() = ResolvedStudySource.Pdf("b", "hash", "Workbook", 12, 2)
    @Test fun sourceOnlyAndLevelLanguageInstructionsReachProvider() = runBlocking {
        val provider = Provider()
        val gateway = AiRouterTutorGateway(router(provider), { false })
        val response = gateway.respond(turn(session().copy(learnerLevel = StudyLearnerLevel.PRIMARY))) as TutorTurnResponse.Ready
        val prompt = provider.requests.single().text!!
        assertTrue(prompt.contains("Use only supplied material"))
        assertTrue(prompt.contains("information is missing"))
        assertTrue(prompt.contains("at most 120 words", ignoreCase = true))
        assertTrue(prompt.contains("Explain in ru"))
        assertTrue(prompt.contains("brief clarification"))
        assertFalse(prompt.contains("[a]"))
        assertEquals(listOf("b"), response.citations.map { it.sourceObjectId })
        assertEquals(StudyVerdict.UNVERIFIED, response.verdict)
    }
    @Test fun topicLessonUsesQuestionWithoutCitations() = runBlocking {
        val provider = Provider()
        val response = AiRouterTutorGateway(router(provider), { false }).respond(turn(session(emptyList()), emptyList())) as TutorTurnResponse.Ready
        assertTrue(provider.requests.single().text!!.contains("standalone topic"))
        assertTrue(response.citations.isEmpty())
    }
    @Test fun textOnlyTargetNeverReceivesVisualBytes() = runBlocking {
        val provider = Provider()
        var preparations = 0
        val gateway = AiRouterTutorGateway(router(provider), { true }, prepareVisual = { preparations++; error("Must not prepare") })
        assertTrue(gateway.respond(turn(visual = visual())) is TutorTurnResponse.Unavailable)
        assertEquals(0, preparations)
        assertTrue(provider.requests.isEmpty())
    }
    @Test fun localOnlyVisualExcludesRemoteProviderBeforePreparation() = runBlocking {
        val provider = Provider(isLocal = false, vision = true)
        var preparations = 0
        val gateway = AiRouterTutorGateway(router(provider), { true }, prepareVisual = { preparations++; error("Must not prepare") }, permitsVisual = { _, local -> local })
        assertTrue(gateway.respond(turn(visual = visual())) is TutorTurnResponse.Unavailable)
        assertEquals(0, preparations)
        assertTrue(provider.requests.isEmpty())
    }
    @Test fun selectedPdfPageAndNoteBUseOneImageAndBoundedText() = runBlocking {
        val provider = Provider(vision = true)
        val preparedPages = mutableListOf<Int>()
        val gateway = AiRouterTutorGateway(router(provider), { false }, prepareVisual = {
            preparedPages += (it as ResolvedStudySource.Pdf).selectedPage
            PreparedImageInput(MediaAssetContent(byteArrayOf(1, 2), "image/jpeg"), ImagePreparationSummary(null, 100, 100, 100, 100, 2, 2, "image/jpeg", true, "test"))
        })
        val result = gateway.respond(turn(session(listOf("a", "b", "c")),
            sources + StudySourceExcerpt("c", null, "Workbook", "", "Page 2", SourceCoverage.MISSING, version = "hash"),
            visual().copy(objectId = "c"))) as TutorTurnResponse.Ready
        assertEquals(listOf(2), preparedPages)
        assertNotNull(provider.requests.single().image)
        assertTrue(provider.requests.single().text!!.contains("Second passage"))
        assertEquals(ToolUseMode.NONE, provider.requests.single().toolUseMode)
        assertEquals(StudyVerdict.UNVERIFIED, result.verdict)
    }

    @Test fun reviewRetainsCardIdentityAndEditedCardRejectsRegeneration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(listOf("a"), 7, StudyLanguagePreferences(), "Goal")
            val useCases = StudyUseCases(repository)
            val card = repository.addPracticeDraft(session.id, "What?", "Answer", "a", 7, sourceVersion = "7", sourceLocator = "Block 3")
            assertFalse(card.accepted)
            useCases.reviewPracticeItem(card.id, true)
            repository.recordReview(card, StudyReviewPolicy().schedule(card, true, 10), sourceRevision = 99)
            assertEquals(7L, repository.getPracticeItem(card.id)!!.sourceRevision)
            assertEquals("7", repository.getPracticeItem(card.id)!!.sourceVersion)
            assertEquals("Block 3", repository.getPracticeItem(card.id)!!.sourceLocator)
            useCases.updatePracticeItem(card.id, "Edited", "Edited answer", false)
            assertEquals(PracticeRegeneration.RefusedUserEdited, useCases.regeneratePracticeItem(card.id, "Replacement", "Answer"))
            assertEquals("Edited", repository.getPracticeItem(card.id)!!.question)
        } finally { db.close() }
    }
    private class Files(private val file: WorkspaceFile) : WorkspaceFileRepository {
        override suspend fun getFile(objectId: WorkspaceObjectId) = file.takeIf { it.objectId == objectId }
        override fun observeFiles() = flowOf(listOf(file))
        override suspend fun importFile(uri: Uri, displayName: String?, mimeType: String?, sizeBytes: Long?, source: WorkspaceObjectSource) = error("No acquisition")
        override suspend fun keepLocalCopy(objectId: WorkspaceObjectId) = error("No acquisition")
        override suspend fun relink(objectId: WorkspaceObjectId, uri: Uri) = error("No relink")
        override suspend fun reconcileAvailability() = Unit
    }
    private suspend fun withTextFile(bytes: ByteArray, availability: WorkspaceFileAvailability = WorkspaceFileAvailability.AVAILABLE,
        test: suspend (WorkspaceStudySourceResolver) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val path = File(context.cacheDir, "study-input.txt").apply { writeBytes(bytes) }
        try {
            db.notebookDao().insertWorkspaceObject(WorkspaceObjectEntity("file", "FILE", "Material", 1, 1, "IMPORT", "ACTIVE", false, "READY", "file:file"))
            val file = WorkspaceFile(WorkspaceObjectId("file"), Uri.fromFile(path), "Material", "text/plain", bytes.size.toLong(),
                FilePermissionMode.READ_ONLY, ContentOwnership.EXTERNAL_REFERENCE, availability, "sha")
            val pdf = object : StudyPdfPages {
                override suspend fun pageCount(file: WorkspaceFile): Int = error("Not PDF")
                override suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput = error("Not PDF")
            }
            val resolver = WorkspaceStudySourceResolver(context, NotebookDocumentRepository(db.notebookDocumentDao(), db),
                RoomWorkspaceRepository(db.notebookDao()), Files(file), pdf)
            test(resolver)
        } finally { db.close(); path.delete() }
    }
    @Test fun utf8FileCapAndPartialCoverageAreHonest() = runBlocking {
        withTextFile(("x".repeat(120_000)).toByteArray()) { resolver ->
            val source = resolver.resolve("file", "") as ResolvedStudySource.Text
            assertEquals(100_000, source.totalCharacters)
            assertEquals(SourceCoverage.PARTIAL, source.coverage)
            assertTrue(source.passages.size <= 3)
        }
    }
    @Test fun bomIsRemovedAndInvalidUtf8IsUnsupported() = runBlocking {
        withTextFile("\ufeffHello material".toByteArray()) { resolver ->
            val source = resolver.resolve("file", "") as ResolvedStudySource.Text
            assertEquals("Hello material", source.passages.single().text)
            assertEquals(SourceCoverage.FULL, source.coverage)
        }
        withTextFile(byteArrayOf(0xc3.toByte(), 0x28)) { assertTrue(it.resolve("file", "") is ResolvedStudySource.Unsupported) }
    }
    @Test fun uriPermissionLossIsExplicit() = runBlocking {
        withTextFile("Material".toByteArray(), WorkspaceFileAvailability.NEEDS_RELINK) {
            val source = it.resolve("file", "") as ResolvedStudySource.Unsupported
            assertEquals("Needs relink", source.reason)
        }
    }
    @Test fun fourSourcesRespectCombinedBudgetAndDistinctVersions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val useCases = StudyUseCases(repository, sourceResolver = StudySourceResolver { id, _ ->
                ResolvedStudySource.Text(id, "version-$id", id, listOf(StudyPassage("x".repeat(1600), StudySourceLocator.TextRange(0,1600)),
                    StudyPassage("y".repeat(1600), StudySourceLocator.TextRange(1600,3200)), StudyPassage("z".repeat(1600), StudySourceLocator.TextRange(3200,4800))), 4800, SourceCoverage.FULL)
            })
            val result = useCases.startMaterial(listOf("a", "b", "c", "d"), StudyLanguagePreferences(), "Goal") as StudyStartResult.Started
            val refs = useCases.sourceReferences(result.session)
            assertTrue(refs.sumOf { it.excerpt.orEmpty().length } <= 8000)
            assertEquals(listOf("version-a", "version-b", "version-c", "version-d"), result.session.sources.map { it.revision })
            assertEquals(listOf("a", "b", "c", "d"), refs.map { it.objectId })
        } finally { db.close() }
    }

    @Test fun shortMultiBlockNoteSuppliesAllRealBlocks() {
        val passages = StudyPassageSelector.notePassages(listOf("One", "Two", "Three", "Four"))
        assertEquals(1, passages.size)
        assertEquals(StudySourceLocator.NoteBlocks(1, 4), passages.single().locator)
        assertEquals("One\nTwo\nThree\nFour", passages.single().text)
    }
    @Test fun smallBudgetPreservesRelevantLaterPassage() {
        val passages = (1..20).map { StudyPassage(if (it == 18) "chlorophyll " + "x".repeat(1500) else "x".repeat(1500), StudySourceLocator.NoteBlocks(it, it)) }
        assertEquals(StudySourceLocator.NoteBlocks(18, 18), StudyPassageSelector.select(passages, "chlorophyll", 2000).single().locator)
    }
    @Test fun initialAskIncludesBeginningAndGoalWhereBudgetAllows() {
        val passages = (1..20).map { StudyPassage(if (it == 18) "chlorophyll" else "paragraph", StudySourceLocator.NoteBlocks(it, it)) }
        val result = StudyPassageSelector.select(passages, "chlorophyll", includeBeginning = true)
        assertTrue(result.any { it.locator == StudySourceLocator.NoteBlocks(1,1) })
        assertTrue(result.any { it.locator == StudySourceLocator.NoteBlocks(18,18) })
    }
    @Test fun allLearnerLevelsReachBoundedPromptAndGeneralPolicyIsExplicit() {
        val gateway = AiRouterTutorGateway(router(Provider()), { false })
        StudyLearnerLevel.entries.forEach { level ->
            val prompt = gateway.buildPrompt(turn(session().copy(learnerLevel = level)))
            assertTrue(prompt.contains("at most ${level.wordLimit} words", ignoreCase = true))
        }
        val prompt = gateway.buildPrompt(turn(session().copy(knowledgePolicy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL)))
        assertTrue(prompt.contains("Clearly label any additional general explanation"))
        assertFalse(prompt.contains("Use only supplied material"))
    }
    @Test fun oversizedPdfDimensionsAreBoundedWithoutChangingPageIdentity() {
        listOf(20_000 to 10_000, 600 to 80_000, 100 to 100).forEach { (w,h) ->
            val (width,height) = StudyPdfPageRenderer.boundedSize(w,h)
            assertTrue(width in 1..1600 && height in 1..1600)
        }
    }
    @Test fun canonicalLongNoteRetrievesLaterExercise() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val body = (1..20).joinToString("\n\n") { if (it == 18) "chlorophyll exercise " + "x".repeat(1100) else "unrelated " + "x".repeat(1100) }
            val note = RoomNotebookRepository(db.notebookDao()).createNote(body, WorkspaceSource.USER_CREATED, "Biology")
            val files = object : WorkspaceFileRepository {
                override suspend fun getFile(objectId: WorkspaceObjectId): WorkspaceFile? = null
                override fun observeFiles(): Flow<List<WorkspaceFile>> = flowOf(emptyList())
                override suspend fun importFile(uri: Uri, displayName: String?, mimeType: String?, sizeBytes: Long?, source: WorkspaceObjectSource): WorkspaceFile = error("No acquisition")
                override suspend fun keepLocalCopy(objectId: WorkspaceObjectId): WorkspaceFile = error("No acquisition")
                override suspend fun relink(objectId: WorkspaceObjectId, uri: Uri): WorkspaceFile = error("No relink")
                override suspend fun reconcileAvailability() = Unit
            }
            val pdf = object : StudyPdfPages {
                override suspend fun pageCount(file: WorkspaceFile): Int = error("No PDF")
                override suspend fun render(file: WorkspaceFile, page: Int): PreparedImageInput = error("No rendering")
            }
            val resolver = WorkspaceStudySourceResolver(context, NotebookDocumentRepository(db.notebookDocumentDao(), db), RoomWorkspaceRepository(db.notebookDao()), files, pdf)
            val resolved = resolver.resolveForTurn(note.id, "chlorophyll", 2000, false) as ResolvedStudySource.Text
            assertTrue(resolved.passages.any { it.text.contains("chlorophyll") })
            assertEquals(SourceCoverage.PARTIAL, resolved.coverage)
            assertEquals("Biology", resolved.title)
        } finally { db.close() }
    }

    @Test fun workspaceSharingOffRefusesRemoteText() = runBlocking {
        val provider = Provider(isLocal = false)
        assertTrue(AiRouterTutorGateway(router(provider), { false }).respond(turn()) is TutorTurnResponse.Unavailable)
        assertTrue(provider.requests.isEmpty())
    }
    @Test fun childLessonCannotUseAdultCloudConsentWithoutTheChildGrant() = runBlocking {
        val provider = Provider(isLocal = false)
        val gateway = AiRouterTutorGateway(router(provider), { true }, allowChildCloudSharing = { false })
        val child = turn().copy(session = session().copy(learnerLevel = StudyLearnerLevel.PRIMARY))
        assertTrue(gateway.respond(child) is TutorTurnResponse.Unavailable)
        assertTrue(provider.requests.isEmpty())
    }
    @Test fun selectionChangeAfterVisionPreflightKeepsTheVerifiedTarget() = runBlocking {
        val local = Provider(vision = true)
        val remote = Provider(isLocal = false, vision = true)
        val router = DefaultAiProviderRouter(listOf(local, remote), MemoryStorage(),
            defaultInferenceProviderId = local.id, defaultInferenceModelId = "model")
        val gateway = AiRouterTutorGateway(router, { false }, permitsVisual = { _, localOnly -> localOnly }, prepareVisual = {
            router.selectTarget(AiModelRef(remote.id, "model"))
            PreparedImageInput(MediaAssetContent(byteArrayOf(1), "image/jpeg"), ImagePreparationSummary(null, 100, 100, 100, 100, 1, 1, "image/jpeg", true, "test"))
        })
        assertTrue(gateway.respond(turn(visual = visual())) is TutorTurnResponse.Ready)
        assertEquals(1, local.requests.size)
        assertTrue(remote.requests.isEmpty())
        assertEquals(remote.id, router.selectedProviderId.value)
    }
    @Test fun revokedSourceWhileInferenceRunsCannotBecomeAcceptedLesson() = runBlocking {
        var permitted = true
        val provider = Provider(beforeGenerate = { permitted = false })
        val gateway = AiRouterTutorGateway(router(provider), { false }, permitsText = { _, _ -> permitted })
        assertTrue(gateway.respond(turn()) is TutorTurnResponse.Unavailable)
        assertEquals(1, provider.requests.size)
    }
    @Test fun staleReviewSnapshotCannotDuplicateMasteryOrScheduleUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(), "Review")
            val card = repository.addPracticeDraft(session.id, "Question", "Answer", null, null)
            val useCases = StudyUseCases(repository)
            assertNotNull(useCases.reviewPracticeItem(card.id, true, expectedItem = card))
            assertNull(useCases.reviewPracticeItem(card.id, true, expectedItem = card))
            assertNull(repository.recordReview(card, StudyReviewPolicy().schedule(card, true, 10), null))
            assertEquals(1, repository.getPracticeItem(card.id)!!.reviewCount)
            assertEquals(1, repository.getReviewEvents(card.id).size)
        } finally { db.close() }
    }
    @Test fun recreatedRepositoryRestoresStagePreferencesAndIdempotentAttempt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val languages = StudyLanguagePreferences("lv", "ru", "en")
            val session = repository.createSession(listOf("source"), 7, languages, "Learn fractions")
            repository.updateSetup(session.id, listOf(StudySessionSource("source", "7")),
                StudyLearnerLevel.ADULT, StudySupportStyle.PRACTICE, StudyKnowledgePolicy.SOURCE_ONLY)
            val study = StudyUseCases(repository)
            study.moveTo(session.id, TutorStage.HINT)
            val first = study.submitAttempt(session.id, "My attempt", submissionKey = "one-submission") as StudyAttemptResult.Recorded
            val restored = StudyRepository(db, db.studyDao())
            val state = StudyUseCases(restored).resume(session.id)!!
            assertEquals(TutorStage.HINT, state.tutorStage)
            assertEquals(languages, state.languages)
            assertEquals(StudyLearnerLevel.ADULT, state.learnerLevel)
            assertEquals(StudySupportStyle.PRACTICE, state.supportStyle)
            assertEquals(StudyKnowledgePolicy.SOURCE_ONLY, state.knowledgePolicy)
            assertEquals(listOf(StudySessionSource("source", "7")), state.sources)
            val repeated = StudyUseCases(restored).submitAttempt(session.id, "My attempt", submissionKey = "one-submission") as StudyAttemptResult.Recorded
            assertEquals(first.attempt.id, repeated.attempt.id)
            assertEquals(1, restored.getAttempts(session.id).size)
        } finally { db.close() }
    }
    @Test fun policyChangeDuringPreparationStopsGeneration() = runBlocking {
        val provider = Provider(vision = true)
        var permitted = true
        val gateway = AiRouterTutorGateway(router(provider), { false }, permitsVisual = { _, _ -> permitted }, prepareVisual = {
            permitted = false
            PreparedImageInput(MediaAssetContent(byteArrayOf(1), "image/jpeg"), ImagePreparationSummary(null, 100, 100, 100, 100, 1, 1, "image/jpeg", true, "test"))
        })
        assertTrue(gateway.respond(turn(visual = visual())) is TutorTurnResponse.Unavailable)
        assertTrue(provider.requests.isEmpty())
    }

    @Test fun missingSourceDoesNotBlockOtherReadableMaterial() = runBlocking {
        val provider = Provider()
        val supplied = sources.mapIndexed { index, source -> if (index == 0) source.copy(text = "", coverage = SourceCoverage.MISSING) else source }
        val gateway = AiRouterTutorGateway(router(provider), { false }, permitsText = { id, _ -> id != "a" })
        assertTrue(gateway.respond(turn(excerpts = supplied)) is TutorTurnResponse.Ready)
        assertFalse(provider.requests.single().text!!.contains("First passage"))
    }
    @Test fun localOnlyLessonHistoryCannotBecomeRemoteContext() = runBlocking {
        val provider = Provider(isLocal = false)
        val gateway = AiRouterTutorGateway(router(provider), { true }, permitsText = { id, local -> id != "private" || local })
        val request = turn().copy(recentLesson = listOf("Private worksheet explanation"), recentLessonOrigins = listOf(listOf("private")))
        assertTrue(gateway.respond(request) is TutorTurnResponse.Unavailable)
        assertTrue(provider.requests.isEmpty())
    }
    @Test fun unknownLegacyHistoryIsOmittedFromNewPrompts() = runBlocking {
        val provider = Provider()
        val response = AiRouterTutorGateway(router(provider), { false }).respond(turn().copy(recentLesson = listOf("Legacy private source content")))
        assertTrue(response is TutorTurnResponse.Ready)
        assertFalse(provider.requests.single().text!!.contains("Legacy private source content"))
    }

    @Test fun onlyCanonicalFourSourceKeysCanCreateCitations() {
        val five = sources + sources + sources.take(1)
        assertTrue(StudyCitationMarkers.citations("[S5] [S01]", five).isEmpty())
        assertEquals(2, StudyCitationMarkers.unknownCount("[S5] [S01]", five))
    }
    @Test fun missingMaterialNeedsExplicitGeneralPermissionForTopic() = runBlocking {
        val provider = Provider()
        val missing = sources.map { it.copy(text = "", coverage = SourceCoverage.MISSING) }
        val gateway = AiRouterTutorGateway(router(provider), { false })
        assertTrue(gateway.respond(turn(excerpts = missing)) is TutorTurnResponse.Unavailable)
        val allowed = gateway.respond(turn(session().copy(knowledgePolicy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL), missing)) as TutorTurnResponse.Ready
        assertTrue(allowed.citations.isEmpty())
        assertEquals(1, provider.requests.size)
    }
    @Test fun startingFromUnavailableSourcesRequiresReadableMaterialOrPermittedTopic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = StudyRepository(db, db.studyDao())
            val useCases = StudyUseCases(repository, sourceResolver = StudySourceResolver { id, _ -> ResolvedStudySource.Unsupported(id, "Missing", "Needs relink") })
            assertEquals(StudyUnavailableReason.SOURCE_NEEDS_RELINK,
                (useCases.startMaterial(listOf("missing"), StudyLanguagePreferences(), "Algebra") as StudyStartResult.Unavailable).reason)
            val result = useCases.startMaterial(listOf("missing"), StudyLanguagePreferences(), "Algebra", policy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL) as StudyStartResult.Started
            assertEquals(listOf("missing"), result.session.sourceObjectIds)
            assertEquals(StudyKnowledgePolicy.SOURCE_PLUS_GENERAL, repository.getSession(result.session.id)!!.knowledgePolicy)
        } finally { db.close() }
    }

}
