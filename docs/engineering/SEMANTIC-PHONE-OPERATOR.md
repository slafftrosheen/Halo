# Semantic phone operator — MINING-P5-001

Status: SOURCE COMPLETE; build, tests and device acceptance NOT RUN. Updated 2026-09-30.

Actual baseline: `12a31947a732790f1e6f8418c5b5aade831aa321`. The pasted request named
`faca1ae45cb1fc3ac8d8f7dee5836244e6deebc6`; local main already includes the P4 batch and
the subsequent migration/registry fixes. Those changes are preserved. This batch adds no
Room tables or migration: transaction payloads use the existing AgentStep table, step schema 7;
the existing Room database remains version 20.

## One execution path

OBSERVE → TARGET → PREFLIGHT → AUTHORIZE → DISPATCH → SETTLE → VERIFY.

`HaloAccessibilityService` captures a bounded semantic tree on demand. Accessibility events
advance `uiRevision` without collecting screen contents. Snapshot identity, age, revision,
foreground package and package policy must all validate. A token is actionable only in that
exact generation. A fingerprint compares evidence across generations; it never authorizes a
new target, substitutes a token or restores permission after restart.

`UiTargetResolver` resolves an emitted token to a typed target and failure. Traversal and
platform dispatch run on Main without a suspension between their last validation and action.
Every dispatch attempt invalidates its original snapshot immediately. An already-satisfied
checked setter also invalidates its old snapshot and returns a fresh observation.

`AccessibilityTools` implements optional `ToolPreflightProvider`. Both HostTools aggregators
forward it. ToolTurnExecutor validates descriptor/schema/exposure first, then performs
preflight, then invokes the existing durable PolicyToolAuthorizer. Preflight has no platform
side effects. It supplies an exact intent and a bounded semantic preview using PackageManager
app identity. Resolved consequence hints only elevate authorization. Delete, uninstall,
purchase-like and communication actions cannot gain permission from a permissive skill policy.
Permission and recognized CAPTCHA controls require manual takeover. Dispatch revalidates after
approval, resource acquisition and the durable ACTING record.

Set-text values exist only in transient execution context. Provider tool-call and prepared
invocation arguments are journaled with length/digest instead of text. A bounded process-local
cache can restore the exact same draft for same-process approval continuation; it admits at
most eight entries, expires after 60 seconds, and removes an entry after an action attempt.
Snapshot age remains limited to 30 seconds. Process death cannot restore the text or authorize
an old token. Editable previews and fingerprints use platform hints/view identifiers instead
of field values. Diagnostic exports contain no values, labels, screen trees or tokens.

## Action-specific evidence

| Operation | Confirmation evidence | Conservative fallback |
|---|---|---|
| Set text | Unique matching editable target exposes its full bounded value; normalized whitespace/trim matches the transient intended value | Truncated, absent or hidden value stays unconfirmed |
| Set checked | Non-sensitive enabled checkable target equals the requested Boolean after settling | Click failure or unreadable state cannot confirm; already-satisfied state performs no click |
| Activate | Requested bounded effect: package/root navigation, dialog transition, disappearance, or target state/focus change; navigation can include a selected tab change | Unrelated text or animations do not confirm |
| Scroll | Selected container has changed row keys with directional shared-row movement or consistent collection indices | No row evidence, ambiguity, unchanged content or unverified movement stops bounded search |
| Back | Package/root navigation or dialog dismissal | Platform Boolean alone is dispatch |
| Home | Package changes to the PackageManager-resolved default launcher | Missing exact launcher evidence stays unconfirmed |

`UiPostconditionVerifier` produces typed `UiActionReceipt` with dispatch, verification, ActionTruth,
source/destination package, before/after snapshot identity, content-free change summary,
settle status/time, failure and V1 protocol. It does not infer success from English tool prose.
Quiet windows and deadlines are action-specific: normal setters/scroll use 180 ms observation
and quiet delay / 1500 ms ceiling; activate/back/home use 250 / 2000 ms. SETTLE_TIMEOUT preserves
uncertainty rather than turning Android's action return value into confirmation.

## Bounded search and disclosure

Find ranks exact label, exact description, view-id tail, prefix, substring and role in order.
Role, region, interactive and editable filters are optional. Results are limited to 20;
region/depth/tree order stabilizes output but never breaks an equal-best semantic ambiguity.
Ambiguous destructive matches require takeover; ordinary ambiguities require refinement.

