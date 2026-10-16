# HALO-AI-133 owner and alpha acceptance matrix

**IMPLEMENTED — NOT BUILT — NOT TESTED — PHYSICAL ACCEPTANCE PENDING**

All results below start as **NOT_RUN**. This document specifies future manual work; Codex executed none of it. Obtain a separately authorized build through the project owner's release process. Existing AI-132 observations do not qualify AI-133. Do not run paid provider generation without the owner's explicit scope.

## Evidence header for every session

Record source SHA, app version, compatibility epoch (2), Android version, device/RAM tier, runtime versions, exact model ID/SHA/pack version/voice, requested and observed backend, context/output/sampler/thread settings, memory/concurrency/residency policy, route, language, thermal state, time, operator and result. Keep content-bearing private evidence locally; safe exports deliberately exclude transcripts, queries, source IDs, vectors and generated previews. Never upload credentials or private documents.

Use PASS / FAIL / BLOCKED / NOT_RUN / UNSUPPORTED. A load, marker response, finite vector or nonempty PCM is not task qualification. Record expected and observed behavior separately. Where instrumentation is absent, record NOT_MEASURED, including true audibility, GPU operation placement and GPU memory.

## Device and language coverage

Repeat applicable rows on physical 4 GiB, 6 GiB, 8 GiB and higher-RAM devices. Record known available RAM; do not infer headroom from total RAM alone. On 4/6 GiB expect conservative serialization and no speculative initialization; on 8+ GiB speculation still requires explicit opt-in, current task evidence, known headroom and acceptable pressure/thermal state.

Repeat supported speech rows for EN/LV/RU and PL only where the exact installed artifact/provider declares support. Label unsupported languages UNSUPPORTED rather than manufacturing a voice. Exercise phone speaker, connected wired endpoint, Bluetooth headset and exact supported wearable/glasses endpoint. Record missing hardware BLOCKED. English-only Kokoro must reject unsupported requests or use explicitly configured fallback.

