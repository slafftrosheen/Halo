# Voice Mode command recovery #38

Base `6628274679ed74b0933fd2cb33a3ea9f5846eaae`, freshly fetched with zero divergence before the source freeze. Scope: queued host control correction; no replacement Voice Mode, audio engine, speech recognizer, provider route or microphone permission owner.

## Source behavior

HomeViewModel's existing serialized host command path now uses operation tickets in `VoiceInputCommandQueue`. Explicit starts/stops, mute/unmute, replay and delayed automatic continuation recheck the ticket after waiting. Ending a session invalidates a pending start even before its initial conversation becomes ready. Earlier stop commands cannot stop a newer explicit capture. Automatic continuation requires the same active conversation, an unmuted session, no newer capture, available speech and microphone permission.

Repeat retains its originating conversation and exact last-response object identity. It rechecks both after a suspending capture stop before publishing the fresh replay request. A queued repeat which optimistically muted capture but loses its source stops through the canonical owner rather than leaving a falsely muted microphone state. Playback remains a TTS request, not proof that audio was heard.

Current denied starts/unmute reconcile optimistic listening/session state through the existing stop owner. Stale denied requests leave newer state untouched. Failure callbacks cannot clear a newer request's state; cancellation is rethrown from the changed asynchronous control paths. Existing already-dispatched runtime cancellation/capture identities remain authoritative; queue tickets do not revoke an action already delivered to the audio engine.

## Integration boundaries

This corrects host controls already used by Home/Chat and the existing Study/Notebook entry callbacks. It adds no new assistant tool, navigation surface, hidden microphone activation, provider transmission, permission, model-selection change, UI copy or diagnostic display. The explicitly initiated foreground microphone/Android permission boundary remains in place. Typed and STT-final cognition routing, tool authorization and action replay policy are unchanged. This is not new voice/tool functional-parity or actual spoken acceptance evidence.

## Source review and regressions

All production and eleven scheduler regression sources were written before the focused gate. Read-only review found a current-denial optimistic-state gap; it was corrected with explicit current-only rejection handling and two state reconciliation regressions. Follow-up review found no remaining actionable blocker.

Authored cases: mute during the 250ms reply delay; queued unmute followed by stop; queued old stop followed by new capture; permission/conversation checks after waiting; leaving/returning to the same conversation; current denial clears optimistic flags; stale denial preserves newer flags; queued and active cancellation release serialization; command failure recovery; rapid repeated controls execute only the latest waiting intent. These are deterministic host queue/state-callback fixtures, not physical capture or complete HomeViewModel end-to-end tests.

## Gate and acceptance

Focused app production/test compilation plus `VoiceInputCommandQueueTest`, `ConversationalVoiceTest` and `SpeechPlaybackLifecycleTest`: PASS, 5m38s; 42 cases (queue11, conversational stream/interruption12, playback lifecycle19), zero failures/errors/skips. No APK assembly or installation in this batch while ADB inventory is empty.

#38 remains OPEN. Its physical 10+ turn conversation, echo rejection, offline/generation interruption, model change during playback, measured latency/energy/thermal/residency, real STT and permitted cloud-boundary acceptance remain unverified. Earlier #39 native Workspace fixtures do not establish Voice Mode acceptance. No CI/Actions or remote-provider call is part of this batch.


Compiled app production tree: `86c51883379a2ce79443fa2ef996b20113db56be`. First focused gate passed; no production changes followed it. Raw log and three result XML files are in `artifacts/HALO-VOICE-COMMAND-038-2026-10-23/`. No APK was assembled or installed for this batch; the last installed package remains the first #39 APK, and the last built package remains the final #39 APK. Neither contains these #38 changes. Latest ADB inventory is empty.
