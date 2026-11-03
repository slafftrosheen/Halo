# Issue 63 requirements audit — 3 November 2026

Follow-up: [the ten-finding coding batch](/home/pi/Projects/Halo/docs/testing/ISSUE-63-AUDIT-FIXES-2026-11-03.md) now tracks authored corrections and current validation. This audit describes code at `ba177d110`; its original findings/checklist are retained as that historical assessment.

**Audit complete. Issue 63 implementation is not code complete.** The production source implements most requested owners and surfaces, but the findings below require code changes. Device acceptance is a separate remaining gate.

Audited code: `ba177d110b1c1e092cea6666ccaeca1409f7baff` on `codex/issue-63-completion`, including the `511718999` corrections and `ba177d110` Organizer/menu changes. The live [GitHub issue #63](https://github.com/slafftrosheen/Halo/issues/63) was read for this audit: updated `2026-11-03T04:56:11Z`, still open; its requirements body matches [the repository snapshot][issue]. The updated issue title is “Halo Everyday Intelligence — Organizer, Collectors, Daily Briefing & Widgets | UI 5.0”.

Method: traced requirements to production owners, composition/dispatch boundaries, UI consumers and existing regression source. Independent read-only reviews covered A/D and global policy/B/onboarding/C; a further review checked the Screen persistence and typed contract/parity findings. This audit did not run Gradle, compilation, tests, an APK, device actions or live endpoint probes. Test names below mean **source inspected, execution NOT_RUN for this code revision**. Historical results remain evidence only for their recorded inputs. The only pre-existing working-tree change was the executable bit on `gradlew`.

## Confirmed source findings

P1 needs prompt correction because the user can receive a successful response while an alarm continues. P2 is a functional, durability, localization or required contract gap. P3 is a narrower setup consistency issue. These are source conclusions with explicit triggers, not newly reproduced device observations.

### AUD63-01 · P1 · Assistant snooze/dismiss does not cancel the posted notification

The `halo_organizer_delivery` branch calls repository methods directly ([executor][delivery-tool]). The working touch path calls [OrganizerDeliveryCoordinator.snooze/dismiss][delivery-actions], which explicitly dismisses the old occurrence/version through the Android port. [Repository snooze][delivery-repository] increments the record version and overwrites the sole receipt with the new version. Therefore a subsequent worker no longer has the old notification tag to cancel. Recurring dismiss leaves the record active and its current-version receipt terminal; [reconciliation][delivery-reconcile] does not dismiss that current-version terminal receipt either. The composition callback only enqueues scheduling and invalidates the direct-boot mirror ([wiring][organizer-wiring]); it does not cancel a notification.

Impact: assistant snooze, and dismiss of a repeating ringing alarm, can leave the original insistent notification active until Android's ten-minute timeout. Recurring reminder dismissal can leave the old notification too. The [Android port][notification] posts/cancels by occurrence **and version**, so saving the next receipt is insufficient.

Required change: route delivery actions through the same coordinator under the current tool authority; retain exact occurrence/version checks and uncertain-write handling. Regression source needed: executor → coordinator → fake port, assert cancellation of the old tag for snooze and recurring dismiss, one future schedule, no duplicate replay. A repository-only state assertion cannot verify this effect.

### AUD63-02 · P2 · Recognized alarm/timer intents operate Android Clock outside the Halo organizer

[CognitiveSession][native-session] gives owned deterministic intents to the native dispatcher. [NativeCapabilityDispatcher][native-map] maps `TimerIntent` to `halo_timer` and `SetAlarmIntent` (including the “wake me at” form) to `halo_set_alarm`. [AlarmTools][host-alarm] delegates alarms to [AndroidAlarmAction][android-alarm] and timers to `ACTION_SET_TIMER`. Those actions dispatch to Android Clock; they do not create an Organizer record.

Impact: a recognized “Wake me at 7” or timer command can create an external Clock item which Halo's organizer, brief and cards do not own or list. Having a separate callable `halo_organizer_create` does not close this production intent path. The current external dispatch receipt is truthful, but the owner differs from the new first-party alarm/timer UI.

Required change: route intended Halo alarm/timer commands to the canonical organizer through the existing Runtime contracts, preserve deliberate OS Clock functionality explicitly, and prevent double dispatch. Regression source needed: recognized typed/STT-final semantic intent → registered tool → saved organizer ID/version visible in the same projections; ambiguity, permissions, cancellation and replay cases. Real speech recognition remains a later independent acceptance check.

### AUD63-03 · P2 · Unavailable attention is falsely presented as resolved

This regression was introduced by **our `511718999` change**. [The engine][attention-stamp] now marks `halo:attention` successfully observed even when empty. A genuinely empty successful read needs that stamp. However, [the graph][attention-wiring] supplies an empty snapshot if the owner is uninitialized, and [DailyAssistantController.recentBase][attention-owner] supplies one if its WhileSubscribed owner is not currently observed. No availability flag reaches the engine. Its successful-local-source removal comparison consequently labels previous morning attention facts `REMOVED` when the owner was merely unavailable.

Required change: carry explicit observed/unavailable state (and observation time) or perform a valid bounded owner read. Preserve successful-empty semantics while keeping unavailable state conservative. Regression source needed: morning with attention → uninitialized/unsubscribed owner → changes brief must not assert resolution; separately, an observed empty owner must still resolve the last item. The new empty-snapshot unit cases alone do not distinguish these states.

### AUD63-04 · P2 · Previous-day outstanding reminders disappear from today's brief

[The service][brief-service] requests only today's occurrence range. Point occurrences before the range are excluded by recurrence expansion. The [engine fallback][brief-overdue] includes overdue/undated tasks and active timers, but no overdue reminders. A blocked, failed or missed one-off reminder can remain ACTIVE because [delivery][delivery-failure] updates its receipt without completing the record.

Impact: yesterday's unresolved reminder cannot appear as an outstanding reminder in today's brief, despite the explicit C example.

Required change: include bounded outstanding reminder occurrences with receipt-aware status and accurate time/priority, without reviving dismissed/completed/cancelled occurrences. Regression source needed: crossing midnight with blocked/failed/missed, dismissed and snoozed reminders, including recurring occurrence identity and Kids filtering.

### AUD63-05 · P2 · Calendar import reasons bypass EN/RU/LV localization

[OrganizerScreen][ics-reasons-ui] translates the warning/error heading but renders `issue.reason` directly. [OrganizerIcs][ics-parser] creates those reasons as application-authored English strings, including the installed-IANA-zone warning for otherwise supported imports. These are application messages, not publisher titles.

Required change: stable reason codes/parameters with localized presentation, including the new duration/overlap rejection reasons. Preserve calendar content as untrusted publisher data. Regression source needed: RU/LV supported-import warning and unsupported-import reasons rendered through localized resources.

### AUD63-06 · P2 · Cross-link search cannot find older eligible organizer items

[OrganizerViewModel][link-choices] takes the first 40 eligible task/event records before building editor choices (plus only 20 Activity and 20 Timeline choices). [The editor][link-search] searches only that truncated collection. With more than 40 eligible organizer records, an older record is absent even when its exact title is entered. Bounded results are appropriate; filtering after truncation makes the search incomplete without explaining the limit.

Required change: search the complete eligible owner set before bounded paging/results; retain current selection and current-version checks. Make intentionally limited Activity/Timeline history explicit or query it by the supplied term. Regression source needed: an eligible match beyond the initial 40 and older Activity/Timeline results, selected links outside the first page, and current sharing authority.

### AUD63-07 · P2 · Screen touch Apply acknowledges before persistence failure can be known

[HaloScreen][screen-apply] closes editing and reports “Layout changed” as soon as `applyDraft` returns true. [LocalScreenStore][screen-store] publishes the new in-memory layout after asynchronous `SharedPreferences.apply()` with no disk result. A failed write therefore has no UI recovery and can revert after restart. By comparison, setup and [the tool path][screen-tool] call the owner's synchronous persistence check and reject durable success on failure.

This is specifically a disk-failure/acknowledgement gap. It is **not** evidence that ordinary Android process death always loses an `apply()` write.

Required change: a common persistence-aware Apply result, checked off the UI thread; retain the draft and report unknown/failed persistence without blindly replaying a possibly applied operation. Regression source needed: a failing persistence adapter, restart/readback and truthful UI outcome, plus concurrent revision rejection.

### AUD63-08 · P2 · Everyday result operability metadata is structurally empty

[EverydayToolContracts.operability][result-contract] declares all 22 commands as JSON with only `{type: object}` and empty `resultIdFields`; non-query commands are uniformly EDIT. Actual executor results are structured and bounded, so this is not a claim that the commands or their results are absent. The metadata cannot describe required receipt fields, object identities, bounds or differing operation semantics, and the current generic review can accept it vacuously.

[Repository directive 18][constitution] and [the verification template][verification] require meaningful versioned operability, bounded result contracts/encoding and durable identities. The existing Screen contract demonstrates a more explicit schema.

Required change: meaningful per-operation result schemas/semantics and applicable ID declarations matching actual success/dispatch/unknown outcomes; keep the central envelope and runtime unchanged. Regression source needed: production registry report plus actual executor payload validation, including create/edit IDs, query limits, dispatch-only effects and failure/unknown receipts.

### AUD63-09 · P2 · Supported calendar import/export lacks functional assistant parity

[OrganizerViewModel][ics-ui-operations] supports local import and prepares a real calendar export through Workspace. The [22 registered Everyday definitions][tool-definitions] contain no import/export/prepare-calendar operation. Source search did not locate another registered executor reaching the organizer calendar importer/exporter. Existing calendar tools target Android CalendarContract; text import/export handles TXT/Markdown/CSV, not Organizer ICS semantics. Navigation to Organizer and creating individual events are not equivalent to operating the supported interchange function.

Required change: bounded reviewed import/export preparation using canonical owners and exact source IDs/revisions. Keep any final Android document-picker/security gesture human-owned and explicitly document that boundary; do not claim arbitrary filesystem or automatic picker access. Regression source needed: selected local calendar data → reviewed supported subset → canonical records, and exact selected organizer data → real export result/ID, with stale selection, limits, untrusted content and uncertain mutation cases. This parity obligation follows G and directives 18–19; A's “ICS as feasible” does not require implementing every ICS feature.

### AUD63-10 · P3 · Repeated setup's “Remove weather city” retains the selected weather source

[The setup action][setup-city] clears only `draft.cityId`. After a previous save, [EverydaySetupChoices][setup-choices] has already copied that weather source into `draft.sourceIds`; applying the cleared city keeps it selected. Selecting a different city can likewise retain the former city source.

Impact: the city shown by the setup picker and its “Remove” action disagree with which weather sources remain selected for permitted Power refresh. Multiple selected cities may be legitimate, but this action does not explain that behavior.

Required change: either update the corresponding source selection when removing/replacing the setup city or label the action clearly as clearing only the pending picker choice. Regression source needed: save city A → reopen → remove/save, and city A → city B, asserting both visible selection and actual saved source selection. No Local network bypass was found here.

## Requirement-by-requirement coverage

Legend: **Present** = production path found, not execution/acceptance. **Gap** = finding above. **Limited** = deliberate bounded/supported subset allowed by the requirement. **Validate** = execution/hardware/provider/human evidence still needed. One implemented owner can support several requirements; rows are not independent test passes or a percentage score.

### Global policy and architecture

| Requirement | Production evidence and assessment |
|---|---|
| Exactly Local/Power across experiences | **Present.** [PrivacyModeStore][privacy] has two serialized modes; [policy matrix][matrix] maps Home/voice, Screen, Workspace, Kids, Study, widgets, collectors and model/network workers to actual shared boundaries. No third Everyday privacy owner found. |
| Local: no external fetch; timestamped stale cache | **Present; Validate.** [CollectorCoordinator][collector-coordinator] denies before adapter/admission; [guarded transport][guarded] independently guards before HTTP/DNS; passive readers/cards show saved timestamps. Existing hermetic tests assert denied dispatch. No fresh packet/session evidence was collected. |
| Deliberate Power, scoped context, no repeated routine cloud prompts | **Present.** Existing mode/disclosure and central descriptor/context-category owners are reused. Public collector requests contain endpoint and transport metadata; manual weather uses selected city-scale coordinates. Brief speech uses only the selected brief. No Halo-operated backend introduced. |
| Mode/profile/family changes cancel and fence stale work | **Present; Validate.** Durable privacy revision/leases, guarded call cancellation/body checks, source CAS/reservations and final widget publication authority checks; selected endpoints do not inherit arbitrary provider cookies/proxy settings. |
| Kids/Study share global policy, separate data grants | **Present.** Existing parent-controlled router plus current shared-record/source grants; Study changes presentation, not privacy. Real narrated on-device/Power sessions remain separate. |

### A · Organizer

| Requirement | Production evidence and assessment |
|---|---|
| Persistent offline records and restart-safe edits | **Present.** [Room repository][organizer-repository], typed records, transaction/CAS, schedule outbox and saved editor state. |
| Calendar events, recurrence and named zones | **Present; Limited.** [Models][organizer-models], recurrence engine and editor use explicit wall/zone values, per-occurrence overrides, defined DST and invalid-month behavior. Bounded recurrence is documented. |
| Agenda, conflicts, search and editing | **Present.** [OrganizerScreen][organizer-screen] and its ViewModel expose range/filter/search, overlapping-event indication and exact-version editing. |
| ICS import/export as feasible | **Present; Limited; Gap 05/09.** [ICS parser/exporter][ics-parser], review/import UI and [Workspace export preparation][ics-ui-operations]. Unsupported recurrence/time/attachment constructs are rejected or warned; import is not advertised as full RFC coverage. Reasons need localization; functional assistant interchange is missing. |
| Tasks/subtasks/priorities/due/completion/recurrence | **Present.** [Use cases][organizer-usecases], repository parent/cycle checks, editor and recurrence occurrence completion. |
| One-off/recurring reminders, snooze, notifications, reboot | **Present; Gap 01; Validate.** [Delivery coordinator][delivery-reconcile], persistent receipts/outbox, WorkManager and redacted direct-boot mirror. Assistant action bypass remains. Real OS delivery/reboot/permission acceptance is pending. |
| Alarms/timers, stop/snooze/cancel/full-screen/Doze/zones | **Present; Gap 01/02; Validate.** Local domain and Android queue/notification/AlarmActivity adapters exist, with elapsed-time timers and reboot fallback. Recognized native intent path must join the same owner. Android exact/full-screen permission and sound are separate observations. |
| Exact permission only where warranted; explicit precision fallback | **Present; Validate.** [Android constraints][android-constraints], access checks and queue fallback/receipt UI. A saved schedule does not prove exact or audible delivery. |
| Reuse Workspace Notes/planning | **Present.** No new Note organizer kind/store; links and open actions reuse the canonical Workspace repository. |
| World clock/date, offline city selection | **Present.** [WorldClockScreen][world-clock] and Room clock owner use phone/fixed IANA zones, reordered saved cities and offline catalogue. |
| Cross-link tasks/events/Notes to Activity/Timeline | **Present; Gap 06.** Typed links, existing Activity/Timeline projection and exact-owner navigation exist. UI search truncates eligible choices before filtering. |
| Safe delete/undo | **Present.** Versioned trash/restore and undo operations preserve canonical records; dependent parent/link checks remain in the repository. |
| Accessibility and EN/RU/LV | **Present; Gap 05; Validate.** Semantic Material controls, minimum touch sizes, scrollable forms, translated resources and large-text render test sources. Raw ICS reasons remain; TalkBack/Switch Access/real large-font interaction is unobserved. |

### B · Collectors and starter setup

| Requirement | Production evidence and assessment |
|---|---|
| Provider-neutral registry and typed schemas | **Present.** [CollectorModels/registry][collector-models], five local and four public adapter registrations in composition. |
| Source metadata/provenance/attribution/language/timestamps/cadence | **Present.** Typed source/stamp/payload models, source settings/readers and actual-attempt/freshness UI. |
| Conditional caching/backoff/dedupe/offline/quotas/cancellation | **Present; Limited.** [PublicHttpCollector][public-http] plus [durable admission repository][collector-repository], final-resource validator proof, rolling requests, cooldown lineage, immutable successful retrieval times and cancellation. Dedupe is source identity, not cross-publisher semantic dedupe. |
| SSRF/URL/DNS/redirect/size/sanitization | **Present.** [PublicNetworkPolicy][public-policy], guarded public client, bounded body/XML/JSON parsers, inert reader text. No remote page scripts/images/fonts execute in these readers. |
| WorkManager scheduling and observability | **Present; Validate.** [CollectorRefreshWorker][collector-worker] shares coordinator and bounded per-pass admissions. Attempts/freshness/status are durable and visible. Requested cadence is at least 15 minutes; Android may delay work. |
| Internal organizer/Notes/Timeline/Activities/Halo notifications | **Present.** [AndroidLocalCollectors][local-collectors] read canonical owners; no third-party notification permission is silently acquired. |
| Device/battery/model/runtime state | **Present.** Passive Android local adapter and injected model/runtime health facts. Unavailable device state is represented rather than fabricated. |
| Weather/forecast/alerts/RSS/Atom/notices/public ICS/topical feeds | **Present; Limited; Validate.** [Public adapters][public-http], Open-Meteo, feed and ICS parsers. No unsupported finance/transit is advertised. Actual nonempty live warning acceptance is still unverified. |
| Manual URL preview, management/category/language/mute/disable/freshness | **Present.** [Preview][collector-preview], source ViewModel/settings/catalogue; Local validates/saves without fetching, Power preview uses same transport/budgets. |
| External polling only Power; saved content in Local | **Present; Validate.** Shared policy admission at foreground/background boundary; cached data remains visible with successful retrieval timestamps and stale status. |
| Latvia LSM/public agencies; world/science/technology catalogue | **Present; Validate.** [Starter catalogue][catalogue] includes actual LV/RU/EN LSM, BBC, NASA, Ars, Verge, HN, CERT.LV and Meteoalarm. [Saved endpoint observations][feed-validation] are historical availability evidence. BBC is the permitted verified replacement for unsupported Reuters/AP feeds. |
| No-key weather, optional manual city, no mandatory geolocation | **Present.** Derived Open-Meteo endpoint, bundled offline cities and no forced account/key/location permission. [Saved weather/alert observation][weather-validation] predates the exact current request shape and does not prove current parser acceptance. |
| Real local initial content, never fake live news | **Present.** Internal selected defaults project actual local owners; unavailable/empty states are honest. Clock and organizer remain useful without network. |
| First launch cards/category/language/preview/cadence/city/Screen choices | **Present; Gap 10.** [EverydaySetup][setup] is attached to onboarding, retains drafts, offers explicit Power preview and saves reviewed choices. Repeated-city removal needs consistent source selection or wording. |
| Power fetches selected sources only; no forced cloud account | **Present.** Setup applies local metadata; separate explicit preview/selected enabled source admission performs governed network work. |

### C · Your Day

| Requirement | Production evidence and assessment |
|---|---|
| Deterministic typed offline brief, no LLM dependency | **Present.** [Engine][brief-engine] and [service][brief-service] read local snapshots/owners and never fetch or call a model while composing. |
| Calendar/tasks/reminders/activity/weather/news/user priorities | **Present; Gap 04.** Typed projections, priority/pinned records and bounded sections exist. Overdue reminders need receipt-aware inclusion. |
| Morning/on-demand/evening and changes since morning | **Present; Gap 03.** Fixed durable morning baseline and stable fact fingerprints; unobserved attention must not be stamped successfully empty. |
| Attribution/freshness/timestamps/actionable links | **Present.** Brief source stamps/fact routes and [brief UI][brief-screen] expose sources/time/links; routing rechecks current owner/grants. |
| Article → attributed local reminder | **Present.** Reviewed immutable article/source/revision and SourcesViewModel transaction create a linked reminder, with editable tomorrow default. |
| Optional speech/proactivity/quiet hours/settings | **Present; Validate.** [BriefReadAloud][brief-speech] uses existing audio owner and scoped disclosure; durable proactive coordinator/worker and final attention sink recheck availability/quiet hours. No automatic spoken-brief promise. |
| Kids/Study same policy and suitable presentation | **Present; Validate.** Audience/grant filtering plus shared global mode; remote speech category/revocation checks retained. Real recognition/provider/audio behavior remains unobserved for this revision. |

### D · UI 5.0 and the user's navigation corrections

| Requirement | Production evidence and assessment |
|---|---|
| Real organizer/agenda/tasks/alarm/timer/source/news/weather/brief screens | **Present.** Organizer, [sources/reader][sources-screen], world clock and brief instruments use real owner ViewModels and actionable forms. |
| Tokens/typography/icons/accessibility/motion where appropriate | **Present; Validate.** Existing themed components/semantic assets are reused. Visual polish, assistive use and light/dark/dynamic behavior require final rendered/device review; source presence is not a usability score. |
| Bottom Halo Zone ~quarter, Orb menu/contextual labels, no duplicate navigation | **Present.** [HaloZone][halo-zone], shell and Orb ownership; nested instruments retain exact deep links. No new independent navigation shell. |
| Loading/empty/error/offline/stale/permission/success recovery | **Present; Gap 05/07.** Real loading/empty/source-attempt/permission/import/editor states; raw ICS reasons and Screen persistence failure need corrections. |
| Library integration and typed registry, not isolated screens | **Present; Gap 01/02/08/09.** [Library projection][library] and production HostTools registration exist; meaningful assistant operations are broader than navigation, but identified parity/contract paths remain. |
| Organizer beside HaloStudio/HaloNotes; Your Day/world clock/Sources nested | **Present in ba177d110; Validate.** [OrganizerToolLinks][organizer-links], Workspace tool list/Orb catalogue and legacy-route parent correction implement the requested hierarchy. Regression sources authored; no new execution. |
| Kids/full Halo switch remains available in menu | **Present in ba177d110; Validate.** [FamilyModeMenuControl][family-menu] state is shell-owned, menu entry reused across pages; credential-gated exit survives menu lifecycle dismissal and uses existing family policy. Real device/menu/credential round trip still pending. |

### E · Halo Screen

| Requirement | Production evidence and assessment |
|---|---|
| Every listed card purpose with real data | **Present.** [EverydayCards][cards] covers clock/date, weather, upcoming, agenda, tasks, reminders, alarms/timers, headlines, brief, dashboard and quick actions; existing Screen device card remains connected. Device is not missing merely because it is outside the Everyday kind catalogue. |
| Per-instance configuration/arrangement/size/visibility | **Present.** Stable presentation IDs, bounded typed config, editor, size/grid controls, filters and saved presets. Shared faces support the correct purpose and states. |
| Apply/Cancel/CAS/Undo and process restore | **Present; Gap 07.** [LocalScreenStore][screen-store] persists identities/configuration/layout, rejects stale draft base and increments undo revisions. Touch Apply needs persistence-failure acknowledgement parity. |
| Correct instrument opens and private/current authority | **Present.** [Snapshot reader][snapshot-reader] reads coherent local data; [target routing][widget-targets] and owner routes revalidate current record/source identity/version/grants before dispatch. |
| Overlay/AoD-like concept, no abusive polls/wake locks | **Present; Validate.** Existing overlay/full-screen presentation retained; visible lifecycle owner reads local state on a minute tick and never fetches. [ScreenWindowPolicy][screen-window] scopes display flags to visible/resumed/interactive/unlocked ownership. Actual power/thermal behavior is unmeasured here. |

### F · Android launcher widgets

| Requirement | Production evidence and assessment |
|---|---|
| All eight resizable variants | **Present.** [Provider declarations][widget-provider] and manifest/XML describe clock, clock/weather, upcoming, tasks/reminders, brief, headlines, alarms/timers and dashboard; native layouts/previews/vector assets are packaged. |
| Independent config/IDs, process death/restore, truthful Apply | **Present; Validate.** [Atomic instance store][widget-store], configuration Activity and durable operation receipts use fsync/readback and ID mapping. No remaining concrete F production defect was identified by this audit; this is not a launcher acceptance pass. |
| Theme/locale/size/accessibility/native previews | **Present; Validate.** [RemoteViews][widget-views] bounds content by size, supplies purpose/accessibility text and themed native faces; host TextClock uses phone/fixed city zones and 12/24-hour settings. Actual launchers/fonts/assistive interaction need observation. |
| Clicks open the current correct item | **Present.** Immutable unique PendingIntent identities, bounded encoded targets and [current version/visible-face target revalidation][widget-targets]. Arbitrary remote routes are not accepted as trusted intents. |
| Reboot/time/zone/locale/data changes and realistic refresh | **Present; Validate.** Local change observer/debounce, passive 30-minute worker and maintenance receiver. Widget binding/clock tick/preview never fetch; Android can defer refresh. TextClock ticks through host support, not a pretend application background clock. |
| Battery limits and privacy-safe lock presentation | **Present; Validate.** No per-second refresh service/exact-alarm polling; conservative native faces hide personal details even outside the lock screen. Family/mode/config authority is checked immediately before publication. Device battery/launcher/lock behavior is pending. |

### G · Runtime and functional controllability

| Requirement | Production evidence and assessment |
|---|---|
| Discoverable typed read/write/query commands and same pipeline | **Present; Gap 02/09.** [22 definitions][tool-definitions], built-in `halo.everyday` manifest, central descriptors and [production registration][registration] reuse existing ToolExecutor/CognitiveSession. Recognized alarm/timer native intents and calendar interchange need parity corrections. |
| Permissions/mode/Kids/source versions at execution boundary | **Present; Gap 01.** Current interaction/family/disclosure authority, repository CAS, source/content revisions, selected-source grants and governed refresh/preview. Delivery side effects must use the same coordinator as touch. |
| Structured results, durable IDs/traces, no chat-as-action | **Present; Gap 08.** Actual results use canonical records/outcomes, central traces, ActionTruth and bounded payloads. Operability result schemas/ID metadata remain incomplete. |
| No duplicate agent/alternate database/network plumbing | **Present.** Existing registry/descriptors/domain repositories, shared Runtime/native dispatch and remote policy are reused. Intent-owner correction must preserve single dispatch. |
| Example workflows incl. wake/move/plan/news/weather widget | **Present as operations; Gap 02; Validate.** Organizer edit/create, brief build, Screen config and exact widget operations exist; full recognized voice and permitted provider workflows have not been observed on current code. Callable definitions are not proof of actual spoken acceptance. |

### H · Engineering and acceptance

| Requirement | Evidence and assessment |
|---|---|
| Audit before architecture changes; extend existing owners | **Present.** Existing architecture/integration audits and canonical owners are documented; this review adds direct source-backed gaps rather than treating earlier checklists as proof. |
| Coherent coding batches, targeted tests at boundaries | **Present / current execution deferred.** Recent correction/menu batches include authored regressions. User explicitly requested implementation first on this machine; this audit ran no builds/tests. |
| Parsing/cache/backoff/SSRF/provider failure/offline regression sources | **Present; Validate.** Collector parsing/integration/reviewed-refresh and SDK public-download/guarded-client suites exist. Current revision is unexecuted. |
| Recurrence/alarm/reminder/zone/reboot/persistence sources | **Present; gaps require new regression coverage; Validate.** Organizer recurrence/ICS/delivery/timing/persistence/editing and clock/city suites exist. They do not establish the missing cross-owner effects above. |
| Brief/setup/instance/update/voice-dispatch sources | **Present; Gap 01–04/06–10 require targeted source additions; Validate.** Existing brief/setup/Screen/widget/executor/native-dispatch suites cover many bounded cases, not the newly identified paths. |
| Explicit Local/Power × all requested surfaces matrix | **Present; Validate.** [LOCAL-POWER-MATRIX][matrix], hermetic boundary tests and transport inventory exist. No new UI-session packet capture or current remote acceptance was performed. |
| Device OS notifications/exact/full-screen/launcher/background | **Validate.** Historical installation/startup evidence is bounded to its APK. Actual current alarms, audio, Doze, locked boot, placement/resize/background/lock behavior remain open; no missing production feature is silently moved to user testing. |
| Accessibility/localization/theme/performance/battery | **Present; Gap 05; Validate.** Resource/render source and bounded scheduling/request policies exist. No current performance, energy, thermal or assistive-use result; prior Pi build behavior is not an Android battery result. |
| Migration/backward compatibility/archive/restore/retention | **Present; Validate.** [Room migrations through 34][database], [archive validation/restore][archive] and existing Brain import owner retain CAS/ID remapping, pause restored timers, disable restored external sources and report partial settings restore honestly. Retention is bounded/documented. Migration/archive/retention sources need the eventual matching gate. |
| Architecture/contracts/starter validation/threat model/limitations/UI mapping | **Present; corrected status needed.** [Integration][integration], collectors/briefing/Android/policy docs and endpoint evidence exist. Previous “all code complete” headlines are superseded by this audit and corrected in the current handoff/scope/ledger/tools docs. |
| All code/assets, no dummy screens/providers, final checklist/commits | **Present owners/assets; issue remains open.** No new advertised dummy provider or mock production screen was identified. Confirmed gaps prevent a completion claim. This audit is a documentation-only commit; it does not close or publish the issue. |

## Evidence and remaining gates

Existing regression sources inspected include [OrganizerDeliveryTest][test-delivery], [OrganizerIcsTest][test-ics], [OrganizerPersistenceTest][test-organizer], [CollectorIntegrationTest][test-collectors], [EverydayBriefTest][test-brief], [BriefPersistenceTest][test-brief-persistence], [EverydaySetupChoicesTest][test-setup], [EverydayCardsTest][test-cards], [EverydayScreenInstancesTest][test-screen], [EverydayWidgetStateTest][test-widget-state], [EverydayWidgetApplyOperationTest][test-widget-apply], [EverydayWidgetRenderingTest][test-widget-render], [EverydayToolExecutorTest][test-tools], [EverydayArchiveTest][test-archive] and [EverydayRetentionTest][test-retention]. The new menu/hierarchy regression sources are recorded in [the menu follow-up][menu-follow-up]. Presence of a suite is not evidence that each newly identified trigger is covered.

The earlier 306-case release record, later startup recovery, Raspberry Pi baseline failures and subsequent unexecuted correction batches have different source identities. None may be relabeled as a green run of `ba177d110`. In particular, the older release APK predates both current code commits. Fresh assertions must run against the final corrected source, with exact identities and failures retained.

Completion checklist:

- [x] Read current issue and map global policy, A–H and the user's navigation/family corrections to production paths.
- [x] Separate confirmed source gaps, supported limits and evidence-only acceptance.
- [x] Record concrete fixes and regression intent; identify our introduced attention regression explicitly.
- [x] Correct current documentation that called the whole implementation complete.
- [ ] Implement AUD63-01 through AUD63-09; resolve AUD63-10 selection semantics/wording.
- [ ] Author targeted regressions for those fixes in coherent batches.
- [ ] After implementation is ready, run the focused matching policy/organizer/collector/brief/Screen/widget/tool/archive/migration gate, then one app build; retain exact source/APK results.
- [ ] Perform authorized native device and actual recognized voice/permitted Power checks separately, including current production endpoint shape and a nonempty warning fixture/real source observation as available.
- [ ] Complete visual/accessibility and performance/battery assessment; preserve genuine hardware/provider/human NOT_RUN items.
- [ ] Re-audit corrected requirements and deliver the final evidence-backed checklist before declaring issue 63 complete.

No percentage is assigned: a large amount of implemented code and old green tests do not establish that every required integration works. **The audit deliverable is finished; issue implementation and acceptance remain unfinished.**

[issue]: /home/pi/Projects/Halo/docs/everyday/ISSUE-63-SOURCE.md:1
[delivery-tool]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/tools/EverydayToolExecutor.kt:180
[delivery-actions]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/OrganizerDeliveryCoordinator.kt:161
[delivery-repository]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/data/everyday/RoomOrganizerRepository.kt:181
[delivery-reconcile]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/OrganizerDeliveryCoordinator.kt:62
[organizer-wiring]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt:237
[notification]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/android/AndroidOrganizerDelivery.kt:104
[native-session]: /home/pi/Projects/Halo/runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt:321
[native-map]: /home/pi/Projects/Halo/runtime/cognition/src/main/java/com/halo/runtime/cognition/native/NativeCapabilityDispatcher.kt:408
[host-alarm]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/tools/host/AlarmTools.kt:89
[android-alarm]: /home/pi/Projects/Halo/host-android/src/main/java/com/halo/host/android/capability/AndroidAlarmAction.kt:25
[attention-stamp]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/briefing/EverydayBriefEngine.kt:116
[attention-wiring]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt:194
[attention-owner]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/daily/DailyAssistantController.kt:48
[brief-service]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/briefing/EverydayBriefService.kt:32
[brief-overdue]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/briefing/EverydayBriefEngine.kt:47
[delivery-failure]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/OrganizerDeliveryCoordinator.kt:144
[ics-reasons-ui]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/OrganizerScreen.kt:349
[ics-parser]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/OrganizerIcs.kt:173
[link-choices]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/OrganizerViewModel.kt:97
[link-search]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/OrganizerEditor.kt:118
[screen-apply]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/screen/HaloScreen.kt:433
[screen-store]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/screen/LocalScreenStore.kt:89
[screen-tool]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/tools/ScreenToolExecutor.kt:76
[result-contract]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/tools/EverydayToolContracts.kt:74
[constitution]: /home/pi/Projects/Halo/.agents/AGENTS.md:25
[verification]: /home/pi/Projects/Halo/docs/testing/AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md:1
[ics-ui-operations]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/OrganizerViewModel.kt:218
[setup-city]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/EverydaySetup.kt:168
[setup-choices]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/EverydaySetupChoices.kt:14
[privacy]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ai/PrivacyModeStore.kt:12
[matrix]: /home/pi/Projects/Halo/docs/everyday/LOCAL-POWER-MATRIX.md:1
[collector-coordinator]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/CollectorCoordinator.kt:38
[guarded]: /home/pi/Projects/Halo/sdk/provider-sdk/src/main/java/com/halo/sdk/provider/GuardedRemoteClient.kt:66
[organizer-repository]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/data/everyday/RoomOrganizerRepository.kt:21
[organizer-models]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/OrganizerModels.kt:39
[organizer-screen]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/OrganizerScreen.kt:116
[organizer-usecases]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/organizer/OrganizerUseCases.kt:31
[android-constraints]: /home/pi/Projects/Halo/docs/everyday/ANDROID-CONSTRAINTS.md:1
[world-clock]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/WorldClockScreen.kt:35
[collector-models]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/CollectorModels.kt:6
[public-http]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/PublicHttpCollector.kt:38
[collector-repository]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/data/everyday/RoomCollectorRepository.kt:99
[public-policy]: /home/pi/Projects/Halo/sdk/provider-sdk/src/main/java/com/halo/sdk/provider/PublicNetworkPolicy.kt:12
[collector-worker]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/android/CollectorRefreshWorker.kt:13
[local-collectors]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/android/AndroidLocalCollectors.kt:21
[collector-preview]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/CollectorPreview.kt:13
[catalogue]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/collectors/StarterSourceCatalogue.kt:13
[feed-validation]: /home/pi/Projects/Halo/docs/everyday/starter-source-validation.json:5
[weather-validation]: /home/pi/Projects/Halo/docs/everyday/weather-alert-source-validation.json:5
[setup]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/EverydaySetup.kt:30
[brief-engine]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/briefing/EverydayBriefEngine.kt:11
[brief-screen]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/EverydayBriefScreen.kt:75
[brief-speech]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/briefing/BriefReadAloud.kt:16
[sources-screen]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/SourcesScreen.kt:88
[halo-zone]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/shell/HaloZone.kt:44
[library]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/presentation/EverydayLibraryProjection.kt:14
[organizer-links]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/everyday/OrganizerToolLinks.kt:15
[family-menu]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/ui/screens/kids/FamilyModeMenuControl.kt:19
[cards]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/presentation/EverydayCards.kt:73
[snapshot-reader]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/everyday/presentation/EverydaySnapshotReader.kt:18
[widget-targets]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/nativeactions/EverydayWidgetTargets.kt:39
[screen-window]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/screen/ScreenWindowPolicy.kt:14
[widget-provider]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/nativeactions/EverydayWidgetProvider.kt:14
[widget-store]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/nativeactions/EverydayWidgetStore.kt:27
[widget-views]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/nativeactions/EverydayWidgetViews.kt:25
[registration]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt:1174
[database]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/data/AppDatabase.kt:1050
[archive]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/backup/EverydayArchive.kt:40
[integration]: /home/pi/Projects/Halo/docs/everyday/EVERYDAY-INTEGRATION.md:1
[menu-follow-up]: /home/pi/Projects/Halo/docs/testing/ISSUE-63-ORGANIZER-MENU-FAMILY-SWITCH-2026-11-03.md:1
[tool-definitions]: /home/pi/Projects/Halo/app/src/main/java/com/halo/app/di/tools/EverydayToolContracts.kt:39
[test-delivery]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/data/everyday/OrganizerDeliveryTest.kt:1
[test-ics]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/everyday/organizer/OrganizerIcsTest.kt:1
[test-organizer]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/data/everyday/OrganizerPersistenceTest.kt:1
[test-collectors]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/data/everyday/CollectorIntegrationTest.kt:1
[test-brief]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/everyday/briefing/EverydayBriefTest.kt:1
[test-brief-persistence]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/data/everyday/BriefPersistenceTest.kt:1
[test-setup]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/ui/everyday/EverydaySetupChoicesTest.kt:1
[test-cards]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/everyday/presentation/EverydayCardsTest.kt:1
[test-screen]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/screen/EverydayScreenInstancesTest.kt:1
[test-widget-state]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/nativeactions/EverydayWidgetStateTest.kt:1
[test-widget-apply]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/nativeactions/EverydayWidgetApplyOperationTest.kt:1
[test-widget-render]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/nativeactions/EverydayWidgetRenderingTest.kt:1
[test-tools]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/di/tools/EverydayToolExecutorTest.kt:1
[test-archive]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/backup/EverydayArchiveTest.kt:1
[test-retention]: /home/pi/Projects/Halo/app/src/test/java/com/halo/app/data/everyday/EverydayRetentionTest.kt:1
