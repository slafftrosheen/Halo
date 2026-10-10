> 2026-10-10: the development matrix remains an advanced surface. Ordinary setup now uses [managed local model acquisition](../10-features/LOCAL-MODELS.md); [implementation and validation](../engineering/LOCAL-MODEL-LEVELUP-001.md) documents dynamic Hub discovery and authenticated license gates.

# LiteRT development model matrix (RUNTIME-049 / LITERT-MATRIX-003)

## LOCAL-AI-RECOVERY-001 — bounded local thinking policy — 2026-10-07

Qwen3 CHAT, FAST and AGENTIC now explicitly disable thinking through LiteRT-LM
0.17.1 `ThinkingConfig`; exact thinking-capable REASONING targets receive a
64-token budget. Capability metadata does not enable thinking by itself.
Provider-boundary `ToolUseMode.NONE` removes both native tools and Halo prompt
protocol instructions. Diagnostics use the same explicit policy without updating
production health. Role probe suite v2 and performance compatibility epoch 2
retain older history but exclude it from current recommendations/calibration.
The 90-second interactive deadline is unchanged; its local timeout diagnostic
contains execution metadata only. Validation and the single phone acceptance
are recorded in `docs/engineering/LOCAL-AI-RECOVERY-001.md`.

Automated gate: 721 tests, zero failures/errors/skips; debug APK assembled.
Physical acceptance is pending the user's manual one-pass run; no new phone
performance or conversational-quality claim is made.


## RUNTIME-063 — local role qualification — 2026-10-06

Explicit On-device developer actions now qualify installed exact artifacts by
FAST, CHAT, REASONING, AGENTIC and VISION separately. Cold primer is excluded from
three warm FAST samples; deterministic CHAT/reasoning probes and the existing
exact-target tool continuation produce process/session evidence. Physical identity
is verified before/after; backend drift invalidates evidence. Matrix execution is
serial, cancellable, failure-isolated, and cannot preempt active user inference.
Diagnostic traffic bypasses production EWMA, role/CHAT settings remain user-owned,
and the catalog/download/GPU gates remain unchanged. Developer tests are collapsed;
System Check reads recent evidence without running probes. Copy exports bounded
metadata only. ROLE-002 adds schema 25 durable role evidence; configuration remains explicit.

Full serial automated gate passed: 691 tests, zero failures/errors/skips, debug APK assembled.
Both-phone physical qualification remains pending;
no default-model recommendation or following batch selected.
See [qualification contract and acceptance plan](../engineering/LOCAL-MODEL-ROLE-001.md).

## RUNTIME-062 - exact role capability truth - 2026-10-05

Role execution and browsing share the portable exact-model eligibility evaluator.
CHAT remains the conversation default; FAST, REASONING, AGENTIC and VISION have
central requirements, strict explicit assignments and conservative automatic
ranking behind privacy gates. HALO_PROMPT is a valid Halo tool loop and remains
distinct from proof-gated NATIVE. Cached role queries run no inference or discovery.
Pinned continuations retain their target, FAST adaptation follows the resolved
model, and request correlation uses the real interaction identity. Roles and
System Check expose exact capabilities and human assignment labels.
Combined serial gate passed: 666 tests, zero failures/errors/skips, debug APK built.
Physical role/model acceptance remains pending.
See [RUNTIME-062 acceptance record](../engineering/RUNTIME-062-ROLE-MODEL-FABRIC.md).



## RUNTIME-061 - input truth and live local adaptation - 2026-10-05

Input modality is independent of response delivery. Typed Home requests keep CHAT and
text budgets even when Halo speaks; actual speech selects the voice latency policy.
Normal calibration lookup consumes verified inventory and a bounded process cache;
physical SHA verification belongs to inventory, load, and explicit benchmark boundaries.
One process-scoped tracker separates cold/warm TTFT, applies warm EWMA with sample gates
and hysteresis, and keeps live memory pressure as the final conservative authority.
System Check and model Details distinguish CALIBRATION, LIVE, and EFFECTIVE policy;
System Check can copy a content-free local AI diagnostic snapshot.

The shared model browser now includes the existing downloadable LiteRT catalogue on a
fresh install and updates installed/deleted entries from inventory. Uninstalled entries
open setup rather than becoming inference selections. No models or automatic GPU
promotion were added; no EWMA persistence or Room migration was introduced.
Serial automated gate passed: 649 tests, zero failures, debug APK assembled.
Two-phone physical acceptance remains pending.
See [implementation and acceptance record](../engineering/RUNTIME-061-LIVE-LOCAL-ADAPTATION.md).

