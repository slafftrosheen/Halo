# Interface 4.0 Screen configuration tool parity — Batch E2

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

Issue #62. Source batch based on checkout
`045f98506f30797974311fe27e80645e0cf20d01`; integration owns the final commit,
registration and validation evidence. This follows the
`AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md` and v4 user-facing contract.

## Canonical owner and action mapping

The assistant adapter is `ScreenToolExecutor(LocalScreenStore, allowed)`.
The existing `LocalScreenStore` remains the only persistence owner; no
separate Screen runtime, preview history or source store is introduced.
`withToolState` holds the owner's existing lock for observation, revision
validation and the same mutation methods that the UI uses. Existing settings
setters now use this lock too, so manual settings cannot race a tool guard.

| Typed tool | Manual equivalent / existing owner method |
|---|---|
| `halo_screen_state` | Read saved layout, appearance, settings and preset metadata; optional native widget filter |
| `halo_screen_preview` | Customize / Ask halo local layout preview; does not persist |
| `halo_screen_apply` | Apply preview through `applyDraft`; initial starter through existing `saveLayout` |
| `halo_screen_settings` | `setPrivacy`, `setKeepCharging`, `setDimAtNight`, `setTopics` |
| `halo_screen_undo` | Existing `undo` layout history; does not undo settings or source content |
| `halo_screen_save_preset` | Existing `savePreset` of the saved layout, including its same-name replacement behavior |
| `halo_screen_remove_preset` | Existing `removePreset`; preserves current layout and source records |

Native preview changes cover add/remove, reorder, directional move, card
size and grid resize, pin/archive, card typography, appearance, starting
layout and saved preset selection. Add uses `ScreenLayoutRequests`' native
whitelist and empty configuration. Reorder/move/resize use `ScreenGrid`, as
the manual touch/keyboard controls do. Appearance and card fields use the
same native immutable layout types and bounds as the UI. At most 24 changes
and 24 cards are admitted; presets remain capped at 12 by their owner.

Cards retain their existing source references and presentation identities
through edits. Removing a card or replacing a layout never deletes or
changes a source. Source titles, bodies, IDs, citations, arbitrary card
configuration and presentation IDs are never returned by these tools.
Responses include only bounded layout/settings metadata: zero-based card
indices, registered type, size, flags, typography, placement and whether a
source/configuration exists. Topic text (≤1,000 characters), preset names
(≤64 characters) and layout name are personal configuration and require
the existing personal-source policy even though source bodies are absent.

## Typed contract and revision safety

Argument objects and nested change/settings objects reject unknown fields.
The executor additionally validates the exact per-operation field set,
native enums, integer ranges, input types and text bounds. It rejects HTML,
JS, arbitrary widget/config fields, fractional indices and external font URLs.
It never creates research summaries, fetches topics or fabricates cards with
content. Unconfigured weather/interests/research remain native setup/empty
presentations under their existing source owners.

The returned revision is a SHA-256 fingerprint of saved layout, undo layout,
settings and presets, computed under the owner's lock. Source item bodies
are excluded. Every proposal or mutation requires the exact observed
configuration revision. Manual layout, settings or preset changes invalidate
it. Preview returns a deterministic `previewToken` bound to that revision
and the complete native draft. Apply recomputes the draft from identical
typed changes and rejects a changed/tampered token before saving. The token
is an integrity check; it grants no permission or confirmation authority.
Configuration equality after equivalent undo cycles can return an earlier
fingerprint, so mutation descriptors must still prohibit automatic replay.

There is no ephemeral preview ID to reconcile after process death: clients
read fresh state, preview the explicit changes again and obtain any required
approval. An uncertain mutation is never replayed to obtain a success result.
Mutations flush this same SharedPreferences owner's queued `apply` writes
using a checked synchronous `commit` under the same lock on `Dispatchers.IO`.
Only a successful commit returns CONFIRMED with `persisted=true`; commit
failure returns `Failed(completionUnknown=true)` and prohibits replay. Reads
and pure previews do not commit. Reopened-owner regressions check the saved
layout/settings readback. There is no new persistence backend or test-only
failure injection seam; disk-failure/device process-kill acceptance remains
NOT_RUN. A confirmed configuration write does not establish that an Android
surface rendered, speech worked or OS permissions changed. Existing undo
semantics are retained.

