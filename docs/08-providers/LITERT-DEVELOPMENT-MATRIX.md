# LiteRT development model matrix (RUNTIME-049 / LITERT-MATRIX-003)

Status: SOURCE COMPLETE — implementation only. Not compiled, unit-run or phone-verified in this batch.
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
