# Cloud images and one privacy mode — 2026-10-22

Based on `2d7b650faa3ade3c00f504f13bf9f91495d6b73d`, synchronized with `origin/main` before changes. The code, tests and evidence in this change belong to the same working tree. No hosted CI was requested or run.

## Result

The connected Samsung SM-G990B2 (Android API 36) passed authenticated OpenRouter image questions through both the router and full chat. The final tests explicitly removed per-image grants: **Cloud mode alone enabled the image path**. Private mode rejected an explicitly pinned cloud model before inference. Notebook and Canvas image persistence passed with a clearly labelled offline raster fixture.

Live AI image generation reached OpenRouter and returned **HTTP 402**. It did not produce an image. This is a provider credit/key-spending-limit block, distinct from sharing permissions or TLS. The UI now preserves the draft, shows the credit error and links to the provider credit page; it does not retry automatically. The actual studio state transition is also covered by a hermetic integration test. No account top-up was attempted. The single QA attempt used a $0.10 local reservation; a reservation is not a provider invoice.

## One sharing decision

The persistent **Private / Cloud** switch appears above navigation and in full-screen Settings pages. New installations/migrations without an explicit mode start Private. Unknown stored modes fail closed.

- Cloud enables eligible conversation, image, memory, workspace, screen, audio/video and device-context sharing together. Previous per-file, per-conversation and workload sharing choices cannot silently disable parts of Cloud mode.
- Private confines AI processing to the phone and blocks cloud inference, network speech, image creation, paid web lookup and web-fetch tools. Active cloud jobs/cancellable operations are cancelled when permission changes.
- This is sharing authority, not a model download, capability qualification, Android permission, parental approval, action confirmation or spending authorization. Those existing boundaries remain.

Separate image/memory/workspace sharing controls were removed from Privacy, capture review, file details, Learning, Costs and workload settings. Image creation explains Private mode before dispatch. Blocked cloud actions offer **Switch to Cloud**; camera/microphone denial paths link to Android app permissions. Provider setup and credit failures have their respective recovery routes.

The Settings summary follows the mode rather than incorrectly saying “In the cloud” while Private is selected. Phone inspection caught and fixed status-bar overlap. The real toggle was tapped in both directions, remained synchronized across Settings and Appearance, and was left Private. The Privacy page contained one privacy control, with no per-category toggles.

## Generation wiring

Notebook and Canvas continue to use the shared image studio/service and owned local assets. Image-output model selection stays separate from chat/vision-input selection.

OpenRouter image metadata now requires advertised `input_references` support before offering editing, and retains whether a reference is required. Create/edit lists select compatible models. The request omits optional `n`, allowing models whose supported parameter set does not contain it; the endpoint's single-image default is used. Source identity is included in approval fingerprints and source access is rechecked before dispatch and after reservation persistence. Kids readiness is checked at the image/media boundary.

The provider contract and recovery were checked against [OpenRouter's image-generation guide](https://openrouter.ai/blog/tutorials/image-generation/), [live image-model metadata](https://openrouter.ai/api/v1/images/models) and the [credit page](https://openrouter.ai/settings/credits). The phone discovered 55 raster image models through the configured OpenRouter account. Direct OpenAI and Gemini image adapters were unconfigured on this phone; no live inference claim is made for them.

## TLS investigation

Final public-host probes retained normal certificate/hostname verification and passed all nine combinations:

| Host | Default | TLS 1.2 only | IPv4 only |
|---|---|---|---|
| openrouter.ai | TLS 1.3 / HTTP 200 | TLS 1.2 / HTTP 200 | TLS 1.3 / HTTP 200 |
| api.openai.com | TLS 1.3 / HTTP 421 | TLS 1.2 / HTTP 421 | TLS 1.3 / HTTP 421 |
| generativelanguage.googleapis.com | TLS 1.3 / HTTP 404 | TLS 1.2 / HTTP 404 | TLS 1.3 / HTTP 404 |

421/404 are root-route HTTP responses after successful TLS, not authenticated inference acceptance. The phone had validated Wi-Fi and no VPN. Both IP families resolved; these probes do not claim an IPv6-only connection test. Authenticated OpenRouter vision succeeded, and generation received an HTTP 402 response.

The earlier Xiaomi handshake resets in `HALO-IMAGE-QUESTIONS-2026-10-22.md` were not reproduced on this different connected device. Their original network/device cause remains unconfirmed. No trust-all TLS, certificate bypass, production protocol downgrade, pinned DNS address or blind generation retry was added. The opt-in diagnostic test records content-free evidence for a future reproduction on that device.

## Verification and artifact

- Full `haloVerify`: **2,363 tests, zero failures/errors/skips**, including local-provider unit suites.
- Final targeted app gate after the remaining UI/capture changes: **173 tests, zero failures/errors/skips**. It includes Settings, Kids, privacy/routing, Home, capture, admission and credit-recovery cases. These overlap the full suite and are not an additional unique-test count.
- Localization: **3,749 EN/RU/LV strings** checked; both language-generator Python tests passed; `git diff --check` passed.
- Final phone tests: privacy boundary/pinned-cloud rejection; Notebook/Canvas offline persistence; red and blue cloud questions; full chat red-image question; all nine TLS probes — passed.
- Physical UI: one mode control, correct status-bar placement, synchronized Private/Cloud state in Main Settings and full-screen Appearance, and Private restored after testing.

Installed app and test APKs with `adb install -r`, preserving app data. The installed base APK checksum was read back and matched the local final artifact:

```text
Local checkpoint: releases/checkpoints/halo-cloud-privacy-2026-10-22-debug.apk
Bytes: 285507321
SHA-256: fa8783f0d23deff9019cb79e481a71781b0f17560635737b5208fe0759cec9b1
```

The APK was built from the tested working tree before this commit; its source marker is the prior HEAD plus dirty state. The checkpoint APK is local/ignored, not a Git artifact. Structured evidence is in the adjacent `-host-results.json` and `-phone-results.json` files. They contain stages, model identities, timings and verdicts, not credentials, prompts, private media or network names. Synthetic QA notes/conversations are labelled as such; original provider/model selections and active conversation were restored. No existing pending user action was approved.

## Local image-generation options

Gemma image understanding does not make it an image generator. The existing local LiteRT provider is not advertised as an `AiImageProvider`.

An on-phone option would require a separate diffusion runtime and model. [Google's Android MediaPipe image-generation codelab](https://codelabs.developers.google.com/mp-image-generation-basic-android) calls the task experimental and recommends at least 8 GB RAM; its [Android guide](https://ai.google.dev/edge/mediapipe/solutions/vision/image_generator/android) requires compatible converted foundation weights downloaded separately. This Samsung reports 5,493,788 KiB usable RAM. Local generation therefore needs its own memory/GPU/thermal qualification before being offered. No heavy model download or physical local inference test was run here. A separate server would also require an image-provider adapter and Cloud mode because processing would leave the phone.
