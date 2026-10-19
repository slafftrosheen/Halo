package com.halo.app.ai

import com.halo.engines.interpretation.rules.ModelIntentDecision
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtArtifactTask
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.providers.litert.models.LiteRtModelManager
import com.halo.providers.litert.models.LocalModelInventoryState
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex

/**
 * Advisory, on-device-only model for final-turn intent classification.
 * No tools, actions, memory, microphone, screen, files, cloud or untrusted chat history
 * are passed to this model. Its sole output is one of three bounded labels.
 *
 * A model label NEVER grants authorization, fills arguments, or bypasses the
 * host's native/agentic tool admission and review.
 */
class LocalIntentModelRouter(
    private val storage: Storage,
    private val inventory: LiteRtModelManager,
    private val provider: () -> LiteRtLocalProvider?,
) {
    /** Never queue two intent turns behind one another or behind an active generation. */
    private val intentLease = Mutex()

    companion object {
        const val MODEL_KEY = "intent_classifier_model_id"
        const val ENABLED_KEY = "intent_classifier_enabled"
        const val DEFAULT_ID = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4_ID
        const val TIMEOUT_MS = 2_000L

        /** Pure gate for source tests; never cold-switch a different resident LLM. */
        internal fun shouldDefer(residentId: String?, requestedId: String, busy: Boolean): Boolean =
            busy || (residentId != null && residentId != requestedId)

        private const val INSTRUCTIONS = """
You are Halo's intent gate. Classify whether the user's FINAL message asks the assistant to DO an external action or just ANSWER.
Reply with EXACTLY one word: ACTION, ANSWER, or UNCERTAIN.
ACTION: execute an operation via an app, device, tool, UI, reminder, search, message, or workflow. This includes indirect and polite requests.
ANSWER: explain, translate, discuss, write an example, ask how to do something, or mention an action without requesting that Halo do it.
UNCERTAIN: both plausible, negated/quoted command, or missing context.
Examples:
Open the camera -> ACTION
Can you send a message? -> UNCERTAIN
Send a message to my family -> ACTION
How do I send a message? -> ANSWER
Tell me how to open an app -> ANSWER
Найди мои заметки -> ACTION
Как найти заметки? -> ANSWER
Atver kameru -> ACTION
Kā atvērt kameru? -> ANSWER
Otwórz aparat -> ACTION
Jak otworzyć aparat? -> ANSWER
Do not perform any action. Do not output JSON, text, quotes, thinking, or explanations."""
        /** Reject any untrusted output that isn't exactly a single label. */
        fun parse(text: String): ModelIntentDecision? = when(text.trim().uppercase()) {
            "ACTION" -> ModelIntentDecision.ACTION
            "ANSWER" -> ModelIntentDecision.ANSWER
            "UNCERTAIN" -> ModelIntentDecision.UNCERTAIN
            else -> null
        }
    }

    fun configuredModelId(): String = storage.getString(MODEL_KEY)
        ?.takeIf(String::isNotBlank) ?: DEFAULT_ID

    suspend fun classify(finalText: String): ModelIntentDecision? {
        if (!storage.getBoolean(ENABLED_KEY, true) || finalText.length !in 3..384) return null
        val id = configuredModelId()
        val catalog = LiteRtModelCatalog.byId(id) ?: return null
        if (catalog.task != LiteRtArtifactTask.GENERATION || !catalog.supportsText ||
            catalog.tier != com.halo.providers.litert.models.LiteRtModelTier.SMALL) return null
        val installed = (inventory.inventoryState.value as? LocalModelInventoryState.Ready)
            ?.models?.firstOrNull { it.id == id && it.valid && it.verified && it.sha256 == catalog.sha256 }
            ?: return null
        val local = provider() ?: return null
        // The classifier shares the full LiteRT engine with chat. Never unload a
        // different resident conversational model merely to decide intent.
        // Never wait behind a running native inference (including another classifier).
        if (installed.sha256 != catalog.sha256 ||
            shouldDefer(local.residentModelIdForIntent(), id, local.inferenceBusyForIntent()) ||
            !intentLease.tryLock()) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                // Read again after acquiring the intent gate to avoid a competing
                // model switch or a second queued intent turn.
                if (shouldDefer(local.residentModelIdForIntent(), id,
                        local.inferenceBusyForIntent())) return@withTimeoutOrNull null
                val reply = local.generate(
                    AiRequest(text = finalText, modelId = id, maxOutputTokens = 12,
                        toolUseMode = ToolUseMode.NONE,
                        reasoningMode = AiReasoningMode.DISABLED,
                        publishStreamingTokens = false,
                        temperatureOverride = 0.0),
                    AiContext(conversation = listOf(AIMessage(Role.SYSTEM, INSTRUCTIONS))))
                parse(reply.text)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { intentLease.unlock() }
    }
}
