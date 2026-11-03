Status: Authoritative GitHub issue snapshot, 3 November 2026. Source implementation and execution evidence are tracked separately.

Source: https://github.com/slafftrosheen/Halo/issues/64
Updated at source: 2026-11-03T07:49:57Z

# Halo Organizer — local-first personal scheduling, delegation and daily operations

## Context

Halo Workspace currently has six top-level instrument families:

1. HaloStudio
2. HaloNotes
3. HaloStudy
4. HaloMemory
5. HaloCanvas
6. HaloOrganizer

HaloOrganizer is not a copy of Google Calendar, Todoist, Android Clock, or another subscription planner. It is Halo's local-first daily operating layer: time, commitments, tasks, events, alarms, routines, information sources, briefs, notifications, and work delegated to Halo.

The user should be able to organize their own work and Halo's work from the same place, primarily by voice or direct UI actions, without relying on OEM task/calendar/clock apps for core behavior.

## Product definition

**Organizer = Human commitments + Halo commitments + time + triggers + results.**

Home remains the conversational surface.
Workspace remains the content/tool work surface.
Organizer owns commitments, scheduling, delegation, monitoring, and the return of actionable results to the user.

## Core responsibility model

Every actionable item has an explicit owner:

- `ME` — human-owned action.
- `HALO` — fully delegated action.
- `SHARED` — Halo prepares/monitors/executes part of the work and returns a decision or completion step to the user.

The user can transition ownership through explicit actions:

- **Delegate to Halo**
- **Take over**
- **Make shared**
- **Watch / monitor**

The UI must never imply Halo completed work when it only dispatched or attempted it.

## Core entity model

Use a unified intent/task model rather than separate incompatible subsystems.

Suggested conceptual shape:

```
IntentTask
- id
- title
- owner: ME | HALO | SHARED
- type: TASK | EVENT | REMINDER | ALARM | TIMER | ROUTINE | WATCH
- intent
- trigger
- schedule
- startAt
- dueAt
- duration
- recurrence
- priority
- state
- executionPlan
- requiredCapabilities
- linkedObjectIds
- sourceContext
- notificationPolicy
- result
- nextHumanAction
- createdBy: USER | HALO | IMPORTED
```

The exact persistence model should follow existing Halo repository/domain conventions and should not introduce parallel state owners unnecessarily.

## Halo task lifecycle

Halo-owned or shared work should expose truthful lifecycle states:

`DRAFT -> READY -> RUNNING -> WAITING -> NEEDS_USER -> COMPLETED | FAILED`

Semantics:

- **RUNNING**: Halo is actively executing work.
- **WAITING**: Halo is waiting for time, an external condition, a response, or a monitoring trigger.
- **NEEDS_USER**: Halo reached a point requiring an explicit human decision/action.
- **COMPLETED**: the underlying owner produced a confirmed result.
- **FAILED**: execution failed and recovery information is available.

Do not treat dispatch as completion.

## Main Organizer surfaces

### Today

Primary daily surface.

Show only what is relevant now/today:

- upcoming events
- due tasks
- active timers
- alarms
- recurring routines
- weather/current information relevant to the day
- unresolved reminders
- Halo work in progress
- Halo work waiting on the user
- daily brief

Today should make ownership immediately visible:

- **You**
- **Halo**
- **Shared**

Do not collapse these into one ambiguous list.

### Planner

A unified future planning surface for:

- calendar
- tasks with dates/deadlines
- meetings
- reminders
- recurring work
- Halo delegated jobs
- shared work

Avoid rigid separation where a task with a time, duration and reminder becomes artificially different from a scheduled item.

### Time

Internal Halo time tools:

- alarms
- timers
- stopwatch
- recurring alarms
- world clocks

Core behavior should be Halo-owned rather than dependent on OEM Clock apps.

### Routines

First-class recurring scenarios, for example:

- weekdays at 07:30: weather + schedule + tasks
- Sunday evening: weekly planning brief
- after work starts: remind to review a specific source
- every morning: check a monitored condition and surface only if changed

Routines should be local-first and capability-driven.

### Sources

Information inputs used by Organizer:

- weather
- calendar feeds
- RSS/news feeds
- user-selected information sources
- future connector-backed sources

