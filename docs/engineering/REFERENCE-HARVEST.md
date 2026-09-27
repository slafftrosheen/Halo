> Status: WORKING LEDGER. Started 2026-09-27.
>
> Records ideas harvested from external reference projects (`build/refs/OneBox-main`, `build/refs/zafiro-main`,
> extracted from local archives, never committed and never depended on) and how each maps onto Halo.
> Nothing here is a capability claim: a row is only "WIRED" when Halo code implements it.
> Read present-tense rows about Halo against the actual code and [CURRENT-STATE.md](../../CURRENT-STATE.md).
>
> Rules for using this document:
> - Harvest the *idea*, never the source. No file, class, string or asset is copied.
> - Wire it through Halo's own contracts (`docs/engineering/ALPHA-REFERENCE-IMPLEMENTATIONS.md`):
>   one concept per owner, sealed results instead of booleans, Android only behind `host-android`,
>   repositories instead of direct storage access, flows instead of callbacks.
> - Deleting a reference idea is a valid outcome: record why.

# REFERENCE HARVEST

| Route | Reference | Idea | Halo state | Status |
| --- | --- | --- | --- | --- |
| LiteRT | OneBox `LiteRtLmRuntime` | GPU can pass `Engine.initialize()` and only fail at first inference (emulator / older GPUs); recover by rebuilding the engine on CPU once, and only when no token was emitted | `LiteRtRuntime` only fell back when `initialize()` itself failed | WIRED 2026-09-27 |
| LiteRT | OneBox `LiteRtLmRuntime` | Engine teardown (`close()`) releases weights for seconds and must not run in a cancelled coroutine | `close()` ran inline in the caller's context | WIRED 2026-09-27 |
| LiteRT | OneBox `LiteRtLmRuntime` | `cancelProcess()` may end a stream without raising: a cancelled turn must never be reported as completed | cancellation skipped tokens but still returned success | WIRED 2026-09-27 |
| LiteRT | OneBox `LiteRtLmRuntime` | Inference compile/weight caches must live in `filesDir`; the OS purges `cacheDir` (observed ~45 s after write on a flagship) | already `filesDir/litertlm_cache/<model>-<sha>/{gpu,cpu}` | CONFIRMED (no change) |
| LiteRT | OneBox `LocalModelDirectoryRegistry` | Reject path separators in a model id before touching the filesystem | catalog ids are validated; SAF imports are slug-sanitized | CONFIRMED (no change) |
| LiteRT | OneBox `LiteRtLmRuntime` | Post-process output for stop sequences (no native parameter) | Halo has no `stop` concept in `RuntimeGenerationConfig`; out of scope for the acquisition batch | OPEN |
| LiteRT | OneBox flavor split | Ship the ~21 MB native runtime only in flavors that can use it | Halo ships one APK; the ABI preflight covers the "device cannot run it" case | REJECTED |
| OpenRouter | OneBox `core/model/ai` | Provider spec tables with per-provider auth, protocol and capability declarations | Halo has `AiProviderRouter` + `AiModelCatalogRepository` + a failure-matrix test | TBD |
| OpenRouter | Zafiro `okia/OpenAIChatCompletionProtocol`, `RetryableErrorClassifier` | Protocol-level adapter plus an explicit retryable/non-retryable classifier | Halo has `Recoverability` / `retryAfterMs` normalization | TBD |
| Agentic skills | Zafiro `.agents/skills/<name>/SKILL.md` + `references/` | A skill is a bounded document with on-demand references (progressive disclosure), loaded by a tool | `SkillMarkdownPackageParser` (custom packages) + `halo_use_skill` with offset chunking already match the document model; built-in bodies still live in Kotlin | PARTIAL |
| Agentic skills | Zafiro `LoadSkillBuiltin` | Skill loading is a first-class tool call | `halo_use_skill` does this | CONFIRMED (no change) |
| Phone control | Zafiro `PruningRules` | Map widget class names to semantic types, infer `LIST_ITEM` from a list parent, collapse transparent non-interactive containers | `AccessibilitySnapshotFormatter.semanticType` is heuristic (substring on class name) and never collapses containers | OPEN |
| Phone control | Zafiro `TreeFormatter` | Summarize pruned off-screen children inside scrollables ("more: …") so a model can decide to scroll | off-screen nodes are counted as omissions only | OPEN |
| Phone control | Zafiro `TreeFormatter` | Encode position as screen thirds (`top-left`, `center`) instead of raw bounds | raw bounds are emitted per node | OPEN |
| Phone control | Zafiro `ScreenControlConsent` | Explicit informed consent before screen control; deny while backgrounded | Halo gates per action (`ASK_EVERY_TIME`) and blocks sensitive packages | CONFIRMED (Halo stricter) |

## Harvested this round (LiteRT route)

Three OneBox observations became code in `providers/litert`:

1. **Late GPU failure recovery.** `LiteRtRuntime.generate()` now retries once on the CPU when, and
   only when, all of the following hold: the request asked for `AUTO`, the resident engine is on the
   GPU, no token was emitted yet, the user did not cancel, and the residency has not already used its
   single CPU rebuild. The rebuild runs under `loadMutex` and outside `inferenceMutex`, which is the
   same lock order `loadModel` uses, so it cannot invert. If the rebuild cannot be installed, the
   failure is reported as a `LiteRtModelLoadException` carrying the original GPU cause.
2. **Teardown safety.** Engine close now happens in `withContext(NonCancellable + Dispatchers.Default)`
   because releasing weights takes seconds and a cancelled caller could otherwise abort it.
3. **Honest cancellation.** `cancelRequested` is re-checked after the stream ends; a cancelled turn
   returns a failure instead of a completed response with partial text.

To make a late fallback observable, the runtime reports its accelerator through the new optional
`LocalRuntimeBackendReporting` SDK capability, and `LocalModelSession` re-reads it after every
generation, so provider status and the model browser cannot keep claiming "GPU" after a CPU rebuild.

## Next candidates (not started)

- Phone control: transparent-container collapse, parent-aware semantic types, `more:` summaries inside
  scrollables, and thirds-based position encoding. Target: a smaller, better screen for a local 0.6B
  model, without changing Halo's token/consent/redaction model.
- Agentic skills: move built-in instruction bodies out of Kotlin into the existing manifest document
  channel, resolve the duplicated `halo.phone` declaration (Kotlin says `SENSITIVE`, the asset says
  `EXTERNAL_SIDE_EFFECT`, and the asset silently wins), and delete the unread `assets/skills/*.yaml`.
- OpenRouter: compare provider spec/retry classification against Halo's current normalization before
  deciding whether anything is missing at all.