## RUNTIME-060 — adaptive local execution — 2026-10-05

Explicit benchmarks now persist per artifact/device/runtime variant. Compatible measured
TTFT and live memory pressure reduce local history/output budgets below the 512/128
voice and 1024/256 text baseline; budgets never expand. Simple fresh spoken provider
turns prefer the existing FAST role. Action, vision, explicit reasoning and pinned
continuation ownership remain intact. Details/System Check expose calibration and
policy; generation diagnostics report actual cold/warm engine residency.

GPU qualification is unchanged, benchmarking remains explicit, and no device-name
heuristics or models are added. Serial automated gate passed: 641 tests, zero failures, and debug APK assembled.
Both-phone physical acceptance remains pending. See [implementation and acceptance record](../engineering/RUNTIME-060-ADAPTIVE-LOCAL-EXECUTION.md).

## DOGFOOD-006 physical evidence and latency gate — 2026-10-04

The supplied `ad5131ab4fa8+dirty` phone session verifies GPU crash containment,
AUTO selecting CPU while GPU qualification is UNKNOWN, and stable Qwen 3 0.6B
Dynamic INT4 CPU generation. LiteRT-LM remains **0.17.1**; quarantine,
qualification and native-attempt journaling remain enabled.

Interactive CPU latency is currently unacceptable: 75 history messages produced
23,661 ms TTFT and 27,273 ms generation. Cognition now projects a provisional
512-token history budget / 128-token output cap for voice and 1024 / 256 for text
on constrained on-device models. Durable conversation and required working context
are preserved; tool and unresolved run journals remain explicit. Projection reduction
is deterministic and does not invoke a summary LLM.

DOGFOOD-006.1: SOURCE VERIFIED / UNIT VERIFIED / CLEAN CHECKOUT VERIFIED;
PHONE VERIFIED: NO for these repairs and latency profiles. The required gate passed
469 tests with zero failures and assembled a clean candidate APK. History keeps a
contiguous newest suffix with conservative framing overhead; current requests, Working
Set and atomic tool continuations are preserved. The 512/1024 and 128/256 values remain
ceilings; RUNTIME-060 adds measured downward tiers. Expansion still requires physical TTFT evidence. See the [verification record](../engineering/STABILIZATION-2026-10-04.md).

`HALO_CONTEXT_PROJECTION` reports only target, surface, window, budget and counts.
Generation diagnostics expose estimated input/output tokens, history message count,
engine initialization duration (`engineLoadMs` for the resident engine), conversation
creation, TTFT, generation duration and effective backend. These are estimates,
not tokenizer measurements; required context can exceed the history latency budget.

| History projection | Warm CPU TTFT on control phone |
| --- | --- |
| 256 estimated tokens | Pending: no ADB device connected |
| 512 estimated tokens | Pending: no ADB device connected |
| 1024 estimated tokens | Pending: no ADB device connected |
| Existing unrestricted projection | Reported 23,661 ms; same-prompt comparison pending |

Repeat the same short prompt with each projected history size, warm CPU residency,
128 output tokens and no debugger. Desirable TTFT is below 3 seconds; the first
acceptable milestone is below 5 seconds. If 256–512 still misses this substantially,
classify CPU Qwen 0.6B as fallback/offline. Do not re-enable GPU to satisfy this gate.

Status for DOGFOOD-006.1: SOURCE VERIFIED / UNIT VERIFIED / CLEAN CHECKOUT VERIFIED / PHONE VERIFIED: NO.
Runtime: **LiteRT-LM 0.17.1** (unchanged; no upgrade in this batch).

This page exists because a single artifact (`Qwen 3 0.6B Dynamic INT4`) cannot distinguish:

- a model-specific failure
- a LiteRT runtime failure
- a GPU-driver failure
- a quantization-specific failure
- a prompt/template failure
- a resource-scheduling failure

The catalog therefore carries several real, byte-pinned **text** artifacts plus one explicitly gated
one, and model switching/testing is first-class in the Model Browser.

## 1. Evidence ladder (do not conflate these)

