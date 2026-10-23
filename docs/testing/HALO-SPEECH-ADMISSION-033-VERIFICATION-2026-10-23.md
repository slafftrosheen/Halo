# Native speech admission cancellation — 2026-10-23

Base main: `8739c2a6cd67de457f131253bdb7f8573444e9fc`. Issue #33 stays open.

## Implemented

Supertonic's resource acquisition now executes inside its existing non-cancellable cleanup boundary. Kokoro no longer skips cleanup when acquisition is interrupted before returning success. Both keep the current synthesizer lock, native engine owner, native-close-before-resource-release ordering, successful warm/stream residency policy and output route. A partially reserved `compute.tts` resource can no longer remain behind after a cancelled admission attempt.

No new scheduler, speech engine, UI, tool, provider fallback, download or inference. The existing bounded dependency scheduler, parallel context/preparation, measured-backend policy and content-free traces remain authoritative.

## Validation

Production and regression sources preceded the focused gate; read-only review found no concrete blocker. NativeSpeechAdmissionTest uses actual Kokoro/Supertonic providers, bounded fixture packs, the real generic resource manager and the real coordinator. An independent resident ASR owner's idle callback blocks coordinator admission. Each test observes the TTS compute reservation, cancels the request, releases the block and checks that TTS execution/reservation is gone while independent ASR residency survives. Cancellation occurs before native initialization; fixture bytes do not establish synthesis/model compatibility.

Focused app gate passed on the first run in 5 minutes 42 seconds: 37 cases across NativeSpeechAdmissionTest (2), ConversationalVoiceTest (12), SpeechPlaybackLifecycleTest (19) and SupertonicAcquisitionOwnershipTest (4), zero failures/errors/skips. Production and unit-test compilation passed. Raw log/XML results are in `artifacts/HALO-SPEECH-ADMISSION-033-2026-10-23/desktop/`. No APK assembly or device install in this batch; current ADB and Windows device inventories show no Android phone.

## Remaining acceptance

No new physical latency, CPU/GPU placement, throughput, power, thermal, peak memory, real audible output, barge-in or headset/glasses result is claimed. #33 still needs matched physical baseline/concurrent measurements, permitted provider flows and the required manual/backend/device cases. Source correctness cannot establish median/p95 performance improvement.
