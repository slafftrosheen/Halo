# LiteRT and voice verification, 2026-10-13

Base: `f0f47f9ad86e4252f0086db3c91355e60fa37e15`, pulled from origin/main before changes.
Device: Redmi `23124RA7EO`, Android 15; LiteRT-LM 0.18.0.

## Completed device results

All five installed model artifacts passed physical size/hash verification. Each passing backend completed both the raw native control probe and Halo provider verification with nonempty generated text. These are short text smoke checks, not quality, vision, tools, or long-session acceptance.

| Downloaded model | CPU | GPU | Provider load / first token |
| --- | --- | --- | --- |
| Qwen 3 0.6B INT4 | PASS | PASS; stability 3/3 also passed | CPU 3.5s / 10.8s; GPU 9.7s / 4.1s |
| SmolLM2 135M | PASS | PASS | CPU 0.9s / 4.3s; GPU 2.4s / 5.0s |
| Qwen3.5 0.8B VL int8 | PASS, text only | PASS, text only | CPU 53.2s / 2.3s; GPU 241.9s / 5.2s |
| DeepSeek R1 Qwen 1.5B Q8 | PASS | FAIL before first token | CPU 9.7s / 3.2s |
| Qwen 3 1.7B INT4 | PASS | FAIL before first token | CPU 8.0s / 17.5s |

Qwen 1.7B GPU logs identify an OpenCL invalid work-group size `{1, 1, 512}`. DeepSeek GPU failed at native execution with a LiteRtLmJniException. Neither failure is a missing model artifact. Automatic GPU selection remains subject to the existing stability qualification.

SmolLM2 did not reliably follow the instruction to reply with OK. DeepSeek's short sample contained reasoning prose; Qwen 1.7B's provider verification sample contained closing thinking tags. Nonempty generation does not prove a useful final response. Qwen3.5's very long GPU initialization can look stuck; native initialization remains synchronous.

Structured stage results: [model-diagnostics.jsonl](artifacts/LITERT-VOICE-2026-10-13/model-diagnostics.jsonl).

## Changes delivered

- Completed WorkManager downloads now leave Verifying and reconcile into Installed or a concrete failure. Inventory refresh keeps the last ready model list available; explicit verification rehashes the requested artifact.
- The pinned Qwen 1.7B chat template accepts the content lists emitted by LiteRT-LM's Kotlin API.
- Model settings expose the existing GPU stability test and its failure detail; generation probes have a timeout.
- Kokoro uses an explicit JNI callback bridge. The prior synthetic lambda caused a reproducible native crash; the bridge correction stopped that crash.
- Kokoro now offers the pinned full-precision multilingual pack, validates finite non-silent audio before installation/cache, and avoids old silent cached audio. The previous int8 multilingual pack returned silent PCM on this device. This Sherpa pack supports English and Chinese; Russian/Latvian need another provider.
- Voice setup shows provider selection, pack/language state, voices and explicit provider playback tests. Settings share card shape, screen spacing, section insets and headers.

## Build and acceptance boundary

Latest `:app:assembleDebug` and `:app:assembleDebugAndroidTest` passed. Focused app tests: 24 passed; LiteRT tests: 148 passed. Localization check: 2306 EN/RU/LV strings; `git diff --check` passed. Both latest APKs installed successfully.

The full-precision Kokoro English/Chinese device test was stopped at the user's request to finish and push. Its result is NOT_RUN/incomplete; audible playback is not confirmed. Final settings screenshots and GPU context experiments were not completed. Existing screenshots in the artifact folder show the earlier layout, before the final shared section inset change.

The user's AI selection and speech provider were preserved/restored. No human "I heard it" confirmation was fabricated. User will perform further device acceptance.
