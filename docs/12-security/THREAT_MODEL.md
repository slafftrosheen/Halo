# Security and privacy assessment

## FEATURE-071 / CONTINUITY-001 — 2026-10-08

Phone-hosted conversations now bind each fresh turn and durable run to an exact existing conversation; background commits never activate another conversation. Explicit Continue on… changes delivery only, preserving run/interaction/origin and exact wearable no-phone-fallback policy. Recovery is tracked, missing conversations require explicit rebind, and pending cross-surface input uses the existing atomic first-wins handler. Home projects recent capture metadata into an exact Notebook link; System Check stays passive. No migration, new device owner, driver protocol, inference owner or automatic connection is added. Process death preserves journal truth without surprise execution. Automated and physical acceptance status are recorded in the [implementation report](../engineering/CONTINUITY-001-PHONE-WEARABLE.md).


## FEATURE-069 — source-grounded Study (2026-10-08)

Study now chooses/imports up to four Workspace sources, resolves block-aware notes and bounded UTF-8 text, and sends one explicitly active image/PDF page only to exact verified vision targets under Workspace/cloud privacy policy. PDF support is lazy page-image rendering; PDF text extraction and OCR are not installed. Learner level, support style, independent languages and explicit source-only/general policy persist with per-source versions. Citations follow actual S1–S4 answer markers; model feedback remains unverified. Practice generation produces bounded, source-located drafts requiring Keep, and review preserves original provenance. Material/settings live in a lesson sheet; topic-only lessons remain available.

See [implementation, limits and validation](../engineering/STUDY-SOURCES-002.md). Older source-only/design statements below are historical context.


## FEATURE-065 / MCP-UI-001 — integrations control plane — 2026-10-07

Settings → Integrations and Skills → MCP counts open the MCP server list and
server detail routes. Add/edit, enable/disable, explicit Connect/Refresh tools,
per-tool toggles, credential replacement/removal and integration removal reuse
the existing manager, Room tables, SecretStore and single ToolRegistry overlay.
New servers start disabled. Opening or observing configuration performs no MCP
network request. Current connection state comes from live manager state, never
a stored last-connect timestamp. Cached tool metadata remains inspectable.

Server configuration is not execution authority; trust is not permission;
connection is not tool success; tool enabled is not automatically approved.
Bearer credentials never enter Room, settings or Brain export. Settings never
invokes remote tools. Descriptions remain untrusted data; risk is not editable.
Explicit discovery cancellation reaches the existing client/transport and
registry synchronization removes disabled, replaced or removed live surfaces.

See [implementation and validation](../engineering/MCP-UI-001-INTEGRATIONS.md).


## STABILIZATION-PROVIDER-001 - 2026-09-30

**IMPLEMENTED / UNVALIDATED.** SecretBackedStorage now shares SecretMigrationV2.SECRET_KEYS as the single protected set; ElevenLabs was already included. Compose uses blank replacement drafts and configured booleans. Backup exports use existing non-secret allowlists. Speech telemetry excludes voice prefixes and spoken-text length. Endpoint location metadata no longer marks public Custom/Companion servers as local.

See [source map and deferred acceptance](../engineering/PROVIDER-CONVERSATION-STABILIZATION.md). Build, tests and device acceptance were not run. Earlier dated sections below are historical.


## P8 provider UX and conversation reset — 2026-09-30

General conversational answering is a core Halo capability and does not depend on tool availability. DirectResponse/CHAT is separate from Agentic actions; configuration, authentication, model discovery and generation proof are separate. Speech uses the selected PCM provider, protected ElevenLabs opt-in, phone-only settings tests and host-owned route policy. See [implementation and deferred acceptance](../engineering/PROVIDER-UX-AND-CONVERSATION-RESET.md). Earlier sections retain historical context.


## PRODUCT-P0-001 — source update, 2026-09-30

ElevenLabs keys join the Keystore-protected set and migration version 3. Protected batch writes and the Brain guard prevent plaintext export. Saved key text is absent from Intelligence UI state; replacement drafts clear immediately. Cloud speech receives only TtsSanitizer output, with account-controlled retention. Synthesis fallback cannot relax audio-route policy. New diagnostics contain IDs/counts/enums, never credentials or text bodies.

See the [control-plane source map](../engineering/PROVIDER-CONTROL-PLANE.md) for ownership, official contracts, bounds, security and the later QA plan. Build/tests/device acceptance: not run. Earlier dated descriptions below remain historical.

Status: CURRENT SOURCE MAP / OPEN REQUIREMENTS. Reviewed 2026-09-18.
This is source inspection, not a penetration test or security certification.