`BoundedUiSearchCoordinator` is a portable orchestrator wired through the explicitly described
`halo_ui_search` tool. Its approval advertises at most three forward scrolls in the selected
container. It uses normal scroll preflight, exact-generation dispatch, settle and verification
contracts. Each internal scroll is admitted by the parent run guard and has its own durable
transaction id/receipt. It cannot navigate, switch containers, bypass package policy or continue
after an unverified scroll. Its parent invocation is NEVER_REPLAY, including after partial progress.
The helper is not an unrestricted or hidden scroll-until-found action. Typed FOUND/NOT_FOUND/AMBIGUOUS
metadata is journaled for completion summaries without parsing tool prose.

The existing ToolExposurePlanner keeps screen tools out of bootstrap. Loading the screen skill
initially exposes read/find; live inspectable exact-generation evidence enables applicable
click/text/checked/scroll/global/wait/search definitions. Dedicated native phone capabilities
remain the preferred path. No registry-wide mutation or exposure directive from screen content
is accepted. Ordinary screen output uses one untrusted header, a bounded node projection,
an end delimiter and a next directive. A bounded read-only `halo_ui_wait` observes one of four
conditions for at most five seconds and never acts.

## Durable workflow and recovery

UiTransactionRecorded is an additive AgentStep variant. UiWorkflowState and completion summaries
are projections of that existing journal. No new workflow database or execution-truth store exists.
Phases are OBSERVING, READY, WAITING_FOR_APPROVAL, ACTING, SETTLING, VERIFYING,
WAITING_FOR_MODEL, COMPLETED and FAILED. Actual admission is bounded centrally by P1 limits:
12 actions, 6 scrolls, 3 identical semantic actions per run, with existing wall-clock, thermal,
round and oscillation guards still applying. ACTING admission counts survive restart; these conservatively count attempts, while dispatch metrics
count actual platform dispatches. Semantic signatures
use fingerprint + operation + argument digest, excluding rotating snapshot ids and tokens.

Unclaimed same-process continuation may use a still-valid generation and exact approved argument
digest. Claimed UI actions are never replayed. A recorded receipt remains evidence; missing receipts
produce UNKNOWN for claimed actions and OBSERVATION_REQUIRED. Fresh set-checked state can be
reconciled observationally without a click. Incompatible protocol records fail conservative journal
decoding, interrupt recovery and cannot restore actionability. Old approvals cannot authorize new
tokens or materially changed intent.

Blocked packages are rejected before destination traversal. Unsupported/unreadable screens,
recognized CAPTCHA/permission controls and ambiguous destructive targets use the existing durable
clarification surface: “Please complete this step yourself. Halo can continue afterward.” Continue
reacquires a fresh semantic observation; no coordinate guess or hidden replay follows it.

## User surfaces and diagnostics

- Skills → Phone screen control shows service, reading/action policy, enforced sensitive-app rules,
  unsupported input mechanisms, last observed app/inspectability and last transaction. Policy labels
  are Ask before screen actions / Allow reversible screen actions / Never allow screen actions;
  mandatory descriptor confirmation and consequence elevation still apply.
- Full and compact Assistant approvals share the existing InteractionSurface confirmation renderer
  and the same semantic app/action/target/role/region/character-count preview. No snapshot or raw JSON
  is rendered in an approval.
- Home uses the existing P2 checklist renderer with journal-derived phase, preview and action counts.
  Activity shows a compact semantic summary and expanded safe transaction details. Unconfirmed effects
  stay unconfirmed in Activity, the final response and TTS.
- System Check adds passive operator contract rows/counters and explicit Read current screen / Preview
  snapshot controls. Opening a screen or exporting diagnostics never captures it automatically.
- PhoneControlSandboxScreen is reached from System Check using the existing app navigation. Counter,
  switch, input, a 50-row semantic collection, dialog and local details navigation have no external
  effects; its expected-state panel reports local state. It is never sent to a model automatically.
- UiOperatorDiagnostics forwards nine content-free counters to the existing RuntimeMetricsSink:
  reads, dispatches, confirmations, dispatched-unconfirmed, unknown, stale, ambiguous, blocked and
  settle-timeout. Structured HaloUi logs contain only typed facts and bounded identity prefixes.

## Restrictions and deferred work

Coordinate taps/swipes, long press/drag/pinch, shell input, root, Shizuku, Xposed, UiAutomator
privilege bridges, permission-dialog automation, CAPTCHA solving, password entry, banking and
authenticator workflows, unrestricted Settings automation, autonomous payment/purchase/account
confirmation and background screen surveillance remain outside this batch. Screenshot vision is
observation only and grants no coordinate action capability. Draft typing and Send/Post/Publish
remain distinct authorized operations.

## Reference harvest

