> Status: WORKING LEDGER. Started 2026-09-27. Last verified against source 2026-09-28.
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
> - Every status is checked against source, not against another status document.

# REFERENCE HARVEST

## Next target: Notebook and Study

The 2026-09-28 [Notebook/Study mining ledger](NOTEBOOK-STUDY-MINING.md) records inspected
editor, attachment and session patterns, current Halo gaps and explicit adopt/reject decisions.
[RFC 0002](../rfc/0002-notebook-editor-and-study.md) schedules NBS-01–07. These are PLANNED,
not WIRED. Notebook's existing block/assets/workspace paths must be reconciled before extending;
the current Study tool's success message is not evidence of a persisted learning session.

## Runtime harvest

| Route | Reference | Idea | Halo state | Status |
| --- | --- | --- | --- | --- |
| LiteRT | OneBox `LiteRtLmRuntime` | GPU can pass `Engine.initialize()` and only fail at first inference (emulator / older GPUs); recover by rebuilding the engine on CPU once, and only when no token was emitted | `LiteRtRuntime.generate()` retries once on CPU under the documented conditions | WIRED 2026-09-27 |
| LiteRT | OneBox `LiteRtLmRuntime` | Engine teardown (`close()`) releases weights for seconds and must not run in a cancelled coroutine | `close()` runs in `NonCancellable + Dispatchers.Default` | WIRED 2026-09-27 |
| LiteRT | OneBox `LiteRtLmRuntime` | `cancelProcess()` may end a stream without raising: a cancelled turn must never be reported as completed | cancel flag re-checked after the stream; cancelled turn returns a failure | WIRED 2026-09-27 |
| LiteRT | OneBox `LiteRtLmRuntime` | Inference compile/weight caches must live in `filesDir`; the OS purges `cacheDir` (observed ~45 s after write on a flagship) | already `filesDir/litertlm_cache/<model>-<sha>/{gpu,cpu}` | CONFIRMED (no change) |
| LiteRT | OneBox `LocalModelDirectoryRegistry` | Reject path separators in a model id before touching the filesystem | catalog ids are validated; SAF imports are slug-sanitized | CONFIRMED (no change) |
| LiteRT | OneBox `LiteRtLmRuntime` | Release heavyweight native resources on OS memory pressure, not only on a background timer; must be idempotent and safe at any moment | `NativeResourceCoordinator.updateBudget` calls `releaseIdle("memory_pressure")` from `Application.onTrimMemory`/`onLowMemory`, and `LiteRtLocalProvider` registers with that coordinator | CONFIRMED (no change) |
| LiteRT | OneBox `LiteRtLmRuntime` | A new turn should preempt an in-flight generation of a different session instead of queueing behind it | not verified in Halo's `LocalModelSession` | TBD |
| LiteRT | OneBox `LiteRtLmRuntime` | Post-process output for stop sequences (no native parameter): accumulate, find the first stop occurrence, emit only the not-yet-sent prefix, cancel, and treat it as a normal completion | Halo has no `stop` concept in `RuntimeGenerationConfig` | OPEN → RUNTIME-047 |
| LiteRT | OneBox flavor split | Ship the ~21 MB native runtime only in flavors that can use it | Halo ships one APK; the ABI preflight covers the "device cannot run it" case | REJECTED |
| OpenRouter | Zafiro `LLMErrorCode` | Context overflow is its own non-retryable class, distinct from a generic malformed request, because it is the one `400` the user can act on | `AiErrorType.CONTEXT_TOO_LARGE` had consumers but **no producer**; OpenRouter 400s were all `INVALID_REQUEST` | WIRED 2026-09-28 |
| OpenRouter | Zafiro `RetryPolicy` | Backoff must cap its growth and guard the exponent shift: `1 shl n` overflows negative at `n >= 31` | `DefaultRetryPolicy.nextDelay` was uncapped with a bare `1 shl (attempt - 1)` | WIRED 2026-09-28 |
| OpenRouter | Zafiro `RetryPolicy` | Multiplicative jitter (±10%) on retry delays to spread a retry herd | single-client on-device runtime with no herd; provider `Retry-After` is honoured upstream | REJECTED |
| OpenRouter | Zafiro `LLMError.LLMErrorCode` | Retry decisions keyed on stable codes, never on human-readable text | `DefaultRetryPolicy.maxAttempts` still classifies by `reason.contains("network")`-style text | PARTIAL → RUNTIME-044b |
| OpenRouter | OneBox `AiProvider` / `AiExecutionLocation` | Execution location is a distinct axis from provider brand; UI and stats branch on location, not on a hardcoded provider name | `AiModelCatalogRepository` maps provider id to `AiExecutionLocation` in exactly one place and everything downstream branches on the location | CONFIRMED (no change) |
| OpenRouter | OneBox `AiRequestProtocol` | Wire protocol modelled separately from brand, with non-chat protocols kept out of chat routing | Halo's `LocalInferenceRuntime` is a separate SDK capability from `AiInferenceProvider`, and local selection never builds a cloud request | CONFIRMED (no change) |
| OpenRouter | OneBox `AuthType.defaultFor(protocol)` | Auth strategy defaults from the protocol with an explicit per-provider override | not inspected | TBD |
| OpenRouter | OneBox `LlmStreamEvent` | One protocol-independent streaming event set that adapters map into | Halo has its own `AiStreamEvent` contract per provider | CONFIRMED (no change) |
| Agentic skills | Zafiro `.agents/skills/<name>/SKILL.md` + `references/` | A skill is a bounded document with on-demand references (progressive disclosure), loaded by a tool | `SkillMarkdownPackageParser` (custom packages) + `halo_use_skill` with offset chunking already match the document model; built-in bodies still live in Kotlin | PARTIAL → RUNTIME-045b |
| Agentic skills | Zafiro `LoadSkillBuiltin` | Skill loading is a first-class tool call | `halo_use_skill` does this | CONFIRMED (no change) |
| Agentic skills | OneBox skill frontmatter + usage policy | Schema validation, size ceiling and content provenance for a shipped manifest | `SkillManifestPolicy` enforces schema/size/id rules; the loader records SHA-256 provenance | CONFIRMED (no change) |
| Agentic skills | Zafiro `ScreenControlConsent` / consent coordinators | Consent must be informed, and dismissals must not become approvals | Halo gates per action and reuses the durable `AgentInputRequest` instead of a second approval machine | CONFIRMED (Halo stricter) |
| Agentic skills | (Halo defect found while mining) | Two declarations of one built-in skill must not resolve to the weaker classification | 10 of 11 built-ins were declared twice; `associateBy` let the asset silently override the registry. `halo.phone` shipped `EXTERNAL_SIDE_EFFECT` where the registry said `SENSITIVE` | WIRED 2026-09-28 |
| Agentic skills | (Halo defect found while mining) | Unread duplicate manifests are dead weight that mislead readers about the authoritative schema | four `assets/skills/*.yaml` files were never read (the loader filters `.json`) and duplicated four JSON manifests with different enum casing | WIRED 2026-09-28 (deleted) |
| Agentic skills | Zafiro `phone-use/SKILL.md` | Behavioral guidance, not capability: stop and report on a non-native surface instead of retrying; define a scroll's terminal signal before starting; treat on-screen text as data; avoid dock components that steal the swipe | Halo's phone guidance covers read-before-action, untrusted content and no blind replay, but not these | OPEN → RUNTIME-045b |
| Phone control | Zafiro `PruningRules` | Map widget class names to semantic types through an exact table plus a bounded suffix table, and infer `LIST_ITEM` from a `LIST` parent | `AccessibilityProjectionRules.classify` resolves exact role, then a bounded suffix table ordered specific-first, then platform flags, then a `LIST` parent; `TAB`/`CHIP`/`TOOLBAR`/`DIALOG` are expressible roles. No substring matching remains | WIRED 2026-09-28 |
| Phone control | Zafiro `PruningRules` | Collapse inert containers (non-interactive, no text, no description) while keeping their children | inert containers are dropped from the projection (counted as `collapsed_container`) and their children are still emitted; selected or focused nodes are never collapsed | WIRED 2026-09-28 |
| Phone control | Zafiro `TreeFormatter` | Summarize pruned off-screen children inside scrollables ("more: …") so a model can decide to scroll | a scrollable carries `hiddenChildCount` and up to three redacted previews of the rows it pruned; the projection is transparent, so an unlabeled off-screen wrapper passes its label up instead of swallowing it | WIRED 2026-09-28 |
| Phone control | Zafiro `TreeFormatter` | Encode position as screen thirds (`top-left`, `center`) instead of raw bounds | every node carries `region`; model-visible text prints the region and keeps bounds only as a fallback. Bounds stay on the contract because no action ever used coordinates | WIRED 2026-09-28 |
| Phone control | Zafiro `TreeFormatter` | Detect a non-inspectable surface (empty or root-only tree, e.g. Flutter/Unity/WebView) and report it distinctly instead of returning a near-empty snapshot | `ScreenContextSnapshot.inspectability` separates `INSPECTABLE` / `EMPTY_OR_ROOT_ONLY` / `NO_SEMANTIC_CONTENT`, and `halo_read_screen` answers `status=unreadable_surface next=REPORT_TO_USER` with a do-not-retry advisory | WIRED 2026-09-28 |
| Phone control | Zafiro `TreeFormatter` / consent coordinators | One failure message for several different truths makes a model retry the wrong thing | a nullable snapshot collapsed "nothing to observe" and "Halo refuses to observe this app" into one string; sealed `ScreenCaptureOutcome` separates `NoActiveWindow` (TRANSIENT) from `PackageBlocked` (PERMISSION, with reason) and the disabled service reports `ACCESSIBILITY_DISABLED` | WIRED 2026-09-28 |
| Phone control | Zafiro `SemanticToken(version, index)` | A token is a regenerated handle, not a durable identity | Halo's tokens stay a digest of the node, its true tree path and its **intrinsic** role; parent-derived roles such as `LIST_ITEM` are display-only, so a token cannot change because a list gained or lost a wrapper | CONFIRMED (deliberately stricter) |
| Phone control | Zafiro `phone-use/SKILL.md` | Touch events are consumed by the topmost component under the finger, so a docked bar steals a swipe that starts beneath it | Halo's scroll targets a semantic container, so this is annotation-only for now | OPEN (deferred) |
| Phone control | Zafiro `screen_operation_accessibility(operation:"search")` | Keyword search over the bounded tree to narrow candidate tokens | `halo_find_on_screen` already provides snapshot-scoped semantic search | CONFIRMED (no change) |
| Phone control | Zafiro `ScreenControlConsent` | Explicit informed consent before screen control; deny while backgrounded | Halo gates per action (`ASK_EVERY_TIME`) and blocks sensitive packages | CONFIRMED (Halo stricter) |