Sources provide information; they do not own user commitments.

### Briefs

Composable summaries:

- morning
- midday
- evening
- weekly

Brief composition should be user-selectable and may include:

- weather
- events
- tasks
- delegated Halo work
- unfinished work from yesterday
- selected news/RSS
- reminders
- alarms/routines
- relevant linked Workspace objects

### Inbox

Low-friction capture for unstructured requests such as:

- “remind me tomorrow to call...”
- “next week look at this document”
- “Friday buy material”

Inbox items can be promoted into task/event/reminder/watch/shared work.

Voice capture should be a first-class path.

## Voice-first behavior

Examples:

- “Halo, tomorrow morning remind me to check the order.”
  - ME task/reminder.

- “Tomorrow morning check whether the order status changed and tell me.”
  - HALO task.

- “Find printer options tonight and tomorrow I will choose.”
  - SHARED task.

- “Watch this price and tell me if it drops.”
  - WATCH task.

The user should not be forced to open Organizer to create these.

## Watch / monitoring

Monitoring is a first-class delegation mode.

Examples:

- watch a price
- watch for an email/reply
- watch availability
- watch a source for a change
- notify if weather changes materially
- watch a system/device condition

The runtime/automation layer performs the work; Organizer owns the human-facing commitment, state, trigger, and result.

## Notifications

Organizer should become the semantic source of user-facing notifications for commitment-related events.

Notifications are presentation, not the source of truth.

Examples:

- task due
- Halo completed work
- Halo needs a user decision
- watch condition matched
- upcoming meeting
- alarm fired
- shared task ready for handoff

Notification Settings still control policy, channels, quiet hours, etc., but Organizer owns the underlying task/event semantics.

## Cross-instrument linking

Organizer may reference objects from the other five instrument families but must not absorb their responsibilities.

Examples:

- task -> HaloStudio project
- meeting -> HaloNotes note
- study reminder -> HaloStudy lesson
- planning item -> HaloCanvas
- context source -> HaloMemory

Boundary rules:

- long-form text remains HaloNotes
- saved context/knowledge remains HaloMemory
- visual idea mapping remains HaloCanvas
- graphics remain HaloStudio
- learning/source workflows remain HaloStudy

## Halo Zone 5.0 behavior

When Organizer is active, Orb satellites and page actions must be context-specific.

Top-level examples:

- Add task
- Add event
- Timer
- Alarm
- Brief
- Today

Inside an item:

- Complete
- Reschedule
- Remind
- Attach
- Delegate to Halo
- Take over
- Watch
- Stop/cancel Halo work where supported

Global navigation must not be duplicated around the Orb.

## Capability-driven execution

Organizer should not hard-code tool implementations.

A delegated task can express required capabilities, for example:

```
web.search
monitor
summarize
calendar.write
notification.schedule
```

The runtime decides which internal tool/provider/package satisfies the capability.

This prepares Organizer for the later tool-pack / manifest / capability-delivery architecture without coupling this issue to that future system.

## Future planning intelligence

Design the model so later iterations can support:

- free-time detection
- suggested scheduling
- “you have 45 minutes free before the meeting”
- conditional reminders
- device/context triggers
- location-aware triggers when privacy policy permits
- handoff between Halo and user
- recovery from failed delegated work

Do not fake these capabilities before real owners exist.

## Local-first requirement

Core Organizer behavior should work without requiring subscription services.

Persist Halo-owned tasks, schedules, routines, alarms, briefs and results locally using existing Halo storage conventions.

External sources are optional inputs, not mandatory dependencies for the Organizer core.

## Acceptance criteria

- [ ] Organizer is exposed as the sixth Workspace family.
- [ ] Today clearly separates You / Halo / Shared responsibility.
- [ ] Users can create ME, HALO and SHARED tasks.
- [ ] Delegate to Halo and Take over transitions are explicit and reversible where valid.
- [ ] Halo tasks expose truthful lifecycle states.
- [ ] Watch/monitor is represented as waiting/conditional work, not fake active execution.
- [ ] Planner supports tasks, events, reminders and delegated work.
- [ ] Alarms/timers/routines are Halo-owned rather than just links to OEM apps.
- [ ] Briefs are composable from user-selected sources.
- [ ] Notifications are driven from Organizer semantics and real task states.
- [ ] Voice can create Organizer commitments without opening the screen.
- [ ] Organizer items can link to Studio, Notes, Study, Memory and Canvas objects.
- [ ] No instrument responsibility is duplicated.
- [ ] Capability requirements do not hard-code a future tool-pack implementation.
- [ ] UI follows Halo Zone 5.0 75/25 workspace rules and contextual Orb action ownership.
- [ ] No UI claims completion, scheduling, monitoring or delivery without a real underlying owner/receipt.