- [Brownie BrowserTool](https://raw.githubusercontent.com/natanloterio/Brownie/refs/heads/master/core/tools/src/main/java/me/loteiro/brownie/core/tools/BrowserTool.kt): quiet-window/deadline and bounded-output concepts adapted to accessibility; browser runtime not copied.
- [Brownie MessagingTool](https://raw.githubusercontent.com/natanloterio/Brownie/refs/heads/master/core/tools/src/main/java/me/loteiro/brownie/core/tools/MessagingTool.kt) and [NotificationTool](https://raw.githubusercontent.com/natanloterio/Brownie/refs/heads/master/core/tools/src/main/java/me/loteiro/brownie/core/tools/NotificationTool.kt): dedicated reply/bounded observation preferred; dispatch is not delivery proof.
- [Android-Agent AgentRunner, harness-improve](https://raw.githubusercontent.com/Rickeal-Boss/Android-Agent/refs/heads/harness-improve/core-agent/src/main/java/com/rickeal/agent/core/agent/AgentRunner.kt): exact-argument approval, bounded execution and committed failure evidence reviewed. Halo keeps schema validation before approval, its own journal and its existing oscillation detector. No reference code or additional runtime imported. An initial guessed core/agent path failed; the actual core-agent path above was inspected.
- Existing OneBox/Zafiro mining and Phone Acceptance documents were reviewed before updating this ledger.

## Later stabilization — not acceptance claims

No compiler, Gradle, test, lint, adb, emulator, device or benchmark was run. Basic source checks
cover internal imports/named-constructor arguments, JSON parsing, manifest/tool/risk parity, sealed
journal branch review, Git whitespace and local branch/worktree inventory. These do not establish
compilation or Android behavior.

Physical validation is still needed for event granularity, Compose semantic hints/row indices,
same-process approvals near expiry, nested list evidence, animations/quiet deadlines, manufacturer
launcher resolution, dialog/root identity, keyboard mutations, process death at every journal
boundary, compact Assistant interaction and blocked cross-app transitions. Ambiguous fingerprints,
missing collection metadata, identical structural roots, hidden/truncated editable values and
unavailable launcher identity deliberately remain DISPATCHED/UNKNOWN rather than false confirmation.
Native-tool preference is conveyed through disclosure/descriptors/skill instructions; model behavior
on the smallest provider still needs later acceptance. The sandbox’s local page transition can be
unconfirmed when Android exposes no strong navigation evidence.

## Production file inventory

The lists below name every changed/new production file in this batch. Documentation updates are
CURRENT-STATE plus Architecture, Features, UI status, Threat Model, Phone Acceptance, Roadmap,
Reference Harvest, OneBox/Zafiro and this ledger.

### Changed production files (36)

- `app/src/main/assets/skills/halo.phone.screen.json`
- `app/src/main/java/com/halo/app/data/agent/AgentRunRepository.kt`
- `app/src/main/java/com/halo/app/di/InfrastructureGraph.kt`
- `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`
- `app/src/main/java/com/halo/app/di/tools/HaloToolDescriptors.kt`
- `app/src/main/java/com/halo/app/di/tools/HostTools.kt`
- `app/src/main/java/com/halo/app/di/tools/host/AccessibilityTools.kt`
- `app/src/main/java/com/halo/app/di/tools/host/HostToolRegistry.kt`
- `app/src/main/java/com/halo/app/diagnostics/DiagnosticsExport.kt`
- `app/src/main/java/com/halo/app/ui/screens/activity/ActivityScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/AndroidAssistantScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SkillsScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckViewModel.kt`
- `app/src/main/java/com/halo/app/ui/shell/AppShell.kt`
- `app/src/main/java/com/halo/app/ui/state/RuntimeUiFormatters.kt`
- `host-android/src/main/java/com/halo/host/android/accessibility/AccessibilityActions.kt`
- `host-android/src/main/java/com/halo/host/android/accessibility/AccessibilitySnapshotFormatter.kt`
- `host-android/src/main/java/com/halo/host/android/accessibility/HaloAccessibilityService.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunGuard.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunJournal.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/AgentRunLimits.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/agent/recovery/AgentRecoveryPlanner.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/interaction/PlanPresentationProjector.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/skills/SkillInstructionSource.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/skills/SkillRegistry.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/LocalModelToolPolicy.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/PolicyToolAuthorizer.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolExecutionResult.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolExposurePlanner.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/AgentRecoveryExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurnExecutor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/CognitiveTurnPreparer.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ToolTurnExecutor.kt`
- `sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/HostInvocation.kt`

### New production files (7)

- `app/src/main/java/com/halo/app/data/agent/UiTransactionCodec.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/PhoneControlSandboxScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/PhoneOperatorPanel.kt`
- `host-android/src/main/java/com/halo/host/android/accessibility/UiTargetResolver.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolPreflight.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/ui/BoundedUiSearchCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/ui/UiOperator.kt`