## Resolution of the RUNTIME-038…043 "delivered vs open" discrepancy

`ONEBOX-ZAFIRO-MINING.md` claimed RUNTIME-038 through RUNTIME-043 were delivered while this ledger
still listed the same work as OPEN or PARTIAL. The source was checked directly and **the mining doc
was right**; the stale rows were in this ledger. Verified in source:

- RUNTIME-038 — `ToolRegistry` applies `ToolResultBudgeter` to the descriptor budget, and
  `ToolTurnExecutor` persists the executor's actual `ActionTruth` instead of `AUTHORIZED`.
- RUNTIME-039 — `HaloAccessibilityService` advances a host-owned UI revision that post-action
  settling observes.
- RUNTIME-041 — `ConversationContextPolicy` exists and is resolved by `DefaultContextAssembler`.
- RUNTIME-042 — `InferenceLedger` in `runtime/observer` and `InferenceObserved` in the runtime
  event contract.
- RUNTIME-043 — `AndroidAssistantRoleController` and `AssistantEligibilityInspector` use the
  platform `RoleManager` and active-service truth.

Process fix: status rows in this ledger are now checked against source before a pass is described as
open, and a plan document is never used as evidence that its own plan was implemented.

## Harvested this round — RUNTIME-044 and RUNTIME-045

