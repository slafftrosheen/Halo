Status: Authoritative issue body snapshotted on 1 November 2026 and reread unchanged on 3 November 2026; implementation is tracked separately.

Source: https://github.com/slafftrosheen/Halo/issues/63
Updated at source: 2026-11-03T04:56:11Z
Current GitHub title: Halo Everyday Intelligence — Organizer, Collectors, Daily Briefing & Widgets | UI 5.0

# Halo Everyday — Personal Intelligence, Organizer, Information Collectors & Widgets (UI 5.0)

## Mission
Implement a production-ready, local-first everyday intelligence layer inside Halo. **Halo UI 5.0 is the sole visual and interaction source of truth; UI 4.0 is superseded.** Audit current code and documentation before changing architecture. Deliver 100% of implementation in Codex, including functional code, UI, assets, integrations, tests and documentation; no placeholders or unfinished demo flows.

## Non-negotiable global policy
- Exactly two global execution/privacy modes: **Local** and **Power**, inherited by every screen, tool and experience (Home, Screen, Workspace, Kids, Study, voice, widgets, agents).
- Local: on-device storage, local reasoning and tools; no external network requests. Previously cached externally sourced information may be displayed with timestamp and stale label; no silent refresh.
- Power: user has deliberately enabled cloud models, network collectors and permitted integrations globally. Do not introduce repetitive consent prompts for ordinary operations already covered by this choice. Respect Android OS permissions and separately necessary account authentication; never silently grant privileged access.
- Kids/Study are UX and pedagogical experiences, not alternative privacy policies. Audit all model/provider/network bypasses. Local↔Power switching must cancel or prevent disallowed in-flight/future requests, and avoid leaking cached private context.
- User data stays local by default. In Power, only task-relevant scoped context is transmitted to configured providers; document exactly what and why. No Halo-operated backend required.

## Workstream A — First-class organizer
Build persistent offline-first domain modules and user-facing tools for:
- Calendar (events, recurrence, time zones, agenda, conflict indication, editing/search, ICS import/export as feasible).
- Tasks, subtasks, priorities, due dates, completion, recurring tasks.
- Reminders (one-off, recurring, snooze, notifications, reliable scheduling and reboot restoration).
- Alarms and timers (create, stop, snooze, cancel, persistent alarm scheduling, full-screen alarm where Android policy allows, Doze/reboot/time-zone handling).
- Notes and lightweight planning (reuse existing Workspace/Notes, no duplicate storage).
- Clock/world time and relevant date information.
- Cross-link notes/tasks/events to activities and timeline; safe delete/undo; accessibility and EN/RU/LV localization.
Use native Android exact-alarm permissions/APIs only where warranted, and provide graceful fallback with explicit user-facing precision limits. Do not misrepresent an Android widget as an always-running clock.

## Workstream B — Information collectors / pullers / spiders
Introduce a provider-neutral **Collector Registry** with typed schemas, source metadata, attribution, provenance, language, timestamp, refresh cadence, ETag/Last-Modified caching, backoff, deduplication, offline snapshots, quotas, cancellation, SSRF and URL validation, size limits, safe HTML sanitization, WorkManager scheduling and observability.
Collectors:
1. Internal: organizer, notes, Halo Timeline, Activities, notifications (only Halo-owned by default; other apps only with OS-granted access), device readiness/battery when available, local model/runtime state.
2. External: weather/forecast/alerts, RSS/Atom news and blogs, official notices, selected public event calendars/ICS, user-selected topical feeds. Optional extension points for transit, finance and other data, but do not fabricate unsupported integrations.
3. Manual URL feed addition with preview/validation; source management, categories, mute/disable, freshness controls and per-source settings.
4. External polling and network fetching **only in Power**; no network in Local even for public RSS/weather. Cached feeds remain visible in Local.

## Starter source catalogue and onboarding
Ship a curated, configurable, localization-aware catalogue, with working validated endpoints rather than hardcoded unverified URLs:
- Latvia: LSM.lv (Latvian/Russian/English feeds where actually supported), LV public agency feeds as verified.
- World: BBC, Reuters or AP **only where an accessible RSS/Atom feed is actually available**, otherwise replace with a verified open feed.
- Science/space: NASA news feeds where supported.
- Technology: Ars Technica / The Verge / Hacker News (verified feeds).
- Weather: Open-Meteo where permitted and suitable; explain that geolocation requires OS permission or allow manual city. No mandatory paid API key for initial experience.
- Internal preloaded sample content generated from real Halo local state (clock, upcoming reminders, tasks, recent activity), never fake live news.
At first launch, offer understandable selectable cards, category and language filters, preview of sources, refresh policy, manual city, and optional selection of initial Halo Screen widgets. Default Local onboarding remains useful without network; Power fetches only selected sources. No forced cloud account or keys.

