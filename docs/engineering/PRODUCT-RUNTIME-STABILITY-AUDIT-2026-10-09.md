# Halo product/runtime stability audit — 2026-10-09

**Scope:** read of current `main` source and current-state documents, plus public open-source and Play-market feature references. The starting source head was `510ec86753e44b19952ca16ce651a99c0c1e124f`; changes from this pass were pushed as later commits. This is a **source review**, not a clean Android compile, phone soak, full security audit, or independent feature benchmark.

**Operating rule:** optimize for Halo's portable cognitive runtime, not for Android or any particular glasses/LLM vendor. Keep host/device-specific code behind adapters. Prefer behaviorally correct existing functions over multiplying tools, models, roles, or tabs. **Do not use GitHub Actions.**

## 1. Market / GitHub reference check

| Reference | Source / product evidence | Useful pattern for Halo | Not a parity claim |
| --- | --- | --- | --- |
| [OneBox](https://github.com/wangzhishou/OneBox) | Android agent with 90+ in-app local tools, permission-bounded execution, per-tool timeouts, multi-step tool use, PiP task progress, adaptive portrait/landscape navigation | Clear run receipts and explicit pending/cancel/retry states; keep capability dispatch bounded and owner-based | Tool count is not a meaningful Halo target; existing tools must complete reliably |
| [CyanBridge / Alternative HeyCyan App](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK), [Play listing](https://play.google.com/store/apps/details?id=com.fersaiyan.cyanbridge) | Device-family adapters, HeyCyan BLE + Wi-Fi media sync, live camera/translation workflows, local model controls | Measured glass/microphone/speaker routes, readable per-device capability truth, media transfer recovery | Experimental or firmware-dependent features cannot be assumed on a BW-AG1 Pro |
| [PocketPal AI](https://github.com/brentonbluehouse/pocketpal-ai), [Play listing](https://play.google.com/store/apps/details?id=com.pocketpalai) | Model-first offline onboarding, model import/download, device fit/benchmark narrative | A credible “download → verify → run offline” happy path, explicit hardware limits and honest progress | A chat-only offline app does not validate Halo's agentic actions or speech |
| [ChatterUI](https://github.com/Vali-98/ChatterUI) | Local / remote models, chat history, fine-grained model controls, integrated TTS | Simple visible defaults and advanced tuning behind disclosure | Halo should not inherit character-chat complexity |
| [Gemini on Android](https://support.google.com/gemini/answer/14579631?hl=en), [Gemini Live](https://www.android.com/articles/gemini-on-android/) | Familiar assistant entry/overlay, screen-context assistance, camera/screen-aware voice | Fast startup, clear listening/responding states, deliberate source inclusion and interruption UX | Cloud live-video features and Google account access are not Halo capabilities by association |

**Market signal:** A wide feature inventory does not compensate for an untrustworthy main path. The most defensible Halo difference is the same *local-first interaction identity* across phone, wearable, runtime actions, Notebook/Study, and an explicit optional remote provider.

## 2. Functional inventory — code-present versus acceptance status

These are bounded observations from source files and `CURRENT-STATE.md`, not blanket assertions of real-world success.

| Domain | Existing implementation / key owners | Review judgment | Highest-value acceptance |
| --- | --- | --- | --- |
| App shell & language | `app/.../MainActivity.kt`, `language/AppLanguage.kt`, `language/UiText.kt`, 1,147 EN/RU/LV `ui.tsv` entries | Broad navigation/localization in source; generated resource sets verified, new Gradle/device gate missing | Rotate/restart/switch language with drafts; verify dynamic strings and narrow LV/RU layouts |
| Direct chat & conversation identity | `ui/screens/home`, `data/RoomConversationStore.kt`, `runtime/cognition` | Provider-boundary retry, partial-output receipt, explicit conversation identity developed; still needs real mixed-provider/busy-device soak | Typed → stream → tool → cancel → retry → history, with no duplicated actions or lost partials |
| On-device models & roles | `providers/litert`, `ai/LocalModelCatalogRepository.kt`, `ai/LocalRoleEvidenceController.kt` | Explicit catalog, pinned download/verify, LiteRT role qualification; performance is device/artifact-specific | Download, interrupted resume, SHA proof, airplane-mode answer; role probes only on exact installed artifact |
| Remote models | `ai/ProviderControlRepository.kt`, `providers/openrouter`, `providers/gemini`, `providers/ollama` | Explicit configuration and catalogue control; no provider succeeds merely by being shown in Settings | Invalid key/429/network loss/model stale/partial stream; never silently change provider or billing boundary |
| STT / voice input | `engines/speech`, `app/audio`, `app/speech/diagnostics` | VAD, ASR probe, language selection and editable uncertain transcripts; RU/LV recognition quality not established by UI translations | Device microphone / connected headset / interrupted STT / Russian and Latvian reference samples |
| Speech output & continuity | `audio/HaloSpeechOutputCoordinator.kt`, `audio/SpeechOutputRouteController.kt` | Routed TTS, optional ElevenLabs/Kokoro and fallback policy; HeyCyan audible success is a physical gate | From glasses mic → glasses audio; verify **no phone-speaker leak** when fallback is disallowed |
| Android native assistant & actions | `assistant/AssistantInvocationCoordinator.kt`, `assistant/AssistantRuntimeBridge.kt`, `di/tools/host` | Native assistant lifecycle and bounded action authority in source; OS permission/role details vary by device | System assistant role, repeated overlay invocation, start failure and cancellation cleanup, safe screen omissions |
| Devices | `embodiment/DeviceAutoConnectCoordinator.kt`, HeyCyan and BuddyBot hosts, `ui/screens/devices` | Registry, reconnection and per-device controls present; reconnect races and exact protocol support require hardware | BT off/on, glasses power cycle, BuddyBot Wi-Fi drop, app process death, endpoint attribution |
| Camera, visual context, media | `capture`, `app/context`, `study/StudyTextExtractor.kt` | Ephemeral capture, explicit image attachment and policy checks present | Large photo/file, unsupported vision model, privacy-denied upload, interrupted save and retry |
| Notebook & Canvas | `data/notebook`, `ui/screens/notebook`, `notebook/WorkspaceUseCases.kt` | Editors, source references, saves/conflict choices, Canvas and media tools implemented | Unsaved edits across process/background, conflict, empty/missing target, backup/export/restore round-trip |
| Study | `app/study`, `data/study` | PDF/DOCX extraction, editable review, source-qualified explanations, routed narration implemented | Multi-language PDFs, malformed/large files, evidence provenance, child-friendly guidance and offline provider limitation |
| Memory & personal learning | `data/memory`, `ui/screens/notebook/MemoryHubScreen.kt` | Confirm-before-promotion and source scoping; UI now localizes memory filters and counts | No inference-based fact written without approval, correct scope, correction/forget, restart and cloud-memory opt-in |
| Automations & follow-ups | `automation/AndroidRoutineScheduler.kt`, `data/watch/RoomContinuationWatchRepository.kt` | WorkManager scheduling, event watches, dedup/limits, explicit review; quota re-save bug corrected | Reboot/timezone change, running edit/cancel, full-capacity update, no duplicate notifications/actions |
| Skills, MCP and phone operator | `mcp`, `skills`, `di/tools`, `ui/screens/settings` | Capability manifests, authorization and supervised semantic phone execution; not every third-party app is supported | Malformed MCP, unreachable server, privilege prompts, stale UI snapshots, denial and irreversible tool approval |
| Notifications, daily assistance | `notification`, `daily`, `data/notification` | Local private inbox/digest and bounded daily projection | Revoked access, updated/dismissed notification, reply once, 24-hour retention and no accidental cloud handoff |
| Backups & storage | `backup/BrainBackupWorker.kt`, `backup/HaloVaultRepository.kt`, `backup/RestoreReconciler.kt` | Local scheduled backup and restore reconciliation; cancellation/retry hardening included below | Encrypted export/import, storage full, wrong password, interrupted import and clean-install restore |
| Security/privacy observability | `security`, `diagnostics`, `runtime/events`, `data/workspace/ContextSharingPolicy.kt` | Permission contracts and structured diagnostics exist; source/credential/user data must stay out of logs | Deny every cloud/screen permission; verify data does not leave device and logs contain no payloads |

**Not a shipped-capability claim:** `CURRENT-STATE.md` flags FEATURE-073 watch physical microphone/PTT ownership as missing; wearable controls remain partial without it. Phone screen control is supervised and limited to supported/accessibility-exposed surfaces. Live always-on video, every-language offline speech quality, arbitrary third-party app execution, and universally working vendor-device protocols are **not established**.

## 3. Confirmed defects corrected in this pass

1. **LiteRT CDN resume integrity and recovery** — `providers/litert/.../LiteRtModelDownloader.kt`: a returned 206 with the wrong/missing range was previously treated as a fresh body. That produces a truncated download and unnecessary integrity failure; now one explicit full-request retry is used, and an unsolicited 206 is rejected. `LiteRtDownloadResumeTest.kt` adds matching MockWebServer regressions. Final bytes and SHA remain authoritative.
2. **Assistant failed-start ownership** — `app/.../assistant/AssistantInvocationCoordinator.kt`: an exception during `delegate.begin` left a phantom active interaction. Failure now resets identity and attempts cancellation/lease cleanup in a non-cancellable section before rethrowing. New `AssistantInvocationCoordinatorTest.kt` covers failure then successful subsequent invocation.
3. **Scheduled backup cancellation/retry** — `app/.../backup/BrainBackupWorker.kt`: a broad `Throwable` catch swallowed WorkManager cancellation and retried without a finite application-level bound. Explicit cancellation is rethrown, fatal errors are no longer recast as retries and transient failures are bounded to four worker attempts (initial attempt count zero through three).
4. **Runtime token/event hot-path output** — `runtime/events/DefaultEventBus.kt`: removed unconditional `println` for every event. High-frequency token events no longer force synchronous stdout traffic; specific diagnostic observers remain available.
5. **Model URL metadata redaction** — `LiteRtModelDownloader.kt`: logs/HTTP failure summaries now display host, not the full URL/query (which could carry signed or sensitive parameters).
6. **Continuation-watch quota update** — `app/.../data/watch/RoomContinuationWatchRepository.kt`: re-saving an already-active watch at global/per-run capacity no longer consumes an imaginary extra slot. In-instance save serialization and `ContinuationWatchQuotaTest.kt` cover quota semantics.

These edits are purposely bounded: no new LLM fallback provider, no extra Android privileges, no changes to stored memory/Notebook formats, no new autonomous actions and no broader cloud sharing.

## 4. Prioritized follow-up implementation and physical acceptance

### P0 — release-blocking

- **Build gate after localization and this pass.** Run `python scripts/generate_ui_languages.py`, verify no generated diffs, then `./gradlew :providers:litert:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` on the Android development host. Also run `python scripts/audit_ui_strings.py --limit 25`. Confirm Kotlin/Compose, resource formatting and Room schema consistency. **This review cannot claim those builds ran.**
- **Audio routing and voice.** Physical HeyCyan mic → answer in glasses, no host speaker for `DISALLOW_PHONE`; also test disconnected, bonding, reconnect, Android TTS and ElevenLabs credential restore. Collect actual route endpoint and audible confirmation separately.
- **Actions and long tasks.** Test exact user command; untrusted screen snapshot; approve/deny; cancel mid-tool; provider timeout after partial output; process restart and task receipt. Check “at most once” for side-effecting actions.
- **Local model lifecycle.** Simulated broken 206/resume plus genuine 1+ GB interrupted transfer, disk full, mismatched SHA, airplane mode, backend compatibility and thermal pressure on device. The new tests are authored but not executed here.
- **Restore before destructive changes.** Full backup/restore on sacrificial data with secrets omitted, missing media/links handled explicitly, no foreign conversation/memory sharing.

### P1 — product completeness and clarity

- Introduce one consistent status vocabulary and actionable recovery across provider/model/device/speech/automation screens: *Ready*, *Needs setup*, *Working*, *Needs permission*, *Unavailable*, *Retryable error*, *Requires review* — **statuses must come from their actual owners**.
- Make offline-first onboarding follow PocketPal's clear progression: select trusted compatible artifact → estimate storage/compute → download/verify → run first sample → optional role qualification. Never auto-install packs or switch models.
- Bring per-device capability truth up to CyanBridge standards: distinguish supported-on-this-hardware, exposed-but-unverified, and not available; never promise vendor features based only on model name.
- Keep OneBox-style execution receipts visible in Activity without copying its tool count or promoting arbitrary UI actions to unreviewed autonomy.
- Finish dynamic EN/RU/LV runtime labels and accessibility QA; do **not** apply translation to user content, source quotes, tool results or provider output.
- Evaluate Latvian and Russian ASR/TTS with real reference samples rather than assuming localization implies voice-model coverage.
- Improve landscape/narrow-screen ergonomics and process restoration of Home input, notebook edits and pending approvals.

### P2 — after the P0 evidence exists

- Profile assistant first-audible latency by stage: capture → ASR → prefill → first text → first speakable chunk → TTS generation → routed playback.
- Compare the initial setup flow against actual installs of PocketPal/CyanBridge; optimize error clarity, not number of settings.
- Expand wearables only when driver capabilities, button mapping and routing are hardware-proven; the outstanding watch microphone/PTT gap is separate work.

## 5. Evidence and instructions for the next coding iteration

The full compatibility/validation record must always include: **source SHA**, Android version, device model and firmware, selected STT/TTS provider, exact local artifact SHA/runtime backend (if any), connectivity, permission state, physical audio endpoints, interaction ID, execution receipt/result, and whether success was UI-simulated, unit-tested, built, installed, or audibly verified.

Do not erase uncertainty: GitHub source writes and unit-test **code added** are verified by the repository; actual Gradle test execution, APK assembly, Bluetooth association and physical acceptance **are not** established by this audit. Work on and push to `main` only with non-force, expected-head fast-forward updates. No GitHub Actions.
