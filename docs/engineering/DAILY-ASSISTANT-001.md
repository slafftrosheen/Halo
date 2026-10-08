# FEATURE-070 / DAILY-ASSISTANT-001

Implemented against FEATURE-069 (`1243ac1d`), using its final Study session and review APIs.

## Ownership

Daily Assistant owns presentation priority only. Attention owns interruption and delivery priority. The agent journal owns execution truth. Notification Inbox owns notification content; Study owns learning state; Memory owns saved personal context; Automations own scheduling; device/audio owners own connectivity and route truth.

`DailyAssistantController` observes existing bounded flows and creates a process-only snapshot through the pure `DailyAssistantProjector`. It has no router, model, speech, execution, scheduler, or persistence dependency. Home resumes refresh time and initiative policy without a polling loop. There is no Daily table, migration, restored cache, automatic brief, notification, or new background job.

## Presentation

Home remains conversation-first. Today shows at most one primary item and three secondary items. Answers appear above Today; listening, transcribing, reasoning, execution, speech and visible responses use a compact summary. See all opens an ordinary bottom sheet with nonempty priority sections. The composer remains separate. Healthy devices appear as context, never as manufactured work. An empty projection says “You're caught up”; persisted future routine times can still appear as Next.

Approval and clarification lead, followed by immediate Attention, recent failed/blocked/partial routines, replyable Inbox, due Study review, soon Attention/route problems, upcoming automation, permitted learning suggestions, Study resume and ordinary notifications. Importance, oldest unresolved need and stable ID resolve ties. Expired Attention is excluded; journal input resolution overrides a lingering queued input. Attention takes precedence over matching run-input and routine-failure projections. A matching typed failed/blocked/partial routine outcome retains failure priority and the exact automation action even when the queued Attention intent is ordinary Inform. Completed and expired journal input prevents stale queued input from reappearing. Inbox is one aggregate, not individual rows. Due review suppresses lesson resume.

Review targets the oldest due card's lesson, with counts across lessons. Resume chooses the most recently updated active lesson when no cards are due. Upcoming routines use enabled state and stored `nextRunAt` within the next 24 hours; schedules are never calculated or reconciled by Home. Routine observation has an explicit read-only decode path so malformed rows are not disabled merely by Home collection. Failed routine evidence is limited to the preceding 24 hours. Completed run history remains in Activity.

Daily actions are closed typed values. AppShell maps them to existing owner routes. Activity accepts a run focus and scrolls/expands the matching story. Automation accepts a routine focus using its existing detail screen. Missing focus targets fall back to normal owner views. Study uses its existing focused review route and Memory its Suggestions section. Actions do not execute approval, reply, review, promotion or automation work themselves.

## Privacy and boundaries

Inbox contributes counts and replyability, never notification titles, bodies or drafts. Learning contributes a policy-gated pending count, never suggestion content. Study contributes session metadata and card scheduling, never source material or card answers. Agent input wording remains at Activity; Home uses generic waiting copy. Voice uses existing affinity labels, distinguishes last ENDED replies from current routes, and shows a warning only for LOST/UNAVAILABLE. Presence counts only READY/CONNECTED states, not associated or nearby devices. Unknown device names use a generic label; MAC addresses and UUIDs are rejected. Logs include item/count/action kinds only.

Opening or projecting Home does not mark Inbox read, resolve Attention, accept learning, mark memory used, review Study, or change routine schedules. Existing notification and Study owners continue to own their expiry/due clocks. The existing main Halo card owns active task progress through runtime/plan projectors; Daily does not duplicate that task or invent another progress calculation. Existing explicit capability setup remains separate from daily work.

## Verification

Consolidated gate passed: runtime SDK 48, cognition 216, app 340; total 604 tests with zero failures/errors/skips. Debug APK assembled and `git diff --check` passed. Deterministic regression coverage checks priority, ties, expiry, deduplication, journal input authority, oldest Study review target, stored schedules, privacy-safe counts, voice-route state and action routes. Physical acceptance is separate: one compact phone pass checks available Inbox/Study/learning/waiting/routine states, navigation and conversational collapse; unavailable fixtures or wearables are NOT RUN.

Optional AI briefs, daily routine templates, widgets and wearable brief delivery are deferred. FEATURE-071 is not started by this batch.

### Connected phone evidence

The final APK installed successfully on the development phone and the Halo process was observed running. Android keyguard remained active and the screen returned to off after normal ADB wake/dismiss and launch. No credential bypass was attempted. Inbox aggregation/navigation, focused Study review, learning navigation, waiting-task priority/navigation, stored routine display/navigation, wearable routes and conversational collapse are all **NOT RUN physically**. Automated coverage and installation do not establish these physical acceptance checks. The previously requested single manual pass remains pending; no artificial user notifications, routine schedules or Study state were inserted for this pass.
