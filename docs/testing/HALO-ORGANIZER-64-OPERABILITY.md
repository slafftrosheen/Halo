# Organizer issue 64 operability record

Uses [AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md](AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md). Source base `b381c6834`; branch `codex/issue-64-organizer`. Runtime, test and device evidence are NOT_RUN while the user repairs host power. No issue closure or release acceptance is implied.

## Feature and canonical owners

UI and assistant use `OrganizerOperations` / `OrganizerUseCases` and the same `RoomOrganizerRepository`. The existing `RoutineExecutor`, durable agent journal, `RoomRoutineRepository`, `CollectorCoordinator` / `RoomCollectorRepository`, brief service and attention owner retain their responsibilities. No parallel commitment or execution store exists.

| Registered descriptor | Operability capability / operation | Functional coverage |
|---|---|---|
| `halo_commitments_query` | v1 `workspace.read` / QUERY | Exact ID or bounded section/owner/text page; actual installed capability argument schemas; revalidated result availability |
| `halo_commitment_save` | v1 `workspace.write` / EDIT | Create/update local commitment definition; explicit owner/purpose/intent, selected source versions and capability plan; no execution on save |
| `halo_commitment_action` | v1 `workspace.write` / EDIT | PREPARE, ARM, RUN_NOW, STOP, TAKE_OVER, DELEGATE, MAKE_SHARED, WATCH, Inbox PROMOTE, shared ACKNOWLEDGE and stopwatch START/PAUSE/LAP/RESET |
| `halo_research_query` | v1 `workspace.read` / QUERY | Bounded retained-evidence search; zero external request; dates, citations, actual publisher, stale status and computed lexical relevance |
| `halo_research_source` | v1 `workspace.write` / EDIT | Create/edit/select explicit free research query/provider, exact old source version; no fetch on save |
| `halo_organizer_perform` (internal only) | v1 `workspace.write` / EDIT | One exact reviewed step under the existing claimed routine run; never exposed to conversational provider tool lists |

Existing registered operations retain schedule/recurrence/item editing, task/event/reminder/alarm/timer creation, occurrence changes, deletion/undo/calendar export, source refresh/remove/cache controls, clocks, brief composition/read-aloud and launcher entry behavior. Android permission and widget placement dialogs remain explicit human/system security boundaries. Voice does not bypass them.

Production registration: `ManualDependencyGraph` adds the five public commands to host executors, the internal command to the automation registry, and uses the existing descriptor builder. `halo.everyday` asset/discovery/default skill metadata contains all 32 public everyday operations. `PersonalConfigurationToolAccess` includes the new commands, with Workspace plus location/recent-event/device disclosure categories. `OrganizerOperationsTest.registeredCommitmentCommandsUseTheSameOwnerAndClosedReceipts` authors the real registry operability report assertion; actual output NOT_RUN. Manifest parity assertion updated to 80 configuration commands, with the two new queries read-only.

Discovery examples: “capture Friday buy material”, “delegate to Halo check the order”, “create shared task choose a printer”, “watch for a reply”; corresponding RU/LV/PL explicit phrases are recognized. Capture preserves original intent; it does not invent dates, installed connectors or capability plans. Existing reminder/time intents resolve dates through their existing owner. Ambiguous required capabilities are needs-user. Unsupported email, price-comparator and location/free-time intelligence have no fake callable implementation.

## Typed contract and authority

Argument objects are closed and bounded; unknown fields and invalid enums are rejected. Each installed capability provides its own closed argument schema. Results are bounded JSON with record/source ID and durable version, actual lifecycle, result availability and confirmed data only. Query pagination is at most 30 commitments within a character budget; research at most 20 passages. `saved` is persistence, `deliveryConfirmed=false` never pretends a notification appeared.

Edits and actions require exact record/source versions. Reviewed plan identity hashes canonical content plus a fresh nonce and is bound to actual routine action/trigger/grant content. Initial and later step claims reference the actual routine occurrence and durable tool claim. A stale plan, tampered content, changed source, missing grant or direct internal call cannot execute. Stop persists revocation before scheduler cancellation. Interrupted or uncertain writes must be inspected, not replayed. Process-loss and restored authority remain needs-user; backups never reinstate execution grants.

The foreground adapter requires a loaded unlocked adult session, captured family/mode revisions and an interaction ID for writes. Reads capture disclosure authority; source/result dependencies are checked before publication and return. Background work rechecks adult/skill permission and exact journal/run/plan/version. Collector requests use the existing revocable remote-dispatch lease, categories, transport and policy; Local makes zero external requests. Permission/approval/Private/Power/quiet-hour owners remain existing policy owners. Remote result categories are intentionally conservative because a derived brief may contain location, activity or device facts.

Public pages check robots policy before retrieval with the same revocable scope; no page redirects or recursive crawling. Source revocation during a request cancels or suppresses publication. Persistent per-origin research quotas apply across different queries and remove/re-add. Missing/stale source observations yield waiting or needs-user; only a new matched condition generates a new conditional notification. Stored source/result text is untrusted data and never execution authority.

## Three functional workflows

These are authored regression scenarios, not executed device/STT/provider evidence. Tests construct USER/MICROPHONE semantic interpretations, not real recognized speech.

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable readback |
|---|---|---|---|---|---|
| Capture ME Inbox → promote to dated task/reminder/event → link Workspace item | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN; Room/codec/promotion sources authored |
| Create HALO/SHARED → explicit plan → review → run or enable → real result → shared acknowledgement/take over | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN; real RoutineExecutor/Room harness authored |
| Select explicit research source → permitted refresh → retained citations → selected Research/Web brief → source revocation | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN; collector/brief/disclosure regressions authored |

## Evidence and acceptance

Production and regression sources are authored before the later gate. Source review is pending; no compilation or test outcome has been inferred from the diff. Source-only checks: generated language resources from reviewed catalog and whitespace/diff checks. Full acceptance mapping: [ISSUE-64-IMPLEMENTATION.md](../everyday/ISSUE-64-IMPLEMENTATION.md).

Focused tests/build, exact APK identity, installed APK readback, device serial/API, UI render inspection, native Android work/alarm/notification behavior, actual STT-final recognition and permitted model/provider routes: **NOT_RUN**. The issue remains open. No current passing #63 combined gate exists either; its prior power-interrupted evidence is retained separately.
