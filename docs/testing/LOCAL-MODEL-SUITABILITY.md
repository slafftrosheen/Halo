# Local model suitability: evidence audit and phone test contract

Date: 2026-10-21, Europe/Riga. Audit base: `da67070485a193ee791ad72c22a807e9f9b109f9`, equal to fetched `origin/main`. The agent-facing ADB/test-APK workflow below implements a first controlled role screen. Broader quality/soak targets later in this document remain future campaign requirements. No new physical model results are claimed; Android SDK ADB showed no attached phone.

## Automated agent workflow through ADB and the test APK

The entry point is `com.halo.app.audio.LocalModelRolePhoneTest#qualifyInstalledRoles` in the instrumentation APK. Run it through `tools/hil/Run-LiteRtPhoneMatrix.ps1 -RoleSuite`, not by manually tapping diagnostic screens. One model/backend/language runs in each instrumentation process. The host inventories real installed artifacts, checks installed app/test APK hashes against the built files and passes exact model SHA/length into each test. This is installed-only testing; community acquisition/discovery remains the existing model catalog's responsibility.

Build both APKs using JDK 21 or the configured Android Studio JBR, then install each onto the explicitly selected phone without clearing data:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8'
$adb = 'C:/Users/slavd/AppData/Local/Android/Sdk/platform-tools/adb.exe'
& $adb devices -l
# Set this to an actual attached physical serial from the command above.
$phone = 'YOUR_PHONE_SERIAL'
& $adb -s $phone install -r -t app/build/outputs/apk/debug/app-debug.apk
& $adb -s $phone install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
./tools/hil/Run-LiteRtPhoneMatrix.ps1 -Adb $adb -Serial $phone -InstalledOnly -RoleSuite -Backend CPU -Languages EN,LV,RU -Repetitions 3 -ContextTokens 1024 -OutputDir phone-role-cpu-1024
```

For a first bounded run, substitute `-Models qwen3-0.6b-dynamic-int4` for `-InstalledOnly`. For a second phone, select its serial explicitly and use a different output directory. Never combine device results into one recommendation cell.

To resume after a disconnect, rerun the same command with `-Resume`. Resume re-inventories the device and requires the same app/test APK source stamp, device serial, selection hashes/lengths, backend, context, languages and repetition count. Only successful instrumentation with all five matching completed role rows is skipped; interrupted cells rerun. Resume is at model/backend/language granularity, not midway through a native generation. Negative semantic grades are completed evidence and need not be repeated merely because they failed. A changed artifact/APK/configuration requires a new directory.

`-TimeoutSeconds` bounds each instrumentation process (default 1800 seconds). Each extended text/vision case and tool loop also has a 120-second coroutine deadline. A host timeout kills its ADB subprocess, stops the target app, records an interrupted outcome and retains available evidence. These are diagnostic timeouts, not measured interactive latency guarantees. Use PowerShell 7 (`pwsh`); bounded process invocation uses .NET `ArgumentList` and avoids shell interpolation.

### What fixture suite v1 actually runs

- The existing suite v2 first checks cold/warm generation, three CHAT and three REASONING answers with real session ownership and teardown.
- Each requested language adds five FAST, five CHAT and five REASONING fixtures, repeated three times by default: 45 scored text cases per model/language. They cover instruction/extraction, grounding, negation, unknown answers, corrected history, arithmetic and constraints. Exact-answer scoring rejects arbitrary nonempty output. This is a compact controlled screen, not a general conversational or native-speaker quality certification.
- AGENTIC uses the provider's actual tool protocol/parser with a synthetic `fixture_lookup` definition and a fabricated in-memory tool result. The continuation must return the exact changing code with matching target/backend. No production tool dispatcher or real side effect is reachable. Three tool loops run by default; no tool protocol is explicitly UNSUPPORTED.
- VISION uses paired red/blue/green synthetic PNGs through the local provider only when both native metadata and the installed descriptor support image input. All colors must match. This is image routing/color discrimination, not OCR or general vision qualification. The English color-answer contract is intentionally constant across campaign languages.
- No STT, TTS, embedding semantic, WAKE or VAD success is fabricated. Non-generation artifacts are skipped by the LLM runner; their LLM-role report cells are unsupported. Those specialized workloads still need their own test-APK entry points and labelled fixtures.

The implemented grades are deliberately stricter and narrower than the future campaign targets below: FAST/CHAT/REASONING require every expected case correct, at least 15 observations, successful baseline role checks, proven warm residency and normal memory pressure. Full correctness with median case completion <=4000 ms is GOOD **on these fixtures**; slower/unmeasured timing is SUITABLE. Completed mismatches or failed baseline stability are NOT_SUITABLE. Short/incomplete evidence is NOT_TESTED. Tool and vision probes never receive GOOD from this small suite; passing complete probes are SUITABLE on their stated scope. No p95 or broad language quality claim follows from 15 repeated cases.

### Results the agent consumes

| File | Use |
| --- | --- |
| `selected-installed-models.json` | Immutable run ID, app/test source stamp, serial, configuration and selected artifact hashes |
| `summary.csv`, per-process `.txt` logs | Runner completion/crash/interruption truth, separately from semantic quality |
| `local-model-role-evidence.jsonl` | Content-free STARTED/CONFIGURATION/per-case/COMPLETED rows; case IDs, outcomes, timings, actual target/backend and suite identity |
| `suitability.csv` | One latest row per selected artifact/backend/language/role; fills missing evidence with NOT_TESTED; completed negative evidence remains visible |
| `installed-model-footprints.jsonl` | Sampled process PSS/native heap/RAM; GPU memory explicitly unavailable |

An instrumentation `OK (1 test)` means the campaign collected its observations. It does **not** mean every model role passed. Read `suitability.csv` and per-role grades. Failed/crashed/interrupted instrumentation cannot promote partial rows into suitable recommendations.

Compatible English results are also saved through the existing `LocalRoleQualificationRepository` and `WorkloadQualificationStore` when context, backend, threads and vision configuration match the current user profile. Expanded fixture failures conservatively veto overall qualification in that legacy store; the baseline's actual three-question scores are preserved, and precise per-role suitability remains in the export. This conservative veto is broader than the per-role report because the legacy store lacks extended-fixture identity. Existing profile provenance checks gate retained tool-loop evidence. A context override may intentionally prevent publication; inspect `selectionEvidenceSaved`. LV/RU evidence remains in the exported matrix because the existing production store has no language key. No role binding, model download or model tuning preference is changed. Extending production selection with language-specific recommendation labels remains separate work; do not import these rows as universal multilingual suitability.

GPU role runs retain the existing `-AllowGpu`, `-CpuCycleEvidenceDir` and `-CpuResidentEvidenceDir` prerequisites for the exact artifact/device/APKs. A resolved CPU fallback is never a GPU recommendation. First use `-Cycles 3 -ContextTokens 1024` and `-ResidentTurns 5 -ContextTokens 1024` in separate CPU matrix directories, then pass those directories for explicit GPU trials. CPU/GPU evidence stays separate.

Host contract checks (no phone needed): `./tools/hil/Test-LiteRtProbeStatus.ps1` and `./tools/hil/Test-LiteRtRoleEvidence.ps1`. They cover stale identity, interruption, latest negative evidence, complete role projection, subprocess exit/output and hard timeout. JVM fixture tests live in `LocalModelPhoneFixturesTest`; provider override/diagnostic regression tests live in `LiteRtLocalProviderStreamTest`.

### Harness verification on 2026-10-21

Both matching debug app and instrumentation APKs built successfully. The focused JVM checks passed 35/35: 15 provider-stream/diagnostic tests, 16 existing role-runner tests and four new fixture/scoring tests. Host protocol, identity/projection and subprocess-timeout checks passed. The disconnected-device role invocation correctly stopped with `NOT_RUN_DEVICE_UNAVAILABLE`; no model run or phone installation occurred. [APK hashes, test counts and delivery state](artifacts/LOCAL-MODEL-ROLE-HARNESS-2026-10-21/verification.json) and the adjacent build log/JUnit XML retain the evidence. This is a local, uncommitted build from a dirty checkout containing the preserved unrelated UI work, not a clean release certification.

## Outcome

Help a user choose a model for a job on their phone, in their language, without interpreting quantization names, metadata flags or raw timings. Grade an exact artifact separately for each role, device and effective backend. A model can be good for English chat, unsuitable for Russian tutoring and untested for tools at the same time.

Use the existing catalog, role runner, qualification repositories and workload selection owners. Do not build a competing model inventory or treat a community listing as Halo runtime support.

## What exists today

| Owner / test | What it establishes | Important gap |
| --- | --- | --- |
| `DownloadedModelDiagnosticsTest`, `LocalArtifactInspectionTest`, `tools/hil/Run-LiteRtPhoneMatrix.ps1` and archived phone matrices | Physical artifact checks, native metadata, bounded generation, fresh-engine/resident probes and provider checks where explicitly run | Marker/nonempty generation is compatibility evidence, not general task quality |
| `LocalModelManagerPhoneSmokeTest` | Import, deduplication and rejected-import cleanup contracts | Does not measure model answer quality |
| `LocalModelRoleQualificationRunner`, suite v2 | Four FAST samples; CHAT instruction, arithmetic and short context recall; three REASONING questions; callback-owned AGENTIC and VISION evidence | Tiny English fixture set; no multilingual quality, long-context quality or sustained-use assessment |
| `fastQualification` | Nonempty outputs and three warm timings; checks reported residency when available | Does not check FAST answers; missing residency telemetry can pass its raw verdict with unknown proof. A PASS may still be labelled VERY_SLOW |
| `LocalRoleQualificationRepository` / Room entity | Artifact/device/runtime/config/suite-bound LLM history and stability fields | No language or fixture-level quality breakdown; skipped/resource-busy runs are not durable campaign outcomes here |
| `WorkloadQualificationStore` | Separate workload evidence; stricter CHAT correctness and proven warm residency before recording stable role-suite evidence | Existing 3-question checks remain smoke-level; history is bounded to 256 entries, so it is not a complete campaign archive |
| `LocalModelDeviceFit` | Conservative storage/RAM estimate | Estimate does not prove load, speed, quality or sustained stability |
| `LocalModelBrowse` | Catalog task/capability and inventory filters | Declared capabilities and availability must remain distinct from measured suitability |

Source paths are under `providers/litert/src/main/java/com/halo/providers/litert/runtime`, `app/src/main/java/com/halo/app/{ai,data/models,ui/screens/settings}` and `sdk/provider-sdk`. Relevant JVM tests include `LocalModelRoleQualificationTest`, `LocalRoleQualificationRepositoryTest`, `LocalRoleQualificationMigrationTest`, `LocalModelDeviceFitTest` and `LocalModelBrowseTest`. They verify software contracts; they are not new physical model results.

## Previous results: retain, do not over-promote

| Evidence | Finding | Selection implication |
| --- | --- | --- |
| [2026-10-13 non-Qwen matrix](NON-QWEN-2026-10-13.md) | Six artifacts inspected; USB interruptions; eight-token limit could suppress final answers from thinking exports | Interrupted = incomplete, not unsuitable. Retest with enough bounded reasoning and answer budget |
| [AI-132, 2026-10-15](HALO-AI-132-2026-10-15.md) | 24 hash-verified artifacts; 23 readable native metadata; 14 LLMs produced nonempty CPU output across three engines; six passed five resident turns; eight failed resident criteria | Six are priority candidates for semantic tests, not six recommended models. All roles/languages without semantic evidence remain untested |
| AI-132 unreadable artifact `hf-bd11678b7f60ff805a525b60` | Hash-valid file failed native ModelInfo | Historical runtime incompatibility for that hash/runtime/device; no family-wide rejection |
| AI-132 embedding entries | Metadata only | Embedding semantic suitability remains untested |
| [AI-133 manual acceptance](HALO-AI-133-MANUAL-ACCEPTANCE.md) | Checklist begins NOT_RUN | A test specification is not a result |
| [Issues 33/34, 2026-10-19](HALO-ISSUES33-34-PHONE-2026-10-19.md) | Qwen3 0.6B Dynamic INT4, SHA `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`: CPU three strict probes, five resident turns, provider check; one GPU strict probe/provider check on Redmi 23124RA7EO | Best documented next baseline, still not role/language qualified. CPU first token 5512–5984 ms after conversation start; GPU 3887 ms in one sample. No percentile claim |
| Same 2026-10-19 report | Observed Russian Study answer was incoherent | Keep failed journey acceptance visible; do not assign the failure to a particular artifact without a matched trace |

The six AI-132 resident passes were `qwen3-0.6b-dynamic-int4`, `hf-9d6251014a57541e6d480246`, `hf-56dddfebc1a578ba0819b264`, `hf-a7b72b611ef7b000b9a8863b`, `hf-bd0aa69cc5a463dc68829a7a` and `hf-0715c8de6c56d9acce57bc95`. Resolve their full hashes from that report's artifact inventory before rerunning. Opaque IDs and family names are insufficient identity.

## Classification contract

Keep execution outcome separate from suitability:

- Execution: PASS, FAIL, NOT_RUN, INTERRUPTED, BLOCKED, UNSUPPORTED. Record stage and safe reason code. Disconnects, missing files and admission conflicts never become semantic failures.
- **Not tested**: no compatible completed task-quality evidence; includes historical/stale evidence with its date shown separately.
- **Suitable**: passes role quality and stability gates for the tested configuration; show measured latency and limitations.
- **Good for…**: suitable plus the stricter quality and interactive performance gates below. This is the recommendation tier.
- **Not suitable for…**: completed evidence fails required quality, stability or resource limits for that role/configuration. Preserve the reason and successful other roles. Unsupported task adapters are shown as unsupported in Halo, not poor model quality.

Compatibility key: repository and pinned revision, filename, SHA-256, task, phone/SoC/RAM, Android build, app and test APK hashes, source stamp including dirty state, runtime version, suite/fixture version, language, requested/effective backend, context, output/thinking budgets, sampler, threads, GPU layers and residency policy. Changed keys require retesting affected cells; retain older evidence as historical. Never merge CPU and GPU samples or transfer a recommendation between phones.

## Future broader coverage for every role and workload

These are proposed initial acceptance gates, not measured results. Version the fixtures and thresholds before the first campaign. Run EN, LV and RU separately where language is applicable; native-speaker review is required for free-form language quality.

| Role / workload | Minimum fixtures per language and run | Quality acceptance |
| --- | --- | --- |
| FAST | 20 short requests: direct answers, extraction, simple arithmetic, bounded format and abstention | At least 18/20 correct for suitable, 19/20 for good; no correctness credit for nonempty output alone |
| CHAT | 20 tasks: instruction following, summarization, grounded Q&A, structured extraction, uncertainty; five 6-turn conversations with corrections and distractors | At least 90% of scored assertions; good at least 95%; no invented source facts or cross-conversation leakage |
| REASONING / THINK | 20 held-out multi-step arithmetic, ordering, constraints and evidence-based decisions | At least 80% correct for suitable, 90% for good; score final answers, never thought length; exhausted budget recorded separately |
| AGENTIC / ACTION | 20 sandbox cases: schema/argument validity, no-tool decisions, missing-input questions, tool-result continuation, errors and injected tool content | At least 90% task success, 95% for good; 100% authority/provenance/no-unauthorized-action checks. Test actual supported protocol through Halo provider |
| VISION | 20 fixed synthetic/consented images: objects, spatial relations, OCR, grounded questions and unanswerable cases | At least 85% scored assertions, 95% for good; correct image routing required. Text-only answers do not count |
| EMBED | 50 labelled query/document sets per language, hard negatives, paraphrases and cross-language retrieval | Dimension/finite-vector/identity checks all pass; Recall@5 at least 0.80, good at least 0.90; report retrieval separately from vector generation |
| STT | 30 labelled clips per language, clean/noisy speech, silence and names/numbers | Proposed WER <=20%, good <=10%; silence must not generate fabricated speech; retain critical number/name accuracy separately |
| TTS | 20 fixed passages per language, numbers, punctuation, long text and cancellation | All syntheses finite/nonempty; human intelligibility >=4/5 for suitable; good >=4.5/5; no critical number/name errors. PCM alone cannot prove audibility |
| INTENT | 50 balanced labelled utterances including out-of-domain and ambiguous cases | Macro-F1 >=0.90, good >=0.95; ambiguity asks for clarification; classification cannot authorize an action |
| WAKE / VAD | Labelled positive/negative audio and timed speech boundaries through existing speech owners | Report misses, false activations/hour and endpoint errors. Freeze device-specific limits with a labelled corpus before grading; initially NOT_TESTED |

Specialized `.tflite` tasks need their actual adapters and preprocessing. An LLM must not be failed for lacking STT/TTS/embedding support; mark those cells unsupported when verified. Audio-understanding candidates also need fixed clip question-answering fixtures through an audio-capable adapter before any recommendation.

## Phone execution protocol

1. Enumerate attached phones and record hardware/OS, free storage/RAM, battery, charging and thermal status. Pin each serial explicitly. Preserve app data and user model selections. Snapshot installed artifacts before acquiring anything else.
2. Discover community candidates by task; pin repository revision and exact file/hash. Record license, required adapter/runtime and declared context/capabilities. Every new candidate starts NOT_TESTED. Do not download the whole organization: prioritize installed artifacts, then a small task-diverse shortlist fitting each phone.
3. Run serially: physical hash -> native task/metadata -> three fresh-engine checks -> provider-path check -> five resident isolation turns -> role semantics. A native crash stops that model/backend batch; preserve partial rows and continue only after resource recovery. Explicit GPU trials record fallback separately.
4. Screen at 1024 context where supported, capped to the artifact's real limit. Qualify finalists at the actual workload context and at roughly 25%, 50% and 80% of its usable input budget with distractor recall. Keep room for output/thinking. Never call a model long-context capable from its metadata limit alone.
5. Repeat semantic fixtures three times. Keep per-run failures visible. Use at least 30 valid timing samples per finalist/configuration before p50/p95 claims; report failures and timeouts outside the successful-sample latency distribution. Record engine load, first visible answer token, completion and output-token count separately from reasoning.
6. Run a 15-minute repeated workload soak and cancellation/recovery checks for finalists; sample process PSS, available RAM, thermal status and battery conditions. Report unavailable GPU memory/energy counters as NOT_MEASURED. Cooling and charge conditions must match comparisons. Fresh Engine with retained caches is not cache-cleared cold start.
7. Initial FAST good-performance gate: warm visible-answer TTFT p95 <=4 seconds, total answer p95 <=8 seconds. CHAT good gate: warm visible-answer TTFT p95 <=8 seconds. Publish slower passing candidates as suitable with timings. These are provisional product targets, not claims that existing models meet them; THINK and multimodal roles need explicit workload budgets before a good label.
8. Archive append-only machine-readable per-case rows, aggregate scores and a readable matrix after every model. Persist cancellation, interruption, timeout and unavailable-device outcomes as well as success. No user prompts, outputs, reasoning, credentials or tool arguments in diagnostics; synthetic fixtures are versioned test assets. Human quality review uses only consented fixtures.

## Make selection painless

Show role and language first, then “Good for Latvian chat on this phone”, “Suitable, slower replies”, “Not tested on this phone” or a concrete limitation. Show evidence date, backend, tested context and measured latency on demand. Keep estimated fit visibly separate. Filter by current compatible evidence and rank quality/stability before speed, then memory/storage cost. Explain why an option is recommended and offer an explicit test action for untested installed candidates.

Reuse `LocalRoleQualificationRepository` and `WorkloadQualificationStore` for production evidence. Extend their versioned identities for language, fixture suite and effective settings before importing expanded results. Keep the complete campaign archive outside bounded on-device history. Recommendation must not silently overwrite explicit user bindings or trigger downloads. Unsupported tasks and blocked runs need distinct labels. Re-running a changed artifact invalidates only the relevant current evidence, not the audit trail.

## Remaining implementation after the automated screen

Expand held-out fixtures and specialized workload adapters, then connect language-keyed current grades to the existing selection UI. The first automated screen now adds strict FAST answer scoring, proven residency gates, resumable model/language exports and exact identity checks. Broader vision semantics, speech/embedding testing, soak testing and percentile measurements remain with their existing owners. Start physical semantic tests with the installed Qwen baseline and the other historical resident passes, independently on each attached phone.

Community source checked on 2026-10-21: [LiteRT Community models](https://huggingface.co/litert-community/models). Runtime evaluation reference: [Google LiteRT-LM build/run documentation](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/getting-started/build-and-run.md). Neither source qualifies a model for Halo; pin revisions when creating the campaign inventory.
