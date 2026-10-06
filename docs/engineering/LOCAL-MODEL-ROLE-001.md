# RUNTIME-063 / LOCAL-MODEL-ROLE-001

## Local model role qualification, suite v1 — 2026-10-06

Base: `4ad3569c273c4b667ae43f1fd073780b8756a3a9`. RUNTIME-061 and
RUNTIME-062 are landed. This batch adds evidence, not model recommendations or
configuration. Physical qualification on either phone remains pending.

### Execution boundary

Settings → Intelligence → On device exposes an explicit per-model **Run role
suite** inside **Developer tests**, plus **Run installed role matrix**, **Cancel**,
**Copy role matrix**, and navigation to **Configure model roles**. Opening any
screen, System Check refresh, startup, download completion, model selection, and
provider refresh never start a suite. System Check reads current process evidence.
The existing **Smoke matrix** remains runtime bring-up evidence; the **Role
matrix** adds fixed role-oriented behavior probes. Existing Verify, Smoke,
Benchmark, Direct runtime probe and Tool channel actions remain available.

`LocalModelRoleQualificationRunner` reserves the existing `LocalModelSession`
benchmark admission, verifies physical SHA-256 and size, forces a cold residency,
runs bounded diagnostic generations, unloads, and verifies physical identity again.
Artifact or backend drift discards behavioral results. Models run serially;
one failure yields a bounded failed row and does not skip subsequent installed
models. Cancellation stops remaining work and releases residency/admission in a
non-cancellable cleanup block. Diagnostic resource leases cannot ask an active
owner to yield or cooperatively preempt it. Active cognition is rejected as
RESOURCE_BUSY. Every loaded model and agentic request carries the exact target.

The provider adapter reuses its existing conversation mapper, HALO_PROMPT parser,
backend safety policy and native coordinator. It does not instantiate another
engine or call the production `ProviderTurnExecutor` observation sink. There are
no role, CHAT, routing-policy, temperature, download, GPU-evidence or benchmark
profile creation writes. Production live EWMA is neither observed nor reset by
these generations. Model switching drops native conversation residency; each probe
uses only its fixed synthetic context.

### Separate dimensions

- FAST: one cold primer, then three warm short generations, temperature 0,
  at most 32 output tokens. The primer never enters warm median/min/max. Where
  runtime residency is reported, primer must be cold and successful warm samples
  must be warm. Three valid samples are required for PASS. GOOD ≤ 4000 ms,
  SLOW ≤ 8000 ms, VERY_SLOW > 8000 ms describe the actual warm median, not a
  universal speed judgment. Unsupported residency reporting is explicitly UNKNOWN.
- CHAT: strict `HALO_CHAT_OK`, multiplication `136`, and synthetic contextual
  recall `COBALT-47`, at most 64 tokens. Three of three pass; other counts fail.
- REASONING: fixed arithmetic, transitive relation and constrained counting,
  final answers `142`, `Chris`, `90`, at most 192 tokens. Three of three pass;
  two of three visibly remains FAIL. Only final text is evaluated; separated
  reasoning contributes a character count and never enters display/TTS/export.
- Normalization: trim, strip one matching surrounding quote pair, strip one
  terminal period, compare case-insensitively. Additional prose and repeated
  punctuation fail. No fuzzy matching or LLM judge.
- AGENTIC: the existing complete phase probe resolves an optional explicit
  provider/model. It requires exactly one synthetic `halo_probe`, expected
  argument, correct call/result pairing, continuation on the same target, final
  output, and no unexpected second call. HALO_PROMPT and NATIVE remain distinct.
  Unsupported protocol yields NOT_APPLICABLE. Temperature is 0 and output bounded.
- VISION: current text-only artifacts yield NOT_APPLICABLE /
  NO_VERIFIED_IMAGE_CAPABILITY. Only existing Vision Lab PROVEN evidence is
  projected for eligible imports. This suite never executes a vision probe.

