# #33/#36 native cancellation ownership and thinking request

Base `09f09fe408775e9e25ac2442570c9f4cf3a41835`, fetched main0/0 and clean before edits. **Source correction verified; intent latency/quality qualification failed and issues remain open.** No prompt,2000ms target, fixture expectations, model selection, context/backend/privacy preferences or downloads changed. No actual speech/tool execution or recommendation saved.

## Final implementation

`LiteRtLocalProvider` preserves explicit reasoning DISABLED as native thinking=false even when artifact metadata does not advertise enabling thinking. DEFAULT remainsnull and enabling capability remains metadata-gated. Native event `thinking=OFF` proves configuration propagation, not independent inspection of hidden model reasoning or universal template compliance. Existing SDK ThinkingConfig regressions check false becomes disabled plus zero native thinking budget. See also the [SDK conversation control contract](https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/conversation/conversation.h); the packaged0.18.0 API and actual execution are the implementation authority.

`LocalModelSession` now retains a cancelled caller's ownership until its own worker/native cleanup finishes. Each queued item has a completion barrier. Cancellation removes a queued request without touching another owner; dispatched cancellation targets only the matching ActiveGeneration, forwards the existing native cancelGeneration API, then waits for worker finally. A per-generation cancellation mutex prevents a delayed stop from reaching the next generation. Busy reporting sees cancelling-but-incomplete provider jobs and actual Generating/Benchmarking activity.

`LiteRtRuntime` serializes Conversation cancellation and close with a lifetime mutex, taking stateMutex only for state snapshots. A late native Conversation allocation is published noncancellably before cancellation can propagate, so finally owns its close. No new engine, scheduler, persistent store or replacement teardown owner. Native compute remains owned through cleanup. The coroutine deadline stays cooperative; no hard JNI preemption guarantee is claimed.

## Review and desktop evidence

Production and regression sources preceded each focused gate. Read-only source review found no actionable findings in either phase.

- First gate35412: PASS7m8s,43 cases (28provider,11session,4SDK-thinking), bothAPKs. The first phone run exposed the additional missing native-stop handoff; its source patch, hashes, rawXML and failed physical result are retained under `phase1`.
- Final gate11578: PASS6m5s,17 affected cases (13session,4SDK-thinking), bothAPKs;488 tasks,13 executed/475 up-to-date.
- Final provider gate61774: PASS32s,28 cases against the changed underlying session; no APK assembly;31 tasks,one executed/30 up-to-date.

**45 distinct tests pass against final code**, zero failures/errors/skips. New tests cover noncancellable cleanup before ownership release/replacement, queued cancellation isolation, forwarding native stop to unblock cleanup, slow stop isolation from a newer request, provider busy/mutex retention, and explicit thinking-disable across generate/stream/advisory with unadvertised enable-thinking metadata. Five recorded final source hashes still match after all verification.

The provider-only Gradle filter replaced the earlier result directory. Its rawXML is preserved; final session/thinking XML summaries were directly observed before replacement and are explicitly labelled **derived observed summaries**, beside the terminal gate log. Phase1 rawXML is historical, not relabelled as final. No unchanged storage/embedding/speech/UI gates or CI/Actions were run.

## Physical results

Devicef01fe673 /23124RA7EO /API35, LiteRT0.18.0. Both phases use exact Qwen3 0.6B artifact SHA `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`,344671744 bytes, actualCPU/context4096. Installed package hashes were read back and matched; MainActivity resumed after each attempt.

First phone run `f36918a069e54b7599f40086a6326745`: selected inventory PASS53.653s; explicit warm initialization3742ms. Native configurationOFF/tools0/maxTokens12 confirmed. Seven EN calls returned NO_ADVICE at23832,22005,22012,22517,21919,23450,22798ms; eighth started before cooperative180s matrix timeout. JUnit one case/one failure237.995s; hostFAILED_RUNTIME/outerexit1. Peak sampledPSS1954163KiB/native1190937632bytes. This prompted native-stop forwarding before publication.

Final installed app SHA `128a2975e7ad7e562def475f3bcdb74c8a7c7ba612eaa5db98043de1ec0ab7d1`,288504360 bytes; test SHA `36a1ac36f4f9554e968a12ce700a9dcb11367a166117b5dbbed79bc4847b33db`,1963640 bytes. Selected inventory PASS59.176s. Run `1ad51f9a9c944429b50c5e9501f15741`: explicit warm initialization3813ms, native configurationOFF/tools0/maxTokens12. Seven EN calls returned NO_ADVICE at23872,22440,22923,22156,23799,21879,22602ms; eighth started, then the cooperative180s matrix deadline terminated the run. JUnit one case/one failure251.249s; hostFAILED_RUNTIME/outerexit1. This was a completed negative fixture result, not a host timeout or native crash.

**Native-stop forwarding did not make this prefill workload meet2000ms.** No completed labels, no semantic accuracy/label-conformity result, no RU/LV/PL inference acceptance and no completed48-case qualification. Do not infer performance improvement from the source fix. Final peak sampledPSS1957217KiB/native1192046576bytes at200ms, not exact RSS/GPU/energy. Native ownership remained with the existing residency owner; no fixture manual unload or teardown qualification.

[Evidence directory](artifacts/HALO-NATIVE-INTENT-OWNERSHIP-033-036-2026-10-24) contains both failed native runs, terminal gate logs, installed APK/source identities, filtered run footprints and bounded content-free native events. Device-wide raw journals and APK binaries are excluded.

## Outstanding acceptance

Keep #33/#36/#43 open for actual timely/accurate multilingual intent, native/agentic authorization and duplicate safety, real spoken/keyboard workflows, low-RAM/STT/TTS priority and full original acceptance. Prepared final strings are not actual STT or keyboard capture. The next admission correction must avoid optional unqualified native work blocking the user's final-turn path; repeating this unchanged matrix or relaxing its deadline would not establish suitability.