| Level | Meaning | How it is established |
| --- | --- | --- |
| CATALOGUED | Halo knows the artifact's pinned identity | `LiteRtCatalogModel` entry |
| DOWNLOAD VERIFIED | Bytes on disk match the pinned size **and** SHA-256 | downloader + inventory resolution |
| ENGINE VERIFIED | `Engine.initialize()` succeeded | load path |
| GENERATION VERIFIED | A non-empty completion was produced | `verifyModel`, smoke test or a real turn |
| PHYSICAL DEVICE VERIFIED | The above passed on real hardware, per backend | phone acceptance run |

`Engine.initialize()` succeeding is **never** described as verification: conversation creation and a
non-empty generation are separate, independently visible failure stages
(`ENGINE_INIT` / `CONVERSATION` / `FIRST_TOKEN` / `GENERATION`).

## 2. Built-in development matrix

Control artifact (unchanged pins):

| id | Display | Repo | Revision | File | Bytes | SHA-256 | Context |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `qwen3-0.6b-dynamic-int4` | Qwen 3 0.6B Dynamic INT4 | `litert-community/Qwen3-0.6B` | `a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76` | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` | `344671744` | `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856` | 4096 |

Development additions (all `OPEN_DIRECT`, directly downloadable):

| id | Display | Repo | Revision | Bytes | SHA-256 | Preferred backend | Maturity |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `qwen3-1.7b-dynamic-int4` | Qwen 3 1.7B Dynamic INT4 | `litert-community/Qwen3-1.7B` | `73fbc3fe8271c162a603ee66f6e7ed25b6211195` | `977184032` | `2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b` | GPU (CPU fallback allowed) | DEVELOPMENT |
| `qwen2.5-1.5b-instruct-q8` | Qwen 2.5 1.5B Instruct Q8 | `litert-community/Qwen2.5-1.5B-Instruct` | `19edb84c69a0212f29a6ef17ba0d6f278b6a1614` | `1597931520` | `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9` | AUTO / GPU first | DEVELOPMENT |
| `deepseek-r1-distill-qwen-1.5b-q8` | DeepSeek R1 Distill Qwen 1.5B Q8 | `litert-community/DeepSeek-R1-Distill-Qwen-1.5B` | `e34bb88632342d1f9640bad579a45134eb1cf988` | `1833451520` | `69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295` | AUTO | EXPERIMENTAL |
| smollm2-135m-instruct | SmolLM2 135M Instruct | litert-community/SmolLM2-135M-Instruct | 749fc23bc8d7ef89db031d075004289c88f41c2f | 142819328 | ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37 | CPU | EXPERIMENTAL |
| smollm2-360m-instruct | SmolLM2 360M Instruct | litert-community/SmolLM2-360M-Instruct | 507c99cfe6541ba2bcd84818786f7b025935e5e1 | 373719040 | 8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e | CPU | DEVELOPMENT |

Files:

- `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm`
- `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`
- `DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm`

Gated entry (catalogued for identity only — never downloadable, never mirrored):

| id | Display | Repo | Revision | File | Bytes | SHA-256 | Access |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `gemma3-1b-it-int4` | Gemma 3 1B IT INT4 | `litert-community/Gemma3-1B-IT` | `42d538a932e8d5b12e6b3b455f5572560bd60b2c` | `gemma3-1b-it-int4.litertlm` | `584417280` | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` | `GATED_IMPORT_ONLY` |

Gemma must be acquired through the upstream license gate. Halo shows
"Requires Gemma license acceptance on Hugging Face. Import a licensed .litertlm artifact from
device." instead of a Download button. A manually imported file whose bytes and SHA-256 match the
official artifact is canonicalized to `gemma3-1b-it-int4` and treated as the known artifact.

Large multimodal Gemma 3n / Gemma 4 artifacts are deliberately **not** added yet: they are
3.5–5 GB with substantially larger peak memory and need the capability/residency architecture first.

Capability flags describe the **artifact** as Halo can prove it, never the base model family.
`supportsTools` is false for every artifact in this batch; `supportsVision`/`supportsAudio` are false
everywhere. A development **Probe tools** action can promote a specific artifact to
"tool generation proven" via a harmless synthetic `halo_probe` tool call, and a failed probe never
marks the model broken.

## 3. Per-model runtime profile

`LiteRtRuntimeProfile` (preferred backend, CPU-fallback permission, known/default context, sampling
and max-output defaults, bounded context ladder) is resolved in this order:

1. explicit request override
2. user/developer model preference
3. catalog runtime profile
4. Halo safe default