| Area / explicit action | Expected behavior | Observed / evidence | Result |
|---|---|---|---|
| Enter AI Lab, Model roles and speech settings; select diagnostic model | Cached status only; no native init, probe, download or generation | — | NOT_RUN |
| Verify installed artifact | Actual current hash; modified/missing artifact rejected | — | NOT_RUN |
| Inspect native metadata | Separate task/backend metadata; unsupported export explained; no inferred model-name capabilities | — | NOT_RUN |
| Cold initialization; then load/reuse | Separate fresh load and load/reuse latency; observed CPU; no synthetic throughput | — | NOT_RUN |
| Short text diagnostic / inspect preview | Output visible locally; fixture match is not task proof; closing clears preview | — | NOT_RUN |
| Existing role suite / multi-turn | Instruction, arithmetic, recall, warm stability and applicable reasoning/tool checks; no marker-only qualification | — | NOT_RUN |
| Change model SHA/runtime/epoch/Android/context/sampler/backend/memory policy | Prior records retained historically and excluded from current adaptive decisions | — | NOT_RUN |
| Explicit role target; then AUTOMATIC with adaptive opt-in off/on | Explicit target preserved; no evidence means conservative legacy policy; only matching correct/stable proof may influence automatic roles | — | NOT_RUN |
| Manual GPU request / CPU comparison | Unsupported combination rejected honestly; effective backend separate from requested; no claim of actual GPU operation placement | — | NOT_RUN |
| Install two Kokoro versions; interrupt/retry import; low storage | Old selected pack retained; complete verified installations independent; no automatic selection or old-file deletion | — | NOT_RUN |
| Select/remove voice while playback active | Stream pins identity; active pack cannot be removed; next response uses explicit new selection | — | NOT_RUN |
| Voice/rate/language/cache change | Correct identity/cache invalidation; no stale voice; non-silent PCM plus separate human audible quality | — | NOT_RUN |
| Warm Kokoro; idle expiration; memory pressure | Reuse only matching engine; eject idle; never release JNI during active call | — | NOT_RUN |
| INT8 / Supertonic / arbitrary ONNX | Unqualified INT8 blocked; Supertonic unavailable until host supplies qualified exact pack; arbitrary ONNX not accepted as a compatible voice | — | NOT_RUN |
| Speech test in existing Speech output surface | Init/reuse/cache/first PCM/RTF distinct; routed write is not reported as audibility | — | NOT_RUN |
| STT measurement in existing speech diagnostics | Genuine recognition; initialization/audio/decode/RTF/language where available; unavailable VAD detail NOT_MEASURED | — | NOT_RUN |
| Full voice request, long response and final reconciliation | Only stable final text spoken once; no reasoning/tool payloads or repeated prefix | — | NOT_RUN |
| Barge-in / Stop / new utterance / route loss during synthesis and playback | Capture/playback owners cancel children; no late audio; exact route proof before unmute | — | NOT_RUN |
| Partial STT revisions / ambiguity / direct command | Advisory partials only; final command through existing authority/confirmation; no speculative side effect | — | NOT_RUN |
| Background / lock / process death / restart | No resumed speculative jobs, duplicated speech or replayed tool effects; existing journals authoritative | — | NOT_RUN |
| Low-memory / severe thermal / resource contention | No speculative load; bounded queue/deadline; no competing heavy engine; truthful failure/skip | — | NOT_RUN |
| Context and selected-model preparation overlap | Independent stages may overlap only when admitted; existing native lease arbitration remains exclusive where required | — | NOT_RUN |
| Diagnostic embedding on exact compatible export | Native normalized finite vector of actual dimension; reject mismatch; no generated-text substitute | — | NOT_RUN |
| Opt in selected note/canvas/study/plain-text attachment IDs; index | Bounded local derived index; source documents unchanged; unsupported extraction absent | — | NOT_RUN |
| Query retrieval / inspect provenance | Relevant excerpt/source/version; semantic correctness judged separately from finite vectors; no action authority | — | NOT_RUN |
| Edit/delete source during/after index/query; disable scope | Old chunks invalidated; post-inference permission/version recheck; disabled index cleared | — | NOT_RUN |
| Interrupted index / changed embedding SHA/dimension/runtime | Atomic publication; incompatible space rejected/rebuilt by explicit action | — | NOT_RUN |
| Retrieval while voice/heavy LiteRT active | Busy/conflict reported; no extra resident heavy engine, no silent remote fallback | — | NOT_RUN |
| Copy safe workload report / compare local preview | No private text/query/excerpt/vector/source ID/credential/exception message; unavailable metrics explicit | — | NOT_RUN |
| Tool/vision diagnostics in existing surfaces | Exact protocol/image capability qualification; approvals and provenance retained; no image pass inferred from text | — | NOT_RUN |
| EN/LV/RU accessibility/layout | Localized labels, readable scaling, touch targets, navigation and DevKit styling on actual device | — | NOT_RUN |

## Limits to keep visible during acceptance

The Supertonic provider requires a host-supplied qualified pack; there is no end-user trusted-pack acquisition/qualification workflow yet. INT8 cannot be promoted through a fabricated success switch. Workload execution presets currently drive conversational LiteRT requests; specialized STT/TTS owners retain their existing provider-specific controls. Optional learned intent classification is an interface, with deterministic intent remaining active.

The semantic service exposes provenance-bearing local-only context segments and an explicit index/retrieval UI. Automatic insertion into every conversational context is not wired: the manual retrieval preview is the current end-to-end surface. Do not claim full automatic Notebook/Study semantic augmentation. This also avoids silently carrying newly retrieved private text into existing remote fallback routes.

New persisted qualification production covers existing LLM role-suite evidence. Speech audibility, STT accuracy, vision semantics, embedding semantic quality and tool side-effect acceptance require their existing specialized owners/manual evidence; no generic diagnostic action stamps them qualified. Warm and cold suite timing is controlled fixture performance, not a prediction of arbitrary user prompt latency.

End-of-speech-to-audible latency, per-token throughput without real token accounting, queue/chunk acoustic timing, GPU allocation/operation placement, aggregated fallback frequency and all-workload calibration remain NOT_MEASURED or source-gated. Last-operation TTS diagnostics are in-memory and are not automatically persisted as task proof. No release, build, source validation or physical acceptance has been established.
