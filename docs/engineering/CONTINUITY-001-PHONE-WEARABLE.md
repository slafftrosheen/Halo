# FEATURE-071 / CONTINUITY-001 — Phone ↔ wearable continuity

Implemented against `1e1411960e7c1158d2adc9ef419a0ad7bb63d05c`. Room remains version 26; additive journal payloads require no migration.

## Identity and ownership

A conversation is a long-lived container. A fresh phone, wearable or Android-assistant follow-up captures that conversation before execution and has a fresh interaction/request/capture identity. A resumed unfinished run retains its AgentRunId and original interaction/capture evidence. Conversation navigation, device presence and connection are not execution authority.

`CognitiveTurnRequest`, `AgentRequest`, `RunPrepared` and `CognitiveResponseCommit` carry optional conversation ownership. Context assembly reads the bound conversation. Targeted Room commits check the existing conversation and ACTIVE Workspace object, append under one transaction, and leave the active conversation untouched. Stable commit identities are global: SQL LIKE selects escaped candidates, then decoded JSON verifies exact identity. Missing or archived conversations are never recreated by recovery or commits. `activateExistingConversation` activates only available existing objects.

Legacy explicit resume appends one `ContinuityBound` step. Explicit current-conversation continuation appends a new binding without rewriting historical RunPrepared. Missing owned conversations block; Home offers Continue in current conversation or Cancel task. Startup classification and routine NO_RESUME behavior remain conservative.

## Endpoint handoff

Portable endpoints are Phone, AndroidAssistant and Wearable(exact device ID). RunContinuityResolver projects latest binding/handoff before original run route. The coordinator owns delivery changes only: it cannot generate, execute tools, connect a device or migrate compute. Handoff keeps historical origin. Android eligibility reads CapabilityCatalogue, DevicePresenceRepository and DeviceSessionManager; association alone is insufficient. No implicit connection occurs when the sheet opens.

Home offers a secondary Continue on… chooser during active/waiting work or active voice when a ready alternative exists. Phone takeover explicitly allows phone delivery. Wearable handoff fixes the exact device and DISALLOW_PHONE. Route failure remains visible and cannot silently speak on the phone. Final presentation/TTS resolves the current handoff after semantic commit. Handoff and final-delivery selection serialize; a completed run cannot be resumed by changing its endpoint.

Audio affinity retains the historical input, replaces intended target, clears stale endpoint evidence and enters PENDING. Audible playback stops on handoff without replay or PCM splicing. Unspoken buffered streaming may use the new final route. Physical activation remains interruption: it cancels old work and starts a fresh turn; it never rewrites origin or replays tools.

## Recovery and pending input

Fresh and recovered provider work share lazy registration and exact-job cleanup in ActiveTurnCoordinator. Duplicate same-interaction resumes cannot cancel the winning job; the existing durable resume claim is still authoritative. Automatic resume does not replace an active user turn. Every recovery variant receives bound conversation, original origin/capture and journal-resolved delivery. Unknown external dispatch remains non-replayable.

`InteractionResponseSubmission` adds the submitting endpoint to the existing response protocol. The existing atomic resolveInputIfPending admits the first valid response; only its accepted endpoint may journal INPUT_MOVED before resume. Phone UI responses use Phone explicitly. Bounded recognized speech can answer one pending voice-suitable confirmation or nonsensitive text input; ambiguous multiple pending requests and visual-required surfaces are not resolved by guessing. Waiting status is not a final semantic answer and does not complete its run.

## Capture and privacy

A five-minute, dismissible recent-capture projection reads completed existing ActionReceipt events. It holds only exact Workspace object ID, source label, media kind and timestamp. Open in Notebook navigates to that exact note. No artifact bytes, URI/path, Bluetooth address, user text or tool arguments enter the card. No capture store, Inbox or automatic vision submission is added. Existing explicit interaction media binding remains authoritative.

Committed conversation metadata adds safe inputSurface values. System Check reads content-free bound/active/run flags, endpoint kinds, handoff status and audio-affinity state. Opening or refreshing it performs no inference, handoff or connection.

## Process death

RunPrepared/ContinuityBound/InteractionHandoffRecorded and original route/capture evidence survive Room reopen. Portable optional fields decode old rows conservatively. The ephemeral coordinator/capture card does not survive process death; no process state restores execution automatically. A subsequent explicit resume restores journal truth.

## Verification

Automated gate: **PASS — 839 tests, 0 failures/errors/skips; debug APK assembled**. Runtime SDK 48, cognition 232, speech 3, app 350, HeyCyan 206. The mandated serial gate ran on the final source with one worker and no daemon. `git diff --check` and staged whitespace checks passed. The APK installed successfully via ADB on the development phone and MainActivity launched. Tests cover delayed generation with conversation switching, global/concurrent commit identity, fresh surface identities, Room journal reload/legacy ownership, missing conversation blocking and explicit rebind, duplicate tracked resume, one winning cross-endpoint input response, exact endpoint eligibility, final delivery and capture metadata/TTL.

Physical phone + HeyCyan acceptance: **NOT RUN**. ADB development phone is connected and unlocked; Halo Devices reports **No saved devices**. There is no eligible saved/connected wearable for the paired acceptance pass. Required single pass: phone RAM → glasses ROM in one conversation with fresh IDs; glasses answer visible on phone; explicit handoff during a task with one provider generation and one commit; barge-in; phone takeover preserving wearable origin; disconnect without phone audio; exact captured Notebook object; conversation navigation during slow generation; cross-endpoint pending input if reachable. Automated tests and APK installation are not hardware acceptance. No model download, role suite, GPU qualification or general device acceptance is part of this batch.
