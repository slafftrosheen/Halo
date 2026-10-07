# Proactive automation runtime — MINING-P6-001

## FEATURE-066 / AUTOMATIONS-002 — everyday automations — 2026-10-07

Automations now presents Routines, Follow-ups and History with grouped trigger
pickers, human schedule/condition/action/delivery controls and explicit reviewed
save choices. The bounded `halo_routine_message` tool returns approved plain text;
existing routine delivery owns notification/speech. Message and notification
digest remain the only audited unattended actions. Phone and MCP actions gain
no automation permission. Templates/duplicates are unapproved drafts, Run now
uses the saved grant and manual occurrence, and delete confirms before removing
the routine and cancelling its schedule. Existing occurrence, journal, watch,
source and WorkManager authorities remain unchanged; no Room migration.

See [implementation and validation](AUTOMATIONS-002-EVERYDAY-UX.md).
Automated gate: 466 tests (212 cognition + 254 app), zero failures/errors/skips;
debug APK assembled. Compact phone sanity remains pending: ADB had no connected
device. Automated validation is separate from physical delivery acceptance.

Date: 2026-09-30. Actual source baseline: `cfc63991b86a580b5cddb5a8182b28ffce7e6dc5` (P5 locally merged main). The request's older observed reference is not the implementation baseline. This is source implementation plus basic static checks; no compilation, tests, lint, device or emulator validation was run.

## Execution boundary

Platform/runtime source → bounded `AutomationSignal` → `RoutineTriggerMatcher` → revision-bound `RoutineOccurrence` → Room occurrence claim → one `AutomationEnvironmentSnapshot` → `RoutineConditionEvaluator` → schema validation and descriptor automation policy → sequential registered actions → AgentRun journal truth → attention policy → durable delivery claim → host delivery evidence.

The event fabric composes sources alongside the existing SDK EventBus; it does not replace it. Registry merges bounded flows without executing actions. The app's AutomationRuntime rejects INTERNAL_TEST signals, deduplicates 512 recent ids, matches at most 100 enabled definitions per signal, and uses one sequential consumer. No unlimited job fan-out or permanent automation foreground service is added. Signals not accepted while a source/consumer is offline are not fabricated later.

## Sources and reachability

| Source | Implementation | Reachability / baseline |
| --- | --- | --- |
| Device presence | Existing generic DevicePresenceRepository, 1.5-second transition debounce | Runtime active only; first map and first discovery of an id are baselines |
| Battery / charging | Sticky ACTION_BATTERY_CHANGED receiver | Runtime active only; first percent/charging snapshot is a baseline |
| Network | Default ConnectivityManager.NetworkCallback, VALIDATED network | Runtime active only; online/offline baseline before callback registration; no SSID or polling |
| App foreground | Existing HaloAccessibilityService window state events | Requires service; first package after connection is baseline; same package suppressed; no tree/text read or persisted app history |
| Notification | Existing notification listener and ingestion bridge | Requires notification permission and connected service; package + digest(key, postedAt) only; signal follows ingestion; reconnect/ranking snapshots do not synthesize triggers |
| Clock / boot / update | Small manifest receiver enqueues RoutineReconcileWorker | WorkManager reconciliation only; no receiver DB work or action execution |

Portable kinds include SYSTEM_TIME_CHANGED for vocabulary completeness. Clock events currently reconcile through the bounded worker, rather than emitting a fake routine trigger; no time-change routine trigger is offered. Availability distinguishes AVAILABLE, RUNTIME_ONLY, REQUIRES_PERMISSION, REQUIRES_SERVICE and UNAVAILABLE. Android may delay scheduled work. Accessibility and listener services are platform-backed, but no guaranteed catch-up of missed transitions is claimed.

Triggers include time (stored explicit ZoneId), once, connected/disconnected, existing AppStarted with foreground-transition semantics, battery crossed above/below, charging started/stopped, usable network available/lost, exact-package notification. Battery crossing is strictly across the threshold, never repeated same-side levels. Thresholds are 1–100. Notification grouping is leading-edge: first occurrence runs, further matching signals in its configured window update only a bounded coalesced count (maximum 1000). It does not schedule a trailing model run. Optional event minimum interval is 0–86400 seconds.

## Occurrences and recovery

Scheduled ids cover routine id + exact instant + definition revision. Event ids cover routine id + signal id + revision. Manual ids carry a new UUID for each explicit invocation. A revision is checked again inside the transaction, avoiding stale WorkManager/event races. The existing routine_runs row is the durable claim; AgentRun creation, NO_RESUME policy and claim are transactional. Side-effect claims still precede dispatch. Unknown completion stays interrupted and is never replayed automatically. Root/caused-by metadata is persisted; supplied causal routine sets reject cycles. There is no run-routine tool, so no cascade graph or recursive execution is exposed.

