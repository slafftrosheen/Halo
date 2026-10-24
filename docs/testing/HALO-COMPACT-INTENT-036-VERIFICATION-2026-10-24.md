# #36 compact intent workload

Base `5c9b1b68a86423863a509cde8d1dd8792fac1cbf`, clean fetched main0/0 before edits. **Source checks pass; physical intent qualification fails. Keep #36 open.** No CI/Actions.

## Source change

The actual provider mapping contains only the final message and classifier instructions. Tool protocol injection is skipped when tools are empty; the native runtime receives thinkingOFF and maxTokens12. There was no extra history or missing output limit to remove. Each fresh Conversation still processes its system instruction.

Reduced the instruction from974 to453 characters, replacing repeated examples with the three label definitions, quoted-command/information distinction, ambiguity/negation handling and explicit message-as-data/nonexecution. All48 EN/RU/LV/PL fixture IDs, prompts and expectations remain unchanged (SHA `13437f06ffe3f1ca3cb89a781ce06701ce1a1a2bf51248dfeb6b86daee0425ef`). No semantic accuracy is inferred from shortening instructions.

The existing exact-instruction qualification identity changes automatically to `a3fc404fef296b6934c91c99655ba2cbfae37b52b31b0df1f77e6961b227ab70`; old proof cannot qualify the new workload. Opt-in, ready/exact resident model, canonical mutex, qualification gate,2s cooperative deadline,12tokens,temperature0,thinkingOFF,no tools/history/cloud and action authorization remain unchanged.

Warm qualification now records protocol identity and stops after the first nonqualifying case. Its finally path saves a partial negative result through the existing qualification owner. A successful run still requires all48 warm cases correct and within2s. Review caught an unrecognized early-stop exception marker before the first gate; the existing exact-run/SHA `INTENT_OUTCOME ... result=NOT_TESTED` marker now produces host `INTENT_NOT_QUALIFIED`. No further actionable review findings.

## Focused verification

Production/regression sources complete before gate58466, terminal exit0: **PASS8m19s**,511 tasks (20executed/491up-to-date), both APKs. Router4 tests/0.062s and qualification17/5.721s, zero failures/errors/skips. The actual-provider regression checks compact prompt, final text only, no history/tools,12tokens,thinkingfalse,temperature0,topK1 and exact prompt-bound protocol identity. All three captured compiled-input hashes still match. The unchanged host parser's11 runner,11 embedding and15 exact-run intent protocol cases pass; no other Android suites or localization rebuild checks were repeated.

## Phone result

Phone f01fe673 /23124RA7EO /API35; LiteRT0.18.0. Installed production APK SHA `95748a53d7f373ab8e7b8fd7b3a044b595bb41b317e12864c74af56e87a7aa55`,290364120bytes; test SHA `21b3dcb918e6029c37fd81da43b949320d2e5b98fbb4f677ff95ac72aa05d85b`,2122096bytes. Installed base.apk readbacks match both. Evidence source stamp includes the base-dirty revision and both APK hashes, not a claim that the later publication commit was compiled.

Selected inventory passed one native case57.082s. Exact Qwen3 0.6B artifact SHA `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`,344671744bytes. Run `1a00c62ce1e441c5bff1eb3311ac6574`: explicit engine initialization3814ms, actualCPU/context4096. The first prepared final-text case EN-V01 returned **NO_ADVICE after9580ms**. Native events confirm thinkingOFF/tools0/maxTokens12 and Conversation creation11ms. Runtime residencyCOLD means first generation on this explicitly initialized engine; the router observed the exact engine resident before admission. No accepted label or fresh completed-generation diagnostics was returned.

The run stopped immediately with LATENCY_MISS, attempted1/48; partial negative qualification evidence saved, no pass or recommendation saved. Native JUnit one case/one expected qualification failure72.163s, host resultINTENT_NOT_QUALIFIED/process exit0/outer helper50693 exit1. This is a completed negative fixture outcome, not a native crash or host timeout. Sampled peakPSS1878858KiB/nativeheap1196566688bytes at200ms; these are samples, not exact peakRSS/energy. Normal residency retains native ownership; no manual unload. MainActivity resumed.

The single shorter-prompt observation does not establish paired performance, p95, semantic/raw-label conformity or multilingual accuracy. It still fails the unchanged2s requirement. Remaining47 inference cases, actual spoken/keyboard action loops, tool-markup UI/TTS/history, restart/removal, low-RAM/STT/TTS priority and full original acceptance remain unverified. Production advice remains gated on a passing exact workload. Do not repeat this unchanged configuration or close #36/#33/#43 on these results.

[Bounded raw evidence](artifacts/COMPACT-INTENT-036-2026-10-24) includes exact-run exports, desktop XML, terminal build log, package identities, native events and host result. Raw accumulated device journals, APK/model binaries and user content are excluded. No new interactive tool/schema/permission/side-effect operation was added or changed.
