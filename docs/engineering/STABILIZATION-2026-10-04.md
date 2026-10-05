# DOGFOOD-006.1 - clean-tree recovery and regression lock - 2026-10-05

Base: `73c469564d6b92bb68368bc85354b7c2df75dc0a`.
Verified candidate before the documentation amendment:
`626bdd9717188f9b80004fa3481e498e0baf6852`.

| Evidence boundary | Result |
| --- | --- |
| SOURCE VERIFIED | Yes: recovered helpers are committed; rejection and projection paths audited |
| UNIT VERIFIED | Yes: 469 tests, zero failures/errors/skips |
| CLEAN CHECKOUT VERIFIED | Yes: full gate and debug APK succeeded in a separate Git worktree |
| PHONE VERIFIED | No: no installation, TTFT measurement, effect verification or physical soak in this batch |

The missing memory recovery, notification lifecycle and reminder validation implementations
already existed as untracked files. They were retained, audited and committed, including the
portable NativeMemorySample definition. No source was copied into the clean worktree and no
local.properties file was needed; Android SDK discovery used ANDROID_HOME. Git status and
untracked-file lists were empty before and after verification. The normal Gradle build cache
was enabled: 243 tasks executed and 242 restored from cache.

Recovery uses suspendable sampling, serialized bounded probes, and strict hysteresis above
1.5 times the larger platform/10-percent threshold. Notification mutations are synchronized.
History projection selects a contiguous newest suffix, reserves 16 framing tokens per pair,
and counts omissions against the original history. The 512/1024 history and 128/256 output
limits remain provisional; no hardware tuning was performed. Application launch ports make
policy testable without a physical launch. Semantic rejections distinguish expired/unavailable
resources, permissions, disabled/unknown tools, unsupported operations and bad arguments,
including claimed-action replay refusal. Notification reply receipts explicitly use DISPATCHED
rather than inheriting CONFIRMED.

Regressions cover native receipt/rejection/wait states and older-invocation isolation; fresh
exact human authority and ToolTurnExecutor propagation with confirmation/risk gates; launch
policy; contiguous history with current request, Working Set and atomic tool continuations;
virtual-time memory recovery and ASR grace; concurrent notification lifecycle access and
unconditional listener update routing; temporal validation, interpretation clarification and
one-use evening follow-up. Invalid reminders carry clarificationOnly, preventing entry into
the existing reasoning/session task route. Listener routing is unit tested; real Android
callback delivery and phone effects remain physical acceptance work.

## Reproducible clean-checkout gate

```text
./gradlew.bat :libraries:halo-cognition-language:test :sdk:runtime-sdk:test :engines:interpretation:test :runtime:cognition:test :app:testDebugUnitTest :app:assembleDebug --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8"
```

The four portable suites ran first in the development checkout, then app tests and assembly.
The complete command subsequently passed in the clean candidate worktree.

| Test module/task | Tests | Failures/errors/skips |
| --- | ---: | ---: |
| :libraries:halo-cognition-language:test | 31 | 0 |
| :sdk:runtime-sdk:test | 41 | 0 |
| :engines:interpretation:test | 11 | 0 |
| :runtime:cognition:test | 194 | 0 |
| :app:testDebugUnitTest | 192 | 0 |
| Total | 469 | 0 |

- assembleDebug: BUILD SUCCESSFUL from the clean candidate; full gate duration 16m 59s.
- Candidate output: `app/build/outputs/apk/debug/app-debug.apk` in the clean worktree.
- Preserved candidate: `build/reports/stabilization/dogfood0061-candidate.apk` (ignored).
- Candidate APK size: 227,371,591 bytes (216.84 MiB).
- Candidate APK SHA-256: `c9f7e6a2aff47a0ad327f8dabca5757870761ec78e15f90cdd07d072b7caccbb`.
- Candidate BuildConfig: HALO_SOURCE_ID=`626bdd971718`, HALO_GIT_DIRTY=false.
- Raw logs are ignored under `build/reports/stabilization/`; the root log artifact was removed.