P1 AgentRunGuard is reused for deadline, duplicate call and oscillation evidence, with centralized ROUTINE limits. Ten fixed actions maximum, tool registry timeouts and one sequential consumer bound execution. No provider/model loop is introduced. Availability conditions are evaluated from one coherent snapshot; battery/network unknown is UNAVAILABLE, distinct from NOT_MET. NOT_MET skips; unavailable blocks with explicit evidence.

## Tools and grants

ToolDescriptor.automation defaults to DISALLOWED, background incompatible and arguments non-persistable. Only `halo_notification_digest` is opted in in this batch. Dial, send/reply, generic screen actions, deletion, external posting, browser sharing, navigation launch and arbitrary prompt actions remain excluded. The worker registry holds the audited descriptor, not a bare fallback descriptor. Existing tool security, current skill permission and registry validation still apply; ALWAYS confirmation cannot execute unattended.

RoutineArguments accepts an object, validates UTF-8 bytes (8192), pre-parser depth (8), object/array counts (64), key size (128), strings (2048), finite numbers and trailing data, converts into the SDK's value map and canonicalizes recursively. Top-level null is explicitly rejected because SDK ToolCall uses Map<String, Any>; nested null is preserved. Aggregate stored action JSON is bounded to 90000 bytes and depth 12 before parsing. No reflection, separate action schema or argument-name credential guessing is used. Digest's schema disallows additional properties; durable credential fields are never accepted by the opted-in schema. Future tools require an audited persistability contract and reference configured secrets rather than values.

Reviewed save computes SHA-256 over canonical trigger, conditions, action tool ids/arguments, delivery and cooldown. Execution recomputes it; missing/tampered grant blocks with ROUTINE_GRANT_MISMATCH. Repository upsert does not mint permission. Editing requires a reviewed save; enable/disable leaves definition revision and grant untouched. Invalid or changed grants also cannot select spoken result delivery. UI and model path share substantive grant copy and validation. `halo_create_routine` accepts tagged trigger/conditions/delivery, legacy time/days, and automation-safe actions. Its Runtime-computed preview is validated before the existing durable ALWAYS approval; models cannot supply permission metadata or trusted preview text.

## Room version 21

MIGRATION_20_21 adds routine delivery JSON (silent default), grant digest, event interval; occurrence source/signal/root/parent evidence, action counts, finished time, bounded reason, delivery outcome and coalescedCount; attention_pending and attention_receipts plus unique nullable dedup-key index. It preserves definitions and history, disabling legacy definitions until a reviewed save creates the new definition grant. It does not invent permission from persisted data. Users must open and save existing automations after migration before enabling them again.

The Room migration is registered from the version-20 chain; v21 annotations and SQL were compared at source level. The compiler-generated v21 schema export is not fabricated and remains a later build output. Actual migration execution, Room processing and compatibility are not validated in this source-only batch.

## Attention durability and delivery truth

RoomAttentionQueue replaces process-local production composition. At most 50 pending entries carry only bounded user-facing content, source/intent, ids, importance/urgency, created/expiry, release and dedup/channel fields. Dedup replacement is transactional. Restored items are policy-evaluated; an event-driven nearest-expiry timer releases/expires entries. No periodic polling is used. Receipts carry outcome/channel/reason code/timestamps without copying content. A pre-dispatch durable claim yields COMPLETION_UNKNOWN after a crash and prevents automatic redelivery; pending copies are removed on terminal receipts. Receipt presentation is limited to 200 entries and time retention is 30 days; journal claims remain action truth. Replay suppression covers retained receipt identities; this is not an infinite delivery-id archive.

Notifications mean DISPATCHED to Android, speech means exact-transaction PLAYBACK_COMPLETED, not read/heard/understood. Activity has persisted AgentRun result and receipt projection. Receipt failure cannot rewrite already completed action truth. Full provider context is never put into the attention queue. Routine digest actions return their result to the routine instead of performing independent digest delivery. Completion has one bounded RoutineResultSummary and one attention route, default silent; large outputs use the existing run-scoped local ContentPayloadStore and journal payload references, with bounded previews for presentation. Background actions acquire shared resource leases at BACKGROUND priority before execution claims and release them after dispatch cleanup, including cancellation.