**Provider failure truth (`RUNTIME-044`).** Two Zafiro error-model ideas became code:

1. **Context overflow is now a real class.** `AiErrorType.CONTEXT_TOO_LARGE` had a presenter string
   and a router rejection but no producer, so every OpenAI-compatible `400` — including "maximum
   context length exceeded" — became a generic malformed request. The new provider-neutral
   `ContextOverflow` owner decides that question once from an authoritative provider code or a
   deliberately narrow prose fallback, and `OpenRouterProvider` maps that `400` to
   `CONTEXT_TOO_LARGE` with `Recoverability.PERMANENT`, which now reaches the existing
   presenter/router paths. Retrying is still wrong (the same request always fails), but the failure
   is no longer indistinguishable from a bug.
2. **Backoff growth is bounded.** `DefaultRetryPolicy.nextDelay` doubled without limit and applied
   `1 shl (attempt - 1)` directly, which overflows into a negative `Int` at `attempt >= 32` and
   would have produced a negative delay. Growth is now capped and the exponent bounded, preserving
   the existing 100/200/400/800/1600 ms sequence.

**Skill declaration ownership (`RUNTIME-045`).** A real shipping defect: ten of eleven built-in
skills were declared both in the Runtime registry and as a shipped asset manifest, and the loader
resolved the duplicate map last-wins, so the asset always won. For `halo.phone` the asset declared
`EXTERNAL_SIDE_EFFECT` while the registry declared `SENSITIVE`, meaning Halo silently shipped the
*weaker* classification of its highest-risk capability. `halo.reminders` showed the inverse, so the
fix cannot simply prefer one document: `BuiltInSkillMerge` keeps the strictest declared risk at skill
and per-tool level and unions tools and permissions, so neither document can weaken or silently drop
the other's requirements. The `halo.phone` manifest was also corrected to declare `SENSITIVE`
truthfully, and the four unread `assets/skills/*.yaml` files were deleted.

