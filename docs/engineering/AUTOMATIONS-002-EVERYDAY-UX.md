# AUTOMATIONS-002 — Everyday automations

FEATURE-066, based on `4414986943a08e4579e5ae4f219904230dbf7eb9`.

Automations is a user-facing workspace with Routines, Follow-ups and History.
Trigger selection groups Schedule, Device, Apps, Battery & power and Network.
Time/date pickers and day shortcuts use the existing LocalTime/Instant/day-set
contracts; the optional time-zone selector uses ZoneId. Cards project the
persisted nextRunAt, exact grant and source readiness. They do not compute a
second schedule. App labels are loaded through the existing resolver and cached
package-label projection; exact package names remain authority. Saved device
names are presentation only and missing devices are never substituted.

## Action and delivery boundary

`halo_routine_message` is the safe general alert primitive: a closed `text`
schema, trimmed 1–240-character plain text and an ordinary ToolExecutor Success.
It has no Android, network, model or external-action dependency. Text resembling
HTML, a URI or a tool call stays inert text. Its descriptor is READ_ONLY,
confirmation NONE, replay SAFE and explicitly ALLOWED_WITH_ROUTINE_GRANT,
background compatible, persistable and model-free. The ordinary registry and
existing worker composition share the executor/descriptor contract. The
`halo.automation` skill includes it and the existing skill enabled/approval
settings still gate unattended execution.

The audited action set remains message plus deterministic notification digest.
Phone control, timers/alarms, notification reply/dismissal, screen controls,
calendar writes, message sending and arbitrary MCP tools have no added automation
permission. MCP trust does not supply automation permission.

Routine delivery owns notification and speech. Choices explain Silent Activity,
Android notification, foreground Halo and one selected wearable with no phone
speaker fallback. Notification delivery checks host notification readiness;
wearable selection checks the saved device speaker capability and current
presence. Actual routing/delivery receipts remain authoritative; connected does
not prove audible playback. Missing notification listener/screen service setup
is surfaced on cards and linked explicitly from the editor. Opening the screen
requests no permissions, runs no inference and executes no actions.

## Review, saved commands and history

Templates (battery below 20%, network returns, selected device connects, evening
8-hour digest at 18:00 system zone) only prefill disabled unapproved drafts. The
device template requires explicit device selection. Duplicate preserves trigger,
all conditions including repeated conditions, actions, delivery and cooldown,
but assigns a new identity/time and clears grant, lastRunAt and nextRunAt.
Neither path persists or schedules before reviewed save.

Review uses RoutineExecutor.validateForSave and grantSummary; save retains the
existing exact RoutineArguments.grant and WorkManager replacement. Changing the
message invalidates the grant. Save off / Save & turn on are explicit. Editing
an enabled automation may keep it on after review. Add/remove condition rows
retain every existing domain condition unless explicitly removed. Generic
simple-schema action editing remains behind the known message/digest editors.
Unknown serialized future conditions still fail closed under the existing codec;
this batch does not change schema compatibility or invent a fallback decoder.

Run now appears on saved detail pages, never in draft execution. Confirmation
previews actions/delivery, then delegates the saved id to RoutineExecutor with
the existing manual occurrence. It remains on the automation surface with
running state and durable routine-run evidence. Delete confirms first, then
removes the definition and cancels its existing unique schedule. History lists
human status, actions and bounded details from existing routine_runs. Notification
DISPATCHED means dispatched, not physical receipt. Interrupted execution and
uncertain delivery remain uncertain. AgentRun journal details stay in Activity.

Follow-ups remain separate ContinuationWatch objects with typed human condition,
status, created/expiry and behavior projections, linked Activity and explicit
cancel confirmation. No watch-to-routine conversion or second journal is added.

No database migration, new scheduler, trigger-semantic changes, automatic learned
routine creation or expansion of unattended authority is included.

## Validation

Focused tests cover message bounds/inert output/closed schema, audited action
metadata and dangerous-tool exclusions, draft templates, cleared duplicate
identity/authority with retained conditions, message grant invalidation, saved
manual execution, deletion cancellation and human presentation truth.

Consolidated serial gate: `:runtime:cognition:test :app:testDebugUnitTest
:app:assembleDebug --no-parallel --max-workers=1 --no-daemon`.
Final gate passed: 466 tests (212 cognition + 254 app), zero failures, errors or
skipped tests, and debug APK assembled. Nine focused everyday-automation tests
are included in the app total. The pure automation tests explicitly use legacy
Robolectric graphics/SQLite; existing native vision and database tests remain
unchanged and pass in the full suite. `git diff --check` passed.

Compact physical sanity remains pending: `adb devices` showed no connected
phone. No notification delivery, wearable playback or physical schedule
execution is claimed from JVM tests or APK assembly.

Manual sanity: create Network is back, review, Save & turn on, inspect active
card, disable/enable, Run now, inspect history, delete. Create the daily digest
from its template and inspect actual scheduled nextRunAt. No AI provider is
required. This is a compact sanity run, not a new acceptance campaign.
