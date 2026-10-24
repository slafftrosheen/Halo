package com.halo.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ModelInfo
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeLoadConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Fixed public fixtures only, fresh CPU engine per case. Never routes through remote providers. */
@RunWith(AndroidJUnit4::class)
class InstalledLanguageQualificationTest {
    private data class Fixture(val language: String, val kind: String, val prompt: String, val answers: Set<String>,
        val history: List<com.halo.sdk.provider.RuntimeConversationMessage> = emptyList())

    @Test fun fixedEnglishLatvianRussianFixtures() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        val source = requireNotNull(args.getString("sourceStamp"))
        val runId = requireNotNull(args.getString("runId"))
        val graph = ManualDependencyGraph.providerGraph
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        ModelInfo.from(descriptor.path).use { require(it is ModelInfo.Llm) }
        val provider = graph.providers.inference as LiteRtLocalProvider
        val config = RuntimeLoadConfig(contextSize = 1024, computeBackend = LocalComputeBackend.CPU)
        val roleChecks = args.getString("semanticRoles") == "true"
        val output = File(instrumentation.targetContext.filesDir,
            if (roleChecks) "installed-semantic-role-acceptance.jsonl" else "installed-language-acceptance.jsonl")
        val fixtures = if (roleChecks) listOf(
            Fixture("en", "chat_instruction", "Reply with exactly HALO_CHAT_OK.", setOf("halo_chat_ok")),
            Fixture("en", "chat_arithmetic", "What is 17 multiplied by 8? Reply with only the number.", setOf("136")),
            Fixture("en", "chat_recall", "What is the test code word? Reply with only the code word.", setOf("cobalt-47"),
                listOf(com.halo.sdk.provider.RuntimeConversationMessage(com.halo.sdk.provider.Role.USER,
                    "For this test, remember that the code word is COBALT-47."),
                    com.halo.sdk.provider.RuntimeConversationMessage(com.halo.sdk.provider.Role.ASSISTANT, "Understood."))),
            Fixture("en", "reasoning_arithmetic", "A workshop cuts 23 panels. Each panel requires 7 clips. 19 clips are already in stock. How many additional clips are needed? Give only the final number.", setOf("142")),
            Fixture("en", "reasoning_order", "Alex is taller than Ben. Ben is taller than Chris. Who is the shortest? Reply with only the name.", setOf("chris")),
            Fixture("en", "reasoning_budget", "A robot has 4 batteries. Each battery powers it for 35 minutes. It has already used 50 minutes of total battery time. How many minutes remain? Reply with only the number.", setOf("90")),
        ) else listOf(
            Fixture("en", "arithmetic", "What is 2 + 2? Reply with just the number.", setOf("4")),
            Fixture("lv", "arithmetic", "Cik ir 2 + 2? Atbildi tikai ar skaitli.", setOf("4")),
            Fixture("ru", "arithmetic", "Сколько будет 2 + 2? Ответь только числом.", setOf("4")),
            Fixture("en", "translation", "Translate the Latvian word kaķis into English. Reply with one word.", setOf("cat")),
            Fixture("lv", "translation", "Iztulko angļu vārdu cat latviski. Atbildi ar vienu vārdu.", setOf("kaķis")),
            Fixture("ru", "translation", "Переведи английское слово cat на русский. Ответь одним словом.", setOf("кот", "кошка")),
        )
        var passed = 0
        for (fixture in fixtures) {
            assertTrue("Existing inference could not unload", provider.unloadLocalModel())
            val row = JSONObject().put("modelId", modelId).put("sha256", descriptor.sha256)
                .put("bytes", descriptor.bytes).put("sourceStamp", source).put("runId", runId)
                .put("language", fixture.language).put("fixture", fixture.kind)
                .put("backendRequested", "CPU").put("contextRequested", 1024)
                .put("outputBudget", 256).put("stage", "ENGINE_INIT")
            val started = System.currentTimeMillis()
            try {
                val load = provider.modelSession.loadModel(modelId, descriptor.path, config = config,
                    artifactSha256 = descriptor.sha256).getOrThrow()
                row.put("initMs", System.currentTimeMillis() - started)
                    .put("backendEffective", load.activeBackend.name).put("contextEffective", load.effectiveContextSize)
                require(load.activeBackend == LocalComputeBackend.CPU)
                row.put("stage", "GENERATION")
                val result = withTimeout(120_000) {
                    provider.modelSession.generate(RuntimeGenerationConfig(prompt = fixture.prompt,
                        temperature = 0.0, topP = 1.0, topK = 1, maxTokens = 256, initialMessages = fixture.history))
                }.getOrThrow()
                val text = result.text.trim()
                val normalized = text.lowercase(Locale.ROOT).trim(' ', '\n', '\r', '.', '!', '"', '\'', '*', '`')
                val matched = normalized in fixture.answers
                if (matched) passed++
                row.put("stage", "COMPLETED").put("nonempty", text.isNotEmpty()).put("matched", matched)
                    .put("fixedFixtureOutput", text.take(256)).put("generationMs", result.durationMs)
                    .put("firstTokenMs", if (text.isEmpty()) JSONObject.NULL else result.timeToFirstTokenMs)
                    .put("status", if (matched) "PASSED" else if (text.isEmpty()) "NO_ANSWER_TEXT" else "ANSWER_MISMATCH")
            } catch (error: Exception) {
                row.put("status", "FAILED").put("exceptionType", error.javaClass.simpleName)
            } finally {
                row.put("engineUnloaded", provider.unloadLocalModel())
                row.put("totalMs", System.currentTimeMillis() - started)
                output.appendText(row.toString() + "\n")
            }
        }
        assertTrue("Fixed local fixtures passed $passed/${fixtures.size}; see content-bounded JSONL", passed == fixtures.size)
    }
}