## Harvested this round — RUNTIME-046

Phone observation was the weak link on the phone route: the projection was one loose substring
chain over widget class names that never consulted the parent, it spent budget on nodes that carried
nothing, and it said nothing about what a scrollable had hidden. What changed:

- **Roles are a table, not a guess.** `AccessibilityProjectionRules` resolves an exact class name
  first, then a *bounded* suffix table ordered so the specific match wins (`ToggleButton` before
  `Button`, `AutoCompleteTextView` before `TextView`), then falls back to platform flags, then asks
  whether the parent is a `LIST`. `TAB`, `CHIP`, `TOOLBAR` and `DIALOG` became expressible roles. A
  name that merely contains a role word is no longer classified as it, and list rows are no longer
  anonymous containers.
- **Nothing is emitted that teaches a model nothing.** Inert containers are collapsed and their
  children kept, so a wrapper costs no node and no line.
- **A scrollable says what it hid.** The rows a scrollable had to prune are counted and previewed on
  the scrollable node itself (bounded, redacted), so a model can choose to scroll instead of
  assuming the list ended. The pruner is transparent: an off-screen unlabeled wrapper promotes the
  labelled row inside it rather than consuming the item.
- **Position is a third, not a rectangle.** `region=top-left` replaced `bounds=[...]` in model-visible
  text; bounds remain on the contract as evidence, and nothing in the action path ever used them.
- **An unreadable screen says so.** `inspectability` distinguishes content from an empty or root-only
  tree and from a surface that exposes nothing labelled or interactive, and `halo_read_screen`
  answers with `status=unreadable_surface next=REPORT_TO_USER` plus a do-not-retry advisory.
- **Capture failure is not one message.** Sealed `ScreenCaptureOutcome` carries the difference
  between nothing to observe, an app Halo refuses to observe (with the reason) and a captured screen.

What did **not** change: node/depth/character budgets, redaction, untrusted-content framing, opaque
snapshot-scoped tokens, settled snapshots, receipts and the per-action consent model. The only
contract edits are additive fields on the existing screen types, and the token digest deliberately
excludes parent-derived roles so a token cannot be invalidated by a wrapper appearing above it.

Also recorded: the pure rules object gained JVM tests that **have never been executed** (this pass ran
neither builds nor tests). They describe intent until run once.

## Next candidates (not started)

- `RUNTIME-044b`: `DefaultRetryPolicy.maxAttempts` classifies retries by substring-matching
  human-readable reason text, which contradicts RUNTIME-038's own rule that retry decisions use
  structured failure kinds. Needs a typed kind on `Outcome.RetryableFailure` and migration of the
  producers, not a rewrite of the lookup alone.
- `RUNTIME-045b`: move built-in instruction bodies out of the Kotlin map into the existing manifest
  document channel so a manifest-declared skill can carry its own bounded guidance, then fold in the
  Zafiro `phone-use` behavioral rules. Requires a bounded `instructions` field and a contract change
  across `SkillDescriptor` → registry → instruction source.
- `RUNTIME-047`: bounded stop-sequence support for local generation.
- Ollama still classifies failures coarsely without reading HTTP status, so it cannot use the new
  `ContextOverflow` owner yet.

## Rejected, and why

- **Zafiro root/Shizuku/Xposed shell control** — optional privileged adapter only; the default phone
  path stays accessibility-based.
- **Zafiro Python/terminal tools** — no sandbox and no product requirement; a hidden file path would
  create a second data-access route.
- **Zafiro fuzzy `launch_app`** — `halo_open_app` requiring an exact validated package id is safer;
  only discovery is improvable.
- **OneBox durable tool-call task tables** — Halo's append-only AgentStep journal stays execution
  truth.
- **OneBox A2UI renderer registry as execution truth** — interaction surfaces remain bounded,
  read-only supplements to Halo's closed semantic action protocol.
- **Zafiro full-output file export for large tool results** — would create a second data-access
  route; Halo bounds the payload instead.
- **Zafiro retry jitter** — no retry herd on a single-client device, and `Retry-After` is already
  honoured upstream.
- **Coordinate tapping, long-press and gestures** — remain unexposed until risk semantics and a
  concrete product need exist.

## Harvested this round — RUNTIME-055 (remote provider fabric / free-tier presets / OAuth + companion)

