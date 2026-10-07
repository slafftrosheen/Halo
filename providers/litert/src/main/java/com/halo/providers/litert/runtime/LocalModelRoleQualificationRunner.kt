package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import java.util.UUID

/** Explicit diagnostic boundary: no configuration, acquisition, TTS or production observation sink. */
class LocalModelRoleQualificationRunner(
    private val session: LocalModelSession,
    private val inventory: LocalModelInventoryRepository,
    private val admissionAllowed: () -> Boolean = { true },
    private val memoryPressure: () -> com.halo.sdk.runtime.NativeMemoryPressure? = { null },
) {
    suspend fun runRoleMatrix(models: List<LocalModelDescriptor>,
        loadConfigFor: suspend (LocalModelDescriptor) -> RuntimeLoadConfig,
        agentic: suspend (LocalModelDescriptor, String) -> LocalAgenticQualification,
        acquire: suspend () -> Unit = {}, release: suspend () -> Unit = {},
        requestedConfigFor: suspend (LocalModelDescriptor) -> RuntimeLoadConfig = loadConfigFor,
        vision: (LocalModelDescriptor) -> LocalVisionQualification = { LocalVisionQualification(LocalRoleProbeVerdict.NOT_APPLICABLE) },
        onRow: suspend (LocalModelRoleQualification) -> Unit = {},
    ): List<LocalModelRoleQualification> {
        val rows = mutableListOf<LocalModelRoleQualification>()
        for (model in models.filter { it.valid && it.verified }.distinctBy { it.id }) {
            currentCoroutineContext().ensureActive()
            val row = try { runSuite(model.id, loadConfigFor, agentic, acquire, release, requestedConfigFor, vision) }
            catch (cancelled: CancellationException) {
                val now = System.currentTimeMillis()
                onRow(LocalModelRoleQualification(model.id, model.sha256.orEmpty(), model.bytes, null, null, null, null, null, null,
                    failure = LocalRoleProbeFailure.CANCELLED, startedAtMs = now, completedAtMs = now))
                throw cancelled
            } catch (_: Exception) {
                val now = System.currentTimeMillis()
                LocalModelRoleQualification(model.id, model.sha256.orEmpty(), model.bytes, null, null, null, null, null, null,
                    failure = LocalRoleProbeFailure.GENERATION_FAILED, startedAtMs = now, completedAtMs = now)
            }
            rows += row
            onRow(row)
        }
        return rows
    }

    suspend fun runSuite(modelId: String, loadConfigFor: suspend (LocalModelDescriptor) -> RuntimeLoadConfig,
        agentic: suspend (LocalModelDescriptor, String) -> LocalAgenticQualification,
        acquire: suspend () -> Unit = {}, release: suspend () -> Unit = {},
        requestedConfigFor: suspend (LocalModelDescriptor) -> RuntimeLoadConfig = loadConfigFor,
        vision: (LocalModelDescriptor) -> LocalVisionQualification = { LocalVisionQualification(LocalRoleProbeVerdict.NOT_APPLICABLE) },
    ): LocalModelRoleQualification {
        val start = System.currentTimeMillis()
        var row = LocalModelRoleQualification(modelId, "", 0, null, null, null, null, null, null,
            startedAtMs = start, completedAtMs = start)
        val owner = UUID.randomUUID().toString()
        if (!admissionAllowed() || session.tryBeginBenchmark(owner) !is InferenceAdmission.Granted)
            return row.copy(failure = LocalRoleProbeFailure.RESOURCE_BUSY, completedAtMs = System.currentTimeMillis())
        var acquired = false
        var loaded = false
        var peakPressure = memoryPressure()
        fun samplePressure() {
            val pressure = memoryPressure()
            if (pressure != null && (peakPressure == null || pressure.ordinal > peakPressure!!.ordinal)) peakPressure = pressure
        }
        var before: LocalModelDescriptor? = null
        var drift = false
        try {
            before = inventory.verifyPhysicalArtifact(modelId)
            val descriptor = before?.takeIf { it.valid && !it.sha256.isNullOrBlank() }
                ?: return row.copy(failure = LocalRoleProbeFailure.ARTIFACT_UNAVAILABLE)
            val requested = requestedConfigFor(descriptor)
            row = row.copy(artifactFingerprint = descriptor.sha256!!, artifactBytes = descriptor.bytes,
                requestedBackend = requested.computeBackend, threads = requested.cpuThreads, vision = vision(descriptor))
            acquire()
            acquired = true
            // Force a real cold residency, including when this model was already used interactively.
            session.unloadModel().getOrThrow()
            val loadStart = System.currentTimeMillis()
            val effective = loadConfigFor(descriptor)
            // Preserve attempted configuration for a physically validated load-failure snapshot.
            row = row.copy(backend = effective.computeBackend,
                contextTokens = effective.contextSize.takeUnless { effective.useModelDefaultContext },
                gpuLayers = if (effective.computeBackend == LocalComputeBackend.CPU) 0 else effective.gpuLayers)
            val load = session.loadModel(descriptor.id, descriptor.path, config = effective, artifactSha256 = descriptor.sha256)
            row = row.copy(loadMs = System.currentTimeMillis() - loadStart)
            load.getOrThrow()
            loaded = true
            samplePressure()
            val backend = session.activeBackend
            row = row.copy(backend = backend, contextTokens = session.effectiveContextSize,
                gpuLayers = session.activeGpuLayers, loadMs = System.currentTimeMillis() - loadStart)
            suspend fun generate(prompt: String, limit: Int, messages: List<RuntimeConversationMessage> = emptyList(), reasoning: Boolean = false): RuntimeGenerationResult? {
                currentCoroutineContext().ensureActive()
                val result = try {
                    session.generate(RuntimeGenerationConfig(prompt = prompt, maxTokens = limit,
                        thinkingEnabled = if (descriptor.supportsThinking) reasoning else null,
                        thinkingTokenBudget = if (descriptor.supportsThinking && reasoning) LOCAL_INTERACTIVE_REASONING_TOKEN_BUDGET else null,
                        temperature = if (descriptor.supportsThinking && reasoning) 0.6 else 0.0,
                        topK = if (descriptor.supportsThinking && reasoning) 20 else 1,
                        topP = if (descriptor.supportsThinking && reasoning) 0.95 else 0.9,
                        priority = InferencePriority.INTERACTIVE, initialMessages = messages), benchmarkRequestId = owner)
                        .getOrThrow()
                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                samplePressure()
                if (session.activeBackend != backend) drift = true
                return result
            }
            val fast = listOf("Reply with exactly HALO_READY.", "Reply with exactly HALO_FAST_ONE.",
                "What is 2+3? Reply with only the number.", "Reply with exactly HALO_FAST_THREE.")
                .map { generate(it, 32) }
            row = row.copy(fast = fastQualification(fast, backend?.name))
            val chatSamples = listOf(
                generate("Reply with exactly HALO_CHAT_OK and nothing else.", 64),
                generate("What is 17 multiplied by 8? Reply with only the number.", 64),
                generate("What is the test code word? Reply with only the code word.", 64,
                    listOf(RuntimeConversationMessage(Role.USER, "For this test, remember that the code word is COBALT-47."),
                        RuntimeConversationMessage(Role.ASSISTANT, "Understood."))),
            )
            val chat = chatSamples.zip(listOf("HALO_CHAT_OK", "136", "COBALT-47")).map { (sample, expected) -> sample != null && probeMatches(sample.text, expected) }
            row = row.copy(chat = LocalChatQualification(chat.count { it }, 3, chat[0], chat[1], chat[2], verdict(chat.all { it }),
                if (chatSamples.any { it == null }) LocalRoleProbeFailure.GENERATION_FAILED
                else if (chatSamples.any { it?.text.isNullOrBlank() }) LocalRoleProbeFailure.EMPTY_OUTPUT
                else if (!chat[0]) LocalRoleProbeFailure.INSTRUCTION_NOT_FOLLOWED
                else if (!chat.all { it }) LocalRoleProbeFailure.WRONG_ANSWER else null))
            val reasoning = listOf(
                "A workshop cuts 23 panels. Each panel requires 7 clips. 19 clips are already in stock. How many additional clips are needed? Give only the final number.",
                "Alex is taller than Ben. Ben is taller than Chris. Who is the shortest? Reply with only the name.",
                "A robot has 4 batteries. Each battery powers it for 35 minutes. It has already used 50 minutes of total battery time. How many minutes remain? Reply with only the number.",
            ).map { generate(it, 192, reasoning = true) }
            val passed = reasoning.zip(listOf("142", "Chris", "90")).count { (sample, expected) -> sample != null && probeMatches(sample.text, expected) }
            row = row.copy(reasoning = LocalReasoningQualification(passed, 3, reasoning.sumOf { it?.reasoningText?.length ?: 0 }, verdict(passed == 3),
                if (reasoning.any { it == null }) LocalRoleProbeFailure.GENERATION_FAILED
                else if (reasoning.any { it?.text.isNullOrBlank() }) LocalRoleProbeFailure.EMPTY_OUTPUT
                else if (passed != 3) LocalRoleProbeFailure.WRONG_ANSWER else null))
            row = row.copy(agentic = agentic(descriptor, owner))
            samplePressure()
            if (session.activeBackend != backend || row.agentic?.failure == LocalRoleProbeFailure.BACKEND_CHANGED) drift = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            row = row.copy(failure = if (error is AiProviderException && error.type == AiErrorType.SESSION_CONFLICT)
                LocalRoleProbeFailure.RESOURCE_BUSY else if (!loaded) LocalRoleProbeFailure.LOAD_FAILED else LocalRoleProbeFailure.GENERATION_FAILED)
        } finally {
            withContext(NonCancellable) {
                try { if (acquired) { try { session.unloadModel().getOrThrow() } finally { release() } } }
                finally { session.endBenchmark(owner) }
            }
        }
        val after = try { inventory.verifyPhysicalArtifact(modelId) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        val identityChanged = before != null && (after?.sha256 != before.sha256 || after?.bytes != before.bytes)
        if (identityChanged || drift) row = row.copy(fast = null, chat = null, reasoning = null, agentic = null, vision = null,
            failure = if (identityChanged) LocalRoleProbeFailure.ARTIFACT_CHANGED else LocalRoleProbeFailure.BACKEND_CHANGED)
        return row.copy(completedAtMs = System.currentTimeMillis(), memoryPressure = peakPressure?.name)
    }
    private fun verdict(pass: Boolean) = if (pass) LocalRoleProbeVerdict.PASS else LocalRoleProbeVerdict.FAIL
}
