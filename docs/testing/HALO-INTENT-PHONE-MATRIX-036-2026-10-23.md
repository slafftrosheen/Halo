# Executable physical intent advice matrix — #36

Base main: `ca4c0e9d298cd2316eae6fb24e489a1711c091fa`. This batch adds instrumentation code/assets only. Production app source and APK are unchanged from the verified Polish UI phone gate.

## Scope

`LocalIntentClassifierPhoneTest#classifyPreparedFinalTextMatrix` executes the 48 synthetic EN/RU/LV/PL cases from the prepared [manual matrix](HALO-INTENT-150-CLASSIFICATION-MATRIX.md) through the actual production `LocalIntentModelRouter`. The prepared Voice rows are supplied as final text here: **this is not ASR, a spoken test, a keyboard UI journey or a tool/action loop**.

The harness verifies exact installed model SHA/length and the production small-generative-candidate constraints. Classifier preferences are isolated in memory; saved user role/model/classifier settings are untouched. It requires an idle session and no resident conversational model at entry. Only canonical nonblocking advisory admission may load/reuse a model; explicit diagnostic warm loading and cleanup were removed after source review identified a race with newer chat residency. Provider policy and the isolated instrumentation process own lifecycle/teardown.

One first cold observation precedes all48 prepared cases. Every case records actual `warmAtStart`, effective backend, expected/observed normalized label and elapsed time. No model responses, prompts or user content enter exported evidence. A missing decision is `NO_ADVICE`, not a proven semantic failure. Interrupted/incomplete suites remain `NOT_TESTED`. Completing the instrumentation test does not mean the model passed its semantic fixtures.

## Reproduction

Build `:app:assembleDebugAndroidTest` only after the source batch/review. Reuse app APK SHA-256 `484174eecbf9f0b1d9ab9fce548fa74dbdee68d840f06898813c0080ff828ce4`. The build preserves the unchanged app metadata (`ca93d4037ab2+dirty`) to avoid recompiling app code merely for a committed base label; the independent harness/fixture source manifest, new test APK hash and explicit `sourceStamp` identify the new test source.

Invoke the class through AndroidJUnitRunner with `modelId`, `expectedSha256`, `expectedBytes`, unique `runId` and `sourceStamp`. Current verified candidate snapshot: `qwen3-0.6b-dynamic-int4`, SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`,344671744 bytes. The harness verifies it again before native use. No downloads, persistent selection, backend override, qualification promotion, tools or cloud calls.

Export `files/intent-model-acceptance.jsonl` using app-owned debug access and select the exact runId. Original APK/readback identity must still match before the run. Read `COMPLETED`/`INCOMPLETE`, per-case outcomes and warmth separately; never turn AndroidJUnitRunner's `OK` into a model suitability claim.

## Results

**Instrumentation compilation PASS**, first gate,1m10s. App production Kotlin was `UP-TO-DATE`; no app APK rebuild/install. New test APK SHA-256: `75be97f86bd33b4dfffbae41eaead2c0de36d4cd1b512e15971f7360641d9bb4`. Source stamp `intent-phone-v1-8f811077e4d682007282`; [log and manifest](artifacts/HALO-INTENT-PHONE-MATRIX-036-2026-10-23). Offline asset comparison confirms all48 IDs/prompts/expected labels match the prepared manual matrix,12 per language.

**Physical matrix NOT_RUN.** The phone disconnected before the new run could verify the installed app, install the test APK or start instrumentation. ADB and Windows present-device inventory show no Android target. The previous11 physical checks in the Polish UI batch remain valid; they do not substitute for classifier inference. No native classification, accuracy, latency, warmth or model suitability result exists for this new harness yet. Source follow-up review is clear after removing the unsafe explicit mutations. No CI/Actions.

Full #36 acceptance remains open: actual spoken/keyboard/tool routes, side effects/authorization/duplicates, streamed markup UI/TTS/history, warm/cold distributions, low-RAM/model switching and supported language quality require their own evidence.