## Central integration contract

`ScreenOperabilityContracts.forDefinition` supplies version-1 contracts with
owner `LocalScreenStore`, JSON results and exact `revision` source guards.
It reuses already-published **workspace.read / workspace.write** capabilities,
avoiding a new unsupported device capability. The two read tools are QUERY;
save-preset is CREATE; other mutations are EDIT. Both typed intent and STT-final
voice intent must resolve through the existing ToolRegistry/CognitiveSession.

The root integration owner must wire all seven definitions into the existing
executor list, central descriptors, discovery/skill catalogue and source
policy. Required metadata:

| Tools | Risk / confirmation / replay |
|---|---|
| state, preview | READ_ONLY / NONE or existing personal-source consent / SAFE |
| apply, undo, save-preset, remove-preset | Existing local-mutation risk and USER_POLICY approval / NEVER_REPLAY |
| settings | SENSITIVE / ALWAYS confirmation / NEVER_REPLAY |

The required `allowed` callback must retain the current loaded Kids and
runtime-policy admission; there is no permissive default. The executor
checks it before reads and again under the mutation lock. Existing runtime
timeout/cancellation, source dispatch consent and permission policy remain
outside this configuration adapter. Remote/Power exposure must additionally
pass the **WORKSPACE personal-source category** because results include
topics, saved preset labels and layout metadata. Configuration mutation
does not imply permission to read any referenced Notebook, Calendar, routine,
research, device or other source, nor permission to send it remotely.

Disabling `hidePrivate` while it is enabled also requires a nonblank
`ToolExecutionContext.interactionId` supplied by the runtime. Settings remain
ALWAYS/SENSITIVE and the existing ToolRegistry/PolicyToolAuthorizer durable
approval is the authorization boundary. The interaction ID scopes execution;
it is not a replacement approval mechanism. The direct-authority fast-path
flag is deliberately false for ALWAYS/SENSITIVE tools even after approval,
so it is not used as this operation's gate. An argument such as
`confirmed=true`, document content or the preview token cannot mint execution
context or bypass approval. `ToolPreflightProvider` supplies a content-free
approval preview: “Show private details in full screen” and “People nearby
may see personal and private cards”. It does not dispatch an action or expose
topic text, source bodies or IDs.
All settings validate before any setter runs, preventing partial mutation
from a later invalid field. Privacy defaults remain enabled. Nothing enables
Full Power, cloud sharing, microphone, overlay/full-screen mode, stay-awake OS
permission, Calendar permission or provider access. The keep-charging toggle
is the existing preference, subject to the existing host/window policy.

## Explicit ownership exceptions

Opening/rendering native screens, changing full-screen/window/lock state,
capturing speech and granting OS permission retain their existing presentation,
host and security owners. Source actions such as reading a card aloud, opening
its original item, saving grounded research or refreshing a provider retain
their existing source tool/receipt/policy routes; these configuration tools
cannot imitate those actions by returning invented source content. Physical
dragging is covered through the existing equivalent reorder/move/resize
commands; no synthetic pen/touch gesture is required for those semantic edits.

## Regression sources and acceptance

`ScreenToolExecutorTest` supplies eight regressions authored with the adapter:
bounded metadata/no sensitive source leakage; preview versus real native
packing/apply/readback; stale layout/settings/preset and tampered token rejection;
scoped approved-context admission and validate-before-write; content-free
privacy consequence preview; presets/starting layouts/undo;
native whitelist/integer/markup rejection; and actual ToolRegistry descriptor
binding plus versioned operability review. The root must run the central
registered-feature review after its wiring; fixture descriptors alone are
not proof of application registration or runtime policy.

No build, install, device run or commit was performed by this batch owner.
Production/source regressions: **NOT_RUN** until the integrated checkpoint.
Suggested filter: `com.halo.app.di.tools.ScreenToolExecutorTest`, alongside
the existing `com.halo.app.screen.LocalScreenStoreTest` and Screen layout/grid
regressions to protect the shared owner behavior.

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable readback |
|---|---|---|---|---|---|
| Read state → native reorder/resize preview → exact apply | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Settings privacy review → guarded update → state readback | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Save preset → different starter → preset preview/apply → undo | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

Source/desktop tests, rendered native UI, real spoken recognition and permitted
provider acceptance remain separate. This document does not close issue #62.