This documentation amendment changes no functional source or tests. Delivery requires another
clean-worktree assembly at final HEAD to stamp the final source identity. The release copy is
`releases/halo-alpha-debug.apk`. ADB sees a connected phone, but PHONE VERIFIED remains no.
The next step is a new physical dogfood log. Halo is not RC-ready.

---

# DOGFOOD-006 — Execution truth, local latency and runtime signals

Base: `ad5131ab4fa88c21b19cfb452c5307fab95f2b63`. Supplied physical build:
`ad5131ab4fa8+dirty`. Initial status, unstaged/staged diffs and eight-commit log were
inspected before editing. No tracked dirty changes were present; the pre-existing
untracked `ToolRegistryOwnershipTest.kt` was preserved and excluded from the original
DOGFOOD-006 work; it is already committed in the DOGFOOD-006.1 base.
Historical reports below are retained.

## Physical truth supplied by the phone report

| Area | Physical evidence |
| --- | --- |
| LiteRT GPU crash containment | Working |
| LiteRT AUTO safe CPU resolution | Phone verified |
| LiteRT CPU generation stability | Phone verified |
| LiteRT CPU interactive latency | Currently unacceptable; optimization in progress |
| Whisper Small warm decode | Phone verified |
| Native action truth | Regression found |
| Memory-pressure recovery | Regression found |
| Reminder temporal clarification | Regression found |
| Notification update deduplication | Phone acceptance pending |

These are user-supplied observations, not new hardware measurements from this batch.
No new physical acceptance was run for DOGFOOD-006. Halo is not RC-ready.

## Implemented repairs

- Native outcomes resolve the current invocation's durable rejection/receipt and terminal
  run state. Loop `Completed` supplies no success evidence. Result and timing logs share
  the resolved outcome; response publication and TTS use that response. Existing episode
  and reflection generation remain based on durable receipts, with a rejection/blocked
  consistency regression. Dispatch responses explicitly leave effect completion unconfirmed.
- Host rejection kinds distinguish schemas, unavailable capabilities/resources, permissions
  and unsupported operations. Diagnostic reasons use bounded static labels; private
  arguments and platform exception contents are excluded.
- The runtime validates interaction ID, tool ID, canonical argument digest, human origin
  and a 30-second authority lifetime. ToolRegistry and HostToolRegistry pass the resulting
  context to ApplicationTools. Foreground or validated direct authority permits an attempt;
  background autonomous/model-only calls get no authority. Capability failures are inspected,
  and a dispatch receipt never claims the app reached foreground. Android may silently deny
  a background start; phone acceptance must verify the external effect.
- Cognition projects newest complete conversation pairs into provisional voice/text history
  budgets (512/1024 estimated tokens) with output caps (128/256). Durable ConversationStore,
  current request/system context, required Working Set and atomic execution journals remain
  untouched. No extra summary inference occurs for these profiles.
- Memory callbacks apply transient LOW/CRITICAL, evict idle ejectable models, and probe after
  5, then 15, then 30 seconds. Probes serialize with callbacks, cancel superseded jobs, use
  MemoryInfo plus hysteresis (10% or platform threshold; recovery at 1.5 times that level),
  and stop on NORMAL or after the bounded sequence. Lifecycle and pre-admission workload
  evaluation restart live sampling, so recovery after the last timed probe cannot stay latched
  across subsequent ASR/model turns.
  Reacquired ASR under NORMAL regains its configured 90-second warm grace.
- Reminder content and temporal slots are separate. Missing, incomplete, invalid, ambiguous
  bare-hour and past-today times clarify before task creation. Usable word/day-part times
  canonicalize to backend clock expressions; day parts use deterministic 09:00/15:00/19:00/21:00
  defaults. Explicit tomorrow bare-hour commands retain the existing morning interpretation.
  Positive relative durations retain backend support. Follow-up resolves the pending reminder
  once without LLM repair.
- Notification keys are seeded on connection. Repeated updates still reach the bridge but
  emit one automation post per active lifecycle. Removal permits another post with a fresh
  opaque occurrence ID. Own-package/group-summary exclusions remain. Routine trigger kinds
  are indexed on configuration updates, avoiding per-notification repository scans.

## Verification

