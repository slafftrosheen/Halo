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