**Provider identity ≠ wire protocol ≠ auth method.** The batch adds the shared
OpenAI-compatible transport and descriptor/preset types so Groq, Cerebras, xAI,
OpenCode Zen and local/custom OpenAI-compatible endpoints stop duplicating HTTP,
SSE, tool-call parsing and error mapping. Gemini keeps official Google OAuth,
OpenCode companion is a user-operated inference host, and secret migration expands
the keystore-protected credential set. This is a source-only pass: no build/test
was run.

| Route | Reference | Idea | Halo state | Status |
| --- | --- | --- | --- | --- |
| Generic remote inference | OneBox provider/protocol/auth split | Provider identity, request protocol and auth type are separate concepts; data-driven engine config drives multiple providers from one transport | New `OpenAiCompatibleTransport` + `ConfiguredOpenAiCompatibleProvider` + `RemoteProviderDescriptor` extracted in `sdk/provider-sdk`; existing OpenAI/OpenRouter/Gemini/Anthropic providers are reference points | WIRED 2026-09-29 |
| Generic remote inference | FerSaiyan OpenAI-compatible client + relay boundary | Generic OpenAI-compatible client is useful; CLI/relay provider and mobile ephemeral credential patterns are separate boundaries | Transport reuse adopted; credential transport is HTTPS-or-explicitly-trusted-local, public cleartext key transport returns `INSECURE_CREDENTIAL_TRANSPORT` | WIRED 2026-09-29 |
| Ephemeral mobile auth | FerSaiyan Gemini Live credential pattern | Long-lived credential stays outside the mobile boundary; mobile receives only bounded/ephemeral authorization | Adopted as a design constraint; no Halo-hosted relay added in this batch | CONFIRMED constraint (no Relay added) |
| OpenAI-compatible error/rate-limit model | OneBox/Zafiro error classification + retry-after | HTTP mapping, 429 handling and quota snapshot belong in the shared transport, not per vendor | `ProviderQuotaSnapshot`, `AiProviderException` 429 handling and `INSECURE_CREDENTIAL_TRANSPORT` wired into the transport; existing OpenAI/OpenRouter error mapping preserved as compatibility reference | WIRED 2026-09-29 |
| Free-tier metadata | OneBox `AiExecutionLocation` / `AiCostClass` | Cost class and execution location are distinct from provider brand; free is per-model metadata, not provider-wide marketing | `ProviderAccessKind` added alongside existing `AiCostClass`/`AiModel.isFree`; free is REMOTE_LIVE evidence, not baked permanent truth | WIRED 2026-09-29 |
| Remote free/cost policy | OneBox routing policy | Free-tier preference is an optional policy applied after privacy/capability/role constraints; it must never silently bill a paid model or bypass LOCAL_ONLY | `RemoteCostPreference` added; `LOCAL_ONLY` remote guard enforced before any remote consideration | WIRED 2026-09-29 |
| OAuth lifecycle | Google AuthorizationClient pattern in existing Gemini UI | Real OAuth session with silent reissue, single-flight token expiry + one retry, and explicit Google-owned debug/release client config | `GeminiOAuthCoordinator` keeps the existing in-flight OAuth path honest; in-memory-only token behavior is replaced with reusable-but-ephemeral session semantics; CLI credential import stays unsupported | WIRED 2026-09-29 |
| OAuth boundary | Gemini CLI prohibition | Do not implement Gemini CLI OAuth piggyback, token harvesting, or CLI credential import | Hard comment + unsupported path left in auth routing; no CLI credential import added | WIRED 2026-09-29 |
| Device-code OAuth | xAI device auth semantics in supported tooling | RFC-8628 device flow abstraction (request code, verify URL, poll, authorization_pending/slow_down/access_denied/expired_token, single-flight refresh, no token logging) | `XaiSubscriptionAuthCoordinator` skeleton added; enabled only when Halo has a documented/public or Halo-registered xAI client; otherwise surfaces "Experimental / client registration required" | WIRED (placeholder) 2026-09-29 |
| User-owned LAN remote inference | OpenCode headless/companion pattern | A user-operated OpenCode instance can own its own provider credentials; Halo stores only endpoint + local auth and performs inference only | `OpenCodeCompanionProvider` with connection config, health, provider/model discovery and bounded inference session; companion shell/fs/MCP/browser/phone tool execution disabled for normal Halo turns | WIRED 2026-09-29 |
| Custom OpenAI-compatible provider | FerSaiyan/OneBox generic remote endpoint idea | vLLM/LM Studio/llama.cpp/private gateways should not require another Halo provider module | `custom.openaicompatible` preset + settings UI + discovery toggle added | WIRED 2026-09-29 |
| Secret protection | Existing `AndroidKeystoreSecretStore` / `SecretBackedStorage` / `SecretMigration` | All provider API keys and OAuth/companion secrets belong in the keystore-backed secret store; BrainSnapshot must not export secrets and must restore providers as NEEDS_AUTH | `SecretMigrationV2` expands the protected key set, is idempotent/crash-safe/backward-aware and removes plaintext; `BrainSnapshotSecretsGuard` blocks secret export and sets restored remote providers to NEEDS_AUTH | WIRED 2026-09-29 |
| Composable provider composition | Existing `AndroidProviderFactory` / `ProviderGraph` / `Providers` | New presets/companion must wire into the existing factory and provider registry without breaking existing flow | `ProviderGraph` and `AndroidProviderFactory` extended with remote fabric presets and companion; existing providers retained | WIRED 2026-09-29 |
| Provider auth state UI contract | Existing `IntelligenceScreen`/`SystemCheckScreen` | Connecting a provider must not auto-select it; separate "Use as Default / Assign to role" actions preserve RUNTIME-054 semantics | `ProviderAuthState`, `ProviderAuthInfo`, `AiProviderUiState` extensions and `RemoteProviderPresence` added; `IntelligenceScreen`/`SystemCheckScreen` extended for remote auth state, connect/test/refresh, badges and source chips | WIRED 2026-09-29 |
| Model catalog normalization | OneBox provider/model identity stability | Remote models normalized to `AiModelRef(providerId, modelId)` with REMOTE_LIVE/REMOTE_CACHED and TTL; companion underlying models must not collide | `AiModelCatalogRepository` extended with remote/cached normalization and `ModelCatalogCache`; companion models encoded with underlying provider prefix | WIRED 2026-09-29 |
| Provider diagnostic probe | Existing System Check AI probe | Diagnostic probes must be bounded, short, content-free and not send history | `ProviderDiagnosticProbe` added with bounded prompt / max-output guard and no conversation history; System Check exposes per-provider rows and "Run provider probe" | WIRED 2026-09-29 |