| Area | Current evidence | Limit |
|---|---|---|
| Keys | IntelligenceViewModel.setOpenRouterKey -> AndroidStorage SharedPreferences | No application-level encryption on this path; masked text is presentation only |
| Backup | Manifest allowBackup=true | Backup/restore exclusions need review |
| Context | Session/context assembly applies sharing policy | Provider adapters own clients; no universal unbypassable outbound gateway |
| Plugins | In-process registry/dispatch | No security sandbox established |
| Tools | Registration/argument validation | Does not establish user intent, Android permission or physical safety |
| Prompt injection | Typed context/tool boundaries | Sanitization does not prove resistance to hostile instructions |
| BLE | Transport/parser/error-handling code | Does not establish secure pairing for all transports/firmware |
| Diagnostics | Export implementation | “Zero secret leakage” requires actual log/export tests |

Sources: [storage](../../host-android/src/main/java/com/halo/host/android/AndroidStorage.kt),
[key writes](../../app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceViewModel.kt),
[manifest](../../app/src/main/AndroidManifest.xml),
[authorizer](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/InMemoryToolAuthorizer.kt),
[plugins](../09-plugins/SPEC.md).

Required validation: hostile tool inputs, permission revocation, untrusted notifications/web
content, duplicate side effects, backup/restore, credential migration and redacted exports.

## RUNTIME-058 / MINING-P1-001 additions

| Surface | Control now in source | Not established |
| --- | --- | --- |
| Remote MCP servers | Namespaced `mcp.<server>.<tool>` ids, `SENSITIVE` default risk regardless of trust level, bounded untrusted results (`UNTRUSTED_MCP_CONTENT`), per-call timeout + cancellation, credentials only by `credentialRef` into SecretStore | That no hostile server can influence a model through schema/description wording; that every server's auth model is correctly configured by the user |
| Imported remote tool schema | Schema is translated into Halo's closed parameter map; unknown keywords dropped; `additionalProperties = false` | That a schema cannot still steer argument *choice* within the allowed shape |
| Learned procedures | Instruction-only recipes; unknown capability ids rejected at proposal time; nothing persists without the durable approval surface; every step re-passes current policy | That a procedure's *text* cannot be interpreted as authority |
| `halo_create_routine` | `ConfirmationPolicy.ALWAYS`; existing `RoutineExecutor.validate` + `AutomationPermission` gate before saving; SAVED/SCHEDULED/BLOCKED reported separately | That unattended execution is safe for every future tool added to the automation policy |
| Notification reply | Live-action registry only (never persisted), RemoteInput-only, `SENSITIVE` + `ALWAYS`, `DISPATCHED` only | That a reply sent to the wrong live notification cannot happen if keys collide across apps |
| Thermal / resource pressure | Typed thermal policy, release of idle native resources, no silent cloud fallback | Battery/thermal behaviour on real hardware |
| Approval grants | Exact scope + tool + canonical arguments + security mode; denied requests never cached; `ALWAYS`/`SENSITIVE`/`NEVER_REPLAY` never reusable | That a re-prompt is not skipped where a human would have wanted one |
| Run guards | Typed reasons, content-free evidence (ids, counts, digests) in the durable journal | That the thresholds are well-tuned on a real 0.6B device |

Tightened by this batch: an MCP server can never classify its own risk, a learned procedure can never
grant authority, `automaticToolCalling` stays `false` and `HaloOpenApiTool.execute()` still fails
loudly, and no prompt, utterance, tool argument or memory content is written into any new log,
journal step or export field.
Do not mark a threat “handled” merely because a named module or design paragraph exists.

## RUNTIME-059 / MINING-P2-001 additions

| Surface | Control now in source | Not established |
| --- | --- | --- |
| Model-generated presentation | Closed node catalog with no callback, Intent, URI, tool name or fetchable URL; hard depth/node/text/list/comparison/choice ceilings; structurally invalid input rejected with bounded reasons, never truncated | That a hostile payload cannot mislead a *reader* through wording — it can, which is why it is labelled generated content, never authority |
| Model-generated "actions" | Not representable. A `Button`-with-tool-action cannot exist; `ChoiceGroup` resolves only an already durable input request through `PresentationChoiceAuthority` | That option ids can never be spoofed for a pending request (bound to the journal, but unverified on device) |
| Presentation as execution truth | Presentation is a view: approval truth stays in `AgentInputRequest`, execution truth in the `AgentRun` journal. Saving to the Notebook is a **host** action, never a model action | That no future producer treats a rendered card as evidence an action happened |
| Presentation persistence | Bounded payload with an explicit schema version in existing committed-turn metadata and existing provenance metadata; size cap before any write; decode failure degrades to text | Room round-tripping, and that a payload cannot survive into a context window (the assembler reads assistant text only) |
| Image references | Asset ids only; `http:`, `file:`, `content:`, `javascript:` and `intent:` prefixes are rejected outright and only Halo-owned assets resolve | That asset resolution itself cannot be used as an SSRF vector |
| Dynamic WebView extraction | Opt-in, only when static text is under 200 characters; one isolated ephemeral WebView, Halo-owned script only, JavaScript enabled for rendering but no model-supplied script, file/content access, DOM storage, cookies, autofill and password saving disabled, teardown to `about:blank` then `destroy()`, public-network re-validation on every navigation and subresource | That a hostile page cannot fingerprint the extraction, and that no credential can leak from a session-scoped view |
| Extracted web content | Still `UNTRUSTED_EXTERNAL`; it can never become instructions, execute tools, change approval policy or override memory policy | That a summary of hostile text cannot still steer a small model's next *choice* |
| Plan/progress display | COMPLETED can only come from recorded execution evidence (`ToolOutcomeRecorded(success)`, `ToolCompleted`); a claim in prose is unreachable from the projector | That thresholds and wording feel honest on a real device |
| Static preview | Fixed sample data, explicit tap, no model, provider, tool or network call; the sample choice group is inert and unbound | Nothing security-relevant; it exists so rendering can be inspected without a model |

