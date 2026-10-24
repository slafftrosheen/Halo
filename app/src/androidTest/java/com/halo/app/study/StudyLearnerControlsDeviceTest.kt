package com.halo.app.study

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.data.AppDatabase
import com.halo.app.data.study.StudyRepository
import com.halo.app.di.tools.HaloToolDescriptors
import com.halo.app.di.tools.WorkspaceStudyActions
import com.halo.app.ui.screens.study.StudyLessonStore
import com.halo.host.android.AndroidStorage
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Isolated real Room/registered owners; no global learning data, mic, inference or provider use. */
@RunWith(AndroidJUnit4::class)
class StudyLearnerControlsDeviceTest {
    @Test fun learnerAttemptCardReviewAndCompletionSurviveDatabaseRecreation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        val sourceStamp = requireNotNull(args.getString("sourceStamp"))
        val name = "study-controls-fixture-${UUID.randomUUID()}.db"
        val preferences = AndroidStorage(context, name = "study-controls-fixture-${UUID.randomUUID()}")
        val lessons = StudyLessonStore(preferences)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var database = open()
        try {
            var repository = StudyRepository(database, database.studyDao())
            var study = StudyUseCases(repository, localSessionData = lessons)
            var actions = WorkspaceStudyActions(database, repository, study)
            fun registry(): ToolRegistry {
                val descriptors = actions.definitions().map { requireNotNull(HaloToolDescriptors.workspace(it)) }
                return ToolRegistry(listOf(actions), descriptors).also {
                    assertTrue(it.operabilityReport(listOf(ToolFeatureOperability("study", descriptors.map { descriptor -> descriptor.name }.toSet()))).isEmpty())
                }
            }
            var tools = registry()
            suspend fun call(name: String, fields: Map<String, Any>): JSONObject {
                val call = ToolCall(UUID.randomUUID().toString(), name, fields)
                val policy = PolicyToolAuthorizer(defaultPolicyProvider = { ToolApprovalPolicy.ALWAYS_ALLOW },
                    descriptorProvider = { tools.descriptor(name) })
                val approved = policy.authorize(call) as ToolAuthorization.Authorized
                val result = tools.executeAuthorized(approved)
                assertTrue(result.toString(), result is ToolExecutionResult.Success)
                return JSONObject((result as ToolExecutionResult.Success).output)
            }
            val session = repository.createSession(emptyList(), null, StudyLanguagePreferences(explanationLanguage = "en"), "Fixture arithmetic")
            lessons.saveDraft(session.id, "A separate saved learner draft")
            suspend fun current() = call("halo_study_state", mapOf("sessionId" to session.id))
            fun stateArgs(state: JSONObject) = mapOf("sessionId" to session.id, "version" to state.getString("version"))
            var state = current()
            state = call("halo_study_select_exercise", stateArgs(state) + ("exerciseId" to "fixture-question"))
            val attempt = call("halo_study_submit_attempt", stateArgs(state) + mapOf("exerciseId" to "fixture-question", "response" to "One half plus one half equals one"))
            assertFalse(attempt.getBoolean("graded"))
            val created = call("halo_study_card_create", stateArgs(current()) + mapOf("question" to "One half plus one half?", "answer" to "One"))
            val itemId = created.getString("itemId")
            fun cardArgs(card: JSONObject) = stateArgs(card) + mapOf("itemId" to itemId, "itemVersion" to card.getString("itemVersion"))
            val accepted = call("halo_study_card_accept", cardArgs(created) + ("accepted" to true))
            val reviewed = call("halo_study_card_review", cardArgs(accepted) + ("rating" to "ALMOST"))
            assertEquals(1, reviewed.getInt("reviewCount"))
            val staleCall = ToolCall("stale-review", "halo_study_card_review", cardArgs(accepted) + ("rating" to "KNOWN"))
            assertTrue(tools.executeAuthorized(ToolAuthorization.Authorized(staleCall)) is ToolExecutionResult.Rejected)
            call("halo_study_complete", stateArgs(current()))
            database.close()
            database = open()
            repository = StudyRepository(database, database.studyDao())
            study = StudyUseCases(repository, localSessionData = lessons)
            actions = WorkspaceStudyActions(database, repository, study)
            tools = registry()
            val restored = current()
            assertEquals("COMPLETED", restored.getString("lifecycle"))
            val saved = call("halo_study_card_get", mapOf("sessionId" to session.id, "itemId" to itemId))
            assertEquals("One", saved.getString("answer")); assertTrue(saved.getBoolean("userEdited"))
            assertEquals(1, saved.getInt("reviewCount"))
            assertEquals("ALMOST", repository.getReviewEvents(itemId).single().recallRating)
            assertEquals(attempt.getString("attemptId"), repository.getAttempts(session.id).single().id)
            assertEquals("A separate saved learner draft", lessons.loadRecovery(session.id).draft)
            val row = JSONObject().put("scope", "ISOLATED_STUDY_LEARNER_CONTROLS")
                .put("runId", runId).put("sourceStamp", sourceStamp).put("status", "PASS")
                .put("registeredTools", actions.definitions().size).put("attempts", 1).put("reviewEvents", 1)
                .put("databaseRecreated", true).put("staleReviewRejected", true)
                .put("localRecoveryRetained", true)
                .put("spokenRecognition", "NOT_RUN").put("providerExecution", "NOT_RUN").put("osProcessDeath", "NOT_RUN")
            File(context.filesDir, "study-learner-controls-evidence.jsonl").appendText(row.toString() + "\n")
        } finally {
            database.close()
            context.deleteDatabase(name)
            preferences.clear()
        }
    }
}
