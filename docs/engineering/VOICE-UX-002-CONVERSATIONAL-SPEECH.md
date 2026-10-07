# VOICE-UX-002 — Conversational speech

HaloSpeechOutputCoordinator remains the only playback owner. StreamingVoiceResponse
holds an eight-chunk queue, a bounded sentence buffer, and SHA-256 prefix digest and
length. It never persists partial text. StreamingSpeechPrepared establishes exact
request/interaction/route identity only after tool-free preparation; token events
from any other request are ignored. The final TtsRequested is authoritative.

An audible stream finishes with an unspoken suffix only if the final normalized
sanitized prefix matches. Otherwise playback stops with FINAL_MISMATCH. Before
first audio, the queue/setup is discarded and the existing full-response path
speaks exactly once. Synthesis or route failure before audio permits final
fallback; failure after audio stops without replay. One route/focus/output lease
spans chunks. Synthesis selection is pinned once; voice/model/style changes apply
to the next response, credential revocation and explicit Stop still cancel calls.

Spoken interruption consumes content-free SpeechActivityObserved intervals from an
active capture. Positive duration accumulates continuously and resets on silence.
Explicit activation is distinct from VAD/wake/automatic/unknown. No semantic model
is installed or downloaded; an optional classifier has a tested integration seam.
The playback lifecycle, not synthesis request/generation completion, determines
whether output is active. Rejected echo changes nothing.

VoiceResponsePolicy is a host snapshot carried into portable preparation. It
selects spoken framing without changing input modality, role configuration or
model budgets. fallbackFor is the shared response-origin policy. Explicit preview,
study narration and routine attention delivery keep their intentional policies.
No Room migration, partial-response persistence or new audio provider is needed.

Automated tests cover policy/timing/physical override, stale capture, classifier
backchannel, early sentence, short-answer fallback, prefix reconciliation, bounded
queue cancellation, route/provider pinning, exactly one playback lifecycle, settings
persistence, eligibility boundaries and VAD evidence. Physical acceptance is separate.

Run one compact physical check with the configured provider: ask for a brief RAM
explanation; hear a sentence before completion and no replay; interrupt with
sustained speech while successor capture continues; verify a short acknowledgement
continues Natural playback; Stop leaves no late chunks. If glasses are available,
verify same-origin output and silent phone after disconnect with fallback off.
Do not repeat download, lifecycle or Android-action campaigns.

## Verification — 2026-10-07

The serial automated gate passed: runtime SDK 48, speech engine 20, cognition 216,
runtime speech 3, Android host 20, app 269 and ElevenLabs 9 tests: 585 total with
zero failures, errors or skips. The debug APK assembled. Fallback regression also
proves sticky Android selection and one synthesis attempt per failed chunk.

The APK installed and opened on the development phone. The one synthetic typed
RAM question resolved the existing OpenRouter / google/gemma-4-31b-it:free target
with TEXT input, tool mode NONE, and no tools included. It returned RATE_LIMITED /
HTTP 429 before any token or audio. The prepared speech stream was cancelled.
The configured model/provider and existing fallback preference were preserved;
no second request, download or alternate-provider trial was performed. This proves
submission and failure cancellation, not early audible speech or STT acceptance.

Physical early speech, sustained microphone interruption, brief backchannel, Stop
during audible playback and wearable route/disconnect acceptance remain pending.
A Bluetooth headset was visible, but no HeyCyan route acceptance was performed.