Qwen 2.5 1.5B ships the upstream sampling profile (topK 20, topP 0.8, temperature 0.7). Gemma 3 1B
declares `useModelDefaultContext = true` because Halo has no verified artifact profile for it.

## 4. Developer backend override

AUTO / GPU / CPU is selectable **per model** in the Model Browser and persisted by model id.

- Explicit **GPU** means "do **not** silently CPU fallback".
- Explicit **CPU** means "never initialize GPU".
- **AUTO** means GPU first, then CPU fallback under the existing rules.

Runtime reports requested backend, effective backend and the fallback reason separately.

## 5. Context fallback

For known 4096-token artifacts under an AUTO policy, an engine initialization failure that is
specifically a memory/resource allocation failure may step down the bounded ladder
`4096 -> 2048 -> 1024`. Downshift never happens for a corrupt model, unsupported op, ABI error,
invalid metadata or a generic unknown exception, and once a model/backend/context combination is
proven in a residency it is reused instead of retrying the larger allocation on every prompt.
Requested context, effective context and the reason are recorded.

## 6. Downloads

Large artifacts (up to ~1.8 GB) are resumable:

- partial metadata is stored beside `<id>.litertlm.part` (modelId, repoId, revision, filename,
  expected bytes, expected SHA-256, downloaded bytes, ETag, Last-Modified)
- resume is attempted only when the metadata matches the exact pinned artifact, using an HTTP
  `Range` request; a valid `206` appends, anything else restarts from zero
- after completion: fsync, exact byte count, full SHA-256 over the whole file, sidecar staging,
  atomic move with fallback, inventory refresh, exact catalog resolution
- `HTTP success`, `Content-Length` and `ETag` alone are never trusted
- partials survive network loss, socket timeouts, cancellation and app restart; they are deleted on
  hash mismatch, an oversized partial, a changed pin, or explicit **Discard partial**
- before starting a transfer, a `StatFs` preflight requires artifact + finalization overhead + a
  512 MB reserve, and reports required/free values instead of waiting for a filesystem write failure

## 7. Model switching invariant

Exactly one LiteRT `Engine` may own model weights at once. Before a new model is initialized the
existing generation is cancelled, the conversation closed, the old engine closed and stale engine
resources released. The UI never claims model A is Ready while model B is resident: `selected`,
`installed`, `verified`, `resident` and `generation-proven` are separate facts.

## 8. Failure memory

Health is recorded per **model + artifact fingerprint + backend**:

- `lastLoadFailure` / `lastGenerationFailure` with stage and backend
- `lastSuccessfulGeneration`, `lastSuccessfulBackend`, `lastSuccessfulContext`
- `generationProven`

A GPU failure for Qwen 3 1.7B cannot poison Qwen 3 0.6B, and a failed artifact fingerprint cannot
follow a newly downloaded revision.

## 9. Detection / logging events

`HALO_LITERT_SELECT`, `HALO_LITERT_DOWNLOAD`, `HALO_LITERT_RESUME`, `HALO_LITERT_VERIFY`,
`HALO_LITERT_LOAD_START`, `HALO_LITERT_LOAD_READY`, `HALO_LITERT_CONVERSATION_READY`,
`HALO_LITERT_FIRST_TOKEN`, `HALO_LITERT_GENERATION_COMPLETE`, `HALO_LITERT_GENERATION_FAILED`,
`HALO_LITERT_UNLOAD`, `HALO_LITERT_SWITCH`, `HALO_LITERT_CONTEXT_DOWNSHIFT`, `HALO_LITERT_THINKING`,
`HALO_LITERT_TOOL_PROBE`.

Fields: interaction/request id, model id, artifact SHA prefix, requested/effective backend,
requested/effective context, duration and failure stage. Full prompts are never logged by default.

## 10. Phone acceptance checklist (STILL REQUIRED)

Build first:

```
./gradlew :providers:litert:test
./gradlew :engines:ai:test
./gradlew :app:compileDebugKotlin
```

Then, on the physical phone, for each of Qwen3 0.6B, Qwen3 1.7B INT4, Qwen2.5 1.5B Q8 and
DeepSeek R1 Distill Qwen 1.5B Q8 (record each classification separately, per backend):

1. download
2. verify hash
3. select
4. AUTO load
5. smoke generation #1
6. generation #2 (no process restart)
7. unload
8. reload
9. generation #3