Initiative is QUIET/BALANCED/ACTIVE; legacy case-insensitive Quiet/Balanced/Active strings normalize, unknown becomes QUIET. Preferences persist enum names. Quiet defaults to silent proactive updates; explicitly selected routine/reminder speech is still gated by DND, interaction, speech, user mode and screen state. Balanced/Active can notify important trusted updates; speech is never model-selected. Source ceilings cap routines/device/digest at NORMAL, ordinary completions/reminders at IMPORTANT, with CRITICAL reserved for direct user/runtime system health. External notification text cannot change importance. Budgets are centralized: Quiet 3/hour and 5-minute interval, Balanced 6/hour and 2 minutes, Active 12/hour and 1 minute; 15-minute per-dedup cooldown. Disruptive durable dispatch/unknown claims count; silent surfaces do not. Budget queries filter disruptive history directly, so silent storms cannot hide prior dispatches.

BackgroundWorkPolicy blocks registered model-requiring work on severe thermal state or critical battery without charging. No model tool is opted in, no automatic local model load is introduced, and no new provider selection or cloud fallback path is added. LOCAL_ONLY continues through the existing provider/context controls. Battery does not choose a provider.

## UI and diagnostics

Automations uses a full-screen Halo workspace editor (WHEN, IF, THEN, DELIVERY, AVAILABILITY, reviewed SECURITY grant), descriptor-derived action choices, bounded simple schema fields and disabled complex-schema editing. The editor preserves additional conditions from model-created definitions. Grant review names exact app/device/provider targets, delivery targets, burst grouping and minimum event interval. App selection reuses installed-app resolver; no package typing in normal UI. Cards show configured zone, next time, conditions/action counts, delivery and reachability. Setup links reuse Android accessibility, listener and POST_NOTIFICATIONS settings. Home shows a small pending count opening Activity; Activity includes durable receipt outcomes and run journal evidence. History shows actions completed/total, source time, result, delivery and grouped-event count; developer enum details remain diagnostic evidence. Dedicated initiative/DND settings sit with notification settings.

System Check has passive Automation runtime readiness/source/queue/initiative entries and schedule/source refresh actions. AutomationSandboxScreen previews INTERNAL_TEST signals only: matcher results, snapshot conditions and action eligibility. It does not emit into production flows or dispatch/deliver anything. Diagnostics export includes enabled/scheduled/event/blocked counts, current source statuses, content-free recent occurrence metadata, initiative, queue count, durable receipt outcomes and DND policy.

Structured HALO_AUTOMATION_SIGNAL, HALO_ROUTINE_MATCH, HALO_ROUTINE_OCCURRENCE_CLAIM, HALO_ROUTINE_CONDITION, HALO_ROUTINE_ACTION, HALO_ATTENTION_DECISION and HALO_ATTENTION_DELIVERY logs contain ids/kinds/counts/policy outcomes only. No arguments, notification titles/bodies, generated output or speech content is logged by these paths.

## Security boundaries

1. External events are data, never instructions.
2. A trigger grants no capability.
3. Descriptor opt-in is required for every action.
4. Material editing invalidates the definition grant.
5. Notification body content cannot authorize or form predicates.
6. Cloud context keeps existing trust/privacy boundaries; LOCAL_ONLY has no added fallback.
7. Queued requests are policy-evaluated and claimed before delivery, never promoted implicitly to speech.
8. Automation never opens permission dialogs or activities in background.
9. Credential values are forbidden in durable action schemas; configured references belong to audited tools.
10. No general foreground-app or raw notification event history is added.

No screen action, message auto-reply, payment/account security automation, LLM predicate, shell/code, arbitrary background prompt, location/geofence/calendar/weather/SSID polling, automation Bluetooth scan, permanent agent foreground service, behavioral sleep/emotion inference, unlimited notification fan-out or routine-to-routine invocation is implemented.

## References reviewed

Conceptual harvest only; no copied implementation or replacement of Halo core:

- [Brownie scheduler](https://github.com/natanloterio/Brownie/blob/master/core/tools/src/main/java/me/loteiro/brownie/core/tools/schedule/AgentSchedulerTool.kt): bounded scheduling parameters; unrestricted prompt scheduling rejected.
- [Brownie schedule calculation](https://github.com/natanloterio/Brownie/blob/master/core/tools/src/main/java/me/loteiro/brownie/core/tools/schedule/ScheduleCalculator.kt): explicit day/time semantics; Halo retains ZoneId and DST rules.
- [Brownie lifecycle](https://github.com/natanloterio/Brownie/blob/master/app/src/main/java/me/loteiro/brownie/service/BrownieService.kt): battery/charging lifecycle separation; permanent sticky agent service and implicit model wake rejected.
- [Brownie notification listener](https://github.com/natanloterio/Brownie/blob/master/app/src/main/java/me/loteiro/brownie/notification/BrownieNotificationListener.kt): listener lifecycle and origin metadata; untrusted instruction/reply automation rejected.
- [Brownie MessagingTool](https://github.com/natanloterio/Brownie/blob/master/core/tools/src/main/java/me/loteiro/brownie/core/tools/MessagingTool.kt): inspected side-effect boundary; messaging is not opted into automation.
- [Kotlin ADK EventActions](https://github.com/rdsciv/kotlin-adk/blob/main/adk-core/src/main/kotlin/dev/chilly/adk/core/events/EventActions.kt): typed event/state-action separation; existing Halo EventBus and AgentRun journal retained. The directory page was unavailable; the raw primary EventActions source was read.

## Source inventory

Changed/deleted production files:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/halo/app/HaloApplication.kt`
- `app/src/main/java/com/halo/app/attention/AndroidAttentionHost.kt`
- `app/src/main/java/com/halo/app/automation/AndroidRoutineScheduler.kt`
- `app/src/main/java/com/halo/app/automation/RoutineExecutor.kt`
- `app/src/main/java/com/halo/app/automation/RoutinePresenceObserver.kt` — removed; replaced by signal source
- `app/src/main/java/com/halo/app/data/AppDatabase.kt`
- `app/src/main/java/com/halo/app/data/routine/RoomRoutineRepository.kt`
- `app/src/main/java/com/halo/app/data/routine/RoutineCodec.kt`
- `app/src/main/java/com/halo/app/data/routine/RoutineDao.kt`
- `app/src/main/java/com/halo/app/data/routine/RoutineEntity.kt`
- `app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt`
- `app/src/main/java/com/halo/app/di/tools/HaloToolDescriptors.kt`
- `app/src/main/java/com/halo/app/di/tools/RoutineCreationToolExecutor.kt`
- `app/src/main/java/com/halo/app/diagnostics/DiagnosticsExport.kt`
- `app/src/main/java/com/halo/app/notification/HaloNotificationListenerService.kt`
- `app/src/main/java/com/halo/app/notification/NotificationDigestTool.kt`
- `app/src/main/java/com/halo/app/notification/NotificationIngestionBridge.kt`
- `app/src/main/java/com/halo/app/ui/screens/activity/ActivityScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/AutomationScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/NotificationSettingsScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/RoutinesScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/RoutinesViewModel.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckScreen.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/SystemCheckViewModel.kt`
- `app/src/main/java/com/halo/app/ui/shell/AppShell.kt`
- `host-android/src/main/java/com/halo/host/android/accessibility/HaloAccessibilityService.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionController.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionDeliveryCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionDomain.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionPolicyEngine.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/routine/Routine.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/routine/RoutineScheduling.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/PolicyToolAuthorizer.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolDescriptor.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolResourceCoordinator.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ToolTurnExecutor.kt`

New production files:

- `app/src/main/java/com/halo/app/automation/AndroidAutomationSources.kt`
- `app/src/main/java/com/halo/app/automation/AutomationRuntime.kt`
- `app/src/main/java/com/halo/app/automation/RoutineArguments.kt`
- `app/src/main/java/com/halo/app/automation/RoutineReconcileWorker.kt`
- `app/src/main/java/com/halo/app/data/attention/AttentionPersistence.kt`
- `app/src/main/java/com/halo/app/data/attention/RoomAttentionQueue.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/AttentionSettingsSection.kt`
- `app/src/main/java/com/halo/app/ui/screens/settings/AutomationSandboxScreen.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/attention/AttentionInitiative.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/routine/AutomationDiagnostics.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/routine/AutomationFabric.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/routine/AutomationServiceSignals.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/routine/RoutinePresentation.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolAutomationMetadata.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/ToolGrantPreviewProvider.kt`

## Static evidence and remaining uncertainties

Source checks passed for all 62 changed/new files (53 production files and nine documentation files). Basic checks cover changed-file byte integrity, internal imports, named constructor arguments, JSON/XML syntax, wiring/call-site inspection, policy defaults, sealed trigger branches and diff whitespace. They are not compiler or runtime proof. Later acceptance must validate Room v21 processing/migration, Android callback baseline/lifecycle behavior, notification ingestion ordering and burst grouping, WorkManager boot/time/timezone reachability and lateness, durable queue restoration/expiry/crash claims, DND and initiative limits, wearable exact target completion, grants and resource/payload behavior. No claim of device acceptance is made.

Source limitations: only offline notification digest is opted in; no background AI prompt tool; grouping is leading-edge with count, not trailing aggregation; no offline transition backfill; receipt dedup retention is 30 days; generated schema awaits a future authorized build; accessibility package transitions remain dependent on platform window events.