There is no universal score, “best model”, recommendation application, or automatic
role assignment. A role probe is not a full benchmark; a pass does not establish
general intelligence. The reasoning probe is not a reasoning benchmark, FAST
latency does not qualify GPU, and agentic probe success does not authorize arbitrary
tools. Development traffic is not production health. AUTO with GPU UNKNOWN remains
CPU, and GPU qualification is a separate future batch.

### Identity, privacy and evidence lifecycle

The trusted installed matrix includes only already installed verified artifacts.
Unknown imports require explicit per-model runs. Existing pinned direct-download
catalog entries remain unchanged: Qwen 3 0.6B Dynamic INT4, Qwen 3 1.7B Dynamic
INT4, Qwen 2.5 1.5B Instruct Q8 and DeepSeek R1 Distill Qwen 1.5B Q8. Gemma 3 1B
IT INT4 participates only if the exact licensed artifact was already imported.
No automatic download, mirror, acquisition, license bypass or catalog expansion.

Results are process/session evidence, without a Room table or migration. Agentic
provenance is retained only after the whole suite's physical/backend checks pass,
and only in an existing matching stable benchmark profile with exact artifact,
device/runtime, backend, context, threads and GPU-layer identity. Without that
profile, a session pass remains valid but UI asks the operator to run Benchmark
to retain device/runtime provenance. Each validated model retains its matching
provenance before the matrix advances, so cancelling a later model preserves
completed evidence. Benchmark is never run implicitly. Concurrent inventory
refreshes return their own locked snapshots; a startup refresh cannot make a
just-verified installed artifact appear missing.

Copy uses a field allowlist: Halo source id, suite version 1, model id, SHA prefix,
size, requested/effective backend, context, threads, GPU layers, load time, cold
TTFT, three warm TTFTs and statistics, sample count/residency, CHAT/REASONING counts,
AGENTIC protocol/tool/continuation verdicts, Vision eligibility, memory pressure,
and bounded failure codes. No prompts, responses, reasoning text, synthetic
conversation, tool argument values, device fingerprint, Android ID, or raw
exceptions are exported. Normal cognition stays hash-free; strict physical
verification is confined to explicit development boundaries.

### Automated validation

Full serial gate passed on 2026-10-06: **691 tests, 0 failures, 0 errors,
0 skips**; `:app:assembleDebug` succeeded. `git diff --check` passed.

| Module | Tests |
| --- | ---: |
| provider-sdk | 16 |
| runtime-sdk | 44 |
| engines/ai | 75 |
| runtime/cognition | 210 |
| runtime/observer | 11 |
| providers/litert | 107 |
| providers/sherpa-stt | 18 |
| app | 210 |

Verified command:

```powershell
.\gradlew.bat :sdk:provider-sdk:test :sdk:runtime-sdk:test :engines:ai:test `
  :runtime:cognition:test :runtime:observer:test :providers:litert:testDebugUnitTest `
  :providers:sherpa-stt:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug `
  --no-parallel --max-workers=1 --no-daemon `
  "-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m"
```

Regression coverage includes warm scoring/residency, strict answer normalization,
final text versus reasoning, exact-model continuation, unchanged settings/health,
serial load/unload, failing-model isolation, installed-only filtering, artifact and
backend changes, active-owner admission, cancellation and content-free export.

### Physical acceptance plan (not executed)

Install the identical APK on the development phone and Samsung S22 Ultra. Start
with already installed Qwen 3 0.6B and 1.7B. Add Qwen 2.5 1.5B Q8 and DeepSeek R1
1.5B Q8 only if storage/time permit; the several-GB matrix is never required to
use Halo. Run the same suite version and copy each report. Record artifact SHA
prefix, backend/configuration, load ms, memory pressure, cold TTFT, all three warm
TTFTs/median, CHAT x/3, REASONING x/3, and AGENTIC protocol/initial/continuation
verdicts. Check one-model residency, cancellation, an active conversation refusal,
and unchanged roles/settings on both phones.

Choose defaults and the next batch only after physical evidence: stronger
artifacts for measured role gaps, GPU qualification if CPU is the common
bottleneck, or role-aware prewarm if switching dominates. No HeyCyan protocol
changes belong to this batch.