Then, where feasible, repeat for explicit **GPU** and explicit **CPU**, recording results
separately (CPU/GPU differences are the point of the matrix).

After the individual runs: switch `Qwen 0.6 -> Qwen 1.7 -> Qwen 2.5 -> DeepSeek -> Qwen 0.6`
without restarting Halo, and finally run `speech -> Whisper -> selected LiteRT -> TTS` at least twice.

Tool-call probe results and context-downshift events must be recorded per artifact; a failure in one
artifact must not abort testing the others.

## Diagnostic ladder (RUNTIME-057 / MINING-P0-001)

Status: SOURCE COMPLETE / **BUILD NOT RUN / TESTS NOT RUN / PHONE ACCEPTANCE NOT RUN**.

Local inference failures are now attributable to a layer. Each level is inspectable without implying
the next one passed:

| Level | Owner | What it exercises | How to invoke |
| --- | --- | --- | --- |
| 0 Artifact/inventory | `LocalModelInventoryRepository` | bytes, SHA-256, compatibility | automatic (inventory refresh) |
| 1 Direct LiteRT control | `LiteRtControlProbeRunner` | `Engine` → `Conversation` → first token → completion | System Check → **Run control probe**; Model Browser → **Direct runtime probe** |
| 2 Halo LiteRT provider | `LiteRtLocalProvider` + `LocalModelSession` | load policy, backend fallback, provider status | System Check → **Verify** |
| 3 Native resource arbitration | `NativeResourceCoordinator` | execution acquire/release vs residency | System Check → Native resource row |
| 4 Provider router | `AiProviderRouter` | explicit provider/model routing | System Check → **Run router probe** |
| 5 Agent/cognition | `CognitiveSession` + journal | normal user interaction | a real conversation |
| 6 Speech route | `HaloAudioSessionCoordinator` + `SpeechOutputRouteController` | delivered to the correct target | Android Assistant → Audio continuity |

**Native tool truth.** A catalog `supportsTools = true` is a declaration: it makes the artifact
eligible for probing and never by itself means `NATIVE`. `NATIVE` is claimed only for a proven
`<sha256>:litertlm-<version>:halo-openapi-v1` key, so proof does not survive a runtime or schema
change. A declared-native but unproven artifact honestly reports `HALO_PROMPT`. The synthetic probe
tool (`halo_probe`) never executes inside LiteRT — `automaticToolCalling = false` and
`HaloOpenApiTool.execute()` still throws loudly.

**Control-probe safety.** A probe is `BLOCKED` while any heavyweight native resource is
ACQUIRING/LOADING/IN_USE, and `BLOCKED` while a model is merely warm RESIDENT (use the existing
unload path). It never bypasses artifact verification or the arm64-v8a ABI preflight, and it never
changes selection, routing or generation-proven state.

Acceptance additions for the next physical pass:

1. System Check shows the local artifact as VERIFIED and the four control-probe stages separately.
2. Provider verification and the router probe report separately from the control probe.
3. A warm RESIDENT ASR/LLM shows `executionLeaseHeld=false`; an active one shows `true`.
4. A tool-capable artifact runs the synthetic probe explicitly and is not called native-proven from
   catalog metadata alone; a malformed emitted call cannot execute.
5. A HeyCyan-origin interaction keeps HeyCyan as intended output across STT → inference → TTS
   preparation, and releasing mic capture does not erase that affinity.
6. A `DISALLOW_PHONE` wearable policy never turns into an audible phone fallback.


## LOCAL-MODEL-ROLE-002

SmolLM2 135M/360M are immutable, inspected text candidates, acquired only explicitly. Schema 25 retains exact device/artifact/runtime/config/suite role evidence. Model Roles separates configured, measured and suggested targets; explicit Use revalidates and uses existing selection authorities. On-device shows current-device history, retest and content-free copy. Evaluation-purpose hints never grant capability or choose roles. No default changes, automatic acquisition, GPU qualification or new residency owner. Two-phone comparison is pending. See docs/engineering/LOCAL-MODEL-ROLE-002.md for pins, policy and physical acceptance.

SmolLM2 filenames are SmolLM2_135M_Instruct.litertlm and SmolLM2_360M_instruct.litertlm. Inspected context/KV limits are respectively 4096 and 8192; these are artifact metadata, not measured role claims. Both are Apache-2.0 OPEN_DIRECT candidates with no verified image/audio/native-tool capabilities.
