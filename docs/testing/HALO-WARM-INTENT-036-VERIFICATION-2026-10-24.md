# #36 explicit warmed final-text intent diagnostic

Source base: fetched main `1e0d6cf9a6573836609f3e0afc8812332271dd00`, clean0/0 before edits. No production source changes in this batch. #36 remains open.

## Implementation and review

The previous fixture intentionally kept the provider cold, so it could not reach the production warm-only advice branch. The separate `classifyWarmFinalTextMatrix` initializes the selected verified model through the graph's existing LiteRtLocalProvider/LocalModelSession/coordinator, then invokes the unmodified LocalIntentModelRouter. Existing backend resolution must be CPU. Optional context arguments assert current settings, never override them. Router enabled/model keys are temporary MemoryStorage; provider roles, model choices, backend/context/tuning and privacy choices are preserved. Ordinary content-free native health/attempt journals remain owned by the provider.

Review rejected a second provider instance (duplicate resource identity/resident observer) and a manual teardown predicate (could race with newer user work before a suspending unload). Final source reuses the single existing graph owner and returns lifecycle control to normal residency/eviction. No fixture unload or native-teardown qualification. The sampler always stops.

The shared loader validates the unchanged48-case fixture and SHA, EN/LV/RU/PL12 cases each,24 Voice/24 Keyboard prepared texts. These are final strings, not real speech recognition or keyboard UI input. Fixed criteria preceded execution: all48 cases start warm, all accepted labels match, no no-advice, all caller latencies<=2000ms. Record actual backend/context/artifact, expected/accepted label, no-advice/latency and content-free per-generation diagnostics. No private response/prompt/audio data, tool/CognitiveSession input, remote provider or action execution.

Native initialization120s and matrix180s deadlines are cooperative; host360s bounds blocked JNI. Accepted labels establish production-parser conformity, not independent capture of raw SDK output. Unmatched/no-advice/late/partial cases do not pass, and no suitability/recommendation is saved. Footprint samples are PSS/native heap at200ms, not exact peak RSS/GPU/energy. `medianMs` uses the upper middle observation for even sample counts; p95 uses nearest rank. The success criterion checks every call, not these summaries. Warm means the engine is initialized/ready; there is no preceding unscored generation primer.

`-WarmIntentSuite` uses the existing host matrix with exact-run/SHA completed-failure markers. Unmet CPU/context prerequisites, incomplete advice, fixture/latency misses, runtime failures and interruptions stay distinct. Scoped installed inventory reuses startup discovery and freshly verifies only requested IDs without USER_REQUEST clearing all cached fingerprints. Initial discovery can still hash installed files once. Full-inventory default/directory-stability guards remain. Host verifies requested-ID echo and scope, then exact APK/model/source identities.

## Preflight checks

37 runner protocol cases passed (11 existing,11 embedding,15 warm-intent), including exact-run marker mismatch, no-advice, quality/latency miss, prerequisite failure, interruption, crash, timeout and teardown failure truth. Ten invalid mode combinations rejected before inventory. Four selected-ID invalid cases (punctuation, duplicate, uppercase, count>128) reject before output creation after fixing PowerShell's default case-insensitive matching with `-cnotmatch`.

The initial uppercase negative preflight exposed that casing mismatch and unintentionally entered the old full-inventory fixture. Its host session17812 was explicitly cancelled (terminal exit1); the test target was stopped and MainActivity restored. No inference/qualification result is claimed from that cancelled inventory. All source/review corrections preceded the APK gate. Final read-only review: no actionable findings. PowerShell syntax and source whitespace checks passed.

## APK and physical result

Single test-package gate75111: **PASS5m21s**, 464 tasks (12 executed /452 up-to-date). Requested only `:app:assembleDebugAndroidTest`; its dependency graph compiled production/test classes, but the production APK was not assembled. No repeated unchanged desktop/model/storage/speech tests.

Installed production SHA-256 `f48801cdd8858d29d5b6e5b00275aca3fd82013bcdf713223b7a09a5bb334a20` (288504360 bytes); test SHA-256 `ca09c599056ecf69862a8029868b70dee0cb0e74aa277d110828ccc89881cfa0` (1963640 bytes). Both installed readbacks match. Only the test APK was installed in this batch. Seven source/fixture/host-script identities recorded before compilation still match after the phone run.

On connected device f01fe673 /23124RA7EO /API35, selected-ID inventory passed one case in53.454s, returning exactly the requested Qwen descriptor with `verificationScope=SELECTED_IDS`. Artifact SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`,344671744 bytes, LiteRT0.18.0; current configured context4096.

Warm run `ff064e0e07dc41b28bfbdc3d687d0684`: **INTENT_PREREQUISITE_UNMET /CPU_CONFIGURATION**. Existing production backend resolution was not CPU. The guard stopped before model initialization; zero classifications attempted. JUnit completed one case/one failure, host process exited1 after recording nonqualification. Warm classification accuracy and latency remain **NOT_TESTED**. No settings were overridden and no recommendation was saved. Setup-only sampled PSS363398KiB/native20788304 bytes are not inference memory qualification. No fixture manual unload; native ownership was false. MainActivity resumed afterward.

Raw bounded evidence is in [HALO-WARM-INTENT-036-2026-10-24](artifacts/HALO-WARM-INTENT-036-2026-10-24). The filtered footprint contains only this run; the device-wide historical raw journal is excluded. Build output records terminal success; native output records the failed prerequisite without converting it into a pass. The interrupted uppercase preflight remains external and is not qualification evidence.

## Remaining acceptance

Keep #36 open for actual spoken/keyboard action loops, full native/tool authorization/duplicate safety, UI/TTS/history markup behavior, low-RAM/STT/TTS priority/model-switch/restart/removal and full original acceptance. Cold-skip correctness, prepared-text classification and actual speech are separate evidence. No unrelated prior storage/embedding/speech gates repeated; no CI/Actions.