## Explicitly out of scope

- Full downloadable/installable tool-pack system.
- Marketplace or remote package delivery.
- Cloud-only scheduling backend.
- Enterprise team/project management.
- Silent dependence on OEM Calendar/Clock/Todo apps for core Organizer semantics.

Those can be handled in separate issues.


## Universal Search / Research source

Organizer must include a first-class **Search / Research** information source in addition to structured sources such as weather, calendar, RSS, alarms, and clocks.

This source is intended to let Halo gather general-purpose information and references from the public web for:

- daily and weekly briefs
- topic monitoring
- background research
- current-events context
- fact/reference collection for delegated Halo tasks
- filling knowledge gaps for local models
- supplying source material to OpenRouter-backed models
- building a user-specific agenda based on the user's active commitments, interests, linked Workspace objects, and saved context

The goal is closer to a general web search capability than a fixed vertical API. Halo should be able to perform broad queries, discover relevant pages/documents, extract useful text/metadata, retain source provenance, and pass bounded results into the runtime/model context.

### Implementation direction

The implementation may use one or more of:

- an internal lightweight search/crawler pipeline
- direct retrieval from openly accessible web pages
- open/public search endpoints where legally and operationally appropriate
- local indexing/caching of fetched documents
- OpenRouter models capable of research/search when available at no additional subscription cost to the user
- local Halo models for query planning, filtering, ranking, extraction, summarization, and synthesis

Search-provider availability may vary. The architecture therefore must not hard-code one commercial provider or make Google itself a mandatory dependency. The UX may present this as a Google-like general search experience, but the underlying system must remain provider-agnostic and truthful about which source actually supplied each result.

### Cost requirement

The baseline Organizer research flow must not require the user to buy another subscription.

Preferred order:

1. Halo-owned/local retrieval and processing.
2. Free/open/public data sources where suitable.
3. OpenRouter/free-route model assistance when available.
4. Optional paid/provider-specific integrations only as explicit opt-in extensions.

A missing paid provider must never make core Organizer search unusable if a free/local path can satisfy the request.

### Model-facing contract

Search should expose a bounded, structured result set to local or routed models, including where available:

- query
- title
- canonical URL/source identifier
- source type
- retrieval timestamp
- short extracted passage or summary
- publication/update time when known
- confidence/relevance signal where genuinely computed
- provenance/citation metadata

Models should reason over retrieved evidence rather than silently invent current information.

### Brief integration

Briefs should be able to include a **Research / Web** section generated from saved user intent and active Organizer context.

Examples:

- today's relevant industry news
- updates related to active projects
- important changes affecting a planned task
- background information for an upcoming meeting
- follow-up research requested in a delegated Halo task

This should be driven by explicit user interests, active commitments, saved sources, and current context rather than an opaque engagement feed.

### Search safety and resource limits

The crawler/retrieval layer must be bounded:

- rate limits
- fetch size limits
- timeout/retry policy
- robots/site policy compliance where applicable
- clear source provenance
- deduplication
- stale-cache handling
- no uncontrolled recursive crawling
- no hidden cloud persistence requirement

### Additional acceptance criteria

- [ ] Organizer includes a general Search / Research source, not only fixed vertical feeds.
- [ ] Search can provide evidence to both local Halo models and routed models.
- [ ] Retrieved results preserve source provenance/citations.
- [ ] Baseline search/research does not require a paid subscription.
- [ ] Architecture is provider-agnostic; Google-like search UX does not imply a hard Google dependency.
- [ ] Briefs can incorporate web research based on explicit user context and active commitments.
- [ ] Retrieval is bounded, rate-limited, deduplicated and cache-aware.
- [ ] Current information is retrieved before models are asked to synthesize it.

