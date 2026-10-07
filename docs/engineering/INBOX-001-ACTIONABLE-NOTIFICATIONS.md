# FEATURE-064 / INBOX-001 — actionable private notifications

Implemented 2026-10-07 against baseline 2c07ba0361e6a372c7b727d5d879b5ea57d43203.

## Product flow

Home’s compact Inbox action and Notification Settings open `notifications`.
Rows open `notifications/{notificationKey}` with URI-encoded keys. These are
secondary routes; the five primary tabs remain unchanged, with Home selected.
The workspace groups Android categories into Messages, Calendar, Calls, Media
and Other, newest first, showing app, title/body preview, age and Active/Ended.
The safe Halo conversation glyph is the fallback app icon. Search runs off the
Compose hot path; the filter sheet lists only apps present in permitted rows.
Detail shows full stored content and explicit app privacy navigation.

## Authority and privacy

RoomNotificationRepository, NotificationPolicyManager, NotificationDao and
HaloNotificationListenerService retain ownership. Individual views receive only
ALLOW domain projections, never NotificationEntity or live platform objects.
Policy changes invalidate observation even when Room rows do not change.
SUMMARIZE_ONLY contributes to digests but has no individually browsable content.
HIDE_CONTENT and BLOCK are absent. Existing public-content redaction stays in
force. History is limited to 24 hours / 500 rows; expiry updates the projection.
No new database, retention policy, attention system or runtime layer is added.

LiveNotificationOwner and NotificationReplyRegistry.shared own live truth.
Registry changes, listener state, Room updates and screen resume refresh UI.
A deadline timer handles actual registry/history expiry rather than frame polling.
A persisted active row alone cannot enable reply or dismissal after process death.
Listener loss disables live actions without deleting permitted history.

Reply uses the real app action label, a ViewModel-only draft (maximum 500 chars)
and a confirmation containing target/title/text. Confirmation captures an opaque
per-capture fingerprint. Before dispatch the source rechecks current policy,
retention, active row and live ownership; the existing dispatcher rejects action
replacement. PendingIntent.send success means only DISPATCHED, rendered as
“Reply handed to app” and “Delivery not confirmed”. Failures never claim sent,
delivered or read. Drafts survive normal rotation but are discarded on leaving
detail and are not persisted or restored after process death.

Dismiss confirmation calls LiveNotificationOwner.dismiss. Success reads
“Dismissal requested”; only the listener callback reconciles the active Room row.
There is no optimistic deletion. Viewing does not create Activity events; this
feature does not invent receipts outside the existing tools’ durable paths.

## Explicit local digest

Digest windows are 15 minutes, 1 hour, 3 hours and 24 hours. The existing
DeterministicNotificationDigestSummarizer renders notification/app counts,
category counts and per-app counts. “Local summary · no AI · no cloud” is visible.
Opening any feature screen never initiates inference, network, model loading,
notification-to-memory or tool execution. Notification prose remains untrusted
data. Ask Halo and Create Reminder are deferred. Existing notification tools
retain their meanings. No notification content or keys are added to System Check.

## Validation

Focused tests cover policy transitions and redaction, filtered search, removed /
disconnected / restored history, actual reply labels, registry expiry and action
replacement, dispatcher outcomes and truthful copy, deterministic content-free
digest output and secondary navigation selection.

Required serial gate: `:runtime:cognition:test :app:testDebugUnitTest
:app:assembleDebug --no-parallel --max-workers=1 --no-daemon` with 2 GB heap.
The prescribed serial gate passed: 440 tests, zero failures/errors/skips; debug
APK assembled. After the expiry/rotation correction, all 11 focused Inbox tests passed again
and the final debug APK assembled. `git diff --check` passed.
Physical acceptance is not performed by this implementation batch.

## Manual physical acceptance (pending)

Allow a messaging app; inspect live and ended items. Confirm real action labels,
reply review/cancel and dispatch copy without a delivery claim. Confirm dismissal
request then listener-driven state change. Disconnect/reconnect notification
access, expire a registry entry, restart the process, and change each privacy
policy with screens open. Check search/filter privacy, all four digest windows,
large text, accessibility and normal rotation. Actual external delivery remains
unconfirmed unless independently observed in the destination app.