Tightened by this batch: a generated surface can never create capability, and a newer presentation
schema degrades to text rather than being partially interpreted.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

Images, OCR/sign text and recordings are untrusted evidence, not authority for tools, policy or memory writes. Media bytes and host URIs stay outside durable Runtime context. App-private ephemeral cache is interaction-owned, bounded to 64 MiB with 15-minute maximum age and terminal-interaction cleanup; explicit Save/import uses Workspace storage. Image decoding is source-size/sample/pixel/dimension/output-byte bounded; EXIF is applied and re-encoding strips metadata. Global image policy and per-object LOCAL_ONLY / ALLOW_CONFIGURED_CLOUD / EXCLUDE_FROM_AI apply before resolution; LAN counts as off-device. Screen fallback additionally enforces opt-in, package safety and omissions and cannot fall back to cloud. Diagnostics contain media metadata only. Audio first-pass has empty history/tools, duration/byte/output/time bounds and rejects tool calls.

## MINING-P4-001: bounded evidence and delegation

External tool, URL/MCP, payload, retrieved history and child content remain reference data, never SYSTEM authority. Only actual discovery/skill tool outcomes can widen root tool exposure. Opaque run-owned payload IDs authorize access; knowing a SHA does not. Range limits, full-body digest checks, quotas, reference-aware collection and app-private paths bound storage/read exposure. Missing or corrupt bodies return typed failure instead of invented content.

Children receive only host-resolved explicit references and revision checks. Remote dispatch requires every source to permit sharing plus the normal routing policy; selected memories additionally require cloud-memory permission. Children retain their own scoped payload references, inherit no parent approval grants, cannot delegate recursively and expose only read-only no-confirmation fetch/payload/MCP tools. Central execution rejects disallowed writes; requests for parent action carry no execution authority.

Root cancellation cascades to children and active work is watched by parent lifecycle. Child target pins and journal results survive restart; no unattended independent child recovery publishes a response. Diagnostics export budgets/counts/classifications/target status without prompts, summary text or source bodies. Explicit saved Note content/provenance is user workspace truth and follows existing backup behavior; transient payload caches are excluded from portable backup. Android system backup may retain DB metadata while excluding body files; reads then truthfully report missing payloads.

Device/provider/Room acceptance remains unrun; see [ledger](../engineering/LONG-HORIZON-AGENT-MINING.md).

## MINING-P5-001 — screen transaction boundaries (2026-09-30)

Stale-generation or substituted target → exact snapshot id/age/revision/package validation before
resolution and again at dispatch. A comparison fingerprint is never authority. All dispatched actions
kill the original token generation. Old approvals cannot authorize materially changed targets.

Malicious screen text → explicitly untrusted bounded projection. Exposure directives are parsed only
from trusted discovery/skill results; screen text cannot activate a skill, lower risk or grant approval.
Entered text → transient comparison, bounded expiring draft cache for exact same-process continuation,
length/digest-only durable tool arguments, no field values in previews/fingerprints/diagnostics.

Interrupted UI effect → NEVER_REPLAY; missing claimed receipt remains UNKNOWN, then fresh observation.
Checked state can reconcile without a click. Blocked destination → reject before traversal and manual
Continue surface. Consequence hints elevate confirmation and never weaken descriptor policy.

Coordinate taps, shell input, root, Shizuku, Xposed, privileged UiAutomator bridges, permission-dialog
automation, CAPTCHA solving, password/banking/authenticator control and background surveillance are
rejected. Screenshot vision remains observation only. [Contracts and residual uncertainties](../engineering/SEMANTIC-PHONE-OPERATOR.md).
Source review is not device acceptance; build/tests/device acceptance not run.

## MINING-P6-001 — proactive automation runtime (2026-09-30)