DOGFOOD-006.1 completed the automated and clean-checkout gate below. Physical acceptance remains pending.
No clean startup capture, TTFT experiment or uninterrupted phone acceptance was run,
in this batch. ADB now sees a phone, but the next physical dogfood log is still required.
OEM/debugger/package-replacement noise is outside scope.

## Remaining phone acceptance

1. AUTO + Qwen 0.6B: verify CPU while GPU UNKNOWN, no death, cleared attempt journal;
   compare the same prompt with 256, 512, 1024 and unrestricted history and record TTFT.
2. Five speech turns within 60 seconds: first may load; subsequent four should show loadMs=0.
   Verify recovery to NORMAL after real pressure and restored ASR warm residency.
3. Launch Spotify from foreground, then invoke Halo while Spotify is foreground to open
   YouTube. Check that a background autonomous request remains blocked. Verify platform
   effects separately from dispatch receipts.
4. No active media session must speak rejection, never completion. Check an alarm for seven
   tomorrow morning, complete reminders and incomplete/invalid reminders with follow-up.
5. Soak an updating external notification: bridge updates continue, one automation post per
   lifecycle, second post only after removal/repost.
6. Capture one force-stop/logcat-clear/monkey startup without a debugger, then inspect only
   Halo-owned main-thread violations.

---

# Speech and phone-control stabilization — 2026-10-04

Scope: the latest 25 commits (`5a1578ed` through `4e1bc6e5`), their affected-file history,
existing dogfood reports, and the current production paths for the reported failures.
This is a targeted source audit, not a claim that every feature in those commits was
validated on physical hardware. The working tree initially contained two untracked
LiteRT support files; they were preserved and repaired in place. They must accompany
the other LiteRT changes when this work is committed.

## What went wrong

The history combines fast feature growth with repeated integration repairs. Many different
changes carry the same tool-feature title, and two recent changes are titled only `s`.
Those titles conceal scope: speech setup, model routing, permissions, durable runs,
and UI changes often arrived together. Historical unit-test totals cannot establish
that the current tree compiles or that the physical device works.

The initial verification reproduced Kotlin compilation errors in LiteRT. Four ordinary
functions called suspend inventory resolution; the GPU probe lacked a return; the
qualification store had a malformed multi-statement `when` branch. HEAD also refers to
support classes that existed only as untracked files.

## Verified defects and repairs

| Reported problem | Source defect | Repair and regression coverage |
| --- | --- | --- |
| STT struggles with spoken commands | Explicit capture enters Listening before VAD admits speech. The buffered onset was used only from Idle, dropping the audio collected while VAD decided. | Include the pre-roll at the first admitted frame for explicit listening, once. A delayed-VAD test checks exact sample count and frame order. |
| Phone control becomes a text answer | Native command matching did not normalize “Can/Could/Would you…” requests; flexible volume/brightness requests could become Conversation/CHAT with tools disabled. | Shared anchored request-prefix normalization, conservative action cues, and tests for native commands, flexible actions, explanations, and explicit tool opt-out. |
| OpenRouter describes actions without calling tools | The request builder always sent `tool_choice: auto`, even for REQUIRED initial action turns. | Send required for REQUIRED initial turns, auto for continuations, and request a backend that supports the supplied parameters. Test all three wire shapes, including tool-free chat. |
| ElevenLabs accepts a key but discovery/checks fail | Key persistence only proves local storage. Discovery, authentication, and synthesis are separate. HTTP failures discarded endpoint/status and useful permission classification; a voices failure also hid successful models discovery. | Safe endpoint-specific errors for authentication, missing permissions, quota/rate limit, network and selection failures. Preserve fetched models when voices fail. HTTP tests cover models/voices, PCM synthesis and restricted keys. No user credential was read or sent during this audit. |
| Duplicate Allow all | Two buttons used different implementations: per-skill writes on the UI scope versus a batch policy write coupled to Android access requests. Remembered row policies could also remain stale. | Keep one “Allow all tools without asking” button using the existing batch API on IO, refresh displayed policies, retain separate Android access granting. |
| Confirmation policy regression | `94bf74a0` added a name-based direct-authority shortcut before checking descriptor risk and ALWAYS confirmation. Legacy scope grants also bypassed ALWAYS after one approval. | Apply descriptor/forced-confirmation constraints before direct authority; prohibit scope reuse for ALWAYS. Tests exercise exact digest, risk classes, and repeated approvals. |
| LiteRT crash protection is incomplete | Wrong Android exit-code constants, no caller of crash reconciliation, idle attempts left armed, later resident generations only advanced an absent record, and catalog GPU preference could bypass qualification as if it were an explicit developer choice. | Correct platform mapping, reconcile stale attempts before backend resolution, clear clean load/generation endpoints, arm each generation/teardown, and distinguish catalog preference from explicit GPU override. Tests cover platform constants, artifact/runtime separation, journal persistence and policy. |