## Workstream C — 'Your Day in Halo' intelligence
Build a deterministic structured daily briefing engine consuming typed collector snapshots, calendar, tasks, reminders, activity, weather, news and user priorities. Morning/on-demand/evening briefs, freshness/attribution, actionable links, optional spoken summaries and proactive suggestions with user-configurable quiet hours. Examples:
- 'What's my day?' → next meeting, two urgent tasks, rain forecast (if cached/Power), three selected headlines, outstanding reminders.
- 'Remind me about this article tomorrow' → attributed news item linked to local reminder.
- 'What changed since morning?' → event/task changes and newly fetched items, with clear timestamps.
- 'Read my brief in Kids/Study' → same global Local/Power routing with age-appropriate presentation.
Briefs must work offline with local data and no LLM dependency; optional LLM wording in accordance with global mode.

## Workstream D — Halo UI 5.0 first-party screens
Implement polished organizer tools, source catalogue/management, news reader, weather detail, agenda, tasks, alarm/timer interfaces and briefing view. Reuse current UI 5.0 design tokens, components, typography, icons, motion/Lottie and accessibility patterns. **Halo Zone** bottom ~1/4: persistent Orb, contextual labeled satellites; upper ~3/4: working content. Navigation lives in Orb long-press/swipe menu; no redundant top navigation, duplicate tools or unlabeled icon clusters. Provide loading, empty, error, offline, stale, permission-denied and success states; gestures and labels must be understandable. Integrate all new tools into Workspace Library and voice/AI tool registry, not as isolated screens.

## Workstream E — Complete Halo Screen widget system
Functional responsive and configurable cards for clock/date, upcoming event, agenda, tasks, reminders, alarms/timers, weather, headlines, briefing, device status and quick actions. User-controlled arrangement, size, visibility and widget configuration; restore after process death. Cards must show actual data and open the correct instrument. Preserve Screen's overlay/AoD-like concept and UI 5.0 visual language. Avoid background polling or wake-lock abuse.

## Workstream F — Android launcher widgets
Implement Android AppWidget/RemoteViews or supported modern framework as appropriate, with multiple resizable variants: compact clock/date, clock+weather, upcoming event, tasks/reminders, daily brief, news headlines, timer/alarm quick action and configurable combined dashboard. Match Halo UI 5.0 within launcher rendering limitations; create required icons, layouts, preview assets, configuration activities, accessibility descriptions and click actions. Handle multiple independent instances, resize, locale/theme, process death, reboot, data changes, battery limits and privacy-safe lock-screen presentation. State realistic refresh constraints; no pretend real-time ticking.

## Workstream G — Full AI/voice controllability
Every new tool exposes discoverable typed commands and read/write/query capabilities through existing Runtime/ReasoningEngine/CommandExecutor contracts. Examples: 'Wake me at 7', 'Move my meeting to Friday', 'Summarize today's selected news', 'Add this to my plan', 'Show the weather widget'. Enforce capability permissions and mode at execution boundary; distinguish tool calls from chat text; return structured results and auditable traces. No duplicate ad hoc agent plumbing.

## Workstream H — Engineering and acceptance
- Begin with repo audit and map to existing Screen, widget, alarm/calendar tools, UI 5.0 docs and model routing; extend instead of duplicating.
- Implement in substantial coherent coding batches (target 90% implementation / 10% testing effort), avoid edit-build-test loops; run targeted tests at batch boundaries.
- Unit/integration tests: collector parsing/cache/backoff/SSRF, recurring alarms and reminders, clock/time zone/reboot, briefs, first-run source selection, widget instance isolation/update actions, voice intent dispatch, persistence, provider failures, offline behaviour.
- Explicit global policy matrix for **Local/Power × Home/Screen/Workspace/Kids/Study/Android widgets/background workers**; assert zero outgoing network calls in Local and no cloud leakage on mode transition.
- Device verification when available for OS notifications, exact alarm permissions, launcher widgets and background restrictions; document any hardware-only validation not performed rather than marking it passed.
- Accessibility, EN/RU/LV, light/dark/dynamic layouts, performance and battery budgets; migration and backward compatibility.
- Document architecture, collector/provider contract, starter source validation, data retention, mode threat model, Android limitations, UI 5.0 component mapping and test evidence.
- Codex owns all code and assets, including vector/motion assets as needed. No unimplemented mock screens, dummy providers or falsely 'complete' features.
- Deliver commits to repository per established development policy and include a final checklist with evidence, files changed and any genuine external dependencies.

## Definition of Done
A fresh install provides useful local organizer and widgets immediately; after user enables Power and selects sources, real weather/news appear with citations and timestamps; 'Your Day' composes both; Android launcher widgets work; all experiences including Kids/Study obey the same two global modes; voice can operate all tools; entire experience is visually consistent with **Halo UI 5.0**. No new Halo server, hidden third mode or repeated unnecessary cloud confirmation.

