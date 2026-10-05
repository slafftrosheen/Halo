# RUNTIME-062 - role-specialized model fabric

Builds on RUNTIME-061 commit `82d5b8f9`. Both batches were verified together before
being committed in dependency order. No model artifacts were added.

## Authority

`AiTargetEligibilityEvaluator` in provider-sdk is portable and shared by routing,
pinned recovery, the read-only candidate query and host presentation. Exact
`ModelInputCapabilityReporting.capabilitiesForModel(model.id)` wins over aggregate
provider capabilities and static model declarations. The metadata intersection is
used only when exact reporting is absent. Exact ModelReadinessReporting also separates
installed role-model readiness from a missing conversation default without
rewriting selection or the default status projection. Static false cannot reject a proven Halo
prompt loop; static true cannot invent runtime TOOLS.

`AiRoleRequirements` defines TEXT for CHAT, FAST and REASONING, TEXT + TOOLS for
AGENTIC and TEXT + IMAGE for VISION. Image input still requires
`hasVerifiedImageInput()`. Tool protocol is resolved for the exact model; NATIVE
qualification remains owned by each provider. LiteRT's artifact/runtime/schema
native proof gate is unchanged.

The portable `AiRoleCandidate` contains only bounded eligibility reasons, exact
capabilities, location and protocol. `candidatesForRole` consumes supplied cached
catalogue and configured-provider IDs; it never calls listModels, getStatus,
generation, model load, benchmark, probe, login or remote refresh. Existing local
inventory supplies installed/downloadable truth. ProviderControlRepository shares
this projection across Model Browser and Roles; System Check uses the same query.

## Routing and persistence

Hard eligibility precedes `AiRoleCandidateRanking`. FAST prefers the compatible
ready conversation target, then on-device, LAN and remote candidates. AGENTIC
prefers NATIVE before HALO_PROMPT among policy-permitted targets. Private context
and LOCAL_ONLY remove forbidden targets before ranking. REASONING uses TEXT
eligibility and ordinary location ordering, without model-name heuristics.
CHAT continues to use PersistedAiSelection and has no separate stored binding.

Explicit assignments require both provider and model. Malformed stored values,
deleted artifacts and incompatible targets fail clearly without substitution.
The existing role_* persistence keys and atomic Storage.putBatch writes remain.
Deleted-model bindings are retained for recovery after reinstallation.

Journaled provider/model identities remain pinned through initial generation,
tool results, continuation and recovery. Role changes affect future runs. LiteRT
receives the routed model ID before the configured default; its single resident
session switches A -> B -> A and reuses only matching residency. FAST profile and
health lookup follow the resolved model, not the conversation default.

## Presentation and diagnostics

Roles explain their purpose and use human mode labels: Use conversation model,
Choose automatically, Use selected model and Off. CHAT opens ordinary conversation
model selection. Successful role picks return to Roles. Role browsers filter exact
eligible candidates; other browser entries expose bounded rejection reasons.
System Check shows mode, provider/model, eligibility, capabilities and protocol
without generation. Tool labels distinguish Native tool loop, Halo prompt tool
loop and No tool loop.

AiRequest.interactionId defaults to null for direct callers. Cognitive preparation
binds the real turn identity before routing and carries it into outbound requests,
including delegated turns. Remote traces use the actual workload role and retain
the interaction identity across fallback with a new provider request ID.
HALO_ROLE_TARGET_RESOLVED logs role, mode, provider/model, location, protocol,
fallback and bounded candidate/rejection counts, never request contents.

## Distinctions

- Catalogue declaration is not runtime capability.
- supportsTools is not equivalent to Halo being able to complete a tool loop.
- NATIVE and HALO_PROMPT are different transports.
- Role binding is not current resident model.
- Role selection is not acceleration qualification.
- AUTOMATIC grants no permission to send private context remotely.
- Role changes grant no permission to retarget an in-flight run.

## Verification

Combined RUNTIME-061/RUNTIME-062 verification passed: **666 tests, zero failures,
errors or skips**, and debug APK assembly succeeded. The serial Gradle gate covers both
SDKs, engines/ai, cognition, observer, LiteRT, app unit tests and debug assembly.
Regressions cover exact capability overrides, protocols, strict malformed/deleted
bindings, atomic process recreation, inherited/disabled modes, privacy precedence,
FAST automatic/inherited ordering, verified-image gates, cached read-only queries, role-bound pinning,
local residency switching, exact readiness with a missing conversation default,
resolved FAST calibration, browser selection and fallback correlation.

Physical development acceptance remains pending: configure CHAT/FAST/AGENTIC on
installed models and confirm typed CHAT, spoken FAST and tool-task AGENTIC routing,
with HALO_PROMPT when native qualification is absent. GPU AUTO remains CPU when
unqualified. No automatic downloads, new models or speculative multi-engine
residency were introduced.

| Gate | Tests | Failures / errors / skips |
| --- | ---: | ---: |
| provider-sdk | 16 | 0 |
| runtime-sdk | 43 | 0 |
| engines/ai | 75 | 0 |
| runtime/cognition | 210 | 0 |
| runtime/observer | 11 | 0 |
| LiteRT | 91 | 0 |
| Sherpa STT | 18 | 0 |
| app | 202 | 0 |
| Total | 666 | 0 |

The combined gate used one worker, no parallel execution, no daemon, a 2 GB heap
and 768 MB maximum metaspace. Sherpa's gate was retained to cover RUNTIME-061.
The final log is outside the repository at
C:/Users/slavd/.codex/artifacts/runtime061/runtime062-final-verification.log.
The APK is app/build/outputs/apk/debug/app-debug.apk (228100244 bytes).
Both staged and working-tree diff checks passed. No physical phone, live account,
benchmark or acceleration acceptance is inferred from automated results.