### RUNTIME-057 / MINING-P0-001 — execution truth, tool qualification, resource and audio-route observability (2026-09-30)

Sources: `https://github.com/Rickeal-Boss/Android-Agent` branch `harness-improve`
(`core-engine/.../local/NativeToolBridge.kt` plus its native-tools handoff doc),
`https://github.com/ChaseDreamInfinity/voice-agent` (`AudioCaptureEngine.kt`,
`VoiceAudioRouting.kt`, `LlmEngine.kt`), and the minimal ADK+LiteRT sample
`https://github.com/MukeshKumar009/android-agent-adk-litertlm-on-device-ai` (conceptual only; no root
LICENSE in the inspected tree). Status for this batch is **SOURCE COMPLETE / BUILD NOT RUN / TESTS NOT
RUN / PHONE ACCEPTANCE NOT RUN**.

| Idea | Source | What Halo needed | What was wired | Status |
| --- | --- | --- | --- | --- |
| Detect the layer that failed, not "LiteRT is broken" | Android-Agent diagnostics ladder; minimal ADK/LiteRT control path | A probe below the provider and below the router | `LiteRtControlProbeRunner` + `LiteRtControlProbeResult`/`Stage`/`Status`: artifact → ABI → engine init → conversation → first token → completion → teardown, stages independently reported | SOURCE COMPLETE 2026-09-30 |
| One owner for engine configuration | Minimal ADK/LiteRT sample (how small a direct path can be) | The probe and the runtime must not drift | `LiteRtEngineConfigFactory` extracted (backend mapping, `EngineConfig`, content-addressed cache paths, context ladder, ABI preflight, memory-failure classification) and consumed by `LiteRtRuntime` and the probe | SOURCE COMPLETE 2026-09-30 |
| `automaticToolCalling = false` and fail-loud `execute()` | Android-Agent `NativeToolBridge` | Preserve, make explicit | Unchanged behaviour; explicit `SECURITY INVARIANT` comment at the `ConversationConfig` site; `HaloOpenApiTool.execute()` still throws | PRESERVED 2026-09-30 |
| Native tool support must be observed, not declared | Android-Agent capability/probe truth | Stop trusting a catalog boolean | `LiteRtToolQualification` states; `NATIVE` only for a proven `<sha256>:litertlm-<version>:<schema>` key; declared-native-but-unproven falls back to `HALO_PROMPT` | SOURCE COMPLETE 2026-09-30 |
| Tool call/result history pairing and malformed-call rejection | Android-Agent native tool call/result pairing | Reject, do not execute | `LiteRtNativeToolCallValidator` rejects missing name / incomplete arguments, logs `HALO_LITERT_TOOL_CALL_REJECTED`, and records content-free channel observation including unpaired model calls after trimming | SOURCE COMPLETE 2026-09-30 |
| Channel-drift visibility | Android-Agent channel drift | See whether the native channel was even used | `LiteRtToolChannelDiagnostics` + `LiteRtToolProbeResult` tightened (field counts and key-name digests only; no argument values, no response text) | SOURCE COMPLETE 2026-09-30 |
| Conversation/preface diagnostics | Android-Agent preface diagnostics | Explain a preface problem without leaking it | `LiteRtConversationDiagnostics` (mode, length, fingerprint, schema count, timings, echo flag) — raw instruction never retained; prompt-echo observation only (`matchedChars>=64` threshold, no text logged, no termination) | SOURCE COMPLETE (observation only) 2026-09-30 |
| Residency vs active execution | Halo's own `NativeResourceCoordinator` work in progress | Make the distinction undeniable | `executionLeaseHeld` and friends on `NativeResourceSnapshot`, `executingKinds()`/`isExecuting()` added beside `activeKinds()` (unchanged semantics for residency consumers) | SOURCE COMPLETE 2026-09-30 |
| Audio mode/routing belongs to the interaction session | Voice Agent `VoiceAudioRouting` | Formalise the lifetime Halo already honoured | `InteractionAudioAffinity` in runtime-sdk; binding survives `InputLease.release()`; ends only with the interaction | SOURCE COMPLETE 2026-09-30 |