Automation threats: forged external events must never become instructions or capability grants. Only audited descriptor opt-ins run; always-interactive actions remain blocked. Canonical grants bind trigger/conditions/tool ids/arguments/delivery/cooldown; missing/tampered/edited grants block both action dispatch and disruptive delivery. Notification signals contain no body/title/sender and cannot select CRITICAL attention or predicate execution. No credential values, background permission dialogs, external auto-replies, raw event histories or implicit cloud fallback are introduced. Queue restoration rechecks policy and durable delivery claims prevent replay; receipt retention is 30 days.

See [Proactive automation runtime](../engineering/PROACTIVE-AUTOMATION-RUNTIME.md).

## MINING-P7-001 — personal intelligence and reflection (2026-09-30)

P7 threats include sensitive inferred facts, secret retention, forged evidence, cross-project recall, prompt injection through reference data and unsolicited learned side effects. Boundaries: exact supplied evidence validation, active scope before SQL LIMIT, reference-as-data framing, local-only optional reflection, secret rejection/legacy recall exclusion, inferred sensitive/unknown rejection, explicit confirmation for durable tool writes, never-replay semantics and user review for procedures/routines. Lexical/category validation is not semantic proof; later adversarial acceptance remains necessary.

See [P7 implementation ledger](../engineering/PERSONAL-INTELLIGENCE-REFLECTION.md).

## FEATURE-067 / VOICE-UX-002 — Conversational voice

Voice settings persist spoken interruption (default on), Quick/Natural/Deliberate
timing, early speech (default on), and Concise/Natural/Detailed spoken answer style.
Smart interruption remains Not installed. Explicit microphone/assistant/wearable
activation always interrupts actual playback; ordinary speech requires a valid
active capture and sustained VAD evidence. Acceptance stops old output and cognition
without stopping successor capture. No microphone monitoring service was added.

The existing speech playback owner streams only exact, fresh, tool-free terminal
responses from streaming-capable targets. It sanitizes complete sentence chunks,
pins synthesis selection and route once, holds focus/lease for the response, and
emits one audible start and one terminal event. Short answers use final-response
playback. A digest reconciles the queued prefix against committed final text; a
mismatch stops rather than replaying. Stop/cancellation clears queued chunks and
suppresses late final requests. Route loss stops remaining audio; visual truth
remains committed. Phone fallback is off for external-origin replies unless the
existing setting explicitly permits handoff. Typed requests remain TEXT.

Voice, Skills and System Check expose configuration and process-only speech/route
status without initiating capture, inference or playback. Cloud speech receives
only sanitized answer chunks, never hidden reasoning, tool data, prompts or input
context. Audio and partial tokens remain process-only; Android TTS remains default.

Automated verification: 585 tests, zero failures/errors/skips; debug APK assembled.
The APK installed and launched on the development phone. One synthetic Home RAM
request resolved the configured OpenRouter target and failed with HTTP 429 before
any token/audio; the prepared stream cancelled. No provider/model/fallback setting
was changed. Physical early speech, microphone interruption/backchannel, Stop
during playback and wearable routing remain unverified; no repeated request was run.
See [implementation notes](../engineering/VOICE-UX-002-CONVERSATIONAL-SPEECH.md).

## FEATURE-068 / MEMORY-UX-002 — Personal Memory control center

Workspace includes Memory at `notebook/memory`, with saved/pending counts, the
existing conversation-memory policy, local debounced shared lexical retrieval,
kind/scope filters and browse sorting. Opening the hub does not mark memory used.
Expired records remain browsable as Needs review; current search/context retrieval
keeps existing expiry and scope rules. Semantic retrieval remains not installed.

Manual creation and note promotion review content and exact source revision before
conflict detection. Reinforcement and conflicting replacement require explicit
review; secrets are rejected through existing repository contracts. Imported
confirmation reinforces approval while preserving origin, content, scope,
confidence and expiry. Correction/forgetting use canonical revision/tombstone,
Workspace trash and Working Set detachment paths. No new schema or memory owner.

The existing detail screen presents human provenance, scope, actual usage, genuine
Workspace source links and collapsed history. Approval is separate from original
source; usage is context admission, not causal proof. Suggestions remain pending
until accepted through PersonalLearningRepository. Procedures, automations and
capability setup keep their existing review destinations. Episodes are Learning
history, distinct from saved facts and transient working memory.

Learning modes remain Off / Explicit only / Suggest, with Explicit only the default
and local reflection opt-in. Memory use is independent; cloud permission and
retrieval budgets/ranking are unchanged. Notebook documents remain separate.
Automated gate: 216 cognition + 283 app tests (499 total), zero failures/errors/skips;
debug APK assembled. ADB had no connected device; the single manual Memory sanity
pass remains pending. No physical acceptance is inferred from the automated gate.
See docs/engineering/MEMORY-UX-002-CONTROL-CENTER.md for validation and limitations.
