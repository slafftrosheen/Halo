# Halo current state

Status: CURRENT SOURCE MAP. Provider review updated 2026-09-24.
Source review is correlated with the supplied 2026-09-21 device trace. No post-fix build, instrumented test, benchmark, or hardware rerun is claimed.

[ManualDependencyGraph](app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt) is
the process composition root. [App HaloRuntime](app/src/main/java/com/halo/app/HaloRuntime.kt)
starts the phone Engine/EventBus/Blackboard and speech components. Its source explicitly
excludes the older microkernel/ProcessManager path from normal phone startup.

| Area | Present in code | Limit |
|---|---|---|
| UI | Five primary tabs (Home, Activity, Notebook, Devices, Settings); persistent NB-01 notes/search/lifecycle flow; spatial Halo Presence; System/Light/Dark appearance; reachable controls traced to real actions; shared 48dp/accessibility contracts; Home exposes provider/STT/microphone recovery; pairing exposes Bluetooth permission/radio recovery | Notebook files/memory/conversations/Working Set are not implemented; console/lab/inspector are not AppShell routes; full TalkBack/device acceptance and frame-time performance are not established |
| Presence | Asymmetric rounded-cell renderer driven by HaloRuntimeState; no permanent idle animation; compact signal-cell glyphs in Activity/Notebook/navigation; Android animator-scale zero disables Presence transitions | No measured audio/output envelope yet; low-power behavior beyond platform animation removal is not established |
| Cognition | Engine pipeline and CognitiveSession streaming/tool rounds; persisted AiContextPolicy from Intelligence is resolved for each new default conversational turn; Tools UI controls real tool exposure | General model-to-Android capability bridge is not wired; memory context source is still unpopulated, so Memory UI is intentionally hidden; no authoritative agent-mode switch exists yet |
| Model tools | Echo and current time registered in app graph | Not general phone/device actions |
| Providers | Native llama.cpp is the designated clean-install/default inference provider; Ollama is optional LAN HTTP inference; OpenRouter is optional cloud inference with `openrouter/free` as its default router; Android assistant is handoff-only; Sherpa and Android TTS own speech. GGUF download integrity no longer depends on Halo projecting `general.architecture`. | Model compatibility is still proven by the native loader at load time; Ollama/OpenRouter require explicit setup; Qwen3 Android binding/chat-template behavior still needs physical acceptance. |
| Speech input | Physical HeyCyan microphone capture was validated in the supplied 2026-09-21 run with zero packet/frame drops in sampled turns; external buffers receive whole-capture analysis with labeled Silero/energy estimation; low-gain `SIMPLE_ENERGY` captures are no longer rejected solely by the Silero voiced-ratio threshold | Threshold/corpus acceptance remains manual; post-fix acceptance still requires a fresh hardware run |
| Persistence | Room Memory plus NB-01 workspace-object/note/recovery-draft tables, local note search and lifecycle management; in-memory cognitive/conversation stores | Notebook storage is not cognitive memory; Room-backed CognitiveSession and remaining Notebook object types are not established |
| Reminders | Exact-alarm adapter with simple duration parsing | Complete CRUD, robust ambiguity, permission/reboot recovery not established |
| Notifications/routines/privacy | Named modules/contracts | Operational summarizer, full automation engine and universal network firewall not established |
| Plugins | In-process registration/capability contribution/dispatch | No security sandbox or downloadable plugin installer established |
| Devices | Persisted identities/session handling; HeyCyan/BuddyBot paths; callback-serialized GATT physically validated; CompanionDeviceManager audio association, exact BLE-control-to-Classic-audio identity mapping, process-level association restoration, exact-address route verification, and strict synthesize-to-PCM/AudioTrack playback are implemented. Wearable-origin LLM and deterministic command responses both preserve the wearable target with phone fallback disabled. Recording/media intent completion now requires device mode/media-count evidence; missing evidence becomes `UNCONFIRMED` | Physical HeyCyan speaker output remains unvalidated on the current post-fix build; the next run must prove the glasses are audible and the phone speaker stays silent |
| Performance | Benchmark build type, Macrobenchmark module, Baseline Profile generator, startup/frame metrics, reportFullyDrawn and debug StrictMode are present. Storage/model/secret migration is now asynchronous, WorkManager is lazily initialized, appearance persistence is worker-dispatched, and provider diagnostics/assistant discovery are kept off Main | The supplied pre-fix trace contained a startup ANR and a 133-frame stall; no post-fix benchmark or device rerun has yet demonstrated those regressions are cleared |
| Credentials | Runtime storage is wrapped by `SecretBackedStorage`; OpenRouter credential migration moves the legacy key into Android Keystore-backed secret storage | Keystore migration and backup/restore behavior still require device acceptance across upgrade/process-recreation scenarios |
| UI verification | Robolectric/Roborazzi dependencies; light/dark/1.30x-font snapshots; appearance, interaction, Bluetooth permission mapping, platform mic-state, provider projection, canonical pairing identity, auto-connect selection/migration and reloadable-STT tests | Golden images/tests have not been executed in this source-only pass; whole-screen/device-specific coverage is incomplete |
| CI | Workflow declares lint/test/build/upload | Current success not established; detekt/ktlint configuration mismatch |

Source-only follow-up on 2026-09-24: the HeyCyan transfer path now carries the BLE-decoded
device IP into live device state, prefers that endpoint for media sync, and scopes transfer HTTP to
the glasses Android `Network` instead of process-wide routing. Wearable voice activation can also
start native llama.cpp model loading while capture is in progress, but only when native llama.cpp is
the selected inference provider and the native-resource budget permits concurrent heavyweight
models. Neither change is counted as hardware or latency acceptance until rerun on the physical
glasses/phone pair.

See [architecture](docs/04-architecture/ARCHITECTURE.md),
[providers](docs/ai/AI_PROVIDER_ARCHITECTURE.md),
[persistence](docs/04-architecture/DATABASE.md),
[security](docs/12-security/THREAT_MODEL.md),
[HeyCyan](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md) and
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

Read [release readiness](RELEASE_READINESS.md) separately. Code presence is not measured
latency, power consumption, passing tests or physical acceptance.