### What was NOT copied

- Android-Agent's Agent architecture, memory system or journal: Halo's `AgentStep` journal remains execution truth.
- Voice Agent's forced built-in speaker: Halo preserves wearable/headset/Bluetooth route affinity, including `DISALLOW_PHONE` for wearable origin.
- The minimal ADK/LiteRT sample's dependency surface: no ADK in production, no `MANAGE_EXTERNAL_STORAGE`, no provider-architecture replacement, LiteRT-LM stays pinned at 0.17.1.
- No new agent runtime, durable journal, provider, router, resource manager, speech stack, assistant state machine or model database was introduced.
- Gemini CLI OAuth client identity, token cache, or any CLI credential import path.
- OpenCode OpenAI/ChatGPT OAuth client identity or ChatGPT cookie/session token handling.
- xAI OAuth client identity; subscription auth is only enabled when Halo has its own suitable client configuration.
- Any vendor-operated Halo relay; OpenCode companion is a user-operated endpoint.
- Per-vendor duplicate HTTP/SSE/tool parsing; existing providers are preserved as compatibility references, not rewritten wholesale.

## RUNTIME-058 / MINING-P1-001 — bounded agent, memory pull, MCP, voice continuity, procedures

Full record and the deferred-items list: [ANDROID-AGENT-VOICE-MCP-MINING.md](ANDROID-AGENT-VOICE-MCP-MINING.md).

### Harvested

- **Android-Agent `harness-improve`** — bounded execution guards (wall-clock, tool-round, failure
  streak, oscillation, run token accounting), thermal gating as policy, memory *pull* rather than a
  memory prompt dump, and short-lived exact-argument approval grants. Concepts only; no code copied.
- **ChaseDreamInfinity/voice-agent** — semantic barge-in evidence, interaction/session-owned audio
  routing, sentence/clause TTS chunking, sticky backend/route per response, tiered memory and an
  *optional* embedding capability.
- **Brownie** — MCP JSON-RPC mechanics (`initialize` → `tools/list` → `tools/call`), namespaced dynamic
  tool registration, learned-procedure metadata (origin, usage count, staleness), agent-authored
  schedules, notification RemoteInput reply.
- **kotlin-adk** — SKILL.md interoperability concepts only.

### Deliberately not adopted

- Android-Agent's `AgentRunner`/agent architecture, memory system or journal — Halo's `AgentStep`
  journal stays execution truth.
- Voice Agent's forced built-in speaker — Halo keeps wearable/headset/Bluetooth affinity, including
  `DISALLOW_PHONE` for wearable origin.
- Brownie's trust model, shell and filesystem delegation — imported MCP tools default to `SENSITIVE`,
  trust never implies execution approval, and results are bounded untrusted content.
- kotlin-adk's LiteRT module (intentionally incomplete/stubbed) and its skill runtime.
- No new agent runtime, journal, scheduler, provider, router, resource manager, speech stack, assistant
  state machine or model database. `ToolRegistry` gained a bounded removable dynamic overlay rather than
  a second registry.

## RUNTIME-059 / MINING-P2-001 — adaptive interaction, plan, web, optional capabilities

Full record and deferred-items list: [ADAPTIVE-INTERACTION-MINING.md](ADAPTIVE-INTERACTION-MINING.md).

### Harvested

