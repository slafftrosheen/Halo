# HALO-VOICE-142 — Local-first voices and starter model onboarding

Date: 2026-10-19
Scope: Halo Android only. User-reported physical acceptance plus source changes. No builds, CI or new physical acceptance were run for this batch.

## Tester evidence and boundaries

- User reports having heard **all 10 Supertonic voices across EN/LV/RU/PL** on their physical Android phone. Quality is especially good in English and perceived latency is satisfactory; no objective real-time factor or latency histogram was captured in this report.
- User reports Kokoro **1.1** works well. The published multilingual pack currently advertises **EN/ZH**, not LV/RU/PL. Do not infer tested language coverage beyond that report.
- Supertonic 3 INT8 is promoted out of catalog experimental status. Its existing `supertonic-3-candidate` catalog ID is deliberately retained to avoid breaking stored identities. This is a *supported pack definition*, not a claim that every model archive, voice, runtime or device is qualified.
- Because the conversation does not supply the active pack fingerprint or audibility ticket, **no device-specific voice approval was silently written**. Settings → Speech output → Supertonic now exposes explicit attestation covering exactly 10 voices × 4 languages on the selected, physically verified pack. The user must confirm the statement on-device. Upgrades, reinstalls with different hashes and alternate packs do not inherit approval.

## Routing

Automatic TTS preference: (1) installed Supertonic 3 INT8 with an exact approved fingerprint/language/voice; (2) installed Kokoro for its actual EN/ZH language coverage; (3) Android TTS. This selection happens once per utterance in the existing `SpeechSynthesisRegistry` and remains subject to the pinned request, native resource coordinator, the existing Stop/barge-in implementation and local/cloud policy. Explicit TTS selections and Kids/cloud restrictions are unchanged. No network TTS is automatically selected.

## Opt-in onboarding

- **TOOLS / INTENT candidate:** pinned LiteRT **Qwen3 0.6B Dynamic INT4** is first in local model suggestions. Download alone never qualifies AGENTIC or changes tool permissions. Run the measured role suite and select the explicit role only after device verification.
- **Local CHAT alternative:** LFM2.5 remains optional / English-first, not an automatic multilingual selection. The onboarder links to the existing local model catalog, which controls download eligibility.
- **Speech recognition:** **Whisper Small INT8** is recommended for quality-first EN/LV/RU/PL (roughly 375 MB); **Whisper Base INT8** remains a lighter 161 MB option. User starts downloads, selects the installed STT, and must pass microphone/transcription readiness checks.
- **Speech activity:** **Silero VAD** (~0.6 MB) is an explicit optional install through the existing Sherpa inventory; energy fallback remains available.
- **Speech output:** official archive-pinned **Supertonic 3 INT8** (~129 MB download) is offered from the setup step, installed/selected on user action, then auditioned/approved through the existing speech settings. **Kokoro 1.1** remains an alternative from the same provider screen.

## Manual follow-up

1. Reopen onboarding on a fresh install and ensure none of the starter downloads starts by itself. Verify storage, progress, cancellation, resume and SHA checks.
2. With a verified, selected Supertonic pack and no audible approval, Automatic must **not** use it; approve individual voice or explicit all-40 attestation and confirm AUTO then chooses it in all four languages when requested.
3. Change language, voice and model pack during a response; request pinning and Stop must prevent stale playback and stale approval propagation. Check local memory pressure and route loss.
4. Remove Supertonic, leave Kokoro 1.1 installed, and verify automatic English uses Kokoro while Latvian/Russian/Polish use Android fallback. Test Chinese explicitly only if a supported Kokoro pack is installed.
5. Install Whisper Small and Silero, select STT, record a phrase in all four languages. Check VAD and quality/latency on real hardware, especially with concurrent TTS memory pressure.
6. Download Qwen3 0.6B, run the tool-role suite, and confirm no tool role is assigned without a passing capability result. Try LFM English versus the other three languages separately.

## Not changed

No edits to CI, cloud AI providers, platform SDKs, Bluetooth, Linux or brand assets. No forced override of existing user provider choices. This change is source-committed; no new phone/build measurements are claimed.