The Allow all policy still follows the existing descriptor contract for actions marked
Always confirm. Android permissions remain Android-controlled. No second global policy
control is introduced.

## Follow-up hardening

- ToolRegistry now resolves one binding for each tool name/id. Static sources take
  precedence, then dynamic sources in registration order. Definitions, security metadata
  and executor lookup use the same owning source. Removing a disabled source removes its
  metadata without deleting another source's descriptor. Re-registering a source updates
  its metadata without adding another owner; dynamic dependencies are checked against
  the visible catalogue. Five regressions cover collisions, aliases, removal, updates
  and dependency availability.
- A cancelled buffered decode returns an owned Recognizing capture to Idle and publishes
  its capture-specific failure. A completed transcript already handed off to Thinking
  remains valid. The cancellation regression checks the terminal state and capture id.
- ElevenLabs requests are bound to one configuration version across metadata pages and
  speech chunks. Changing credentials, voice, model or style cancels affected work;
  stale responses cannot return PCM or overwrite the new configuration's diagnostics.
  Voice/model/style changes invalidate synthesis proof while preserving previously
  verified authentication. Duplicate model ids are collapsed, malformed refreshes retain
  the last catalogue and publish a safe error, and the settings page displays provider
  errors even when they originated outside the current button action.
- HTTP regressions cover cancellation during a multi-chunk utterance, ensuring no later
  chunk uses the new key, plus duplicate/malformed metadata and readiness invalidation.
  Explicit `stopCurrent` also propagates cancellation without publishing a network error
  or invalidating previous synthesis proof, preserving the speech coordinator's no-fallback
  cancellation contract.
  Follow-up verification passed; results are recorded below.

## Commit map

| Commit | Scope relevant to this audit |
| --- | --- |
| `4e1bc6e5` | GPU qualification/probe/journal integration; reproduced compilation and recovery gaps. |
| `94bf74a0` | Direct authority in policy authorizer, duplicate Allow all button, permissions screen, asynchronous model selection. |
| `2fe4de61` | Durable journal and skill permission tests; useful coverage but predates newest regressions. |
| `3a34c393` | Declared skill permission set normalization. |
| `fb88f04d` | Global batch approval and Android access flow. |
| `bf12c577` | Tool-derived permission planning and batch policy API. |
| `50c4dedc` | Android permission truth for skills. |
| `3d3e8d51` | Journal codecs, direct authority, run state and continuation repair. |
| `80b6e7a3` | Physical-runtime hardening, image binding, timing and model selection. |
| `cd715f07` | Capability snapshots, ownership indexing, initial direct-command journal work. |
| `db5563e3` | Durable follow-up/watch runtime and database schema 23. |
| `c3fb3c11` | Semantic app bridge, deterministic app operations and external capability boundaries. |
| `3f507ef9` | Host-first native/skill integration, agentic/vision selection and vision lab. |
| `8dcd5adf` | Glasses audio association/route state, TTS UI and ASR residency. |
| `a10d8d4b` | TTS credential durability and sanitized runtime diagnostics. |
| `dba5eb06` | Deterministic action/app resolution, reminder recovery, TTS thread purity and crash artifact removal. |
| `04533dfd` | Assistant tools, platform actions, live schemas and skill activation. |
| `175dd151` | JVM crash/replay artifacts; removed by later repair. |
| `f3bb8708` | Speech models/probes/metrics, provider failure and retry hardening. |
| `32d20ca1` | Workspace/provider ownership, secret handling, settings writes and STT lifecycle. |
| `2d404bc5` | Large mixed feature/UI/provider batch; broad integration surface. |
| `6ae06d44` | Conversation/action routing, provider UX, TTS setup and privacy controls. |
| `940da597` | Provider control center, conversation routing and ElevenLabs PCM integration. |
| `58c2b2a6` | Personal memory and bounded reflection. |
| `5a1578ed` | Event fabric and durable proactive automation. |