- **kotlin-adk (A2UI)** — versioned structured UI messages, closed component catalog, streaming-safe
  accumulation, host renderer registry, and create/update/delete surface concepts (mapped onto
  projection events with monotonic revisions).
- **Android-Agent `harness-improve`** — visible plan steps and PENDING / IN_PROGRESS / COMPLETED
  progress projection.
- **Brownie** — DOM-settle: observe mutations, require a quiet window, then extract bounded content.
- **voice-agent** — the lesson that audio models may need a container (source of `Pcm16Wav`) and the
  optional-embedding capability shape (source of `OptionalRuntimeCapability`).

### Rejected

- A2UI as a second surface system; foreign durable-state ownership; arbitrary action dispatch;
  generic model-generated Compose; executable form callbacks; arbitrary URLs as image sources.
- Android-Agent's plan store: Halo's `ExecutionPlan`, `Task`/`TaskState` and `AgentStep` journal remain
  the only plan truth.
- Unrestricted browser automation: the dynamic web path is one isolated ephemeral WebView behind an
  explicit opt-in contract with a hard deadline.
- voice-agent's forced speaker routing and mandatory embedding download; Halo ships no capability
  artifact it has not verified, and reports every optional capability as Not installed.
- Replacing Sherpa or enabling `AUDIO_MODEL_TRANSCRIBE_THEN_REASON` by default.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

MINING-P3-001 harvest is documented in [MULTIMODAL-PERCEPTION-MINING.md](MULTIMODAL-PERCEPTION-MINING.md). Reviewed [voice-agent LlmEngine](https://github.com/ChaseDreamInfinity/voice-agent/blob/main/android/app/src/main/java/io/github/chasedreaminfinity/voiceagent/engine/LlmEngine.kt) for same-turn image/text, explicit image backend and PCM16 WAV framing; [AgentADK](https://github.com/MukeshKumar009/android-agent-adk-litertlm-on-device-ai/blob/main/app/src/main/java/com/mukesh/android_agent_adk_litertlm_on_device_ai/AgentADK.kt) for lifecycle context. No ADK or second assistant runtime is imported. [LiteRT Engine source](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Engine.kt) informs configuration but does not establish pinned 0.17.1 acceptance. [AndroidX ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface) 1.4.2 is declared for JPEG/PNG/WebP metadata/orientation parsing; no dependency was downloaded in this batch. The Android-Agent harness-improve/core-engine URL could not be inspected reliably; no source-copy claim is made for that path.

## MINING-P4-001 harvest — 2026-09-30

[Long-horizon mining ledger](LONG-HORIZON-AGENT-MINING.md) records inspected Android-Agent `harness-improve` and Brownie references. Adopted concepts: immutable content-addressed bodies/atomic writes, atomic tool groups, bounded reference search, parent-scoped read-only children and role routing. Halo implementation reuses its own persistence, executor, guards, router and Working Set; no reference code is copied or imported.

Android-Agent SegmentedHistoryStore explicitly reports itself unwired; its existence is not evidence of integration. The inspected compressor currently uses window trimming rather than a wired summarizing implementation. Brownie's summary-to-SYSTEM approach is rejected, as are separate subagent session storage and hidden execution truth. Exact source links, source uncertainty and per-file inventory appear in the ledger. Status: Halo source wired; build/tests/device acceptance not run.

## MINING-P5-001 harvest — 2026-09-30

[Semantic phone operator ledger](SEMANTIC-PHONE-OPERATOR.md) records the actual baseline, complete
production inventory and primary reference links. Brownie BrowserTool informs bounded quiet/deadline
settling; MessagingTool/NotificationTool inform native reply and bounded observation preference, without
claiming dispatch proves delivery. Android-Agent harness-improve AgentRunner informs exact-argument
approval and bounded failure/guard evidence. No reference code, alternate journal, agent loop, approval
store or privileged input runtime was copied. Halo's existing P1 guard/oscillation detector and AgentStep
journal remain owners. Source wired; build/tests/device acceptance not run.

## MINING-P6-001 — proactive automation runtime (2026-09-30)

P6 reviewed Brownie AgentSchedulerTool, ScheduleCalculator, BrownieService, BrownieNotificationListener and MessagingTool, plus kotlin-adk EventActions through primary raw sources. Harvested event-driven scheduling, typed event/action separation, background lifecycle and origin-only notification mechanics. Rejected unrestricted prompts, permanent sticky agent service as correctness requirement, untrusted notification instructions, unattended message side effects and silent capability escalation. Exact references, differences and source inventory are in the P6 engineering ledger.

See [Proactive automation runtime](PROACTIVE-AUTOMATION-RUNTIME.md).