## Reference archives

Inspected selected source directly inside `OneBox-main (1).zip` and `zafiro-main.zip`;
neither archive was modified or added as a dependency.

- OneBox's permission requester normalizes/deduplicates permission names. Its tool
  execution context explicitly carries interaction ownership. Halo already has the
  corresponding portable permission and durable interaction contracts.
- Zafiro separates tool confirmation from Android access and ties permission UI to
  activity lifecycle/generation. Its in-memory single foreground confirmation slot
  is not a suitable replacement for Halo's durable run journal.
- Adopted the separation and single-owner approach through Halo's existing contracts;
  no reference implementation was copied.

## External contract checks

- [OpenRouter tool calling](https://openrouter.ai/docs/guides/features/tool-calling)
- [ElevenLabs authentication](https://elevenlabs.io/docs/api-reference/authentication)
- [ElevenLabs voice discovery](https://elevenlabs.io/docs/api-reference/voices/search)
- [Android ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo)

## Validation and physical acceptance

Initial audit verification passed: 366 tests across language, interpretation, speech,
cognition, OpenRouter, ElevenLabs, LiteRT and Sherpa STT, plus 181 app tests:
547 total, zero failures/errors/skips. `:app:assembleDebug` succeeded and produced
`releases/halo-alpha-debug.apk` (227,338,803 bytes).

The broad default-memory run exhausted native JVM memory on this machine. Verification
completed with one worker, no parallel execution, and a 2 GB Gradle heap. Logs are kept
locally under `build/reports/stabilization/`, especially `stabilization-core-tests.log`
and `stabilization-app-tests.log`; the failed run is retained for diagnosis.

Follow-up hardening verification passed in two sequential runs: 19 speech tests,
9 ElevenLabs tests, 181 cognition tests and 181 app tests — 390 total, zero
failures/errors/skips. Nine regressions were added in this pass. The final run rebuilt
the APK with explicit-stop cancellation included. Logs: `polish-verification.log`
and `polish-final-verification.log` in the same report directory. The app composition
audit reported `skills=32 enabled=32 tools=89 orphans=0 unresolved=0 duplicates=0
permissionMismatches=0`. This checks registry/manifest consistency, not physical execution.

No device was connected (`adb devices` returned no devices). Physical STT accuracy,
native GPU stability, live OpenRouter execution and the user's ElevenLabs account
permissions therefore remain unverified. Mocked HTTP success does not establish that
a particular account/key can access the live service.

On device, use the new build and check:

1. Skills: one Allow all button; policies update immediately. Grant needed Android access separately.
2. Say “Could you please set a timer for five minutes?” and “Can you set volume to 30 percent?”
   Compare the transcript with typed execution, so ASR errors and action errors remain distinguishable.
3. Use a flexible command with a tool-capable AGENTIC OpenRouter model. Confirm an actual
   tool outcome in Activity, then a final answer. A plain explanatory question stays CHAT.
4. ElevenLabs: refresh models/voices and test connection. Record the safe endpoint/status
   message if it fails. Select a voice/model and run the phone test before checking glasses playback.
5. Run Speech Lab on one captured sample with Tiny/Base and fixed/Auto language. Choose
   based on measured transcription and latency; this patch does not invent model accuracy gains.
6. Start a long ElevenLabs response, then stop speech. Confirm silence and no fallback
   announcement. Change voice/style, confirm synthesis returns to untested, and run
   another test phrase. Removing or reconnecting an MCP integration should update its
   tools without duplicate entries or stale approval metadata.
